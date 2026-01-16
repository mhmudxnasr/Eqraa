package com.eqraa.reader.data

import com.eqraa.reader.data.model.ReadingPosition
import com.eqraa.reader.utils.CFICompressor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.shared.publication.Locator
import timber.log.Timber
import kotlin.math.abs

/**
 * Manages the active reading session state, enforcing "Safe Start" protocols
 * and implementing smart sync heuristics (e.g. filtering rapid page turns).
 */
class ReadingSessionManager(
    private val readingProgressRepository: ReadingProgressRepository,
    private val readingSyncManager: ReadingSyncManager,
    private val scope: CoroutineScope
) {

    sealed class SessionState {
        object Initializing : SessionState()
        object Reconciling : SessionState() // Checking remote vs local
        object Ready : SessionState()       // Safe to save
        data class Syncing(val percentage: Float) : SessionState()
        object Synced : SessionState()
        data class Error(val message: String) : SessionState()
        data class Conflict(val local: ReadingPosition, val remote: ReadingPosition) : SessionState()
    }

    private val _state = MutableStateFlow<SessionState>(SessionState.Initializing)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var bookId: Long = -1
    private var bookIdentifier: String = ""
    
    // Heuristics configuration
    private val RAPID_SCROLL_THRESHOLD_MS = 1000L // 1 second
    private var lastPageChangeTime: Long = 0
    private var pendingSyncJob: Job? = null
    
    // Flags
    private var isInitialized = false
    private var allowSaves = false

    /**
     * Step 1: Initialize the session and perform the "Safe Start" Handshake
     */
    fun initializeSession(id: Long, identifier: String) {
        if (isInitialized) return
        bookId = id
        bookIdentifier = identifier
        isInitialized = true
        
        scope.launch {
            _state.value = SessionState.Initializing
            performHandshake()
        }
    }

    private suspend fun performHandshake() {
        try {
            _state.value = SessionState.Reconciling
            
            // 1. Get Local State
            val localState = readingProgressRepository.getLocalProgress(bookId)
            val localTimestamp = localState?.second ?: 0L
            
            // 2. Get Remote State (Async)
            val remoteDto = readingSyncManager.getProgress(bookIdentifier)
            
            // 3. Arbitration Logic (The "Brilliant" Part)
            if (remoteDto != null) {
                val remoteTimestamp = remoteDto.updatedAt
                val timeDiff = remoteTimestamp - localTimestamp
                
                Timber.d("🤝 Handshake: Local=$localTimestamp, Remote=$remoteTimestamp, Diff=$timeDiff")
                
                if (remoteTimestamp > localTimestamp) {
                    // Case A: Remote is newer (New device, or re-install)
                    Timber.i("📥 Remote is newer. Enforcing remote state.")
                    
                    val remotePos = ReadingPosition(
                        bookId = bookIdentifier,
                        cfi = CFICompressor.decompress(remoteDto.cfi),
                        percentage = remoteDto.percentage,
                        timestamp = remoteDto.updatedAt,
                        deviceId = remoteDto.deviceId,
                        pageNumber = remoteDto.pageNumber
                    )
                    
                    // Emit conflict/jump event? 
                    // For "Smart" auto-sync, we might just want to return it so VM can apply it.
                    // But here we need to block saves until applied.
                    allowSaves = false
                    _state.value = SessionState.Conflict(
                        local = ReadingPosition(
                             bookId = bookIdentifier,
                             cfi = localState?.first ?: "",
                             percentage = 0f, // Approx
                             timestamp = localTimestamp,
                             deviceId = readingSyncManager.deviceId
                        ),
                        remote = remotePos
                    )
                } else {
                    // Case B: Local is equal or newer.
                    // Case C: No remote data.
                    Timber.d("✅ Local is authoritative. Ready to read.")
                    allowSaves = true
                    _state.value = SessionState.Ready
                }
            } else {
                 // No remote data -> Fresh book
                 Timber.d("✨ New book (no remote). Ready.")
                 allowSaves = true
                 _state.value = SessionState.Ready
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Handshake failed")
            _state.value = SessionState.Error("Sync Check Failed")
            // Fallback: Allow reading, but maybe show warning?
            allowSaves = true 
        }
    }

    /**
     * Called when user confirms "Jump to Remote" or "Keep Local"
     */
    fun resolveConflict(useRemote: Boolean, remotePosition: ReadingPosition?) {
        if (useRemote && remotePosition != null) {
            // Apply remote
             scope.launch {
                 allowSaves = true // Now authoritative
                 // The VM will actually move the reader, but we update our state
                 _state.value = SessionState.Ready // Or Synced?
             }
        } else {
            // Keep local
            allowSaves = true
            _state.value = SessionState.Ready
        }
    }

    /**
     * Step 2: Handle Page Changes with Heuristics
     */
    fun onPageChanged(locator: Locator) {
        if (!allowSaves) {
            Timber.v("🚫 Saves blocked (Handshake incomplete or waiting for resolution)")
            return
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceLastPage = currentTime - lastPageChangeTime
        lastPageChangeTime = currentTime

        // 1. Is this a rapid scroll?
        if (timeSinceLastPage < RAPID_SCROLL_THRESHOLD_MS) {
            Timber.v("🐇 Rapid scroll detected (${timeSinceLastPage}ms). Saving LOCAL ONLY.")
            
            // Update local DB only (Cheap)
            scope.launch {
                readingProgressRepository.saveProgressLocally(
                    bookId = bookId,
                    cfi = locator.toJSON().toString(),
                    timestamp = currentTime
                )
            }
            
            // Cancel any pending heavy cloud sync
            pendingSyncJob?.cancel()
            _state.value = SessionState.Ready // Just "Ready", not "Syncing"
            
        } else {
            // 2. User paused / Slow read. Trigger Cloud Sync.
            Timber.v("🐢 Stable reading detected. scheduling Cloud Sync.")
            
            pendingSyncJob?.cancel()
            pendingSyncJob = scope.launch {
                _state.value = SessionState.Syncing((locator.locations.totalProgression ?: 0.0).toFloat())
                
                // Save locally first
                 readingProgressRepository.saveProgressLocally(
                    bookId = bookId,
                    cfi = locator.toJSON().toString(),
                    timestamp = currentTime
                )
                
                // Trigger Manager (which has its own debounce, but we add an extra layer of "intent" here)
                val percentage = (locator.locations.totalProgression ?: 0.0).toFloat()
                
                readingSyncManager.updateProgress(
                    bookId = bookId,
                    bookIdentifier = bookIdentifier,
                    cfi = locator.toJSON().toString(),
                    percentage = percentage,
                    pageNumber = locator.locations.position
                )
                
                // Ideally wait for sync success? ReadingSyncManager is fire-and-forget-ish currently
                // But we can listen to its status flow.
                // For now, optimistically set Synced
                delay(1000) 
                _state.value = SessionState.Synced
            }
        }
    }
    
    fun onCleanup() {
        pendingSyncJob?.cancel()
        // Force final sync?
    }
}
