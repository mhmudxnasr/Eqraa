package org.readium.r2.testapp.data

import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.from
import org.readium.r2.testapp.Application
import org.readium.r2.testapp.data.model.NoteDto
import org.readium.r2.testapp.data.model.ReadingProgressDto
import org.readium.r2.testapp.data.model.UserPreferencesDto
import org.readium.r2.testapp.data.db.BooksDao
import org.readium.r2.testapp.data.db.StatsDao
import org.json.JSONObject
import timber.log.Timber

/**
 * Handles full backup of user data (notes, highlights, stats, settings).
 */
class BackupManager(
    private val context: android.content.Context,
    private val booksDao: BooksDao,
    private val statsDao: StatsDao,
    private val scope: CoroutineScope
) {
    private val supabase by lazy { SupabaseService.client }
    
    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        object Success : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun performFullBackup() {
        scope.launch(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing
                Timber.d("Starting full backup...")

                // 1. Gather Library Data (Books, Notes/Highlights)
                val books = try { booksDao.getAllBooks().first() } catch (e: Exception) { emptyList() }
                
                val allNotes = mutableListOf<NoteDto>()
                val readingProgressMap = mutableMapOf<String, ReadingProgressDto>()

                books.forEach { book ->
                    val bookId = book.id ?: return@forEach
                    
                    // Add current progression to backup
                    book.progression?.let { progression ->
                        try {
                            val json = org.json.JSONObject(progression)
                            val userId = supabase.auth.currentSessionOrNull()?.user?.id 
                                ?: throw IllegalStateException("User must be logged in for backup")
                            val deviceId = (context.applicationContext as Application).readingSyncManager.deviceId
                                
                            val progressDto = ReadingProgressDto(
                                userId = userId,
                                bookId = book.identifier,
                                cfi = org.readium.r2.testapp.utils.CFICompressor.compress(progression),
                                percentage = json.optDouble("progression", 0.0).toFloat(),
                                updatedAt = System.currentTimeMillis(),
                                deviceId = deviceId
                            )
                            readingProgressMap[book.identifier] = progressDto
                            
                            // Batch Upsert Progress? Or just collect list
                            // Ideally we upsert the list later.
                            // But map is keyed by ID.
                        } catch (e: Exception) {
                            Timber.w("Failed to parse progression for book ${book.identifier}")
                        }
                    }

                    try {
                        // Highlights -> Notes
                        val highlights = booksDao.getHighlightsForBook(bookId).first()
                        highlights.forEach { highlight ->
                            val userId = supabase.auth.currentSessionOrNull()?.user?.id 
                                ?: throw IllegalStateException("User must be logged in for backup")

                            allNotes.add(
                                NoteDto(
                                    userId = userId,
                                    bookId = book.identifier,
                                    content = "", 
                                    cfi = highlight.href, 
                                    color = highlight.tint.toString(),
                                    timestamp = highlight.creation ?: 0L,
                                    note = highlight.annotation
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Timber.w("Failed to get highlights for book $bookId")
                    }
                }

                // 2. Stats
                val stats = try { statsDao.getAllSessions().first() } catch (e: Exception) { emptyList() }
                val statsMap = mapOf(
                    "totalSessions" to stats.size,
                    "totalDuration" to stats.sumOf { it.endTime - it.startTime },
                    "sessions" to stats.map { 
                        mapOf(
                            "bookId" to it.bookId,
                            "date" to it.date,
                            "duration" to (it.endTime - it.startTime)
                        ) 
                    }
                )

                // 3. Settings (Real data)
                val localPrefs = org.readium.r2.testapp.settings.ReadingPreferences(context)
                val userId = supabase.auth.currentSessionOrNull()?.user?.id 
                    ?: throw IllegalStateException("User must be logged in for backup")

                val settingsDto = org.readium.r2.testapp.data.model.UserPreferencesDto(
                    userId = userId,
                    fontSize = localPrefs.fontSize,
                    theme = when (localPrefs.theme) {
                        0 -> "paper"
                        1 -> "sepia"
                        2 -> "dark"
                        else -> "paper"
                    },
                    fontFamily = localPrefs.fontFamily,
                    lastUpdated = System.currentTimeMillis()
                )

                // Perform Batch Sync to Supabase
                
                // 1. Settings
                supabase.from("user_preferences").upsert(settingsDto) { select() }
                
                // 2. Reading Progress
                if (readingProgressMap.isNotEmpty()) {
                    val progressList = readingProgressMap.values.toList()
                    supabase.from("reading_progress").upsert(progressList) { select() }
                }
                
                // 3. Notes
                if (allNotes.isNotEmpty()) {
                    supabase.from("notes").upsert(allNotes) {
                         onConflict = "user_id, book_id, cfi"
                         select()
                    }
                }
                
                Timber.d("Backup/Sync completed for Settings, Progress (${readingProgressMap.size}), and Notes (${allNotes.size})")
                _syncState.value = SyncState.Success

                // Stats - TODO: Map and Sync Stats
                
            } catch (e: Exception) {
                Timber.e(e, "Full backup exception")
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            } finally {
                scope.launch {
                    kotlinx.coroutines.delay(3000)
                    _syncState.value = SyncState.Idle
                }
            }
        }
    }

    /**
     * Sync data for a specific book only.
     */
    fun syncBook(bookIdentifier: String) {
        scope.launch(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing
                Timber.d("Starting sync for book: $bookIdentifier")

                val book = booksDao.getBookByIdentifier(bookIdentifier)
                    ?: throw Exception("Book not found locally: $bookIdentifier")

                val bookId = book.id ?: throw Exception("Book ID is null")

                // 1. Sync Progress
                book.progression?.let { progression ->
                    try {
                        val json = JSONObject(progression)
                        val userId = supabase.auth.currentSessionOrNull()?.user?.id 
                            ?: throw IllegalStateException("User must be logged in for backup")
                        val deviceId = (context.applicationContext as Application).readingSyncManager.deviceId

                            val progressDto = ReadingProgressDto(
                                userId = userId,
                                bookId = book.identifier,
                                cfi = org.readium.r2.testapp.utils.CFICompressor.compress(progression),
                                percentage = json.optDouble("progression", 0.0).toFloat(),
                                updatedAt = System.currentTimeMillis(),
                                deviceId = deviceId
                            )
                        Timber.d("Syncing progress for $bookIdentifier: ${progressDto.percentage}%")
                        supabase.from("reading_progress").upsert(progressDto) { select() }
                        Timber.d("Progress synced for $bookIdentifier")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to sync progress for $bookIdentifier")
                    }
                }

                // 2. Sync Notes / Highlights
                try {
                    val highlights = booksDao.getHighlightsForBook(bookId).first()
                    val userId = supabase.auth.currentSessionOrNull()?.user?.id 
                        ?: throw IllegalStateException("User must be logged in for backup")

                    val notes = highlights.map { highlight ->
                        NoteDto(
                            userId = userId,
                            bookId = book.identifier,
                            content = "",
                            cfi = highlight.href,
                            color = highlight.tint.toString(),
                            timestamp = highlight.creation ?: 0L,
                            note = highlight.annotation
                        )
                    }
                    if (notes.isNotEmpty()) {
                        supabase.from("notes").upsert(notes) { 
                            onConflict = "user_id, book_id, cfi"
                            select() 
                        }
                        Timber.d("Notes synced for $bookIdentifier: ${notes.size} items")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync notes for $bookIdentifier")
                }

                Timber.d("Sync completed for book: $bookIdentifier")
                _syncState.value = SyncState.Success

            } catch (e: Exception) {
                Timber.e(e, "Book sync exception for $bookIdentifier")
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            } finally {
                scope.launch {
                    kotlinx.coroutines.delay(3000)
                    _syncState.value = SyncState.Idle
                }
            }
        }
    }
}
