/*
 * MCP REST Adapter - Reactive API Health Service
 * 
 * Service for monitoring API health and availability with reactive streams.
 * Provides health checks, status tracking, and alerting for external APIs.
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
import com.mcpwrapper.platform.persistence.entity.ApiHealthStatusEntity;
import com.mcpwrapper.platform.persistence.repository.ReactiveApiHealthStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Reactive service for monitoring API health and availability.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
public class ReactiveApiHealthService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReactiveApiHealthService.class);
    
    private final ReactiveApiHealthStatusRepository healthRepository;
    private final ReactiveConfigurationService configurationService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    public ReactiveApiHealthService(
            ReactiveApiHealthStatusRepository healthRepository,
            ReactiveConfigurationService configurationService,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.healthRepository = healthRepository;
        this.configurationService = configurationService;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }
    
    /**
     * Performs health check for a specific API configuration.
     */
    public Mono<ApiHealthStatusEntity> checkApiHealth(UUID apiConfigId) {
        logger.debug("Checking health for API configuration: {}", apiConfigId);
        
        return configurationService.getConfiguration(apiConfigId)
            .flatMap(config -> performHealthCheck(config.baseUrl())
                .flatMap(result -> updateHealthStatus(apiConfigId, result))
                .onErrorResume(error -> recordHealthFailure(apiConfigId, error.getMessage())))
            .doOnSuccess(status -> logger.debug("Health check completed for API {}: {}", 
                apiConfigId, status.healthState()))
            .doOnError(error -> logger.error("Health check failed for API {}", apiConfigId, error));
    }
    
    /**
     * Gets current health status for an API.
     */
    public Mono<ApiHealthStatusEntity> getApiHealthStatus(UUID apiConfigId) {
        return healthRepository.findById(apiConfigId)
            .switchIfEmpty(Mono.just(ApiHealthStatusEntity.create(apiConfigId)));
    }
    
    /**
     * Gets all unhealthy APIs.
     */
    public Flux<ApiHealthStatusEntity> getUnhealthyApis() {
        return healthRepository.findUnhealthyApis();
    }
    
    /**
     * Gets APIs with consecutive failures above threshold.
     */
    public Flux<ApiHealthStatusEntity> getApisWithConsecutiveFailures(int threshold) {
        return healthRepository.findApisWithConsecutiveFailures(threshold);
    }
    
    /**
     * Gets stale health records that need refresh.
     */
    public Flux<ApiHealthStatusEntity> getStaleHealthRecords(Duration maxAge) {
        Instant threshold = Instant.now().minus(maxAge);
        return healthRepository.findStaleHealthRecords(threshold);
    }
    
    /**
     * Scheduled health check for all enabled APIs.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void performScheduledHealthChecks() {
        logger.debug("Starting scheduled health checks");
        
        configurationService.getAllConfigurations(true) // enabled only
            .flatMap(config -> checkApiHealth(config.id())
                .onErrorContinue((error, item) -> 
                    logger.warn("Health check failed for API {}: {}", config.id(), error.getMessage())))
            .collectList()
            .subscribe(
                results -> logger.info("Completed scheduled health checks for {} APIs", results.size()),
                error -> logger.error("Scheduled health check batch failed", error)
            );
    }
    
    // Private helper methods
    
    private Mono<HealthCheckResult> performHealthCheck(String baseUrl) {
        Instant startTime = Instant.now();
        
        return webClient.get()
            .uri(baseUrl + "/health") // Assume health endpoint
            .retrieve()
            .toBodilessEntity()
            .map(response -> {
                long responseTime = Duration.between(startTime, Instant.now()).toMillis();
                boolean isHealthy = response.getStatusCode().is2xxSuccessful();
                
                return new HealthCheckResult(
                    isHealthy,
                    (int) responseTime,
                    isHealthy ? "API is responding" : "API returned error status",
                    createHealthDetails(response.getStatusCode().value(), responseTime)
                );
            })
            .onErrorReturn(new HealthCheckResult(
                false,
                (int) Duration.between(startTime, Instant.now()).toMillis(),
                "Health check failed: connection error",
                createErrorDetails("CONNECTION_ERROR")
            ));
    }
    
    private Mono<ApiHealthStatusEntity> updateHealthStatus(UUID apiConfigId, HealthCheckResult result) {
        return healthRepository.findById(apiConfigId)
            .switchIfEmpty(Mono.just(ApiHealthStatusEntity.create(apiConfigId)))
            .map(existing -> result.isHealthy() 
                ? existing.recordSuccess(result.responseTimeMs(), result.details())
                : existing.recordFailure(result.message(), result.details()))
            .flatMap(healthRepository::save);
    }
    
    private Mono<ApiHealthStatusEntity> recordHealthFailure(UUID apiConfigId, String errorMessage) {
        return healthRepository.findById(apiConfigId)
            .switchIfEmpty(Mono.just(ApiHealthStatusEntity.create(apiConfigId)))
            .map(existing -> existing.recordFailure(errorMessage, createErrorDetails("HEALTH_CHECK_FAILED")))
            .flatMap(healthRepository::save);
    }
    
    private JsonNode createHealthDetails(int statusCode, long responseTime) {
        var details = objectMapper.createObjectNode();
        details.put("statusCode", statusCode);
        details.put("responseTimeMs", responseTime);
        details.put("timestamp", Instant.now().toString());
        return details;
    }
    
    private JsonNode createErrorDetails(String errorType) {
        var details = objectMapper.createObjectNode();
        details.put("errorType", errorType);
        details.put("timestamp", Instant.now().toString());
        return details;
    }
    
    // Helper record
    
    private record HealthCheckResult(
        boolean isHealthy,
        int responseTimeMs,
        String message,
        JsonNode details
    ) {}
}
