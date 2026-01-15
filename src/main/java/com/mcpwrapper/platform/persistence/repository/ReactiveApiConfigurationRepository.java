/*
 * MCP REST Adapter - Reactive API Configuration Repository
 * 
 * Reactive repository interface for API configuration data access using R2DBC.
 * Provides non-blocking database operations for managing API configurations
 * with custom query methods and reactive streams support.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.persistence.repository;

import com.mcpwrapper.platform.persistence.entity.ApiConfigurationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive repository for API configuration entities.
 * 
 * This repository provides reactive database access for API configurations
 * using R2DBC. All operations return reactive types (Mono/Flux) for
 * non-blocking database interactions.
 * 
 * Features:
 * - CRUD operations with reactive streams
 * - Custom queries for filtering and searching
 * - Optimized queries for common use cases
 * - Support for complex filtering criteria
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Repository
public interface ReactiveApiConfigurationRepository extends R2dbcRepository<ApiConfigurationEntity, UUID> {
    
    /**
     * Finds an API configuration by its unique name.
     * 
     * @param name the configuration name
     * @return Mono containing the configuration if found
     */
    Mono<ApiConfigurationEntity> findByName(String name);
    
    /**
     * Checks if a configuration with the given name exists.
     * 
     * @param name the configuration name to check
     * @return Mono containing true if exists, false otherwise
     */
    Mono<Boolean> existsByName(String name);
    
    /**
     * Finds all enabled API configurations.
     * 
     * @return Flux of enabled configurations
     */
    Flux<ApiConfigurationEntity> findByEnabledTrue();
    
    /**
     * Finds all disabled API configurations.
     * 
     * @return Flux of disabled configurations
     */
    Flux<ApiConfigurationEntity> findByEnabledFalse();
    
    /**
     * Finds configurations by OpenAPI source type.
     * 
     * @param sourceType the OpenAPI source type (URL, FILE, TEXT)
     * @return Flux of configurations with matching source type
     */
    Flux<ApiConfigurationEntity> findByOpenapiSourceType(String sourceType);
    
    /**
     * Finds configurations created by a specific user.
     * 
     * @param createdBy the user ID who created the configurations
     * @return Flux of configurations created by the user
     */
    Flux<ApiConfigurationEntity> findByCreatedBy(String createdBy);
    
    /**
     * Finds configurations created after a specific timestamp.
     * 
     * @param timestamp the timestamp threshold
     * @return Flux of configurations created after the timestamp
     */
    Flux<ApiConfigurationEntity> findByCreatedAtAfter(Instant timestamp);
    
    /**
     * Finds configurations updated after a specific timestamp.
     * 
     * @param timestamp the timestamp threshold
     * @return Flux of configurations updated after the timestamp
     */
    Flux<ApiConfigurationEntity> findByUpdatedAtAfter(Instant timestamp);
    
    /**
     * Searches configurations by name pattern (case-insensitive).
     * 
     * @param namePattern the name pattern to search for
     * @return Flux of configurations with matching names
     */
    @Query("SELECT * FROM api_configurations WHERE LOWER(name) LIKE LOWER(:namePattern)")
    Flux<ApiConfigurationEntity> findByNameContainingIgnoreCase(@Param("namePattern") String namePattern);
    
    /**
     * Searches configurations by description pattern (case-insensitive).
     * 
     * @param descriptionPattern the description pattern to search for
     * @return Flux of configurations with matching descriptions
     */
    @Query("SELECT * FROM api_configurations WHERE LOWER(description) LIKE LOWER(:descriptionPattern)")
    Flux<ApiConfigurationEntity> findByDescriptionContainingIgnoreCase(@Param("descriptionPattern") String descriptionPattern);
    
    /**
     * Finds configurations with specific base URL pattern.
     * 
     * @param baseUrlPattern the base URL pattern to match
     * @return Flux of configurations with matching base URLs
     */
    @Query("SELECT * FROM api_configurations WHERE base_url LIKE :baseUrlPattern")
    Flux<ApiConfigurationEntity> findByBaseUrlLike(@Param("baseUrlPattern") String baseUrlPattern);
    
    /**
     * Finds configurations with timeout greater than specified value.
     * 
     * @param timeoutSeconds minimum timeout in seconds
     * @return Flux of configurations with timeout greater than specified
     */
    Flux<ApiConfigurationEntity> findByTimeoutSecondsGreaterThan(Integer timeoutSeconds);
    
    /**
     * Finds configurations with rate limit greater than specified value.
     * 
     * @param rateLimit minimum rate limit per minute
     * @return Flux of configurations with rate limit greater than specified
     */
    Flux<ApiConfigurationEntity> findByRateLimitPerMinuteGreaterThan(Integer rateLimit);
    
    /**
     * Counts total number of enabled configurations.
     * 
     * @return Mono containing the count of enabled configurations
     */
    Mono<Long> countByEnabledTrue();
    
    /**
     * Counts total number of disabled configurations.
     * 
     * @return Mono containing the count of disabled configurations
     */
    Mono<Long> countByEnabledFalse();
    
    /**
     * Counts configurations by OpenAPI source type.
     * 
     * @param sourceType the OpenAPI source type
     * @return Mono containing the count of configurations with the source type
     */
    Mono<Long> countByOpenapiSourceType(String sourceType);
    
    /**
     * Finds configurations that need OpenAPI refresh (URL-based with old timestamps).
     * 
     * @param sourceType the OpenAPI source type (should be "URL")
     * @param refreshThreshold timestamp threshold for refresh
     * @return Flux of configurations that need refresh
     */
    @Query("SELECT * FROM api_configurations " +
           "WHERE openapi_source_type = :sourceType " +
           "AND enabled = true " +
           "AND updated_at < :refreshThreshold")
    Flux<ApiConfigurationEntity> findConfigurationsNeedingRefresh(
        @Param("sourceType") String sourceType,
        @Param("refreshThreshold") Instant refreshThreshold
    );
    
    /**
     * Finds configurations with authentication of specific type.
     * 
     * @param authType the authentication type to search for
     * @return Flux of configurations with matching auth type
     */
    @Query("SELECT * FROM api_configurations " +
           "WHERE auth_config->>'type' = :authType")
    Flux<ApiConfigurationEntity> findByAuthenticationType(@Param("authType") String authType);
    
    /**
     * Finds configurations that reference a specific environment variable.
     * 
     * @param envVarName the environment variable name
     * @return Flux of configurations that reference the variable
     */
    @Query("SELECT * FROM api_configurations " +
           "WHERE auth_config::text LIKE :envVarPattern " +
           "OR advanced_config::text LIKE :envVarPattern")
    Flux<ApiConfigurationEntity> findByEnvironmentVariableReference(
        @Param("envVarPattern") String envVarPattern
    );
    
    /**
     * Gets summary statistics for all configurations.
     * 
     * @return Mono containing configuration statistics
     */
    @Query("SELECT " +
           "COUNT(*) as total_count, " +
           "COUNT(CASE WHEN enabled = true THEN 1 END) as enabled_count, " +
           "COUNT(CASE WHEN enabled = false THEN 1 END) as disabled_count, " +
           "COUNT(DISTINCT openapi_source_type) as source_type_count " +
           "FROM api_configurations")
    Mono<ConfigurationStats> getConfigurationStats();
    
    /**
     * Deletes configurations older than the specified timestamp.
     * 
     * @param timestamp the timestamp threshold
     * @return Mono containing the number of deleted configurations
     */
    @Query("DELETE FROM api_configurations WHERE created_at < :timestamp")
    Mono<Long> deleteByCreatedAtBefore(@Param("timestamp") Instant timestamp);
    
    /**
     * Updates the enabled status for multiple configurations.
     * 
     * @param configIds list of configuration IDs to update
     * @param enabled new enabled status
     * @return Mono containing the number of updated configurations
     */
    @Query("UPDATE api_configurations SET enabled = :enabled, updated_at = NOW() " +
           "WHERE id = ANY(:configIds)")
    Mono<Long> updateEnabledStatus(@Param("configIds") UUID[] configIds, @Param("enabled") Boolean enabled);
    
    /**
     * Configuration statistics record.
     */
    record ConfigurationStats(
        Long totalCount,
        Long enabledCount,
        Long disabledCount,
        Long sourceTypeCount
    ) {}
}
