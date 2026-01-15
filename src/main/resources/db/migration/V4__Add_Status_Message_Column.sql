-- Add missing status_message column to api_health_status table
-- 
-- This migration adds the status_message column that is expected by the
-- ReactiveApiHealthService but was missing from the database schema
-- 
-- Migration: V4__Add_Status_Message_Column.sql
-- Description: Adds missing status_message column to api_health_status table
-- 
-- @author Neeraj Yadav
-- @version 1.0.0
-- @since 2026-01-01

-- Add the missing status_message column
ALTER TABLE api_health_status 
    ADD COLUMN IF NOT EXISTS status_message TEXT;

-- Update existing records to have a default status message
UPDATE api_health_status 
SET status_message = CASE 
    WHEN health_state = 'UP' THEN 'API is healthy'
    WHEN health_state = 'DOWN' THEN 'API is not responding'
    ELSE 'Status unknown'
END
WHERE status_message IS NULL;

-- Create index for better query performance on status_message
CREATE INDEX IF NOT EXISTS idx_api_health_status_status_message ON api_health_status(status_message);
