/*
 * Copyright 2024 Eqraa. All rights reserved.
 * Android Instrumentation Test - Room Database Tests
 */
package com.eqraa.reader.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eqraa.reader.data.db.AppDatabase
import com.eqraa.reader.data.db.BooksDao
import com.eqraa.reader.data.model.Book
import com.eqraa.reader.data.model.Bookmark
import com.eqraa.reader.data.model.Highlight
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Instrumented tests for Room database operations.
 * 
 * These tests run on an Android device or emulator to verify
 * actual SQLite database behavior.
 */
@RunWith(AndroidJUnit4::class)
class BooksDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var booksDao: BooksDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        booksDao = database.booksDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // region Book CRUD Tests

    @Test
    fun insertBook_returnsId() = runTest {
        // Given
        val book = createTestBook()

        // When
        val id = booksDao.insertBook(book)

        // Then
        assertThat(id).isGreaterThan(0)
    }

    @Test
    fun getAllBooks_returnsInsertedBooks() = runTest {
        // Given
        booksDao.insertBook(createTestBook(title = "Book 1"))
        booksDao.insertBook(createTestBook(title = "Book 2"))

        // When
        val books = booksDao.getAllBooks().first()

        // Then
        assertThat(books).hasSize(2)
        assertThat(books.map { it.title }).containsExactlyInAnyOrder("Book 1", "Book 2")
    }

    @Test
    fun getBook_returnsCorrectBook() = runTest {
        // Given
        val id = booksDao.insertBook(createTestBook(title = "Specific Book"))

        // When
        val book = booksDao.get(id)

        // Then
        assertThat(book).isNotNull
        assertThat(book?.title).isEqualTo("Specific Book")
    }

    @Test
    fun deleteBook_removesBook() = runTest {
        // Given
        val id = booksDao.insertBook(createTestBook())
        assertThat(booksDao.get(id)).isNotNull

        // When
        booksDao.deleteBook(id)

        // Then
        assertThat(booksDao.get(id)).isNull()
    }

    // endregion

    // region Progress Tests

    @Test
    fun saveProgression_updatesBook() = runTest {
        // Given
        val id = booksDao.insertBook(createTestBook(progression = "{}"))
        val newProgression = """{"position":5}"""
        val timestamp = System.currentTimeMillis()

        // When
        booksDao.saveProgression(newProgression, timestamp, id)

        // Then
        val book = booksDao.get(id)
        assertThat(book?.progression).isEqualTo(newProgression)
    }

    // endregion

    // region Bookmark Tests

    @Test
    fun insertBookmark_returnsId() = runTest {
        // Given
        val bookId = booksDao.insertBook(createTestBook())
        val bookmark = createTestBookmark(bookId = bookId)

        // When
        val id = booksDao.insertBookmark(bookmark)

        // Then
        assertThat(id).isGreaterThan(0)
    }

    @Test
    fun getBookmarksForBook_returnsOnlyBookBookmarks() = runTest {
        // Given
        val bookId1 = booksDao.insertBook(createTestBook(title = "Book 1"))
        val bookId2 = booksDao.insertBook(createTestBook(title = "Book 2"))
        booksDao.insertBookmark(createTestBookmark(bookId = bookId1))
        booksDao.insertBookmark(createTestBookmark(bookId = bookId1))
        booksDao.insertBookmark(createTestBookmark(bookId = bookId2))

        // When
        val bookmarks = booksDao.getBookmarksForBook(bookId1).first()

        // Then
        assertThat(bookmarks).hasSize(2)
        assertThat(bookmarks.all { it.bookId == bookId1 }).isTrue()
    }

    @Test
    fun deleteBookmark_removesBookmark() = runTest {
        // Given
        val bookId = booksDao.insertBook(createTestBook())
        val bookmarkId = booksDao.insertBookmark(createTestBookmark(bookId = bookId))
        assertThat(booksDao.getBookmarkById(bookmarkId)).isNotNull

        // When
        booksDao.deleteBookmark(bookmarkId)

        // Then
        assertThat(booksDao.getBookmarkById(bookmarkId)).isNull()
    }

    // endregion

    // region Highlight Tests

    @Test
    fun insertHighlight_returnsId() = runTest {
        // Given
        val bookId = booksDao.insertBook(createTestBook())
        val highlight = createTestHighlight(bookId = bookId)

        // When
        val id = booksDao.insertHighlight(highlight)

        // Then
        assertThat(id).isGreaterThan(0)
    }

    @Test
    fun getHighlightsForBook_returnsOnlyBookHighlights() = runTest {
        // Given
        val bookId1 = booksDao.insertBook(createTestBook(title = "Book 1"))
        val bookId2 = booksDao.insertBook(createTestBook(title = "Book 2"))
        booksDao.insertHighlight(createTestHighlight(bookId = bookId1))
        booksDao.insertHighlight(createTestHighlight(bookId = bookId2))
        booksDao.insertHighlight(createTestHighlight(bookId = bookId2))

        // When
        val highlights = booksDao.getHighlightsForBook(bookId2).first()

        // Then
        assertThat(highlights).hasSize(2)
    }

    @Test
    fun updateHighlightAnnotation_updatesAnnotation() = runTest {
        // Given
        val bookId = booksDao.insertBook(createTestBook())
        val highlightId = booksDao.insertHighlight(createTestHighlight(bookId = bookId, annotation = "Old note"))

        // When
        booksDao.updateHighlightAnnotation(highlightId, "New note")

        // Then
        val highlight = booksDao.getHighlightById(highlightId)
        assertThat(highlight?.annotation).isEqualTo("New note")
    }

    // endregion

    // region Helper Methods

    private fun createTestBook(
        title: String = "Test Book",
        progression: String? = "{}"
    ) = Book(
        creation = System.currentTimeMillis(),
        title = title,
        author = "Test Author",
        href = "/test/book.epub",
        identifier = "urn:isbn:${System.currentTimeMillis()}",
        mediaType = MediaType.EPUB,
        progression = progression,
        cover = "/test/cover.jpg"
    )

    private fun createTestBookmark(bookId: Long) = Bookmark(
        creation = System.currentTimeMillis(),
        bookId = bookId,
        resourceIndex = 0L,
        resourceHref = "chapter1.xhtml",
        resourceType = "application/xhtml+xml",
        resourceTitle = "Chapter 1",
        location = "{}",
        locatorText = "{}"
    )

    private fun createTestHighlight(
        bookId: Long,
        annotation: String = ""
    ): Highlight {
        val locator = org.readium.r2.shared.publication.Locator(
            href = org.readium.r2.shared.util.Url("chapter1.xhtml")!!,
            mediaType = MediaType("application/xhtml+xml")!!
        )
        return Highlight(
            bookId = bookId,
            style = Highlight.Style.HIGHLIGHT,
            tint = 0xFFFFFF00.toInt(),
            locator = locator,
            annotation = annotation
        )
    }

    // endregion
}
