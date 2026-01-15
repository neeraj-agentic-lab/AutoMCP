/*
 * MCP REST Adapter - Reactive Tool Registry Service
 * 
 * Implementation of the ToolRegistry interface with reactive streams and caching.
 * Provides high-performance tool management with in-memory caching backed by
 * database persistence for reliability and consistency.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpwrapper.platform.persistence.entity.ToolDefinitionEntity;
import com.mcpwrapper.platform.persistence.repository.ReactiveToolDefinitionRepository;
import com.mcpwrapper.runtime.registry.ToolRegistry;
import com.mcpwrapper.transport.mcp.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Reactive implementation of ToolRegistry with caching and persistence.
 * 
 * This service provides:
 * - In-memory caching for high-performance tool access
 * - Database persistence for reliability
 * - Reactive streams for non-blocking operations
 * - Tool validation and conflict resolution
 * - Metadata indexing and search capabilities
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
public class ReactiveToolRegistryService implements ToolRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(ReactiveToolRegistryService.class);
    
    private final ReactiveToolDefinitionRepository toolRepository;
    private final ObjectMapper objectMapper;
    
    // In-memory cache for high-performance access
    private final Map<String, McpTool> toolCache = new ConcurrentHashMap<>();
    private final Map<String, String> metadataIndex = new ConcurrentHashMap<>();
    
    public ReactiveToolRegistryService(
            ReactiveToolDefinitionRepository toolRepository,
            ObjectMapper objectMapper) {
        this.toolRepository = toolRepository;
        this.objectMapper = objectMapper;
        
        // Initialize cache on startup
        initializeCache();
    }
    
    @Override
    @CacheEvict(value = "tools", key = "#tool.name")
    public Mono<McpTool> registerTool(McpTool tool) {
        logger.debug("Registering tool: {}", tool.name());
        
        return validateTool(tool)
            .then(persistTool(tool))
            .doOnSuccess(registeredTool -> {
                toolCache.put(tool.name(), registeredTool);
                updateMetadataIndex(registeredTool);
                logger.info("Registered tool: {}", registeredTool.name());
            })
            .doOnError(error -> logger.error("Failed to register tool: {}", tool.name(), error));
    }
    
    @Override
    @CacheEvict(value = "tools", key = "#tool.name")
    public Mono<McpTool> updateTool(McpTool tool) {
        logger.debug("Updating tool: {}", tool.name());
        
        return validateTool(tool)
            .then(toolExists(tool.name()))
            .filter(exists -> exists)
            .switchIfEmpty(Mono.error(new ToolNotFoundException("Tool not found: " + tool.name())))
            .then(persistTool(tool))
            .doOnSuccess(updatedTool -> {
                toolCache.put(tool.name(), updatedTool);
                updateMetadataIndex(updatedTool);
                logger.info("Updated tool: {}", updatedTool.name());
            })
            .doOnError(error -> logger.error("Failed to update tool: {}", tool.name(), error));
    }
    
    @Override
    @CacheEvict(value = "tools", key = "#toolName")
    public Mono<Boolean> unregisterTool(String toolName) {
        logger.debug("Unregistering tool: {}", toolName);
        
        return toolRepository.findByToolName(toolName)
            .flatMap(entity -> toolRepository.deleteById(entity.id())
                .thenReturn(true))
            .defaultIfEmpty(false)
            .doOnSuccess(deleted -> {
                if (deleted) {
                    toolCache.remove(toolName);
                    removeFromMetadataIndex(toolName);
                    logger.info("Unregistered tool: {}", toolName);
                } else {
                    logger.warn("Tool not found for unregistration: {}", toolName);
                }
            })
            .doOnError(error -> logger.error("Failed to unregister tool: {}", toolName, error));
    }
    
    @Override
    @Cacheable(value = "tools", key = "#toolName")
    public Mono<McpTool> getTool(String toolName) {
        logger.debug("Retrieving tool: {}", toolName);
        
        // Check cache first
        McpTool cachedTool = toolCache.get(toolName);
        if (cachedTool != null) {
            return Mono.just(cachedTool);
        }
        
        // Fallback to database
        return toolRepository.findByToolName(toolName)
            .map(this::entityToTool)
            .doOnSuccess(tool -> {
                if (tool != null) {
                    toolCache.put(toolName, tool);
                    logger.debug("Retrieved and cached tool: {}", toolName);
                }
            })
            .switchIfEmpty(Mono.error(new ToolNotFoundException("Tool not found: " + toolName)));
    }
    
    @Override
    public Flux<McpTool> getAllTools() {
        logger.debug("Retrieving all tools");
        
        // Return cached tools if available
        if (!toolCache.isEmpty()) {
            return Flux.fromIterable(toolCache.values());
        }
        
        // Fallback to database
        return toolRepository.findAll()
            .map(this::entityToTool)
            .doOnNext(tool -> toolCache.put(tool.name(), tool))
            .doOnComplete(() -> logger.debug("Retrieved {} tools from database", toolCache.size()));
    }
    
    @Override
    public Flux<McpTool> findToolsByMetadata(String metadataKey, Object metadataValue) {
        logger.debug("Finding tools by metadata: {}={}", metadataKey, metadataValue);
        
        return getAllTools()
            .filter(tool -> {
                if (tool.metadata() == null) return false;
                Object value = tool.metadata().get(metadataKey);
                return value != null && value.equals(metadataValue);
            });
    }
    
    @Override
    public Flux<McpTool> findToolsByNamePattern(String namePattern) {
        logger.debug("Finding tools by name pattern: {}", namePattern);
        
        Pattern pattern = Pattern.compile(namePattern, Pattern.CASE_INSENSITIVE);
        
        return getAllTools()
            .filter(tool -> pattern.matcher(tool.name()).find());
    }
    
    @Override
    public Mono<Long> getToolCount() {
        return Mono.fromCallable(() -> (long) toolCache.size())
            .switchIfEmpty(toolRepository.count());
    }
    
    @Override
    public Flux<String> getMetadataKeys() {
        return getAllTools()
            .flatMap(tool -> {
                if (tool.metadata() == null) return Flux.empty();
                return Flux.fromIterable(tool.metadata().keySet());
            })
            .distinct();
    }
    
    @Override
    public Flux<Object> getMetadataValues(String metadataKey) {
        return getAllTools()
            .mapNotNull(tool -> {
                if (tool.metadata() == null) return null;
                return tool.metadata().get(metadataKey);
            })
            .distinct();
    }
    
    @Override
    public Mono<Boolean> toolExists(String toolName) {
        // Check cache first
        if (toolCache.containsKey(toolName)) {
            return Mono.just(true);
        }
        
        // Fallback to database
        return toolRepository.existsByToolName(toolName);
    }
    
    @Override
    public Mono<Void> validateTool(McpTool tool) {
        if (tool == null) {
            return Mono.error(new IllegalArgumentException("Tool cannot be null"));
        }
        
        if (tool.name() == null || tool.name().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Tool name cannot be empty"));
        }
        
        if (tool.description() == null || tool.description().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Tool description cannot be empty"));
        }
        
        if (tool.inputSchema() == null) {
            return Mono.error(new IllegalArgumentException("Tool input schema cannot be null"));
        }
        
        // Additional validation can be added here
        return Mono.empty();
    }
    
    @Override
    public Mono<Void> clearAllTools() {
        logger.warn("Clearing all tools from registry");
        
        return toolRepository.deleteAll()
            .doOnSuccess(v -> {
                toolCache.clear();
                metadataIndex.clear();
                logger.info("Cleared all tools from registry");
            });
    }
    
    @Override
    public Mono<Map<String, McpTool>> exportTools() {
        logger.debug("Exporting all tools");
        
        return getAllTools()
            .collectMap(McpTool::name)
            .doOnSuccess(tools -> logger.info("Exported {} tools", tools.size()));
    }
    
    @Override
    public Mono<Integer> importTools(Map<String, McpTool> tools, ConflictStrategy conflictStrategy) {
        logger.info("Importing {} tools with strategy: {}", tools.size(), conflictStrategy);
        
        return Flux.fromIterable(tools.values())
            .flatMap(tool -> importSingleTool(tool, conflictStrategy))
            .collectList()
            .map(results -> (int) results.stream().mapToLong(i -> i).sum())
            .doOnSuccess(count -> logger.info("Successfully imported {} tools", count));
    }
    
    // Private helper methods
    
    private void initializeCache() {
        logger.info("Initializing tool registry cache");
        
        toolRepository.findAll()
            .map(this::entityToTool)
            .doOnNext(tool -> {
                toolCache.put(tool.name(), tool);
                updateMetadataIndex(tool);
            })
            .doOnComplete(() -> logger.info("Initialized cache with {} tools", toolCache.size()))
            .subscribe();
    }
    
    private Mono<McpTool> persistTool(McpTool tool) {
        logger.debug("Persisting tool: {}", tool.name());
        
        return Mono.fromCallable(() -> {
            try {
                // Ensure tool serialization works properly
                JsonNode toolDefinition;
                String toolDefinitionJson;
                try {
                    toolDefinition = objectMapper.valueToTree(tool);
                    toolDefinitionJson = objectMapper.writeValueAsString(toolDefinition);

                    if (toolDefinitionJson == null || toolDefinitionJson.isBlank() || "null".equals(toolDefinitionJson)) {
                        // Fallback: create a basic JSON representation
                        logger.warn("ObjectMapper returned null for tool {}, creating fallback JSON", tool.name());
                        toolDefinition = objectMapper.createObjectNode()
                            .put("name", tool.name())
                            .put("description", tool.description() != null ? tool.description() : "")
                            .set("inputSchema", tool.inputSchema() != null ? tool.inputSchema() : objectMapper.createObjectNode());
                        toolDefinitionJson = objectMapper.writeValueAsString(toolDefinition);
                    } else {
                        // already set
                    }
                } catch (Exception e) {
                    logger.error("Failed to serialize tool {}, creating fallback JSON: {}", tool.name(), e.getMessage());
                    // Create a minimal valid JSON representation
                    toolDefinition = objectMapper.createObjectNode()
                        .put("name", tool.name())
                        .put("description", tool.description() != null ? tool.description() : "Generated tool")
                        .set("inputSchema", tool.inputSchema() != null ? tool.inputSchema() : objectMapper.createObjectNode());
                    toolDefinitionJson = objectMapper.writeValueAsString(toolDefinition);
                }
                
                // Extract metadata with proper validation
                String httpMethod = extractHttpMethod(tool);
                String endpointPath = extractEndpointPath(tool);
                UUID apiConfigId = extractApiConfigId(tool);
                
                // Validate required fields
                if (apiConfigId == null) {
                    throw new IllegalArgumentException("API Config ID is required for tool: " + tool.name());
                }
                if (httpMethod == null || httpMethod.trim().isEmpty()) {
                    httpMethod = "GET"; // Default fallback
                }
                if (endpointPath == null || endpointPath.trim().isEmpty()) {
                    endpointPath = "/"; // Default fallback
                }
                
                logger.debug("Creating entity for tool: {} with apiConfigId: {}, toolDefinition size: {}", 
                    tool.name(), apiConfigId, toolDefinitionJson != null ? toolDefinitionJson.length() : 0);
                
                return ToolDefinitionEntity.create(
                    apiConfigId,
                    tool.name(),
                    toolDefinition,
                    httpMethod,
                    endpointPath
                );
            } catch (Exception e) {
                logger.error("Failed to convert tool to entity: {}", tool.name(), e);
                throw new RuntimeException("Failed to convert tool to entity: " + e.getMessage(), e);
            }
        })
        .flatMap(entity -> {
            // Use explicit SQL for JSONB persistence (CAST(:toolDefinition AS jsonb)).
            // Also delete existing row by (apiConfigId, toolName) to satisfy UNIQUE(api_config_id, tool_name).
            UUID apiConfigId = entity.apiConfigId();
            String toolName = entity.toolName();
            String toolDefinitionJson;
            try {
                toolDefinitionJson = objectMapper.writeValueAsString(entity.toolDefinition());
            } catch (Exception e) {
                return Mono.error(new RuntimeException("Failed to serialize tool_definition JSON for " + toolName, e));
            }

            return toolRepository.deleteByApiConfigIdAndToolName(apiConfigId, toolName)
                .onErrorResume(error -> {
                    // If delete fails because row doesn't exist, continue with insert.
                    logger.debug("Delete before insert failed (continuing): {}", error.getMessage());
                    return Mono.just(0);
                })
                .then(toolRepository.insertToolDefinition(
                    apiConfigId,
                    toolName,
                    toolDefinitionJson,
                    entity.httpMethod(),
                    entity.endpointPath(),
                    Instant.now()
                ))
                .doOnSuccess(saved -> logger.debug("Successfully inserted tool: {}", saved != null ? saved.toolName() : "null"));
        })
        .map(entity -> {
            if (entity == null) {
                logger.error("Saved entity is null for tool: {}", tool.name());
                throw new RuntimeException("Database save returned null entity for tool: " + tool.name());
            }
            logger.debug("Converting saved entity to tool: {}", entity.toolName());
            return entityToTool(entity);
        })
        .onErrorMap(error -> {
            logger.error("Error in persistTool for {}: {}", tool.name(), error.getMessage(), error);
            return new RuntimeException("Failed to persist tool: " + tool.name() + " - " + error.getMessage(), error);
        });
    }
    
    private McpTool entityToTool(ToolDefinitionEntity entity) {
        try {
            return objectMapper.treeToValue(entity.toolDefinition(), McpTool.class);
        } catch (Exception e) {
            logger.error("Failed to convert entity to tool: {}", entity.toolName(), e);
            throw new RuntimeException("Failed to convert entity to tool", e);
        }
    }
    
    private Mono<Void> checkForConflicts(McpTool tool) {
        return toolExists(tool.name())
            .flatMap(exists -> exists 
                ? Mono.error(new ToolAlreadyExistsException("Tool already exists: " + tool.name()))
                : Mono.empty());
    }
    
    private void updateMetadataIndex(McpTool tool) {
        if (tool.metadata() != null) {
            tool.metadata().forEach((key, value) -> 
                metadataIndex.put(tool.name() + ":" + key, value.toString()));
        }
    }
    
    private void removeFromMetadataIndex(String toolName) {
        metadataIndex.entrySet().removeIf(entry -> entry.getKey().startsWith(toolName + ":"));
    }
    
    private Mono<Integer> importSingleTool(McpTool tool, ConflictStrategy strategy) {
        return toolExists(tool.name())
            .flatMap(exists -> {
                if (!exists) {
                    return registerTool(tool).thenReturn(1);
                }
                
                return switch (strategy) {
                    case SKIP -> {
                        logger.debug("Skipping existing tool: {}", tool.name());
                        yield Mono.just(0);
                    }
                    case OVERWRITE -> updateTool(tool).thenReturn(1);
                    case FAIL_ON_CONFLICT -> Mono.error(new ToolAlreadyExistsException("Tool already exists: " + tool.name()));
                };
            });
    }
    
    private String extractHttpMethod(McpTool tool) {
        if (tool.metadata() != null && tool.metadata().containsKey("httpMethod")) {
            return tool.metadata().get("httpMethod").toString();
        }
        return "GET"; // default
    }
    
    private String extractEndpointPath(McpTool tool) {
        if (tool.metadata() != null && tool.metadata().containsKey("endpointPath")) {
            return tool.metadata().get("endpointPath").toString();
        }
        return "/"; // default
    }
    
    private UUID extractApiConfigId(McpTool tool) {
        if (tool.metadata() != null && tool.metadata().containsKey("apiConfigId")) {
            try {
                String configIdStr = tool.metadata().get("apiConfigId").toString();
                return UUID.fromString(configIdStr);
            } catch (Exception e) {
                logger.warn("Invalid apiConfigId in tool metadata: {}", tool.metadata().get("apiConfigId"));
            }
        }
        
        // Don't use random UUID as fallback - this causes foreign key constraint violations
        logger.error("No valid apiConfigId found in tool metadata for tool: {}", tool.name());
        throw new IllegalArgumentException("Tool must have valid apiConfigId in metadata: " + tool.name());
    }
    
    // Exception classes
    
    public static class ToolNotFoundException extends RuntimeException {
        public ToolNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class ToolAlreadyExistsException extends RuntimeException {
        public ToolAlreadyExistsException(String message) {
            super(message);
        }
    }
}
