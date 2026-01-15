/*
 * MCP REST Adapter - Environment Variable Entity
 * 
 * Reactive entity model for storing encrypted environment variables using R2DBC.
 * This entity provides secure storage for sensitive configuration values like
 * API keys, tokens, and passwords used by API configurations.
 * 
 * @author Neeraj Yadav
 * @version 1.0.0
 * @since 2025-12-16
 * 
 * Copyright (c) 2025 Neeraj Yadav. All rights reserved.
 */
package com.mcpwrapper.platform.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive entity representing encrypted environment variables.
 * 
 * This entity provides secure storage for sensitive configuration values
 * that are referenced by API configurations. All values are encrypted
 * at rest and can be marked as masked for security purposes.
 * 
 * Features:
 * - Encrypted storage of sensitive values
 * - Masking support for UI display
 * - Required/optional flag for validation
 * - Audit trail with creation/update timestamps
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Table("environment_variables")
public record EnvironmentVariableEntity(
    /**
     * Unique identifier for the environment variable.
     * 
     * @return UUID primary key
     */
    @Id
    UUID id,
    
    /**
     * Unique name of the environment variable.
     * Used for referencing in configurations (e.g., ${API_KEY}).
     * 
     * @return variable name
     */
    @Column("name")
    String name,
    
    /**
     * Human-readable description of the variable's purpose.
     * 
     * @return variable description
     */
    @Column("description")
    String description,
    
    /**
     * Encrypted value of the environment variable.
     * Never exposed in plain text through APIs.
     * 
     * @return encrypted value
     */
    @Column("encrypted_value")
    String encryptedValue,
    
    /**
     * Whether this variable is required for system operation.
     * Required variables must have values before configurations can be activated.
     * 
     * @return true if required, false if optional
     */
    @Column("is_required")
    Boolean isRequired,
    
    /**
     * Whether this variable should be masked in UI displays.
     * Masked variables show as asterisks or similar in user interfaces.
     * 
     * @return true if should be masked, false otherwise
     */
    @Column("is_masked")
    Boolean isMasked,
    
    /**
     * Timestamp when the variable was created.
     * 
     * @return creation timestamp
     */
    @CreatedDate
    @Column("created_at")
    Instant createdAt,
    
    /**
     * Timestamp when the variable was last updated.
     * 
     * @return last update timestamp
     */
    @LastModifiedDate
    @Column("updated_at")
    Instant updatedAt
) {
    
    /**
     * Creates a new environment variable entity with generated ID and timestamps.
     * 
     * @param name unique variable name
     * @param description variable description
     * @param encryptedValue encrypted variable value
     * @param isRequired whether the variable is required
     * @param isMasked whether the variable should be masked
     * @return new entity instance
     */
    public static EnvironmentVariableEntity create(
            String name,
            String description,
            String encryptedValue,
            Boolean isRequired,
            Boolean isMasked) {
        
        Instant now = Instant.now();
        return new EnvironmentVariableEntity(
            UUID.randomUUID(),
            name,
            description,
            encryptedValue,
            isRequired != null ? isRequired : false,
            isMasked != null ? isMasked : true,
            now,
            now
        );
    }
    
    /**
     * Creates an updated copy of this entity with new values.
     * 
     * @param description new description
     * @param encryptedValue new encrypted value
     * @param isRequired new required flag
     * @param isMasked new masked flag
     * @return updated entity instance
     */
    public EnvironmentVariableEntity withUpdates(
            String description,
            String encryptedValue,
            Boolean isRequired,
            Boolean isMasked) {
        
        return new EnvironmentVariableEntity(
            this.id,
            this.name, // name cannot be changed
            description,
            encryptedValue,
            isRequired != null ? isRequired : this.isRequired,
            isMasked != null ? isMasked : this.isMasked,
            this.createdAt,
            Instant.now() // update timestamp
        );
    }
    
    /**
     * Creates a copy with updated encrypted value only.
     * 
     * @param newEncryptedValue new encrypted value
     * @return updated entity instance
     */
    public EnvironmentVariableEntity withNewValue(String newEncryptedValue) {
        return new EnvironmentVariableEntity(
            this.id,
            this.name,
            this.description,
            newEncryptedValue,
            this.isRequired,
            this.isMasked,
            this.createdAt,
            Instant.now()
        );
    }
    
    /**
     * Gets a masked representation of the value for UI display.
     * 
     * @return masked value string
     */
    public String getMaskedValue() {
        if (!isMasked || encryptedValue == null) {
            return encryptedValue;
        }
        
        // Show first and last 2 characters with asterisks in between
        if (encryptedValue.length() <= 4) {
            return "****";
        }
        
        return encryptedValue.substring(0, 2) + 
               "*".repeat(Math.max(4, encryptedValue.length() - 4)) + 
               encryptedValue.substring(encryptedValue.length() - 2);
    }
    
    /**
     * Checks if this variable has a non-empty value.
     * 
     * @return true if value is present and non-empty
     */
    public boolean hasValue() {
        return encryptedValue != null && !encryptedValue.trim().isEmpty();
    }
    
    /**
     * Validates that required variables have values.
     * 
     * @return true if valid (not required or has value)
     */
    public boolean isValid() {
        return !isRequired || hasValue();
    }
}
