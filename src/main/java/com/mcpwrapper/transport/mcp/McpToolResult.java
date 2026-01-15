/*
 * MCP REST Adapter - Model Context Protocol Tool Execution Result
 * 
 * This file defines the MCP Tool Result model that represents the outcome of
 * a tool invocation. The result encapsulates both successful responses and
 * error conditions, providing structured feedback to LLM agents about the
 * execution of REST API operations.
 * 
 * Results follow the MCP specification for tool responses and include
 * comprehensive error information to help agents understand and handle
 * various failure scenarios appropriately.
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the result of a Model Context Protocol (MCP) tool execution.
 * 
 * An MCP Tool Result encapsulates the outcome of invoking a tool, including:
 * 
 * - **Content**: The actual response data from the REST API
 * - **Status Information**: Success/failure indicators and metadata
 * - **Error Details**: Comprehensive error information when applicable
 * - **Performance Metrics**: Execution timing and resource usage
 * - **Correlation Data**: Request tracking and audit information
 * 
 * The result structure follows the MCP specification and provides LLM agents
 * with sufficient information to:
 * 1. Process successful responses appropriately
 * 2. Handle errors gracefully with proper context
 * 3. Make informed decisions about retry strategies
 * 4. Provide meaningful feedback to end users
 * 
 * Example Successful Result:
 * ```java
 * McpToolResult result = McpToolResult.success()
 *     .content(List.of(
 *         new TextContent("Found 3 pets: Fluffy, Buddy, Goldie"),
 *         new JsonContent(petsJsonData)
 *     ))
 *     .callId("550e8400-e29b-41d4-a716-446655440000")
 *     .build();
 * ```
 * 
 * Example Error Result:
 * ```java
 * McpToolResult result = McpToolResult.error()
 *     .errorCode("invalid_arguments")
 *     .errorMessage("Parameter 'limit' must be between 1 and 100")
 *     .callId("550e8400-e29b-41d4-a716-446655440000")
 *     .build();
 * ```
 * 
 * JSON Representation (Success):
 * ```json
 * {
 *   "content": [
 *     {
 *       "type": "text",
 *       "text": "Found 3 pets in the inventory"
 *     },
 *     {
 *       "type": "json",
 *       "data": {"pets": [...]}
 *     }
 *   ],
 *   "isError": false,
 *   "callId": "550e8400-e29b-41d4-a716-446655440000",
 *   "executionTime": "PT0.245S",
 *   "timestamp": "2025-12-16T08:15:30.368Z"
 * }
 * ```
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 * @see McpTool
 * @see McpToolCall
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolResult(
    /**
     * The content returned by the tool execution.
     * 
     * Content is represented as a list of content blocks, where each block
     * can be of different types:
     * - **Text**: Human-readable text responses
     * - **JSON**: Structured data from the API
     * - **Binary**: File or image data (base64 encoded)
     * - **Error**: Error-specific content with details
     * 
     * For successful executions, content typically includes:
     * - A summary text block describing the result
     * - The raw JSON response from the REST API
     * - Any additional context or metadata
     * 
     * For error cases, content may include:
     * - Error description and guidance
     * - Debugging information (in development mode)
     * - Suggested corrective actions
     * 
     * @return list of content blocks, may be null for pure status responses
     */
    @JsonProperty("content")
    List<ContentBlock> content,
    
    /**
     * Indicates whether this result represents an error condition.
     * 
     * When true, the result represents a failure in tool execution, and
     * additional error information will be available in {@link #errorCode},
     * {@link #errorMessage}, and {@link #errorDetails}.
     * 
     * When false, the result represents successful execution, and the
     * {@link #content} field contains the actual response data.
     * 
     * @return true if this is an error result, false for success
     */
    @JsonProperty("isError")
    boolean isError,
    
    /**
     * Standardized error code for failed tool executions.
     * 
     * Error codes follow the MCP specification and include:
     * - **invalid_arguments**: Input validation failed
     * - **unauthorized**: Authentication or authorization failed
     * - **tool_not_found**: Requested tool does not exist
     * - **tool_execution_error**: Error during REST API invocation
     * - **timeout**: Request exceeded configured timeout
     * - **rate_limited**: Request was throttled due to rate limits
     * - **service_unavailable**: External API is temporarily unavailable
     * 
     * Only present when {@link #isError} is true.
     * 
     * @return standardized error code, null for successful results
     */
    @JsonProperty("errorCode")
    String errorCode,
    
    /**
     * Human-readable error message describing what went wrong.
     * 
     * The error message should be:
     * - Clear and actionable for developers
     * - Safe to display to end users (no sensitive information)
     * - Specific enough to aid in troubleshooting
     * - Consistent in format and tone
     * 
     * Examples:
     * - "Parameter 'email' must be a valid email address"
     * - "API rate limit exceeded. Try again in 60 seconds"
     * - "External service is temporarily unavailable"
     * 
     * Only present when {@link #isError} is true.
     * 
     * @return human-readable error description, null for successful results
     */
    @JsonProperty("errorMessage")
    String errorMessage,
    
    /**
     * Additional error details for debugging and troubleshooting.
     * 
     * May include:
     * - **httpStatus**: HTTP status code from the external API
     * - **apiErrorCode**: Error code from the external API
     * - **requestId**: External API request identifier
     * - **retryAfter**: Suggested retry delay for rate-limited requests
     * - **validationErrors**: Detailed field-level validation failures
     * - **stackTrace**: Exception stack trace (development mode only)
     * 
     * This information is primarily for debugging and should not be
     * displayed directly to end users in production environments.
     * 
     * Only present when {@link #isError} is true.
     * 
     * @return additional error context, null for successful results
     */
    @JsonProperty("errorDetails")
    Map<String, Object> errorDetails,
    
    /**
     * Correlation ID linking this result to the original tool call.
     * 
     * Must match the {@link McpToolCall#callId()} of the request that
     * generated this result. Used for:
     * - Request/response correlation in async scenarios
     * - Audit logging and tracing
     * - Duplicate detection
     * - Performance monitoring
     * 
     * @return call correlation ID, never null
     */
    @JsonProperty("callId")
    String callId,
    
    /**
     * Total time taken to execute the tool.
     * 
     * Includes:
     * - Input validation time
     * - REST API call duration
     * - Response processing time
     * - Any retry attempts
     * 
     * Used for:
     * - Performance monitoring and alerting
     * - SLA compliance tracking
     * - Capacity planning
     * - Timeout tuning
     * 
     * @return execution duration, never null
     */
    @JsonProperty("executionTime")
    Duration executionTime,
    
    /**
     * Timestamp when the tool execution completed.
     * 
     * Represents the moment when the result was finalized, whether
     * successful or failed. Used for:
     * - Result ordering and sequencing
     * - Performance analysis
     * - Audit logging
     * - Cache expiration calculations
     * 
     * @return completion timestamp, never null
     */
    @JsonProperty("timestamp")
    Instant timestamp,
    
    /**
     * Additional metadata about the tool execution.
     * 
     * May include:
     * - **httpStatus**: HTTP response status from external API
     * - **responseHeaders**: Relevant headers from the API response
     * - **cacheHit**: Whether the result was served from cache
     * - **retryCount**: Number of retry attempts made
     * - **circuitBreakerState**: Circuit breaker status during execution
     * - **apiVersion**: Version of the external API that was called
     * 
     * This metadata provides additional context for monitoring,
     * debugging, and optimization purposes.
     * 
     * @return execution metadata, may be null or empty
     */
    @JsonProperty("metadata")
    Map<String, Object> metadata
) {
    
    /**
     * Creates a new McpToolResult with validation.
     * 
     * @param content response content blocks
     * @param isError whether this represents an error
     * @param errorCode standardized error code (required if isError is true)
     * @param errorMessage human-readable error message (required if isError is true)
     * @param errorDetails additional error context
     * @param callId correlation ID from the original request
     * @param executionTime total execution duration
     * @param timestamp completion timestamp
     * @param metadata additional execution metadata
     * 
     * @throws IllegalArgumentException if validation fails
     */
    public McpToolResult {
        Objects.requireNonNull(callId, "Call ID cannot be null");
        Objects.requireNonNull(executionTime, "Execution time cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        
        if (callId.trim().isEmpty()) {
            throw new IllegalArgumentException("Call ID cannot be empty");
        }
        
        if (isError) {
            Objects.requireNonNull(errorCode, "Error code is required for error results");
            Objects.requireNonNull(errorMessage, "Error message is required for error results");
            
            if (errorCode.trim().isEmpty()) {
                throw new IllegalArgumentException("Error code cannot be empty");
            }
            if (errorMessage.trim().isEmpty()) {
                throw new IllegalArgumentException("Error message cannot be empty");
            }
        }
    }
    
    /**
     * Creates a builder for successful tool results.
     * 
     * @return a new builder configured for success results
     */
    public static McpToolResultBuilder success() {
        return new McpToolResultBuilder().isError(false);
    }
    
    /**
     * Creates a builder for error tool results.
     * 
     * @return a new builder configured for error results
     */
    public static McpToolResultBuilder error() {
        return new McpToolResultBuilder().isError(true);
    }
    
    /**
     * Creates a successful result with content and call ID.
     */
    public static McpToolResult success(String callId, Object content, Duration executionTime) {
        return success()
            .callId(callId)
            .content(java.util.List.of(new ContentBlock("json", null, 
                content instanceof JsonNode ? (JsonNode) content : 
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(content), null, null)))
            .executionTime(executionTime)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Creates an error result with call ID and error details.
     */
    public static McpToolResult error(String callId, String errorCode, String errorMessage, 
                                    Map<String, Object> errorDetails, Duration executionTime) {
        return error()
            .callId(callId)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .errorDetails(errorDetails)
            .executionTime(executionTime)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Represents a content block within a tool result.
     * 
     * Content blocks allow for rich, multi-format responses that can include
     * text, structured data, and binary content.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContentBlock(
        /**
         * Type of content in this block.
         * 
         * Standard types include:
         * - "text": Plain text content
         * - "json": Structured JSON data
         * - "binary": Base64-encoded binary data
         * - "error": Error-specific content
         * 
         * @return content type identifier
         */
        @JsonProperty("type")
        String type,
        
        /**
         * Text content for "text" type blocks.
         * 
         * @return text content, null for non-text blocks
         */
        @JsonProperty("text")
        String text,
        
        /**
         * JSON data for "json" type blocks.
         * 
         * @return structured data, null for non-JSON blocks
         */
        @JsonProperty("data")
        JsonNode data,
        
        /**
         * Base64-encoded binary data for "binary" type blocks.
         * 
         * @return encoded binary content, null for non-binary blocks
         */
        @JsonProperty("binary")
        String binary,
        
        /**
         * MIME type for binary content.
         * 
         * @return content type, null for non-binary blocks
         */
        @JsonProperty("mimeType")
        String mimeType
    ) {
        
        /**
         * Creates a text content block.
         * 
         * @param text the text content
         * @return new text content block
         */
        public static ContentBlock text(String text) {
            return new ContentBlock("text", text, null, null, null);
        }
        
        /**
         * Creates a JSON content block.
         * 
         * @param data the JSON data
         * @return new JSON content block
         */
        public static ContentBlock json(JsonNode data) {
            return new ContentBlock("json", null, data, null, null);
        }
        
        /**
         * Creates a binary content block.
         * 
         * @param binary base64-encoded binary data
         * @param mimeType MIME type of the content
         * @return new binary content block
         */
        public static ContentBlock binary(String binary, String mimeType) {
            return new ContentBlock("binary", null, null, binary, mimeType);
        }
    }
    
    /**
     * Builder class for creating McpToolResult instances.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public static class McpToolResultBuilder {
        private List<ContentBlock> content;
        private boolean isError;
        private String errorCode;
        private String errorMessage;
        private Map<String, Object> errorDetails;
        private String callId;
        private Duration executionTime;
        private Instant timestamp = Instant.now();
        private Map<String, Object> metadata;
        
        public McpToolResultBuilder content(List<ContentBlock> content) {
            this.content = content;
            return this;
        }
        
        public McpToolResultBuilder isError(boolean isError) {
            this.isError = isError;
            return this;
        }
        
        public McpToolResultBuilder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }
        
        public McpToolResultBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public McpToolResultBuilder errorDetails(Map<String, Object> errorDetails) {
            this.errorDetails = errorDetails;
            return this;
        }
        
        public McpToolResultBuilder callId(String callId) {
            this.callId = callId;
            return this;
        }
        
        public McpToolResultBuilder executionTime(Duration executionTime) {
            this.executionTime = executionTime;
            return this;
        }
        
        public McpToolResultBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public McpToolResultBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public McpToolResult build() {
            return new McpToolResult(
                content, isError, errorCode, errorMessage, errorDetails,
                callId, executionTime, timestamp, metadata
            );
        }
    }
}
