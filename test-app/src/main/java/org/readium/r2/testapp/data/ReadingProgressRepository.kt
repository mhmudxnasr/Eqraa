package org.readium.r2.testapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.readium.r2.testapp.Application
import org.readium.r2.testapp.data.db.BooksDao
import org.readium.r2.testapp.data.db.SyncDao
import org.readium.r2.testapp.data.model.ReadingProgressDto
import org.readium.r2.testapp.data.model.SyncAction
import org.readium.r2.testapp.utils.NetworkMonitor
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import org.readium.r2.testapp.data.model.ReadingPosition
import timber.log.Timber

/**
 * Repository for managing reading progress.
 * 
 * Responsibilities:
 * 1. Save progress locally (SQLite).
 * 2. Queue sync actions for background worker.
 * 3. Fetch progress (Local vs Remote).
 * 4. Handle Conflict Resolution.
 */
class ReadingProgressRepository(
    private val booksDao: BooksDao,
    private val syncDao: SyncDao,
    private val context: Context // For SyncWorker enqueueing
) {
    private val supabase by lazy { SupabaseService.client }

    /**
     * Save reading progress:
     * 1. Writes to Local DB immediately.
     * 2. Queues a sync action for the background worker.
     */
    suspend fun saveProgress(
        bookId: Long,
        bookIdentifier: String,
        cfi: String,
        percentage: Float,
        pageNumber: Int? = null
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. Save Local
            booksDao.saveProgression(cfi, bookId)
            
            // Save timestamp to Prefs
            val prefs = context.getSharedPreferences("reading_progress_timestamps", Context.MODE_PRIVATE)
            val currentTimestamp = System.currentTimeMillis()
            prefs.edit().putLong("book_$bookId", currentTimestamp).apply()
            
            Timber.d("Progress saved locally for book $bookId at $currentTimestamp")

            // 2. Queue Sync
            val userId = supabase.auth.currentUserOrNull()?.id
            if (userId != null) {
                // ... sync logic ...
                // Use the same timestamp
                val app = context.applicationContext as Application
                val deviceId = app.readingSyncManager.deviceId
                Timber.v("ReadingProgressRepository: Preparing sync action for $bookIdentifier on device $deviceId")
                val compressedCfi = org.readium.r2.testapp.utils.CFICompressor.compress(cfi)
                
                val dto = ReadingProgressDto(
                    userId = userId,
                    bookId = bookIdentifier,
                    cfi = compressedCfi,
                    percentage = percentage,
                    pageNumber = pageNumber,
                    updatedAt = currentTimestamp,
                    deviceId = deviceId
                )
                
                val payload = Json.encodeToString(dto)
                
                // Updates existing action if present (Debounce effect via DB)
                val existing = syncDao.getActionByTarget(SyncAction.TYPE_POSITION, bookIdentifier)
                if (existing != null) {
                    syncDao.updateAction(existing.copy(payload = payload, timestamp = currentTimestamp))
                } else {
                    syncDao.insertAction(
                        SyncAction(
                            type = SyncAction.TYPE_POSITION,
                            key = bookIdentifier,
                            payload = payload,
                            timestamp = currentTimestamp
                        )
                    )
                }
                
                // Trigger Worker (One-time immediate attempt)
                SyncWorker.enqueue(context)
                Timber.d("Sync action queued for book $bookIdentifier")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save reading progress")
        }
    }

    /**
     * Get reading progress.
     * Currently returns local data via Dao, but can be extended to fetch remote.
     */
    suspend fun getRemoteProgress(bookIdentifier: String): ReadingPosition? = withContext(Dispatchers.IO) {
        try {
            val dto = supabase.from("reading_progress").select {
                filter { eq("book_id", bookIdentifier) }
            }.decodeSingleOrNull<ReadingProgressDto>()

            if (dto != null) {
                return@withContext ReadingPosition(
                    bookId = bookIdentifier,
                    cfi = org.readium.r2.testapp.utils.CFICompressor.decompress(dto.cfi),
                    percentage = dto.percentage,
                    timestamp = dto.updatedAt,
                    deviceId = dto.deviceId,
                    pageNumber = dto.pageNumber
                )
            }
            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch remote progress")
            return@withContext null
        }
    }

    /**
     * Get the last saved local progress (CF string and Timestamp)
     */
    suspend fun getLocalProgress(bookId: Long): Pair<String, Long>? = withContext(Dispatchers.IO) {
        val book = booksDao.get(bookId) ?: return@withContext null
        val cfi = book.progression ?: return@withContext null
        
        val prefs = context.getSharedPreferences("reading_progress_timestamps", Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("book_$bookId", 0L)
        
        return@withContext Pair(cfi, timestamp)
    }
}
