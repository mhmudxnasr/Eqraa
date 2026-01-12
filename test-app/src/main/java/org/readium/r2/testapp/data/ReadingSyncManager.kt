package org.readium.r2.testapp.data

import android.content.Context
import android.content.SharedPreferences
import com.google.common.cache.CacheBuilder
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.readium.r2.testapp.data.model.ReadingProgressDto
import org.readium.r2.testapp.data.model.UpsertProgressParams
import org.readium.r2.testapp.utils.CFICompressor
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.readium.r2.testapp.data.model.SyncConflict
import org.readium.r2.testapp.data.model.ReadingPosition

/**
 * Manager for syncing reading progress via Supabase.
 * Handles:
 * - Local-first updates (instant feedback)
 * - Debounced cloud sync (prevents database spam)
 * - Conflict resolution (via server-side RPC)
 * - Real-time updates (cross-device sync)
 */
class ReadingSyncManager(
    private val supabase: SupabaseClient,
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("reading_sync", Context.MODE_PRIVATE)
    
    val deviceId: String by lazy {
        prefs.getString("device_id", null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", newId).apply()
            newId
        }
    }
    
    private val syncJobs = mutableMapOf<String, Job>()
    
    // In-memory cache for fast access
    private val localCache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build<String, ReadingProgressDto>()
    
    // Debounce duration in milliseconds (30 seconds as per spec)
    private val debounceDuration = 30000L

    private val _remoteProgressFlow = MutableSharedFlow<ReadingProgressDto>(replay = 0)
    val remoteProgressFlow: SharedFlow<ReadingProgressDto> = _remoteProgressFlow.asSharedFlow()

    /**
     * Update reading progress (local-first with debounced sync)
     */
    fun updateProgress(
        bookId: String,
        cfi: String,
        percentage: Float,
        pageNumber: Int? = null,
        chapterId: String? = null
    ) {
        val timestamp = System.currentTimeMillis()
        val compressedCfi = CFICompressor.compress(cfi)
        
        val progress = ReadingProgressDto(
            bookId = bookId,
            cfi = compressedCfi,
            percentage = percentage,
            pageNumber = pageNumber,
            chapterId = chapterId,
            updatedAt = timestamp,
            deviceId = deviceId
        )
        
        // 1. Save Locally (In-memory)
        localCache.put(bookId, progress)
        
        // 2. Debounced Remote Sync
        syncJobs[bookId]?.cancel()
        syncJobs[bookId] = scope.launch {
            Timber.v("ReadingSyncManager: Debouncing sync for $bookId (2000ms)")
            delay(debounceDuration)
            Timber.d("ReadingSyncManager: Executing debounced sync for $bookId")
            syncToSupabase(progress)
        }
        
        Timber.v("Local progress updated for book $bookId: $percentage")
    }
    
    /**
     * Sync to Supabase using RPC function
     */
    private suspend fun syncToSupabase(progress: ReadingProgressDto) {
        try {
            val params = UpsertProgressParams(
                bookId = progress.bookId,
                cfi = progress.cfi,
                percentage = progress.percentage,
                deviceId = progress.deviceId,
                updatedAt = progress.updatedAt,
                pageNumber = progress.pageNumber,
                chapterId = progress.chapterId
            )
            
            val response = supabase.postgrest.rpc(
                function = "upsert_reading_progress",
                parameters = params
            ).decodeAs<org.readium.r2.testapp.data.model.SyncResponseDto>()
            
            // Update local cache with server response (contains server-side resolved data)
            localCache.put(progress.bookId, response.data)
            
            if (response.updated) {
                Timber.d("✅ Cloud sync successful for book ${progress.bookId}")
            } else {
                Timber.i("ℹ️ Sync skipped for book ${progress.bookId} (Reason: ${if (response.conflict) "Hot Window / Conflict" else "No change"})")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Sync failed for book ${progress.bookId}")
            // Optional: Schedule via SyncWorker for retry when back online
            SyncWorker.enqueue(context)
        }
    }
    
    /**
     * Get latest progress for a book
     */
    suspend fun getProgress(bookId: String): ReadingProgressDto? {
        // 1. Try Memory Cache
        localCache.getIfPresent(bookId)?.let { return it }
        
        // 2. Try Fetch from Supabase
        return try {
            val result = supabase.postgrest.from("reading_progress")
                .select {
                    filter {
                        eq("book_id", bookId)
                    }
                }
                .decodeSingleOrNull<ReadingProgressDto>()
            
            if (result != null) {
                localCache.put(bookId, result)
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch progress for $bookId")
            null
        }
    }

    /**
     * Start listening to real-time updates for other devices
     */
    fun startRealtimeSync() {
        scope.launch {
            val progressChannel = supabase.realtime.channel("reading_progress_sync")
            val changeFlow = progressChannel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "reading_progress"
            }
            
            changeFlow.onEach { action ->
                val progress = action.decodeRecord<ReadingProgressDto>()
                
                // Only notify if update is from another device
                if (progress.deviceId != deviceId) {
                    val local = localCache.getIfPresent(progress.bookId)
                    // If remote is newer, update local and notify listeners
                    if (local == null || progress.updatedAt > local.updatedAt) {
                        localCache.put(progress.bookId, progress)
                        _remoteProgressFlow.emit(progress)
                        Timber.i("Remote update received for book ${progress.bookId}")
                    }
                }
            }.launchIn(this)
            
            progressChannel.subscribe()
        }
    }
    
    /**
     * Detects conflicts between local and remote positions.
     * A conflict occurs when both have been updated since last sync
     * and timestamps differ by > threshold (e.g., 1 minute).
     *
     * Note: This is a simplified detection. In a real scenario, you might want to compare
     * against a "last known common base" to detect true forks.
     * Here we just check if local and remote differ significantly and both are "recent".
     */
    fun detectConflicts(): Flow<List<SyncConflict>> = flow {
        // In a real implementation, you might want to observe database changes or
        // periodically check for conflicts.
        // For this example, we'll check whenever the remote progress flow emits.
        
        remoteProgressFlow.collect { remote ->
            val local = localCache.getIfPresent(remote.bookId)
            if (local != null && isConflict(local, remote)) {
                val conflict = SyncConflict(
                    bookId = remote.bookId,
                    localPosition = ReadingPosition(
                        bookId = local.bookId,
                        cfi = CFICompressor.decompress(local.cfi),
                        percentage = local.percentage,
                        timestamp = local.updatedAt
                    ),
                    remotePosition = ReadingPosition(
                        bookId = remote.bookId,
                        cfi = CFICompressor.decompress(remote.cfi),
                        percentage = remote.percentage,
                        timestamp = remote.updatedAt
                    )
                )
                emit(listOf(conflict))
            } else {
                emit(emptyList())
            }
        }
    }

    private fun isConflict(
        local: ReadingProgressDto,
        remote: ReadingProgressDto
    ): Boolean {
        // Ignore if same device (shouldn't happen via remote flow usually, but safe to check)
        if (local.deviceId == remote.deviceId) return false

        // Conflict if they differ significantly
        val positionsDiffer = abs(local.percentage - remote.percentage) > 0.01 // 1% difference
        
        // And maybe check if timestamps are somewhat close or both are "new" 
        // compared to some last sync time? 
        // For now, simple difference check is a good start for "Conflict" behavior demonstration.
        // A more robust check would involve vector clocks or tracking last viewed states.
        
        return positionsDiffer
    }

    /**
     * Force upload a position, overwriting any remote conflicts
     */
    suspend fun forceUploadPosition(bookId: String, position: ReadingPosition) {
         updateProgress(
             bookId = bookId,
             cfi = position.cfi,
             percentage = position.percentage.toFloat(),
             // We don't have pageNumber/chapterId in ReadingPosition easily available here 
             // unless we fetch it or store it. For now, we'll proceed with basic info.
             // Ideally ReadingPosition should have these fields or we re-fetch local.
         )
         // We might want to clear the debounce and sync immediately
         syncJobs[bookId]?.cancel()
         val local = localCache.getIfPresent(bookId)
         if (local != null) {
             syncToSupabase(local)
         }
    }

    /**
     * Force download a position, overwriting any local conflicts
     */
    suspend fun forceDownloadPosition(bookId: String, position: ReadingPosition) {
        // This effectively accepts the remote version as the "true" version.
        // We update our local cache and notify any listeners (like the Reader) 
        // to update the UI.
        
        // We need to construct a DTO to put in cache/notify
        // Since we don't have all fields in ReadingPosition, we might need to 
        // rely on `remoteProgressFlow`'s last value or refetch.
        // But `position` passed here likely comes from the Conflict object which has limited data.
        
        // Better approach: fetch the latest remote again to get full data
        val remoteDto = getProgress(bookId)
        if (remoteDto != null) {
             // Update local cache
             localCache.put(bookId, remoteDto)
             // Notify app to update UI (ReaderViewModel listens to this)
             _remoteProgressFlow.emit(remoteDto)
        }
    }

    fun cleanup() {
        syncJobs.values.forEach { it.cancel() }
        scope.cancel()
    }
}
