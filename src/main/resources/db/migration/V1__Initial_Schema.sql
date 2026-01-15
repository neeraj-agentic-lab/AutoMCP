-- MCP REST Adapter - Initial Database Schema
-- 
-- This migration creates the initial database schema for the MCP REST Adapter,
-- including all tables, indexes, functions, and triggers needed for the application.
-- 
-- Migration: V1__Initial_Schema.sql
-- Description: Creates the complete database schema for API configurations,
--              tool definitions, audit logging, and usage statistics.
-- 
-- @author Neeraj Yadav
-- @version 1.0.0
-- @since 2025-12-16

-- Enable required PostgreSQL extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- =============================================================================
-- CORE TABLES
-- =============================================================================

-- API Configurations Table
-- Stores configuration for external REST APIs that will be exposed as MCP tools
CREATE TABLE api_configurations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    enabled BOOLEAN DEFAULT true,
    openapi_source_type VARCHAR(50) NOT NULL CHECK (openapi_source_type IN ('URL', 'FILE', 'TEXT')),
    openapi_url TEXT,
    openapi_content TEXT,
    openapi_hash VARCHAR(64), -- SHA-256 hash for change detection
    base_url TEXT NOT NULL,
    timeout_seconds INTEGER DEFAULT 30 CHECK (timeout_seconds > 0 AND timeout_seconds <= 300),
    rate_limit_per_minute INTEGER DEFAULT 100 CHECK (rate_limit_per_minute > 0),
    auth_config JSONB,
    advanced_config JSONB,
    status VARCHAR(20) DEFAULT 'INACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'ERROR', 'CONFIGURING')),
    last_sync_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(255) DEFAULT 'system'
);

-- Tool Definitions Cache Table
-- Caches generated MCP tool definitions from OpenAPI specifications
CREATE TABLE tool_definitions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    api_config_id UUID NOT NULL REFERENCES api_configurations(id) ON DELETE CASCADE,
    tool_name VARCHAR(255) NOT NULL,
    tool_definition JSONB NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    endpoint_path TEXT NOT NULL,
    operation_id VARCHAR(255),
    tags TEXT[],
    is_deprecated BOOLEAN DEFAULT false,
    generated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(api_config_id, tool_name)
);

-- Configuration Audit Log Table
-- Tracks all changes to API configurations for compliance and debugging
CREATE TABLE configuration_audit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    config_id UUID REFERENCES api_configurations(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'ENABLE', 'DISABLE', 'SYNC')),
    changes JSONB,
    timestamp TIMESTAMP DEFAULT NOW(),
    user_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT
);

-- Tool Usage Statistics Table
-- Tracks usage metrics for each MCP tool
CREATE TABLE tool_usage_stats (
    tool_name VARCHAR(255) PRIMARY KEY,
    api_config_id UUID REFERENCES api_configurations(id) ON DELETE CASCADE,
    invocation_count BIGINT DEFAULT 0,
    success_count BIGINT DEFAULT 0,
    error_count BIGINT DEFAULT 0,
    total_execution_time_ms BIGINT DEFAULT 0,
    min_execution_time_ms INTEGER DEFAULT 0,
    max_execution_time_ms INTEGER DEFAULT 0,
    avg_execution_time_ms INTEGER DEFAULT 0,
    last_used TIMESTAMP,
    last_success TIMESTAMP,
    last_error TIMESTAMP,
    last_error_message TEXT,
    last_error_code VARCHAR(50)
);

-- Environment Variables Table
-- Stores encrypted environment variables for API authentication
CREATE TABLE environment_variables (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    encrypted_value TEXT NOT NULL,
    encryption_key_id VARCHAR(50),
    is_required BOOLEAN DEFAULT false,
    is_masked BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(255) DEFAULT 'system'
);

-- API Health Status Table
-- Tracks health status of configured APIs
CREATE TABLE api_health_status (
    api_config_id UUID PRIMARY KEY REFERENCES api_configurations(id) ON DELETE CASCADE,
    status VARCHAR(20) DEFAULT 'UNKNOWN' CHECK (status IN ('HEALTHY', 'DEGRADED', 'UNHEALTHY', 'UNKNOWN')),
    last_check TIMESTAMP DEFAULT NOW(),
    response_time_ms INTEGER,
    error_message TEXT,
    consecutive_failures INTEGER DEFAULT 0,
    last_success TIMESTAMP,
    uptime_percentage DECIMAL(5,2) DEFAULT 0.00
);

-- Configuration Templates Table
-- Stores reusable configuration templates for common API patterns
CREATE TABLE configuration_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    category VARCHAR(100),
    template_data JSONB NOT NULL,
    is_public BOOLEAN DEFAULT false,
    download_count INTEGER DEFAULT 0,
    rating DECIMAL(3,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(255)
);

-- =============================================================================
-- INDEXES FOR PERFORMANCE
-- =============================================================================

-- API Configurations indexes
CREATE INDEX idx_api_configurations_name ON api_configurations(name);
CREATE INDEX idx_api_configurations_enabled ON api_configurations(enabled);
CREATE INDEX idx_api_configurations_status ON api_configurations(status);
CREATE INDEX idx_api_configurations_created_at ON api_configurations(created_at);

-- Tool Definitions indexes
CREATE INDEX idx_tool_definitions_api_config_id ON tool_definitions(api_config_id);
CREATE INDEX idx_tool_definitions_tool_name ON tool_definitions(tool_name);
CREATE INDEX idx_tool_definitions_http_method ON tool_definitions(http_method);
CREATE INDEX idx_tool_definitions_tags ON tool_definitions USING GIN(tags);
CREATE INDEX idx_tool_definitions_deprecated ON tool_definitions(is_deprecated);

-- Configuration Audit indexes
CREATE INDEX idx_configuration_audit_config_id ON configuration_audit(config_id);
CREATE INDEX idx_configuration_audit_action ON configuration_audit(action);
CREATE INDEX idx_configuration_audit_timestamp ON configuration_audit(timestamp);
CREATE INDEX idx_configuration_audit_user_id ON configuration_audit(user_id);

-- Tool Usage Statistics indexes
CREATE INDEX idx_tool_usage_stats_api_config_id ON tool_usage_stats(api_config_id);
CREATE INDEX idx_tool_usage_stats_last_used ON tool_usage_stats(last_used);
CREATE INDEX idx_tool_usage_stats_invocation_count ON tool_usage_stats(invocation_count);

-- Environment Variables indexes
CREATE INDEX idx_environment_variables_name ON environment_variables(name);
CREATE INDEX idx_environment_variables_required ON environment_variables(is_required);

-- Configuration Templates indexes
CREATE INDEX idx_configuration_templates_category ON configuration_templates(category);
CREATE INDEX idx_configuration_templates_public ON configuration_templates(is_public);
CREATE INDEX idx_configuration_templates_rating ON configuration_templates(rating);

-- =============================================================================
-- FUNCTIONS AND TRIGGERS
-- =============================================================================

-- Function to update the updated_at timestamp automatically
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_api_configurations_updated_at 
    BEFORE UPDATE ON api_configurations 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_environment_variables_updated_at 
    BEFORE UPDATE ON environment_variables 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_configuration_templates_updated_at 
    BEFORE UPDATE ON configuration_templates 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to calculate average execution time automatically
CREATE OR REPLACE FUNCTION update_avg_execution_time()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.invocation_count > 0 THEN
        NEW.avg_execution_time_ms = NEW.total_execution_time_ms / NEW.invocation_count;
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger for automatic average calculation
CREATE TRIGGER update_tool_usage_stats_avg 
    BEFORE UPDATE ON tool_usage_stats 
    FOR EACH ROW EXECUTE FUNCTION update_avg_execution_time();

-- =============================================================================
-- VIEWS FOR COMMON QUERIES
-- =============================================================================

-- View for active API configurations with tool counts
CREATE VIEW active_apis_with_tools AS
SELECT 
    ac.id,
    ac.name,
    ac.description,
    ac.base_url,
    ac.status,
    ac.created_at,
    COUNT(td.id) as tool_count,
    MAX(td.generated_at) as last_tool_generation
FROM api_configurations ac
LEFT JOIN tool_definitions td ON ac.id = td.api_config_id
WHERE ac.enabled = true
GROUP BY ac.id, ac.name, ac.description, ac.base_url, ac.status, ac.created_at;

-- View for tool usage summary with success rates
CREATE VIEW tool_usage_summary AS
SELECT 
    tus.tool_name,
    ac.name as api_name,
    tus.invocation_count,
    tus.success_count,
    tus.error_count,
    CASE 
        WHEN tus.invocation_count > 0 
        THEN ROUND((tus.success_count::decimal / tus.invocation_count * 100), 2)
        ELSE 0 
    END as success_rate_percentage,
    tus.avg_execution_time_ms,
    tus.last_used
FROM tool_usage_stats tus
JOIN api_configurations ac ON tus.api_config_id = ac.id
ORDER BY tus.invocation_count DESC;
