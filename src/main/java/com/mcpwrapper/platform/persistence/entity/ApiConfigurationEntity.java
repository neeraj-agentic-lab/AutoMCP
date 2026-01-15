/*
 * MCP REST Adapter - API Configuration Entity
 * 
 * Reactive entity model for storing API configuration data using R2DBC.
 * This entity represents the configuration needed to connect to and interact
 * with external REST APIs that are exposed as MCP tools.
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive entity representing API configuration data.
 * 
 * This entity stores all the information needed to configure and connect
 * to external REST APIs, including authentication details, OpenAPI specifications,
 * and advanced configuration options.
 * 
 * The entity is designed for reactive access patterns using R2DBC and supports:
 * - Immutable data structures using Java records
 * - JSON column types for flexible configuration storage
 * - Audit fields for tracking changes
 * - UUID primary keys for distributed systems
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Table("api_configurations")
public record ApiConfigurationEntity(
    /**
     * Unique identifier for the API configuration.
     * 
     * @return UUID primary key
     */
    @Id
    UUID id,
    
    /**
     * Human-readable name for the API configuration.
     * Must be unique across all configurations.
     * 
     * @return unique configuration name
     */
    @Column("name")
    String name,
    
    /**
     * Optional description of the API and its purpose.
     * 
     * @return configuration description
     */
    @Column("description")
    String description,
    
    /**
     * Whether this API configuration is currently enabled.
     * Disabled configurations won't generate tools.
     * 
     * @return true if enabled, false otherwise
     */
    @Column("enabled")
    Boolean enabled,
    
    /**
     * Source type for the OpenAPI specification.
     * Can be URL, FILE, or TEXT.
     * 
     * @return OpenAPI source type
     */
    @Column("openapi_source_type")
    String openapiSourceType,
    
    /**
     * URL to the OpenAPI specification (if source type is URL).
     * 
     * @return OpenAPI specification URL
     */
    @Column("openapi_url")
    String openapiUrl,
    
    /**
     * Direct OpenAPI specification content (if source type is TEXT or FILE).
     * 
     * @return OpenAPI specification content
     */
    @Column("openapi_content")
    String openapiContent,
    
    /**
     * Base URL for the REST API endpoints.
     * 
     * @return API base URL
     */
    @Column("base_url")
    String baseUrl,
    
    /**
     * Request timeout in seconds for API calls.
     * 
     * @return timeout in seconds
     */
    @Column("timeout_seconds")
    Integer timeoutSeconds,
    
    /**
     * Rate limit for API calls per minute.
     * 
     * @return rate limit per minute
     */
    @Column("rate_limit_per_minute")
    Integer rateLimitPerMinute,
    
    /**
     * Authentication configuration stored as JSON.
     * Contains auth type, credentials, and related settings.
     * 
     * @return authentication configuration
     */
    @Column("auth_config")
    JsonNode authConfig,
    
    /**
     * Advanced configuration options stored as JSON.
     * Contains headers, SSL settings, retry policies, etc.
     * 
     * @return advanced configuration
     */
    @Column("advanced_config")
    JsonNode advancedConfig,
    
    /**
     * Timestamp when the configuration was created.
     * 
     * @return creation timestamp
     */
    @CreatedDate
    @Column("created_at")
    Instant createdAt,
    
    /**
     * Timestamp when the configuration was last updated.
     * 
     * @return last update timestamp
     */
    @LastModifiedDate
    @Column("updated_at")
    Instant updatedAt,
    
    /**
     * Identifier of the user who created this configuration.
     * 
     * @return creator user ID
     */
    @Column("created_by")
    String createdBy
) {
    
    /**
     * Creates a new API configuration entity with generated ID and timestamps.
     * 
     * @param name unique configuration name
     * @param description configuration description
     * @param enabled whether the configuration is enabled
     * @param openapiSourceType OpenAPI source type
     * @param openapiUrl OpenAPI specification URL
     * @param openapiContent OpenAPI specification content
     * @param baseUrl API base URL
     * @param timeoutSeconds request timeout
     * @param rateLimitPerMinute rate limit
     * @param authConfig authentication configuration
     * @param advancedConfig advanced configuration
     * @param createdBy creator user ID
     * @return new entity instance
     */
    public static ApiConfigurationEntity create(
            String name,
            String description,
            Boolean enabled,
            String openapiSourceType,
            String openapiUrl,
            String openapiContent,
            String baseUrl,
            Integer timeoutSeconds,
            Integer rateLimitPerMinute,
            JsonNode authConfig,
            JsonNode advancedConfig,
            String createdBy) {
        
        Instant now = Instant.now();
        return new ApiConfigurationEntity(
            null, // Let the database generate the UUID
            name,
            description,
            enabled,
            openapiSourceType,
            openapiUrl,
            openapiContent,
            baseUrl,
            timeoutSeconds,
            rateLimitPerMinute,
            authConfig,
            advancedConfig,
            now,
            now,
            createdBy
        );
    }
    
    /**
     * Creates an updated copy of this entity with new values.
     * 
     * @param description new description
     * @param enabled new enabled status
     * @param openapiSourceType new OpenAPI source type
     * @param openapiUrl new OpenAPI URL
     * @param openapiContent new OpenAPI content
     * @param baseUrl new base URL
     * @param timeoutSeconds new timeout
     * @param rateLimitPerMinute new rate limit
     * @param authConfig new auth configuration
     * @param advancedConfig new advanced configuration
     * @return updated entity instance
     */
    public ApiConfigurationEntity withUpdates(
            String description,
            Boolean enabled,
            String openapiSourceType,
            String openapiUrl,
            String openapiContent,
            String baseUrl,
            Integer timeoutSeconds,
            Integer rateLimitPerMinute,
            JsonNode authConfig,
            JsonNode advancedConfig) {
        
        return new ApiConfigurationEntity(
            this.id,
            this.name, // name cannot be changed
            description,
            enabled,
            openapiSourceType,
            openapiUrl,
            openapiContent,
            baseUrl,
            timeoutSeconds,
            rateLimitPerMinute,
            authConfig,
            advancedConfig,
            this.createdAt,
            Instant.now(), // update timestamp
            this.createdBy
        );
    }
}
