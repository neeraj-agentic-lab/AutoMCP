/*
 * MCP REST Adapter - MCP Tool Generator Service
 * 
 * Service for generating MCP tool definitions from OpenAPI endpoints.
 * Converts parsed API endpoints into structured MCP tools with proper
 * JSON schemas, descriptions, and metadata for LLM consumption.
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpwrapper.adapter.openapi.OpenApiParserService.ApiEndpoint;
import com.mcpwrapper.adapter.openapi.OpenApiParserService.EndpointParameter;
import com.mcpwrapper.adapter.openapi.OpenApiParserService.ParsedApiSpecification;
import com.mcpwrapper.transport.mcp.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating MCP tool definitions from OpenAPI specifications.
 * 
 * This service converts parsed OpenAPI endpoints into MCP tools that can be
 * consumed by LLM agents. It handles:
 * - Tool naming and description generation
 * - JSON schema creation for input validation
 * - Metadata extraction and enrichment
 * - Tool categorization and tagging
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
public class McpToolGeneratorService {
    
    private static final Logger logger = LoggerFactory.getLogger(McpToolGeneratorService.class);
    
    private final ObjectMapper objectMapper;
    
    public McpToolGeneratorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Generates MCP tools from a parsed API specification.
     * 
     * @param apiSpec parsed API specification
     * @param config tool generation configuration
     * @return Flux of generated MCP tools
     */
    public Flux<McpTool> generateToolsFromSpec(ParsedApiSpecification apiSpec, ToolGenerationConfig config) {
        logger.debug("Generating MCP tools from API spec: {} ({} endpoints)", 
            apiSpec.title(), apiSpec.endpoints().size());
        
        return Flux.fromIterable(apiSpec.endpoints())
            .filter(endpoint -> shouldIncludeEndpoint(endpoint, config))
            .map(endpoint -> generateToolFromEndpoint(endpoint, apiSpec, config))
            .doOnNext(tool -> logger.debug("Generated MCP tool: {}", tool.name()))
            .doOnComplete(() -> logger.info("Generated {} MCP tools from API spec: {}", 
                apiSpec.endpoints().size(), apiSpec.title()));
    }
    
    /**
     * Generates a single MCP tool from an API endpoint.
     * 
     * @param endpoint API endpoint
     * @param apiSpec parent API specification
     * @param config generation configuration
     * @return generated MCP tool
     */
    public Mono<McpTool> generateToolFromEndpointAsync(ApiEndpoint endpoint, ParsedApiSpecification apiSpec, 
                                                       ToolGenerationConfig config) {
        logger.debug("Generating MCP tool for endpoint: {} {}", endpoint.httpMethod(), endpoint.path());
        
        return Mono.fromCallable(() -> generateToolFromEndpoint(endpoint, apiSpec, config));
    }
    
    /**
     * Validates and optimizes generated tools.
     * 
     * @param tools generated tools
     * @return Flux of validated and optimized tools
     */
    public Flux<McpTool> validateAndOptimizeTools(Flux<McpTool> tools) {
        return tools
            .map(this::validateTool)
            .map(this::optimizeTool)
            .doOnNext(tool -> logger.debug("Validated and optimized tool: {}", tool.name()));
    }
    
    // Private helper methods
    
    private McpTool generateToolFromEndpoint(ApiEndpoint endpoint, ParsedApiSpecification apiSpec, 
                                           ToolGenerationConfig config) {
        
        String toolName = generateToolName(endpoint, config);
        String description = generateToolDescription(endpoint, apiSpec, config);
        JsonNode inputSchema = generateInputSchema(endpoint, config);
        Map<String, Object> metadata = generateToolMetadata(endpoint, apiSpec, config);
        List<String> tags = generateToolTags(endpoint, apiSpec, config);
        
        return McpTool.builder()
            .name(toolName)
            .description(description)
            .inputSchema(inputSchema)
            .metadata(metadata)
            .tags(tags)
            .version("1.0.0")
            .deprecated(endpoint.deprecated())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
    
    private String generateToolName(ApiEndpoint endpoint, ToolGenerationConfig config) {
        String baseName = endpoint.operationId() != null && !endpoint.operationId().isEmpty()
            ? endpoint.operationId()
            : generateNameFromPath(endpoint);
        
        // Apply naming convention
        return switch (config.namingConvention()) {
            case OPERATION_ID -> baseName;
            case METHOD_PATH -> endpoint.httpMethod().toLowerCase() + "_" + 
                              endpoint.path().replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_");
            case DESCRIPTIVE -> generateDescriptiveName(endpoint);
            case PREFIXED -> config.namePrefix() + "_" + baseName;
        };
    }
    
    private String generateToolDescription(ApiEndpoint endpoint, ParsedApiSpecification apiSpec, 
                                         ToolGenerationConfig config) {
        StringBuilder description = new StringBuilder();
        
        // Start with endpoint summary or description
        if (endpoint.summary() != null && !endpoint.summary().isEmpty()) {
            description.append(endpoint.summary());
        } else if (endpoint.description() != null && !endpoint.description().isEmpty()) {
            description.append(endpoint.description());
        } else {
            description.append(generateDefaultDescription(endpoint));
        }
        
        // Add API context if configured
        if (config.includeApiContext()) {
            description.append("\n\nAPI: ").append(apiSpec.title());
            if (apiSpec.version() != null) {
                description.append(" (v").append(apiSpec.version()).append(")");
            }
        }
        
        // Add endpoint details
        description.append("\n\nEndpoint: ").append(endpoint.httpMethod().toUpperCase())
                  .append(" ").append(endpoint.path());
        
        // Add parameter information
        if (!endpoint.pathParameters().isEmpty() || !endpoint.queryParameters().isEmpty()) {
            description.append("\n\nParameters:");
            
            endpoint.pathParameters().forEach(param -> 
                description.append("\n- ").append(param.name()).append(" (path")
                          .append(param.required() ? ", required" : "")
                          .append("): ").append(param.description()));
            
            endpoint.queryParameters().forEach(param -> 
                description.append("\n- ").append(param.name()).append(" (query")
                          .append(param.required() ? ", required" : "")
                          .append("): ").append(param.description()));
        }
        
        // Add deprecation warning
        if (endpoint.deprecated()) {
            description.append("\n\n⚠️ This endpoint is deprecated and may be removed in future versions.");
        }
        
        return description.toString();
    }
    
    private JsonNode generateInputSchema(ApiEndpoint endpoint, ToolGenerationConfig config) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        
        // Add path parameters
        endpoint.pathParameters().forEach(param -> {
            ObjectNode paramSchema = properties.putObject(param.name());
            addParameterToSchema(param, paramSchema);
            if (param.required()) {
                required.add(param.name());
            }
        });
        
        // Add query parameters
        endpoint.queryParameters().forEach(param -> {
            ObjectNode paramSchema = properties.putObject(param.name());
            addParameterToSchema(param, paramSchema);
            if (param.required()) {
                required.add(param.name());
            }
        });
        
        // Add header parameters (if configured to include)
        if (config.includeHeaderParams()) {
            endpoint.headerParameters().forEach(param -> {
                ObjectNode paramSchema = properties.putObject("header_" + param.name());
                addParameterToSchema(param, paramSchema);
                if (param.required()) {
                    required.add("header_" + param.name());
                }
            });
        }
        
        // Add request body schema
        if (endpoint.requestBodySchema() != null) {
            ObjectNode bodySchema = properties.putObject("requestBody");
            bodySchema.setAll((ObjectNode) endpoint.requestBodySchema());
            required.add("requestBody");
        }
        
        // Add metadata fields
        if (config.includeMetadataFields()) {
            ObjectNode metaSchema = properties.putObject("_metadata");
            metaSchema.put("type", "object");
            metaSchema.put("description", "Optional metadata for request tracking");
            
            ObjectNode metaProps = metaSchema.putObject("properties");
            metaProps.putObject("requestId").put("type", "string").put("description", "Unique request identifier");
            metaProps.putObject("timeout").put("type", "integer").put("description", "Request timeout in seconds");
        }
        
        return schema;
    }
    
    private void addParameterToSchema(EndpointParameter param, ObjectNode schema) {
        schema.put("type", mapParameterType(param.type()));
        
        if (param.description() != null && !param.description().isEmpty()) {
            schema.put("description", param.description());
        }
        
        if (param.example() != null) {
            schema.set("example", objectMapper.valueToTree(param.example()));
        }
        
        // Add additional schema constraints if available
        if (param.schema() != null) {
            JsonNode paramSchema = param.schema();
            if (paramSchema.has("minimum")) {
                schema.set("minimum", paramSchema.get("minimum"));
            }
            if (paramSchema.has("maximum")) {
                schema.set("maximum", paramSchema.get("maximum"));
            }
            if (paramSchema.has("pattern")) {
                schema.set("pattern", paramSchema.get("pattern"));
            }
            if (paramSchema.has("enum")) {
                schema.set("enum", paramSchema.get("enum"));
            }
        }
    }
    
    private Map<String, Object> generateToolMetadata(ApiEndpoint endpoint, ParsedApiSpecification apiSpec, 
                                                   ToolGenerationConfig config) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Core endpoint information
        metadata.put("httpMethod", endpoint.httpMethod());
        metadata.put("endpointPath", endpoint.path());
        metadata.put("operationId", endpoint.operationId());
        
        // API information
        metadata.put("apiTitle", apiSpec.title());
        metadata.put("apiVersion", apiSpec.version());
        
        // Server information
        if (!apiSpec.servers().isEmpty()) {
            metadata.put("servers", apiSpec.servers());
        }
        
        // Tags and categorization
        if (!endpoint.tags().isEmpty()) {
            metadata.put("tags", endpoint.tags());
        }
        
        // Security requirements
        if (!endpoint.securityRequirements().isEmpty()) {
            metadata.put("securityRequirements", endpoint.securityRequirements());
        }
        
        // Response information
        if (!endpoint.responseSchemas().isEmpty()) {
            metadata.put("responseTypes", endpoint.responseSchemas().keySet());
        }
        
        // Generation metadata
        metadata.put("generatedAt", Instant.now().toString());
        metadata.put("generatorVersion", "1.0.0");
        
        // Custom metadata from config
        if (config.customMetadata() != null) {
            metadata.putAll(config.customMetadata());
        }
        
        return metadata;
    }
    
    private List<String> generateToolTags(ApiEndpoint endpoint, ParsedApiSpecification apiSpec, 
                                        ToolGenerationConfig config) {
        Set<String> tags = new HashSet<>();
        
        // Add endpoint tags
        tags.addAll(endpoint.tags());
        
        // Add HTTP method tag
        tags.add("http-" + endpoint.httpMethod().toLowerCase());
        
        // Add API tag
        tags.add("api-" + apiSpec.title().toLowerCase().replaceAll("[^a-z0-9]", "-"));
        
        // Add path-based tags
        String[] pathSegments = endpoint.path().split("/");
        for (String segment : pathSegments) {
            if (!segment.isEmpty() && !segment.startsWith("{")) {
                tags.add("path-" + segment.toLowerCase());
            }
        }
        
        // Add custom tags from config
        if (config.customTags() != null) {
            tags.addAll(config.customTags());
        }
        
        return new ArrayList<>(tags);
    }
    
    private boolean shouldIncludeEndpoint(ApiEndpoint endpoint, ToolGenerationConfig config) {
        // Skip deprecated endpoints if configured
        if (endpoint.deprecated() && config.skipDeprecated()) {
            return false;
        }
        
        // Filter by HTTP methods
        if (config.includedMethods() != null && 
            !config.includedMethods().contains(endpoint.httpMethod().toUpperCase())) {
            return false;
        }
        
        // Filter by tags
        if (config.includedTags() != null && !config.includedTags().isEmpty()) {
            boolean hasIncludedTag = endpoint.tags().stream()
                .anyMatch(tag -> config.includedTags().contains(tag));
            if (!hasIncludedTag) {
                return false;
            }
        }
        
        // Filter by path patterns
        if (config.pathPatterns() != null && !config.pathPatterns().isEmpty()) {
            boolean matchesPattern = config.pathPatterns().stream()
                .anyMatch(pattern -> endpoint.path().matches(pattern));
            if (!matchesPattern) {
                return false;
            }
        }
        
        return true;
    }
    
    private McpTool validateTool(McpTool tool) {
        // Perform validation and return the tool (or throw exception if invalid)
        if (tool.name() == null || tool.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be empty");
        }
        
        if (tool.description() == null || tool.description().trim().isEmpty()) {
            throw new IllegalArgumentException("Tool description cannot be empty");
        }
        
        if (tool.inputSchema() == null) {
            throw new IllegalArgumentException("Tool input schema cannot be null");
        }
        
        return tool;
    }
    
    private McpTool optimizeTool(McpTool tool) {
        // Perform optimizations like schema simplification, description cleanup, etc.
        return tool; // For now, return as-is
    }
    
    private String generateNameFromPath(ApiEndpoint endpoint) {
        return endpoint.httpMethod().toLowerCase() + 
               endpoint.path().replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_");
    }
    
    private String generateDescriptiveName(ApiEndpoint endpoint) {
        if (endpoint.summary() != null && !endpoint.summary().isEmpty()) {
            return endpoint.summary().toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", "_");
        }
        return generateNameFromPath(endpoint);
    }
    
    private String generateDefaultDescription(ApiEndpoint endpoint) {
        return String.format("Executes %s request to %s", 
            endpoint.httpMethod().toUpperCase(), endpoint.path());
    }
    
    private String mapParameterType(String openApiType) {
        return switch (openApiType != null ? openApiType.toLowerCase() : "string") {
            case "integer" -> "integer";
            case "number" -> "number";
            case "boolean" -> "boolean";
            case "array" -> "array";
            case "object" -> "object";
            default -> "string";
        };
    }
    
    // Configuration record
    
    public record ToolGenerationConfig(
        NamingConvention namingConvention,
        String namePrefix,
        boolean includeApiContext,
        boolean includeHeaderParams,
        boolean includeMetadataFields,
        boolean skipDeprecated,
        Set<String> includedMethods,
        Set<String> includedTags,
        Set<String> pathPatterns,
        Map<String, Object> customMetadata,
        Set<String> customTags
    ) {
        
        public static ToolGenerationConfig defaultConfig() {
            return new ToolGenerationConfig(
                NamingConvention.OPERATION_ID,
                null,
                true,
                false,
                true,
                false,
                null,
                null,
                null,
                null,
                null
            );
        }
        
        public static ToolGenerationConfig.Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private NamingConvention namingConvention = NamingConvention.OPERATION_ID;
            private String namePrefix;
            private boolean includeApiContext = true;
            private boolean includeHeaderParams = false;
            private boolean includeMetadataFields = true;
            private boolean skipDeprecated = false;
            private Set<String> includedMethods;
            private Set<String> includedTags;
            private Set<String> pathPatterns;
            private Map<String, Object> customMetadata;
            private Set<String> customTags;
            
            public Builder namingConvention(NamingConvention convention) {
                this.namingConvention = convention;
                return this;
            }
            
            public Builder namePrefix(String prefix) {
                this.namePrefix = prefix;
                return this;
            }
            
            public Builder includeApiContext(boolean include) {
                this.includeApiContext = include;
                return this;
            }
            
            public Builder includeHeaderParams(boolean include) {
                this.includeHeaderParams = include;
                return this;
            }
            
            public Builder includeMetadataFields(boolean include) {
                this.includeMetadataFields = include;
                return this;
            }
            
            public Builder skipDeprecated(boolean skip) {
                this.skipDeprecated = skip;
                return this;
            }
            
            public Builder includedMethods(Set<String> methods) {
                this.includedMethods = methods;
                return this;
            }
            
            public ToolGenerationConfig build() {
                return new ToolGenerationConfig(
                    namingConvention, namePrefix, includeApiContext, includeHeaderParams,
                    includeMetadataFields, skipDeprecated, includedMethods, includedTags,
                    pathPatterns, customMetadata, customTags
                );
            }
        }
    }
    
    public enum NamingConvention {
        OPERATION_ID,
        METHOD_PATH,
        DESCRIPTIVE,
        PREFIXED
    }
}
