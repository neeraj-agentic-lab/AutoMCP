/*
 * MCP REST Adapter - Production-Grade Model Context Protocol Server
 * 
 * This application serves as a bridge between REST APIs and the Model Context Protocol (MCP),
 * allowing Large Language Model (LLM) agents to discover and invoke arbitrary REST endpoints
 * as MCP tools in a secure, scalable, and enterprise-ready manner.
 * 
 * Key Features:
 * - Dynamic OpenAPI specification parsing and tool generation
 * - Reactive, non-blocking architecture using Spring WebFlux
 * - Server-Sent Events (SSE) for MCP protocol communication
 * - Comprehensive security with API key and OAuth2 support
 * - Production-grade observability with metrics, logging, and tracing
 * - Resilience patterns including circuit breakers and retries
 * 
 * Architecture:
 * The application follows a strict layered architecture:
 * - Transport Layer: MCP protocol communication via SSE
 * - Runtime Layer: Tool registry and invocation engine
 * - Adapter Layer: OpenAPI parsing and REST API integration
 * - Platform Layer: Cross-cutting concerns (auth, metrics, resilience)
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
package com.mcpwrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * Main application class for the MCP REST Adapter.
 * 
 * This Spring Boot application bootstraps a production-grade MCP server that:
 * 
 * 1. **Tool Discovery**: Exposes REST API operations as discoverable MCP tools
 * 2. **Tool Invocation**: Provides secure, validated execution of REST API calls
 * 3. **Protocol Compliance**: Implements MCP over Server-Sent Events (SSE)
 * 4. **Enterprise Features**: Includes authentication, authorization, and observability
 * 
 * The application is designed to be:
 * - **Reactive**: Built on Spring WebFlux for non-blocking I/O
 * - **Scalable**: Handles concurrent requests efficiently
 * - **Secure**: Implements multiple authentication mechanisms
 * - **Observable**: Provides comprehensive metrics and logging
 * - **Resilient**: Includes circuit breakers, retries, and timeouts
 * 
 * Configuration is externalized through YAML files and environment variables,
 * allowing for easy deployment across different environments (dev, staging, prod).
 * 
 * Usage Example:
 * ```bash
 * # Start with default configuration
 * java -jar mcp-rest-adapter-1.0.0.jar
 * 
 * # Start with custom configuration
 * java -jar mcp-rest-adapter-1.0.0.jar --spring.config.location=classpath:/custom-config.yml
 * 
 * # Start with specific profile
 * java -jar mcp-rest-adapter-1.0.0.jar --spring.profiles.active=prod
 * ```
 * 
 * Health Checks:
 * - Application health: GET /actuator/health
 * - Readiness probe: GET /actuator/health/readiness
 * - Liveness probe: GET /actuator/health/liveness
 * 
 * MCP Endpoints:
 * - Tool discovery: GET /mcp/tools/list
 * - Tool invocation: POST /mcp/tools/call
 * - Event streaming: GET /mcp/events (SSE)
 * 
 * @author Neeraj Yadav
 * @see org.springframework.boot.SpringApplication
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 * @since 1.0.0
 */
@SpringBootApplication
@EnableWebFlux
@EnableAsync
@ConfigurationPropertiesScan(basePackages = "com.mcpwrapper")
public class McpRestAdapterApplication {

    /**
     * Main entry point for the MCP REST Adapter application.
     * 
     * This method initializes the Spring Boot application context and starts
     * the embedded web server. The application will:
     * 
     * 1. Load configuration from application.yml and environment variables
     * 2. Initialize all Spring beans and components
     * 3. Parse configured OpenAPI specifications
     * 4. Register MCP tools dynamically
     * 5. Start the reactive web server
     * 6. Begin accepting MCP protocol requests
     * 
     * The application supports various runtime arguments:
     * - `--server.port=8080`: Override the default server port
     * - `--spring.profiles.active=prod`: Activate specific configuration profile
     * - `--mcp.apis.config-path=/path/to/apis.yml`: Custom API configuration path
     * 
     * Environment Variables:
     * - `MCP_SERVER_PORT`: Server port (default: 8080)
     * - `MCP_LOG_LEVEL`: Logging level (default: INFO)
     * - `MCP_METRICS_ENABLED`: Enable metrics collection (default: true)
     * 
     * @param args Command line arguments passed to the application.
     *             These are processed by Spring Boot's argument parsing mechanism.
     * 
     * @throws IllegalStateException if the application fails to start due to
     *         configuration errors, port conflicts, or missing dependencies
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public static void main(String[] args) {
        // Configure system properties for optimal performance
        System.setProperty("reactor.netty.http.server.accessLogEnabled", "true");
        System.setProperty("spring.main.lazy-initialization", "false");
        
        // Start the Spring Boot application
        SpringApplication.run(McpRestAdapterApplication.class, args);
    }
}
