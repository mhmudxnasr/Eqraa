/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package com.eqraa.reader.reader

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.LocatorCollection
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.SearchTry
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError
import com.eqraa.reader.Application
import com.eqraa.reader.R
import com.eqraa.reader.data.BackupManager
import com.eqraa.reader.data.BookRepository
import com.eqraa.reader.data.ReadingProgressRepository
import com.eqraa.reader.data.ReadingSyncManager
import com.eqraa.reader.data.RealtimeSyncManager
import com.eqraa.reader.data.model.Highlight
import com.eqraa.reader.data.model.ReadingPosition
import com.eqraa.reader.domain.toUserError
import com.eqraa.reader.reader.preferences.UserPreferencesViewModel
import com.eqraa.reader.reader.tts.TtsViewModel
import com.eqraa.reader.search.SearchPagingSource
import com.eqraa.reader.utils.EventChannel
import com.eqraa.reader.utils.UserError
import com.eqraa.reader.utils.createViewModelFactory
import com.eqraa.reader.utils.extensions.toHtml
import com.eqraa.reader.utils.CFICompressor
import com.eqraa.reader.data.ReadingSessionManager
import timber.log.Timber

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
    val bookId: Long,
    private val bookIdentifier: String,
    private val context: Context,
    private val readerRepository: ReaderRepository,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val readingSyncManager: ReadingSyncManager?,
    private val backupManager: BackupManager?,
    private val realtimeSyncManager: RealtimeSyncManager?,
    private val statsRepository: com.eqraa.reader.data.StatsRepository,
    private val wordCardDao: com.eqraa.reader.data.db.WordCardDao
) : ViewModel(),
    EpubNavigatorFragment.Listener,
    ImageNavigatorFragment.Listener,
    PdfNavigatorFragment.Listener {

    val readerInitData =
        try {
            checkNotNull(readerRepository[bookId])
        } catch (e: Exception) {
            // Fallbacks on a dummy Publication to avoid crashing the app until the Activity finishes.
            DummyReaderInitData(bookId)
        }

    val publication: Publication =
        readerInitData.publication

    private val syncIdentifier: String by lazy {
        publication.metadata.identifier ?: bookIdentifier
    }

    val activityChannel: EventChannel<ActivityCommand> =
        EventChannel(Channel(Channel.BUFFERED), viewModelScope)

    val fragmentChannel: EventChannel<FragmentFeedback> =
        EventChannel(Channel(Channel.BUFFERED), viewModelScope)

    val visualFragmentChannel: EventChannel<VisualFragmentCommand> =
        EventChannel(Channel(Channel.BUFFERED), viewModelScope)

    val searchChannel: EventChannel<SearchCommand> =
        EventChannel(Channel(Channel.BUFFERED), viewModelScope)

    val tts: TtsViewModel? = TtsViewModel(
        viewModelScope = viewModelScope,
        readerInitData = readerInitData
    )

    val settings: UserPreferencesViewModel<*, *>? = UserPreferencesViewModel(
        viewModelScope = viewModelScope,
        readerInitData = readerInitData
    )

    // Initialize ReadingSessionManager
    private val readingSessionManager = if (readingSyncManager != null) {
        ReadingSessionManager(
            readingProgressRepository = readingProgressRepository,
            readingSyncManager = readingSyncManager,
            scope = viewModelScope
        )
    } else {
        null
    }
    
    val currentLocator: StateFlow<Locator?> get() = _currentLocator
    private val _currentLocator = MutableStateFlow<Locator?>(null)

    val totalPositions: StateFlow<Int> get() = _totalPositions
    private val _totalPositions = MutableStateFlow(0)
    
    val currentPosition: StateFlow<Int?> get() = _currentPosition
    private val _currentPosition = MutableStateFlow<Int?>(null)

    // Sync Status for UI
    val readingSyncStatus = readingSyncManager?.syncStatus
        ?: MutableStateFlow(ReadingSyncManager.SyncStatus.Idle)


    init {
        viewModelScope.launch {
            _totalPositions.value = publication.positions().size
        }
        subscribeToRealtimeEvents()
        observeSyncManagerUpdates()
        observeSessionState()
        
        // Start "Safe Start" Handshake
        readingSessionManager?.initializeSession(bookId, syncIdentifier)
        
        // Trigger highlight and bookmark sync on open
        viewModelScope.launch {
            try {
                bookRepository.highlightSyncManager?.fullSync(syncIdentifier)
                bookRepository.bookmarkSyncManager?.fullSync(syncIdentifier)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync highlights/bookmarks on open")
            }
        }
    }
    
    private fun observeSessionState() = viewModelScope.launch {
        readingSessionManager?.state?.collect { state ->
            when (state) {
                is ReadingSessionManager.SessionState.Conflict -> {
                    Timber.d("⚠️ Conflict detected via SessionManager")
                    // Show conflict dialog or auto-resolve based on heuristic?
                    // For "New Device" scenario (Remote > Local), we usually want to Auto-Jump or Prompt.
                    // The ReadingSessionManager has actually already BLOCKED saves.
                    // Let's prompt the user.
                    activityChannel.send(ActivityCommand.ShowConflictDialog(state.local, state.remote))
                }
                is ReadingSessionManager.SessionState.Synced -> {
                    // Update UI status to "Synced"
                }
                is ReadingSessionManager.SessionState.Syncing -> {
                    // Update UI status to "Syncing..."
                }
                else -> {}
            }
        }
    }

    
    // Stats
    val totalReadingTime = statsRepository.totalReadingTimeMs()
    val weeklyActivity = statsRepository.activityForLast7Days()
    val currentStreak = kotlinx.coroutines.flow.flow { emit(statsRepository.currentStreak()) }

    private fun observeSyncManagerUpdates() = viewModelScope.launch {
        readingSyncManager?.remoteProgressFlow?.collect { progress ->
            if (progress.bookId == syncIdentifier) {
                // New remote progress detected via ReadingSyncManager
                Timber.d("New remote progress via ReadingSyncManager: ${progress.cfi}")
                activityChannel.send(ActivityCommand.SyncDetected(
                    cfi = CFICompressor.decompress(progress.cfi),
                    percentage = progress.percentage
                ))
            }
        }
    }

    private fun subscribeToRealtimeEvents() = viewModelScope.launch {
        realtimeSyncManager?.events?.collect { event ->
            when (event) {
                // Reading progress is now handled by ReadingSyncManager.remoteProgressFlow
                else -> {}
            }
        }
    }

    fun checkRemoteProgress(showDialog: Boolean = true) = viewModelScope.launch {
        // Obsolete: ReadingSessionManager handles this via "Handshake"
        // But we might want gracefully handle manual checks here?
        // For now, let's just log.
        Timber.d("Manual check delegated to SessionManager (Active)")
    }

    fun applyRemoteProgress(remote: ReadingPosition) = viewModelScope.launch {
        Timber.d("Applying remote progress: ${remote.cfi}")
        readingSessionManager?.resolveConflict(useRemote = true, remotePosition = remote)
        
        // This will update local DB via SessionManager or we do it here?
        // SessionManager should handle it, or we do it here and notify SessionManager.
        // Let's do it here to ensure UI update immediately
        readingProgressRepository.saveProgressLocally(
            bookId = bookId,
            cfi = remote.cfi,
            timestamp = remote.timestamp
        )
         // Also save hash to prevent duplicate save detection
        val hash = remote.cfi.hashCode()
        saveLastSavedLocatorHash(bookId, hash)
    }
    
    private fun applyRemoteProgressSilently(remote: ReadingPosition) {
         // Moved to SessionManager or kept for quick internal use?
         // For now, let's remove as SessionManager handles handshake.
    }

    fun forceLocalProgress() = viewModelScope.launch {
        Timber.d("Forcing local progress override")
        readingSessionManager?.resolveConflict(useRemote = false, remotePosition = null)
        val locator = _currentLocator.value ?: return@launch
        saveProgression(locator, forceImmediate = true)
    }

    override fun onCleared() {
        // Force sync any pending progress before closing
        readingSessionManager?.onCleanup()
        readerRepository.close(bookId)
    }

    fun saveProgression(locator: Locator, forceImmediate: Boolean = false) {
        // Delegate to ReadingSessionManager
        _currentLocator.value = locator
        
        if (readingSessionManager != null) {
            readingSessionManager.onPageChanged(locator)
        } else {
            // Fallback for offline/no-sync scenario (should rare)
             viewModelScope.launch {
                readingProgressRepository.saveProgressLocally(
                    bookId = bookId,
                    cfi = locator.toJSON().toString(),
                    timestamp = System.currentTimeMillis()
                )
             }
        }
    }


    fun syncBook() = viewModelScope.launch {
        // Fix: Use publication identifier
        val identifier = syncIdentifier
        Timber.d("Manual sync triggered for book: $identifier")
        backupManager?.syncBook(identifier)
    }

    fun performFullBackup() = viewModelScope.launch {
        Timber.d("Manual full backup triggered")
        backupManager?.performFullBackup()
    }

    fun getBookmarks() = bookRepository.bookmarksForBook(bookId)

    fun insertBookmark(locator: Locator) = viewModelScope.launch {
        val id = bookRepository.insertBookmark(bookId, publication, locator)
        if (id != -1L) {
            fragmentChannel.send(FragmentFeedback.BookmarkSuccessfullyAdded)
        } else {
            fragmentChannel.send(FragmentFeedback.BookmarkFailed)
        }
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch {
        bookRepository.deleteBookmark(id)
    }

    // Highlights

    val highlights: Flow<List<Highlight>> by lazy {
        bookRepository.highlightsForBook(bookId)
    }

    /**
     * Database ID of the active highlight for the current highlight pop-up. This is used to show
     * the highlight decoration in an "active" state.
     */
    var activeHighlightId = MutableStateFlow<Long?>(null)

    /**
     * Current state of the highlight decorations.
     *
     * It will automatically be updated when the highlights database table or the current
     * [activeHighlightId] change.
     */
    val highlightDecorations: Flow<List<Decoration>> by lazy {
        highlights.combine(activeHighlightId) { highlights, activeId ->
            highlights.flatMap { highlight ->
                highlight.toDecorations(isActive = (highlight.id == activeId))
            }
        }
    }

    /**
     * Creates a list of [Decoration] for the receiver [Highlight].
     */
    private fun Highlight.toDecorations(isActive: Boolean): List<Decoration> {
        fun createDecoration(idSuffix: String, style: Decoration.Style) = Decoration(
            id = "$id-$idSuffix",
            locator = locator,
            style = style,
            extras = mapOf(
                // We store the highlight's database ID in the extras map, for easy retrieval
                // later. You can store arbitrary information in the map.
                "id" to id
            )
        )

        return listOfNotNull(
            // Decoration for the actual highlight / underline.
            createDecoration(
                idSuffix = "highlight",
                style = when (style) {
                    Highlight.Style.HIGHLIGHT -> Decoration.Style.Highlight(
                        tint = tint,
                        isActive = isActive
                    )
                    Highlight.Style.UNDERLINE -> Decoration.Style.Underline(
                        tint = tint,
                        isActive = isActive
                    )
                }
            ),
            // Additional page margin icon decoration, if the highlight has an associated note.
            annotation.takeIf { it.isNotEmpty() }?.let {
                createDecoration(
                    idSuffix = "annotation",
                    style = DecorationStyleAnnotationMark(tint = tint)
                )
            }
        )
    }

    suspend fun highlightById(id: Long): Highlight? =
        bookRepository.highlightById(id)

    fun addHighlight(
        locator: Locator,
        style: Highlight.Style,
        @ColorInt tint: Int,
        annotation: String = "",
    ) = viewModelScope.launch {
        bookRepository.addHighlight(bookId, style, tint, locator, annotation)
    }

    fun updateHighlightAnnotation(id: Long, annotation: String) = viewModelScope.launch {
        bookRepository.updateHighlightAnnotation(id, annotation)
    }

    fun updateHighlightStyle(id: Long, style: Highlight.Style, @ColorInt tint: Int) = viewModelScope.launch {
        bookRepository.updateHighlightStyle(id, style, tint)
    }

    fun deleteHighlight(id: Long) = viewModelScope.launch {
        bookRepository.deleteHighlight(id)
    }

    // Search

    fun search(query: String) = viewModelScope.launch {
        if (query == lastSearchQuery) return@launch
        lastSearchQuery = query
        _searchLocators.value = emptyList()
        searchIterator = publication.search(query)
            ?: run {
                activityChannel.send(
                    ActivityCommand.ToastError(
                        UserError(R.string.search_error_not_searchable, cause = null)
                    )
                )
                null
            }
        pagingSourceFactory.invalidate()
        searchChannel.send(SearchCommand.StartNewSearch)
    }

    fun cancelSearch() = viewModelScope.launch {
        _searchLocators.value = emptyList()
        searchIterator?.close()
        searchIterator = null
        pagingSourceFactory.invalidate()
    }

    val searchLocators: StateFlow<List<Locator>> get() = _searchLocators
    private var _searchLocators = MutableStateFlow<List<Locator>>(emptyList())

    /**
     * Maps the current list of search result locators into a list of [Decoration] objects to
     * underline the results in the navigator.
     */
    val searchDecorations: Flow<List<Decoration>> by lazy {
        searchLocators.map {
            it.mapIndexed { index, locator ->
                Decoration(
                    // The index in the search result list is a suitable Decoration ID, as long as
                    // we clear the search decorations between two searches.
                    id = index.toString(),
                    locator = locator,
                    style = Decoration.Style.Underline(tint = Color.RED)
                )
            }
        }
    }

    private var lastSearchQuery: String? = null

    private var searchIterator: SearchIterator? = null

    private val pagingSourceFactory = InvalidatingPagingSourceFactory {
        SearchPagingSource(listener = PagingSourceListener())
    }

    // Navigator.Listener

    override fun onResourceLoadFailed(href: Url, error: ReadError) {
        activityChannel.send(
            ActivityCommand.ToastError(error.toUserError())
        )
    }

    // HyperlinkNavigator.Listener
    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        activityChannel.send(ActivityCommand.OpenExternalLink(url))
    }

    override fun shouldFollowInternalLink(
        link: Link,
        context: HyperlinkNavigator.LinkContext?,
    ): Boolean =
        when (context) {
            is HyperlinkNavigator.FootnoteContext -> {
                val text =
                    if (link.mediaType?.isHtml == true) {
                        context.noteContent.toHtml()
                    } else {
                        context.noteContent
                    }

                val command = VisualFragmentCommand.ShowPopup(text)
                visualFragmentChannel.send(command)
                false
            }
            else -> true
        }

    // Search

    inner class PagingSourceListener : SearchPagingSource.Listener {
        override suspend fun next(): SearchTry<LocatorCollection?> {
            val iterator = searchIterator ?: return Try.success(null)
            return iterator.next().onSuccess {
                _searchLocators.value += (it?.locators ?: emptyList())
            }
        }
    }

    val searchResult: Flow<PagingData<Locator>> =
        Pager(PagingConfig(pageSize = 20), pagingSourceFactory = pagingSourceFactory)
            .flow.cachedIn(viewModelScope)

    // Events

    sealed class ActivityCommand {
        object OpenOutlineRequested : ActivityCommand()
        object OpenDrmManagementRequested : ActivityCommand()
        class OpenExternalLink(val url: AbsoluteUrl) : ActivityCommand()
        class ToastError(val error: UserError) : ActivityCommand()
        class SyncDetected(val cfi: String, val percentage: Float) : ActivityCommand()
        class ShowConflictDialog(val local: ReadingPosition?, val remote: ReadingPosition) : ActivityCommand()
    }

    sealed class FragmentFeedback {
        object BookmarkSuccessfullyAdded : FragmentFeedback()
        object BookmarkFailed : FragmentFeedback()
    }

    private fun saveLastSavedLocatorHash(bookId: Long, hash: Int) {
        val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("last_saved_hash_$bookId", hash).apply()
    }

    sealed class VisualFragmentCommand {
        class ShowPopup(val text: CharSequence) : VisualFragmentCommand()
    }

    sealed class SearchCommand {
        object StartNewSearch : SearchCommand()
    }

    fun saveWordCard(word: String, definition: String = "", contextSentence: String = "") = viewModelScope.launch {
        try {
            val existing = wordCardDao.getCardByWord(word)
            if (existing == null) {
                wordCardDao.insert(
                    com.eqraa.reader.data.model.WordCard(
                        word = word,
                        definition = definition,
                        contextSentence = contextSentence,
                        translation = null,
                        sourceBookId = bookId
                    )
                )
                Timber.d("Saved word card: $word")
            } else {
                Timber.d("Word card already exists: $word")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save word card")
        }
    }

    companion object {
        fun createFactory(application: Application, arguments: ReaderActivityContract.Arguments) =
            createViewModelFactory {
                val bookIdentifier = arguments.bookId.toString() // Use book ID as identifier
                val readingProgressRepository = application.readingProgressRepository
                val readingSyncManager = application.readingSyncManager
                val backupManager = application.backupManager
                val realtimeSyncManager = application.realtimeSyncManager
                val statsRepository = application.statsRepository
                // Get DAO from database
                val wordCardDao = com.eqraa.reader.data.db.AppDatabase.getDatabase(application).wordCardDao()
                
                ReaderViewModel(
                    arguments.bookId,
                    bookIdentifier,
                    application.applicationContext,
                    application.readerRepository,
                    application.bookRepository,
                    readingProgressRepository,
                    readingSyncManager,
                    backupManager,
                    realtimeSyncManager,
                    statsRepository,
                    wordCardDao
                )
            }
    }
}
