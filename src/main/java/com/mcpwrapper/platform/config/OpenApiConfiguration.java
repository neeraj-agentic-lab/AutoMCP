/*
 * MCP REST Adapter - OpenAPI Configuration
 * 
 * Configuration class for OpenAPI integration components.
 * Sets up beans and configuration for OpenAPI parsing, tool generation,
 * and REST adapter functionality.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.config;

import com.mcpwrapper.adapter.openapi.McpToolGeneratorService;
import com.mcpwrapper.adapter.openapi.OpenApiIntegrationService;
import com.mcpwrapper.adapter.openapi.OpenApiParserService;
import com.mcpwrapper.adapter.rest.RestAdapterService;
import com.mcpwrapper.runtime.service.ReactiveConfigurationService;
import com.mcpwrapper.runtime.service.ReactiveEnvironmentVariableService;
import com.mcpwrapper.runtime.service.ReactiveToolRegistryService;
import io.swagger.parser.OpenAPIParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for OpenAPI integration components.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Configuration
@EnableScheduling
public class OpenApiConfiguration {
    
    /**
     * Configures OpenAPI parser for specification parsing.
     */
    @Bean
    public OpenAPIParser openApiParser() {
        return new OpenAPIParser();
    }
    
    /**
     * Configures WebClient for REST API calls with optimized settings.
     */
    @Bean
    public WebClient restApiWebClient() {
        return WebClient.builder()
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024); // 16MB
                configurer.defaultCodecs().enableLoggingRequestDetails(true);
            })
            .defaultHeader("User-Agent", "MCP-REST-Adapter/1.0.0")
            .defaultHeader("Accept", "application/json, */*")
            .build();
    }
}
