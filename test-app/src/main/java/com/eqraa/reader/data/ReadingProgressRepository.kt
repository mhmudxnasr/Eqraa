package com.eqraa.reader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.eqraa.reader.data.db.BooksDao
import com.eqraa.reader.data.model.ReadingPosition
import timber.log.Timber

/**
 * Repository for managing reading progress.
 * 
 * Responsibilities:
 * 1. Save progress locally (SQLite) with timestamp.
 * 2. Retrieve local progress with timestamp.
 * 3. Sync is now handled entirely by ReadingSyncManager.
 */
class ReadingProgressRepository(
    private val booksDao: BooksDao,
    private val context: Context
) {
    /**
     * Save reading progress locally only.
     * Syncing is handled by ReadingSyncManager.
     */
    suspend fun saveProgressLocally(
        bookId: Long,
        cfi: String,
        timestamp: Long
    ) = withContext(Dispatchers.IO) {
        try {
            // Save to local database atomically
            booksDao.saveProgression(cfi, timestamp, bookId)
            Timber.d("Progress saved locally for book $bookId at $timestamp")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save reading progress locally")
            throw e
        }
    }

    /**
     * Get the last saved local progress (CFI string and Timestamp)
     */
    suspend fun getLocalProgress(bookId: Long): Pair<String, Long>? = withContext(Dispatchers.IO) {
        try {
            val book = booksDao.get(bookId) ?: return@withContext null
            val cfi = book.progression ?: return@withContext null
            val timestamp = book.updatedAt
            
            return@withContext Pair(cfi, timestamp)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get local progress")
            return@withContext null
        }
    }

    /**
     * Get remote progress from Supabase.
     * This is now delegated to ReadingSyncManager.
     */
    suspend fun getRemoteProgress(bookIdentifier: String): ReadingPosition? {
        // This method is kept for backward compatibility but should use ReadingSyncManager
        Timber.w("getRemoteProgress called - use ReadingSyncManager.getProgress() instead")
        return null
    }
}
