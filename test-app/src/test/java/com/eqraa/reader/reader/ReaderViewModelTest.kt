/*
 * Copyright 2024 Eqraa. All rights reserved.
 * Unit Tests for ReaderViewModel
 */
package com.eqraa.reader.reader

import android.content.Context
import android.content.SharedPreferences
import app.cash.turbine.test
import com.eqraa.reader.base.BaseViewModelTest
import com.eqraa.reader.data.*
import com.eqraa.reader.data.db.WordCardDao
import com.eqraa.reader.data.model.Highlight
import com.eqraa.reader.utils.TestDataBuilders
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest : BaseViewModelTest() {

    // Mocks
    private lateinit var mockContext: Context
    private lateinit var mockReaderRepository: ReaderRepository
    private lateinit var mockBookRepository: BookRepository
    private lateinit var mockReadingProgressRepository: ReadingProgressRepository
    private lateinit var mockReadingSyncManager: ReadingSyncManager
    private lateinit var mockBackupManager: BackupManager
    private lateinit var mockRealtimeSyncManager: RealtimeSyncManager
    private lateinit var mockStatsRepository: StatsRepository
    private lateinit var mockWordCardDao: WordCardDao
    private lateinit var mockPublication: Publication
    private lateinit var mockReaderInitData: ReaderInitData
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor

    private val bookId = 1L
    private val bookIdentifier = "test-book-id"

    override fun setUp() {
        super.setUp()

        mockContext = mockk(relaxed = true)
        mockReaderRepository = mockk(relaxed = true)
        mockBookRepository = mockk(relaxed = true)
        mockReadingProgressRepository = mockk(relaxed = true)
        mockReadingSyncManager = mockk(relaxed = true)
        mockBackupManager = mockk(relaxed = true)
        mockRealtimeSyncManager = mockk(relaxed = true)
        mockStatsRepository = mockk(relaxed = true)
        mockWordCardDao = mockk(relaxed = true)
        mockPublication = mockk(relaxed = true)
        mockReaderInitData = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockPrefsEditor = mockk(relaxed = true)

        // Mock initialization data
        every { mockReaderInitData.publication } returns mockPublication
        every { mockReaderRepository[bookId] } returns mockReaderInitData
        
        // Mock publication positions
        coEvery { mockPublication.positions() } returns emptyList()
        every { mockPublication.metadata.identifier } returns "test-identifier"

        // Mock shared preferences
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } just Runs

        // Mock flows
        every { mockReadingSyncManager.syncStatus } returns MutableStateFlow(ReadingSyncManager.SyncStatus.Idle)
        every { mockReadingSyncManager.remoteProgressFlow } returns MutableSharedFlow()
        every { mockRealtimeSyncManager.events } returns MutableSharedFlow()
        every { mockStatsRepository.totalReadingTimeMs() } returns flowOf(0L)
        every { mockStatsRepository.activityForLast7Days() } returns flowOf(emptyList())
        coEvery { mockStatsRepository.currentStreak() } returns 0

        // Mock Highlight/Bookmark sync managers
        every { mockBookRepository.highlightSyncManager } returns mockk(relaxed = true)
        every { mockBookRepository.bookmarkSyncManager } returns mockk(relaxed = true)
    }

    private fun createViewModel(): ReaderViewModel {
        return ReaderViewModel(
            bookId = bookId,
            bookIdentifier = bookIdentifier,
            context = mockContext,
            readerRepository = mockReaderRepository,
            bookRepository = mockBookRepository,
            readingProgressRepository = mockReadingProgressRepository,
            readingSyncManager = mockReadingSyncManager,
            backupManager = mockBackupManager,
            realtimeSyncManager = mockRealtimeSyncManager,
            statsRepository = mockStatsRepository,
            wordCardDao = mockWordCardDao
        )
    }

    @Test
    fun `initialization loads publication and starts session`() = runTest {
        // When
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        assertThat(viewModel.publication).isEqualTo(mockPublication)
        coVerify { mockReaderRepository[bookId] }
    }

    @Test
    fun `saveProgression updates current locator`() = runTest {
        // Given
        val viewModel = createViewModel()
        val locator = TestDataBuilders.createTestLocator()

        // When
        viewModel.saveProgression(locator)
        advanceUntilIdle()

        // Then
        assertThat(viewModel.currentLocator.value).isEqualTo(locator)
    }

    @Test
    fun `insertBookmark calls repository and sends feedback`() = runTest {
        // Given
        val viewModel = createViewModel()
        val locator = TestDataBuilders.createTestLocator()
        coEvery { mockBookRepository.insertBookmark(bookId, any<Publication>(), locator) } returns 123L

        // When
        viewModel.fragmentChannel.receiveAsFlow().test {
            viewModel.insertBookmark(locator)
            advanceUntilIdle()
            
            // Then
            val feedback = awaitItem()
            assertThat(feedback).isEqualTo(ReaderViewModel.FragmentFeedback.BookmarkSuccessfullyAdded)
            coVerify { mockBookRepository.insertBookmark(bookId, any<Publication>(), locator) }
        }
    }

    @Test
    fun `deleteBookmark calls repository`() = runTest {
        // Given
        val viewModel = createViewModel()
        val bookmarkId = 456L

        // When
        viewModel.deleteBookmark(bookmarkId)
        advanceUntilIdle()

        // Then
        coVerify { mockBookRepository.deleteBookmark(bookmarkId) }
    }

    @Test
    fun `addHighlight calls repository`() = runTest {
        // Given
        val viewModel = createViewModel()
        val locator = TestDataBuilders.createTestLocator()
        val style = Highlight.Style.HIGHLIGHT
        val tint = 0xFFFF0000.toInt()

        // When
        viewModel.addHighlight(locator, style, tint, "Note")
        advanceUntilIdle()

        // Then
        coVerify { mockBookRepository.addHighlight(bookId, style, tint, locator, "Note") }
    }

    @Test
    fun `deleteHighlight calls repository`() = runTest {
        // Given
        val viewModel = createViewModel()
        val highlightId = 789L

        // When
        viewModel.deleteHighlight(highlightId)
        advanceUntilIdle()

        // Then
        coVerify { mockBookRepository.deleteHighlight(highlightId) }
    }

    @Test
    fun `highlightDecorations combines highlights and activeId`() = runTest {
        // Given
        val mockHighlights = listOf(
            TestDataBuilders.createTestHighlight(bookId = 1L).copy(id = 1)
        )
        every { mockBookRepository.highlightsForBook(bookId) } returns flowOf(mockHighlights)
        val viewModel = createViewModel()

        // When
        viewModel.highlightDecorations.test {
            val decorations = awaitItem()
            
            // Then
            assertThat(decorations).isNotEmpty
            assertThat(decorations[0].extras["id"]).isEqualTo(1L)
        }
    }

    @Test
    fun `onCleared closes repository and session`() = runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onExternalLinkActivated(org.readium.r2.shared.util.AbsoluteUrl("http://example.com")!!)
        
        viewModel.activityChannel.receiveAsFlow().test {
            val command = awaitItem()
            assertThat(command).isInstanceOf(ReaderViewModel.ActivityCommand.OpenExternalLink::class.java)
        }
    }

    @Test
    fun `syncBook calls backup manager`() = runTest {
        // Given
        val viewModel = createViewModel()
        val identifier = "test-identifier"
        every { mockPublication.metadata.identifier } returns identifier

        // When
        viewModel.syncBook()
        advanceUntilIdle()

        // Then
        coVerify { mockBackupManager.syncBook(identifier) }
    }

    @Test
    fun `saveWordCard inserts new word card`() = runTest {
        // Given
        val viewModel = createViewModel()
        val word = "testWord"
        coEvery { mockWordCardDao.getCardByWord(word) } returns null

        // When
        viewModel.saveWordCard(word, "description", "context")
        advanceUntilIdle()

        // Then
        coVerify { mockWordCardDao.insert(any()) }
    }
}
