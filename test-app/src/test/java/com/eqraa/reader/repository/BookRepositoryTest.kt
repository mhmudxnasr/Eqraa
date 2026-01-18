/*
 * Copyright 2024 Eqraa. All rights reserved.
 * Unit Tests for BookRepository
 */
package com.eqraa.reader.repository

import app.cash.turbine.test
import com.eqraa.reader.base.BaseViewModelTest
import com.eqraa.reader.data.BackupManager
import com.eqraa.reader.data.BookRepository
import com.eqraa.reader.data.BookmarkSyncManager
import com.eqraa.reader.data.HighlightSyncManager
import com.eqraa.reader.data.db.BooksDao
import com.eqraa.reader.data.model.Highlight
import com.eqraa.reader.utils.TestDataBuilders
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.readium.r2.shared.publication.Publication

/**
 * Unit tests for [BookRepository].
 * 
 * Uses Robolectric because test data builders use Readium's Url class
 * which depends on Android's Uri class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class BookRepositoryTest : BaseViewModelTest() {

    // Mocks
    private lateinit var mockBooksDao: BooksDao
    private lateinit var mockBackupManager: BackupManager
    private lateinit var mockHighlightSyncManager: HighlightSyncManager
    private lateinit var mockBookmarkSyncManager: BookmarkSyncManager

    // Test subject
    private lateinit var repository: BookRepository

    override fun setUp() {
        super.setUp()
        
        mockBooksDao = mockk(relaxed = true)
        mockBackupManager = mockk(relaxed = true)
        mockHighlightSyncManager = mockk(relaxed = true)
        mockBookmarkSyncManager = mockk(relaxed = true)

        repository = BookRepository(mockBooksDao).apply {
            backupManager = mockBackupManager
            highlightSyncManager = mockHighlightSyncManager
            bookmarkSyncManager = mockBookmarkSyncManager
        }
    }

    // region Book CRUD Tests

    @Test
    fun `books returns flow from DAO`() = runTest {
        // Given
        val testBooks = TestDataBuilders.createTestBookList(3)
        every { mockBooksDao.getAllBooks() } returns flowOf(testBooks)

        // When/Then
        repository.books().test {
            val books = awaitItem()
            assertThat(books).hasSize(3)
            assertThat(books[0].title).isEqualTo("Test Book 1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `get returns book from DAO by id`() = runTest {
        // Given
        val testBook = TestDataBuilders.createTestBook(id = 1L)
        coEvery { mockBooksDao.get(1L) } returns testBook

        // When
        val result = repository.get(1L)

        // Then
        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo(1L)
        assertThat(result?.title).isEqualTo("Test Book Title")
    }

    @Test
    fun `get returns null when book not found`() = runTest {
        // Given
        coEvery { mockBooksDao.get(999L) } returns null

        // When
        val result = repository.get(999L)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `deleteBook removes book and triggers backup`() = runTest {
        // Given
        coEvery { mockBooksDao.deleteBook(1L) } just Runs
        coEvery { mockBackupManager.performFullBackup() } just Runs

        // When
        repository.deleteBook(1L)

        // Then
        coVerify { mockBooksDao.deleteBook(1L) }
        coVerify { mockBackupManager.performFullBackup() }
    }

    // endregion

    // region Progress Tests

    @Test
    fun `saveProgression updates book with timestamp`() = runTest {
        // Given
        val locator = TestDataBuilders.createTestLocator()
        val bookId = 1L
        coEvery { mockBooksDao.saveProgression(any(), any(), eq(bookId)) } just Runs

        // When
        repository.saveProgression(locator, bookId)

        // Then
        coVerify { 
            mockBooksDao.saveProgression(
                match { it.contains("position") },
                any(),
                eq(bookId)
            ) 
        }
    }

    // endregion

    // region Bookmark Tests

    @Test
    fun `insertBookmark creates bookmark and queues sync`() = runTest {
        // Given
        val bookId = 1L
        val locator = TestDataBuilders.createTestLocator(href = "chapter1.xhtml")
        val mockPublication = mockk<Publication>(relaxed = true)
        
        // Use a real Link object instead of a mock
        val link = org.readium.r2.shared.publication.Link(
            href = org.readium.r2.shared.util.Url("chapter1.xhtml")!!
        )
        every { mockPublication.readingOrder } returns listOf(link)
        
        coEvery { mockBooksDao.insertBookmark(any()) } returns 1L
        val insertedBookmark = TestDataBuilders.createTestBookmark(id = 1L)
        coEvery { mockBooksDao.getBookmarkById(1L) } returns insertedBookmark
        coEvery { mockBookmarkSyncManager.queueBookmarkSync(any()) } just Runs

        // When
        val result = repository.insertBookmark(bookId, mockPublication, locator)

        // Then
        assertThat(result).isEqualTo(1L)
        coVerify { mockBooksDao.insertBookmark(any()) }
        coVerify { mockBookmarkSyncManager.queueBookmarkSync(insertedBookmark) }
    }

    @Test
    fun `bookmarksForBook returns flow from DAO`() = runTest {
        // Given
        val testBookmarks = listOf(
            TestDataBuilders.createTestBookmark(id = 1L),
            TestDataBuilders.createTestBookmark(id = 2L)
        )
        every { mockBooksDao.getBookmarksForBook(1L) } returns flowOf(testBookmarks)

        // When/Then
        repository.bookmarksForBook(1L).test {
            val bookmarks = awaitItem()
            assertThat(bookmarks).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteBookmark syncs deletion then removes locally`() = runTest {
        // Given
        val bookmark = TestDataBuilders.createTestBookmark(id = 1L)
        coEvery { mockBooksDao.getBookmarkById(1L) } returns bookmark
        coEvery { mockBookmarkSyncManager.queueBookmarkDelete(bookmark) } just Runs
        coEvery { mockBooksDao.deleteBookmark(1L) } just Runs

        // When
        repository.deleteBookmark(1L)

        // Then
        coVerifyOrder {
            mockBooksDao.getBookmarkById(1L)
            mockBookmarkSyncManager.queueBookmarkDelete(bookmark)
            mockBooksDao.deleteBookmark(1L)
        }
    }

    // endregion

    // region Highlight Tests

    @Test
    fun `addHighlight inserts and queues sync`() = runTest {
        // Given
        val bookId = 1L
        val locator = TestDataBuilders.createTestLocator()
        val style = Highlight.Style.HIGHLIGHT
        val tint = 0xFFFFFF00.toInt()
        val annotation = "Test note"

        coEvery { mockBooksDao.insertHighlight(any()) } returns 1L
        val insertedHighlight = TestDataBuilders.createTestHighlight(bookId = 1L)
        coEvery { mockBooksDao.getHighlightById(1L) } returns insertedHighlight
        coEvery { mockHighlightSyncManager.queueHighlightSync(any()) } just Runs

        // When
        val result = repository.addHighlight(bookId, style, tint, locator, annotation)

        // Then
        assertThat(result).isEqualTo(1L)
        coVerify { mockBooksDao.insertHighlight(any()) }
        coVerify { mockHighlightSyncManager.queueHighlightSync(insertedHighlight) }
    }

    @Test
    fun `highlightsForBook returns flow from DAO`() = runTest {
        // Given
        val testHighlights = TestDataBuilders.createTestHighlightList(bookId = 1L, count = 3)
        every { mockBooksDao.getHighlightsForBook(1L) } returns flowOf(testHighlights)

        // When/Then
        repository.highlightsForBook(1L).test {
            val highlights = awaitItem()
            assertThat(highlights).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `highlightById returns highlight from DAO`() = runTest {
        // Given
        val testHighlight = TestDataBuilders.createTestHighlight(bookId = 1L)
        coEvery { mockBooksDao.getHighlightById(1L) } returns testHighlight

        // When
        val result = repository.highlightById(1L)

        // Then
        assertThat(result).isNotNull
    }

    @Test
    fun `deleteHighlight syncs deletion then removes locally`() = runTest {
        // Given
        val highlight = TestDataBuilders.createTestHighlight(bookId = 1L)
        coEvery { mockBooksDao.getHighlightById(1L) } returns highlight
        coEvery { mockHighlightSyncManager.queueHighlightDelete(highlight) } just Runs
        coEvery { mockBooksDao.deleteHighlight(1L) } just Runs

        // When
        repository.deleteHighlight(1L)

        // Then
        coVerifyOrder {
            mockBooksDao.getHighlightById(1L)
            mockHighlightSyncManager.queueHighlightDelete(highlight)
            mockBooksDao.deleteHighlight(1L)
        }
    }

    @Test
    fun `updateHighlightAnnotation updates and syncs`() = runTest {
        // Given
        val highlight = TestDataBuilders.createTestHighlight(bookId = 1L)
        coEvery { mockBooksDao.updateHighlightAnnotation(1L, "New note") } just Runs
        coEvery { mockBooksDao.getHighlightById(1L) } returns highlight
        coEvery { mockHighlightSyncManager.queueHighlightSync(any()) } just Runs

        // When
        repository.updateHighlightAnnotation(1L, "New note")

        // Then
        coVerify { mockBooksDao.updateHighlightAnnotation(1L, "New note") }
        coVerify { mockHighlightSyncManager.queueHighlightSync(highlight) }
    }

    @Test
    fun `updateHighlightStyle updates and syncs`() = runTest {
        // Given
        val highlight = TestDataBuilders.createTestHighlight(bookId = 1L)
        val newStyle = Highlight.Style.UNDERLINE
        val newTint = 0xFF00FF00.toInt()

        coEvery { mockBooksDao.updateHighlightStyle(1L, newStyle, newTint) } just Runs
        coEvery { mockBooksDao.getHighlightById(1L) } returns highlight
        coEvery { mockHighlightSyncManager.queueHighlightSync(any()) } just Runs

        // When
        repository.updateHighlightStyle(1L, newStyle, newTint)

        // Then
        coVerify { mockBooksDao.updateHighlightStyle(1L, newStyle, newTint) }
        coVerify { mockHighlightSyncManager.queueHighlightSync(highlight) }
    }

    // endregion
}
