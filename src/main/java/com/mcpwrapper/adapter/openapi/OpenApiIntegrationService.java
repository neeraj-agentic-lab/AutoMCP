/*
 * MCP REST Adapter - OpenAPI Integration Service
 * 
 * Orchestrator service that combines OpenAPI parsing and MCP tool generation.
 * Provides high-level operations for converting API configurations into
 * working MCP tools with full lifecycle management.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.adapter.openapi;

import com.mcpwrapper.adapter.openapi.McpToolGeneratorService.ToolGenerationConfig;
import com.mcpwrapper.adapter.openapi.OpenApiParserService.OpenApiParseRequest;
import com.mcpwrapper.adapter.openapi.OpenApiParserService.ParsedApiSpecification;
import com.mcpwrapper.platform.persistence.entity.ApiConfigurationEntity;
import com.mcpwrapper.runtime.service.ReactiveConfigurationService;
import com.mcpwrapper.runtime.service.ReactiveToolRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpwrapper.transport.mcp.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for integrating OpenAPI specifications with MCP tool generation.
 * 
 * This service orchestrates the complete workflow from API configuration
 * to working MCP tools:
 * 1. Parse OpenAPI specifications from various sources
 * 2. Generate MCP tool definitions from endpoints
 * 3. Register tools in the tool registry
 * 4. Handle tool lifecycle and updates
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
public class OpenApiIntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenApiIntegrationService.class);
    
    private final OpenApiParserService parserService;
    private final McpToolGeneratorService generatorService;
    private final ReactiveConfigurationService configurationService;
    private final ReactiveToolRegistryService toolRegistryService;
    private final CacheManager cacheManager;
    
    public OpenApiIntegrationService(
            OpenApiParserService parserService,
            McpToolGeneratorService generatorService,
            ReactiveConfigurationService configurationService,
            ReactiveToolRegistryService toolRegistryService,
            CacheManager cacheManager) {
        this.parserService = parserService;
        this.generatorService = generatorService;
        this.configurationService = configurationService;
        this.toolRegistryService = toolRegistryService;
        this.cacheManager = cacheManager;
    }
    
    /**
     * Processes an API configuration and generates MCP tools.
     * 
     * @param configId API configuration ID
     * @return Mono containing processing result
     */
    public Mono<ToolGenerationResult> processApiConfiguration(UUID configId) {
        // Delegate to the "fresh" chain to ensure the full reactive workflow executes
        // and emits the detailed STEP logs.
        return processApiConfigurationFresh(configId);
    }
    
    private Mono<ToolGenerationResult> processApiConfigurationFresh(UUID configId) {
        logger.info("=== ULTIMATE FIX: Fresh execution bypassing ALL caching mechanisms");
        System.out.println("=== SYSTEM.OUT: ULTIMATE FIX - Fresh execution");
        
        // Clear all caches to ensure fresh execution
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(cacheName -> {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                    logger.info("=== CLEARED CACHE: {}", cacheName);
                    System.out.println("=== SYSTEM.OUT: CLEARED CACHE: " + cacheName);
                }
            });
        }
        
        // Force fresh execution by creating a new reactive chain each time
        return Mono.defer(() -> {
            logger.info("=== STEP 1: Starting fresh configuration retrieval");
            System.out.println("=== SYSTEM.OUT STEP 1: Starting fresh configuration retrieval");
            
            return configurationService.getConfiguration(configId);
        })
        .doOnNext(config -> {
            logger.info("=== STEP 2: CONFIG RETRIEVED: {} with content length {}", 
                config.name(), config.openapiContent() != null ? config.openapiContent().length() : 0);
            System.out.println("=== SYSTEM.OUT STEP 2: CONFIG RETRIEVED: " + config.name() + " content: " + 
                (config.openapiContent() != null ? config.openapiContent().length() + " chars" : "null"));
        })
        .switchIfEmpty(Mono.defer(() -> {
            logger.error("=== CONFIG NOT FOUND: {}", configId);
            System.out.println("=== SYSTEM.OUT: CONFIG NOT FOUND: " + configId);
            return Mono.error(new RuntimeException("Configuration not found: " + configId));
        }))
        .flatMap(config -> {
            logger.info("=== STEP 3: PARSING CONFIG: Starting OpenAPI parsing for {}", config.name());
            System.out.println("=== SYSTEM.OUT STEP 3: PARSING CONFIG: Starting for " + config.name());
            
            return Mono.defer(() -> parseApiSpecification(config))
                .doOnNext(spec -> {
                    logger.info("=== STEP 4: SPEC PARSED: {} with {} endpoints", spec.title(), spec.endpoints().size());
                    System.out.println("=== SYSTEM.OUT STEP 4: SPEC PARSED: " + spec.title() + " with " + spec.endpoints().size() + " endpoints");
                    
                    // Log each endpoint for verification
                    if (spec.endpoints().isEmpty()) {
                        logger.warn("=== NO ENDPOINTS DETECTED");
                        System.out.println("=== SYSTEM.OUT: NO ENDPOINTS DETECTED");
                    } else {
                        spec.endpoints().forEach(endpoint -> {
                            logger.info("=== ENDPOINT FOUND: {} {} - {}", endpoint.httpMethod(), endpoint.path(), endpoint.operationId());
                            System.out.println("=== SYSTEM.OUT ENDPOINT: " + endpoint.httpMethod() + " " + endpoint.path() + " - " + endpoint.operationId());
                        });
                    }
                });
        })
        .flatMap(spec -> {
            logger.info("=== STEP 5: PROCESSING ENDPOINTS: Found {} endpoints", spec.endpoints().size());
            System.out.println("=== SYSTEM.OUT STEP 5: PROCESSING ENDPOINTS: Found " + spec.endpoints().size() + " endpoints");
            
            if (spec.endpoints().isEmpty()) {
                logger.warn("=== NO ENDPOINTS: Returning empty result");
                System.out.println("=== SYSTEM.OUT: No endpoints to process");
                return Mono.just(new ToolGenerationResult(configId, spec.title(), List.of(), 
                    List.of("No endpoints found in OpenAPI spec"), Instant.now()));
            }
            
            logger.info("=== STEP 6: GENERATING TOOLS: Processing {} endpoints", spec.endpoints().size());
            System.out.println("=== SYSTEM.OUT STEP 6: GENERATING TOOLS: Processing " + spec.endpoints().size() + " endpoints");
            
            return Mono.defer(() -> generateAndRegisterTools(spec, configId))
                .doOnNext(result -> {
                    logger.info("=== STEP 7: TOOLS GENERATED: {} tools created", result.generatedTools().size());
                    System.out.println("=== SYSTEM.OUT STEP 7: TOOLS GENERATED: " + result.generatedTools().size() + " tools");
                    
                    // Log each generated tool
                    result.generatedTools().forEach(tool -> {
                        logger.info("=== TOOL CREATED: {}", tool.name());
                        System.out.println("=== SYSTEM.OUT TOOL CREATED: " + tool.name());
                    });
                });
        })
        .doOnError(error -> {
            logger.error("=== PROCESSING ERROR: {}", error.getMessage(), error);
            System.out.println("=== SYSTEM.OUT ERROR: " + error.getMessage());
            System.out.println("=== SYSTEM.OUT ERROR CLASS: " + error.getClass().getName());
            error.printStackTrace();
        })
        .onErrorReturn(new ToolGenerationResult(configId, "ERROR", List.of(), 
            List.of("Processing failed: " + configId), Instant.now()));
        
    }
    
    /**
     * Processes multiple API configurations in batch.
     * 
     * @param configIds list of configuration IDs
     * @return Flux of processing results
     */
    public Flux<ToolGenerationResult> processApiConfigurations(List<UUID> configIds) {
        logger.info("Processing {} API configurations in batch", configIds.size());
        
        return Flux.fromIterable(configIds)
            .flatMap(this::processApiConfiguration, 3) // Process 3 at a time
            .doOnComplete(() -> logger.info("Completed batch processing of {} configurations", configIds.size()));
    }
    
    /**
     * Refreshes tools for an API configuration (re-parses and updates).
     * 
     * @param configId API configuration ID
     * @return Mono containing refresh result
     */
    public Mono<ToolRefreshResult> refreshApiTools(UUID configId) {
        logger.info("Refreshing tools for API configuration: {}", configId);
        
        return configurationService.getConfiguration(configId)
            .flatMap(config -> {
                // Remove existing tools for this configuration
                return removeExistingTools(configId)
                    .then(parseApiSpecification(config))
                    .flatMap(spec -> generateAndRegisterTools(spec, configId))
                    .map(result -> new ToolRefreshResult(
                        configId,
                        true,
                        result.generatedTools().size(),
                        result.errors(),
                        Instant.now()
                    ));
            })
            .onErrorReturn(new ToolRefreshResult(configId, false, 0, List.of("Refresh failed"), Instant.now()))
            .doOnSuccess(result -> logger.info("Refreshed tools for configuration {}: {} tools", 
                configId, result.toolCount()));
    }
    
    /**
     * Validates an OpenAPI specification without generating tools.
     * 
     * @param request parse request
     * @return Mono containing validation result
     */
    public Mono<ApiValidationResult> validateApiSpecification(OpenApiParseRequest request) {
        logger.debug("Validating OpenAPI specification from: {}", request.sourceType());
        
        return parserService.parseOpenApiSpec(request)
            .flatMap(spec -> parserService.validateOpenApiSpec(null) // TODO: Pass actual OpenAPI object
                .map(validation -> new ApiValidationResult(
                    true,
                    spec,
                    validation.errors(),
                    validation.warnings(),
                    estimateToolCount(spec)
                )))
            .onErrorReturn(new ApiValidationResult(
                false,
                null,
                List.of("Validation failed"),
                List.of(),
                0
            ));
    }
    
    /**
     * Gets tool generation preview without actually creating tools.
     * 
     * @param configId API configuration ID
     * @param config generation configuration
     * @return Flux of tool previews
     */
    public Flux<ToolPreview> previewToolGeneration(UUID configId, ToolGenerationConfig config) {
        logger.debug("Generating tool preview for configuration: {}", configId);
        
        return configurationService.getConfiguration(configId)
            .flatMap(this::parseApiSpecification)
            .flatMapMany(spec -> generatorService.generateToolsFromSpec(spec, config)
                .map(tool -> new ToolPreview(
                    tool.name(),
                    tool.description(),
                    extractEndpointInfo(tool),
                    tool.tags(),
                    tool.deprecated()
                )));
    }
    
    /**
     * Bulk imports tools from multiple API configurations.
     * 
     * @param request bulk import request
     * @return Mono containing import result
     */
    public Mono<BulkImportResult> bulkImportTools(BulkImportRequest request) {
        logger.info("Starting bulk import of {} configurations", request.configurationIds().size());
        
        return Flux.fromIterable(request.configurationIds())
            .flatMap(configId -> processApiConfiguration(configId)
                .map(result -> new ConfigurationImportResult(configId, true, result.generatedTools().size(), null))
                .onErrorReturn(new ConfigurationImportResult(configId, false, 0, "Import failed")), 
                request.concurrency())
            .collectList()
            .map(results -> new BulkImportResult(
                results,
                results.stream().mapToInt(ConfigurationImportResult::toolCount).sum(),
                results.stream().mapToInt(r -> r.success() ? 0 : 1).sum(),
                Instant.now()
            ))
            .doOnSuccess(result -> logger.info("Bulk import completed: {} tools imported, {} failures", 
                result.totalToolsImported(), result.failureCount()));
    }
    
    /**
     * Schedules automatic refresh for stale API configurations.
     * 
     * @param maxAge maximum age before considering configuration stale
     * @return Mono containing refresh summary
     */
    public Mono<RefreshSummary> refreshStaleConfigurations(Duration maxAge) {
        logger.info("Refreshing stale configurations older than: {}", maxAge);
        
        Instant threshold = Instant.now().minus(maxAge);
        
        return configurationService.getAllConfigurations(true) // enabled only
            .filter(config -> config.updatedAt().isBefore(threshold))
            .flatMap(config -> refreshApiTools(config.id())
                .onErrorReturn(new ToolRefreshResult(config.id(), false, 0, List.of("Refresh failed"), Instant.now())))
            .collectList()
            .map(results -> new RefreshSummary(
                results.size(),
                (int) results.stream().filter(ToolRefreshResult::success).count(),
                results.stream().mapToInt(ToolRefreshResult::toolCount).sum(),
                Instant.now()
            ));
    }
    
    // Private helper methods
    
    private Mono<ParsedApiSpecification> parseApiSpecification(ApiConfigurationEntity config) {
        OpenApiParseRequest request = new OpenApiParseRequest(
            mapSourceType(config.openapiSourceType()),
            getSpecificationSource(config)
        );
        
        return parserService.parseOpenApiSpec(request);
    }
    
    private Mono<ToolGenerationResult> generateAndRegisterTools(ParsedApiSpecification spec, UUID configId) {
        logger.info("Generating tools from specification: {} with {} endpoints", spec.title(), spec.endpoints().size());
        
        if (spec.endpoints().isEmpty()) {
            return Mono.just(new ToolGenerationResult(
                configId,
                spec.title(),
                List.of(),
                List.of("No endpoints found in OpenAPI specification"),
                Instant.now()
            ));
        }
        
        ToolGenerationConfig config = createGenerationConfig(configId);
        
        return generatorService.generateToolsFromSpec(spec, config)
            .flatMap(tool -> {
                McpTool enrichedTool = enrichToolWithConfig(tool, configId);

                // Register tool in database.
                // If registration fails, don't count it as generated.
                return toolRegistryService.registerTool(enrichedTool)
                    .doOnSuccess(registeredTool -> logger.debug("Successfully registered tool: {}", tool.name()))
                    .doOnError(error -> logger.warn("Failed to register tool {}: {}", tool.name(), error.getMessage()))
                    .onErrorResume(error -> Mono.empty());
            })
            .collectList()
            .map(registeredTools -> new ToolGenerationResult(
                configId,
                spec.title(),
                registeredTools,
                List.of(),
                Instant.now()
            ));
    }
    
    private Mono<Void> removeExistingTools(UUID configId) {
        // TODO: Implement removal of existing tools for the configuration
        return Mono.empty();
    }
    
    private ToolGenerationConfig createGenerationConfig(UUID configId) {
        return ToolGenerationConfig.builder()
            .namingConvention(McpToolGeneratorService.NamingConvention.OPERATION_ID)
            .includeApiContext(true)
            .includeMetadataFields(true)
            .skipDeprecated(false)
            .build();
    }
    
    private McpTool enrichToolWithConfig(McpTool tool, UUID configId) {
        Map<String, Object> enrichedMetadata = new java.util.HashMap<>(tool.metadata() != null ? tool.metadata() : Map.of());
        enrichedMetadata.put("apiConfigId", configId.toString());
        enrichedMetadata.put("generatedBy", "OpenApiIntegrationService");
        
        return McpTool.builder()
            .name(tool.name())
            .description(tool.description())
            .inputSchema(tool.inputSchema())
            .metadata(enrichedMetadata)
            .tags(tool.tags())
            .version(tool.version())
            .deprecated(tool.deprecated())
            .createdAt(tool.createdAt())
            .updatedAt(Instant.now())
            .build();
    }
    
    private OpenApiParserService.SourceType mapSourceType(String sourceType) {
        return switch (sourceType.toUpperCase()) {
            case "URL" -> OpenApiParserService.SourceType.URL;
            case "FILE" -> OpenApiParserService.SourceType.FILE;
            case "TEXT" -> OpenApiParserService.SourceType.TEXT;
            default -> throw new IllegalArgumentException("Unknown source type: " + sourceType);
        };
    }
    
    private String getSpecificationSource(ApiConfigurationEntity config) {
        return switch (config.openapiSourceType().toUpperCase()) {
            case "URL" -> config.openapiUrl();
            case "FILE", "TEXT" -> config.openapiContent();
            default -> throw new IllegalArgumentException("Invalid source type: " + config.openapiSourceType());
        };
    }
    
    private int estimateToolCount(ParsedApiSpecification spec) {
        return spec.endpoints().size();
    }
    
    private String extractEndpointInfo(McpTool tool) {
        if (tool.metadata() != null) {
            Object method = tool.metadata().get("httpMethod");
            Object path = tool.metadata().get("endpointPath");
            if (method != null && path != null) {
                return method + " " + path;
            }
        }
        return "Unknown endpoint";
    }
    
    // Request/Response records
    
    public record ToolGenerationResult(
        UUID configurationId,
        String apiTitle,
        List<McpTool> generatedTools,
        List<String> errors,
        Instant generatedAt
    ) {}
    
    public record ToolRefreshResult(
        UUID configurationId,
        boolean success,
        int toolCount,
        List<String> errors,
        Instant refreshedAt
    ) {}
    
    public record ApiValidationResult(
        boolean isValid,
        ParsedApiSpecification specification,
        List<String> errors,
        List<String> warnings,
        int estimatedToolCount
    ) {}
    
    public record ToolPreview(
        String name,
        String description,
        String endpoint,
        List<String> tags,
        boolean deprecated
    ) {}
    
    public record BulkImportRequest(
        List<UUID> configurationIds,
        int concurrency
    ) {
        public BulkImportRequest(List<UUID> configurationIds) {
            this(configurationIds, 3);
        }
    }
    
    public record ConfigurationImportResult(
        UUID configurationId,
        boolean success,
        int toolCount,
        String error
    ) {}
    
    public record BulkImportResult(
        List<ConfigurationImportResult> results,
        int totalToolsImported,
        int failureCount,
        Instant completedAt
    ) {}
    
    public record RefreshSummary(
        int configurationsProcessed,
        int successfulRefreshes,
        int totalToolsRefreshed,
        Instant completedAt
    ) {}
}
