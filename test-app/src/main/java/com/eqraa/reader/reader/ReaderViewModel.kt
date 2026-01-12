/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package com.eqraa.reader.reader

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
import timber.log.Timber

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
    val bookId: Long,
    private val bookIdentifier: String,
    private val readerRepository: ReaderRepository,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val readingSyncManager: ReadingSyncManager?,
    private val backupManager: BackupManager?,
    private val realtimeSyncManager: RealtimeSyncManager?,
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

    val syncState: StateFlow<BackupManager.SyncState> = 
        backupManager?.syncState ?: MutableStateFlow(BackupManager.SyncState.Idle)

    private val _currentLocator = MutableStateFlow<Locator?>(null)
    val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    val currentPosition: Flow<Int?> = currentLocator.map { it?.locations?.position }
    private val _totalPositions = MutableStateFlow(0)
    val totalPositions: StateFlow<Int> = _totalPositions.asStateFlow()

    private var lastSavedLocator: Locator? = null

    init {
        // Initialize lastSavedLocator with the initial location to prevent 
        // immediate "save" triggering a timestamp update on open.
        if (readerInitData is VisualReaderInitData) {
             lastSavedLocator = readerInitData.initialLocation
        }

        viewModelScope.launch {
            _totalPositions.value = publication.positions().size
        }
        subscribeToRealtimeEvents()
        observeSyncManagerUpdates()
    }

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

    private var lastLocalUpdate: Long = 0

    private fun subscribeToRealtimeEvents() = viewModelScope.launch {
        realtimeSyncManager?.events?.collect { event ->
            when (event) {
                // Reading progress is now handled by ReadingSyncManager.remoteProgressFlow
                else -> {}
            }
        }
    }

    fun checkRemoteProgress(showDialog: Boolean = true) = viewModelScope.launch {
        try {
            val identifier = syncIdentifier
            val remote = readingProgressRepository.getRemoteProgress(identifier) ?: return@launch
            
            // 1. Get truly persistent local state
            val localState = readingProgressRepository.getLocalProgress(bookId)
            val storedLocalTimestamp = localState?.second ?: 0L
            
            // 2. Determine effective local timestamp (session vs stored)
            val effectiveLocalTimestamp = if (lastLocalUpdate > storedLocalTimestamp) lastLocalUpdate else storedLocalTimestamp
            
            val currentId = readingSyncManager?.deviceId

            // 3. Conflict Check
            // We have a conflict if Remote is NEWER than Local AND from a DIFFERENT device.
            // If Local is 0 (fresh install), we usually don't consider it a "conflict" unless we want to confirm before jumping.
            // But if we have stored local state, and remote is newer...
            
            val isRemoteNewer = remote.timestamp > effectiveLocalTimestamp
            val isDifferentDevice = remote.deviceId != currentId
            
            if (isRemoteNewer && isDifferentDevice) {
                 Timber.d("Sync conflict detected. Remote: ${remote.timestamp} vs Local: $effectiveLocalTimestamp")
                 
                 // Construct local position object for comparison
                 // Try to resolve from current session locator first, then stored cfi
                 val localCfi = _currentLocator.value?.toJSON()?.toString() ?: localState?.first
                 
                 var localPositionObject: ReadingPosition? = null
                 
                 if (localCfi != null) {
                     try {
                         val locator = Locator.fromJSON(org.json.JSONObject(localCfi))
                         if (locator != null) {
                             localPositionObject = ReadingPosition(
                                 bookId = identifier,
                                 cfi = localCfi,
                                 percentage = (locator.locations.totalProgression ?: 0.0).toFloat(),
                                 timestamp = effectiveLocalTimestamp,
                                 deviceId = currentId,
                                 pageNumber = locator.locations.position,
                                 totalPages = _totalPositions.value
                             )
                         }
                     } catch (e: Exception) { Timber.e(e, "Failed to parse local cfi") }
                 }

                 if (showDialog) {
                     // If we have NO local state (fresh install), usually we just Sync Detected (Prompt).
                     // If we have local state, we show Conflict Dialog.
                     if (storedLocalTimestamp > 0 || lastLocalUpdate > 0) {
                        activityChannel.send(ActivityCommand.ShowConflictDialog(localPositionObject, remote))
                     } else {
                        // First time open? Just prompt normally or auto-sync?
                        // User request: "First time on new device: No conflict, just sync down"
                        // But we still want to inform user "Hey we found progress". 
                        // Prompting "Jump" is safer.
                        activityChannel.send(ActivityCommand.SyncDetected(remote.cfi, remote.percentage))
                     }
                 }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check remote progress")
            // Requirement: "Show subtle banner: Offline mode - progress will sync when connected"
            // For now, logging and treating as local-only flow.
            // Ideally, we'd emit a state to the Activity to show a Snackbar.
            activityChannel.send(
                ActivityCommand.ToastError(
                   com.eqraa.reader.utils.UserError(R.string.offline_mode_message, cause = null)
                )
            )
        }
    }

    fun applyRemoteProgress(remote: ReadingPosition) = viewModelScope.launch {
         Timber.d("Applying remote progress: ${remote.cfi}")
         // Also update local storage so we don't prompt again immediately
         readingProgressRepository.saveProgress(
             bookId = bookId,
             bookIdentifier = syncIdentifier,
             cfi = remote.cfi,
             percentage = remote.percentage,
             pageNumber = remote.pageNumber
         )
    }

    fun forceLocalProgress() = viewModelScope.launch {
        val locator = _currentLocator.value ?: return@launch
        Timber.d("Forcing local progress override")
        // Just trigger a save, which will push to cloud with new timestamp
        saveProgression(locator)
    }

    override fun onCleared() {
        // When the ReaderViewModel is disposed of, we want to close the publication to avoid
        // using outdated information (such as the initial location) if the `ReaderActivity` is
        // opened again with the same book.
        readerRepository.close(bookId)
    }

    fun saveProgression(locator: Locator) {
        // Prevent duplicate saves (especially on initial load)
        if (locator == lastSavedLocator) {
             Timber.v("Ignoring duplicate save progression for $bookId")
             return
        }
        lastSavedLocator = locator
        
        _currentLocator.value = locator
        viewModelScope.launch {
        Timber.v("Saving locator for book $bookId: $locator.")
        lastLocalUpdate = System.currentTimeMillis()
        
        try {
            val cfi = locator.toJSON().toString()
            val percentage = (locator.locations.totalProgression ?: 0.0).toFloat()
            val identifier = syncIdentifier
            
            // 1. Legacy/Local Save (Room)
            readingProgressRepository.saveProgress(
                bookId = bookId,
                bookIdentifier = identifier,
                cfi = cfi,
                percentage = percentage,
                pageNumber = locator.locations.position
            )

            // 2. New Sync Manager (Supabase with Debounce)
            Timber.v("ReaderViewModel: Syncing to Cloud for $identifier")
            readingSyncManager?.updateProgress(
                bookId = identifier,
                cfi = cfi,
                percentage = percentage,
                pageNumber = locator.locations.position
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to save progression")
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

    sealed class VisualFragmentCommand {
        class ShowPopup(val text: CharSequence) : VisualFragmentCommand()
    }

    sealed class SearchCommand {
        object StartNewSearch : SearchCommand()
    }

    companion object {
        fun createFactory(application: Application, arguments: ReaderActivityContract.Arguments) =
            createViewModelFactory {
                val bookIdentifier = arguments.bookId.toString() // Use book ID as identifier
                val readingProgressRepository = application.readingProgressRepository
                val readingSyncManager = application.readingSyncManager
                val backupManager = application.backupManager
                val realtimeSyncManager = application.realtimeSyncManager
                ReaderViewModel(
                    arguments.bookId,
                    bookIdentifier,
                    application.readerRepository,
                    application.bookRepository,
                    readingProgressRepository,
                    readingSyncManager,
                    backupManager,
                    realtimeSyncManager
                )
            }
    }
}
