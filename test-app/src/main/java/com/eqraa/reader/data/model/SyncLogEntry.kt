package com.eqraa.reader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_log")
data class SyncLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "SUCCESS", "ERROR", "INFO"
    val source: String, // "ReadingProgress", "UserPrefs", "CloudLibrary"
    val message: String,
    val details: String? = null
)
