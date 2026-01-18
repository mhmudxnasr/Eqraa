/*
 * Copyright 2024 Eqraa. All rights reserved.
 * Unit Tests for BookshelfViewModel
 */
package com.eqraa.reader.viewmodel

import android.app.Application
import app.cash.turbine.test
import com.eqraa.reader.Application as EqraaApp
import com.eqraa.reader.base.BaseViewModelTest
import com.eqraa.reader.bookshelf.BookshelfViewModel
import com.eqraa.reader.data.BookRepository
import com.eqraa.reader.domain.Bookshelf
import com.eqraa.reader.data.StatsRepository
import com.eqraa.reader.data.model.Book
import com.eqraa.reader.reader.ReaderRepository
import com.eqraa.reader.utils.TestDataBuilders
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Unit tests for [BookshelfViewModel].
 * 
 * Tests cover:
 * - Book list observation
 * - Reading statistics flows
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfViewModelTest : BaseViewModelTest() {

    // Mocks
    private lateinit var mockApplication: EqraaApp
    private lateinit var mockBookRepository: BookRepository
    private lateinit var mockBookshelf: Bookshelf
    private lateinit var mockReaderRepository: ReaderRepository
    private lateinit var mockStatsRepository: StatsRepository

    // Test subject
    private lateinit var viewModel: BookshelfViewModel

    override fun setUp() {
        super.setUp()
        
        // Initialize mocks
        mockApplication = mockk(relaxed = true)
        mockBookRepository = mockk(relaxed = true)
        mockBookshelf = mockk(relaxed = true)
        mockReaderRepository = mockk(relaxed = true)
        mockStatsRepository = mockk(relaxed = true)

        // Configure mock application
        every { mockApplication.bookRepository } returns mockBookRepository
        every { mockApplication.bookshelf } returns mockBookshelf
        every { mockApplication.readerRepository } returns mockReaderRepository
        every { mockApplication.statsRepository } returns mockStatsRepository

        // Default mock behaviors
        every { mockBookRepository.books() } returns flowOf(emptyList())
        every { mockStatsRepository.activityForLast7Days() } returns flowOf(listOf(0L))
        coEvery { mockStatsRepository.currentStreak() } returns 0
        every { mockStatsRepository.readingTimeThisWeekMs() } returns flowOf(0L)
        every { mockStatsRepository.totalReadingTimeMs() } returns flowOf(0L)
    }

    private fun createViewModel(): BookshelfViewModel {
        return BookshelfViewModel(mockApplication)
    }

    // region Book List Tests

    @Test
    fun `books flow emits list from repository`() = runTest {
        // Given
        val testBooks = TestDataBuilders.createTestBookList(3)
        every { mockBookRepository.books() } returns flowOf(testBooks)

        // When
        viewModel = createViewModel()

        // Then
        viewModel.books.test {
            val emittedBooks = awaitItem()
            assertThat(emittedBooks).hasSize(3)
            assertThat(emittedBooks[0].title).isEqualTo("Test Book 1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `books flow emits empty list when no books`() = runTest {
        // Given
        every { mockBookRepository.books() } returns flowOf(emptyList())

        // When
        viewModel = createViewModel()

        // Then
        viewModel.books.test {
            val emittedBooks = awaitItem()
            assertThat(emittedBooks).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Stats Flow Tests

    @Test
    fun `todayReadingTimeMs emits from stats repository`() = runTest {
        // Given
        every { mockStatsRepository.activityForLast7Days() } returns flowOf(listOf(5000L))
        viewModel = createViewModel()

        // When/Then
        viewModel.todayReadingTimeMs.test {
            val time = awaitItem()
            assertThat(time).isEqualTo(5000L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `weeklyReadingTimeMs emits from stats repository`() = runTest {
        // Given
        every { mockStatsRepository.readingTimeThisWeekMs() } returns flowOf(3600000L)
        viewModel = createViewModel()

        // When/Then
        viewModel.weeklyReadingTimeMs.test {
            val time = awaitItem()
            assertThat(time).isEqualTo(3600000L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentStreak emits from stats repository`() = runTest {
        // Given
        coEvery { mockStatsRepository.currentStreak() } returns 5
        viewModel = createViewModel()

        // When/Then
        viewModel.currentStreak.test {
            val streak = awaitItem()
            assertThat(streak).isEqualTo(5)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totalReadingTimeMs emits from stats repository`() = runTest {
        // Given
        every { mockStatsRepository.totalReadingTimeMs() } returns flowOf(7200000L)
        viewModel = createViewModel()

        // When/Then
        viewModel.totalReadingTimeMs.test {
            val time = awaitItem()
            assertThat(time).isEqualTo(7200000L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion
}
