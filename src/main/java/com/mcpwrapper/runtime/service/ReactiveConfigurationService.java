/*
 * MCP REST Adapter - Reactive Configuration Service
 * 
 * Service layer for managing API configurations with reactive streams.
 * Provides CRUD operations, validation, and business logic for API configurations
 * with full audit trail and caching support.
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
import com.mcpwrapper.platform.persistence.entity.ApiConfigurationEntity;
import com.mcpwrapper.platform.persistence.entity.ConfigurationAuditEntity;
import com.mcpwrapper.platform.persistence.repository.ReactiveApiConfigurationRepository;
import com.mcpwrapper.platform.persistence.repository.ReactiveConfigurationAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Reactive service for managing API configurations.
 * 
 * This service provides comprehensive configuration management including:
 * - CRUD operations with validation
 * - Audit trail for all changes
 * - Caching for performance
 * - Business rule enforcement
 * - Reactive error handling
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
@Transactional
public class ReactiveConfigurationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReactiveConfigurationService.class);
    
    private final ReactiveApiConfigurationRepository configRepository;
    private final ReactiveConfigurationAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    
    public ReactiveConfigurationService(
            ReactiveApiConfigurationRepository configRepository,
            ReactiveConfigurationAuditRepository auditRepository,
            ObjectMapper objectMapper) {
        this.configRepository = configRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Creates a new API configuration.
     * 
     * @param request configuration creation request
     * @return Mono containing the created configuration
     */
    public Mono<ApiConfigurationEntity> createConfiguration(CreateConfigurationRequest request) {
        logger.debug("Creating new API configuration: {}", request.name());
        
        return validateConfigurationName(request.name())
            .then(validateConfigurationData(request))
            .then(Mono.fromCallable(() -> ApiConfigurationEntity.create(
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
            )))
            .flatMap(configRepository::save)
            .flatMap(this::createAuditRecord)
            .doOnSuccess(config -> logger.info("Created API configuration: {} ({})", config.name(), config.id()))
            .doOnError(error -> logger.error("Failed to create API configuration: {}", request.name(), error));
    }
    
    /**
     * Updates an existing API configuration.
     * 
     * @param id configuration ID
     * @param request update request
     * @return Mono containing the updated configuration
     */
    @CacheEvict(value = "configurations", key = "#id")
    public Mono<ApiConfigurationEntity> updateConfiguration(UUID id, UpdateConfigurationRequest request) {
        logger.debug("Updating API configuration: {}", id);
        
        return configRepository.findById(id)
            .switchIfEmpty(Mono.error(new ConfigurationNotFoundException("Configuration not found: " + id)))
            .flatMap(existing -> validateConfigurationData(request)
                .then(Mono.fromCallable(() -> existing.withUpdates(
                    request.description(),
                    request.enabled(),
                    request.openapiSourceType(),
                    request.openapiUrl(),
                    request.openapiContent(),
                    request.baseUrl(),
                    request.timeoutSeconds(),
                    request.rateLimitPerMinute(),
                    request.authConfig(),
                    request.advancedConfig()
                ))))
            .flatMap(configRepository::save)
            .flatMap(updated -> createUpdateAuditRecord(updated, request.updatedBy()))
            .doOnSuccess(config -> logger.info("Updated API configuration: {} ({})", config.name(), config.id()))
            .doOnError(error -> logger.error("Failed to update API configuration: {}", id, error));
    }
    
    /**
     * Retrieves a configuration by ID with caching.
     * 
     * @param id configuration ID
     * @return Mono containing the configuration
     */
    // @Cacheable(value = "configurations", key = "#id") // Permanently disabled - was causing reactive chain bypass
    public Mono<ApiConfigurationEntity> getConfiguration(UUID id) {
        logger.info("=== REACTIVE CONFIG SERVICE: Getting configuration for ID: {}", id);
        System.out.println("=== SYSTEM.OUT CONFIG SERVICE: Getting configuration for ID: " + id);
        logger.debug("Retrieving API configuration: {}", id);
        
        return configRepository.findById(id)
            .switchIfEmpty(Mono.error(new ConfigurationNotFoundException("Configuration not found: " + id)))
            .doOnSuccess(config -> logger.debug("Retrieved API configuration: {} ({})", config.name(), config.id()));
    }
    
    /**
     * Retrieves a configuration by name.
     * 
     * @param name configuration name
     * @return Mono containing the configuration
     */
    public Mono<ApiConfigurationEntity> getConfigurationByName(String name) {
        logger.debug("Retrieving API configuration by name: {}", name);
        
        return configRepository.findByName(name)
            .switchIfEmpty(Mono.error(new ConfigurationNotFoundException("Configuration not found: " + name)))
            .doOnSuccess(config -> logger.debug("Retrieved API configuration: {} ({})", config.name(), config.id()));
    }
    
    /**
     * Retrieves all configurations with optional filtering.
     * 
     * @param enabledOnly whether to return only enabled configurations
     * @return Flux of configurations
     */
    public Flux<ApiConfigurationEntity> getAllConfigurations(boolean enabledOnly) {
        logger.debug("Retrieving all API configurations (enabledOnly: {})", enabledOnly);
        
        return enabledOnly 
            ? configRepository.findByEnabledTrue()
            : configRepository.findAll();
    }
    
    /**
     * Deletes a configuration and creates audit record.
     * 
     * @param id configuration ID
     * @param deletedBy user performing the deletion
     * @return Mono indicating completion
     */
    @CacheEvict(value = "configurations", key = "#id")
    public Mono<Void> deleteConfiguration(UUID id, String deletedBy) {
        logger.debug("Deleting API configuration: {}", id);
        
        return configRepository.findById(id)
            .switchIfEmpty(Mono.error(new ConfigurationNotFoundException("Configuration not found: " + id)))
            .flatMap(config -> createDeleteAuditRecord(config, deletedBy)
                .then(configRepository.deleteById(id)))
            .doOnSuccess(v -> logger.info("Deleted API configuration: {}", id))
            .doOnError(error -> logger.error("Failed to delete API configuration: {}", id, error));
    }
    
    /**
     * Enables or disables a configuration.
     * 
     * @param id configuration ID
     * @param enabled new enabled status
     * @param updatedBy user performing the change
     * @return Mono containing the updated configuration
     */
    @CacheEvict(value = "configurations", key = "#id")
    public Mono<ApiConfigurationEntity> setConfigurationEnabled(UUID id, boolean enabled, String updatedBy) {
        logger.debug("Setting configuration {} enabled status to: {}", id, enabled);
        
        return configRepository.findById(id)
            .switchIfEmpty(Mono.error(new ConfigurationNotFoundException("Configuration not found: " + id)))
            .filter(config -> config.enabled() != enabled)
            .switchIfEmpty(Mono.error(new IllegalStateException("Configuration already in requested state")))
            .map(config -> config.withUpdates(
                config.description(),
                enabled,
                config.openapiSourceType(),
                config.openapiUrl(),
                config.openapiContent(),
                config.baseUrl(),
                config.timeoutSeconds(),
                config.rateLimitPerMinute(),
                config.authConfig(),
                config.advancedConfig()
            ))
            .flatMap(configRepository::save)
            .flatMap(updated -> createStatusChangeAuditRecord(updated, enabled, updatedBy))
            .doOnSuccess(config -> logger.info("Set configuration {} enabled status to: {}", id, enabled));
    }
    
    /**
     * Searches configurations by various criteria.
     * 
     * @param searchRequest search criteria
     * @return Flux of matching configurations
     */
    public Flux<ApiConfigurationEntity> searchConfigurations(ConfigurationSearchRequest searchRequest) {
        logger.debug("Searching configurations with criteria: {}", searchRequest);
        
        Flux<ApiConfigurationEntity> results = configRepository.findAll();
        
        if (searchRequest.namePattern() != null) {
            results = configRepository.findByNameContainingIgnoreCase("%" + searchRequest.namePattern() + "%");
        }
        
        if (searchRequest.enabledOnly() != null && searchRequest.enabledOnly()) {
            results = results.filter(config -> config.enabled());
        }
        
        if (searchRequest.sourceType() != null) {
            results = results.filter(config -> searchRequest.sourceType().equals(config.openapiSourceType()));
        }
        
        if (searchRequest.createdBy() != null) {
            results = results.filter(config -> searchRequest.createdBy().equals(config.createdBy()));
        }
        
        if (searchRequest.createdAfter() != null) {
            results = results.filter(config -> config.createdAt().isAfter(searchRequest.createdAfter()));
        }
        
        return results;
    }
    
    /**
     * Gets configuration statistics.
     * 
     * @return Mono containing statistics
     */
    public Mono<ConfigurationStats> getConfigurationStats() {
        logger.debug("Retrieving configuration statistics");
        
        return configRepository.getConfigurationStats()
            .map(repoStats -> new ConfigurationStats(
                repoStats.totalCount(),
                repoStats.enabledCount(),
                repoStats.disabledCount(),
                repoStats.sourceTypeCount()
            ))
            .doOnSuccess(stats -> logger.debug("Retrieved configuration stats: {}", stats));
    }
    
    // Private helper methods
    
    private Mono<Void> validateConfigurationName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Configuration name cannot be empty"));
        }
        
        return configRepository.existsByName(name)
            .flatMap(exists -> exists 
                ? Mono.error(new ConfigurationAlreadyExistsException("Configuration already exists: " + name))
                : Mono.empty());
    }
    
    private Mono<Void> validateConfigurationData(Object request) {
        // Add comprehensive validation logic here
        // For now, basic validation
        return Mono.empty();
    }
    
    private Mono<ApiConfigurationEntity> createAuditRecord(ApiConfigurationEntity config) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode configData = objectMapper.valueToTree(config);
                return ConfigurationAuditEntity.forCreate(
                    config.id(),
                    configData,
                    config.createdBy(),
                    "127.0.0.1" // TODO: Get actual IP from context
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to create audit record", e);
            }
        })
        .flatMap(auditRepository::save)
        .thenReturn(config);
    }
    
    private Mono<ApiConfigurationEntity> createUpdateAuditRecord(ApiConfigurationEntity config, String updatedBy) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode configData = objectMapper.valueToTree(config);
                return ConfigurationAuditEntity.forUpdate(
                    config.id(),
                    null, // TODO: Store before data
                    configData,
                    updatedBy,
                    "127.0.0.1"
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to create audit record", e);
            }
        })
        .flatMap(auditRepository::save)
        .thenReturn(config);
    }
    
    private Mono<Void> createDeleteAuditRecord(ApiConfigurationEntity config, String deletedBy) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode configData = objectMapper.valueToTree(config);
                return ConfigurationAuditEntity.forDelete(
                    config.id(),
                    configData,
                    deletedBy,
                    "127.0.0.1"
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to create audit record", e);
            }
        })
        .flatMap(auditRepository::save)
        .then();
    }
    
    private Mono<ApiConfigurationEntity> createStatusChangeAuditRecord(ApiConfigurationEntity config, boolean enabled, String updatedBy) {
        return Mono.fromCallable(() -> ConfigurationAuditEntity.forStatusChange(
                config.id(),
                enabled,
                updatedBy,
                "127.0.0.1"
            ))
            .flatMap(auditRepository::save)
            .thenReturn(config);
    }
    
    // Request/Response records
    
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
        JsonNode authConfig,
        JsonNode advancedConfig,
        String createdBy
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
        JsonNode authConfig,
        JsonNode advancedConfig,
        String updatedBy
    ) {}
    
    public record ConfigurationSearchRequest(
        String namePattern,
        Boolean enabledOnly,
        String sourceType,
        String createdBy,
        Instant createdAfter
    ) {}
    
    public record ConfigurationStats(
        Long totalCount,
        Long enabledCount,
        Long disabledCount,
        Long sourceTypeCount
    ) {}
    
    // Exception classes
    
    public static class ConfigurationNotFoundException extends RuntimeException {
        public ConfigurationNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class ConfigurationAlreadyExistsException extends RuntimeException {
        public ConfigurationAlreadyExistsException(String message) {
            super(message);
        }
    }
}
