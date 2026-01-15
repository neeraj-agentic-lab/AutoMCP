/*
 * MCP REST Adapter - Configuration Controller
 * 
 * REST API controller for managing API configurations.
 * Provides endpoints for CRUD operations on API configurations,
 * OpenAPI integration, and tool generation management.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.transport.controller;

import com.mcpwrapper.adapter.openapi.OpenApiIntegrationService;
import com.mcpwrapper.adapter.openapi.OpenApiParserService;
import com.mcpwrapper.platform.persistence.entity.ApiConfigurationEntity;
import com.mcpwrapper.runtime.service.YamlApiConfigurationService;
import com.mcpwrapper.runtime.service.McpServiceFacade;
import com.mcpwrapper.runtime.service.ReactiveConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * REST API controller for configuration management.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/configurations")
@Tag(name = "Configuration Management", description = "API configuration management operations")
public class ConfigurationController {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationController.class);
    
    private final McpServiceFacade serviceFacade;
    private final OpenApiIntegrationService integrationService;
    private final YamlApiConfigurationService yamlApiConfigurationService;
    
    public ConfigurationController(
            McpServiceFacade serviceFacade,
            OpenApiIntegrationService integrationService,
            YamlApiConfigurationService yamlApiConfigurationService) {
        this.serviceFacade = serviceFacade;
        this.integrationService = integrationService;
        this.yamlApiConfigurationService = yamlApiConfigurationService;
    }
    
    /**
     * Creates a new API configuration.
     */
    @PostMapping
    @Operation(
        summary = "Create API configuration",
        description = "Creates a new API configuration and optionally generates tools",
        responses = {
            @ApiResponse(responseCode = "201", description = "Configuration created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid configuration data"),
            @ApiResponse(responseCode = "409", description = "Configuration already exists")
        }
    )
    public Mono<ResponseEntity<ConfigurationResponse>> createConfiguration(
            @Parameter(description = "Configuration creation request")
            @Valid @RequestBody CreateConfigurationRequest request) {
        
        logger.info("Creating new API configuration: {}", request.name());
        
        ReactiveConfigurationService.CreateConfigurationRequest serviceRequest = 
            new ReactiveConfigurationService.CreateConfigurationRequest(
                request.name(),
                request.description(),
                request.enabled(),
                request.openapiSourceType(),
                request.openapiUrl(),
                request.openapiContent(),
                request.baseUrl(),
                request.timeoutSeconds(),
                request.rateLimitPerMinute(),
                request.authConfig(),
                request.advancedConfig(),
                request.createdBy()
            );
        
        return serviceFacade.createConfiguration(serviceRequest)
            .flatMap(config -> {
                if (request.generateTools()) {
                    return integrationService.processApiConfiguration(config.id())
                        .map(result -> new ConfigurationResponse(config, result.generatedTools().size(), result.errors()));
                } else {
                    return Mono.just(new ConfigurationResponse(config, 0, List.of()));
                }
            })
            .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
            .doOnSuccess(response -> logger.info("Created configuration: {} with {} tools", 
                request.name(), response.getBody().toolCount()))
            .doOnError(error -> logger.error("Failed to create configuration: {}", request.name(), error));
    }
    
    /**
     * Lists all API configurations.
     */
    @GetMapping
    @Operation(
        summary = "List API configurations",
        description = "Returns a list of all API configurations with optional filtering",
        responses = {
            @ApiResponse(responseCode = "200", description = "Configurations retrieved successfully")
        }
    )
    public Flux<ApiConfigurationEntity> listConfigurations(
            @Parameter(description = "Filter by enabled status")
            @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "Filter by creator")
            @RequestParam(required = false) String createdBy) {
        
        logger.debug("Listing configurations (enabled: {}, createdBy: {})", enabled, createdBy);
        
        if (enabled != null && enabled) {
            return serviceFacade.getEnabledConfigurations();
        }
        
        // TODO: Add filtering by createdBy
        return serviceFacade.getEnabledConfigurations(); // For now, return enabled only
    }
    
    /**
     * Gets a specific API configuration.
     */
    @GetMapping("/{configId}")
    @Operation(
        summary = "Get API configuration",
        description = "Returns detailed information about a specific API configuration",
        responses = {
            @ApiResponse(responseCode = "200", description = "Configuration found"),
            @ApiResponse(responseCode = "404", description = "Configuration not found")
        }
    )
    public Mono<ResponseEntity<ApiConfigurationEntity>> getConfiguration(
            @Parameter(description = "Configuration ID")
            @PathVariable UUID configId) {
        
        logger.debug("Getting configuration: {}", configId);
        
        return serviceFacade.getConfiguration(configId)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Updates an API configuration.
     */
    @PutMapping("/{configId}")
    @Operation(
        summary = "Update API configuration",
        description = "Updates an existing API configuration and optionally regenerates tools",
        responses = {
            @ApiResponse(responseCode = "200", description = "Configuration updated successfully"),
            @ApiResponse(responseCode = "404", description = "Configuration not found"),
            @ApiResponse(responseCode = "400", description = "Invalid configuration data")
        }
    )
    public Mono<ResponseEntity<ConfigurationResponse>> updateConfiguration(
            @Parameter(description = "Configuration ID")
            @PathVariable UUID configId,
            @Parameter(description = "Configuration update request")
            @Valid @RequestBody UpdateConfigurationRequest request) {
        
        logger.info("Updating configuration: {}", configId);
        
        ReactiveConfigurationService.UpdateConfigurationRequest serviceRequest = 
            new ReactiveConfigurationService.UpdateConfigurationRequest(
                request.description(),
                request.enabled(),
                request.openapiSourceType(),
                request.openapiUrl(),
                request.openapiContent(),
                request.baseUrl(),
                request.timeoutSeconds(),
                request.rateLimitPerMinute(),
                request.authConfig(),
                request.advancedConfig(),
                request.updatedBy()
            );
        
        return serviceFacade.updateConfiguration(configId, serviceRequest)
            .flatMap(config -> {
                if (request.regenerateTools()) {
                    return integrationService.refreshApiTools(configId)
                        .map(result -> new ConfigurationResponse(config, result.toolCount(), 
                            result.success() ? List.of() : result.errors()));
                } else {
                    return Mono.just(new ConfigurationResponse(config, 0, List.of()));
                }
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnSuccess(response -> logger.info("Updated configuration: {}", configId));
    }
    
    /**
     * Deletes an API configuration.
     */
    @DeleteMapping("/{configId}")
    @Operation(
        summary = "Delete API configuration",
        description = "Deletes an API configuration and all associated tools",
        responses = {
            @ApiResponse(responseCode = "204", description = "Configuration deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Configuration not found")
        }
    )
    public Mono<ResponseEntity<Void>> deleteConfiguration(
            @Parameter(description = "Configuration ID")
            @PathVariable UUID configId,
            @Parameter(description = "User performing the deletion")
            @RequestParam String deletedBy) {
        
        logger.info("Deleting configuration: {} by user: {}", configId, deletedBy);
        
        return serviceFacade.deleteConfiguration(configId, deletedBy)
            .then(Mono.just(ResponseEntity.noContent().<Void>build()))
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnSuccess(response -> logger.info("Deleted configuration: {}", configId));
    }
    
    /**
     * Generates tools from an API configuration.
     */
    @PostMapping("/{configId}/generate-tools")
    @Operation(
        summary = "Generate tools from configuration",
        description = "Processes the API configuration and generates MCP tools",
        responses = {
            @ApiResponse(responseCode = "200", description = "Tools generated successfully"),
            @ApiResponse(responseCode = "404", description = "Configuration not found"),
            @ApiResponse(responseCode = "400", description = "Tool generation failed")
        }
    )
    public Mono<ResponseEntity<ToolGenerationResponse>> generateTools(
            @Parameter(description = "Configuration ID")
            @PathVariable UUID configId) {
        
        System.out.println("=== SYSTEM.OUT: Controller generateTools called for: " + configId);
        logger.info("Generating tools for configuration: {}", configId);
        
        System.out.println("=== SYSTEM.OUT: About to call integrationService.processApiConfiguration");
        logger.info("=== CONTROLLER: About to call processApiConfiguration for {}", configId);
        
        // Force fresh execution by clearing any potential caches
        return integrationService.processApiConfiguration(configId)
            .doOnNext(result -> {
                logger.info("=== CONTROLLER RESULT: Got {} tools for config {}", result.generatedTools().size(), configId);
                System.out.println("=== SYSTEM.OUT CONTROLLER RESULT: " + result.generatedTools().size() + " tools for " + configId);
            })
            .map(result -> ResponseEntity.ok(new ToolGenerationResponse(
                result.configurationId(),
                result.apiTitle(),
                result.generatedTools().size(),
                result.errors(),
                result.generatedAt()
            )))
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnSuccess(response -> {
                if (response.hasBody()) {
                    logger.info("Generated {} tools for configuration: {}", 
                        response.getBody().toolCount(), configId);
                }
            });
    }

    @PostMapping("/{configId}/refresh-yaml")
    @Operation(
        summary = "Refresh cached YAML for configuration",
        description = "Invalidates the cached YAML (loaded from URL) for this API configuration so it will be re-fetched on next use",
        responses = {
            @ApiResponse(responseCode = "204", description = "YAML cache invalidated"),
            @ApiResponse(responseCode = "404", description = "Configuration not found")
        }
    )
    public Mono<ResponseEntity<Void>> refreshYaml(
        @Parameter(description = "Configuration ID")
        @PathVariable UUID configId
    ) {
        return serviceFacade.getConfiguration(configId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Configuration not found: " + configId)))
            .flatMap(ignored -> yamlApiConfigurationService.invalidate(configId))
            .thenReturn(ResponseEntity.noContent().<Void>build())
            .onErrorResume(IllegalArgumentException.class, error -> Mono.just(ResponseEntity.<Void>status(HttpStatus.NOT_FOUND).build()));
    }

    /**
     * Refreshes tools for an API configuration.
     */
    @PostMapping("/{configId}/refresh-tools")
    @Operation(
        summary = "Refresh tools for configuration",
        description = "Re-parses the API specification and updates tools",
        responses = {
            @ApiResponse(responseCode = "200", description = "Tools refreshed successfully"),
            @ApiResponse(responseCode = "404", description = "Configuration not found")
        }
    )
    public Mono<ResponseEntity<ToolRefreshResponse>> refreshTools(
            @Parameter(description = "Configuration ID")
            @PathVariable UUID configId) {
        
        logger.info("Refreshing tools for configuration: {}", configId);
        
        return integrationService.refreshApiTools(configId)
            .map(result -> ResponseEntity.ok(new ToolRefreshResponse(
                result.configurationId(),
                result.success(),
                result.toolCount(),
                result.errors(),
                result.refreshedAt()
            )))
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnSuccess(response -> {
                if (response.hasBody()) {
                    logger.info("Refreshed {} tools for configuration: {}", 
                        response.getBody().toolCount(), configId);
                }
            });
    }
    
    /**
     * Validates an OpenAPI specification.
     */
    @PostMapping("/validate-openapi")
    @Operation(
        summary = "Validate OpenAPI specification",
        description = "Validates an OpenAPI specification without creating a configuration",
        responses = {
            @ApiResponse(responseCode = "200", description = "Validation completed")
        }
    )
    public Mono<OpenApiValidationResponse> validateOpenApi(
            @Parameter(description = "OpenAPI validation request")
            @Valid @RequestBody OpenApiValidationRequest request) {
        
        logger.debug("Validating OpenAPI specification from: {}", request.sourceType());
        
        OpenApiParserService.OpenApiParseRequest parseRequest = 
            new OpenApiParserService.OpenApiParseRequest(
                OpenApiParserService.SourceType.valueOf(request.sourceType().toUpperCase()),
                request.source()
            );
        
        return integrationService.validateApiSpecification(parseRequest)
            .map(result -> new OpenApiValidationResponse(
                result.isValid(),
                result.errors(),
                result.warnings(),
                result.estimatedToolCount(),
                result.specification() != null ? result.specification().title() : null
            ));
    }
    
    /**
     * Bulk processes multiple configurations.
     */
    @PostMapping("/bulk-process")
    @Operation(
        summary = "Bulk process configurations",
        description = "Processes multiple API configurations in batch",
        responses = {
            @ApiResponse(responseCode = "200", description = "Bulk processing completed")
        }
    )
    public Mono<BulkProcessResponse> bulkProcess(
            @Parameter(description = "Bulk process request")
            @Valid @RequestBody BulkProcessRequest request) {
        
        logger.info("Starting bulk processing of {} configurations", request.configurationIds().size());
        
        OpenApiIntegrationService.BulkImportRequest importRequest = 
            new OpenApiIntegrationService.BulkImportRequest(
                request.configurationIds(),
                request.concurrency()
            );
        
        return integrationService.bulkImportTools(importRequest)
            .map(result -> new BulkProcessResponse(
                result.results().size(),
                result.totalToolsImported(),
                result.failureCount(),
                result.completedAt()
            ));
    }
    
    // Request/Response DTOs
    
    public record CreateConfigurationRequest(
        String name,
        String description,
        Boolean enabled,
        String openapiSourceType,
        String openapiUrl,
        String openapiContent,
        String baseUrl,
        Integer timeoutSeconds,
        Integer rateLimitPerMinute,
        com.fasterxml.jackson.databind.JsonNode authConfig,
        com.fasterxml.jackson.databind.JsonNode advancedConfig,
        String createdBy,
        boolean generateTools
    ) {}
    
    public record UpdateConfigurationRequest(
        String description,
        Boolean enabled,
        String openapiSourceType,
        String openapiUrl,
        String openapiContent,
        String baseUrl,
        Integer timeoutSeconds,
        Integer rateLimitPerMinute,
        com.fasterxml.jackson.databind.JsonNode authConfig,
        com.fasterxml.jackson.databind.JsonNode advancedConfig,
        String updatedBy,
        boolean regenerateTools
    ) {}
    
    public record ConfigurationResponse(
        ApiConfigurationEntity configuration,
        int toolCount,
        List<String> errors
    ) {}
    
    public record ToolGenerationResponse(
        UUID configurationId,
        String apiTitle,
        int toolCount,
        List<String> errors,
        java.time.Instant generatedAt
    ) {}
    
    public record ToolRefreshResponse(
        UUID configurationId,
        boolean success,
        int toolCount,
        List<String> errors,
        java.time.Instant refreshedAt
    ) {}
    
    public record OpenApiValidationRequest(
        String sourceType,
        String source
    ) {}
    
    public record OpenApiValidationResponse(
        boolean isValid,
        List<String> errors,
        List<String> warnings,
        int estimatedToolCount,
        String apiTitle
    ) {}
    
    public record BulkProcessRequest(
        List<UUID> configurationIds,
        int concurrency
    ) {}
    
    public record BulkProcessResponse(
        int configurationsProcessed,
        int totalToolsGenerated,
        int failureCount,
        java.time.Instant completedAt
    ) {}
}
