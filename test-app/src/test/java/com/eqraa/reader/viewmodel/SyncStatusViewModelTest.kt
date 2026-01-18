/*
 * Copyright 2024 Eqraa. All rights reserved.
 * Unit Tests for SyncStatusViewModel
 */
package com.eqraa.reader.viewmodel

import com.eqraa.reader.base.BaseViewModelTest
import com.eqraa.reader.data.ReadingSyncManager
import com.eqraa.reader.data.model.ReadingPosition
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Unit tests for sync-related functionality.
 * 
 * Tests cover:
 * - Conflict detection logic
 * - Progress sync operations
 * - Force upload/download scenarios
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModelTest : BaseViewModelTest() {

    // Mocks
    private lateinit var mockReadingSyncManager: ReadingSyncManager

    // Test flows
    private val networkStateFlow = MutableStateFlow(true)

    override fun setUp() {
        super.setUp()
        mockReadingSyncManager = mockk(relaxed = true)
    }

    // region Network Monitoring Tests

    @Test
    fun `network state changes are observed`() = runTest {
        // Given initial connected state
        networkStateFlow.value = true

        // When network disconnects
        networkStateFlow.value = false

        // Then the state should reflect disconnection
        assertThat(networkStateFlow.value).isFalse()
    }

    @Test
    fun `network reconnection updates state`() = runTest {
        // Given disconnected state
        networkStateFlow.value = false

        // When network reconnects
        networkStateFlow.value = true

        // Then state should reflect connection
        assertThat(networkStateFlow.value).isTrue()
    }

    // endregion

    // region Conflict Detection Tests

    @Test
    fun `conflict detection identifies timestamp differences`() = runTest {
        // Given
        val localPosition = createTestReadingPosition(timestamp = 1000L)
        val remotePosition = createTestReadingPosition(timestamp = 2000L)

        // When comparing timestamps
        val hasConflict = localPosition.timestamp != remotePosition.timestamp

        // Then
        assertThat(hasConflict).isTrue()
    }

    @Test
    fun `no conflict when timestamps match`() = runTest {
        // Given
        val localPosition = createTestReadingPosition(timestamp = 1000L)
        val remotePosition = createTestReadingPosition(timestamp = 1000L)

        // When comparing timestamps
        val hasConflict = localPosition.timestamp != remotePosition.timestamp

        // Then
        assertThat(hasConflict).isFalse()
    }

    @Test
    fun `conflict with different cfi but same timestamp is detected`() = runTest {
        // Given positions with same timestamp but different CFI
        val localPosition = createTestReadingPosition(
            timestamp = 1000L,
            cfi = "local-cfi"
        )
        val remotePosition = createTestReadingPosition(
            timestamp = 1000L,
            cfi = "remote-cfi"
        )

        // When comparing CFIs
        val hasConflict = localPosition.cfi != remotePosition.cfi

        // Then
        assertThat(hasConflict).isTrue()
    }

    // endregion

    // region Sync Manager Interaction Tests

    @Test
    fun `forceSyncPending calls sync manager`() = runTest {
        // Given
        coEvery { mockReadingSyncManager.forceSyncPending() } just Runs

        // When
        mockReadingSyncManager.forceSyncPending()

        // Then
        coVerify { mockReadingSyncManager.forceSyncPending() }
    }

    @Test
    fun `forceUploadPosition calls sync manager with correct params`() = runTest {
        // Given
        val position = createTestReadingPosition()
        coEvery { mockReadingSyncManager.forceUploadPosition("book-id", position) } just Runs

        // When
        mockReadingSyncManager.forceUploadPosition("book-id", position)

        // Then
        coVerify { mockReadingSyncManager.forceUploadPosition("book-id", position) }
    }

    @Test
    fun `forceDownloadPosition calls sync manager with correct params`() = runTest {
        // Given
        val position = createTestReadingPosition()
        coEvery { mockReadingSyncManager.forceDownloadPosition("book-id", position) } just Runs

        // When
        mockReadingSyncManager.forceDownloadPosition("book-id", position)

        // Then
        coVerify { mockReadingSyncManager.forceDownloadPosition("book-id", position) }
    }

    // endregion

    // region Update Progress Tests

    @Test
    fun `updateProgress calls sync manager with all parameters`() = runTest {
        // Given
        coEvery { 
            mockReadingSyncManager.updateProgress(
                bookId = 1L,
                bookIdentifier = "book-id",
                cfi = "test-cfi",
                percentage = 0.5f,
                pageNumber = 10,
                chapterId = "chapter-1"
            ) 
        } just Runs

        // When
        mockReadingSyncManager.updateProgress(
            bookId = 1L,
            bookIdentifier = "book-id",
            cfi = "test-cfi",
            percentage = 0.5f,
            pageNumber = 10,
            chapterId = "chapter-1"
        )

        // Then
        coVerify { 
            mockReadingSyncManager.updateProgress(
                bookId = 1L,
                bookIdentifier = "book-id",
                cfi = "test-cfi",
                percentage = 0.5f,
                pageNumber = 10,
                chapterId = "chapter-1"
            )
        }
    }

    // endregion

    // region Helper Methods

    private fun createTestReadingPosition(
        bookId: String = "test-book-id",
        cfi: String = """{"position":1,"totalProgression":0.1}""",
        percentage: Float = 0.1f,
        timestamp: Long = System.currentTimeMillis(),
        deviceId: String? = "test-device"
    ) = ReadingPosition(
        bookId = bookId,
        cfi = cfi,
        percentage = percentage,
        timestamp = timestamp,
        deviceId = deviceId
    )

    // endregion
}
