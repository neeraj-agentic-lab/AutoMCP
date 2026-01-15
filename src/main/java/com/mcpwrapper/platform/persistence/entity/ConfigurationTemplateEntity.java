/*
 * MCP REST Adapter - Configuration Template Entity
 * 
 * Reactive entity model for storing reusable API configuration templates using R2DBC.
 * This entity provides pre-built configuration templates for common APIs,
 * enabling quick setup and standardization across deployments.
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive entity representing reusable configuration templates.
 * 
 * This entity stores pre-built configuration templates that can be used
 * to quickly set up common API integrations. Templates include all the
 * necessary configuration parameters with placeholders for customization.
 * 
 * Templates support:
 * - Categorization by API type or vendor
 * - Version tracking for template evolution
 * - Built-in vs custom template distinction
 * - Template sharing and marketplace functionality
 * 
 * @author Neeraj Yadav
 * @since 1.0.0
 */
@Table("configuration_templates")
public record ConfigurationTemplateEntity(
    /**
     * Unique identifier for the template.
     * 
     * @return UUID primary key
     */
    @Id
    UUID id,
    
    /**
     * Unique name of the template.
     * Used for identification and selection.
     * 
     * @return template name
     */
    @Column("name")
    String name,
    
    /**
     * Human-readable display name for the template.
     * 
     * @return display name
     */
    @Column("display_name")
    String displayName,
    
    /**
     * Detailed description of what this template configures.
     * 
     * @return template description
     */
    @Column("description")
    String description,
    
    /**
     * Category or type of API this template is for.
     * Examples: "e-commerce", "payment", "social-media", "crm"
     * 
     * @return template category
     */
    @Column("category")
    String category,
    
    /**
     * Version of this template.
     * Follows semantic versioning (e.g., "1.0.0", "2.1.3")
     * 
     * @return template version
     */
    @Column("version")
    String version,
    
    /**
     * Whether this is a built-in template or custom user template.
     * Built-in templates are provided by the system.
     * 
     * @return true if built-in, false if custom
     */
    @Column("is_builtin")
    Boolean isBuiltin,
    
    /**
     * Whether this template is publicly available.
     * Public templates can be shared across organizations.
     * 
     * @return true if public, false if private
     */
    @Column("is_public")
    Boolean isPublic,
    
    /**
     * Template configuration data stored as JSON.
     * Contains the complete configuration structure with placeholders.
     * 
     * @return template configuration JSON
     */
    @Column("template_config")
    JsonNode templateConfig,
    
    /**
     * Required parameters that must be provided when using this template.
     * Stored as JSON array of parameter definitions.
     * 
     * @return required parameters JSON
     */
    @Column("required_parameters")
    JsonNode requiredParameters,
    
    /**
     * Optional parameters with default values.
     * Stored as JSON object with parameter names and defaults.
     * 
     * @return optional parameters JSON
     */
    @Column("optional_parameters")
    JsonNode optionalParameters,
    
    /**
     * Tags for searching and filtering templates.
     * Stored as JSON array of string tags.
     * 
     * @return template tags JSON
     */
    @Column("tags")
    JsonNode tags,
    
    /**
     * Usage instructions or documentation for the template.
     * 
     * @return usage instructions
     */
    @Column("usage_instructions")
    String usageInstructions,
    
    /**
     * Identifier of the user who created this template.
     * 
     * @return creator user ID
     */
    @Column("created_by")
    String createdBy,
    
    /**
     * Timestamp when the template was created.
     * 
     * @return creation timestamp
     */
    @CreatedDate
    @Column("created_at")
    Instant createdAt,
    
    /**
     * Timestamp when the template was last updated.
     * 
     * @return last update timestamp
     */
    @LastModifiedDate
    @Column("updated_at")
    Instant updatedAt
) {
    
    /**
     * Creates a new configuration template entity.
     * 
     * @param name unique template name
     * @param displayName human-readable display name
     * @param description template description
     * @param category template category
     * @param version template version
     * @param isBuiltin whether this is a built-in template
     * @param isPublic whether this template is public
     * @param templateConfig template configuration JSON
     * @param requiredParameters required parameters JSON
     * @param optionalParameters optional parameters JSON
     * @param tags template tags JSON
     * @param usageInstructions usage instructions
     * @param createdBy creator user ID
     * @return new template entity
     */
    public static ConfigurationTemplateEntity create(
            String name,
            String displayName,
            String description,
            String category,
            String version,
            Boolean isBuiltin,
            Boolean isPublic,
            JsonNode templateConfig,
            JsonNode requiredParameters,
            JsonNode optionalParameters,
            JsonNode tags,
            String usageInstructions,
            String createdBy) {
        
        Instant now = Instant.now();
        return new ConfigurationTemplateEntity(
            UUID.randomUUID(),
            name,
            displayName,
            description,
            category,
            version != null ? version : "1.0.0",
            isBuiltin != null ? isBuiltin : false,
            isPublic != null ? isPublic : false,
            templateConfig,
            requiredParameters,
            optionalParameters,
            tags,
            usageInstructions,
            createdBy,
            now,
            now
        );
    }
    
    /**
     * Creates an updated copy of this template with new values.
     * 
     * @param displayName new display name
     * @param description new description
     * @param category new category
     * @param version new version
     * @param isPublic new public flag
     * @param templateConfig new template configuration
     * @param requiredParameters new required parameters
     * @param optionalParameters new optional parameters
     * @param tags new tags
     * @param usageInstructions new usage instructions
     * @return updated template entity
     */
    public ConfigurationTemplateEntity withUpdates(
            String displayName,
            String description,
            String category,
            String version,
            Boolean isPublic,
            JsonNode templateConfig,
            JsonNode requiredParameters,
            JsonNode optionalParameters,
            JsonNode tags,
            String usageInstructions) {
        
        return new ConfigurationTemplateEntity(
            this.id,
            this.name, // name cannot be changed
            displayName,
            description,
            category,
            version,
            this.isBuiltin, // builtin flag cannot be changed
            isPublic,
            templateConfig,
            requiredParameters,
            optionalParameters,
            tags,
            usageInstructions,
            this.createdBy,
            this.createdAt,
            Instant.now() // update timestamp
        );
    }
    
    /**
     * Creates a new version of this template.
     * 
     * @param newVersion new version string
     * @param templateConfig updated template configuration
     * @param requiredParameters updated required parameters
     * @param optionalParameters updated optional parameters
     * @param usageInstructions updated usage instructions
     * @return new template entity with incremented version
     */
    public ConfigurationTemplateEntity createNewVersion(
            String newVersion,
            JsonNode templateConfig,
            JsonNode requiredParameters,
            JsonNode optionalParameters,
            String usageInstructions) {
        
        Instant now = Instant.now();
        return new ConfigurationTemplateEntity(
            UUID.randomUUID(), // new ID for new version
            this.name,
            this.displayName,
            this.description,
            this.category,
            newVersion,
            this.isBuiltin,
            this.isPublic,
            templateConfig,
            requiredParameters,
            optionalParameters,
            this.tags,
            usageInstructions,
            this.createdBy,
            now,
            now
        );
    }
    
    /**
     * Checks if this template can be modified by the given user.
     * 
     * @param userId user attempting to modify the template
     * @return true if the user can modify this template
     */
    public boolean canBeModifiedBy(String userId) {
        // Built-in templates cannot be modified
        if (isBuiltin) {
            return false;
        }
        
        // Only the creator can modify custom templates
        return createdBy != null && createdBy.equals(userId);
    }
    
    /**
     * Checks if this template is accessible to the given user.
     * 
     * @param userId user attempting to access the template
     * @return true if the template is accessible
     */
    public boolean isAccessibleTo(String userId) {
        // Public and built-in templates are accessible to everyone
        if (isPublic || isBuiltin) {
            return true;
        }
        
        // Private templates are only accessible to their creators
        return createdBy != null && createdBy.equals(userId);
    }
}
