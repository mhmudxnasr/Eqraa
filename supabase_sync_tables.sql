-- Supabase SQL for Eqraa Sync Enhancement
-- Run this in the Supabase SQL Editor

-- ============================================
-- HIGHLIGHTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS highlights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    local_id BIGINT,
    book_id TEXT NOT NULL,
    href TEXT NOT NULL,
    cfi TEXT,
    style TEXT DEFAULT 'highlight',
    color INTEGER DEFAULT 0,
    text_before TEXT,
    text_highlight TEXT,
    text_after TEXT,
    annotation TEXT,
    total_progression DOUBLE PRECISION DEFAULT 0,
    timestamp BIGINT NOT NULL,
    deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_highlights_book_id ON highlights(book_id);
CREATE INDEX IF NOT EXISTS idx_highlights_user_id ON highlights(user_id);

-- Enable Row Level Security
ALTER TABLE highlights ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only access their own highlights
CREATE POLICY "Users can view own highlights" ON highlights
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own highlights" ON highlights
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own highlights" ON highlights
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own highlights" ON highlights
    FOR DELETE USING (auth.uid() = user_id);

-- ============================================
-- BOOKMARKS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS bookmarks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    book_id TEXT NOT NULL,
    cfi TEXT NOT NULL,
    title TEXT,
    timestamp BIGINT NOT NULL,
    deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_bookmarks_book_id ON bookmarks(book_id);
CREATE INDEX IF NOT EXISTS idx_bookmarks_user_id ON bookmarks(user_id);

-- Enable Row Level Security
ALTER TABLE bookmarks ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only access their own bookmarks
CREATE POLICY "Users can view own bookmarks" ON bookmarks
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own bookmarks" ON bookmarks
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own bookmarks" ON bookmarks
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own bookmarks" ON bookmarks
    FOR DELETE USING (auth.uid() = user_id);

-- ============================================
-- COLLECTIONS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    book_ids TEXT[] DEFAULT '{}',
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Enable Row Level Security
ALTER TABLE collections ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY "Users can view own collections" ON collections
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own collections" ON collections
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own collections" ON collections
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own collections" ON collections
    FOR DELETE USING (auth.uid() = user_id);

-- ============================================
-- READING SESSIONS TABLE (for stats)
-- ============================================
CREATE TABLE IF NOT EXISTS reading_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    book_id TEXT NOT NULL,
    date DATE NOT NULL,
    duration_seconds INTEGER DEFAULT 0,
    pages_read INTEGER DEFAULT 0,
    start_cfi TEXT,
    end_cfi TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_reading_sessions_date ON reading_sessions(date);
CREATE INDEX IF NOT EXISTS idx_reading_sessions_user_id ON reading_sessions(user_id);

-- Enable Row Level Security
ALTER TABLE reading_sessions ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY "Users can view own sessions" ON reading_sessions
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own sessions" ON reading_sessions
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- ============================================
-- ENABLE REALTIME FOR TABLES
-- ============================================
-- Go to Supabase Dashboard > Database > Replication
-- Enable realtime for: reading_progress, user_preferences, highlights
-- Or run:
ALTER PUBLICATION supabase_realtime ADD TABLE highlights;
ALTER PUBLICATION supabase_realtime ADD TABLE bookmarks;
ALTER PUBLICATION supabase_realtime ADD TABLE collections;
