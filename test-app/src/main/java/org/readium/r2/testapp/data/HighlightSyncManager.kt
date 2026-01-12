/*
 * Highlights Sync Manager
 *
 * Synchronizes user highlights and annotations to Supabase.
 * Uses the same offline-first pattern as ReadingProgressSyncManager.
 */

package org.readium.r2.testapp.data

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
import org.readium.r2.testapp.data.db.AppDatabase
import org.readium.r2.testapp.data.model.Highlight
import org.readium.r2.testapp.data.model.SyncAction
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
            val dto = highlightToDto(highlight)
            val payload = Json.encodeToString(dto)
            
            // Check for existing action for this highlight
            val key = "${highlight.bookId}_${highlight.id}"
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
            Timber.d("HighlightSyncManager: Queued sync for highlight ${highlight.id}")
            
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
    suspend fun fullSync(bookId: String): Int = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing
            
            val cloudHighlights = supabase.from("highlights").select {
                filter { 
                    eq("book_id", bookId)
                    eq("deleted", false)
                }
            }.decodeList<HighlightDto>()
            
            // TODO: Merge with local database
            // For now, just log the count
            
            _syncState.value = SyncState.Success(cloudHighlights.size)
            Timber.d("HighlightSyncManager: Full sync complete, ${cloudHighlights.size} highlights")
            cloudHighlights.size
            
        } catch (e: Exception) {
            Timber.e(e, "HighlightSyncManager: Full sync failed")
            _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            0
        }
    }
    
    private fun highlightToDto(highlight: Highlight): HighlightDto {
        val cfi = highlight.locations.fragments.firstOrNull()
            ?: highlight.locations.otherLocations["cssSelector"]?.toString()
        
        val userId = supabase.auth.currentSessionOrNull()?.user?.id 
            ?: throw IllegalStateException("User must be logged in to sync highlights")
            
        return HighlightDto(
            id = java.util.UUID.randomUUID().toString(), // Generate cloud ID
            userId = userId,
            localId = highlight.id,
            bookId = highlight.bookId.toString(),
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
}
