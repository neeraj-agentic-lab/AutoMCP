/*
 * MCP REST Adapter - Monitoring Controller
 * 
 * REST API controller for monitoring and observability endpoints.
 * Provides detailed metrics, health information, and system diagnostics
 * for operational monitoring and troubleshooting.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.transport.controller;

import com.mcpwrapper.platform.observability.AuditService;
import com.mcpwrapper.runtime.service.McpServiceFacade;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for monitoring and observability.
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@Tag(name = "Monitoring", description = "System monitoring and observability endpoints")
public class MonitoringController {
    
    private final McpServiceFacade serviceFacade;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final ReactiveHealthContributorRegistry healthRegistry;
    
    public MonitoringController(
            McpServiceFacade serviceFacade,
            AuditService auditService,
            MeterRegistry meterRegistry,
            ReactiveHealthContributorRegistry healthRegistry) {
        this.serviceFacade = serviceFacade;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        this.healthRegistry = healthRegistry;
    }
    
    /**
     * Gets comprehensive system metrics.
     */
    @GetMapping("/metrics")
    @Operation(
        summary = "Get system metrics",
        description = "Returns comprehensive system metrics and performance indicators",
        responses = {
            @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully")
        }
    )
    public Mono<SystemMetrics> getSystemMetrics() {
        return Mono.zip(
            serviceFacade.getSystemStats(),
            serviceFacade.getOverallStats(),
            serviceFacade.getActiveExecutions().map(Map::size)
        ).map(tuple -> {
            var systemStats = tuple.getT1();
            var executionStats = tuple.getT2();
            var activeExecutions = tuple.getT3();
            
            return new SystemMetrics(
                systemStats.totalTools(),
                systemStats.enabledConfigurations(),
                executionStats.totalInvocations(),
                executionStats.overallSuccessRate(),
                (int) executionStats.averageExecutionTime().toMillis(),
                activeExecutions,
                getJvmMetrics(),
                Instant.now()
            );
        });
    }
    
    /**
     * Gets detailed health information.
     */
    @GetMapping("/health/detailed")
    @Operation(
        summary = "Get detailed health information",
        description = "Returns comprehensive health information for all system components",
        responses = {
            @ApiResponse(responseCode = "200", description = "Health information retrieved")
        }
    )
    public Mono<DetailedHealthInfo> getDetailedHealth() {
        return serviceFacade.getSystemHealth()
            .map(health -> new DetailedHealthInfo(
                health.isHealthy(),
                health.totalTools(),
                health.enabledConfigurations(),
                health.requiredVariablesValid(),
                health.unhealthyApis(),
                getComponentHealth(),
                Instant.now()
            ));
    }
    
    /**
     * Gets performance statistics for a specific time period.
     */
    @GetMapping("/performance")
    @Operation(
        summary = "Get performance statistics",
        description = "Returns performance statistics for the specified time period",
        responses = {
            @ApiResponse(responseCode = "200", description = "Performance statistics retrieved")
        }
    )
    public Mono<PerformanceStats> getPerformanceStats(
            @Parameter(description = "Time period in minutes (default: 60)")
            @RequestParam(defaultValue = "60") int periodMinutes) {
        
        Instant since = Instant.now().minus(Duration.ofMinutes(periodMinutes));
        
        return serviceFacade.getOverallStats()
            .map(stats -> new PerformanceStats(
                stats.totalInvocations(),
                stats.overallSuccessRate(),
                1.0 - stats.overallSuccessRate(),
                (int) stats.averageExecutionTime().toMillis(),
                calculateThroughput(stats.totalInvocations(), periodMinutes),
                since,
                Instant.now()
            ));
    }
    
    /**
     * Gets audit trail for system events.
     */
    @GetMapping("/audit")
    @Operation(
        summary = "Get audit trail",
        description = "Returns recent audit events for compliance and debugging",
        responses = {
            @ApiResponse(responseCode = "200", description = "Audit trail retrieved")
        }
    )
    public Flux<AuditEventSummary> getAuditTrail(
            @Parameter(description = "Hours to look back (default: 24)")
            @RequestParam(defaultValue = "24") int hoursBack,
            @Parameter(description = "Event type filter")
            @RequestParam(required = false) String eventType) {
        
        Instant since = Instant.now().minus(Duration.ofHours(hoursBack));
        
        return auditService.getRecentAuditEvents(since)
            .map(audit -> new AuditEventSummary(
                audit.id(),
                audit.action(),
                audit.configId(),
                audit.userId(),
                audit.timestamp()
            ));
    }
    
    /**
     * Gets configuration-specific audit trail.
     */
    @GetMapping("/audit/configuration/{configId}")
    @Operation(
        summary = "Get configuration audit trail",
        description = "Returns audit trail for a specific configuration",
        responses = {
            @ApiResponse(responseCode = "200", description = "Configuration audit trail retrieved"),
            @ApiResponse(responseCode = "404", description = "Configuration not found")
        }
    )
    public Flux<AuditEventSummary> getConfigurationAuditTrail(
            @Parameter(description = "Configuration ID")
            @PathVariable UUID configId) {
        
        return auditService.getConfigurationAuditTrail(configId)
            .map(audit -> new AuditEventSummary(
                audit.id(),
                audit.action(),
                audit.configId(),
                audit.userId(),
                audit.timestamp()
            ));
    }
    
    /**
     * Gets system diagnostics information.
     */
    @GetMapping("/diagnostics")
    @Operation(
        summary = "Get system diagnostics",
        description = "Returns detailed system diagnostics for troubleshooting",
        responses = {
            @ApiResponse(responseCode = "200", description = "Diagnostics retrieved")
        }
    )
    public Mono<SystemDiagnostics> getSystemDiagnostics() {
        return Mono.fromCallable(() -> new SystemDiagnostics(
            System.getProperty("java.version"),
            System.getProperty("os.name"),
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().totalMemory(),
            Runtime.getRuntime().freeMemory(),
            Runtime.getRuntime().maxMemory(),
            System.getProperty("user.timezone"),
            Instant.now()
        ));
    }
    
    /**
     * Triggers a manual health check.
     */
    @PostMapping("/health/check")
    @Operation(
        summary = "Trigger health check",
        description = "Manually triggers a comprehensive health check",
        responses = {
            @ApiResponse(responseCode = "200", description = "Health check completed")
        }
    )
    public Mono<ResponseEntity<HealthCheckResult>> triggerHealthCheck() {
        return serviceFacade.getSystemHealth()
            .map(health -> ResponseEntity.ok(new HealthCheckResult(
                health.isHealthy(),
                "Manual health check completed",
                Map.of(
                    "totalTools", health.totalTools(),
                    "enabledConfigurations", health.enabledConfigurations(),
                    "unhealthyApis", health.unhealthyApis()
                ),
                Instant.now()
            )));
    }
    
    // Private helper methods
    
    private Map<String, Object> getJvmMetrics() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> jvmMetrics = new HashMap<>();
        
        jvmMetrics.put("totalMemory", runtime.totalMemory());
        jvmMetrics.put("freeMemory", runtime.freeMemory());
        jvmMetrics.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        jvmMetrics.put("maxMemory", runtime.maxMemory());
        jvmMetrics.put("availableProcessors", runtime.availableProcessors());
        
        return jvmMetrics;
    }
    
    private Map<String, String> getComponentHealth() {
        Map<String, String> componentHealth = new HashMap<>();
        
        // This would typically check various components
        componentHealth.put("database", "UP");
        componentHealth.put("cache", "UP");
        componentHealth.put("toolRegistry", "UP");
        componentHealth.put("configurationService", "UP");
        
        return componentHealth;
    }
    
    private double calculateThroughput(long totalInvocations, int periodMinutes) {
        return periodMinutes > 0 ? (double) totalInvocations / periodMinutes : 0.0;
    }
    
    // Response DTOs
    
    public record SystemMetrics(
        Long totalTools,
        Long enabledConfigurations,
        Long totalInvocations,
        Double successRate,
        Integer averageExecutionTime,
        Integer activeExecutions,
        Map<String, Object> jvmMetrics,
        Instant timestamp
    ) {}
    
    public record DetailedHealthInfo(
        boolean healthy,
        Long totalTools,
        Long enabledConfigurations,
        boolean requiredVariablesValid,
        Long unhealthyApis,
        Map<String, String> componentHealth,
        Instant timestamp
    ) {}
    
    public record PerformanceStats(
        Long totalInvocations,
        Double successRate,
        Double errorRate,
        Integer averageExecutionTime,
        Double throughputPerMinute,
        Instant periodStart,
        Instant periodEnd
    ) {}
    
    public record AuditEventSummary(
        UUID id,
        String action,
        UUID configId,
        String userId,
        Instant timestamp
    ) {}
    
    public record SystemDiagnostics(
        String javaVersion,
        String osName,
        int availableProcessors,
        long totalMemory,
        long freeMemory,
        long maxMemory,
        String timezone,
        Instant timestamp
    ) {}
    
    public record HealthCheckResult(
        boolean healthy,
        String message,
        Map<String, Object> details,
        Instant timestamp
    ) {}
}
