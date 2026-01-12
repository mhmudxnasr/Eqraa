package org.readium.r2.testapp.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.readium.r2.testapp.data.db.AppDatabase
import org.readium.r2.testapp.data.model.SyncLogEntry

class ActivityLogViewModel(application: Application) : AndroidViewModel(application) {
    private val syncLogDao = AppDatabase.getDatabase(application).syncLogDao()

    val logs: StateFlow<List<SyncLogEntry>> = syncLogDao.getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
