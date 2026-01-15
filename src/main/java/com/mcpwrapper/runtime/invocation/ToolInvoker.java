/*
 * MCP REST Adapter - Tool Invocation Engine Interface
 * 
 * This file defines the core interface for executing MCP tools. The Tool Invoker
 * is responsible for orchestrating the complete tool execution lifecycle, from
 * input validation through REST API invocation to result transformation.
 * 
 * The invoker implements comprehensive safety measures including input validation,
 * authentication, rate limiting, circuit breakers, and error handling to ensure
 * reliable and secure tool execution in production environments.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mcpwrapper.runtime.invocation;

import com.mcpwrapper.transport.mcp.McpToolCall;
import com.mcpwrapper.transport.mcp.McpToolResult;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Core interface for executing Model Context Protocol (MCP) tools.
 * 
 * The Tool Invoker orchestrates the complete tool execution lifecycle:
 * 
 * 1. **Input Validation**: Validates arguments against the tool's JSON Schema
 * 2. **Authentication**: Applies required authentication for the target API
 * 3. **Request Transformation**: Converts MCP arguments to REST API format
 * 4. **API Invocation**: Executes the REST API call with resilience patterns
 * 5. **Response Processing**: Transforms API responses to MCP result format
 * 6. **Error Handling**: Maps errors to appropriate MCP error codes
 * 7. **Observability**: Records metrics, logs, and traces for monitoring
 * 
 * The invoker is designed for:
 * - **High Performance**: Non-blocking, reactive execution
 * - **Reliability**: Circuit breakers, retries, and timeouts
 * - **Security**: Input sanitization and authentication enforcement
 * - **Observability**: Comprehensive metrics and distributed tracing
 * - **Scalability**: Efficient resource utilization and backpressure handling
 * 
 * Example Usage:
 * ```java
 * McpToolCall toolCall = McpToolCall.builder()
 *     .name("petstore_list_pets")
 *     .arguments(objectMapper.valueToTree(Map.of("limit", 10)))
 *     .build();
 * 
 * invoker.invokeTool(toolCall)
 *     .doOnNext(result -> {
 *         if (result.isError()) {
 *             log.error("Tool execution failed: {}", result.errorMessage());
 *         } else {
 *             log.info("Tool executed successfully in {}ms", 
 *                     result.executionTime().toMillis());
 *         }
 *     })
 *     .subscribe();
 * ```
 * 
 * Execution Flow:
 * ```
 * ToolCall → Validation → Authentication → Transformation → 
 * API Call → Response Processing → Result → Metrics/Logging
 * ```
 * 
 * Error Handling:
 * The invoker maps various error conditions to standardized MCP error codes:
 * - Input validation failures → `invalid_arguments`
 * - Authentication failures → `unauthorized`
 * - Missing tools → `tool_not_found`
 * - API errors → `tool_execution_error`
 * - Timeouts → `timeout`
 * - Rate limits → `rate_limited`
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 * @see McpToolCall
 * @see McpToolResult
 * @see ToolExecutionContext
 */
public interface ToolInvoker {
    
    /**
     * Executes a tool with the provided arguments.
     * 
     * This is the primary method for tool execution. The implementation performs
     * the complete execution lifecycle with comprehensive error handling and
     * observability instrumentation.
     * 
     * Execution Steps:
     * 1. **Tool Resolution**: Lookup tool definition in the registry
     * 2. **Input Validation**: Validate arguments against JSON Schema
     * 3. **Context Preparation**: Set up execution context with auth and config
     * 4. **Request Building**: Transform MCP arguments to REST request format
     * 5. **API Invocation**: Execute REST call with resilience patterns
     * 6. **Response Processing**: Transform API response to MCP result
     * 7. **Metrics Recording**: Record execution metrics and traces
     * 
     * The method is fully reactive and non-blocking, supporting high concurrency
     * and efficient resource utilization.
     * 
     * Security Measures:
     * - Input sanitization to prevent injection attacks
     * - Authentication token validation and refresh
     * - Rate limiting enforcement
     * - Request size limits
     * - Timeout enforcement
     * 
     * @param toolCall the tool invocation request containing tool name and arguments
     * @return a Mono emitting the tool execution result
     * @throws IllegalArgumentException if toolCall is null or invalid
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<McpToolResult> invokeTool(McpToolCall toolCall);
    
    /**
     * Executes a tool with custom execution context.
     * 
     * Allows overriding default execution parameters for specific invocations:
     * - Custom timeouts
     * - Specific authentication credentials
     * - Custom retry policies
     * - Debug/trace flags
     * 
     * This method is useful for:
     * - Administrative tool executions
     * - Testing with custom parameters
     * - High-priority requests requiring different SLAs
     * - Debugging problematic tool calls
     * 
     * @param toolCall the tool invocation request
     * @param context custom execution context and overrides
     * @return a Mono emitting the tool execution result
     * @throws IllegalArgumentException if toolCall or context is null/invalid
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<McpToolResult> invokeTool(McpToolCall toolCall, ToolExecutionContext context);
    
    /**
     * Validates tool arguments without executing the tool.
     * 
     * Performs input validation against the tool's JSON Schema without
     * actually invoking the underlying REST API. Useful for:
     * - Pre-execution validation
     * - Form validation in UIs
     * - Testing tool definitions
     * - API documentation examples
     * 
     * Validation includes:
     * - JSON Schema compliance
     * - Required field presence
     * - Type validation and coercion
     * - Format validation (email, URL, etc.)
     * - Range and length constraints
     * 
     * @param toolCall the tool call to validate
     * @return a Mono that completes successfully if valid, or emits validation errors
     * @throws IllegalArgumentException if toolCall is null
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<ValidationResult> validateToolCall(McpToolCall toolCall);
    
    /**
     * Gets execution statistics for a specific tool.
     * 
     * Returns performance and reliability metrics for the specified tool:
     * - Total invocation count
     * - Success/failure rates
     * - Average execution time
     * - 95th/99th percentile latencies
     * - Error distribution by type
     * - Recent execution trends
     * 
     * @param toolName the name of the tool to get statistics for
     * @return a Mono emitting execution statistics
     * @throws IllegalArgumentException if toolName is null or empty
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<ToolExecutionStats> getToolStats(String toolName);
    
    /**
     * Gets execution statistics for all tools.
     * 
     * Returns aggregate statistics across all registered tools,
     * useful for system-wide monitoring and capacity planning.
     * 
     * @return a Mono emitting overall execution statistics
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<OverallExecutionStats> getOverallStats();
    
    /**
     * Checks if a tool is currently available for execution.
     * 
     * Considers various factors that might prevent tool execution:
     * - Tool registration status
     * - Circuit breaker state
     * - Rate limiting status
     * - External API health
     * - Authentication token validity
     * 
     * @param toolName the name of the tool to check
     * @return a Mono emitting true if the tool is available, false otherwise
     * @throws IllegalArgumentException if toolName is null or empty
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<Boolean> isToolAvailable(String toolName);
    
    /**
     * Gets the current health status of a tool.
     * 
     * Provides detailed health information including:
     * - Overall health status (healthy, degraded, unhealthy)
     * - Circuit breaker state
     * - Recent error rates
     * - API endpoint reachability
     * - Authentication status
     * 
     * @param toolName the name of the tool to check
     * @return a Mono emitting the tool's health status
     * @throws IllegalArgumentException if toolName is null or empty
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<ToolHealthStatus> getToolHealth(String toolName);
    
    /**
     * Cancels an active tool execution.
     * 
     * Attempts to cancel an in-progress tool execution identified by its call ID.
     * The cancellation may not be immediate depending on the execution phase:
     * - Pre-API call: Immediate cancellation
     * - During API call: HTTP request cancellation
     * - Post-API call: Processing may complete before cancellation
     * 
     * @param callId the unique identifier of the tool call to cancel
     * @return a Mono emitting true if cancellation was successful, false otherwise
     * @throws IllegalArgumentException if callId is null or empty
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<Boolean> cancelExecution(String callId);
    
    /**
     * Gets information about currently executing tools.
     * 
     * Returns details about active tool executions for monitoring and debugging:
     * - Call IDs and tool names
     * - Execution start times
     * - Current execution phase
     * - Resource usage
     * 
     * @return a Mono emitting a map of call IDs to execution information
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    Mono<Map<String, ActiveExecution>> getActiveExecutions();
    
    /**
     * Represents the execution context for a tool invocation.
     * 
     * Allows customization of execution parameters on a per-request basis.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    record ToolExecutionContext(
        /**
         * Custom timeout for this execution.
         * Overrides the default tool timeout if specified.
         */
        Duration timeout,
        
        /**
         * Custom authentication credentials.
         * Overrides default API authentication if provided.
         */
        Map<String, String> authOverrides,
        
        /**
         * Custom retry policy for this execution.
         * Overrides default retry configuration if specified.
         */
        RetryPolicy retryPolicy,
        
        /**
         * Whether to enable debug mode for this execution.
         * Provides additional logging and error details.
         */
        boolean debugMode,
        
        /**
         * Custom headers to include in the API request.
         * Merged with default headers from tool configuration.
         */
        Map<String, String> customHeaders,
        
        /**
         * Priority level for this execution.
         * May affect resource allocation and queue position.
         */
        ExecutionPriority priority
    ) {
        
        /**
         * Creates a default execution context.
         * 
         * @return new context with default settings
         */
        public static ToolExecutionContext defaultContext() {
            return new ToolExecutionContext(
                null, null, null, false, null, ExecutionPriority.NORMAL
            );
        }
        
        /**
         * Creates a debug execution context.
         * 
         * @return new context with debug mode enabled
         */
        public static ToolExecutionContext debugContext() {
            return new ToolExecutionContext(
                null, null, null, true, null, ExecutionPriority.NORMAL
            );
        }
    }
    
    /**
     * Execution priority levels.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    enum ExecutionPriority {
        LOW, NORMAL, HIGH, CRITICAL
    }
    
    /**
     * Retry policy configuration.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    record RetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        Duration maxDelay,
        double backoffMultiplier
    ) {
        
        /**
         * Creates a default retry policy.
         * 
         * @return policy with 3 attempts and exponential backoff
         */
        public static RetryPolicy defaultPolicy() {
            return new RetryPolicy(3, Duration.ofMillis(100), Duration.ofSeconds(5), 2.0);
        }
        
        /**
         * Creates a no-retry policy.
         * 
         * @return policy with single attempt only
         */
        public static RetryPolicy noRetry() {
            return new RetryPolicy(1, Duration.ZERO, Duration.ZERO, 1.0);
        }
    }
    
    /**
     * Result of tool call validation.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    record ValidationResult(
        boolean isValid,
        java.util.List<String> errors,
        java.util.List<String> warnings
    ) {
        
        /**
         * Creates a successful validation result.
         * 
         * @return result indicating successful validation
         */
        public static ValidationResult success() {
            return new ValidationResult(true, java.util.List.of(), java.util.List.of());
        }
        
        /**
         * Creates a failed validation result.
         * 
         * @param errors list of validation errors
         * @return result indicating failed validation
         */
        public static ValidationResult failure(java.util.List<String> errors) {
            return new ValidationResult(false, errors, java.util.List.of());
        }
    }
    
    /**
     * Tool execution statistics.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    record ToolExecutionStats(
        String toolName,
        long totalInvocations,
        long successfulInvocations,
        long failedInvocations,
        double successRate,
        Duration averageExecutionTime,
        Duration p95ExecutionTime,
        Duration p99ExecutionTime,
        Map<String, Long> errorsByType
    ) {}
    
    /**
     * Overall system execution statistics.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    record OverallExecutionStats(
        long totalTools,
        long totalInvocations,
        double overallSuccessRate,
        Duration averageExecutionTime,
        long activeExecutions,
        Map<String, Long> topToolsByUsage
    ) {}
    
    /**
     * Tool health status information.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    record ToolHealthStatus(
        String toolName,
        HealthState state,
        String message,
        java.time.Instant lastChecked,
        Map<String, Object> details
    ) {
        
        /**
         * Health state enumeration.
         */
        public enum HealthState {
            HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN
        }
    }
    
    /**
     * Information about an active tool execution.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    record ActiveExecution(
        String callId,
        String toolName,
        java.time.Instant startTime,
        ExecutionPhase currentPhase,
        Map<String, Object> metadata
    ) {
        
        /**
         * Execution phase enumeration.
         */
        public enum ExecutionPhase {
            VALIDATION, AUTHENTICATION, REQUEST_BUILDING, 
            API_CALL, RESPONSE_PROCESSING, COMPLETED
        }
    }
}
