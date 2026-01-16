/*
 * Highlights Sync Manager
 *
 * Synchronizes user highlights and annotations to Supabase.
 * Uses the same offline-first pattern as ReadingProgressSyncManager.
 */

package com.eqraa.reader.data

import android.content.Context
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.eqraa.reader.data.db.AppDatabase
import com.eqraa.reader.data.model.Highlight
import com.eqraa.reader.data.model.SyncAction
import timber.log.Timber

/**
 * DTO for syncing highlights to Supabase.
 */
@Serializable
data class HighlightDto(
    @SerialName("id") val id: String, // UUID for cloud
    @SerialName("user_id") val userId: String, // Required for RLS
    @SerialName("local_id") val localId: Long, // Local Room ID
    @SerialName("book_id") val bookId: String,
    @SerialName("href") val href: String,
    @SerialName("cfi") val cfi: String?, // From locations
    @SerialName("style") val style: String,
    @SerialName("color") val color: Int,
    @SerialName("text_before") val textBefore: String?,
    @SerialName("text_highlight") val textHighlight: String?,
    @SerialName("text_after") val textAfter: String?,
    @SerialName("annotation") val annotation: String?,
    @SerialName("total_progression") val totalProgression: Double,
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("deleted") val deleted: Boolean = false
)

/**
 * Manages synchronization of highlights and annotations.
 */
class HighlightSyncManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val TYPE_HIGHLIGHT = "highlight"
    }
    
    private val supabase by lazy { SupabaseService.client }

    private val syncDao = AppDatabase.getDatabase(context).syncDao()
    private val booksDao = AppDatabase.getDatabase(context).booksDao()
    private val database = AppDatabase.getDatabase(context)
    
    // Sync state
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        data class Success(val count: Int) : SyncState()
        data class Error(val message: String) : SyncState()
    }
    
    /**
     * Queue a highlight for sync.
     */
    suspend fun queueHighlightSync(highlight: Highlight) {
        try {
            // Ensure cloud_id exists before syncing
            if (highlight.cloudId == null) {
                val newCloudId = java.util.UUID.randomUUID().toString()
                highlight.cloudId = newCloudId
                // Update local DB immediately so we don't generate another one later
                // We don't have a direct "updateCloudId" but we have updateHighlightStyle/Annotation
                // or just insert (REPLACE). `insertHighlight` is REPLACE.
                booksDao.insertHighlight(highlight)
            }

            val dto = highlightToDto(highlight)
            val payload = Json.encodeToString(dto)
            
            // Check for existing action for this highlight
            val key = "${highlight.bookId}_${highlight.cloudId}" // Use cloud_id for key stability
            val existing = syncDao.getActionByTarget(TYPE_HIGHLIGHT, key)
            
            if (existing != null) {
                syncDao.updateAction(existing.copy(
                    payload = payload,
                    timestamp = System.currentTimeMillis()
                ))
            } else {
                syncDao.insertAction(
                    SyncAction(
                        type = TYPE_HIGHLIGHT,
                        key = key,
                        payload = payload,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            
            // Trigger worker
            SyncWorker.enqueue(context)
            Timber.d("HighlightSyncManager: Queued sync for highlight ${highlight.id} (cloud: ${highlight.cloudId})")
            
        } catch (e: Exception) {
            Timber.e(e, "HighlightSyncManager: Failed to queue highlight sync")
        }
    }
    
    /**
     * Queue a highlight deletion for sync.
     */
    suspend fun queueHighlightDelete(highlight: Highlight) {
        try {
            val dto = highlightToDto(highlight).copy(deleted = true)
            val payload = Json.encodeToString(dto)
            
            val key = "${highlight.bookId}_${highlight.id}"
            syncDao.insertAction(
                SyncAction(
                    type = TYPE_HIGHLIGHT,
                    key = key,
                    payload = payload,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            SyncWorker.enqueue(context)
            Timber.d("HighlightSyncManager: Queued delete for highlight ${highlight.id}")
            
        } catch (e: Exception) {
            Timber.e(e, "HighlightSyncManager: Failed to queue highlight delete")
        }
    }
    
    /**
     * Fetch all highlights for a book from the cloud.
     */
    suspend fun fetchHighlightsForBook(bookId: String): List<HighlightDto> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing
            
            val highlights = supabase.from("highlights").select {
                filter { eq("book_id", bookId) }
            }.decodeList<HighlightDto>()
            
            _syncState.value = SyncState.Success(highlights.size)
            Timber.d("HighlightSyncManager: Fetched ${highlights.size} highlights for book $bookId")
            highlights
            
        } catch (e: Exception) {
            Timber.e(e, "HighlightSyncManager: Failed to fetch highlights")
            _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            emptyList()
        }
    }
    
    /**
     * Sync a single highlight to the cloud (called by SyncWorker).
     */
    suspend fun syncHighlight(dto: HighlightDto): Boolean = withContext(Dispatchers.IO) {
        try {
            if (dto.deleted) {
                // Delete from cloud
                supabase.from("highlights").delete {
                    filter { eq("id", dto.id) }
                }
            } else {
                // Upsert to cloud
                supabase.from("highlights").upsert(dto) {
                    select()
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "HighlightSyncManager: Failed to sync highlight ${dto.id}")
            false
        }
    }
    
    /**
     * Full sync: Download all highlights from cloud and merge with local.
     */
    /**
     * Full sync: Download all highlights from cloud and merge with local.
     * Uses a new approach: fetch ALL user highlights and match them to local books.
     */
    suspend fun fullSync(bookId: String): Int = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing
            
            // Fetch ALL highlights for this user (not filtered by book)
            val allUserHighlights = supabase.from("highlights").select {
                filter { 
                    eq("deleted", false)
                }
            }.decodeList<HighlightDto>()
            
            var mergedCount = 0
            for (dto in allUserHighlights) {
                try {
                    // Try to find which local book this highlight belongs to
                    // It could have either global ID or legacy local ID
                    val localBookId = booksDao.getBookIdByIdentifier(dto.bookId) 
                        ?: dto.bookId.toLongOrNull() // Fallback for legacy local IDs
                    
                    if (localBookId == null) {
                        // Book doesn't exist on this device, skip
                        continue
                    }
                    
                    val local = booksDao.getHighlightByCloudId(dto.id)
                    if (local == null) {
                        // Insert new
                        booksDao.insertHighlight(dtoToHighlight(dto, localBookId))
                        mergedCount++
                    } else {
                        // Update if remote is newer
                        if (dto.timestamp > (local.creation ?: 0L)) {
                           val updated = dtoToHighlight(dto, localBookId).copy(id = local.id)
                           booksDao.insertHighlight(updated)
                           mergedCount++
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to merge highlight ${dto.id}")
                }
            }
            
            _syncState.value = SyncState.Success(mergedCount)
            Timber.d("HighlightSyncManager: Full sync complete. Total highlights: ${allUserHighlights.size}, Merged/Updated: $mergedCount")
            mergedCount
            
        } catch (e: Exception) {
            Timber.e(e, "HighlightSyncManager: Full sync failed")
            _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            0
        }
    }
    
    // Mapping Helpers
    
    private suspend fun highlightToDto(highlight: Highlight): HighlightDto {
        val cfi = highlight.locations.fragments.firstOrNull()
            ?: highlight.locations.otherLocations["cssSelector"]?.toString()
        
        val userId = supabase.auth.currentSessionOrNull()?.user?.id 
            ?: "pending_user"
            
        // Fetch global identifier for the book
        val globalBookId = booksDao.getIdentifierByBookId(highlight.bookId) 
            ?: highlight.bookId.toString() // Fallback (shouldn't happen if DB consistent)

        return HighlightDto(
            id = highlight.cloudId ?: java.util.UUID.randomUUID().toString(),
            userId = userId,
            localId = highlight.id,
            bookId = globalBookId,
            href = highlight.href,
            cfi = cfi,
            style = highlight.style.value,
            color = highlight.tint,
            textBefore = highlight.text.before,
            textHighlight = highlight.text.highlight,
            textAfter = highlight.text.after,
            annotation = highlight.annotation.ifEmpty { null },
            totalProgression = highlight.totalProgression,
            timestamp = highlight.creation ?: System.currentTimeMillis()
        )
    }

    private fun dtoToHighlight(dto: HighlightDto, bookId: Long): Highlight {
        // We need to reconstruct Locator
        // This is tricky without the full Locator JSON structure. 
        // We have separate fields (href, cfi, text...). 
        // We can build a basic Locator.
        
        val locations = org.readium.r2.shared.publication.Locator.Locations(
            fragments = listOfNotNull(dto.cfi),
            progression = dto.totalProgression,
            otherLocations = emptyMap() // We lost CSS selector if it was there?
        )
        
        val text = org.readium.r2.shared.publication.Locator.Text(
            before = dto.textBefore,
            highlight = dto.textHighlight,
            after = dto.textAfter
        )
        
        val locator = org.readium.r2.shared.publication.Locator(
            href = org.readium.r2.shared.util.Url(dto.href)!!,
            mediaType = org.readium.r2.shared.util.mediatype.MediaType.BINARY, // Default/Unknown
            locations = locations,
            text = text
        )

        return Highlight(
           bookId = bookId,
           style = Highlight.Style.getOrDefault(dto.style),
           tint = dto.color,
           locator = locator,
           annotation = dto.annotation ?: ""
        ).apply {
            creation = dto.timestamp
            cloudId = dto.id
        }
    }
}
