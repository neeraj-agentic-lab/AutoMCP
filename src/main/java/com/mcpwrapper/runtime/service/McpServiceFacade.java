/*
 * MCP REST Adapter - Service Facade
 * 
 * Unified facade providing access to all reactive services.
 * Simplifies service interactions and provides a single entry point
 * for the transport layer to access business logic.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.runtime.service;

import com.mcpwrapper.platform.persistence.entity.ApiConfigurationEntity;
import com.mcpwrapper.platform.persistence.entity.ApiHealthStatusEntity;
import com.mcpwrapper.platform.persistence.entity.EnvironmentVariableEntity;
import com.mcpwrapper.runtime.invocation.ToolInvoker;
import com.mcpwrapper.runtime.registry.ToolRegistry;
import com.mcpwrapper.transport.mcp.McpTool;
import com.mcpwrapper.transport.mcp.McpToolCall;
import com.mcpwrapper.transport.mcp.McpToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Unified facade for all MCP services.
 * 
 * This facade provides a simplified interface to all reactive services,
 * making it easier for controllers and other components to access
 * business logic without managing multiple service dependencies.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Service
public class McpServiceFacade {
    
    private static final Logger logger = LoggerFactory.getLogger(McpServiceFacade.class);
    
    private final ReactiveConfigurationService configurationService;
    private final ReactiveToolRegistryService toolRegistryService;
    private final ReactiveToolInvokerService toolInvokerService;
    private final ReactiveEnvironmentVariableService environmentService;
    private final ReactiveApiHealthService healthService;
    
    public McpServiceFacade(
            ReactiveConfigurationService configurationService,
            ReactiveToolRegistryService toolRegistryService,
            ReactiveToolInvokerService toolInvokerService,
            ReactiveEnvironmentVariableService environmentService,
            ReactiveApiHealthService healthService) {
        this.configurationService = configurationService;
        this.toolRegistryService = toolRegistryService;
        this.toolInvokerService = toolInvokerService;
        this.environmentService = environmentService;
        this.healthService = healthService;
    }
    
    // Configuration Management
    
    /**
     * Creates a new API configuration.
     */
    public Mono<ApiConfigurationEntity> createConfiguration(ReactiveConfigurationService.CreateConfigurationRequest request) {
        return configurationService.createConfiguration(request);
    }
    
    /**
     * Gets all enabled API configurations.
     */
    public Flux<ApiConfigurationEntity> getEnabledConfigurations() {
        return configurationService.getAllConfigurations(true);
    }
    
    /**
     * Gets configuration by ID.
     */
    public Mono<ApiConfigurationEntity> getConfiguration(UUID id) {
        return configurationService.getConfiguration(id);
    }
    
    /**
     * Updates configuration.
     */
    public Mono<ApiConfigurationEntity> updateConfiguration(UUID id, ReactiveConfigurationService.UpdateConfigurationRequest request) {
        return configurationService.updateConfiguration(id, request);
    }
    
    /**
     * Deletes configuration.
     */
    public Mono<Void> deleteConfiguration(UUID id, String deletedBy) {
        return configurationService.deleteConfiguration(id, deletedBy);
    }
    
    // Tool Registry Management
    
    /**
     * Gets all available tools.
     */
    public Flux<McpTool> getAllTools() {
        return toolRegistryService.getAllTools();
    }
    
    /**
     * Gets a specific tool by name.
     */
    public Mono<McpTool> getTool(String toolName) {
        return toolRegistryService.getTool(toolName);
    }
    
    /**
     * Registers a new tool.
     */
    public Mono<McpTool> registerTool(McpTool tool) {
        return toolRegistryService.registerTool(tool);
    }
    
    /**
     * Searches tools by name pattern.
     */
    public Flux<McpTool> searchTools(String namePattern) {
        return toolRegistryService.findToolsByNamePattern(namePattern);
    }
    
    /**
     * Gets tool count.
     */
    public Mono<Long> getToolCount() {
        return toolRegistryService.getToolCount();
    }
    
    // Tool Invocation
    
    /**
     * Invokes a tool with the given call.
     */
    public Mono<McpToolResult> invokeTool(McpToolCall toolCall) {
        return toolInvokerService.invokeTool(toolCall);
    }
    
    /**
     * Invokes a tool with execution context.
     */
    public Mono<McpToolResult> invokeTool(McpToolCall toolCall, ToolInvoker.ToolExecutionContext context) {
        return toolInvokerService.invokeTool(toolCall, context);
    }
    
    /**
     * Validates a tool call.
     */
    public Mono<ToolInvoker.ValidationResult> validateToolCall(McpToolCall toolCall) {
        return toolInvokerService.validateToolCall(toolCall);
    }
    
    /**
     * Gets tool execution statistics.
     */
    public Mono<ToolInvoker.ToolExecutionStats> getToolStats(String toolName) {
        return toolInvokerService.getToolStats(toolName);
    }
    
    /**
     * Gets overall execution statistics.
     */
    public Mono<ToolInvoker.OverallExecutionStats> getOverallStats() {
        return toolInvokerService.getOverallStats();
    }
    
    /**
     * Checks if a tool is available.
     */
    public Mono<Boolean> isToolAvailable(String toolName) {
        return toolInvokerService.isToolAvailable(toolName);
    }
    
    /**
     * Gets active executions.
     */
    public Mono<Map<String, ToolInvoker.ActiveExecution>> getActiveExecutions() {
        return toolInvokerService.getActiveExecutions();
    }
    
    // Environment Variables
    
    /**
     * Creates a new environment variable.
     */
    public Mono<EnvironmentVariableEntity> createEnvironmentVariable(ReactiveEnvironmentVariableService.CreateVariableRequest request) {
        return environmentService.createVariable(request);
    }
    
    /**
     * Gets all environment variables.
     */
    public Flux<EnvironmentVariableEntity> getAllEnvironmentVariables() {
        return environmentService.getAllVariables();
    }
    
    /**
     * Gets decrypted value of an environment variable.
     */
    public Mono<String> getEnvironmentVariableValue(String name) {
        return environmentService.getDecryptedValue(name);
    }
    
    /**
     * Updates an environment variable.
     */
    public Mono<EnvironmentVariableEntity> updateEnvironmentVariable(UUID id, ReactiveEnvironmentVariableService.UpdateVariableRequest request) {
        return environmentService.updateVariable(id, request);
    }
    
    /**
     * Validates that all required variables are set.
     */
    public Mono<ReactiveEnvironmentVariableService.ValidationResult> validateRequiredVariables() {
        return environmentService.validateRequiredVariables();
    }
    
    // Health Monitoring
    
    /**
     * Checks health of a specific API.
     */
    public Mono<ApiHealthStatusEntity> checkApiHealth(UUID apiConfigId) {
        return healthService.checkApiHealth(apiConfigId);
    }
    
    /**
     * Gets health status of an API.
     */
    public Mono<ApiHealthStatusEntity> getApiHealthStatus(UUID apiConfigId) {
        return healthService.getApiHealthStatus(apiConfigId);
    }
    
    /**
     * Gets all unhealthy APIs.
     */
    public Flux<ApiHealthStatusEntity> getUnhealthyApis() {
        return healthService.getUnhealthyApis();
    }
    
    /**
     * Gets APIs with consecutive failures.
     */
    public Flux<ApiHealthStatusEntity> getApisWithConsecutiveFailures(int threshold) {
        return healthService.getApisWithConsecutiveFailures(threshold);
    }
    
    // System Operations
    
    /**
     * Performs system health check.
     */
    public Mono<SystemHealthStatus> getSystemHealth() {
        logger.debug("Performing system health check");
        
        return Mono.zip(
            getToolCount(),
            configurationService.getConfigurationStats(),
            validateRequiredVariables(),
            getUnhealthyApis().count()
        ).map(tuple -> new SystemHealthStatus(
            tuple.getT1(), // tool count
            tuple.getT2().enabledCount(), // enabled configs
            tuple.getT3().isValid(), // required vars valid
            tuple.getT4() // unhealthy API count
        ));
    }
    
    /**
     * Gets system statistics.
     */
    public Mono<SystemStats> getSystemStats() {
        return Mono.zip(
            getToolCount(),
            configurationService.getConfigurationStats(),
            getOverallStats(),
            environmentService.getRequiredVariables().count()
        ).map(tuple -> new SystemStats(
            tuple.getT1(), // total tools
            tuple.getT2().totalCount(), // total configs
            tuple.getT2().enabledCount(), // enabled configs
            tuple.getT3().totalInvocations(), // total invocations
            tuple.getT3().overallSuccessRate(), // success rate
            tuple.getT4() // required vars count
        ));
    }
    
    // Response records
    
    public record SystemHealthStatus(
        Long totalTools,
        Long enabledConfigurations,
        boolean requiredVariablesValid,
        Long unhealthyApis
    ) {
        public boolean isHealthy() {
            return requiredVariablesValid && unhealthyApis == 0;
        }
    }
    
    public record SystemStats(
        Long totalTools,
        Long totalConfigurations,
        Long enabledConfigurations,
        Long totalInvocations,
        Double successRate,
        Long requiredVariables
    ) {}
}
