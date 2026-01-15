-- Fix API Health Status Schema
-- 
-- This migration fixes the schema mismatch between the database and entity
-- by renaming the 'status' column to 'health_state' and adding missing columns
-- 
-- Migration: V3__Fix_Health_Status_Schema.sql
-- Description: Fixes schema mismatch in api_health_status table
-- 
-- @author Neeraj Yadav
-- @version 1.0.0
-- @since 2025-12-26

-- Add missing columns and rename existing ones to match entity expectations
ALTER TABLE api_health_status 
    RENAME COLUMN status TO health_state;

-- Add missing columns that the entity expects
ALTER TABLE api_health_status 
    ADD COLUMN IF NOT EXISTS last_checked_at TIMESTAMP DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS health_details JSONB,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();

-- Update existing records to have proper timestamps
UPDATE api_health_status 
SET 
    last_checked_at = COALESCE(last_check, NOW()),
    created_at = COALESCE(last_success, NOW()),
    updated_at = NOW()
WHERE last_checked_at IS NULL OR created_at IS NULL;

-- Create index for better query performance
CREATE INDEX IF NOT EXISTS idx_api_health_status_health_state ON api_health_status(health_state);
CREATE INDEX IF NOT EXISTS idx_api_health_status_last_checked ON api_health_status(last_checked_at);
