package org.readium.r2.testapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.readium.r2.testapp.data.model.SyncLogEntry

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(entry: SyncLogEntry)

    @Query("SELECT * FROM sync_log ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<SyncLogEntry>>

    @Query("DELETE FROM sync_log")
    suspend fun clearLogs()
}
