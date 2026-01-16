package com.eqraa.reader.data

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
import com.eqraa.reader.data.model.ReadingProgressDto
import com.eqraa.reader.data.model.UpsertProgressParams
import com.eqraa.reader.utils.CFICompressor
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import com.eqraa.reader.data.model.SyncConflict
import com.eqraa.reader.data.model.ReadingPosition
import com.eqraa.reader.data.db.BooksDao
import io.github.jan.supabase.auth.auth

/**
 * Manager for syncing reading progress via Supabase.
 * Handles:
 * - Local-first updates (instant feedback)
 * - Debounced cloud sync (prevents database spam)
 * - Conflict resolution (via server-side RPC)
 * - Real-time updates (cross-device sync)
 * 
 * FIXES APPLIED:
 * - Consolidated timestamp management (single source of truth)
 * - Includes userId in all DTOs
 * - Immediate sync on app close
 * - Aligned conflict detection logic
 * - Sync status feedback
 */
class ReadingSyncManager(
    private val supabase: SupabaseClient,
    private val context: Context,
    private val booksDao: BooksDao,
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
    
    // Reduced debounce to 5 seconds (better UX)
    private val debounceDuration = 5000L
    
    // Conflict detection window aligned with server (10 seconds)
    private val conflictWindowMs = 10000L

    private val _remoteProgressFlow = MutableSharedFlow<ReadingProgressDto>(replay = 0)
    val remoteProgressFlow: SharedFlow<ReadingProgressDto> = _remoteProgressFlow.asSharedFlow()
    
    // Sync status flow for user feedback
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        object Success : SyncStatus()
        data class Failed(val error: String) : SyncStatus()
        object Offline : SyncStatus()
    }

    /**
     * Update reading progress (local-first with debounced sync)
     * Now saves to local DB immediately AND queues cloud sync
     */
    suspend fun updateProgress(
        bookId: Long,
        bookIdentifier: String,
        cfi: String,
        percentage: Float,
        pageNumber: Int? = null,
        chapterId: String? = null
    ) {
        val timestamp = System.currentTimeMillis()
        
        try {
            // 1. Save Locally FIRST (In Database, not just memory)
            booksDao.saveProgression(cfi, timestamp, bookId)
            Timber.d("✅ Local save for book $bookId at $timestamp")
            
            // 2. Update in-memory cache
            val userId = supabase.auth.currentUserOrNull()?.id
            if (userId == null) {
                Timber.w("User not authenticated, skipping cloud sync")
                _syncStatus.value = SyncStatus.Offline
                return
            }
            
            val compressedCfi = CFICompressor.compress(cfi)
            val progress = ReadingProgressDto(
                userId = userId, // FIX: Always include userId
                bookId = bookIdentifier,
                cfi = compressedCfi,
                percentage = percentage,
                pageNumber = pageNumber,
                chapterId = chapterId,
                updatedAt = timestamp,
                deviceId = deviceId
            )
            
            localCache.put(bookIdentifier, progress)
            
            // 3. Debounced Remote Sync
            syncJobs[bookIdentifier]?.cancel()
            syncJobs[bookIdentifier] = scope.launch {
                try {
                    delay(debounceDuration)
                    Timber.d("🔄 Executing debounced sync for $bookIdentifier")
                    _syncStatus.value = SyncStatus.Syncing
                    syncToSupabase(progress)
                    _syncStatus.value = SyncStatus.Success
                } catch (e: CancellationException) {
                    throw e // Re-throw to allow proper cancellation
                } catch (e: Exception) {
                    Timber.e(e, "Debounced sync failed")
                    _syncStatus.value = SyncStatus.Failed(e.message ?: "Unknown error")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update progress")
            _syncStatus.value = SyncStatus.Failed(e.message ?: "Unknown error")
            throw e
        }
    }
    
    /**
     * Force immediate sync (called on app close)
     */
    suspend fun forceSyncPending() {
        val pendingJobs = syncJobs.values.toList()
        Timber.d("⚡ Force syncing ${pendingJobs.size} pending jobs")
        
        // Cancel debounces and execute immediately
        pendingJobs.forEach { it.cancel() }
        
        localCache.asMap().values.forEach { progress ->
            try {
                syncToSupabase(progress)
            } catch (e: Exception) {
                Timber.e(e, "Failed to force sync ${progress.bookId}")
            }
        }
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
            ).decodeAs<com.eqraa.reader.data.model.SyncResponseDto>()
            
            // Update local cache with server response
            localCache.put(progress.bookId, response.data)
            
            if (response.updated) {
                Timber.d("✅ Cloud sync successful for book ${progress.bookId}")
            } else {
                Timber.i("ℹ️ Sync skipped for book ${progress.bookId} (Reason: ${if (response.conflict) "Conflict Window" else "No change"})")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Sync failed for book ${progress.bookId}")
            _syncStatus.value = SyncStatus.Failed(e.message ?: "Network error")
            throw e
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
            val userId = supabase.auth.currentUserOrNull()?.id ?: return null
            
            val result = supabase.postgrest.from("reading_progress")
                .select {
                    filter {
                        eq("book_id", bookId)
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<ReadingProgressDto>()
            
            if (result != null) {
                localCache.put(bookId, result)
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch progress for $bookId")
            _syncStatus.value = SyncStatus.Offline
            null
        }
    }

    /**
     * Start listening to real-time updates for other devices
     */
    fun startRealtimeSync() {
        scope.launch {
            try {
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
                            Timber.i("🌐 Remote update received for book ${progress.bookId}")
                        }
                    }
                }.launchIn(this)
                
                progressChannel.subscribe()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start realtime sync")
            }
        }
    }
    
    /**
     * Detects conflicts between local and remote positions.
     * ALIGNED with server logic: conflict if timestamps differ by > 10 seconds
     */
    fun detectConflicts(): Flow<List<SyncConflict>> = flow {
        remoteProgressFlow.collect { remote ->
            val local = localCache.getIfPresent(remote.bookId)
            if (local != null && isConflict(local, remote)) {
                val conflict = SyncConflict(
                    bookId = remote.bookId,
                    localPosition = ReadingPosition(
                        bookId = local.bookId,
                        cfi = CFICompressor.decompress(local.cfi),
                        percentage = local.percentage,
                        timestamp = local.updatedAt,
                        deviceId = local.deviceId,
                        pageNumber = local.pageNumber
                    ),
                    remotePosition = ReadingPosition(
                        bookId = remote.bookId,
                        cfi = CFICompressor.decompress(remote.cfi),
                        percentage = remote.percentage,
                        timestamp = remote.updatedAt,
                        deviceId = remote.deviceId,
                        pageNumber = remote.pageNumber
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
        // Ignore if same device
        if (local.deviceId == remote.deviceId) return false

        // ALIGNED WITH SERVER: Check if outside 10-second conflict window
        val timeDiff = abs(local.updatedAt - remote.updatedAt)
        val isOutsideConflictWindow = timeDiff > conflictWindowMs
        
        // And positions differ significantly
        val positionsDiffer = abs(local.percentage - remote.percentage) > 0.01 // 1%
        
        return isOutsideConflictWindow && positionsDiffer
    }

    /**
     * Force upload a position, overwriting any remote conflicts
     */
    suspend fun forceUploadPosition(bookId: String, position: ReadingPosition) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        
        val progress = ReadingProgressDto(
            userId = userId,
            bookId = bookId,
            cfi = CFICompressor.compress(position.cfi),
            percentage = position.percentage,
            updatedAt = System.currentTimeMillis(), // Use current time to ensure it wins
            deviceId = deviceId,
            pageNumber = position.pageNumber,
            chapterId = null
        )
        
        localCache.put(bookId, progress)
        syncToSupabase(progress)
    }

    /**
     * Force download a position, overwriting any local conflicts
     */
    suspend fun forceDownloadPosition(bookId: String, position: ReadingPosition) {
        // Fetch the latest remote to ensure we have full data
        val remoteDto = getProgress(bookId)
        if (remoteDto != null) {
            // Update local cache
            localCache.put(bookId, remoteDto)
            // Notify app to update UI
            _remoteProgressFlow.emit(remoteDto)
        }
    }

    fun cleanup() {
        scope.launch {
            try {
                forceSyncPending()
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync on cleanup")
            } finally {
                syncJobs.values.forEach { it.cancel() }
                scope.cancel()
            }
        }
    }
}
