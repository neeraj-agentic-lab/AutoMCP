/*
 * MCP REST Adapter - Configuration Audit Entity
 * 
 * Reactive entity model for tracking configuration changes using R2DBC.
 * This entity provides an audit trail for all modifications to API configurations,
 * enabling compliance, debugging, and change tracking.
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

/**
 * Reactive entity representing configuration audit log entries.
 * 
 * This entity tracks all changes made to API configurations, providing
 * a complete audit trail for compliance and debugging purposes.
 * 
 * Each audit entry captures:
 * - What configuration was changed
 * - What type of action was performed
 * - Who made the change
 * - When the change occurred
 * - What specific changes were made
 * - Where the change originated from
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Table("configuration_audit")
public record ConfigurationAuditEntity(
    /**
     * Unique identifier for the audit entry.
     * 
     * @return UUID primary key
     */
    @Id
    UUID id,
    
    /**
     * Reference to the API configuration that was changed.
     * May be null for system-wide operations.
     * 
     * @return API configuration ID
     */
    @Column("config_id")
    UUID configId,
    
    /**
     * Type of action that was performed.
     * Common values: CREATE, UPDATE, DELETE, ENABLE, DISABLE
     * 
     * @return action type
     */
    @Column("action")
    String action,
    
    /**
     * Detailed changes made during the action.
     * Stored as JSON containing before/after values.
     * 
     * @return changes JSON
     */
    @Column("changes")
    JsonNode changes,
    
    /**
     * Timestamp when the action occurred.
     * 
     * @return action timestamp
     */
    @CreatedDate
    @Column("timestamp")
    Instant timestamp,
    
    /**
     * Identifier of the user who performed the action.
     * 
     * @return user ID
     */
    @Column("user_id")
    String userId,
    
    /**
     * IP address from which the action was initiated.
     * 
     * @return client IP address
     */
    @Column("ip_address")
    String ipAddress
) {
    
    /**
     * Audit action types.
     */
    public enum AuditAction {
        CREATE("CREATE"),
        UPDATE("UPDATE"),
        DELETE("DELETE"),
        ENABLE("ENABLE"),
        DISABLE("DISABLE"),
        IMPORT("IMPORT"),
        EXPORT("EXPORT"),
        REGENERATE_TOOLS("REGENERATE_TOOLS");
        
        private final String value;
        
        AuditAction(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * Creates a new audit entry with generated ID and timestamp.
     * 
     * @param configId ID of the affected configuration
     * @param action type of action performed
     * @param changes detailed changes made
     * @param userId user who performed the action
     * @param ipAddress IP address of the client
     * @return new audit entry
     */
    public static ConfigurationAuditEntity create(
            UUID configId,
            AuditAction action,
            JsonNode changes,
            String userId,
            String ipAddress) {
        
        return new ConfigurationAuditEntity(
            null, // Let the database generate the UUID
            configId,
            action.getValue(),
            changes,
            Instant.now(),
            userId,
            ipAddress
        );
    }
    
    /**
     * Creates a new audit entry for configuration creation.
     * 
     * @param configId ID of the created configuration
     * @param configData configuration data that was created
     * @param userId user who created the configuration
     * @param ipAddress IP address of the client
     * @return new audit entry
     */
    public static ConfigurationAuditEntity forCreate(
            UUID configId,
            JsonNode configData,
            String userId,
            String ipAddress) {
        
        return create(configId, AuditAction.CREATE, configData, userId, ipAddress);
    }
    
    /**
     * Creates a new audit entry for configuration updates.
     * 
     * @param configId ID of the updated configuration
     * @param beforeData configuration data before update
     * @param afterData configuration data after update
     * @param userId user who updated the configuration
     * @param ipAddress IP address of the client
     * @return new audit entry
     */
    public static ConfigurationAuditEntity forUpdate(
            UUID configId,
            JsonNode beforeData,
            JsonNode afterData,
            String userId,
            String ipAddress) {
        
        // Create a changes object with before/after
        var changesBuilder = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        changesBuilder.set("before", beforeData);
        changesBuilder.set("after", afterData);
        
        return create(configId, AuditAction.UPDATE, changesBuilder, userId, ipAddress);
    }
    
    /**
     * Creates a new audit entry for configuration deletion.
     * 
     * @param configId ID of the deleted configuration
     * @param configData configuration data that was deleted
     * @param userId user who deleted the configuration
     * @param ipAddress IP address of the client
     * @return new audit entry
     */
    public static ConfigurationAuditEntity forDelete(
            UUID configId,
            JsonNode configData,
            String userId,
            String ipAddress) {
        
        return create(configId, AuditAction.DELETE, configData, userId, ipAddress);
    }
    
    /**
     * Creates a new audit entry for enabling/disabling configurations.
     * 
     * @param configId ID of the configuration
     * @param enabled new enabled status
     * @param userId user who changed the status
     * @param ipAddress IP address of the client
     * @return new audit entry
     */
    public static ConfigurationAuditEntity forStatusChange(
            UUID configId,
            boolean enabled,
            String userId,
            String ipAddress) {
        
        var changesBuilder = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        changesBuilder.put("enabled", enabled);
        
        AuditAction action = enabled ? AuditAction.ENABLE : AuditAction.DISABLE;
        return create(configId, action, changesBuilder, userId, ipAddress);
    }
}
