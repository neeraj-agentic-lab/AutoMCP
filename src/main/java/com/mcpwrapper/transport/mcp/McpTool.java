/*
 * MCP REST Adapter - Model Context Protocol Tool Definition
 * 
 * This file defines the core MCP Tool model that represents a discoverable
 * and invokable tool within the Model Context Protocol ecosystem. Each tool
 * corresponds to a REST API operation that has been parsed from an OpenAPI
 * specification and transformed into MCP-compatible format.
 * 
 * The MCP Tool serves as the contract between LLM agents and REST APIs,
 * providing structured metadata that enables safe and validated tool invocation.
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

/**
 * Represents a Model Context Protocol (MCP) tool definition.
 * 
 * An MCP Tool is a structured representation of a REST API operation that can be
 * discovered and invoked by LLM agents. Each tool encapsulates:
 * 
 * - **Identity**: Unique name and human-readable description
 * - **Input Schema**: JSON Schema defining valid input parameters
 * - **Metadata**: Additional context for categorization and filtering
 * - **Execution Context**: Information needed to invoke the underlying REST API
 * 
 * The tool definition follows the MCP specification for tool discovery and ensures
 * that LLM agents have sufficient information to:
 * 1. Understand what the tool does
 * 2. Provide correct input parameters
 * 3. Handle responses appropriately
 * 
 * Example Usage:
 * ```java
 * McpTool tool = McpTool.builder()
 *     .name("petstore_list_pets")
 *     .description("Retrieve a list of pets from the pet store")
 *     .inputSchema(createJsonSchema())
 *     .metadata(Map.of("category", "pets", "version", "1.0"))
 *     .build();
 * ```
 * 
 * JSON Representation:
 * ```json
 * {
 *   "name": "petstore_list_pets",
 *   "description": "Retrieve a list of pets from the pet store",
 *   "inputSchema": {
 *     "type": "object",
 *     "properties": {
 *       "limit": {"type": "integer", "minimum": 1, "maximum": 100}
 *     }
 *   },
 *   "metadata": {
 *     "category": "pets",
 *     "version": "1.0",
 *     "tags": ["animals", "inventory"]
 *   }
 * }
 * ```
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 * @see McpToolCall
 * @see McpToolResult
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpTool(
    /**
     * Unique identifier for the tool within the MCP server.
     */
    @JsonProperty("name")
    String name,
    
    /**
     * Human-readable description of what the tool does.
     */
    @JsonProperty("description")
    String description,
    
    /**
     * JSON Schema defining the structure and validation rules for tool input.
     */
    @JsonProperty("inputSchema")
    JsonNode inputSchema,
    
    /**
     * Additional metadata providing context and categorization for the tool.
     */
    @JsonProperty("metadata")
    Map<String, Object> metadata,
    
    /**
     * Timestamp when this tool definition was created.
     */
    @JsonProperty("createdAt")
    Instant createdAt,
    
    /**
     * Timestamp when this tool definition was last updated.
     */
    @JsonProperty("updatedAt")
    Instant updatedAt,
    
    /**
     * Version of the tool.
     */
    @JsonProperty("version")
    String version,
    
    /**
     * Whether the tool is deprecated.
     */
    @JsonProperty("deprecated")
    boolean deprecated,
    
    /**
     * Tags for categorizing and searching tools.
     */
    @JsonProperty("tags")
    java.util.List<String> tags
) {
    
    /**
     * Creates a new McpTool with validation.
     */
    public McpTool {
        Objects.requireNonNull(name, "Tool name cannot be null");
        Objects.requireNonNull(description, "Tool description cannot be null");
        Objects.requireNonNull(inputSchema, "Tool input schema cannot be null");
        
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be empty");
        }
        if (description.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool description cannot be empty");
        }
    }
    
    /**
     * Creates a builder for constructing McpTool instances.
     * 
     * @return a new McpToolBuilder instance
     */
    public static McpToolBuilder builder() {
        return new McpToolBuilder();
    }
    
    /**
     * Builder class for creating McpTool instances with fluent API.
     * 
     * Provides a convenient way to construct tools with optional parameters
     * and validation.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public static class McpToolBuilder {
        private String name;
        private String description;
        private JsonNode inputSchema;
        private Map<String, Object> metadata;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private String version = "1.0.0";
        private boolean deprecated = false;
        private java.util.List<String> tags = java.util.List.of();
        
        public McpToolBuilder name(String name) {
            this.name = name;
            return this;
        }
        
        public McpToolBuilder description(String description) {
            this.description = description;
            return this;
        }
        
        public McpToolBuilder inputSchema(JsonNode inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }
        
        public McpToolBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public McpToolBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public McpToolBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public McpToolBuilder version(String version) {
            this.version = version;
            return this;
        }
        
        public McpToolBuilder deprecated(boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }
        
        public McpToolBuilder tags(java.util.List<String> tags) {
            this.tags = tags;
            return this;
        }
        
        public McpTool build() {
            return new McpTool(name, description, inputSchema, metadata, createdAt, updatedAt, version, deprecated, tags);
        }
    }
}
