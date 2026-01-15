/*
 * MCP REST Adapter - MCP Controller
 * 
 * Main REST API controller for MCP operations.
 * Provides endpoints for tool discovery, invocation, and management
 * following the MCP protocol specifications.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.transport.controller;

import com.mcpwrapper.adapter.rest.RestAdapterService;
import com.mcpwrapper.runtime.invocation.ToolInvoker;
import com.mcpwrapper.runtime.service.McpServiceFacade;
import com.mcpwrapper.transport.mcp.McpTool;
import com.mcpwrapper.transport.mcp.McpToolCall;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * REST API controller for MCP operations.
 * 
 * This controller provides the main MCP protocol endpoints:
 * - Tool discovery and listing
 * - Tool invocation and execution
 * - Tool statistics and monitoring
 * - System health and status
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/mcp")
@Tag(name = "MCP Operations", description = "Model Context Protocol operations for tool management and execution")
public class McpController {
    
    private static final Logger logger = LoggerFactory.getLogger(McpController.class);
    
    private final McpServiceFacade serviceFacade;
    private final RestAdapterService restAdapter;
    
    public McpController(McpServiceFacade serviceFacade, RestAdapterService restAdapter) {
        this.serviceFacade = serviceFacade;
        this.restAdapter = restAdapter;
    }
    
    /**
     * Lists all available MCP tools.
     */
    @GetMapping("/tools")
    @Operation(
        summary = "List all available tools",
        description = "Returns a list of all MCP tools available for invocation",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved tools"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
        }
    )
    public Flux<McpTool> listTools(
            @Parameter(description = "Filter tools by name pattern")
            @RequestParam(required = false) String namePattern) {
        
        logger.debug("Listing tools with pattern: {}", namePattern);
        
        if (namePattern != null && !namePattern.trim().isEmpty()) {
            return serviceFacade.searchTools(namePattern);
        }
        
        return serviceFacade.getAllTools()
            .doOnComplete(() -> logger.debug("Completed tool listing"));
    }
    
    /**
     * Gets a specific tool by name.
     */
    @GetMapping("/tools/{toolName}")
    @Operation(
        summary = "Get tool by name",
        description = "Returns detailed information about a specific tool",
        responses = {
            @ApiResponse(responseCode = "200", description = "Tool found"),
            @ApiResponse(responseCode = "404", description = "Tool not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
        }
    )
    public Mono<ResponseEntity<McpTool>> getTool(
            @Parameter(description = "Name of the tool to retrieve")
            @PathVariable String toolName) {
        
        logger.debug("Getting tool: {}", toolName);
        
        return serviceFacade.getTool(toolName)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnSuccess(response -> logger.debug("Retrieved tool: {} (status: {})", 
                toolName, response.getStatusCode()));
    }
    
    /**
     * Invokes a tool with the provided parameters.
     */
    @PostMapping("/tools/{toolName}/invoke")
    @Operation(
        summary = "Invoke a tool",
        description = "Executes a tool with the provided parameters and returns the result",
        responses = {
            @ApiResponse(responseCode = "200", description = "Tool executed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid tool call parameters"),
            @ApiResponse(responseCode = "404", description = "Tool not found"),
            @ApiResponse(responseCode = "500", description = "Tool execution failed")
        }
    )
    public Mono<ResponseEntity<McpToolResult>> invokeTool(
            @Parameter(description = "Name of the tool to invoke")
            @PathVariable String toolName,
            @Parameter(description = "Tool invocation request")
            @RequestBody ToolInvocationRequest request) {
        
        logger.info("Invoking tool: {} with call ID: {}", toolName, request.callId());
        System.out.println("=== CONTROLLER DEBUG: Request details - callId: " + request.callId() + 
            ", arguments: " + request.arguments() + ", context: " + request.context());
        logger.debug("=== CONTROLLER: Request details - callId: {}, arguments: {}, context: {}", 
            request.callId(), request.arguments(), request.context());
        
        return serviceFacade.getTool(toolName)
            .doOnNext(tool -> {
                System.out.println("=== CONTROLLER DEBUG: Found tool: " + tool.name());
                logger.debug("=== CONTROLLER: Found tool: {}", tool.name());
            })
            .doOnError(error -> {
                System.out.println("=== CONTROLLER DEBUG: Error getting tool: " + error.getMessage());
                logger.error("=== CONTROLLER: Error getting tool: {}", error.getMessage(), error);
            })
            .switchIfEmpty(Mono.error(new ToolNotFoundException("Tool not found: " + toolName)))
            .flatMap(tool -> {
                System.out.println("=== CONTROLLER DEBUG: Building tool call for tool: " + tool.name());
                logger.debug("=== CONTROLLER: Building tool call for tool: {}", tool.name());
                try {
                    McpToolCall toolCall = McpToolCall.builder()
                        .name(toolName)
                        .arguments(request.arguments())
                        .callId(request.callId())
                        .context(request.context())
                        .build();
                    
                    System.out.println("=== CONTROLLER DEBUG: Tool call built successfully, executing REST call");
                    logger.debug("=== CONTROLLER: Tool call built successfully, executing REST call");
                    return restAdapter.executeRestCall(toolCall, tool);
                } catch (Exception e) {
                    System.out.println("=== CONTROLLER DEBUG: Error building tool call: " + e.getMessage());
                    logger.error("=== CONTROLLER: Error building tool call: {}", e.getMessage(), e);
                    return Mono.error(e);
                }
            })
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }
    
    /**
     * Validates a tool call without executing it.
     */
    @PostMapping("/tools/{toolName}/validate")
    @Operation(
        summary = "Validate tool call",
        description = "Validates tool call parameters without executing the tool",
        responses = {
            @ApiResponse(responseCode = "200", description = "Validation completed"),
            @ApiResponse(responseCode = "404", description = "Tool not found")
        }
    )
    public Mono<ResponseEntity<ValidationResponse>> validateToolCall(
            @Parameter(description = "Name of the tool to validate")
            @PathVariable String toolName,
            @Parameter(description = "Tool call validation request")
            @Valid @RequestBody ToolInvocationRequest request) {
        
        logger.debug("Validating tool call: {}", toolName);
        
        return serviceFacade.getTool(toolName)
            .switchIfEmpty(Mono.error(new ToolNotFoundException("Tool not found: " + toolName)))
            .flatMap(tool -> {
                McpToolCall toolCall = McpToolCall.builder()
                    .name(toolName)
                    .arguments(request.arguments())
                    .callId(request.callId())
                    .build();
                
                return Mono.zip(
                    serviceFacade.validateToolCall(toolCall),
                    restAdapter.validateRestCall(toolCall, tool)
                );
            })
            .map(tuple -> {
                ToolInvoker.ValidationResult mcpValidation = tuple.getT1();
                RestAdapterService.RestValidationResult restValidation = tuple.getT2();
                
                boolean isValid = mcpValidation.isValid() && restValidation.isValid();
                java.util.List<String> errors = new java.util.ArrayList<>();
                
                if (!mcpValidation.isValid()) {
                    errors.add("MCP validation failed");
                    errors.addAll(mcpValidation.errors());
                }
                
                if (!restValidation.isValid()) {
                    errors.addAll(restValidation.errors());
                }
                
                return ResponseEntity.ok(new ValidationResponse(isValid, errors));
            })
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Gets tool execution statistics.
     */
    @GetMapping("/tools/{toolName}/stats")
    @Operation(
        summary = "Get tool statistics",
        description = "Returns execution statistics for a specific tool",
        responses = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved"),
            @ApiResponse(responseCode = "404", description = "Tool not found")
        }
    )
    public Mono<ResponseEntity<ToolInvoker.ToolExecutionStats>> getToolStats(
            @Parameter(description = "Name of the tool")
            @PathVariable String toolName) {
        
        logger.debug("Getting stats for tool: {}", toolName);
        
        return serviceFacade.getToolStats(toolName)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Checks if a tool is available for execution.
     */
    @GetMapping("/tools/{toolName}/availability")
    @Operation(
        summary = "Check tool availability",
        description = "Checks if a tool is available and ready for execution",
        responses = {
            @ApiResponse(responseCode = "200", description = "Availability status retrieved")
        }
    )
    public Mono<AvailabilityResponse> checkToolAvailability(
            @Parameter(description = "Name of the tool")
            @PathVariable String toolName) {
        
        logger.debug("Checking availability for tool: {}", toolName);
        
        return serviceFacade.isToolAvailable(toolName)
            .map(available -> new AvailabilityResponse(toolName, available))
            .defaultIfEmpty(new AvailabilityResponse(toolName, false));
    }
    
    /**
     * Gets overall system statistics.
     */
    @GetMapping("/stats")
    @Operation(
        summary = "Get system statistics",
        description = "Returns overall system execution statistics",
        responses = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved")
        }
    )
    public Mono<SystemStatsResponse> getSystemStats() {
        logger.debug("Getting system statistics");
        
        return Mono.zip(
            serviceFacade.getOverallStats(),
            serviceFacade.getToolCount(),
            serviceFacade.getActiveExecutions()
        ).map(tuple -> new SystemStatsResponse(
            tuple.getT1(),
            tuple.getT2(),
            tuple.getT3().size()
        ));
    }
    
    /**
     * Gets active tool executions.
     */
    @GetMapping("/executions")
    @Operation(
        summary = "Get active executions",
        description = "Returns currently active tool executions",
        responses = {
            @ApiResponse(responseCode = "200", description = "Active executions retrieved")
        }
    )
    public Mono<Map<String, ToolInvoker.ActiveExecution>> getActiveExecutions() {
        logger.debug("Getting active executions");
        
        return serviceFacade.getActiveExecutions();
    }
    
    /**
     * Cancels a tool execution.
     */
    @DeleteMapping("/executions/{callId}")
    @Operation(
        summary = "Cancel tool execution",
        description = "Cancels an active tool execution by call ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Execution cancelled"),
            @ApiResponse(responseCode = "404", description = "Execution not found")
        }
    )
    public Mono<ResponseEntity<CancellationResponse>> cancelExecution(
            @Parameter(description = "Call ID of the execution to cancel")
            @PathVariable String callId) {
        
        logger.info("Cancelling execution: {}", callId);
        
        return serviceFacade.getActiveExecutions()
            .flatMap(executions -> {
                if (!executions.containsKey(callId)) {
                    return Mono.just(ResponseEntity.notFound().<CancellationResponse>build());
                }
                
                // TODO: Implement actual cancellation logic
                return Mono.just(ResponseEntity.ok(new CancellationResponse(callId, true, "Execution cancelled")));
            });
    }
    
    // Request/Response DTOs
    
    public record ToolInvocationRequest(
        String callId,
        com.fasterxml.jackson.databind.JsonNode arguments,
        Map<String, Object> context
    ) {}
    
    public record ValidationResponse(
        boolean isValid,
        java.util.List<String> errors
    ) {}
    
    public record AvailabilityResponse(
        String toolName,
        boolean available
    ) {}
    
    public record SystemStatsResponse(
        ToolInvoker.OverallExecutionStats executionStats,
        Long totalTools,
        Integer activeExecutions
    ) {}
    
    public record CancellationResponse(
        String callId,
        boolean cancelled,
        String message
    ) {}
    
    // Exception classes
    
    public static class ToolNotFoundException extends RuntimeException {
        public ToolNotFoundException(String message) {
            super(message);
        }
    }
}
