/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.data

import androidx.annotation.ColorInt
import java.io.File
import kotlinx.coroutines.flow.Flow
import org.joda.time.DateTime
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.testapp.data.db.BooksDao
import org.readium.r2.testapp.data.model.Book
import org.readium.r2.testapp.data.model.Bookmark
import org.readium.r2.testapp.data.model.Highlight
import org.readium.r2.testapp.utils.extensions.readium.authorName

class BookRepository(
    private val booksDao: BooksDao,
) {
    var backupManager: BackupManager? = null
    var highlightSyncManager: HighlightSyncManager? = null

    fun books(): Flow<List<Book>> = booksDao.getAllBooks()

    suspend fun get(id: Long) = booksDao.get(id)

    suspend fun saveProgression(locator: Locator, bookId: Long) {
        booksDao.saveProgression(locator.toJSON().toString(), bookId)
        // Progression sync allowed by ReadingProgressSyncManager, skip full backup
    }

    suspend fun insertBookmark(bookId: Long, publication: Publication, locator: Locator): Long {
        val resource = publication.readingOrder.indexOfFirstWithHref(locator.href)!!
        val bookmark = Bookmark(
            creation = DateTime().toDate().time,
            bookId = bookId,
            resourceIndex = resource.toLong(),
            resourceHref = locator.href.toString(),
            resourceType = locator.mediaType.toString(),
            resourceTitle = locator.title.orEmpty(),
            location = locator.locations.toJSON().toString(),
            locatorText = Locator.Text().toJSON().toString()
        )

        return booksDao.insertBookmark(bookmark)
    }

    fun bookmarksForBook(bookId: Long): Flow<List<Bookmark>> =
        booksDao.getBookmarksForBook(bookId)

    suspend fun deleteBookmark(bookmarkId: Long) = booksDao.deleteBookmark(bookmarkId)

    suspend fun highlightById(id: Long): Highlight? =
        booksDao.getHighlightById(id)

    fun highlightsForBook(bookId: Long): Flow<List<Highlight>> =
        booksDao.getHighlightsForBook(bookId)

    suspend fun addHighlight(
        bookId: Long,
        style: Highlight.Style,
        @ColorInt tint: Int,
        locator: Locator,
        annotation: String,
    ): Long {
        val highlightId = booksDao.insertHighlight(Highlight(bookId, style, tint, locator, annotation))
        
        // Sync highlight to cloud
        val highlight = booksDao.getHighlightById(highlightId)
        highlight?.let {
            highlightSyncManager?.queueHighlightSync(it)
        }
        
        return highlightId
    }

    suspend fun deleteHighlight(id: Long) {
        // Sync deletion to cloud before deleting locally
        val highlight = booksDao.getHighlightById(id)
        highlight?.let {
            highlightSyncManager?.queueHighlightDelete(it)
        }
        
        booksDao.deleteHighlight(id)
    }

    suspend fun updateHighlightAnnotation(id: Long, annotation: String) {
        booksDao.updateHighlightAnnotation(id, annotation)
        
        // Sync updated highlight to cloud
        val highlight = booksDao.getHighlightById(id)
        highlight?.let {
            highlightSyncManager?.queueHighlightSync(it)
        }
    }

    suspend fun updateHighlightStyle(id: Long, style: Highlight.Style, @ColorInt tint: Int) {
        booksDao.updateHighlightStyle(id, style, tint)
        
        // Sync updated highlight to cloud
        val highlight = booksDao.getHighlightById(id)
        highlight?.let {
            highlightSyncManager?.queueHighlightSync(it)
        }
    }

    suspend fun insertBook(
        url: Url,
        mediaType: MediaType,
        publication: Publication,
        cover: File,
    ): Long {
        val book = Book(
            creation = DateTime().toDate().time,
            title = publication.metadata.title ?: url.filename,
            author = publication.metadata.authorName,
            href = url.toString(),
            identifier = publication.metadata.identifier ?: "",
            mediaType = mediaType,
            progression = "{}",
            cover = cover.path
        )
        val bookId = booksDao.insertBook(book)
        // Trigger library backup for new book
        backupManager?.performFullBackup()
        return bookId
    }

    suspend fun deleteBook(id: Long) {
        booksDao.deleteBook(id)
        // Trigger library backup after deletion
        backupManager?.performFullBackup()
    }
}

