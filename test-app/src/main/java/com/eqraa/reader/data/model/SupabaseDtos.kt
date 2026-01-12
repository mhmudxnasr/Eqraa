package com.eqraa.reader.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReadingProgressDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("book_id") val bookId: String,
    @SerialName("cfi") val cfi: String,
    @SerialName("percentage") val percentage: Float,
    @SerialName("page_number") val pageNumber: Int? = null,
    @SerialName("chapter_id") val chapterId: String? = null,
    @SerialName("updated_at") val updatedAt: Long,          // Millisecond timestamp
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("sync_version") val syncVersion: Int = 1,
    @SerialName("last_opened_at") val lastOpenedAt: Long? = null,
    @SerialName("server_synced_at") val serverSyncedAt: String? = null
)

@Serializable
data class UpsertProgressParams(
    @SerialName("p_book_id") val bookId: String,
    @SerialName("p_cfi") val cfi: String?,
    @SerialName("p_percentage") val percentage: Float,
    @SerialName("p_device_id") val deviceId: String,
    @SerialName("p_updated_at") val updatedAt: Long,
    @SerialName("p_page_number") val pageNumber: Int? = null,
    @SerialName("p_chapter_id") val chapterId: String? = null
)

@Serializable
data class SyncResponseDto(
    @SerialName("updated") val updated: Boolean,
    @SerialName("conflict") val conflict: Boolean,
    @SerialName("data") val data: ReadingProgressDto
)

@Serializable
data class UserPreferencesDto(
    @SerialName("user_id") val userId: String,
    @SerialName("font_size") val fontSize: Int? = 100,
    @SerialName("theme") val theme: String? = "system",
    @SerialName("font_family") val fontFamily: String? = null,
    @SerialName("line_height") val lineHeight: Float? = null,
    @SerialName("margin") val margin: Float? = null,
    @SerialName("reading_speed") val readingSpeed: Int? = null,
    @SerialName("last_updated") val lastUpdated: Long? = null
)

@Serializable
data class CollectionDto(
    @SerialName("user_id") val userId: String,
    @SerialName("name") val name: String,
    @SerialName("book_ids") val bookIds: List<String>,
    @SerialName("timestamp") val timestamp: Long
)

@Serializable
data class NoteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("content") val content: String,
    @SerialName("cfi") val cfi: String,
    @SerialName("color") val color: String,
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("note") val note: String?
)

@Serializable
data class BookmarkDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("cfi") val cfi: String,
    @SerialName("title") val title: String?,
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("deleted") val deleted: Boolean = false
)

@Serializable
data class CloudBookDto(
    @SerialName("id") val id: String, // Supabase UUID or String ID
    @SerialName("user_id") val userId: String,
    @SerialName("identifier") val identifier: String,
    @SerialName("title") val title: String,
    @SerialName("author") val author: String,
    @SerialName("filename") val filename: String,
    @SerialName("stored_filename") val storedFilename: String,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("url") val url: String? = null, // For direct download if needed
    @SerialName("checksum") val checksum: String? = null // SHA-256 hash for integrity check
)
