-- ============================================
-- 1. HIGHLIGHTS TABLE (Unified storage for highlights and notes)
-- ============================================
CREATE TABLE IF NOT EXISTS public.highlights (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  local_id BIGINT,
  book_id TEXT NOT NULL,
  href TEXT NOT NULL,
  cfi TEXT,
  style TEXT,
  color INTEGER,
  text_before TEXT,
  text_highlight TEXT,
  text_after TEXT,
  annotation TEXT,
  total_progression DOUBLE PRECISION DEFAULT 0,
  timestamp BIGINT,
  deleted BOOLEAN DEFAULT false,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.highlights ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view their own highlights" ON public.highlights;
CREATE POLICY "Users can view their own highlights" ON public.highlights FOR SELECT USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert their own highlights" ON public.highlights;
CREATE POLICY "Users can insert their own highlights" ON public.highlights FOR INSERT WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update their own highlights" ON public.highlights;
CREATE POLICY "Users can update their own highlights" ON public.highlights FOR UPDATE USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can delete their own highlights" ON public.highlights;
CREATE POLICY "Users can delete their own highlights" ON public.highlights FOR DELETE USING (auth.uid() = user_id);

-- ============================================
-- 2. READING PROGRESS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS public.reading_progress (
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  book_id TEXT NOT NULL,
  cfi TEXT NOT NULL,
  percentage FLOAT NOT NULL,
  timestamp BIGINT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  PRIMARY KEY (user_id, book_id)
);

ALTER TABLE public.reading_progress ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can manage own progress" ON public.reading_progress;
CREATE POLICY "Users can manage own progress" ON public.reading_progress FOR ALL USING (auth.uid() = user_id);

-- ============================================
-- 3. USER PREFERENCES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS public.user_preferences (
  user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  font_size INTEGER DEFAULT 100,
  theme TEXT DEFAULT 'paper',
  font_family TEXT,
  line_height FLOAT,
  margin FLOAT,
  reading_speed INTEGER,
  last_updated BIGINT,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.user_preferences ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can manage own preferences" ON public.user_preferences;
CREATE POLICY "Users can manage own preferences" ON public.user_preferences FOR ALL USING (auth.uid() = user_id);

-- ============================================
-- 4. CLOUD BOOKS TABLE (Metadata for Storage)
-- ============================================
CREATE TABLE IF NOT EXISTS public.cloud_books (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  identifier TEXT NOT NULL,
  title TEXT NOT NULL,
  author TEXT NOT NULL,
  filename TEXT NOT NULL,
  stored_filename TEXT NOT NULL,
  media_type TEXT,
  url TEXT,
  checksum TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.cloud_books ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can manage own cloud books" ON public.cloud_books;
CREATE POLICY "Users can manage own cloud books" ON public.cloud_books FOR ALL USING (auth.uid() = user_id);

-- ============================================
-- 5. BOOKMARKS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS public.bookmarks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  book_id TEXT NOT NULL,
  cfi TEXT NOT NULL,
  title TEXT,
  timestamp BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT false,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.bookmarks ENABLE ROW LEVEL SECURITY;

-- ============================================
-- 6. NOTES TABLE (Legacy Backup Support)
-- ============================================
CREATE TABLE IF NOT EXISTS public.notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  book_id TEXT NOT NULL,
  content TEXT,
  cfi TEXT NOT NULL,
  color TEXT,
  timestamp BIGINT NOT NULL,
  note TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.notes ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage own notes" ON public.notes FOR ALL USING (auth.uid() = user_id);

-- ============================================
-- 7. STORAGE BUCKETS (For Books)
-- ============================================
-- Create the 'books' storage bucket
INSERT INTO storage.buckets (id, name, public)
VALUES ('books', 'books', true)
ON CONFLICT (id) DO NOTHING;

-- Policies for Storage
DROP POLICY IF EXISTS "Public Access" ON storage.objects;
CREATE POLICY "Public Access"
ON storage.objects FOR SELECT
USING ( bucket_id = 'books' );

DROP POLICY IF EXISTS "Authenticated Export" ON storage.objects;
CREATE POLICY "Authenticated Export"
ON storage.objects FOR INSERT
WITH CHECK ( bucket_id = 'books' AND auth.role() = 'authenticated' );

DROP POLICY IF EXISTS "Authenticated Admin" ON storage.objects;
CREATE POLICY "Authenticated Admin"
ON storage.objects FOR ALL
USING ( bucket_id = 'books' AND auth.uid() = owner );

-- ============================================
-- 8. ENABLE REALTIME
-- ============================================
-- Note: Realtime might need manual enabling in the dashboard for these tables
ALTER PUBLICATION supabase_realtime ADD TABLE highlights;
ALTER PUBLICATION supabase_realtime ADD TABLE reading_progress;
ALTER PUBLICATION supabase_realtime ADD TABLE user_preferences;
