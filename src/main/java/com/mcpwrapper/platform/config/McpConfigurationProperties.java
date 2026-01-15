/*
 * MCP REST Adapter - Configuration Properties
 * 
 * This file defines the configuration properties for the MCP REST Adapter,
 * providing type-safe configuration binding for all application settings.
 * The properties are automatically bound from application.yml and environment
 * variables, supporting externalized configuration for different environments.
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
package com.mcpwrapper.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the MCP REST Adapter application.
 * 
 * This class provides type-safe configuration binding for all application settings,
 * including server configuration, API definitions, security policies, and
 * observability settings. The properties are automatically populated from:
 * 
 * - application.yml configuration files
 * - Environment variables (with MCP_ prefix)
 * - System properties
 * - Command line arguments
 * 
 * The configuration is validated at startup to ensure all required settings
 * are provided and have valid values. Invalid configuration will prevent
 * the application from starting.
 * 
 * Configuration Structure:
 * ```yaml
 * mcp:
 *   server:
 *     max-concurrent-executions: 100
 *     default-timeout: 30s
 *   apis:
 *     petstore:
 *       openapi: classpath:specs/petstore.yaml
 *       base-url: https://api.example.com
 *   security:
 *     auth-enabled: true
 * ```
 * 
 * Environment Variable Examples:
 * - MCP_SERVER_MAX_CONCURRENT_EXECUTIONS=200
 * - MCP_SECURITY_AUTH_ENABLED=true
 * - MCP_APIS_PETSTORE_BASE_URL=https://api.example.com
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
@ConfigurationProperties(prefix = "mcp")
@Validated
public record McpConfigurationProperties(
    
    /**
     * Server configuration settings.
     * 
     * Controls the runtime behavior of the MCP server including
     * concurrency limits, timeouts, and resource constraints.
     */
    @Valid
    @NotNull
    ServerConfig server,
    
    /**
     * Tool registry configuration.
     * 
     * Settings for the tool registry including caching behavior
     * and storage optimization parameters.
     */
    @Valid
    @NotNull
    RegistryConfig registry,
    
    /**
     * API configuration definitions.
     * 
     * Map of API names to their configuration, including OpenAPI
     * specifications, base URLs, and authentication settings.
     */
    @Valid
    @NotNull
    Map<String, ApiConfig> apis,
    
    /**
     * Security configuration settings.
     * 
     * Authentication, authorization, and CORS policies for
     * protecting the MCP endpoints and external API access.
     */
    @Valid
    @NotNull
    SecurityConfig security,
    
    /**
     * Circuit breaker configuration.
     * 
     * Resilience patterns for handling external API failures
     * and preventing cascade failures.
     */
    @Valid
    @NotNull
    CircuitBreakerConfig circuitBreaker,
    
    /**
     * Rate limiting configuration.
     * 
     * Request throttling policies to protect against abuse
     * and ensure fair resource usage.
     */
    @Valid
    @NotNull
    RateLimitConfig rateLimit,
    
    /**
     * Observability configuration.
     * 
     * Settings for metrics collection, distributed tracing,
     * and monitoring integration.
     */
    @Valid
    @NotNull
    ObservabilityConfig observability
) {
    
    /**
     * Server configuration properties.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public record ServerConfig(
        /**
         * Maximum number of concurrent tool executions.
         * 
         * Controls the concurrency level for tool invocations to prevent
         * resource exhaustion. Should be tuned based on available memory
         * and CPU resources.
         * 
         * @return maximum concurrent executions (1-1000)
         */
        @Min(1)
        @Max(1000)
        @DefaultValue("100")
        int maxConcurrentExecutions,
        
        /**
         * Default timeout for tool executions.
         * 
         * Applied to tool invocations that don't specify a custom timeout.
         * Individual tools can override this value in their configuration.
         * 
         * @return default execution timeout
         */
        @NotNull
        @DefaultValue("30s")
        Duration defaultTimeout,
        
        /**
         * Maximum request size for tool invocations.
         * 
         * Prevents denial-of-service attacks through large payloads
         * and ensures memory usage remains bounded.
         * 
         * @return maximum request size
         */
        @NotNull
        @DefaultValue("10MB")
        DataSize maxRequestSize,
        
        /**
         * Enable request/response logging.
         * 
         * When enabled, logs all incoming requests and outgoing responses
         * for debugging purposes. Should be disabled in production for
         * performance and security reasons.
         * 
         * @return true if request logging is enabled
         */
        @DefaultValue("false")
        boolean requestLoggingEnabled
    ) {}
    
    /**
     * Tool registry configuration properties.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public record RegistryConfig(
        /**
         * Initial capacity for tool storage.
         * 
         * Optimizes memory allocation by pre-sizing internal data structures
         * based on expected number of tools.
         * 
         * @return initial storage capacity
         */
        @Min(10)
        @Max(10000)
        @DefaultValue("50")
        int initialCapacity,
        
        /**
         * Enable tool definition caching.
         * 
         * Caches parsed tool definitions to improve lookup performance.
         * Recommended for production environments with stable tool sets.
         * 
         * @return true if caching is enabled
         */
        @DefaultValue("true")
        boolean cacheEnabled,
        
        /**
         * Cache time-to-live for tool definitions.
         * 
         * How long cached tool definitions remain valid before refresh.
         * Shorter TTL provides fresher data but higher overhead.
         * 
         * @return cache TTL duration
         */
        @NotNull
        @DefaultValue("1h")
        Duration cacheTtl
    ) {}
    
    /**
     * Individual API configuration properties.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public record ApiConfig(
        /**
         * Whether this API is enabled.
         * 
         * Allows selective enabling/disabling of APIs without removing
         * their configuration. Useful for maintenance or gradual rollouts.
         * 
         * @return true if API is enabled
         */
        @DefaultValue("true")
        boolean enabled,
        
        /**
         * Path to the OpenAPI specification.
         * 
         * Can be a classpath resource (classpath:specs/api.yaml),
         * file path (file:/path/to/spec.yaml), or HTTP URL
         * (https://api.example.com/openapi.json).
         * 
         * @return OpenAPI specification location
         */
        @NotBlank
        String openapi,
        
        /**
         * Base URL for the API.
         * 
         * All API operations will be invoked relative to this base URL.
         * Should include the protocol, host, port, and base path.
         * 
         * @return API base URL
         */
        @NotBlank
        String baseUrl,
        
        /**
         * Authentication configuration for this API.
         * 
         * Specifies how to authenticate requests to the external API.
         * 
         * @return authentication configuration
         */
        @Valid
        @NotNull
        AuthConfig auth,
        
        /**
         * Request timeout for this API.
         * 
         * Overrides the default server timeout for operations on this API.
         * Should be tuned based on the API's typical response times.
         * 
         * @return API-specific timeout
         */
        @NotNull
        @DefaultValue("10s")
        Duration timeout,
        
        /**
         * Retry configuration for this API.
         * 
         * Defines retry behavior for failed requests to this API.
         * 
         * @return retry configuration
         */
        @Valid
        @NotNull
        RetryConfig retry
    ) {}
    
    /**
     * Authentication configuration properties.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public record AuthConfig(
        /**
         * Authentication type.
         * 
         * Supported types:
         * - none: No authentication
         * - api_key: API key in header or query parameter
         * - oauth2_client_credentials: OAuth2 client credentials flow
         * - bearer_token: Bearer token in Authorization header
         * 
         * @return authentication type
         */
        @NotBlank
        @DefaultValue("none")
        String type,
        
        /**
         * API key header name (for api_key auth type).
         * 
         * @return header name for API key
         */
        String header,
        
        /**
         * API key query parameter name (for api_key auth type).
         * 
         * @return query parameter name for API key
         */
        String queryParam,
        
        /**
         * API key value (for api_key auth type).
         * 
         * Should be provided via environment variables in production.
         * 
         * @return API key value
         */
        String apiKey,
        
        /**
         * OAuth2 token endpoint (for oauth2_client_credentials auth type).
         * 
         * @return OAuth2 token endpoint URL
         */
        String tokenUrl,
        
        /**
         * OAuth2 client ID (for oauth2_client_credentials auth type).
         * 
         * @return OAuth2 client ID
         */
        String clientId,
        
        /**
         * OAuth2 client secret (for oauth2_client_credentials auth type).
         * 
         * Should be provided via environment variables in production.
         * 
         * @return OAuth2 client secret
         */
        String clientSecret,
        
        /**
         * OAuth2 scopes (for oauth2_client_credentials auth type).
         * 
         * @return list of OAuth2 scopes
         */
        List<String> scopes,
        
        /**
         * Bearer token value (for bearer_token auth type).
         * 
         * Should be provided via environment variables in production.
         * 
         * @return bearer token value
         */
        String bearerToken
    ) {}
    
    /**
     * Retry configuration properties.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public record RetryConfig(
        /**
         * Maximum number of retry attempts.
         * 
         * @return maximum retry attempts (1-10)
         */
        @Min(1)
        @Max(10)
        @DefaultValue("3")
        int maxAttempts,
        
        /**
         * Initial delay before first retry.
         * 
         * @return initial retry delay
         */
        @NotNull
        @DefaultValue("100ms")
        Duration initialDelay,
        
        /**
         * Maximum delay between retries.
         * 
         * @return maximum retry delay
         */
        @NotNull
        @DefaultValue("2s")
        Duration maxDelay,
        
        /**
         * Backoff multiplier for exponential backoff.
         * 
         * @return backoff multiplier (1.0-10.0)
         */
        @Min(1)
        @Max(10)
        @DefaultValue("2.0")
        double backoffMultiplier
    ) {}
    
    /**
     * Security configuration properties.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public record SecurityConfig(
        /**
         * Enable authentication for MCP endpoints.
         * 
         * @return true if authentication is required
         */
        @DefaultValue("true")
        boolean authEnabled,
        
        /**
         * Default API key header name for MCP endpoints.
         * 
         * @return API key header name
         */
        @NotBlank
        @DefaultValue("X-API-Key")
        String apiKeyHeader,
        
        /**
         * CORS configuration.
         * 
         * @return CORS settings
         */
        @Valid
        @NotNull
        CorsConfig cors
    ) {}
    
    /**
     * CORS configuration properties.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public record CorsConfig(
        /**
         * Enable CORS support.
         * 
         * @return true if CORS is enabled
         */
        @DefaultValue("true")
        boolean enabled,
        
        /**
         * Allowed origins for CORS requests.
         * 
         * @return list of allowed origins
         */
        @NotNull
        @DefaultValue("*")
        List<String> allowedOrigins,
        
        /**
         * Allowed HTTP methods for CORS requests.
         * 
         * @return list of allowed methods
         */
        @NotNull
        @DefaultValue("GET,POST,OPTIONS")
        List<String> allowedMethods,
        
        /**
         * Allowed headers for CORS requests.
         * 
         * @return list of allowed headers
         */
        @NotNull
        @DefaultValue("*")
        List<String> allowedHeaders,
        
        /**
         * Maximum age for CORS preflight cache.
         * 
         * @return cache max age in seconds
         */
        @Min(0)
        @Max(86400)
        @DefaultValue("3600")
        long maxAge
    ) {}
    
    /**
     * Circuit breaker configuration properties.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public record CircuitBreakerConfig(
        /**
         * Failure rate threshold for opening the circuit breaker.
         * 
         * @return failure rate threshold (0-100)
         */
        @Min(0)
        @Max(100)
        @DefaultValue("50")
        int failureRateThreshold,
        
        /**
         * Minimum number of calls before circuit breaker can open.
         * 
         * @return minimum number of calls
         */
        @Min(1)
        @Max(1000)
        @DefaultValue("10")
        int minimumNumberOfCalls,
        
        /**
         * Wait duration in open state before transitioning to half-open.
         * 
         * @return wait duration
         */
        @NotNull
        @DefaultValue("30s")
        Duration waitDurationInOpenState,
        
        /**
         * Sliding window size for failure rate calculation.
         * 
         * @return sliding window size
         */
        @Min(1)
        @Max(1000)
        @DefaultValue("20")
        int slidingWindowSize,
        
        /**
         * Sliding window type (COUNT_BASED or TIME_BASED).
         * 
         * @return sliding window type
         */
        @NotBlank
        @DefaultValue("COUNT_BASED")
        String slidingWindowType
    ) {}
    
    /**
     * Rate limiting configuration properties.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public record RateLimitConfig(
        /**
         * Enable rate limiting.
         * 
         * @return true if rate limiting is enabled
         */
        @DefaultValue("true")
        boolean enabled,
        
        /**
         * Default rate limit (requests per window).
         * 
         * @return default rate limit
         */
        @Min(1)
        @Max(1000000)
        @DefaultValue("1000")
        int defaultLimit,
        
        /**
         * Rate limit window duration.
         * 
         * @return window duration
         */
        @NotNull
        @DefaultValue("1m")
        Duration windowDuration,
        
        /**
         * Per-tool rate limits.
         * 
         * Map of tool names to their specific rate limits.
         * 
         * @return per-tool rate limits
         */
        @NotNull
        Map<String, Integer> perToolLimits
    ) {}
    
    /**
     * Observability configuration properties.
     * 
     * @author Neeraj Yadav
     * @since 1.0.0
     */
    public record ObservabilityConfig(
        /**
         * Enable distributed tracing.
         * 
         * @return true if tracing is enabled
         */
        @DefaultValue("true")
        boolean tracingEnabled,
        
        /**
         * Trace sampling probability.
         * 
         * @return sampling probability (0.0-1.0)
         */
        @Min(0)
        @Max(1)
        @DefaultValue("0.1")
        double traceSamplingProbability,
        
        /**
         * Enable detailed metrics collection.
         * 
         * @return true if detailed metrics are enabled
         */
        @DefaultValue("true")
        boolean detailedMetricsEnabled,
        
        /**
         * Metrics prefix for all MCP adapter metrics.
         * 
         * @return metrics prefix
         */
        @NotBlank
        @DefaultValue("mcp.adapter")
        String metricsPrefix
    ) {}
}
