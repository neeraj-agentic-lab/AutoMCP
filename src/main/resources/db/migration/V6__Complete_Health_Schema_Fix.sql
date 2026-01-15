-- Complete fix for api_health_status table schema
-- 
-- This migration adds all remaining missing columns that are expected by the
-- ReactiveApiHealthService to ensure complete schema compatibility
-- 
-- Migration: V6__Complete_Health_Schema_Fix.sql
-- Description: Adds all remaining missing columns to api_health_status table
-- 
-- @author Neeraj Yadav
-- @version 1.0.0
-- @since 2026-01-01

-- Add all remaining missing columns
ALTER TABLE api_health_status 
    ADD COLUMN IF NOT EXISTS error_rate_percent DECIMAL(5,2) DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS response_time_trend VARCHAR(20) DEFAULT 'stable',
    ADD COLUMN IF NOT EXISTS consecutive_failures INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error_message TEXT,
    ADD COLUMN IF NOT EXISTS uptime_percentage DECIMAL(5,2) DEFAULT 100.0;

-- Update existing records to have default values
UPDATE api_health_status 
SET 
    error_rate_percent = COALESCE(error_rate_percent, 0.0),
    response_time_trend = COALESCE(response_time_trend, 'stable'),
    consecutive_failures = COALESCE(consecutive_failures, 0),
    uptime_percentage = COALESCE(uptime_percentage, 100.0)
WHERE error_rate_percent IS NULL 
   OR response_time_trend IS NULL 
   OR consecutive_failures IS NULL 
   OR uptime_percentage IS NULL;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_api_health_status_error_rate ON api_health_status(error_rate_percent);
CREATE INDEX IF NOT EXISTS idx_api_health_status_consecutive_failures ON api_health_status(consecutive_failures);
