/*
 * Reading Position Sync - Data Model
 * Stores reading position using EPUB CFI for device-independent positioning
 */

package com.eqraa.reader.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a reading position that can be synced across devices.
 * Uses EPUB CFI (Canonical Fragment Identifier) for precise, device-independent positioning.
 *
 * @property bookId Unique book identifier (e.g., ISBN or file hash)
 * @property cfi EPUB CFI string (e.g., "epubcfi(/6/4[chap1ref]!/4/2/1:0)")
 * @property percentage Reading percentage (0.0 to 1.0) as fallback
 * @property timestamp Unix timestamp in milliseconds when position was recorded
 */
/**
 * Represents a reading position that can be synced across devices.
 * Uses EPUB CFI (Canonical Fragment Identifier) for precise, device-independent positioning.
 *
 * @property bookId Unique book identifier (e.g., ISBN or file hash)
 * @property cfi EPUB CFI string (e.g., "epubcfi(/6/4[chap1ref]!/4/2/1:0)")
 * @property percentage Reading percentage (0.0 to 1.0) as fallback
 * @property timestamp Unix timestamp in milliseconds when position was recorded
 */
@Serializable
data class ReadingPosition(
    val bookId: String,
    val cfi: String,
    val percentage: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String? = null,
    val pageNumber: Int? = null,
    val totalPages: Int? = null
) : java.io.Serializable
