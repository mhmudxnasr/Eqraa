package org.readium.r2.testapp.ui.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.readium.r2.testapp.data.CloudLibraryManager
import org.readium.r2.testapp.data.ReadingProgressSyncManager
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
    private val readingSync: ReadingProgressSyncManager?,
    private val cloudLibrary: CloudLibraryManager?,
    private val realtimeSync: RealtimeSyncManager?,
    private val auth: AuthRepository
) : ViewModel() {

    private val networkMonitor = NetworkMonitor(context)

    // Main sync status for UI
    private val _statusInfo = MutableStateFlow(SyncStatusInfo(SyncStatusInfo.State.SYNCED))
    val statusInfo: StateFlow<SyncStatusInfo> = _statusInfo.asStateFlow()

    // Legacy status for backward compatibility
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    // Conflicts
    val conflicts: StateFlow<List<SyncConflict>> = readingSync?.conflicts ?: MutableStateFlow(emptyList())

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
    }

    private fun combineStates() {
        // Observe Auth
        auth.sessionStatus.onEach { 
            // Refresh pending count on auth change
            refreshPendingCount()
        }.launchIn(viewModelScope)

        // Combine Sync States
        val readingFlow = readingSync?.syncState ?: flowOf(ReadingProgressSyncManager.SyncState.Idle)
        val uploadingFlow = cloudLibrary?.isUploading ?: flowOf(false)
        val downloadingFlow = cloudLibrary?.isDownloading ?: flowOf(false)
        val networkFlow = networkMonitor.isOnline

        combine(readingFlow, uploadingFlow, downloadingFlow, networkFlow, _pendingCount) { reading, upload, download, online, pending ->
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
                reading is ReadingProgressSyncManager.SyncState.Syncing -> {
                    _status.value = SyncStatus.Syncing("Reading Progress")
                    SyncStatusInfo(
                        state = SyncStatusInfo.State.SYNCING,
                        message = "Syncing progress...",
                        lastSyncTime = _lastSyncTime.value
                    )
                }
                reading is ReadingProgressSyncManager.SyncState.Error -> {
                    _status.value = SyncStatus.Error(reading.message)
                    SyncStatusInfo(
                        state = SyncStatusInfo.State.ERROR,
                        message = reading.message,
                        pendingCount = pending,
                        lastSyncTime = _lastSyncTime.value
                    )
                }
                reading is ReadingProgressSyncManager.SyncState.Success -> {
                    _lastSyncTime.value = reading.timestamp
                    _status.value = SyncStatus.Idle
                    SyncStatusInfo(
                        state = if (pending > 0) SyncStatusInfo.State.PENDING else SyncStatusInfo.State.SYNCED,
                        pendingCount = pending,
                        lastSyncTime = reading.timestamp
                    )
                }
                pending > 0 -> {
                    _status.value = SyncStatus.Idle
                    SyncStatusInfo(
                        state = SyncStatusInfo.State.PENDING,
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
        realtimeSync?.events?.onEach { event ->
            when (event) {
                is RealtimeSyncManager.RealtimeEvent.ReadingProgressUpdated -> {
                    _toastMessage.emit("Reading position updated from another device")
                }
                is RealtimeSyncManager.RealtimeEvent.PreferencesUpdated -> {
                    _toastMessage.emit("Preferences synced from another device")
                }
                is RealtimeSyncManager.RealtimeEvent.HighlightUpdated -> {
                    _toastMessage.emit("Highlights synced")
                }
                is RealtimeSyncManager.RealtimeEvent.Error -> {
                    // Don't show error toasts, just log
                }
            }
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
        private val readingSync: ReadingProgressSyncManager?,
        private val cloudLibrary: CloudLibraryManager?,
        private val realtimeSync: RealtimeSyncManager?,
        private val auth: AuthRepository
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SyncStatusViewModel(context, readingSync, cloudLibrary, realtimeSync, auth) as T
        }
    }
}

