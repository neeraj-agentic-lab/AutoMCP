/*
 * MCP REST Adapter - Model Context Protocol Tool Invocation Request
 * 
 * This file defines the MCP Tool Call model that represents a request from an
 * LLM agent to invoke a specific tool. The tool call contains the tool identifier
 * and the arguments needed to execute the underlying REST API operation.
 * 
 * Tool calls are validated against the tool's input schema before execution
 * to ensure type safety and prevent malformed requests from reaching external APIs.
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
package com.mcpwrapper.transport.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a Model Context Protocol (MCP) tool invocation request.
 * 
 * An MCP Tool Call encapsulates a request from an LLM agent to execute a specific
 * tool with provided arguments. The tool call includes:
 * 
 * - **Tool Identity**: The name of the tool to invoke
 * - **Arguments**: Input parameters for the tool execution
 * - **Request Context**: Metadata about the invocation request
 * - **Tracing Information**: Correlation IDs for observability
 * 
 * The tool call undergoes validation against the target tool's input schema
 * before execution to ensure:
 * 1. All required parameters are provided
 * 2. Parameter types match the schema
 * 3. Values meet validation constraints
 * 4. No unexpected parameters are included
 * 
 * Example Usage:
 * ```java
 * McpToolCall toolCall = McpToolCall.builder()
 *     .name("petstore_list_pets")
 *     .arguments(Map.of("limit", 10, "status", "available"))
 *     .build();
 * ```
 * 
 * JSON Representation:
 * ```json
 * {
 *   "name": "petstore_list_pets",
 *   "arguments": {
 *     "limit": 10,
 *     "status": "available"
 *   },
 *   "callId": "550e8400-e29b-41d4-a716-446655440000",
 *   "timestamp": "2025-12-16T08:15:30.123Z",
 *   "context": {
 *     "userId": "agent-123",
 *     "sessionId": "session-456"
 *   }
 * }
 * ```
 * 
 * Security Considerations:
 * - Arguments are validated against JSON Schema to prevent injection attacks
 * - String parameters are sanitized to prevent command injection
 * - Request size limits are enforced to prevent DoS attacks
 * - Authentication context is preserved for audit logging
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 * @see McpTool
 * @see McpToolResult
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolCall(
    /**
     * Name of the tool to invoke.
     * 
     * Must match exactly with a registered tool name in the tool registry.
     * The name is case-sensitive and should follow the same naming convention
     * as defined in {@link McpTool#name()}.
     * 
     * Examples:
     * - "petstore_list_pets"
     * - "user_management_create_user"
     * - "inventory_update_stock"
     * 
     * @return the tool name to invoke, never null or empty
     */
    @JsonProperty("name")
    String name,
    
    /**
     * Arguments to pass to the tool during execution.
     * 
     * The arguments must conform to the tool's input schema as defined in
     * {@link McpTool#inputSchema()}. The structure can be:
     * - Simple key-value pairs for basic parameters
     * - Nested objects for complex data structures
     * - Arrays for list parameters
     * 
     * Validation Process:
     * 1. Schema validation against tool's input schema
     * 2. Type coercion where safe and appropriate
     * 3. Range and format validation
     * 4. Required field verification
     * 
     * Example Arguments:
     * ```json
     * {
     *   "limit": 25,
     *   "filter": {
     *     "status": "active",
     *     "category": "electronics"
     *   },
     *   "sort": ["name", "price"]
     * }
     * ```
     * 
     * @return the tool arguments as a JSON object, may be null for parameterless tools
     */
    @JsonProperty("arguments")
    JsonNode arguments,
    
    /**
     * Unique identifier for this specific tool call.
     * 
     * Used for:
     * - Request correlation across distributed systems
     * - Duplicate request detection
     * - Audit logging and tracing
     * - Asynchronous response matching
     * 
     * The call ID is automatically generated if not provided and should be
     * unique across all tool calls within a reasonable time window.
     * 
     * @return unique call identifier, never null
     */
    @JsonProperty("callId")
    String callId,
    
    /**
     * Timestamp when the tool call was created.
     * 
     * Used for:
     * - Request timeout calculation
     * - Performance monitoring
     * - Audit logging
     * - Request ordering in async scenarios
     * 
     * The timestamp is automatically set to the current time if not provided.
     * 
     * @return call creation timestamp, never null
     */
    @JsonProperty("timestamp")
    Instant timestamp,
    
    /**
     * Additional context information for the tool call.
     * 
     * Context can include:
     * - **userId**: Identifier of the requesting user or agent
     * - **sessionId**: Session identifier for request grouping
     * - **traceId**: Distributed tracing correlation ID
     * - **priority**: Request priority level
     * - **timeout**: Custom timeout for this specific call
     * - **retryPolicy**: Custom retry configuration
     * 
     * This information is used for:
     * - Authentication and authorization
     * - Request routing and load balancing
     * - Observability and monitoring
     * - Custom execution policies
     * 
     * @return context metadata map, may be null or empty
     */
    @JsonProperty("context")
    Map<String, Object> context
) {
    
    /**
     * Creates a new McpToolCall with validation.
     * 
     * @param name tool name to invoke
     * @param arguments input parameters for the tool
     * @param callId unique call identifier
     * @param timestamp call creation time
     * @param context additional request context
     * 
     * @throws IllegalArgumentException if name is null/empty or callId is null/empty
     */
    public McpToolCall {
        Objects.requireNonNull(name, "Tool name cannot be null");
        Objects.requireNonNull(callId, "Call ID cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be empty");
        }
        if (callId.trim().isEmpty()) {
            throw new IllegalArgumentException("Call ID cannot be empty");
        }
    }
    
    /**
     * Creates a builder for constructing McpToolCall instances.
     * 
     * @return a new McpToolCallBuilder instance
     */
    public static McpToolCallBuilder builder() {
        return new McpToolCallBuilder();
    }
    
    /**
     * Convenience method to create a simple tool call with just name and arguments.
     * 
     * @param name the tool name
     * @param arguments the tool arguments
     * @return a new McpToolCall with generated ID and current timestamp
     */
    public static McpToolCall of(String name, JsonNode arguments) {
        return new McpToolCall(
            name,
            arguments,
            UUID.randomUUID().toString(),
            Instant.now(),
            null
        );
    }
    
    /**
     * Builder class for creating McpToolCall instances with fluent API.
     * 
     * Provides convenient methods for constructing tool calls with automatic
     * generation of IDs and timestamps when not explicitly provided.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public static class McpToolCallBuilder {
        private String name;
        private JsonNode arguments;
        private String callId = UUID.randomUUID().toString();
        private Instant timestamp = Instant.now();
        private Map<String, Object> context;
        
        /**
         * Sets the tool name to invoke.
         * 
         * @param name the tool name
         * @return this builder instance
         */
        public McpToolCallBuilder name(String name) {
            this.name = name;
            return this;
        }
        
        /**
         * Sets the tool arguments.
         * 
         * @param arguments input parameters for the tool
         * @return this builder instance
         */
        public McpToolCallBuilder arguments(JsonNode arguments) {
            this.arguments = arguments;
            return this;
        }
        
        /**
         * Sets the call ID.
         * 
         * @param callId unique identifier for this call
         * @return this builder instance
         */
        public McpToolCallBuilder callId(String callId) {
            this.callId = callId;
            return this;
        }
        
        /**
         * Sets the call timestamp.
         * 
         * @param timestamp when the call was created
         * @return this builder instance
         */
        public McpToolCallBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        /**
         * Sets the call context.
         * 
         * @param context additional metadata for the call
         * @return this builder instance
         */
        public McpToolCallBuilder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }
        
        /**
         * Builds the McpToolCall instance.
         * 
         * @return new McpToolCall with configured properties
         * @throws IllegalArgumentException if required fields are missing
         */
        public McpToolCall build() {
            return new McpToolCall(name, arguments, callId, timestamp, context);
        }
    }
}
