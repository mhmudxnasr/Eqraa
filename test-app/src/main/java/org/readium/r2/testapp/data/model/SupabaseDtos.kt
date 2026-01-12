package org.readium.r2.testapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReadingProgressDto(
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("cfi") val cfi: String,
    @SerialName("percentage") val percentage: Float,
    @SerialName("timestamp") val timestamp: Long
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
    @SerialName("identifier") val identifier: String,
    @SerialName("title") val title: String,
    @SerialName("author") val author: String,
    @SerialName("filename") val filename: String,
    @SerialName("stored_filename") val storedFilename: String,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("url") val url: String? = null // For direct download if needed
)
