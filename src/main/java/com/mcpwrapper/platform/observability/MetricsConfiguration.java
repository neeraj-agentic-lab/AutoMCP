/*
 * MCP REST Adapter - Metrics Configuration
 * 
 * Configuration for application metrics and monitoring.
 * Sets up Micrometer metrics, custom meters, and Prometheus integration
 * for comprehensive observability of the MCP REST Adapter.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuration for application metrics and monitoring.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Configuration
public class MetricsConfiguration {
    
    /**
     * Customizes the meter registry with common tags.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "mcp-rest-adapter")
            .commonTags("version", "1.0.0");
    }
    
    /**
     * Counter for tool invocations.
     */
    @Bean
    public Counter toolInvocationCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.tool.invocations")
            .description("Total number of tool invocations")
            .register(meterRegistry);
    }
    
    /**
     * Counter for successful tool invocations.
     */
    @Bean
    public Counter toolInvocationSuccessCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.tool.invocations.success")
            .description("Number of successful tool invocations")
            .register(meterRegistry);
    }
    
    /**
     * Counter for failed tool invocations.
     */
    @Bean
    public Counter toolInvocationErrorCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.tool.invocations.error")
            .description("Number of failed tool invocations")
            .register(meterRegistry);
    }
    
    /**
     * Timer for tool execution duration.
     */
    @Bean
    public Timer toolExecutionTimer(MeterRegistry meterRegistry) {
        return Timer.builder("mcp.tool.execution.duration")
            .description("Tool execution duration")
            .register(meterRegistry);
    }
    
    /**
     * Counter for API configuration operations.
     */
    @Bean
    public Counter configurationOperationsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.configuration.operations")
            .description("Number of configuration operations")
            .register(meterRegistry);
    }
    
    /**
     * Counter for OpenAPI parsing operations.
     */
    @Bean
    public Counter openApiParsingCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.openapi.parsing")
            .description("Number of OpenAPI parsing operations")
            .register(meterRegistry);
    }
    
    /**
     * Timer for OpenAPI parsing duration.
     */
    @Bean
    public Timer openApiParsingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("mcp.openapi.parsing.duration")
            .description("OpenAPI parsing duration")
            .register(meterRegistry);
    }
    
    /**
     * Counter for tool generation operations.
     */
    @Bean
    public Counter toolGenerationCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.tool.generation")
            .description("Number of tool generation operations")
            .register(meterRegistry);
    }
    
    /**
     * Gauge for active tool count.
     */
    @Bean
    public AtomicLong activeToolCount() {
        return new AtomicLong(0);
    }
    
    @Bean
    public Gauge activeToolGauge(MeterRegistry meterRegistry, AtomicLong activeToolCount) {
        return Gauge.builder("mcp.tools.active", activeToolCount, AtomicLong::get)
            .description("Number of active tools")
            .register(meterRegistry);
    }
    
    /**
     * Gauge for active configuration count.
     */
    @Bean
    public AtomicLong activeConfigurationCount() {
        return new AtomicLong(0);
    }
    
    @Bean
    public Gauge activeConfigurationGauge(MeterRegistry meterRegistry, AtomicLong activeConfigurationCount) {
        return Gauge.builder("mcp.configurations.active", activeConfigurationCount, AtomicLong::get)
            .description("Number of active configurations")
            .register(meterRegistry);
    }
    
    /**
     * Gauge for WebSocket connections.
     */
    @Bean
    public AtomicLong webSocketConnectionCount() {
        return new AtomicLong(0);
    }
    
    @Bean
    public Gauge webSocketConnectionGauge(MeterRegistry meterRegistry, AtomicLong webSocketConnectionCount) {
        return Gauge.builder("mcp.websocket.connections", webSocketConnectionCount, AtomicLong::get)
            .description("Number of active WebSocket connections")
            .register(meterRegistry);
    }
    
    /**
     * Counter for HTTP requests by endpoint.
     */
    @Bean
    public Counter httpRequestCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.http.requests")
            .description("Number of HTTP requests")
            .register(meterRegistry);
    }
    
    /**
     * Timer for HTTP request duration.
     */
    @Bean
    public Timer httpRequestTimer(MeterRegistry meterRegistry) {
        return Timer.builder("mcp.http.request.duration")
            .description("HTTP request duration")
            .register(meterRegistry);
    }
    
    /**
     * Counter for database operations.
     */
    @Bean
    public Counter databaseOperationsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.database.operations")
            .description("Number of database operations")
            .register(meterRegistry);
    }
    
    /**
     * Timer for database operation duration.
     */
    @Bean
    public Timer databaseOperationTimer(MeterRegistry meterRegistry) {
        return Timer.builder("mcp.database.operation.duration")
            .description("Database operation duration")
            .register(meterRegistry);
    }
    
    /**
     * Counter for cache operations.
     */
    @Bean
    public Counter cacheOperationsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.cache.operations")
            .description("Number of cache operations")
            .register(meterRegistry);
    }
    
    /**
     * Counter for cache hits.
     */
    @Bean
    public Counter cacheHitCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.cache.hits")
            .description("Number of cache hits")
            .register(meterRegistry);
    }
    
    /**
     * Counter for cache misses.
     */
    @Bean
    public Counter cacheMissCounter(MeterRegistry meterRegistry) {
        return Counter.builder("mcp.cache.misses")
            .description("Number of cache misses")
            .register(meterRegistry);
    }
}
