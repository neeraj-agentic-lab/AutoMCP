/*
 * MCP REST Adapter - Health Indicator
 * 
 * Custom health indicator for the MCP REST Adapter.
 * Provides detailed health information about tools, configurations,
 * database connectivity, and external API availability.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.observability;

import com.mcpwrapper.runtime.service.McpServiceFacade;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Custom health indicator for MCP system components.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Component
public class McpHealthIndicator implements ReactiveHealthIndicator {
    
    private final McpServiceFacade serviceFacade;
    
    public McpHealthIndicator(McpServiceFacade serviceFacade) {
        this.serviceFacade = serviceFacade;
    }
    
    @Override
    public Mono<Health> health() {
        return serviceFacade.getSystemHealth()
            .timeout(Duration.ofSeconds(5))
            .map(systemHealth -> {
                Health.Builder builder = systemHealth.isHealthy() 
                    ? Health.up() 
                    : Health.down();
                
                return builder
                    .withDetail("totalTools", systemHealth.totalTools())
                    .withDetail("enabledConfigurations", systemHealth.enabledConfigurations())
                    .withDetail("requiredVariablesValid", systemHealth.requiredVariablesValid())
                    .withDetail("unhealthyApis", systemHealth.unhealthyApis())
                    .withDetail("status", systemHealth.isHealthy() ? "HEALTHY" : "UNHEALTHY")
                    .build();
            })
            .onErrorReturn(Health.down()
                .withDetail("error", "Health check failed")
                .withDetail("status", "UNKNOWN")
                .build());
    }
}
