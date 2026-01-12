package org.readium.r2.testapp.ui.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.readium.r2.testapp.data.CloudLibraryManager
import org.readium.r2.testapp.data.ReadingSyncManager
import org.readium.r2.testapp.data.RealtimeSyncManager
import org.readium.r2.testapp.data.SyncWorker
import org.readium.r2.testapp.data.model.SyncConflict
import org.readium.r2.testapp.data.auth.AuthRepository
import org.readium.r2.testapp.utils.NetworkMonitor

/**
 * ViewModel for sync status across the app.
 * Combines status from all sync managers and realtime events.
 */
class SyncStatusViewModel(
    private val context: Context,
    // private val readingSync: ReadingProgressSyncManager? (Removed)
    private val cloudLibrary: CloudLibraryManager?,
    private val realtimeSync: RealtimeSyncManager?,
    private val readingSync: ReadingSyncManager?,
    private val auth: AuthRepository
) : ViewModel() {

    private val networkMonitor = NetworkMonitor(context)

    // Main sync status for UI
    private val _statusInfo = MutableStateFlow(SyncStatusInfo(SyncStatusInfo.State.SYNCED))
    val statusInfo: StateFlow<SyncStatusInfo> = _statusInfo.asStateFlow()

    // Legacy status for backward compatibility
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    // FIX: Make this mutable and actually populate it
    private val _conflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val conflicts: StateFlow<List<SyncConflict>> = _conflicts.asStateFlow()

    // Realtime events for toasts
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // Last sync timestamp
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    // Pending actions count
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    init {
        combineStates()
        observeRealtimeEvents()
        observeNetwork()
        observeConflicts()
    }

    // ADD: Observe conflicts from ReadingSyncManager
    private fun observeConflicts() {
        readingSync?.let { syncManager ->
            viewModelScope.launch {
                syncManager.detectConflicts().collect { conflictsList ->
                    _conflicts.value = conflictsList
                }
            }
        }
    }

    // ADD: Handle conflict resolution
    fun resolveConflict(conflict: SyncConflict, keepLocal: Boolean) {
        viewModelScope.launch {
            try {
                if (keepLocal) {
                    // Push local position to cloud, overwriting remote
                    readingSync?.forceUploadPosition(
                        bookId = conflict.bookId,
                        position = conflict.localPosition
                    )
                    _toastMessage.emit("Kept local version")
                } else {
                    // Accept remote position, overwriting local
                    readingSync?.forceDownloadPosition(
                        bookId = conflict.bookId,
                        position = conflict.remotePosition
                    )
                    _toastMessage.emit("Kept cloud version")
                }
                
                // Remove resolved conflict from list
                _conflicts.value = _conflicts.value.filterNot { 
                    it.bookId == conflict.bookId 
                }
                
            } catch (e: Exception) {
                _toastMessage.emit("Failed to resolve conflict: ${e.message}")
            }
        }
    }

    private fun combineStates() {
        // Observe Auth
        auth.sessionStatus.onEach { 
            // Refresh pending count on auth change
            refreshPendingCount()
        }.launchIn(viewModelScope)

        // Combine Sync States
        // Combine Sync States
        // val readingFlow = readingSync?.syncState ?: flowOf(ReadingProgressSyncManager.SyncState.Idle) (Removed)
        val uploadingFlow = cloudLibrary?.isUploading ?: flowOf(false)
        val downloadingFlow = cloudLibrary?.isDownloading ?: flowOf(false)
        val networkFlow = networkMonitor.isOnline

        combine(uploadingFlow, downloadingFlow, networkFlow, _pendingCount) { upload, download, online, pending ->
            when {
                !online -> {
                    _status.value = SyncStatus.Offline
                    SyncStatusInfo(
                        state = SyncStatusInfo.State.OFFLINE,
                        pendingCount = pending,
                        lastSyncTime = _lastSyncTime.value
                    )
                }
                upload || download -> {
                    _status.value = SyncStatus.Syncing("Cloud Library")
                    SyncStatusInfo(
                        state = SyncStatusInfo.State.SYNCING,
                        message = "Syncing library...",
                        lastSyncTime = _lastSyncTime.value
                    )
                }
                // Removed specific ReadingProgressSyncManager states
                // reading is ReadingProgressSyncManager.SyncState.Syncing -> ...
                
                pending > 0 -> {
                    _status.value = SyncStatus.Syncing("Pushing changes...")
                    SyncStatusInfo(
                        state = SyncStatusInfo.State.SYNCING,
                        message = "Syncing...",
                        pendingCount = pending,
                        lastSyncTime = _lastSyncTime.value
                    )
                }
                else -> {
                    _status.value = SyncStatus.Idle
                    SyncStatusInfo(
                        state = SyncStatusInfo.State.SYNCED,
                        lastSyncTime = _lastSyncTime.value
                    )
                }
            }
        }.onEach {
            _statusInfo.value = it
        }.launchIn(viewModelScope)
    }

    private fun observeRealtimeEvents() {
        // Observe global realtime events
        realtimeSync?.events?.onEach { event ->
            when (event) {
                is RealtimeSyncManager.RealtimeEvent.PreferencesUpdated -> {
                    _toastMessage.emit("Preferences synced from another device")
                }
                is RealtimeSyncManager.RealtimeEvent.HighlightUpdated -> {
                    _toastMessage.emit("Highlights synced")
                }
                is RealtimeSyncManager.RealtimeEvent.Error -> {
                    // Don't show error toasts, just log
                }
                else -> {}
            }
        }?.launchIn(viewModelScope)

        // Observe reading progress updates specifically
        readingSync?.remoteProgressFlow?.onEach { progress ->
            _toastMessage.emit("Reading position updated for ${progress.bookId}")
        }?.launchIn(viewModelScope)
    }

    private fun observeNetwork() {
        networkMonitor.isOnline.onEach { online ->
            if (online) {
                refreshPendingCount()
            }
        }.launchIn(viewModelScope)
    }

    fun refreshPendingCount() {
        viewModelScope.launch {
            _pendingCount.value = SyncWorker.getPendingCount(context)
        }
    }

    fun triggerSync() {
        SyncWorker.enqueue(context)
        refreshPendingCount()
    }

    fun enablePeriodicSync() {
        SyncWorker.schedulePeriodicSync(context)
    }

    fun disablePeriodicSync() {
        SyncWorker.cancelPeriodicSync(context)
    }

    // Legacy status sealed class for backward compatibility
    sealed class SyncStatus {
        object Idle : SyncStatus()
        data class Syncing(val message: String) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
        object Offline : SyncStatus()
    }

    class Factory(
        private val context: Context,
        // private val readingSync: ReadingProgressSyncManager?,
        private val cloudLibrary: CloudLibraryManager?,
        private val realtimeSync: RealtimeSyncManager?,
        private val readingSync: ReadingSyncManager?,
        private val auth: AuthRepository
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SyncStatusViewModel(context, cloudLibrary, realtimeSync, readingSync, auth) as T
        }
    }
}

