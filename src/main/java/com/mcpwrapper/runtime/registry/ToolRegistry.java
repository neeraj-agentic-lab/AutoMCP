/*
 * MCP REST Adapter - Tool Registry Interface
 * 
 * This file defines the core interface for managing MCP tools within the system.
 * The Tool Registry serves as the central repository for all discoverable tools,
 * providing operations for registration, discovery, and lifecycle management.
 * 
 * The registry supports dynamic tool registration from OpenAPI specifications
 * and provides efficient lookup mechanisms for tool discovery and invocation.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mcpwrapper.runtime.registry;

import com.mcpwrapper.transport.mcp.McpTool;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Central registry for managing Model Context Protocol (MCP) tools.
 * 
 * The Tool Registry serves as the authoritative source for all tools available
 * within the MCP server. It provides comprehensive functionality for:
 * 
 * - **Tool Registration**: Adding new tools from OpenAPI specifications
 * - **Tool Discovery**: Searching and filtering available tools
 * - **Tool Retrieval**: Accessing tool definitions for invocation
 * - **Lifecycle Management**: Updating, versioning, and removing tools
 * - **Metadata Management**: Organizing tools by categories and tags
 * 
 * The registry is designed to be:
 * - **Thread-Safe**: Supports concurrent access from multiple requests
 * - **Reactive**: Uses Project Reactor for non-blocking operations
 * - **Scalable**: Efficient lookup and filtering mechanisms
 * - **Observable**: Emits events for monitoring and caching
 * 
 * Implementation Considerations:
 * - Tools are indexed by name for O(1) lookup performance
 * - Metadata indexes support efficient filtering and search
 * - Change notifications enable cache invalidation
 * - Validation ensures tool integrity and uniqueness
 * 
 * Example Usage:
 * ```java
 * // Register a new tool
 * registry.registerTool(tool)
 *     .doOnSuccess(registered -> log.info("Tool registered: {}", registered.name()))
 *     .subscribe();
 * 
 * // Find tools by category
 * registry.findToolsByMetadata("category", "pets")
 *     .collectList()
 *     .doOnNext(tools -> log.info("Found {} pet tools", tools.size()))
 *     .subscribe();
 * 
 * // Get a specific tool
 * registry.getTool("petstore_list_pets")
 *     .doOnNext(tool -> log.info("Retrieved tool: {}", tool.description()))
 *     .subscribe();
 * ```
 * 
 * Thread Safety:
 * All methods in this interface must be thread-safe and support concurrent
 * access. Implementations should use appropriate synchronization mechanisms
 * or lock-free data structures to ensure consistency.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 * @see McpTool
 * @see ToolRegistrationEvent
 */
public interface ToolRegistry {
    
    /**
     * Registers a new tool in the registry.
     * 
     * The registration process includes:
     * 1. **Validation**: Ensures tool definition is complete and valid
     * 2. **Uniqueness Check**: Verifies no tool with the same name exists
     * 3. **Indexing**: Adds the tool to internal indexes for efficient lookup
     * 4. **Event Emission**: Notifies listeners of the new tool registration
     * 
     * If a tool with the same name already exists, the registration will fail
     * with a {@code ToolAlreadyExistsException}. Use {@link #updateTool(McpTool)}
     * to modify existing tools.
     * 
     * Validation Rules:
     * - Tool name must be unique within the registry
     * - Tool name must follow naming conventions (lowercase, underscores)
     * - Description must be non-empty and meaningful
     * - Input schema must be valid JSON Schema
     * - Metadata must not contain reserved keys
     * 
     * @param tool the tool to register, must not be null
     * @return a Mono that emits the registered tool on successful registration
     * @throws IllegalArgumentException if the tool is null or invalid
     * @throws ToolAlreadyExistsException if a tool with the same name exists
     * @throws ToolValidationException if the tool definition is invalid
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<McpTool> registerTool(McpTool tool);
    
    /**
     * Updates an existing tool in the registry.
     * 
     * The update process:
     * 1. **Existence Check**: Verifies the tool exists in the registry
     * 2. **Validation**: Ensures the updated definition is valid
     * 3. **Index Update**: Updates all relevant indexes
     * 4. **Event Emission**: Notifies listeners of the tool update
     * 
     * The tool name cannot be changed during an update. To rename a tool,
     * you must unregister the old tool and register a new one.
     * 
     * Update Scenarios:
     * - Schema changes (adding/removing parameters)
     * - Description updates
     * - Metadata modifications
     * - Deprecation status changes
     * 
     * @param tool the updated tool definition, must not be null
     * @return a Mono that emits the updated tool on successful update
     * @throws IllegalArgumentException if the tool is null or invalid
     * @throws ToolNotFoundException if no tool with the given name exists
     * @throws ToolValidationException if the updated definition is invalid
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<McpTool> updateTool(McpTool tool);
    
    /**
     * Removes a tool from the registry.
     * 
     * The unregistration process:
     * 1. **Existence Check**: Verifies the tool exists
     * 2. **Dependency Check**: Ensures no active invocations are using the tool
     * 3. **Index Removal**: Removes the tool from all indexes
     * 4. **Event Emission**: Notifies listeners of the tool removal
     * 
     * Safety Considerations:
     * - Active tool invocations are allowed to complete
     * - New invocations of the tool are rejected immediately
     * - Graceful degradation for dependent services
     * 
     * @param toolName the name of the tool to remove, must not be null or empty
     * @return a Mono that emits true if the tool was removed, false if it didn't exist
     * @throws IllegalArgumentException if toolName is null or empty
     * @throws ToolInUseException if the tool has active invocations
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<Boolean> unregisterTool(String toolName);
    
    /**
     * Retrieves a specific tool by name.
     * 
     * This is the primary method for tool lookup during invocation.
     * The lookup is optimized for performance with O(1) complexity.
     * 
     * @param toolName the name of the tool to retrieve, must not be null or empty
     * @return a Mono that emits the tool if found, or empty if not found
     * @throws IllegalArgumentException if toolName is null or empty
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<McpTool> getTool(String toolName);
    
    /**
     * Retrieves all registered tools.
     * 
     * Returns a Flux of all tools currently registered in the system.
     * The order is not guaranteed unless specified by the implementation.
     * 
     * Performance Note:
     * For large numbers of tools, consider using filtering methods
     * to reduce the result set size.
     * 
     * @return a Flux emitting all registered tools
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Flux<McpTool> getAllTools();
    
    /**
     * Finds tools matching specific metadata criteria.
     * 
     * Searches for tools where the specified metadata key has the given value.
     * This method supports efficient filtering for tool discovery scenarios.
     * 
     * Common metadata keys:
     * - "category": Functional grouping (e.g., "inventory", "user-management")
     * - "version": API version
     * - "deprecated": Boolean indicating deprecation status
     * - "tags": Array of searchable keywords
     * 
     * @param metadataKey the metadata key to search by, must not be null or empty
     * @param metadataValue the value to match, must not be null
     * @return a Flux emitting tools with matching metadata
     * @throws IllegalArgumentException if metadataKey is null/empty or metadataValue is null
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Flux<McpTool> findToolsByMetadata(String metadataKey, Object metadataValue);
    
    /**
     * Searches for tools by name pattern.
     * 
     * Supports pattern matching for tool discovery:
     * - Exact match: "petstore_list_pets"
     * - Prefix match: "petstore_*"
     * - Wildcard match: "*_list_*"
     * - Regex match: "user_.*_create"
     * 
     * @param namePattern the pattern to match against tool names
     * @return a Flux emitting tools with names matching the pattern
     * @throws IllegalArgumentException if namePattern is null or empty
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Flux<McpTool> findToolsByNamePattern(String namePattern);
    
    /**
     * Gets the total number of registered tools.
     * 
     * Useful for:
     * - Monitoring and alerting
     * - Capacity planning
     * - Health checks
     * - Administrative dashboards
     * 
     * @return a Mono emitting the total tool count
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<Long> getToolCount();
    
    /**
     * Gets all unique metadata keys used by registered tools.
     * 
     * Helps with:
     * - Dynamic UI generation for filtering
     * - API documentation
     * - Tool categorization analysis
     * - Metadata validation
     * 
     * @return a Flux emitting all unique metadata keys
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Flux<String> getMetadataKeys();
    
    /**
     * Gets all unique values for a specific metadata key.
     * 
     * Useful for building filter interfaces and understanding
     * the distribution of metadata values.
     * 
     * @param metadataKey the metadata key to get values for
     * @return a Flux emitting all unique values for the specified key
     * @throws IllegalArgumentException if metadataKey is null or empty
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Flux<Object> getMetadataValues(String metadataKey);
    
    /**
     * Checks if a tool with the given name exists.
     * 
     * Optimized method for existence checking without retrieving
     * the full tool definition.
     * 
     * @param toolName the name to check, must not be null or empty
     * @return a Mono emitting true if the tool exists, false otherwise
     * @throws IllegalArgumentException if toolName is null or empty
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<Boolean> toolExists(String toolName);
    
    /**
     * Validates a tool definition without registering it.
     * 
     * Performs all validation checks that would be done during registration:
     * - Name format validation
     * - Schema validation
     * - Metadata validation
     * - Business rule validation
     * 
     * Useful for:
     * - Pre-registration validation
     * - Tool definition testing
     * - Configuration validation
     * 
     * @param tool the tool to validate, must not be null
     * @return a Mono that completes successfully if valid, or emits an error if invalid
     * @throws IllegalArgumentException if tool is null
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<Void> validateTool(McpTool tool);
    
    /**
     * Clears all registered tools from the registry.
     * 
     * This is a destructive operation that should be used with caution.
     * Typically used for:
     * - Testing scenarios
     * - Configuration reloads
     * - Emergency cleanup
     * 
     * Safety Considerations:
     * - Active tool invocations may fail
     * - Dependent services should be notified
     * - Consider graceful shutdown procedures
     * 
     * @return a Mono that completes when all tools have been cleared
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<Void> clearAllTools();
    
    /**
     * Exports all tool definitions for backup or migration.
     * 
     * Returns a snapshot of all registered tools that can be used for:
     * - Configuration backup
     * - Environment migration
     * - Disaster recovery
     * - Audit purposes
     * 
     * @return a Mono emitting a map of tool names to tool definitions
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<Map<String, McpTool>> exportTools();
    
    /**
     * Imports tool definitions from a backup or migration source.
     * 
     * Bulk operation for registering multiple tools at once.
     * The import process:
     * 1. Validates all tools before importing any
     * 2. Registers tools in dependency order if applicable
     * 3. Handles conflicts according to the specified strategy
     * 
     * @param tools map of tool names to tool definitions
     * @param conflictStrategy how to handle existing tools with the same name
     * @return a Mono emitting the number of successfully imported tools
     * @throws IllegalArgumentException if tools map is null
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<Integer> importTools(Map<String, McpTool> tools, ConflictStrategy conflictStrategy);
    
    /**
     * Strategy for handling conflicts during tool import operations.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    enum ConflictStrategy {
        /** Skip tools that already exist */
        SKIP,
        /** Overwrite existing tools with imported versions */
        OVERWRITE,
        /** Fail the entire import if any conflicts exist */
        FAIL_ON_CONFLICT
    }
}
