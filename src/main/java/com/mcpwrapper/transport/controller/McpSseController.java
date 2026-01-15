/*
 * MCP REST Adapter - SSE Controller
 * 
 * Server-Sent Events controller for real-time MCP communication.
 * Provides streaming endpoints for tool execution, status updates,
 * and system events using reactive streams.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.transport.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpwrapper.runtime.service.McpServiceFacade;
import com.mcpwrapper.transport.mcp.McpToolResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE controller for real-time MCP communication.
 * 
 * This controller provides streaming endpoints for:
 * - Real-time tool execution results
 * - System status and health updates
 * - Tool availability changes
 * - Execution progress tracking
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/mcp/stream")
@Tag(name = "MCP Streaming", description = "Real-time streaming endpoints for MCP operations")
public class McpSseController {
    
    private static final Logger logger = LoggerFactory.getLogger(McpSseController.class);
    
    private final McpServiceFacade serviceFacade;
    private final ObjectMapper objectMapper;
    
    // Event sinks for broadcasting
    private final Sinks.Many<SystemEvent> systemEventSink;
    private final Sinks.Many<ToolExecutionEvent> executionEventSink;
    private final Map<String, Sinks.Many<ToolExecutionEvent>> clientSinks;
    
    public McpSseController(McpServiceFacade serviceFacade, ObjectMapper objectMapper) {
        this.serviceFacade = serviceFacade;
        this.objectMapper = objectMapper;
        this.systemEventSink = Sinks.many().multicast().onBackpressureBuffer();
        this.executionEventSink = Sinks.many().multicast().onBackpressureBuffer();
        this.clientSinks = new ConcurrentHashMap<>();
        
        // Start background tasks
        startSystemMonitoring();
        startExecutionMonitoring();
    }
    
    /**
     * Streams system events (health, stats, tool changes).
     */
    @GetMapping(value = "/system", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Stream system events",
        description = "Provides real-time stream of system events including health status, statistics, and tool changes",
        responses = {
            @ApiResponse(responseCode = "200", description = "Event stream established",
                content = @Content(mediaType = "text/event-stream"))
        }
    )
    public Flux<ServerSentEvent<SystemEvent>> streamSystemEvents(
            @Parameter(description = "Client ID for tracking connections")
            @RequestParam(required = false) String clientId) {
        
        logger.info("Starting system event stream for client: {}", clientId);
        
        return systemEventSink.asFlux()
            .map(event -> ServerSentEvent.<SystemEvent>builder()
                .id(event.id())
                .event(event.type())
                .data(event)
                .build())
            .doOnSubscribe(subscription -> logger.debug("Client {} subscribed to system events", clientId))
            .doOnCancel(() -> logger.debug("Client {} cancelled system event subscription", clientId))
            .doOnError(error -> logger.error("System event stream error for client {}", clientId, error));
    }
    
    /**
     * Streams tool execution events.
     */
    @GetMapping(value = "/executions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Stream execution events",
        description = "Provides real-time stream of tool execution events and results",
        responses = {
            @ApiResponse(responseCode = "200", description = "Execution stream established",
                content = @Content(mediaType = "text/event-stream"))
        }
    )
    public Flux<ServerSentEvent<ToolExecutionEvent>> streamExecutionEvents(
            @Parameter(description = "Client ID for tracking connections")
            @RequestParam(required = false) String clientId,
            @Parameter(description = "Filter by tool name")
            @RequestParam(required = false) String toolName) {
        
        logger.info("Starting execution event stream for client: {} (tool filter: {})", clientId, toolName);
        
        Flux<ToolExecutionEvent> eventStream = executionEventSink.asFlux();
        
        // Apply tool name filter if specified
        if (toolName != null && !toolName.trim().isEmpty()) {
            eventStream = eventStream.filter(event -> toolName.equals(event.toolName()));
        }
        
        return eventStream
            .map(event -> ServerSentEvent.<ToolExecutionEvent>builder()
                .id(event.callId())
                .event(event.status())
                .data(event)
                .build())
            .doOnSubscribe(subscription -> logger.debug("Client {} subscribed to execution events", clientId))
            .doOnCancel(() -> logger.debug("Client {} cancelled execution event subscription", clientId));
    }
    
    /**
     * Streams events for a specific tool execution.
     */
    @GetMapping(value = "/executions/{callId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Stream specific execution",
        description = "Provides real-time stream for a specific tool execution by call ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Execution stream established"),
            @ApiResponse(responseCode = "404", description = "Execution not found")
        }
    )
    public Flux<ServerSentEvent<ToolExecutionEvent>> streamExecution(
            @Parameter(description = "Call ID of the execution to stream")
            @PathVariable String callId,
            @Parameter(description = "Client ID for tracking connections")
            @RequestParam(required = false) String clientId) {
        
        logger.info("Starting execution stream for call ID: {} (client: {})", callId, clientId);
        
        return executionEventSink.asFlux()
            .filter(event -> callId.equals(event.callId()))
            .map(event -> ServerSentEvent.<ToolExecutionEvent>builder()
                .id(event.callId())
                .event(event.status())
                .data(event)
                .build())
            .doOnSubscribe(subscription -> logger.debug("Client {} subscribed to execution {}", clientId, callId))
            .doOnCancel(() -> logger.debug("Client {} cancelled execution {} subscription", clientId, callId));
    }
    
    /**
     * Streams tool availability changes.
     */
    @GetMapping(value = "/tools/availability", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Stream tool availability",
        description = "Provides real-time stream of tool availability changes",
        responses = {
            @ApiResponse(responseCode = "200", description = "Availability stream established")
        }
    )
    public Flux<ServerSentEvent<ToolAvailabilityEvent>> streamToolAvailability(
            @Parameter(description = "Client ID for tracking connections")
            @RequestParam(required = false) String clientId) {
        
        logger.info("Starting tool availability stream for client: {}", clientId);
        
        return Flux.interval(Duration.ofSeconds(30)) // Check every 30 seconds
            .flatMap(tick -> serviceFacade.getAllTools()
                .flatMap(tool -> serviceFacade.isToolAvailable(tool.name())
                    .map(available -> new ToolAvailabilityEvent(
                        tool.name(),
                        available,
                        Instant.now()
                    ))))
            .map(event -> ServerSentEvent.<ToolAvailabilityEvent>builder()
                .id(event.toolName() + "_" + event.timestamp().toEpochMilli())
                .event("availability")
                .data(event)
                .build())
            .doOnSubscribe(subscription -> logger.debug("Client {} subscribed to availability events", clientId))
            .doOnCancel(() -> logger.debug("Client {} cancelled availability subscription", clientId));
    }
    
    /**
     * Streams system health status.
     */
    @GetMapping(value = "/health", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Stream health status",
        description = "Provides real-time stream of system health status",
        responses = {
            @ApiResponse(responseCode = "200", description = "Health stream established")
        }
    )
    public Flux<ServerSentEvent<HealthEvent>> streamHealthStatus(
            @Parameter(description = "Client ID for tracking connections")
            @RequestParam(required = false) String clientId) {
        
        logger.info("Starting health status stream for client: {}", clientId);
        
        return Flux.interval(Duration.ofSeconds(15)) // Check every 15 seconds
            .flatMap(tick -> serviceFacade.getSystemHealth()
                .map(health -> new HealthEvent(
                    health.isHealthy(),
                    health.totalTools(),
                    health.enabledConfigurations(),
                    health.unhealthyApis(),
                    Instant.now()
                )))
            .map(event -> ServerSentEvent.<HealthEvent>builder()
                .id("health_" + event.timestamp().toEpochMilli())
                .event("health")
                .data(event)
                .build())
            .doOnSubscribe(subscription -> logger.debug("Client {} subscribed to health events", clientId))
            .doOnCancel(() -> logger.debug("Client {} cancelled health subscription", clientId));
    }
    
    /**
     * Publishes a tool execution event (called by other services).
     */
    public void publishExecutionEvent(ToolExecutionEvent event) {
        executionEventSink.tryEmitNext(event);
    }
    
    /**
     * Publishes a system event (called by other services).
     */
    public void publishSystemEvent(SystemEvent event) {
        systemEventSink.tryEmitNext(event);
    }
    
    // Private helper methods
    
    private void startSystemMonitoring() {
        Flux.interval(Duration.ofMinutes(1))
            .flatMap(tick -> serviceFacade.getSystemStats()
                .map(stats -> new SystemEvent(
                    "stats_" + Instant.now().toEpochMilli(),
                    "system.stats",
                    "System statistics update",
                    Map.of(
                        "totalTools", stats.totalTools(),
                        "totalConfigurations", stats.totalConfigurations(),
                        "enabledConfigurations", stats.enabledConfigurations(),
                        "totalInvocations", stats.totalInvocations(),
                        "successRate", stats.successRate()
                    ),
                    Instant.now()
                )))
            .subscribe(
                systemEventSink::tryEmitNext,
                error -> logger.error("System monitoring error", error)
            );
    }
    
    private void startExecutionMonitoring() {
        Flux.interval(Duration.ofSeconds(5))
            .flatMap(tick -> serviceFacade.getActiveExecutions()
                .filter(executions -> !executions.isEmpty())
                .map(executions -> new SystemEvent(
                    "executions_" + Instant.now().toEpochMilli(),
                    "system.executions",
                    "Active executions update",
                    Map.of("activeCount", executions.size()),
                    Instant.now()
                )))
            .subscribe(
                systemEventSink::tryEmitNext,
                error -> logger.error("Execution monitoring error", error)
            );
    }
    
    // Event DTOs
    
    public record SystemEvent(
        String id,
        String type,
        String message,
        Map<String, Object> data,
        Instant timestamp
    ) {}
    
    public record ToolExecutionEvent(
        String callId,
        String toolName,
        String status,
        McpToolResult result,
        Map<String, Object> metadata,
        Instant timestamp
    ) {}
    
    public record ToolAvailabilityEvent(
        String toolName,
        boolean available,
        Instant timestamp
    ) {}
    
    public record HealthEvent(
        boolean healthy,
        Long totalTools,
        Long enabledConfigurations,
        Long unhealthyApis,
        Instant timestamp
    ) {}
}
