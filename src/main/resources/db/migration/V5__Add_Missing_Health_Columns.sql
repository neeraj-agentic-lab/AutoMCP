-- Add missing columns to api_health_status table
-- 
-- This migration adds additional columns that are expected by the
-- ReactiveApiHealthService but were missing from the database schema
-- 
-- Migration: V5__Add_Missing_Health_Columns.sql
-- Description: Adds missing columns to api_health_status table
-- 
-- @author Neeraj Yadav
-- @version 1.0.0
-- @since 2026-01-01

-- Add the missing columns
ALTER TABLE api_health_status 
    ADD COLUMN IF NOT EXISTS avg_response_time_ms INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS error_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS success_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_requests INTEGER DEFAULT 0;

-- Update existing records to have default values
UPDATE api_health_status 
SET 
    avg_response_time_ms = COALESCE(avg_response_time_ms, 0),
    error_count = COALESCE(error_count, 0),
    success_count = COALESCE(success_count, 0),
    total_requests = COALESCE(total_requests, 0)
WHERE avg_response_time_ms IS NULL 
   OR error_count IS NULL 
   OR success_count IS NULL 
   OR total_requests IS NULL;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_api_health_status_avg_response_time ON api_health_status(avg_response_time_ms);
CREATE INDEX IF NOT EXISTS idx_api_health_status_error_count ON api_health_status(error_count);
