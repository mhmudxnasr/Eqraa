package com.eqraa.reader.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.eqraa.reader.data.db.AppDatabase
import com.eqraa.reader.data.model.SyncLogEntry

class ActivityLogViewModel(application: Application) : AndroidViewModel(application) {
    private val syncLogDao = AppDatabase.getDatabase(application).syncLogDao()

    val logs: StateFlow<List<SyncLogEntry>> = syncLogDao.getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
