/*
 * MCP REST Adapter - Tool Usage Statistics Entity
 * 
 * Reactive entity model for tracking tool usage and performance metrics using R2DBC.
 * This entity stores aggregated statistics about tool invocations, success rates,
 * and performance characteristics for monitoring and optimization.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive entity representing tool usage statistics.
 * 
 * This entity tracks performance and reliability metrics for each MCP tool,
 * enabling monitoring, alerting, and optimization of the system.
 * 
 * Statistics include:
 * - Invocation counts (total, success, error)
 * - Execution time metrics (total, average)
 * - Error tracking (last error time and message)
 * - Usage patterns (last used timestamp)
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Table("tool_usage_stats")
public record ToolUsageStatsEntity(
    /**
     * Unique name of the MCP tool.
     * This serves as the primary key for statistics.
     * 
     * @return tool name (primary key)
     */
    @Id
    @Column("tool_name")
    String toolName,
    
    /**
     * Reference to the API configuration that owns this tool.
     * 
     * @return API configuration ID
     */
    @Column("api_config_id")
    UUID apiConfigId,
    
    /**
     * Total number of times this tool has been invoked.
     * 
     * @return total invocation count
     */
    @Column("invocation_count")
    Long invocationCount,
    
    /**
     * Number of successful tool invocations.
     * 
     * @return successful invocation count
     */
    @Column("success_count")
    Long successCount,
    
    /**
     * Number of failed tool invocations.
     * 
     * @return error count
     */
    @Column("error_count")
    Long errorCount,
    
    /**
     * Total execution time across all invocations in milliseconds.
     * 
     * @return total execution time in ms
     */
    @Column("total_execution_time_ms")
    Long totalExecutionTimeMs,
    
    /**
     * Average execution time per invocation in milliseconds.
     * Calculated field updated on each invocation.
     * 
     * @return average execution time in ms
     */
    @Column("avg_execution_time_ms")
    Integer avgExecutionTimeMs,
    
    /**
     * Timestamp of the most recent tool invocation.
     * 
     * @return last used timestamp
     */
    @Column("last_used")
    Instant lastUsed,
    
    /**
     * Timestamp of the most recent error.
     * 
     * @return last error timestamp
     */
    @Column("last_error")
    Instant lastError,
    
    /**
     * Message from the most recent error.
     * 
     * @return last error message
     */
    @Column("last_error_message")
    String lastErrorMessage,
    
    /**
     * Timestamp when these statistics were last updated.
     * 
     * @return last update timestamp
     */
    @LastModifiedDate
    @Column("updated_at")
    Instant updatedAt
) {
    
    /**
     * Creates a new tool usage statistics entity with initial values.
     * 
     * @param toolName name of the tool
     * @param apiConfigId ID of the owning API configuration
     * @return new statistics entity with zero counts
     */
    public static ToolUsageStatsEntity create(String toolName, UUID apiConfigId) {
        Instant now = Instant.now();
        return new ToolUsageStatsEntity(
            toolName,
            apiConfigId,
            0L,      // invocationCount
            0L,      // successCount
            0L,      // errorCount
            0L,      // totalExecutionTimeMs
            0,       // avgExecutionTimeMs
            null,    // lastUsed
            null,    // lastError
            null,    // lastErrorMessage
            now      // updatedAt
        );
    }
    
    /**
     * Records a successful tool invocation.
     * 
     * @param executionTimeMs execution time for this invocation
     * @return updated statistics entity
     */
    public ToolUsageStatsEntity recordSuccess(long executionTimeMs) {
        long newInvocationCount = invocationCount + 1;
        long newSuccessCount = successCount + 1;
        long newTotalTime = totalExecutionTimeMs + executionTimeMs;
        int newAvgTime = (int) (newTotalTime / newInvocationCount);
        
        return new ToolUsageStatsEntity(
            toolName,
            apiConfigId,
            newInvocationCount,
            newSuccessCount,
            errorCount,
            newTotalTime,
            newAvgTime,
            Instant.now(), // lastUsed
            lastError,
            lastErrorMessage,
            Instant.now()  // updatedAt
        );
    }
    
    /**
     * Records a failed tool invocation.
     * 
     * @param executionTimeMs execution time for this invocation
     * @param errorMessage error message from the failure
     * @return updated statistics entity
     */
    public ToolUsageStatsEntity recordError(long executionTimeMs, String errorMessage) {
        long newInvocationCount = invocationCount + 1;
        long newErrorCount = errorCount + 1;
        long newTotalTime = totalExecutionTimeMs + executionTimeMs;
        int newAvgTime = newInvocationCount > 0 ? (int) (newTotalTime / newInvocationCount) : 0;
        
        Instant now = Instant.now();
        return new ToolUsageStatsEntity(
            toolName,
            apiConfigId,
            newInvocationCount,
            successCount,
            newErrorCount,
            newTotalTime,
            newAvgTime,
            now,         // lastUsed
            now,         // lastError
            errorMessage, // lastErrorMessage
            now          // updatedAt
        );
    }
    
    /**
     * Calculates the success rate as a percentage.
     * 
     * @return success rate (0.0 to 1.0)
     */
    public double getSuccessRate() {
        if (invocationCount == 0) {
            return 0.0;
        }
        return (double) successCount / invocationCount;
    }
    
    /**
     * Calculates the error rate as a percentage.
     * 
     * @return error rate (0.0 to 1.0)
     */
    public double getErrorRate() {
        if (invocationCount == 0) {
            return 0.0;
        }
        return (double) errorCount / invocationCount;
    }
    
    /**
     * Checks if this tool has been used recently.
     * 
     * @param threshold time threshold for "recent" usage
     * @return true if used within the threshold
     */
    public boolean isRecentlyUsed(java.time.Duration threshold) {
        if (lastUsed == null) {
            return false;
        }
        return lastUsed.isAfter(Instant.now().minus(threshold));
    }
    
    /**
     * Checks if this tool has had recent errors.
     * 
     * @param threshold time threshold for "recent" errors
     * @return true if errors occurred within the threshold
     */
    public boolean hasRecentErrors(java.time.Duration threshold) {
        if (lastError == null) {
            return false;
        }
        return lastError.isAfter(Instant.now().minus(threshold));
    }
}
