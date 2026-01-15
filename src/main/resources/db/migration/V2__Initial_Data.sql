-- MCP REST Adapter - Initial Data Migration
-- 
-- This migration inserts initial data including default environment variables,
-- configuration templates, and system settings needed for the application to function.
-- 
-- Migration: V2__Initial_Data.sql
-- Description: Inserts default configuration templates, environment variables,
--              and initial system data for the MCP REST Adapter.
-- 
-- @author Neeraj Yadav
-- @version 1.0.0
-- @since 2025-12-16

-- =============================================================================
-- DEFAULT ENVIRONMENT VARIABLES
-- =============================================================================

-- Insert default environment variables for system configuration
INSERT INTO environment_variables (name, description, encrypted_value, is_required, is_masked) VALUES
('MCP_DEFAULT_TIMEOUT', 'Default timeout for API requests in seconds', '30', false, false),
('MCP_DEFAULT_RATE_LIMIT', 'Default rate limit per minute for API calls', '100', false, false),
('MCP_MAX_CONCURRENT_EXECUTIONS', 'Maximum number of concurrent tool executions', '100', false, false),
('MCP_ENCRYPTION_KEY', 'Master key for encrypting sensitive configuration data', 'change-me-in-production-32-chars!!', true, true),
('MCP_JWT_SECRET', 'Secret key for JWT token generation and validation', 'jwt-secret-change-in-production', true, true)
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- CONFIGURATION TEMPLATES
-- =============================================================================

-- Template for REST APIs with API Key authentication
INSERT INTO configuration_templates (name, description, category, template_data, is_public) VALUES
(
    'REST API with API Key',
    'Template for REST APIs that use API key authentication in headers',
    'Authentication',
    '{
        "description": "Standard REST API with API key authentication",
        "auth": {
            "type": "api_key",
            "location": "header",
            "name": "X-API-Key",
            "value": "$${API_KEY}"
        },
        "timeout": "30s",
        "rate_limit": 100,
        "advanced": {
            "headers": {
                "Accept": "application/json",
                "Content-Type": "application/json"
            },
            "retry": {
                "max_attempts": 3,
                "initial_delay": "100ms",
                "max_delay": "2s",
                "backoff_multiplier": 2.0
            }
        }
    }',
    true
),
(
    'OAuth2 Client Credentials',
    'Template for APIs using OAuth2 client credentials flow',
    'Authentication',
    '{
        "description": "OAuth2 client credentials authentication template",
        "auth": {
            "type": "oauth2_client_credentials",
            "token_url": "https://auth.example.com/oauth/token",
            "client_id": "$${CLIENT_ID}",
            "client_secret": "$${CLIENT_SECRET}",
            "scopes": ["read", "write"]
        },
        "timeout": "30s",
        "rate_limit": 100,
        "advanced": {
            "headers": {
                "Accept": "application/json"
            },
            "retry": {
                "max_attempts": 3,
                "initial_delay": "100ms",
                "max_delay": "5s",
                "backoff_multiplier": 2.0
            }
        }
    }',
    true
),
(
    'Basic Authentication',
    'Template for APIs using basic username/password authentication',
    'Authentication',
    '{
        "description": "Basic HTTP authentication template",
        "auth": {
            "type": "basic",
            "username": "$${API_USERNAME}",
            "password": "$${API_PASSWORD}"
        },
        "timeout": "30s",
        "rate_limit": 100,
        "advanced": {
            "headers": {
                "Accept": "application/json"
            },
            "retry": {
                "max_attempts": 2,
                "initial_delay": "200ms",
                "max_delay": "1s",
                "backoff_multiplier": 1.5
            }
        }
    }',
    true
),
(
    'Bearer Token Authentication',
    'Template for APIs using Bearer token authentication',
    'Authentication',
    '{
        "description": "Bearer token authentication template",
        "auth": {
            "type": "bearer_token",
            "token": "$${BEARER_TOKEN}",
            "prefix": "Bearer"
        },
        "timeout": "30s",
        "rate_limit": 100,
        "advanced": {
            "headers": {
                "Accept": "application/json"
            },
            "retry": {
                "max_attempts": 3,
                "initial_delay": "100ms",
                "max_delay": "3s",
                "backoff_multiplier": 2.0
            }
        }
    }',
    true
),
(
    'No Authentication',
    'Template for public APIs that do not require authentication',
    'Public',
    '{
        "description": "Public API template without authentication",
        "auth": {
            "type": "none"
        },
        "timeout": "15s",
        "rate_limit": 200,
        "advanced": {
            "headers": {
                "Accept": "application/json",
                "User-Agent": "MCP-REST-Adapter/1.0"
            },
            "retry": {
                "max_attempts": 2,
                "initial_delay": "50ms",
                "max_delay": "1s",
                "backoff_multiplier": 2.0
            }
        }
    }',
    true
),
(
    'High Performance API',
    'Template optimized for high-performance APIs with aggressive caching',
    'Performance',
    '{
        "description": "High-performance API template with optimized settings",
        "auth": {
            "type": "api_key",
            "location": "header",
            "name": "Authorization",
            "value": "Bearer $${API_TOKEN}"
        },
        "timeout": "5s",
        "rate_limit": 1000,
        "advanced": {
            "headers": {
                "Accept": "application/json",
                "Cache-Control": "max-age=300"
            },
            "retry": {
                "max_attempts": 1,
                "initial_delay": "10ms",
                "max_delay": "100ms",
                "backoff_multiplier": 1.0
            },
            "circuit_breaker": {
                "failure_threshold": 25,
                "minimum_calls": 5,
                "wait_duration": "10s"
            }
        }
    }',
    true
)
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- SAMPLE API CONFIGURATION
-- =============================================================================

-- Insert a sample Pet Store API configuration for demonstration
INSERT INTO api_configurations (
    name, 
    description, 
    enabled, 
    openapi_source_type, 
    openapi_url, 
    base_url, 
    timeout_seconds, 
    rate_limit_per_minute,
    auth_config,
    status,
    created_by
) VALUES (
    'petstore-demo',
    'Sample Pet Store API for demonstration and testing purposes',
    false, -- Disabled by default
    'URL',
    'https://petstore.swagger.io/v2/swagger.json',
    'https://petstore.swagger.io/v2',
    30,
    100,
    '{
        "type": "none"
    }',
    'INACTIVE',
    'system'
) ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- SYSTEM AUDIT LOG ENTRY
-- =============================================================================

-- Log the successful database initialization
INSERT INTO configuration_audit (action, changes, user_id, timestamp) VALUES 
(
    'CREATE', 
    '{
        "message": "Database schema initialized successfully", 
        "version": "1.0.0",
        "migration": "V2__Initial_Data.sql",
        "tables_created": 7,
        "templates_added": 6,
        "environment_variables_added": 5
    }', 
    'system',
    NOW()
);

-- =============================================================================
-- PERFORMANCE OPTIMIZATION
-- =============================================================================

-- Analyze tables for better query planning
ANALYZE api_configurations;
ANALYZE tool_definitions;
ANALYZE configuration_audit;
ANALYZE tool_usage_stats;
ANALYZE environment_variables;
ANALYZE api_health_status;
ANALYZE configuration_templates;
