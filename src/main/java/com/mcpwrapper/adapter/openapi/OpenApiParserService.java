/*
 * MCP REST Adapter - OpenAPI Parser Service
 * 
 * Service for parsing OpenAPI specifications and extracting endpoint information.
 * Converts OpenAPI operations into structured data that can be used to generate
 * MCP tool definitions and handle REST API interactions.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.adapter.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for parsing OpenAPI specifications and extracting endpoint information.
 * 
 * This service provides comprehensive OpenAPI parsing capabilities including:
 * - Specification validation and parsing
 * - Endpoint discovery and analysis
 * - Parameter and schema extraction
 * - Response type analysis
 * - Security requirement processing
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
public class OpenApiParserService {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenApiParserService.class);
    
    private final ObjectMapper objectMapper;
    private final OpenAPIParser parser;
    
    public OpenApiParserService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.parser = new OpenAPIParser();
    }
    
    /**
     * Parses an OpenAPI specification from various sources.
     * 
     * @param request parsing request containing specification source
     * @return Mono containing parsed API specification
     */
    public Mono<ParsedApiSpecification> parseOpenApiSpec(OpenApiParseRequest request) {
        logger.debug("Parsing OpenAPI specification from source: {}", request.sourceType());
        
        return Mono.fromCallable(() -> {
            SwaggerParseResult result = switch (request.sourceType()) {
                case URL -> parser.readLocation(request.source(), null, null);
                case FILE -> parser.readLocation("file://" + request.source(), null, null);
                case TEXT -> parser.readContents(request.source(), null, null);
            };
            
            if (result.getOpenAPI() == null) {
                throw new OpenApiParseException("Failed to parse OpenAPI specification: " + 
                    String.join(", ", result.getMessages()));
            }
            
            return result.getOpenAPI();
        })
        .flatMap(this::convertToApiSpecification)
        .doOnSuccess(spec -> logger.info("Successfully parsed OpenAPI spec: {} with {} endpoints", 
            spec.title(), spec.endpoints().size()))
        .doOnError(error -> logger.error("Failed to parse OpenAPI specification", error));
    }
    
    /**
     * Extracts all endpoints from a parsed OpenAPI specification.
     * 
     * @param openApi parsed OpenAPI object
     * @return Flux of API endpoints
     */
    public Flux<ApiEndpoint> extractEndpoints(OpenAPI openApi) {
        logger.info("=== EXTRACTING ENDPOINTS: Starting endpoint extraction");
        logger.info("=== PATHS AVAILABLE: {}", openApi.getPaths() != null ? openApi.getPaths().keySet() : "null");
        logger.info("=== OPENAPI OBJECT: {}", openApi != null ? "not null" : "null");
        
        if (openApi == null) {
            logger.error("=== NULL OPENAPI: OpenAPI object is null");
            return Flux.empty();
        }
        
        if (openApi.getPaths() == null) {
            logger.error("=== NULL PATHS: OpenAPI.getPaths() returned null");
            return Flux.empty();
        }
        
        if (openApi.getPaths().isEmpty()) {
            logger.error("=== EMPTY PATHS: OpenAPI.getPaths() returned empty map");
            return Flux.empty();
        }
        
        logger.info("=== PATHS COUNT: Found {} paths", openApi.getPaths().size());
        openApi.getPaths().forEach((path, pathItem) -> {
            logger.info("=== PATH DETAILS: {} -> {}", path, pathItem != null ? "PathItem exists" : "PathItem is null");
            if (pathItem != null && pathItem.readOperationsMap() != null) {
                logger.info("=== OPERATIONS: Path {} has {} operations", path, pathItem.readOperationsMap().size());
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    logger.info("=== OPERATION: {} {} -> {}", method, path, operation != null ? operation.getOperationId() : "null operation");
                });
            }
        });
        
        return Flux.fromIterable(openApi.getPaths().entrySet())
            .flatMap(pathEntry -> {
                String path = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();
                
                return Flux.fromIterable(pathItem.readOperationsMap().entrySet())
                    .map(operationEntry -> {
                        PathItem.HttpMethod httpMethod = operationEntry.getKey();
                        Operation operation = operationEntry.getValue();
                        
                        return createApiEndpoint(path, httpMethod, operation, openApi);
                    });
            })
            .doOnNext(endpoint -> logger.debug("Extracted endpoint: {} {}", 
                endpoint.httpMethod(), endpoint.path()))
            .doOnComplete(() -> logger.debug("Completed endpoint extraction"));
    }
    
    /**
     * Validates an OpenAPI specification for completeness and correctness.
     * 
     * @param openApi OpenAPI specification to validate
     * @return Mono containing validation result
     */
    public Mono<ValidationResult> validateOpenApiSpec(OpenAPI openApi) {
        logger.debug("Validating OpenAPI specification");
        
        return Mono.fromCallable(() -> {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            // Basic validation
            if (openApi.getInfo() == null) {
                errors.add("Missing API info section");
            } else {
                if (openApi.getInfo().getTitle() == null || openApi.getInfo().getTitle().trim().isEmpty()) {
                    errors.add("Missing API title");
                }
                if (openApi.getInfo().getVersion() == null || openApi.getInfo().getVersion().trim().isEmpty()) {
                    warnings.add("Missing API version");
                }
            }
            
            // Server validation
            if (openApi.getServers() == null || openApi.getServers().isEmpty()) {
                warnings.add("No servers defined - will need base URL configuration");
            }
            
            // Paths validation
            if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
                errors.add("No paths defined in specification");
            } else {
                validatePaths(openApi, errors, warnings);
            }
            
            boolean isValid = errors.isEmpty();
            return new ValidationResult(isValid, errors, warnings);
        })
        .doOnSuccess(result -> logger.debug("Validation completed: valid={}, errors={}, warnings={}", 
            result.isValid(), result.errors().size(), result.warnings().size()));
    }
    
    /**
     * Generates JSON schema for an endpoint's request parameters.
     * 
     * @param endpoint API endpoint
     * @return Mono containing JSON schema
     */
    public Mono<JsonNode> generateRequestSchema(ApiEndpoint endpoint) {
        logger.debug("Generating request schema for endpoint: {} {}", 
            endpoint.httpMethod(), endpoint.path());
        
        return Mono.fromCallable(() -> {
            var schemaBuilder = objectMapper.createObjectNode();
            schemaBuilder.put("type", "object");
            
            var properties = schemaBuilder.putObject("properties");
            var required = schemaBuilder.putArray("required");
            
            // Add path parameters
            endpoint.pathParameters().forEach(param -> {
                var paramSchema = properties.putObject(param.name());
                paramSchema.put("type", mapSchemaType(param.type()));
                paramSchema.put("description", param.description());
                if (param.required()) {
                    required.add(param.name());
                }
            });
            
            // Add query parameters
            endpoint.queryParameters().forEach(param -> {
                var paramSchema = properties.putObject(param.name());
                paramSchema.put("type", mapSchemaType(param.type()));
                paramSchema.put("description", param.description());
                if (param.required()) {
                    required.add(param.name());
                }
            });
            
            // Add request body schema if present
            if (endpoint.requestBodySchema() != null) {
                var bodySchema = properties.putObject("requestBody");
                bodySchema.setAll((com.fasterxml.jackson.databind.node.ObjectNode) endpoint.requestBodySchema());
                required.add("requestBody");
            }
            
            return schemaBuilder;
        });
    }
    
    // Private helper methods
    
    private Mono<ParsedApiSpecification> convertToApiSpecification(OpenAPI openApi) {
        String title = openApi.getInfo() != null ? openApi.getInfo().getTitle() : "Unknown API";
        String version = openApi.getInfo() != null ? openApi.getInfo().getVersion() : "1.0.0";
        String description = openApi.getInfo() != null ? openApi.getInfo().getDescription() : "";
        
        List<String> servers = openApi.getServers() != null 
            ? openApi.getServers().stream().map(Server::getUrl).collect(Collectors.toList())
            : List.of();
        
        return extractEndpoints(openApi)
            .collectList()
            .map(endpoints -> new ParsedApiSpecification(
                title,
                version,
                description,
                servers,
                endpoints != null ? endpoints : List.of(),
                extractSecuritySchemes(openApi),
                extractTags(openApi)
            ));
    }
    
    private ApiEndpoint createApiEndpoint(String path, PathItem.HttpMethod httpMethod, 
                                        Operation operation, OpenAPI openApi) {
        
        String operationId = operation.getOperationId() != null 
            ? operation.getOperationId() 
            : generateOperationId(httpMethod, path);
        
        String summary = operation.getSummary() != null ? operation.getSummary() : "";
        String description = operation.getDescription() != null ? operation.getDescription() : summary;
        
        List<EndpointParameter> pathParams = extractPathParameters(operation);
        List<EndpointParameter> queryParams = extractQueryParameters(operation);
        List<EndpointParameter> headerParams = extractHeaderParameters(operation);
        
        JsonNode requestBodySchema = extractRequestBodySchema(operation);
        Map<String, JsonNode> responseSchemas = extractResponseSchemas(operation);
        
        List<String> tags = operation.getTags() != null ? operation.getTags() : List.of();
        List<String> securityRequirements = extractSecurityRequirements(operation);
        
        return new ApiEndpoint(
            operationId,
            path,
            httpMethod.toString(),
            summary,
            description,
            pathParams,
            queryParams,
            headerParams,
            requestBodySchema,
            responseSchemas,
            tags,
            securityRequirements,
            operation.getDeprecated() != null ? operation.getDeprecated() : false
        );
    }
    
    private List<EndpointParameter> extractPathParameters(Operation operation) {
        if (operation.getParameters() == null) return List.of();
        
        return operation.getParameters().stream()
            .filter(param -> "path".equals(param.getIn()))
            .map(this::convertParameter)
            .collect(Collectors.toList());
    }
    
    private List<EndpointParameter> extractQueryParameters(Operation operation) {
        if (operation.getParameters() == null) return List.of();
        
        return operation.getParameters().stream()
            .filter(param -> "query".equals(param.getIn()))
            .map(this::convertParameter)
            .collect(Collectors.toList());
    }
    
    private List<EndpointParameter> extractHeaderParameters(Operation operation) {
        if (operation.getParameters() == null) return List.of();
        
        return operation.getParameters().stream()
            .filter(param -> "header".equals(param.getIn()))
            .map(this::convertParameter)
            .collect(Collectors.toList());
    }
    
    private EndpointParameter convertParameter(Parameter parameter) {
        String type = "string"; // default
        if (parameter.getSchema() != null && parameter.getSchema().getType() != null) {
            type = parameter.getSchema().getType();
        }
        
        return new EndpointParameter(
            parameter.getName(),
            type,
            parameter.getDescription() != null ? parameter.getDescription() : "",
            parameter.getRequired() != null ? parameter.getRequired() : false,
            parameter.getExample(),
            extractParameterSchema(parameter)
        );
    }
    
    private JsonNode extractRequestBodySchema(Operation operation) {
        if (operation.getRequestBody() == null || 
            operation.getRequestBody().getContent() == null) {
            return null;
        }
        
        // Look for JSON content type first
        MediaType mediaType = operation.getRequestBody().getContent().get("application/json");
        if (mediaType == null) {
            // Fallback to first available content type
            mediaType = operation.getRequestBody().getContent().values().iterator().next();
        }
        
        if (mediaType != null && mediaType.getSchema() != null) {
            return convertSchemaToJsonNode(mediaType.getSchema());
        }
        
        return null;
    }
    
    private Map<String, JsonNode> extractResponseSchemas(Operation operation) {
        Map<String, JsonNode> schemas = new HashMap<>();
        
        if (operation.getResponses() != null) {
            operation.getResponses().forEach((statusCode, response) -> {
                JsonNode schema = extractResponseSchema(response);
                if (schema != null) {
                    schemas.put(statusCode, schema);
                }
            });
        }
        
        return schemas;
    }
    
    private JsonNode extractResponseSchema(ApiResponse response) {
        if (response.getContent() == null) return null;
        
        MediaType mediaType = response.getContent().get("application/json");
        if (mediaType == null) {
            mediaType = response.getContent().values().iterator().next();
        }
        
        if (mediaType != null && mediaType.getSchema() != null) {
            return convertSchemaToJsonNode(mediaType.getSchema());
        }
        
        return null;
    }
    
    private JsonNode convertSchemaToJsonNode(Schema<?> schema) {
        try {
            // Convert OpenAPI schema to JSON node
            String schemaJson = objectMapper.writeValueAsString(schema);
            return objectMapper.readTree(schemaJson);
        } catch (Exception e) {
            logger.warn("Failed to convert schema to JSON node", e);
            return objectMapper.createObjectNode();
        }
    }
    
    private JsonNode extractParameterSchema(Parameter parameter) {
        if (parameter.getSchema() != null) {
            return convertSchemaToJsonNode(parameter.getSchema());
        }
        return null;
    }
    
    private List<String> extractSecurityRequirements(Operation operation) {
        if (operation.getSecurity() == null) return List.of();
        
        return operation.getSecurity().stream()
            .flatMap(secReq -> secReq.keySet().stream())
            .distinct()
            .collect(Collectors.toList());
    }
    
    private Map<String, Object> extractSecuritySchemes(OpenAPI openApi) {
        Map<String, Object> schemes = new HashMap<>();
        
        if (openApi.getComponents() != null && 
            openApi.getComponents().getSecuritySchemes() != null) {
            
            openApi.getComponents().getSecuritySchemes().forEach((name, scheme) -> {
                Map<String, Object> schemeInfo = new HashMap<>();
                schemeInfo.put("type", scheme.getType().toString());
                schemeInfo.put("description", scheme.getDescription());
                schemes.put(name, schemeInfo);
            });
        }
        
        return schemes;
    }
    
    private List<String> extractTags(OpenAPI openApi) {
        if (openApi.getTags() == null) return List.of();
        
        return openApi.getTags().stream()
            .map(tag -> tag.getName())
            .collect(Collectors.toList());
    }
    
    private void validatePaths(OpenAPI openApi, List<String> errors, List<String> warnings) {
        openApi.getPaths().forEach((path, pathItem) -> {
            if (pathItem.readOperationsMap().isEmpty()) {
                warnings.add("Path " + path + " has no operations defined");
            }
            
            pathItem.readOperationsMap().forEach((method, operation) -> {
                if (operation.getOperationId() == null) {
                    warnings.add("Operation " + method + " " + path + " missing operationId");
                }
                if (operation.getSummary() == null && operation.getDescription() == null) {
                    warnings.add("Operation " + method + " " + path + " missing summary/description");
                }
            });
        });
    }
    
    private String generateOperationId(PathItem.HttpMethod method, String path) {
        return method.toString().toLowerCase() + 
               path.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_");
    }
    
    private String mapSchemaType(String openApiType) {
        return switch (openApiType != null ? openApiType.toLowerCase() : "string") {
            case "integer", "number" -> "number";
            case "boolean" -> "boolean";
            case "array" -> "array";
            case "object" -> "object";
            default -> "string";
        };
    }
    
    // Request/Response records and enums
    
    public record OpenApiParseRequest(
        SourceType sourceType,
        String source
    ) {}
    
    public enum SourceType {
        URL, FILE, TEXT
    }
    
    public record ParsedApiSpecification(
        String title,
        String version,
        String description,
        List<String> servers,
        List<ApiEndpoint> endpoints,
        Map<String, Object> securitySchemes,
        List<String> tags
    ) {}
    
    public record ApiEndpoint(
        String operationId,
        String path,
        String httpMethod,
        String summary,
        String description,
        List<EndpointParameter> pathParameters,
        List<EndpointParameter> queryParameters,
        List<EndpointParameter> headerParameters,
        JsonNode requestBodySchema,
        Map<String, JsonNode> responseSchemas,
        List<String> tags,
        List<String> securityRequirements,
        boolean deprecated
    ) {}
    
    public record EndpointParameter(
        String name,
        String type,
        String description,
        boolean required,
        Object example,
        JsonNode schema
    ) {}
    
    public record ValidationResult(
        boolean isValid,
        List<String> errors,
        List<String> warnings
    ) {}
    
    // Exception class
    
    public static class OpenApiParseException extends RuntimeException {
        public OpenApiParseException(String message) {
            super(message);
        }
        
        public OpenApiParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
