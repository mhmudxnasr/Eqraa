/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.data.db

import androidx.annotation.ColorInt
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.eqraa.reader.data.model.Book
import com.eqraa.reader.data.model.Bookmark
import com.eqraa.reader.data.model.Highlight

@Dao
interface BooksDao {

    /**
     * Inserts a book
     * @param book The book to insert
     * @return ID of the book that was added (primary key)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    /**
     * Deletes a book
     * @param bookId The ID of the book
     */
    @Query("DELETE FROM " + Book.TABLE_NAME + " WHERE " + Book.ID + " = :bookId")
    suspend fun deleteBook(bookId: Long)

    /**
     * Retrieve a book from its ID.
     */
    @Query("SELECT * FROM " + Book.TABLE_NAME + " WHERE " + Book.ID + " = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT identifier FROM books WHERE id = :id")
    suspend fun getIdentifierByBookId(id: Long): String?

    @Query("SELECT id FROM books WHERE identifier = :identifier")
    suspend fun getBookIdByIdentifier(identifier: String): Long?

    /**
     * Retrieve all books
     * @return List of books as Flow
     */
    @Query("SELECT * FROM " + Book.TABLE_NAME + " ORDER BY " + Book.CREATION_DATE + " desc")
    fun getAllBooks(): Flow<List<Book>>

    /**
     * Retrieve all bookmarks for a specific book
     * @param bookId The ID of the book
     * @return List of bookmarks for the book as Flow
     */
    @Query("SELECT * FROM " + Bookmark.TABLE_NAME + " WHERE " + Bookmark.BOOK_ID + " = :bookId")
    fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>>

    /**
     * Retrieve all highlights for a specific book
     */
    @Query(
        "SELECT * FROM ${Highlight.TABLE_NAME} WHERE ${Highlight.BOOK_ID} = :bookId ORDER BY ${Highlight.TOTAL_PROGRESSION} ASC"
    )
    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>>

    /**
     * Retrieves the highlight with the given ID.
     */
    @Query("SELECT * FROM ${Highlight.TABLE_NAME} WHERE ${Highlight.ID} = :highlightId")
    suspend fun getHighlightById(highlightId: Long): Highlight?

    /**
     * Retrieves the highlight by Cloud ID.
     */
    @Query("SELECT * FROM ${Highlight.TABLE_NAME} WHERE CLOUD_ID = :cloudId")
    suspend fun getHighlightByCloudId(cloudId: String): Highlight?

    /**
     * Inserts a bookmark
     * @param bookmark The bookmark to insert
     * @return The ID of the bookmark that was added (primary key)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    /**
     * Inserts a highlight
     * @param highlight The highlight to insert
     * @return The ID of the highlight that was added (primary key)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: Highlight): Long

    /**
     * Updates a highlight's annotation.
     */
    @Query(
        "UPDATE ${Highlight.TABLE_NAME} SET ${Highlight.ANNOTATION} = :annotation WHERE ${Highlight.ID} = :id"
    )
    suspend fun updateHighlightAnnotation(id: Long, annotation: String)

    /**
     * Updates a highlight's tint and style.
     */
    @Query(
        "UPDATE ${Highlight.TABLE_NAME} SET ${Highlight.TINT} = :tint, ${Highlight.STYLE} = :style WHERE ${Highlight.ID} = :id"
    )
    suspend fun updateHighlightStyle(id: Long, style: Highlight.Style, @ColorInt tint: Int)

    /**
     * Deletes a bookmark
     */
    @Query("DELETE FROM ${Bookmark.TABLE_NAME} WHERE ${Bookmark.ID} = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("SELECT * FROM ${Bookmark.TABLE_NAME} WHERE ${Bookmark.ID} = :id")
    suspend fun getBookmarkById(id: Long): Bookmark?

    /**
     * Retrieves the bookmark by Cloud ID.
     */
    @Query("SELECT * FROM ${Bookmark.TABLE_NAME} WHERE CLOUD_ID = :cloudId")
    suspend fun getBookmarkByCloudId(cloudId: String): Bookmark?

    /**
     * Deletes the highlight with given id.
     */
    @Query("DELETE FROM ${Highlight.TABLE_NAME} WHERE ${Highlight.ID} = :id")
    suspend fun deleteHighlight(id: Long)

    /**
     * Saves book progression with timestamp
     * @param locator Location of the book
     * @param timestamp When this progress was made (milliseconds)
     * @param id The book to update
     */
    @Query(
        "UPDATE " + Book.TABLE_NAME + " SET " + Book.PROGRESSION + " = :locator, " + Book.UPDATED_AT + " = :timestamp WHERE " + Book.ID + "= :id"
    )
    suspend fun saveProgression(locator: String, timestamp: Long, id: Long)

    /**
     * Gets the last progression timestamp for a book
     */
    @Query("SELECT " + Book.UPDATED_AT + " FROM " + Book.TABLE_NAME + " WHERE " + Book.ID + " = :id")
    suspend fun getProgressionTimestamp(id: Long): Long?

    @Query("UPDATE books SET is_synced = :isSynced WHERE id = :id")
    suspend fun updateBookSyncStatus(id: Long, isSynced: Boolean)

    @Query("SELECT * FROM " + Book.TABLE_NAME + " WHERE " + Book.IDENTIFIER + " = :identifier")
    suspend fun getBookByIdentifier(identifier: String): Book?
}
