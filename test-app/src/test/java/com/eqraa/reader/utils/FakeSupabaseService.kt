/*
 * Copyright 2024 Eqraa. All rights reserved.
 * Fake Supabase Service for testing
 */
package com.eqraa.reader.utils

import com.eqraa.reader.data.model.ReadingPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake Supabase service for unit testing without network calls.
 */
class FakeSupabaseService {

    // State storage
    private val _readingPositions = mutableMapOf<String, ReadingPosition>()

    // Event simulation
    private val _realtimeEvents = MutableSharedFlow<RealtimeEvent>()
    val realtimeEvents: Flow<RealtimeEvent> = _realtimeEvents

    // Error simulation
    private var shouldFail = false
    private var errorMessage = "Simulated network error"

    // Authentication state
    private var _isAuthenticated = MutableStateFlow(false)
    private var _currentUserId: String? = null

    val isAuthenticated: Flow<Boolean> = _isAuthenticated
    val currentUserId: String? get() = _currentUserId

    // --- Configuration Methods ---

    fun setShouldFail(fail: Boolean, message: String = "Simulated network error") {
        shouldFail = fail
        errorMessage = message
    }

    fun setAuthenticated(authenticated: Boolean, userId: String? = "test-user-id") {
        _isAuthenticated.value = authenticated
        _currentUserId = if (authenticated) userId else null
    }

    fun setReadingPosition(position: ReadingPosition) {
        _readingPositions[position.bookId] = position
    }

    fun clear() {
        _readingPositions.clear()
        shouldFail = false
        _isAuthenticated.value = false
        _currentUserId = null
    }

    // --- Reading Progress Operations ---

    suspend fun getReadingPosition(bookId: String): Result<ReadingPosition?> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        return Result.success(_readingPositions[bookId])
    }

    suspend fun saveReadingPosition(position: ReadingPosition): Result<Unit> {
        if (shouldFail) return Result.failure(Exception(errorMessage))
        _readingPositions[position.bookId] = position
        return Result.success(Unit)
    }

    // --- Realtime Simulation ---

    suspend fun emitRealtimeEvent(event: RealtimeEvent) {
        _realtimeEvents.emit(event)
    }

    // --- Verification Methods ---

    fun getSavedReadingPosition(bookId: String): ReadingPosition? {
        return _readingPositions[bookId]
    }

    fun getReadingPositionCount(): Int = _readingPositions.size

    sealed class RealtimeEvent {
        data class ProgressUpdate(
            val bookId: String,
            val position: ReadingPosition
        ) : RealtimeEvent()

        data class HighlightSync(
            val bookId: Long,
            val action: SyncAction
        ) : RealtimeEvent()
    }

    enum class SyncAction {
        INSERT, UPDATE, DELETE
    }
}
