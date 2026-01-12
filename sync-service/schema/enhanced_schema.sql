-- ============================================
-- Reading Progress Sync Schema (Supabase/PostgreSQL)
-- ============================================

-- 1. READING PROGRESS TABLE
CREATE TABLE IF NOT EXISTS public.reading_progress (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
  book_id TEXT NOT NULL,
  
  -- Progress data
  cfi TEXT NOT NULL,                   -- Compressed CFI string
  percentage FLOAT8 NOT NULL DEFAULT 0,
  page_number INTEGER,
  chapter_id TEXT,
  
  -- Sync metadata
  updated_at BIGINT NOT NULL,          -- Millisecond timestamp (Client-provided)
  server_synced_at TIMESTAMPTZ DEFAULT now(), -- Server-side sync timestamp
  created_at TIMESTAMPTZ DEFAULT now(),
  device_id TEXT NOT NULL,             -- UUID of the device
  sync_version INTEGER DEFAULT 1,
  last_opened_at BIGINT,
  
  -- Unique constraint per user and book
  UNIQUE(user_id, book_id)
);

-- 2. INDEXES
CREATE INDEX IF NOT EXISTS idx_reading_progress_updated ON public.reading_progress (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_reading_progress_server_updated ON public.reading_progress (user_id, server_synced_at DESC);

-- 3. ENABLE ROW LEVEL SECURITY
ALTER TABLE public.reading_progress ENABLE ROW LEVEL SECURITY;

-- 4. RLS POLICIES
DROP POLICY IF EXISTS "Users can only manage their own reading progress" ON public.reading_progress;
CREATE POLICY "Users can only manage their own reading progress"
  ON public.reading_progress FOR ALL
  USING (auth.uid() = user_id);

-- 5. ENABLE REALTIME REPLICATION
-- This is critical for syncing to work consistently.
ALTER PUBLICATION supabase_realtime ADD TABLE public.reading_progress;

-- 6. CONFLICT-AWARE UPSERT FUNCTION (RPC)
-- This function implements Last-Write-Wins logic with a server-side "hot" window.
-- Fixes clock skew and race conditions by using server time for the window check.
CREATE OR REPLACE FUNCTION upsert_reading_progress(
  p_book_id TEXT,
  p_cfi TEXT,
  p_percentage FLOAT8,
  p_device_id TEXT,
  p_updated_at BIGINT,
  p_page_number INTEGER DEFAULT NULL,
  p_chapter_id TEXT DEFAULT NULL
) RETURNS JSON AS $$
DECLARE
  v_user_id UUID;
  v_result public.reading_progress;
  v_current_server_time TIMESTAMPTZ := now();
  v_current_server_ms BIGINT := EXTRACT(EPOCH FROM v_current_server_time) * 1000;
  v_should_update BOOLEAN;
  v_existing_record RECORD;
BEGIN
  v_user_id := auth.uid();
  
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'Not authenticated';
  END IF;

  -- Validate timestamp (optional but recommended)
  IF p_updated_at > v_current_server_ms + 300000 THEN
    RAISE EXCEPTION 'Timestamp too far in future (>5min clock skew)';
  END IF;
  
  IF p_updated_at < v_current_server_ms - 2592000000 THEN
    RAISE EXCEPTION 'Timestamp too old (>30 days)';
  END IF;

  -- Lock the row to prevent race conditions
  SELECT * INTO v_existing_record
  FROM public.reading_progress
  WHERE user_id = v_user_id AND book_id = p_book_id
  FOR UPDATE;

  -- Determine if we should update
  v_should_update := v_existing_record IS NULL OR (
    p_updated_at > v_existing_record.updated_at AND (
      p_device_id = v_existing_record.device_id OR 
      v_existing_record.server_synced_at < v_current_server_time - INTERVAL '10 seconds'
    )
  );

  INSERT INTO public.reading_progress (
    user_id, book_id, cfi, percentage, device_id, 
    updated_at, page_number, chapter_id, last_opened_at, server_synced_at
  ) VALUES (
    v_user_id, p_book_id, p_cfi, p_percentage, p_device_id,
    p_updated_at, p_page_number, p_chapter_id, p_updated_at, v_current_server_time
  )
  ON CONFLICT (user_id, book_id) DO UPDATE SET
    cfi = CASE WHEN v_should_update THEN EXCLUDED.cfi ELSE reading_progress.cfi END,
    percentage = CASE WHEN v_should_update THEN EXCLUDED.percentage ELSE reading_progress.percentage END,
    page_number = CASE WHEN v_should_update THEN EXCLUDED.page_number ELSE reading_progress.page_number END,
    chapter_id = CASE WHEN v_should_update THEN EXCLUDED.chapter_id ELSE reading_progress.chapter_id END,
    device_id = CASE WHEN v_should_update THEN EXCLUDED.device_id ELSE reading_progress.device_id END,
    updated_at = CASE WHEN v_should_update THEN EXCLUDED.updated_at ELSE reading_progress.updated_at END,
    sync_version = CASE WHEN v_should_update THEN reading_progress.sync_version + 1 ELSE reading_progress.sync_version END,
    last_opened_at = GREATEST(EXCLUDED.last_opened_at, COALESCE(reading_progress.last_opened_at, 0)),
    server_synced_at = v_current_server_time
  RETURNING *
  INTO v_result;
  
  RETURN json_build_object(
    'updated', v_should_update,
    'conflict', NOT v_should_update,
    'data', row_to_json(v_result)
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 6. HELPER FUNCTIONS
CREATE OR REPLACE FUNCTION get_reading_progress(p_book_id TEXT)
RETURNS JSON AS $$
  SELECT row_to_json(rp)
  FROM public.reading_progress rp
  WHERE user_id = auth.uid() AND book_id = p_book_id;
$$ LANGUAGE sql SECURITY DEFINER STABLE;
