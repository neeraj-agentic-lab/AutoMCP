/*
 * MCP REST Adapter - Service Configuration
 * 
 * Configuration class for reactive services, circuit breakers, and resilience patterns.
 * Sets up WebClient, circuit breakers, retry policies, and caching for the service layer.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Configuration for reactive services and resilience patterns.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Configuration
// @EnableCaching // Permanently disabled - was causing reactive chain bypass
@EnableScheduling
public class ServiceConfiguration {
    
    /**
     * Configures WebClient for HTTP requests.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .defaultHeader("User-Agent", "MCP-REST-Adapter/1.0.0");
    }
    
    /**
     * Configures circuit breaker for tool invocations.
     */
    @Bean
    @Primary
    public CircuitBreaker toolInvocationCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f) // 50% failure rate threshold
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s in open state
            .slidingWindowSize(10) // Consider last 10 calls
            .minimumNumberOfCalls(5) // Minimum 5 calls before calculating failure rate
            .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 calls in half-open state
            .slowCallRateThreshold(80.0f) // 80% slow call threshold
            .slowCallDurationThreshold(Duration.ofSeconds(5)) // Calls > 5s are slow
            .build();
        
        return CircuitBreaker.of("toolInvocation", config);
    }
    
    /**
     * Configures retry policy for tool invocations.
     */
    @Bean
    public Retry toolInvocationRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3) // Maximum 3 attempts
            .waitDuration(Duration.ofMillis(500)) // Wait 500ms between retries
            .retryOnException(throwable -> 
                !(throwable instanceof IllegalArgumentException)) // Don't retry validation errors
            .build();
        
        return Retry.of("toolInvocation", config);
    }
    
    /**
     * Configures circuit breaker for API health checks.
     */
    @Bean
    public CircuitBreaker healthCheckCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(70.0f) // Higher threshold for health checks
            .waitDurationInOpenState(Duration.ofMinutes(2)) // Wait 2 minutes
            .slidingWindowSize(5)
            .minimumNumberOfCalls(3)
            .build();
        
        return CircuitBreaker.of("healthCheck", config);
    }
    
    /**
     * Configures cache manager for service layer caching.
     */
    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(java.util.List.of(
            "configurations",
            "tools",
            "environmentVariables",
            "healthStatus"
        ));
        return cacheManager;
    }
}
