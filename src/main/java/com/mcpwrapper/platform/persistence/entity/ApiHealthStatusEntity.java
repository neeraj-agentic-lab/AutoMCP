/*
 * MCP REST Adapter - API Health Status Entity
 * 
 * Reactive entity model for tracking API health and availability using R2DBC.
 * This entity stores health check results and status information for external
 * APIs to enable proactive monitoring and circuit breaker decisions.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive entity representing API health status information.
 * 
 * This entity tracks the health and availability of external APIs
 * to support circuit breaker patterns, monitoring, and alerting.
 * 
 * Health information includes:
 * - Current health state (healthy, degraded, unhealthy)
 * - Response time metrics
 * - Error rates and patterns
 * - Last successful/failed check times
 * - Detailed status information
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Table("api_health_status")
public record ApiHealthStatusEntity(
    /**
     * Reference to the API configuration being monitored.
     * 
     * @return API configuration ID (primary key)
     */
    @Id
    @Column("api_config_id")
    UUID apiConfigId,
    
    /**
     * Current health state of the API.
     * Values: HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN
     * 
     * @return health state
     */
    @Column("health_state")
    String healthState,
    
    /**
     * Human-readable status message.
     * 
     * @return status message
     */
    @Column("status_message")
    String statusMessage,
    
    /**
     * Average response time over recent checks in milliseconds.
     * 
     * @return average response time in ms
     */
    @Column("avg_response_time_ms")
    Integer avgResponseTimeMs,
    
    /**
     * Error rate as a percentage (0-100).
     * 
     * @return error rate percentage
     */
    @Column("error_rate_percent")
    Double errorRatePercent,
    
    /**
     * Number of consecutive successful checks.
     * 
     * @return consecutive success count
     */
    @Column("consecutive_successes")
    Integer consecutiveSuccesses,
    
    /**
     * Number of consecutive failed checks.
     * 
     * @return consecutive failure count
     */
    @Column("consecutive_failures")
    Integer consecutiveFailures,
    
    /**
     * Timestamp of the last successful health check.
     * 
     * @return last success timestamp
     */
    @Column("last_success_at")
    Instant lastSuccessAt,
    
    /**
     * Timestamp of the last failed health check.
     * 
     * @return last failure timestamp
     */
    @Column("last_failure_at")
    Instant lastFailureAt,
    
    /**
     * Timestamp of the most recent health check.
     * 
     * @return last check timestamp
     */
    @Column("last_checked_at")
    Instant lastCheckedAt,
    
    /**
     * Additional health details stored as JSON.
     * May include circuit breaker state, detailed metrics, etc.
     * 
     * @return health details JSON
     */
    @Column("health_details")
    JsonNode healthDetails,
    
    /**
     * Timestamp when this record was last updated.
     * 
     * @return last update timestamp
     */
    @LastModifiedDate
    @Column("updated_at")
    Instant updatedAt
) {
    
    /**
     * Health state enumeration.
     */
    public enum HealthState {
        HEALTHY("HEALTHY"),
        DEGRADED("DEGRADED"),
        UNHEALTHY("UNHEALTHY"),
        UNKNOWN("UNKNOWN");
        
        private final String value;
        
        HealthState(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * Creates a new API health status entity with initial unknown state.
     * 
     * @param apiConfigId ID of the API configuration
     * @return new health status entity
     */
    public static ApiHealthStatusEntity create(UUID apiConfigId) {
        Instant now = Instant.now();
        return new ApiHealthStatusEntity(
            apiConfigId,
            HealthState.UNKNOWN.getValue(),
            "Initial state - not yet checked",
            null,    // avgResponseTimeMs
            null,    // errorRatePercent
            0,       // consecutiveSuccesses
            0,       // consecutiveFailures
            null,    // lastSuccessAt
            null,    // lastFailureAt
            null,    // lastCheckedAt
            null,    // healthDetails
            now      // updatedAt
        );
    }
    
    /**
     * Records a successful health check.
     * 
     * @param responseTimeMs response time for this check
     * @param healthDetails additional health information
     * @return updated health status entity
     */
    public ApiHealthStatusEntity recordSuccess(int responseTimeMs, JsonNode healthDetails) {
        Instant now = Instant.now();
        
        // Update average response time (simple moving average)
        int newAvgResponseTime = avgResponseTimeMs != null 
            ? (avgResponseTimeMs + responseTimeMs) / 2 
            : responseTimeMs;
        
        // Determine new health state based on response time
        HealthState newState = determineHealthState(newAvgResponseTime, 0.0);
        
        return new ApiHealthStatusEntity(
            apiConfigId,
            newState.getValue(),
            "API is responding normally",
            newAvgResponseTime,
            calculateErrorRate(true),
            consecutiveSuccesses + 1,
            0, // reset consecutive failures
            now, // lastSuccessAt
            lastFailureAt,
            now, // lastCheckedAt
            healthDetails,
            now  // updatedAt
        );
    }
    
    /**
     * Records a failed health check.
     * 
     * @param errorMessage error message from the failure
     * @param healthDetails additional health information
     * @return updated health status entity
     */
    public ApiHealthStatusEntity recordFailure(String errorMessage, JsonNode healthDetails) {
        Instant now = Instant.now();
        
        // Determine new health state based on consecutive failures
        HealthState newState = consecutiveFailures >= 2 
            ? HealthState.UNHEALTHY 
            : HealthState.DEGRADED;
        
        return new ApiHealthStatusEntity(
            apiConfigId,
            newState.getValue(),
            errorMessage != null ? errorMessage : "API health check failed",
            avgResponseTimeMs,
            calculateErrorRate(false),
            0, // reset consecutive successes
            consecutiveFailures + 1,
            lastSuccessAt,
            now, // lastFailureAt
            now, // lastCheckedAt
            healthDetails,
            now  // updatedAt
        );
    }
    
    /**
     * Determines health state based on response time and error rate.
     */
    private HealthState determineHealthState(int responseTime, double errorRate) {
        if (errorRate > 50.0 || responseTime > 5000) {
            return HealthState.UNHEALTHY;
        } else if (errorRate > 10.0 || responseTime > 2000) {
            return HealthState.DEGRADED;
        } else {
            return HealthState.HEALTHY;
        }
    }
    
    /**
     * Calculates error rate based on recent success/failure pattern.
     */
    private double calculateErrorRate(boolean wasSuccess) {
        int totalChecks = consecutiveSuccesses + consecutiveFailures + 1;
        int failures = wasSuccess ? consecutiveFailures : consecutiveFailures + 1;
        
        return totalChecks > 0 ? (double) failures / totalChecks * 100.0 : 0.0;
    }
    
    /**
     * Checks if the API is currently healthy.
     * 
     * @return true if health state is HEALTHY
     */
    public boolean isHealthy() {
        return HealthState.HEALTHY.getValue().equals(healthState);
    }
    
    /**
     * Checks if the API is currently unhealthy.
     * 
     * @return true if health state is UNHEALTHY
     */
    public boolean isUnhealthy() {
        return HealthState.UNHEALTHY.getValue().equals(healthState);
    }
    
    /**
     * Checks if health data is stale.
     * 
     * @param maxAge maximum age before considering data stale
     * @return true if last check is older than maxAge
     */
    public boolean isStale(java.time.Duration maxAge) {
        if (lastCheckedAt == null) {
            return true;
        }
        return lastCheckedAt.isBefore(Instant.now().minus(maxAge));
    }
}
