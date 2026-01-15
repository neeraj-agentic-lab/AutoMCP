/*
 * MCP REST Adapter - Reactive Tool Invoker Service
 * 
 * Implementation of the ToolInvoker interface with reactive streams.
 * Handles tool execution, validation, statistics tracking, and error handling
 * with circuit breaker patterns and retry logic.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpwrapper.platform.persistence.entity.ToolUsageStatsEntity;
import com.mcpwrapper.platform.persistence.repository.ReactiveToolUsageStatsRepository;
import com.mcpwrapper.runtime.invocation.ToolInvoker;
import com.mcpwrapper.transport.mcp.McpTool;
import com.mcpwrapper.transport.mcp.McpToolCall;
import com.mcpwrapper.transport.mcp.McpToolResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.reactor.retry.RetryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reactive implementation of ToolInvoker with resilience patterns.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
public class ReactiveToolInvokerService implements ToolInvoker {
    
    private static final Logger logger = LoggerFactory.getLogger(ReactiveToolInvokerService.class);
    
    private final ReactiveToolRegistryService toolRegistry;
    private final ReactiveToolUsageStatsRepository statsRepository;
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    // Active executions tracking
    private final Map<String, ActiveExecution> activeExecutions = new ConcurrentHashMap<>();
    
    public ReactiveToolInvokerService(
            ReactiveToolRegistryService toolRegistry,
            ReactiveToolUsageStatsRepository statsRepository,
            WebClient.Builder webClientBuilder,
            CircuitBreaker circuitBreaker,
            Retry retry) {
        this.toolRegistry = toolRegistry;
        this.statsRepository = statsRepository;
        this.webClient = webClientBuilder.build();
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }
    
    @Override
    public Mono<McpToolResult> invokeTool(McpToolCall toolCall) {
        return invokeTool(toolCall, ToolExecutionContext.defaultContext());
    }
    
    @Override
    public Mono<McpToolResult> invokeTool(McpToolCall toolCall, ToolExecutionContext context) {
        logger.debug("Invoking tool: {} with call ID: {}", toolCall.name(), toolCall.callId());
        
        Instant startTime = Instant.now();
        
        // Track active execution
        ActiveExecution execution = new ActiveExecution(
            toolCall.callId(),
            toolCall.name(),
            startTime,
            ActiveExecution.ExecutionPhase.VALIDATION,
            Map.of("priority", context.priority().toString(), "status", "RUNNING")
        );
        activeExecutions.put(toolCall.callId(), execution);
        
        return validateToolCall(toolCall)
            .filter(ValidationResult::isValid)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Tool call validation failed")))
            .then(toolRegistry.getTool(toolCall.name()))
            .flatMap(tool -> executeToolCall(toolCall, tool, context))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry))
            .doOnSuccess(result -> recordSuccess(toolCall.name(), startTime))
            .doOnError(error -> recordError(toolCall.name(), startTime, error.getMessage()))
            .doFinally(signal -> activeExecutions.remove(toolCall.callId()))
            .doOnSuccess(result -> logger.info("Successfully invoked tool: {} ({}ms)", 
                toolCall.name(), Duration.between(startTime, Instant.now()).toMillis()))
            .doOnError(error -> logger.error("Failed to invoke tool: {}", toolCall.name(), error));
    }
    
    @Override
    public Mono<ValidationResult> validateToolCall(McpToolCall toolCall) {
        if (toolCall == null) {
            return Mono.just(ValidationResult.failure(java.util.List.of("Tool call cannot be null")));
        }
        
        if (toolCall.name() == null || toolCall.name().trim().isEmpty()) {
            return Mono.just(ValidationResult.failure(java.util.List.of("Tool name cannot be empty")));
        }
        
        if (toolCall.callId() == null || toolCall.callId().trim().isEmpty()) {
            return Mono.just(ValidationResult.failure(java.util.List.of("Call ID cannot be empty")));
        }
        
        return toolRegistry.toolExists(toolCall.name())
            .map(exists -> exists 
                ? ValidationResult.success()
                : ValidationResult.failure(java.util.List.of("Tool not found: " + toolCall.name())));
    }
    
    @Override
    public Mono<ToolExecutionStats> getToolStats(String toolName) {
        return statsRepository.findById(toolName)
            .map(entity -> new ToolExecutionStats(
                entity.toolName(),
                entity.invocationCount(),
                entity.successCount(),
                entity.errorCount(),
                entity.successCount() > 0 ? (double) entity.successCount() / entity.invocationCount() : 0.0,
                Duration.ofMillis(entity.avgExecutionTimeMs()),
                Duration.ofMillis(entity.avgExecutionTimeMs()), // Using avg as p95 for now
                Duration.ofMillis(entity.avgExecutionTimeMs()), // Using avg as p99 for now
                Map.of() // Empty error map for now
            ))
            .switchIfEmpty(Mono.just(new ToolExecutionStats(
                toolName, 0L, 0L, 0L, 0.0, 
                Duration.ZERO, Duration.ZERO, Duration.ZERO, Map.of()
            )));
    }
    
    @Override
    public Mono<OverallExecutionStats> getOverallStats() {
        return statsRepository.findAll()
            .collectList()
            .map(stats -> {
                long totalInvocations = stats.stream().mapToLong(ToolUsageStatsEntity::invocationCount).sum();
                long totalSuccesses = stats.stream().mapToLong(ToolUsageStatsEntity::successCount).sum();
                long totalErrors = stats.stream().mapToLong(ToolUsageStatsEntity::errorCount).sum();
                double avgExecutionTime = stats.stream()
                    .mapToInt(ToolUsageStatsEntity::avgExecutionTimeMs)
                    .average()
                    .orElse(0.0);
                
                return new OverallExecutionStats(
                    stats.size(), // totalTools
                    totalInvocations,
                    totalInvocations > 0 ? (double) totalSuccesses / totalInvocations : 0.0, // overallSuccessRate
                    Duration.ofMillis((long) avgExecutionTime), // averageExecutionTime
                    activeExecutions.size(), // activeExecutions
                    Map.of() // topToolsByUsage - empty for now
                );
            });
    }
    
    @Override
    public Mono<Boolean> isToolAvailable(String toolName) {
        return toolRegistry.toolExists(toolName)
            .flatMap(exists -> {
                if (!exists) return Mono.just(false);
                
                // Check circuit breaker state
                return Mono.just(circuitBreaker.getState() != CircuitBreaker.State.OPEN);
            });
    }
    
    @Override
    public Mono<ToolHealthStatus> getToolHealth(String toolName) {
        return getToolStats(toolName)
            .map(stats -> {
                ToolInvoker.ToolHealthStatus.HealthState state = determineHealthState(stats);
                return new ToolHealthStatus(
                    toolName,
                    state,
                    getHealthMessage(state, stats),
                    Instant.now(), // lastChecked
                    Map.of(
                        "successRate", stats.successRate(),
                        "totalInvocations", stats.totalInvocations(),
                        "averageExecutionTime", stats.averageExecutionTime().toMillis()
                    ) // details
                );
            });
    }
    
    @Override
    public Mono<Boolean> cancelExecution(String callId) {
        ActiveExecution execution = activeExecutions.get(callId);
        if (execution != null) {
            // Create a new execution with cancelled phase
            ActiveExecution cancelledExecution = new ActiveExecution(
                execution.callId(),
                execution.toolName(),
                execution.startTime(),
                ActiveExecution.ExecutionPhase.COMPLETED,
                Map.of("status", "CANCELLED")
            );
            activeExecutions.put(callId, cancelledExecution);
            logger.info("Cancelled execution: {}", callId);
            return Mono.just(true);
        }
        return Mono.just(false);
    }
    
    @Override
    public Mono<Map<String, ActiveExecution>> getActiveExecutions() {
        return Mono.just(Map.copyOf(activeExecutions));
    }
    
    // Private helper methods
    
    private Mono<McpToolResult> executeToolCall(McpToolCall toolCall, McpTool tool, ToolExecutionContext context) {
        // Extract HTTP method and endpoint from tool metadata
        String httpMethod = extractHttpMethod(tool);
        String endpoint = extractEndpoint(tool);
        
        logger.debug("Executing {} {} for tool: {}", httpMethod, endpoint, tool.name());
        
        return switch (httpMethod.toUpperCase()) {
            case "GET" -> executeGetRequest(toolCall, endpoint, context);
            case "POST" -> executePostRequest(toolCall, endpoint, context);
            case "PUT" -> executePutRequest(toolCall, endpoint, context);
            case "DELETE" -> executeDeleteRequest(toolCall, endpoint, context);
            default -> Mono.error(new UnsupportedOperationException("Unsupported HTTP method: " + httpMethod));
        };
    }
    
    private Mono<McpToolResult> executeGetRequest(McpToolCall toolCall, String endpoint, ToolExecutionContext context) {
        return webClient.get()
            .uri(endpoint)
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> McpToolResult.success(toolCall.callId(), response, Duration.ofMillis(100)))
            .onErrorMap(error -> new RuntimeException("GET request failed", error));
    }
    
    private Mono<McpToolResult> executePostRequest(McpToolCall toolCall, String endpoint, ToolExecutionContext context) {
        return webClient.post()
            .uri(endpoint)
            .bodyValue(toolCall.arguments())
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> McpToolResult.success(toolCall.callId(), response, Duration.ofMillis(100)))
            .onErrorMap(error -> new RuntimeException("POST request failed", error));
    }
    
    private Mono<McpToolResult> executePutRequest(McpToolCall toolCall, String endpoint, ToolExecutionContext context) {
        return webClient.put()
            .uri(endpoint)
            .bodyValue(toolCall.arguments())
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> McpToolResult.success(toolCall.callId(), response, Duration.ofMillis(100)))
            .onErrorMap(error -> new RuntimeException("PUT request failed", error));
    }
    
    private Mono<McpToolResult> executeDeleteRequest(McpToolCall toolCall, String endpoint, ToolExecutionContext context) {
        return webClient.delete()
            .uri(endpoint)
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> McpToolResult.success(toolCall.callId(), response, Duration.ofMillis(100)))
            .onErrorMap(error -> new RuntimeException("DELETE request failed", error));
    }
    
    private void recordSuccess(String toolName, Instant startTime) {
        long executionTime = Duration.between(startTime, Instant.now()).toMillis();
        
        statsRepository.findById(toolName)
            .switchIfEmpty(Mono.just(ToolUsageStatsEntity.create(toolName, UUID.randomUUID())))
            .map(stats -> stats.recordSuccess(executionTime))
            .flatMap(statsRepository::save)
            .subscribe(
                stats -> logger.debug("Updated success stats for tool: {}", toolName),
                error -> logger.error("Failed to update success stats for tool: {}", toolName, error)
            );
    }
    
    private void recordError(String toolName, Instant startTime, String errorMessage) {
        long executionTime = Duration.between(startTime, Instant.now()).toMillis();
        
        statsRepository.findById(toolName)
            .switchIfEmpty(Mono.just(ToolUsageStatsEntity.create(toolName, UUID.randomUUID())))
            .map(stats -> stats.recordError(executionTime, errorMessage))
            .flatMap(statsRepository::save)
            .subscribe(
                stats -> logger.debug("Updated error stats for tool: {}", toolName),
                error -> logger.error("Failed to update error stats for tool: {}", toolName, error)
            );
    }
    
    private String extractHttpMethod(McpTool tool) {
        if (tool.metadata() != null && tool.metadata().containsKey("httpMethod")) {
            return tool.metadata().get("httpMethod").toString();
        }
        return "GET";
    }
    
    private String extractEndpoint(McpTool tool) {
        if (tool.metadata() != null && tool.metadata().containsKey("endpoint")) {
            return tool.metadata().get("endpoint").toString();
        }
        return "/";
    }
    
    private ToolInvoker.ToolHealthStatus.HealthState determineHealthState(ToolExecutionStats stats) {
        double errorRate = 1.0 - stats.successRate();
        if (errorRate > 0.5) return ToolInvoker.ToolHealthStatus.HealthState.UNHEALTHY;
        if (errorRate > 0.1) return ToolInvoker.ToolHealthStatus.HealthState.DEGRADED;
        if (stats.totalInvocations() == 0) return ToolInvoker.ToolHealthStatus.HealthState.UNKNOWN;
        return ToolInvoker.ToolHealthStatus.HealthState.HEALTHY;
    }
    
    private String getHealthMessage(ToolInvoker.ToolHealthStatus.HealthState state, ToolExecutionStats stats) {
        double errorRate = 1.0 - stats.successRate();
        return switch (state) {
            case HEALTHY -> "Tool is operating normally";
            case DEGRADED -> String.format("Tool has elevated error rate: %.1f%%", errorRate * 100);
            case UNHEALTHY -> String.format("Tool is experiencing high error rate: %.1f%%", errorRate * 100);
            case UNKNOWN -> "Tool health status unknown - no recent executions";
        };
    }
}
