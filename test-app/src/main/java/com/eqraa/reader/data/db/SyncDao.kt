package com.eqraa.reader.data.db

import androidx.room.*
import com.eqraa.reader.data.model.SyncAction

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: SyncAction): Long

    @Query("SELECT * FROM ${SyncAction.TABLE_NAME} ORDER BY timestamp ASC")
    suspend fun getAllActions(): List<SyncAction>

    @Query("DELETE FROM ${SyncAction.TABLE_NAME} WHERE id = :id")
    suspend fun deleteAction(id: Long)

    @Query("SELECT * FROM ${SyncAction.TABLE_NAME} WHERE type = :type AND `key` = :key LIMIT 1")
    suspend fun getActionByTarget(type: String, key: String): SyncAction?

    @Update
    suspend fun updateAction(action: SyncAction)

    @Query("DELETE FROM ${SyncAction.TABLE_NAME}")
    suspend fun clearAll()
}
