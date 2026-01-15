/*
 * MCP REST Adapter - Tool Definition Entity
 * 
 * Reactive entity model for storing generated MCP tool definitions using R2DBC.
 * This entity represents the cached tool definitions that are generated from
 * OpenAPI specifications and made available to MCP clients.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive entity representing cached MCP tool definitions.
 * 
 * This entity stores the generated tool definitions that are created from
 * OpenAPI specifications. The tools are cached in the database to improve
 * performance and provide consistency across server restarts.
 * 
 * Each tool definition includes:
 * - The complete MCP tool specification
 * - HTTP method and endpoint information
 * - Generation timestamp for cache invalidation
 * - Reference to the source API configuration
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Table("tool_definitions")
public record ToolDefinitionEntity(
    /**
     * Unique identifier for the tool definition.
     * 
     * @return UUID primary key
     */
    @Id
    UUID id,
    
    /**
     * Reference to the API configuration that generated this tool.
     * 
     * @return API configuration ID
     */
    @Column("api_config_id")
    UUID apiConfigId,
    
    /**
     * Unique name of the MCP tool.
     * Must be unique within the scope of the API configuration.
     * 
     * @return tool name
     */
    @Column("tool_name")
    String toolName,
    
    /**
     * Complete MCP tool definition stored as JSON.
     * Contains name, description, input schema, and metadata.
     * 
     * @return tool definition JSON
     */
    @Column("tool_definition")
    JsonNode toolDefinition,
    
    /**
     * HTTP method for the underlying REST endpoint.
     * 
     * @return HTTP method (GET, POST, PUT, DELETE, etc.)
     */
    @Column("http_method")
    String httpMethod,
    
    /**
     * Path template for the REST endpoint.
     * May contain path parameters in OpenAPI format.
     * 
     * @return endpoint path template
     */
    @Column("endpoint_path")
    String endpointPath,
    
    /**
     * Timestamp when the tool definition was generated.
     * Used for cache invalidation and freshness checks.
     * 
     * @return generation timestamp
     */
    @CreatedDate
    @Column("generated_at")
    Instant generatedAt
) {
    
    /**
     * Creates a new tool definition entity with generated ID and timestamp.
     * 
     * @param apiConfigId ID of the source API configuration
     * @param toolName unique tool name
     * @param toolDefinition complete MCP tool definition
     * @param httpMethod HTTP method for the endpoint
     * @param endpointPath path template for the endpoint
     * @return new entity instance
     */
    public static ToolDefinitionEntity create(
            UUID apiConfigId,
            String toolName,
            JsonNode toolDefinition,
            String httpMethod,
            String endpointPath) {
        
        return new ToolDefinitionEntity(
            null, // Let database generate the ID for INSERT operations
            apiConfigId,
            toolName,
            toolDefinition,
            httpMethod,
            endpointPath,
            Instant.now()
        );
    }
    
    /**
     * Creates an updated copy of this entity with new tool definition.
     * 
     * @param toolDefinition updated MCP tool definition
     * @param httpMethod updated HTTP method
     * @param endpointPath updated endpoint path
     * @return updated entity instance
     */
    public ToolDefinitionEntity withUpdatedDefinition(
            JsonNode toolDefinition,
            String httpMethod,
            String endpointPath) {
        
        return new ToolDefinitionEntity(
            this.id,
            this.apiConfigId,
            this.toolName,
            toolDefinition,
            httpMethod,
            endpointPath,
            Instant.now() // update generation timestamp
        );
    }
    
    /**
     * Checks if this tool definition is stale based on the given threshold.
     * 
     * @param maxAge maximum age before considering the definition stale
     * @return true if the definition is older than maxAge
     */
    public boolean isStale(java.time.Duration maxAge) {
        return generatedAt.isBefore(Instant.now().minus(maxAge));
    }
}
