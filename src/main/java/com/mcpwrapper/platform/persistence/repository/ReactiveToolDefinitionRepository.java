/*
 * MCP REST Adapter - Reactive Tool Definition Repository
 * 
 * Reactive repository interface for tool definition data access using R2DBC.
 * Provides non-blocking database operations for managing cached MCP tool definitions.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.persistence.repository;

import com.mcpwrapper.platform.persistence.entity.ToolDefinitionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive repository for tool definition entities.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Repository
public interface ReactiveToolDefinitionRepository extends R2dbcRepository<ToolDefinitionEntity, UUID> {
    
    /**
     * Finds a tool definition by its unique name.
     */
    Mono<ToolDefinitionEntity> findByToolName(String toolName);

    /**
     * Finds a tool definition by API configuration + tool name.
     */
    @Query("SELECT * FROM tool_definitions WHERE api_config_id = :apiConfigId AND tool_name = :toolName")
    Mono<ToolDefinitionEntity> findByApiConfigIdAndToolName(
        @Param("apiConfigId") UUID apiConfigId,
        @Param("toolName") String toolName
    );
    
    /**
     * Finds all tool definitions for a specific API configuration.
     */
    Flux<ToolDefinitionEntity> findByApiConfigId(UUID apiConfigId);
    
    /**
     * Finds tool definitions by HTTP method.
     */
    Flux<ToolDefinitionEntity> findByHttpMethod(String httpMethod);
    
    /**
     * Finds tool definitions by endpoint path pattern.
     */
    @Query("SELECT * FROM tool_definitions WHERE endpoint_path LIKE :pathPattern")
    Flux<ToolDefinitionEntity> findByEndpointPathLike(@Param("pathPattern") String pathPattern);
    
    /**
     * Finds stale tool definitions older than the specified timestamp.
     */
    Flux<ToolDefinitionEntity> findByGeneratedAtBefore(Instant timestamp);
    
    /**
     * Deletes all tool definitions for a specific API configuration.
     */
    Mono<Long> deleteByApiConfigId(UUID apiConfigId);

    /**
     * Deletes a single tool definition by API configuration + tool name.
     */
    @Query("DELETE FROM tool_definitions WHERE api_config_id = :apiConfigId AND tool_name = :toolName")
    Mono<Integer> deleteByApiConfigIdAndToolName(
        @Param("apiConfigId") UUID apiConfigId,
        @Param("toolName") String toolName
    );

    /**
     * Inserts a tool definition with explicit JSONB cast.
     *
     * NOTE: Spring Data R2DBC does not reliably write JsonNode/JSONB via default mapping.
     */
    @Query("INSERT INTO tool_definitions (api_config_id, tool_name, tool_definition, http_method, endpoint_path, generated_at) " +
           "VALUES (:apiConfigId, :toolName, CAST(:toolDefinition AS jsonb), :httpMethod, :endpointPath, :generatedAt) " +
           "RETURNING *")
    Mono<ToolDefinitionEntity> insertToolDefinition(
        @Param("apiConfigId") UUID apiConfigId,
        @Param("toolName") String toolName,
        @Param("toolDefinition") String toolDefinition,
        @Param("httpMethod") String httpMethod,
        @Param("endpointPath") String endpointPath,
        @Param("generatedAt") Instant generatedAt
    );
    
    /**
     * Counts tool definitions for a specific API configuration.
     */
    Mono<Long> countByApiConfigId(UUID apiConfigId);
    
    /**
     * Checks if a tool with the given name exists.
     */
    Mono<Boolean> existsByToolName(String toolName);
}
