package org.readium.r2.testapp.data

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.testapp.Application
import org.readium.r2.testapp.data.model.ReadingProgressDto
import org.readium.r2.testapp.data.model.UserPreferencesDto
import org.readium.r2.testapp.data.db.AppDatabase
import org.readium.r2.testapp.data.model.SyncAction
import timber.log.Timber
import java.util.concurrent.TimeUnit
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.Json
import org.readium.r2.testapp.data.SupabaseService
import org.readium.r2.testapp.data.HighlightDto
import org.readium.r2.testapp.data.model.BookmarkDto

/**
 * Background worker for syncing data to Supabase.
 * 
 * Handles:
 * - Reading progress sync
 * - User preferences sync  
 * - Highlights sync
 * 
 * Features:
 * - Offline queue with automatic retry
 * - Exponential backoff on failures
 * - Periodic background sync option
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val supabase = SupabaseService.client

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(applicationContext)
            val syncDao = database.syncDao()
            
            val actions = syncDao.getAllActions()
            if (actions.isEmpty()) {
                Timber.d("SyncWorker: No pending actions")
                return@withContext Result.success()
            }

            Timber.d("SyncWorker: Processing ${actions.size} actions")

            var failedCount = 0
            var successCount = 0

            for (action in actions) {
                val success = try {
                    processAction(action)
                } catch (e: Exception) {
                    Timber.e(e, "SyncWorker: Failed to process action ${action.id} (${action.type})")
                    false
                }

                if (success) {
                    syncDao.deleteAction(action.id)
                    successCount++
                } else {
                    failedCount++
                    handleFailedAction(syncDao, action)
                }
            }

            Timber.d("SyncWorker: Completed. Success: $successCount, Failed: $failedCount")

            if (failedCount > 0) Result.retry() else Result.success()
            
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker: Critical error")
            Result.retry()
        }
    }

    private suspend fun processAction(action: SyncAction): Boolean {
        return when (action.type) {
            SyncAction.TYPE_POSITION -> {
                val dto = Json.decodeFromString<ReadingProgressDto>(action.payload)
                supabase.from("reading_progress").upsert(dto) { select() }
                Timber.d("SyncWorker: Synced reading progress for ${dto.bookId}")
                true
            }
            SyncAction.TYPE_PREFERENCE -> {
                val dto = Json.decodeFromString<UserPreferencesDto>(action.payload)
                supabase.from("user_preferences").upsert(dto) { select() }
                Timber.d("SyncWorker: Synced user preferences")
                true
            }
            TYPE_HIGHLIGHT -> {
                val dto = Json.decodeFromString<HighlightDto>(action.payload)
                if (dto.deleted) {
                    supabase.from("highlights").delete { filter { eq("id", dto.id) } }
                    Timber.d("SyncWorker: Deleted highlight ${dto.id}")
                } else {
                    supabase.from("highlights").upsert(dto) { select() }
                    Timber.d("SyncWorker: Synced highlight ${dto.id}")
                }
                true
            }
            TYPE_BOOKMARK -> {
                val dto = Json.decodeFromString<BookmarkDto>(action.payload)
                if (dto.deleted) {
                    supabase.from("bookmarks").delete { filter { eq("id", dto.id) } }
                } else {
                    supabase.from("bookmarks").upsert(dto) { select() }
                }
                Timber.d("SyncWorker: Synced bookmark ${dto.id}")
                true
            }
            else -> {
                Timber.w("SyncWorker: Unknown action type: ${action.type}")
                true // Don't retry unknown types
            }
        }
    }

    private suspend fun handleFailedAction(
        syncDao: org.readium.r2.testapp.data.db.SyncDao,
        action: SyncAction
    ) {
        val maxRetries = 10
        if (action.retryCount >= maxRetries) {
            Timber.w("SyncWorker: Action ${action.id} exceeded max retries ($maxRetries), removing")
            syncDao.deleteAction(action.id)
        } else {
            syncDao.updateAction(action.copy(retryCount = action.retryCount + 1))
            Timber.d("SyncWorker: Action ${action.id} retry count: ${action.retryCount + 1}")
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "SyncWorker"
        private const val PERIODIC_WORK_NAME = "PeriodicSyncWorker"
        
        // Custom action types
        const val TYPE_HIGHLIGHT = "highlight"
        const val TYPE_BOOKMARK = "bookmark"

        /**
         * Enqueue an immediate one-time sync.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
            )
            
            Timber.d("SyncWorker: Enqueued one-time sync")
        }

        /**
         * Schedule periodic background sync.
         * Runs every 15 minutes when connected to network and charging.
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresCharging(true) // Only when charging to save battery
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            
            Timber.d("SyncWorker: Scheduled periodic sync (every 15 min)")
        }

        /**
         * Cancel periodic background sync.
         */
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            Timber.d("SyncWorker: Cancelled periodic sync")
        }

        /**
         * Get pending actions count.
         */
        suspend fun getPendingCount(context: Context): Int {
            return AppDatabase.getDatabase(context).syncDao().getAllActions().size
        }
    }
}
