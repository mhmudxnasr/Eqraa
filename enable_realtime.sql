-- Safe Enable Realtime Script
-- This script safely adds tables to Realtime, ignoring them if they are already added.
-- Run this in Supabase Dashboard -> SQL Editor

DO $$
BEGIN
    -- 1. Reading Progress
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE reading_progress;
    EXCEPTIOn WHEN duplicate_object THEN
        RAISE NOTICE 'reading_progress is already in realtime';
    END;

    -- 2. User Preferences
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE user_preferences;
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'user_preferences is already in realtime';
    END;

    -- 3. Highlights
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE highlights;
    EXCEPTION WHEN duplicate_object THEN
        RAISE NOTICE 'highlights is already in realtime';
    END;
END $$;
