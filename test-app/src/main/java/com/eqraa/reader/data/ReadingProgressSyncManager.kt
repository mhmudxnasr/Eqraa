package com.eqraa.reader.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import com.eqraa.reader.data.db.BooksDao

/**
 * DEPRECATED: This class is no longer used.
 * Replaced by [ReadingProgressRepository] and [SyncWorker].
 * kept for binary compatibility if needed, but should be removed.
 */
@Deprecated("Use ReadingProgressRepository")
class ReadingProgressSyncManager(
    private val userId: String
) {
    fun initialize(context: Context, coroutineScope: CoroutineScope, booksDao: BooksDao) {
        // No-op
    }
    
    fun scheduleSync(bookId: String, cfi: String, percentage: Float) {
        // No-op
    }
}
