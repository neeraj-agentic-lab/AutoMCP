/*
 * MCP REST Adapter - REST Adapter Service
 * 
 * Service for executing HTTP requests to external REST APIs based on MCP tool calls.
 * Handles request construction, authentication, parameter mapping, and response processing
 * with comprehensive error handling and retry logic.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpwrapper.platform.persistence.entity.ApiConfigurationEntity;
import com.mcpwrapper.runtime.service.ReactiveConfigurationService;
import com.mcpwrapper.runtime.service.ReactiveEnvironmentVariableService;
import com.mcpwrapper.runtime.service.YamlApiConfigurationService;
import com.mcpwrapper.transport.mcp.McpTool;
import com.mcpwrapper.transport.mcp.McpToolCall;
import com.mcpwrapper.transport.mcp.McpToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for adapting MCP tool calls to REST API requests.
 * 
 * This service handles the complete request lifecycle:
 * - Parameter extraction and validation
 * - URL construction with path/query parameters
 * - Authentication header injection
 * - Request body construction
 * - HTTP request execution
 * - Response processing and error handling
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
public class RestAdapterService {
    
    private static final Logger logger = LoggerFactory.getLogger(RestAdapterService.class);
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    private final WebClient webClient;
    private final WebClient oauth2WebClient;
    private final ReactiveConfigurationService configurationService;
    private final ReactiveEnvironmentVariableService environmentService;
    private final YamlApiConfigurationService yamlApiConfigurationService;
    private final ObjectMapper objectMapper;
    
    public RestAdapterService(
            WebClient.Builder webClientBuilder,
            ReactiveConfigurationService configurationService,
            ReactiveEnvironmentVariableService environmentService,
            YamlApiConfigurationService yamlApiConfigurationService,
            ObjectMapper objectMapper,
            @Autowired(required = false) @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {
        this.webClient = webClientBuilder.build();
        this.oauth2WebClient = oauth2WebClient;
        this.configurationService = configurationService;
        this.environmentService = environmentService;
        this.yamlApiConfigurationService = yamlApiConfigurationService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Executes a REST API call based on an MCP tool call.
     * 
     * @param toolCall MCP tool call to execute
     * @param tool MCP tool definition
     * @return Mono containing the execution result
     */
    public Mono<McpToolResult> executeRestCall(McpToolCall toolCall, McpTool tool) {
        System.out.println("=== REST ADAPTER DEBUG: Starting executeRestCall for tool: " + toolCall.name());
        logger.debug("Executing REST call for tool: {} with call ID: {}", toolCall.name(), toolCall.callId());
        
        Instant startTime = Instant.now();
        
        return extractApiConfigId(tool)
            .doOnNext(configId -> {
                System.out.println("=== REST ADAPTER DEBUG: Extracted API config ID: " + configId);
                logger.debug("=== EXTRACTED API CONFIG ID: {}", configId);
            })
            .doOnError(error -> {
                System.out.println("=== REST ADAPTER DEBUG: Error extracting API config ID: " + error.getMessage());
                logger.error("=== ERROR extracting API config ID: {}", error.getMessage(), error);
            })
            .flatMap(configurationService::getConfiguration)
            .doOnNext(config -> {
                try {
                    System.out.println("=== REST ADAPTER DEBUG: Got configuration object: " + (config != null ? "present" : "null"));
                    if (config != null) {
                        System.out.println("=== REST ADAPTER DEBUG: Config ID: " + (config.id() != null ? config.id().toString() : "null"));
                        System.out.println("=== REST ADAPTER DEBUG: Config baseUrl: " + (config.baseUrl() != null ? config.baseUrl() : "null"));
                        System.out.println("=== REST ADAPTER DEBUG: Config name: " + (config.name() != null ? config.name() : "null"));
                    }
                    logger.debug("=== GOT CONFIGURATION: id={}, baseUrl={}", 
                        config != null && config.id() != null ? config.id() : "null", 
                        config != null && config.baseUrl() != null ? config.baseUrl() : "null");
                } catch (Exception e) {
                    System.out.println("=== REST ADAPTER DEBUG: Exception in doOnNext config processing: " + e.getClass().getSimpleName() + " - " + (e.getMessage() != null ? e.getMessage() : "null"));
                    System.out.println("=== REST ADAPTER DEBUG: Config processing exception stack trace:");
                    e.printStackTrace();
                    throw e;
                }
            })
            .doOnError(error -> {
                System.out.println("=== REST ADAPTER DEBUG: Error getting configuration: " + error.getMessage());
                logger.error("=== ERROR getting configuration: {}", error.getMessage(), error);
            })
            .flatMap(config -> buildAndExecuteRequest(toolCall, tool, config))
            .map(response -> McpToolResult.success(
                toolCall.callId(),
                response,
                Duration.between(startTime, Instant.now())
            ))
            .onErrorResume(error -> handleExecutionError(toolCall.callId(), error, startTime))
            .doOnSuccess(result -> logger.debug("REST call completed for tool: {} ({}ms)", 
                toolCall.name(), result.executionTime().toMillis()))
            .doOnError(error -> logger.error("REST call failed for tool: {}", toolCall.name(), error));
    }
    
    /**
     * Validates that a tool call can be executed as a REST request.
     * 
     * @param toolCall MCP tool call
     * @param tool MCP tool definition
     * @return Mono containing validation result
     */
    public Mono<RestValidationResult> validateRestCall(McpToolCall toolCall, McpTool tool) {
        logger.debug("Validating REST call for tool: {}", toolCall.name());
        
        return extractApiConfigId(tool)
            .flatMap(configurationService::getConfiguration)
            .flatMap(config -> validateRequestParameters(toolCall, tool, config))
            .map(errors -> new RestValidationResult(errors.isEmpty(), errors))
            .onErrorReturn(new RestValidationResult(false, java.util.List.of("Validation failed")));
    }
    
    // Private helper methods
    
    private Mono<String> buildAndExecuteRequest(McpToolCall toolCall, McpTool tool, ApiConfigurationEntity config) {
        System.out.println("=== BUILD AND EXECUTE DEBUG: Starting for tool " + toolCall.name() + " with config ID " + config.id());
        System.out.println("=== BUILD AND EXECUTE DEBUG: Config details - id=" + config.id() + ", name=" + config.name() + ", baseUrl=" + config.baseUrl());
        System.out.println("=== BUILD AND EXECUTE DEBUG: About to call yamlApiConfigurationService.resolveEffectiveConfig");
        logger.debug("=== BUILD AND EXECUTE REQUEST: Starting for tool {} with config ID {}", toolCall.name(), config.id());
        
        return yamlApiConfigurationService.resolveEffectiveConfig(config)
            .doOnNext(effectiveConfig -> {
                System.out.println("=== BUILD AND EXECUTE DEBUG: Effective config resolved - baseUrl=" + effectiveConfig.baseUrl() + 
                    ", authConfig=" + (effectiveConfig.authConfig() != null ? "present" : "null"));
                logger.debug("=== EFFECTIVE CONFIG RESOLVED: baseUrl={}, authConfig={}", 
                    effectiveConfig.baseUrl(), effectiveConfig.authConfig() != null ? "present" : "null");
            })
            .doOnError(error -> {
                String errorMessage = error.getMessage() != null ? error.getMessage() : "null";
                System.out.println("=== BUILD AND EXECUTE DEBUG: Error resolving effective config: " + errorMessage);
                System.out.println("=== BUILD AND EXECUTE DEBUG: Error stack trace:");
                error.printStackTrace();
                logger.error("=== ERROR resolving effective config: {}", errorMessage, error);
            })
            .flatMap(effectiveConfig -> Mono.fromCallable(() -> {
                System.out.println("=== BUILD AND EXECUTE DEBUG: Extracting request info for tool " + toolCall.name());
                logger.debug("=== EXTRACTING REQUEST INFO for tool {}", toolCall.name());
                return extractRequestInfo(tool);
            })
                .doOnNext(requestInfo -> {
                    System.out.println("=== BUILD AND EXECUTE DEBUG: Request info extracted - method=" + requestInfo.httpMethod() + 
                        ", path=" + requestInfo.endpointPath());
                    logger.debug("=== REQUEST INFO EXTRACTED: method={}, path={}", 
                        requestInfo.httpMethod(), requestInfo.endpointPath());
                })
                .flatMap(requestInfo -> buildRequestUrl(toolCall, requestInfo, effectiveConfig)
                    .doOnNext(url -> {
                        System.out.println("=== BUILD AND EXECUTE DEBUG: URL built: " + url);
                        logger.debug("=== URL BUILT: {}", url);
                    })
                    .flatMap(url -> executeHttpRequest(toolCall, requestInfo, url, effectiveConfig))))
            .doOnError(error -> {
                System.out.println("=== BUILD AND EXECUTE DEBUG: Error in buildAndExecuteRequest: " + error.getMessage());
                logger.error("=== ERROR in buildAndExecuteRequest: {}", error.getMessage(), error);
            });
    }
    
    private RestRequestInfo extractRequestInfo(McpTool tool) {
        Map<String, Object> metadata = tool.metadata();
        if (metadata == null) {
            throw new IllegalArgumentException("Tool metadata is required for REST execution");
        }
        
        String httpMethod = (String) metadata.get("httpMethod");
        String endpointPath = (String) metadata.get("endpointPath");
        
        if (httpMethod == null || endpointPath == null) {
            throw new IllegalArgumentException("Tool must have httpMethod and endpointPath in metadata");
        }
        
        return new RestRequestInfo(
            HttpMethod.valueOf(httpMethod.toUpperCase()),
            endpointPath,
            extractContentType(metadata),
            extractHeaders(metadata)
        );
    }
    
    private Mono<URI> buildRequestUrl(McpToolCall toolCall, RestRequestInfo requestInfo, YamlApiConfigurationService.EffectiveApiConfig config) {
        return Mono.fromCallable(() -> {
            logger.debug("Building request URL - config: {}, baseUrl: {}", config, config != null ? config.baseUrl() : "null");
            String baseUrl = config.baseUrl();
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("Base URL is required in API configuration");
            }
            
            // Replace path parameters
            String path = replacePathParameters(requestInfo.endpointPath(), toolCall.arguments());
            
            // Build URI with query parameters
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + path);
            addQueryParameters(builder, toolCall.arguments());
            
            return builder.build().toUri();
        });
    }
    
    private Mono<String> executeHttpRequest(McpToolCall toolCall, RestRequestInfo requestInfo, 
                                          URI url, YamlApiConfigurationService.EffectiveApiConfig config) {
        
        return buildAuthHeaders(config, toolCall.arguments())
            .flatMap(authHeaders -> {
                WebClient.RequestHeadersSpec<?> request = webClient
                    .method(requestInfo.httpMethod())
                    .uri(url)
                    .headers(headers -> {
                        headers.addAll(authHeaders);
                        headers.addAll(requestInfo.headers());
                        if (requestInfo.contentType() != null) {
                            headers.setContentType(requestInfo.contentType());
                        }
                    });
                
                // Add request body if needed
                if (hasRequestBody(requestInfo.httpMethod()) && toolCall.arguments().has("requestBody")) {
                    request = ((WebClient.RequestBodySpec) request)
                        .bodyValue(toolCall.arguments().get("requestBody"));
                }
                
                // Set timeout from configuration
                Duration timeout = Duration.ofSeconds(config.timeoutSeconds() != null ? config.timeoutSeconds() : 30);
                
                return request.retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout);
            });
    }
    
    private String replacePathParameters(String path, JsonNode arguments) {
        Matcher matcher = PATH_PARAM_PATTERN.matcher(path);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String paramName = matcher.group(1);
            JsonNode paramValue = arguments.get(paramName);
            
            if (paramValue == null || paramValue.isNull()) {
                throw new IllegalArgumentException("Missing required path parameter: " + paramName);
            }
            
            matcher.appendReplacement(result, paramValue.asText());
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    private void addQueryParameters(UriComponentsBuilder builder, JsonNode arguments) {
        arguments.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            
            // Skip special parameters
            if ("requestBody".equals(key) || key.startsWith("header_") || key.startsWith("_")) {
                return;
            }
            
            // Skip path parameters (they're already replaced)
            if (!value.isNull()) {
                builder.queryParam(key, value.asText());
            }
        });
    }
    
    private Mono<HttpHeaders> buildAuthHeaders(YamlApiConfigurationService.EffectiveApiConfig config, JsonNode toolArguments) {
        if (config.authConfig() == null) {
            return Mono.just(new HttpHeaders());
        }
        
        return Mono.fromCallable(() -> {
            HttpHeaders headers = new HttpHeaders();
            JsonNode authConfig = config.authConfig();
            
            // Double-check for null authConfig (defensive programming)
            if (authConfig == null) {
                return headers;
            }

            boolean required = !authConfig.has("required") || authConfig.get("required").asBoolean(true);
            
            String authType = authConfig.has("type") ? authConfig.get("type").asText() : null;
            
            return switch (authType != null ? authType.toLowerCase() : "none") {
                case "bearer" -> addBearerAuth(headers, authConfig, toolArguments, required);
                case "apikey" -> addApiKeyAuth(headers, authConfig, toolArguments, required);
                case "basic" -> addBasicAuth(headers, authConfig, toolArguments, required);
                default -> headers;
            };
        });
    }
    
    private HttpHeaders addBearerAuth(HttpHeaders headers, JsonNode authConfig, JsonNode toolArguments, boolean required) {
        String tokenFromArg = authConfig.hasNonNull("tokenFromArg") ? authConfig.get("tokenFromArg").asText() : null;
        String token = tokenFromArg != null ? extractArgValue(toolArguments, tokenFromArg) : null;
        if (token == null) {
            token = extractAuthValue(authConfig, "token");
        }

        if (token == null) {
            String message = tokenFromArg != null
                ? "Missing required auth argument: " + tokenFromArg
                : "Missing required bearer auth token";

            if (required) {
                logger.warn("Bearer auth required but missing (tokenFromArg={})", tokenFromArg);
                throw new IllegalArgumentException(message);
            }

            logger.debug("Bearer auth not applied (required=false, tokenFromArg={})", tokenFromArg);
            return headers;
        }

        String headerName = authConfig.hasNonNull("headerName") ? authConfig.get("headerName").asText() : HttpHeaders.AUTHORIZATION;
        String prefix = authConfig.hasNonNull("prefix") ? authConfig.get("prefix").asText() : "Bearer ";
        headers.set(headerName, prefix + token);
        logger.debug("Applied bearer auth using headerName={} tokenFromArg={}", headerName, tokenFromArg);

        return headers;
    }
    
    private HttpHeaders addApiKeyAuth(HttpHeaders headers, JsonNode authConfig, JsonNode toolArguments, boolean required) {
        String apiKeyFromArg = authConfig.hasNonNull("apiKeyFromArg") ? authConfig.get("apiKeyFromArg").asText() : null;
        String apiKey = apiKeyFromArg != null ? extractArgValue(toolArguments, apiKeyFromArg) : null;
        if (apiKey == null) {
            apiKey = extractAuthValue(authConfig, "apiKey");
        }

        if (apiKey == null) {
            String message = apiKeyFromArg != null
                ? "Missing required auth argument: " + apiKeyFromArg
                : "Missing required apiKey";

            if (required) {
                logger.warn("ApiKey auth required but missing (apiKeyFromArg={})", apiKeyFromArg);
                throw new IllegalArgumentException(message);
            }

            logger.debug("ApiKey auth not applied (required=false, apiKeyFromArg={})", apiKeyFromArg);
            return headers;
        }

        String headerName = authConfig.hasNonNull("headerName") ? authConfig.get("headerName").asText() : "X-API-Key";
        headers.set(headerName, apiKey);
        logger.debug("Applied apiKey auth using headerName={} apiKeyFromArg={}", headerName, apiKeyFromArg);
        return headers;
    }
    
    private HttpHeaders addBasicAuth(HttpHeaders headers, JsonNode authConfig, JsonNode toolArguments, boolean required) {
        String usernameFromArg = authConfig.hasNonNull("usernameFromArg") ? authConfig.get("usernameFromArg").asText() : null;
        String passwordFromArg = authConfig.hasNonNull("passwordFromArg") ? authConfig.get("passwordFromArg").asText() : null;

        String username = usernameFromArg != null ? extractArgValue(toolArguments, usernameFromArg) : null;
        String password = passwordFromArg != null ? extractArgValue(toolArguments, passwordFromArg) : null;

        if (username == null) {
            username = extractAuthValue(authConfig, "username");
        }

        if (password == null) {
            password = extractAuthValue(authConfig, "password");
        }

        if (username == null || password == null) {
            String message;
            if (username == null && usernameFromArg != null) {
                message = "Missing required auth argument: " + usernameFromArg;
            } else if (password == null && passwordFromArg != null) {
                message = "Missing required auth argument: " + passwordFromArg;
            } else {
                message = "Missing required basic auth credentials";
            }

            if (required) {
                logger.warn("Basic auth required but missing (usernameFromArg={}, passwordFromArg={})", usernameFromArg, passwordFromArg);
                throw new IllegalArgumentException(message);
            }

            logger.debug("Basic auth not applied (required=false, usernameFromArg={}, passwordFromArg={})", usernameFromArg, passwordFromArg);
            return headers;
        }
        
        headers.setBasicAuth(username, password);
        logger.debug("Applied basic auth using usernameFromArg={} passwordFromArg={}", usernameFromArg, passwordFromArg);
        return headers;
    }

    private String extractArgValue(JsonNode toolArguments, String argName) {
        if (toolArguments == null || argName == null) {
            return null;
        }
        JsonNode node = toolArguments.get(argName);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value != null && !value.isBlank() ? value : null;
    }
    
    private String extractAuthValue(JsonNode authConfig, String field) {
        if (!authConfig.has(field)) {
            return null;
        }
        
        String value = authConfig.get(field).asText();
        
        // TODO: Temporarily disabled environment variable resolution to fix blocking issue
        // This needs to be made reactive in the future
        /*
        if (value.startsWith("${") && value.endsWith("}")) {
            String envVarName = value.substring(2, value.length() - 1);
            return environmentService.getDecryptedValue(envVarName).block();
        }
        */
        
        return value;
    }
    
    private MediaType extractContentType(Map<String, Object> metadata) {
        Object contentType = metadata.get("contentType");
        if (contentType != null) {
            return MediaType.parseMediaType(contentType.toString());
        }
        return MediaType.APPLICATION_JSON;
    }
    
    private HttpHeaders extractHeaders(Map<String, Object> metadata) {
        HttpHeaders headers = new HttpHeaders();
        
        // Extract custom headers from metadata
        metadata.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("header."))
            .forEach(entry -> {
                String headerName = entry.getKey().substring(7); // Remove "header." prefix
                headers.set(headerName, entry.getValue().toString());
            });
        
        return headers;
    }
    
    private boolean hasRequestBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }
    
    private Mono<UUID> extractApiConfigId(McpTool tool) {
        if (tool.metadata() == null || !tool.metadata().containsKey("apiConfigId")) {
            return Mono.error(new IllegalArgumentException("Tool must have apiConfigId in metadata"));
        }
        
        try {
            String configIdStr = tool.metadata().get("apiConfigId").toString();
            return Mono.just(UUID.fromString(configIdStr));
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Invalid apiConfigId in tool metadata"));
        }
    }
    
    private Mono<java.util.List<String>> validateRequestParameters(McpToolCall toolCall, McpTool tool, 
                                                                 ApiConfigurationEntity config) {
        return Mono.fromCallable(() -> {
            java.util.List<String> errors = new java.util.ArrayList<>();
            
            // Validate required configuration
            if (config.baseUrl() == null || config.baseUrl().trim().isEmpty()) {
                errors.add("Base URL is required in API configuration");
            }
            
            // Validate tool metadata
            if (tool.metadata() == null) {
                errors.add("Tool metadata is required");
                return errors;
            }
            
            if (!tool.metadata().containsKey("httpMethod")) {
                errors.add("HTTP method is required in tool metadata");
            }
            
            if (!tool.metadata().containsKey("endpointPath")) {
                errors.add("Endpoint path is required in tool metadata");
            }
            
            // Validate required parameters based on input schema
            if (tool.inputSchema() != null && tool.inputSchema().has("required")) {
                JsonNode required = tool.inputSchema().get("required");
                if (required.isArray()) {
                    required.forEach(requiredField -> {
                        String fieldName = requiredField.asText();
                        if (!toolCall.arguments().has(fieldName)) {
                            errors.add("Missing required parameter: " + fieldName);
                        }
                    });
                }
            }
            
            return errors;
        });
    }
    
    private Mono<McpToolResult> handleExecutionError(String callId, Throwable error, Instant startTime) {
        Duration executionTime = Duration.between(startTime, Instant.now());
        
        if (error instanceof WebClientResponseException webError) {
            return Mono.just(McpToolResult.error(
                callId,
                "HTTP_ERROR",
                String.format("HTTP %d: %s", webError.getStatusCode().value(), webError.getResponseBodyAsString()),
                Map.of(
                    "statusCode", webError.getStatusCode().value(),
                    "responseBody", webError.getResponseBodyAsString(),
                    "headers", webError.getHeaders().toSingleValueMap()
                ),
                executionTime
            ));
        }
        
        return Mono.just(McpToolResult.error(
            callId,
            "EXECUTION_ERROR",
            error.getMessage(),
            Map.of("errorType", error.getClass().getSimpleName()),
            executionTime
        ));
    }
    
    // Helper records
    
    private record RestRequestInfo(
        HttpMethod httpMethod,
        String endpointPath,
        MediaType contentType,
        HttpHeaders headers
    ) {}
    
    public record RestValidationResult(
        boolean isValid,
        java.util.List<String> errors
    ) {}
}
