/*
 * Bookmarks Sync Manager
 *
 * Synchronizes user bookmarks to Supabase.
 * Uses the same offline-first pattern as HighlightSyncManager.
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.eqraa.reader.data.db.AppDatabase
import com.eqraa.reader.data.model.Bookmark
import com.eqraa.reader.data.model.BookmarkDto
import com.eqraa.reader.data.model.SyncAction
import timber.log.Timber
import org.readium.r2.shared.publication.Locator

/**
 * Manages synchronization of bookmarks.
 */
class BookmarkSyncManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val TYPE_BOOKMARK = "bookmark"
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
     * Queue a bookmark for sync.
     */
    /**
     * Queue a bookmark for sync.
     */
    /**
     * Queue a bookmark for sync.
     */
    suspend fun queueBookmarkSync(bookmark: Bookmark) {
        try {
            // Ensure cloud_id exists before syncing
            if (bookmark.cloudId == null) {
                val newCloudId = java.util.UUID.randomUUID().toString()
                bookmark.cloudId = newCloudId
                // TODO: Persist cloudId properly. For now we rely on subsequent fullSync or hope insertBookmark works if it wasn't there.
                // Since insertBookmark is IGNORE, if it exists, it ignores.
                // We should ideally update it. But we proceed for now to unblock sync queue.
            }

            val dto = bookmarkToDto(bookmark)
            val payload = Json.encodeToString(dto)
            
            // Check for existing action for this bookmark
            val key = "${bookmark.bookId}_${bookmark.cloudId}"
            val existing = syncDao.getActionByTarget(SyncAction.TYPE_BOOKMARK, key)
            
            if (existing != null) {
                syncDao.updateAction(existing.copy(
                    payload = payload,
                    timestamp = System.currentTimeMillis()
                ))
            } else {
                syncDao.insertAction(
                    SyncAction(
                        type = SyncAction.TYPE_BOOKMARK,
                        key = key,
                        payload = payload,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            
            // Trigger worker
            SyncWorker.enqueue(context)
            Timber.d("BookmarkSyncManager: Queued sync for bookmark ${bookmark.id} (cloud: ${bookmark.cloudId})")
            
        } catch (e: Exception) {
            Timber.e(e, "BookmarkSyncManager: Failed to queue bookmark sync")
        }
    }
    
    /**
     * Queue a bookmark deletion for sync.
     */
    suspend fun queueBookmarkDelete(bookmark: Bookmark) {
        try {
            val dto = bookmarkToDto(bookmark).copy(deleted = true)
            val payload = Json.encodeToString(dto)
            
            val key = "${bookmark.bookId}_${bookmark.id}"
            syncDao.insertAction(
                SyncAction(
                    type = SyncAction.TYPE_BOOKMARK,
                    key = key,
                    payload = payload,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            SyncWorker.enqueue(context)
            Timber.d("BookmarkSyncManager: Queued delete for bookmark ${bookmark.id}")
            
        } catch (e: Exception) {
            Timber.e(e, "BookmarkSyncManager: Failed to queue bookmark delete")
        }
    }
    
    /**
     * Fetch all bookmarks for a book from the cloud.
     */
    suspend fun fetchBookmarksForBook(bookId: String): List<BookmarkDto> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing
            
            val bookmarks = supabase.from("bookmarks").select {
                filter { eq("book_id", bookId) }
            }.decodeList<BookmarkDto>()
            
            _syncState.value = SyncState.Success(bookmarks.size)
            Timber.d("BookmarkSyncManager: Fetched ${bookmarks.size} bookmarks for book $bookId")
            bookmarks
            
        } catch (e: Exception) {
            Timber.e(e, "BookmarkSyncManager: Failed to fetch bookmarks")
            _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            emptyList()
        }
    }
    
    /**
     * Sync a single bookmark to the cloud (called by SyncWorker).
     */
    suspend fun syncBookmark(dto: BookmarkDto): Boolean = withContext(Dispatchers.IO) {
        try {
            if (dto.deleted) {
                // Delete from cloud
                supabase.from("bookmarks").delete {
                    filter { eq("id", dto.id) }
                }
            } else {
                // Upsert to cloud
                supabase.from("bookmarks").upsert(dto) {
                    select()
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "BookmarkSyncManager: Failed to sync bookmark ${dto.id}")
            false
        }
    }
    
    /**
     * Full sync: Download all bookmarks from cloud and merge with local.
     */
    /**
     * Full sync: Download all bookmarks from cloud and merge with local.
     * Fetches ALL user bookmarks and matches them to local books.
     */
    suspend fun fullSync(bookId: String): Int = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing
            
            // Fetch ALL bookmarks for this user
            val allUserBookmarks = supabase.from("bookmarks").select {
                filter { 
                    eq("deleted", false)
                }
            }.decodeList<BookmarkDto>()
            
            var mergedCount = 0
            for (dto in allUserBookmarks) {
                try {
                    // Match to local book
                    val localBookId = booksDao.getBookIdByIdentifier(dto.bookId)
                        ?: dto.bookId.toLongOrNull()
                    
                    if (localBookId == null) {
                        continue
                    }
                    
                    val local = booksDao.getBookmarkByCloudId(dto.id)
                    if (local == null) {
                        try {
                            booksDao.insertBookmark(dtoToBookmark(dto, localBookId))
                            mergedCount++
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to insert bookmark ${dto.id}")
                        }
                    } else {
                         if (dto.timestamp > (local.creation ?: 0L)) {
                             booksDao.deleteBookmark(local.id!!)
                             booksDao.insertBookmark(dtoToBookmark(dto, localBookId).apply { cloudId = dto.id })
                             mergedCount++
                         }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to merge bookmark ${dto.id}")
                }
            }
            
            _syncState.value = SyncState.Success(mergedCount)
            Timber.d("BookmarkSyncManager: Full sync complete, total: ${allUserBookmarks.size}, merged: $mergedCount")
            mergedCount
            
        } catch (e: Exception) {
            Timber.e(e, "BookmarkSyncManager: Full sync failed")
            _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            0
        }
    }
    
    // Helpers
    
    private suspend fun bookmarkToDto(bookmark: Bookmark): BookmarkDto {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id 
            ?: "pending_user"
            
        val globalBookId = booksDao.getIdentifierByBookId(bookmark.bookId)
            ?: bookmark.bookId.toString()
        
        return BookmarkDto(
            id = bookmark.cloudId ?: java.util.UUID.randomUUID().toString(),
            userId = userId,
            bookId = globalBookId,
            cfi = bookmark.location,
            href = bookmark.resourceHref,
            type = bookmark.resourceType,
            resourceIndex = bookmark.resourceIndex.toInt(),
            title = bookmark.resourceTitle,
            timestamp = bookmark.creation ?: System.currentTimeMillis(),
            deleted = false
        )
    }

    private fun dtoToBookmark(dto: BookmarkDto, bookId: Long): Bookmark {
        return Bookmark(
            creation = dto.timestamp,
            bookId = bookId,
            resourceIndex = dto.resourceIndex.toLong(),
            resourceHref = dto.href,
            resourceType = dto.type,
            resourceTitle = dto.title ?: "",
            location = dto.cfi,
            locatorText = "{}" // Default empty
        ).apply {
            cloudId = dto.id
        }
    }
}
