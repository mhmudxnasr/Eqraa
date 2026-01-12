package com.eqraa.reader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = SyncAction.TABLE_NAME)
data class SyncAction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "position", "preference", "collection"
    val key: String,  // bookId or preference name
    val payload: String, // JSON payload
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
) {
    companion object {
        const val TABLE_NAME = "sync_actions"
        
        const val TYPE_POSITION = "position"
        const val TYPE_PREFERENCE = "preference"
        const val TYPE_COLLECTION = "collection"
    }
}
