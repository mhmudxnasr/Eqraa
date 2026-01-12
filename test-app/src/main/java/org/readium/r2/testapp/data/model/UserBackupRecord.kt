package org.readium.r2.testapp.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents the full user backup structure matching the MongoDB schema.
 */
@Serializable
data class UserBackupRecord(
    val _id: String,
    val lastBackup: Long = System.currentTimeMillis(),
    val settings: Map<String, JsonElement> = emptyMap(),
    val stats: Map<String, JsonElement> = emptyMap(),
    val books: List<BookBackupRecord> = emptyList()
)

@Serializable
data class BookBackupRecord(
    val bookId: String,
    val title: String?,
    val author: String?,
    val href: String,
    val highlights: List<Map<String, JsonElement>> = emptyList(),
    val bookmarks: List<Map<String, JsonElement>> = emptyList(),
    val progress: Map<String, JsonElement>? = null
)
