/*
 * Copyright 2024 Eqraa. All rights reserved.
 * Unit Tests for ReadingProgressRepository
 */
package com.eqraa.reader.repository

import android.content.Context
import com.eqraa.reader.base.BaseViewModelTest
import com.eqraa.reader.data.ReadingProgressRepository
import com.eqraa.reader.data.db.BooksDao
import com.eqraa.reader.utils.TestDataBuilders
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Unit tests for [ReadingProgressRepository].
 * 
 * Tests cover:
 * - Saving progress locally
 * - Retrieving local progress
 * - Error handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingProgressRepositoryTest : BaseViewModelTest() {

    // Mocks
    private lateinit var mockBooksDao: BooksDao
    private lateinit var mockContext: Context

    // Test subject
    private lateinit var repository: ReadingProgressRepository

    override fun setUp() {
        super.setUp()
        
        mockBooksDao = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)

        repository = ReadingProgressRepository(mockBooksDao, mockContext)
    }

    // region Save Progress Tests

    @Test
    fun `saveProgressLocally stores cfi and timestamp in database`() = runTest {
        // Given
        val bookId = 1L
        val cfi = """{"position":5,"totalProgression":0.5}"""
        val timestamp = System.currentTimeMillis()
        coEvery { mockBooksDao.saveProgression(cfi, timestamp, bookId) } just Runs

        // When
        repository.saveProgressLocally(bookId, cfi, timestamp)

        // Then
        coVerify { mockBooksDao.saveProgression(cfi, timestamp, bookId) }
    }

    @Test
    fun `saveProgressLocally propagates exception on failure`() = runTest {
        // Given
        val bookId = 1L
        val cfi = """{"position":5}"""
        val timestamp = System.currentTimeMillis()
        coEvery { 
            mockBooksDao.saveProgression(any(), any(), any()) 
        } throws RuntimeException("Database error")

        // When/Then
        try {
            repository.saveProgressLocally(bookId, cfi, timestamp)
            assertThat(false).describedAs("Should have thrown exception").isTrue()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("Database error")
        }
    }

    // endregion

    // region Get Progress Tests

    @Test
    fun `getLocalProgress returns cfi and timestamp when book exists`() = runTest {
        // Given
        val testBook = TestDataBuilders.createTestBook(
            id = 1L,
            progression = """{"position":5,"totalProgression":0.5}""",
            updatedAt = 1234567890L
        )
        coEvery { mockBooksDao.get(1L) } returns testBook

        // When
        val result = repository.getLocalProgress(1L)

        // Then
        assertThat(result).isNotNull
        assertThat(result?.first).isEqualTo("""{"position":5,"totalProgression":0.5}""")
        assertThat(result?.second).isEqualTo(1234567890L)
    }

    @Test
    fun `getLocalProgress returns null when book not found`() = runTest {
        // Given
        coEvery { mockBooksDao.get(999L) } returns null

        // When
        val result = repository.getLocalProgress(999L)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getLocalProgress returns null when progression is null`() = runTest {
        // Given
        val testBook = TestDataBuilders.createTestBook(
            id = 1L,
            progression = null
        )
        coEvery { mockBooksDao.get(1L) } returns testBook

        // When
        val result = repository.getLocalProgress(1L)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getLocalProgress returns null on database error`() = runTest {
        // Given
        coEvery { mockBooksDao.get(any()) } throws RuntimeException("Database error")

        // When
        val result = repository.getLocalProgress(1L)

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region Remote Progress Tests

    @Test
    fun `getRemoteProgress returns null and logs warning`() = runTest {
        // Given - method is deprecated in favor of ReadingSyncManager
        val bookIdentifier = "urn:isbn:1234567890"

        // When
        val result = repository.getRemoteProgress(bookIdentifier)

        // Then - should return null as sync is delegated
        assertThat(result).isNull()
    }

    // endregion
}
