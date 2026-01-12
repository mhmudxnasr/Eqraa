/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.reader

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.view.ActionMode
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Brush
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.BaseActionModeCallback
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Language
import com.eqraa.reader.R
import com.eqraa.reader.data.model.Highlight
import com.eqraa.reader.databinding.FragmentReaderBinding
import com.eqraa.reader.reader.preferences.ReaderSettingsBottomSheet
import com.eqraa.reader.reader.tts.TtsControls
import com.eqraa.reader.reader.tts.TtsPreferencesBottomSheetDialogFragment
import com.eqraa.reader.reader.tts.TtsViewModel
import com.eqraa.reader.utils.clearPadding
import com.eqraa.reader.utils.extensions.confirmDialog
import com.eqraa.reader.utils.extensions.throttleLatest
import com.eqraa.reader.utils.hideSystemUi
import com.eqraa.reader.utils.observeWhenStarted
import com.eqraa.reader.utils.padSystemUi
import com.eqraa.reader.utils.showSystemUi
import com.eqraa.reader.utils.toggleSystemUi
import com.eqraa.reader.utils.viewLifecycle
import com.google.android.material.snackbar.Snackbar
import com.eqraa.reader.data.BackupManager

/*
 * Base reader fragment class
 *
 * Provides common menu items and saves last location on stop.
 */
@OptIn(ExperimentalReadiumApi::class)
abstract class VisualReaderFragment : BaseReaderFragment() {

    protected var binding: FragmentReaderBinding by viewLifecycle()

    private lateinit var navigatorFragment: Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * When true, the user won't be able to interact with the navigator.
     */
    private var disableTouches by mutableStateOf(false)

    private var activeOverlay by mutableStateOf<ActiveOverlay>(ActiveOverlay.None)
    private var selectedTextForOverlay by mutableStateOf("")
    
    // UI State
    private var isSystemUiVisible by mutableStateOf(false)
    private var isEditMode by mutableStateOf(false)
    private val highlighterState = HighlighterState()

    sealed class ActiveOverlay {
        object None : ActiveOverlay()
        data class Dictionary(val tab: Int) : ActiveOverlay()
        object Ai : ActiveOverlay()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        navigatorFragment = navigator as Fragment

        // Start with Hidden System UI (Immersive)
        requireActivity().hideSystemUi()

        (navigator as OverflowableNavigator).apply {
            // This will automatically turn pages when tapping the screen edges or arrow keys.
            addInputListener(DirectionalNavigationAdapter(this, animatedTransition = true))
        }

        (navigator as VisualNavigator).apply {
            addInputListener(object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    // Custom Logic using relative point (0.0 to 1.0)
                    // Zone: Top 20% of screen AND Middle (Horizontal)
                    // assuming Point is relative to view size (normalized) or pixels?
                    // TapEvent usually provides absolute pixels. We need view dimensions.
                    
                    val viewWidth = binding.root.width.toFloat()
                    val viewHeight = binding.root.height.toFloat()
                    
                    if (viewWidth > 0 && viewHeight > 0) {
                        val relX = event.point.x / viewWidth
                        val relY = event.point.y / viewHeight
                        
                        // Condition: Top 20% (y < 0.2) AND Middle Horizontal (30% to 70% width?)
                        // "mid and 20% top of screen"
                        val isTopZone = relY <= 0.2f
                        val isMiddleZone = relX in 0.3f..0.7f
                        
                        if (isTopZone && isMiddleZone) {
                             isSystemUiVisible = !isSystemUiVisible
                             requireActivity().toggleSystemUi() // Actually we hid default bar, so this just handles system bars
                             return true
                        }
                    }
                    
                    // If not in zone, do not toggle UI. Let navigator handle page turns (return false)
                    return false
                }
            })
        }

        setupObservers()

        childFragmentManager.addOnBackStackChangedListener {
            updateSystemUiVisibility()
        }
        binding.fragmentReaderContainer.setOnApplyWindowInsetsListener { container, insets ->
            updateSystemUiPadding(container, insets)
            insets
        }

        binding.overlay.setContent {
            val context = LocalContext.current
            val prefs = remember { com.eqraa.reader.settings.ReadingPreferences(context, model.bookId.toString()) }
            
            // Sync isEditMode with preferences on composition
            LaunchedEffect(Unit) {
                isEditMode = prefs.isEditMode
            }
            
            if (disableTouches) {
                // Add an invisible box on top of the navigator to intercept touch gestures.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                requireActivity().toggleSystemUi()
                            }
                        }
                )
            }
            
            // Edit Mode: Gesture Handler Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .editModeGestures(
                        isEditMode = isEditMode,
                        onStylusDown = { offset -> highlighterState.startStroke(offset) },
                        onStylusMove = { offset -> highlighterState.continueStroke(offset) },
                        onStylusUp = { highlighterState.endStroke() },
                        onTwoFingerSwipeLeft = { 
                            viewLifecycleOwner.lifecycleScope.launch {
                                (navigator as? OverflowableNavigator)?.goForward(animated = true)
                            }
                        },
                        onTwoFingerSwipeRight = {
                            viewLifecycleOwner.lifecycleScope.launch {
                                (navigator as? OverflowableNavigator)?.goBackward(animated = true)
                            }
                        }
                    )
            ) {
                // Highlighter Canvas (only visible in Edit Mode)
                if (isEditMode) {
                    HighlighterCanvas(
                        strokes = highlighterState.strokes,
                        currentPath = highlighterState.currentPath,
                        currentColor = highlighterState.currentColor,
                        currentStrokeWidth = highlighterState.getStrokeWidth(),
                        currentBlendMode = highlighterState.getBlendMode()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                content = { Overlay() }
            )
            
            // Edit Mode Floating Toolbar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Mode Toggle FAB (always visible, positioned at bottom right)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    FloatingActionButton(
                        onClick = {
                            isEditMode = !isEditMode
                            prefs.isEditMode = isEditMode
                        },
                        backgroundColor = if (isEditMode) androidx.compose.ui.graphics.Color(0xFF007AFF) else androidx.compose.ui.graphics.Color.White
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Edit else Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = if (isEditMode) "Edit Mode" else "Read Mode",
                            tint = if (isEditMode) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                        )
                    }
                }
                
                // Style Toolbar (only visible in Edit Mode)
                if (isEditMode) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 72.dp) // Leave space for mode toggle FAB
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Style Buttons
                        StyleButton(
                            icon = Icons.Default.Brush,
                            label = "Highlight",
                            isSelected = highlighterState.currentStyle == StylusStyle.HIGHLIGHTER,
                            onClick = { highlighterState.setStyle(StylusStyle.HIGHLIGHTER) }
                        )
                        StyleButton(
                            icon = Icons.Default.Edit,
                            label = "Pen",
                            isSelected = highlighterState.currentStyle == StylusStyle.PEN,
                            onClick = { highlighterState.setStyle(StylusStyle.PEN) }
                        )
                        StyleButton(
                            icon = Icons.Default.Create,
                            label = "Marker",
                            isSelected = highlighterState.currentStyle == StylusStyle.MARKER,
                            onClick = { highlighterState.setStyle(StylusStyle.MARKER) }
                        )
                        
                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(androidx.compose.ui.graphics.Color.LightGray)
                        )
                        
                        // Color Picker
                        listOf(
                            androidx.compose.ui.graphics.Color(0xFFFFEB3B), // Yellow
                            androidx.compose.ui.graphics.Color(0xFF00BCD4), // Cyan
                            androidx.compose.ui.graphics.Color(0xFFE91E63), // Pink
                            androidx.compose.ui.graphics.Color(0xFF4CAF50), // Green
                            androidx.compose.ui.graphics.Color(0xFFFF9800)  // Orange
                        ).forEach { color ->
                            ColorCircle(
                                color = color,
                                isSelected = highlighterState.currentColor.copy(alpha = 1f) == color,
                                onClick = { highlighterState.setColor(color) }
                            )
                        }
                        
                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(androidx.compose.ui.graphics.Color.LightGray)
                        )
                        
                        // Clear Button
                        androidx.compose.material.IconButton(
                            onClick = { highlighterState.clearStrokes() },
                            enabled = highlighterState.strokes.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear",
                                tint = if (highlighterState.strokes.isNotEmpty()) 
                                    androidx.compose.ui.graphics.Color.Black 
                                else 
                                    androidx.compose.ui.graphics.Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                   // TTS is now in the unified settings sheet
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when (menuItem.itemId) {
                         // R.id.tts -> handled in settings
                        R.id.action_settings -> {
                            ReaderSettingsBottomSheet(
                                readerViewModel = model,
                                onSearchClick = { showSearchDialog() }
                            ).show(childFragmentManager, "ReaderSettings")
                            return true
                        }
                    }
                    return false
                }
            },
            viewLifecycleOwner
        )

        model.visualFragmentChannel.receive(viewLifecycleOwner) { event ->
            when (event) {
                is ReaderViewModel.VisualFragmentCommand.ShowPopup ->
                    showFootnotePopup(event.text)
            }
        }
    }

    @Composable
    private fun BoxScope.Overlay() {
        model.tts?.let { tts ->
            TtsControls(
                model = tts,
                onPreferences = {
                    TtsPreferencesBottomSheetDialogFragment()
                        .show(childFragmentManager, "TtsSettings")
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            )
        }

        when (val overlay = activeOverlay) {
            is ActiveOverlay.Dictionary -> DictionaryOverlay(
                text = selectedTextForOverlay,
                initialTab = overlay.tab,
                onDismiss = { activeOverlay = ActiveOverlay.None },
                onSave = { /* TODO: Implement save functionality */ },
                onAddNote = { /* TODO: Implement add note */ },
                onCopy = { /* TODO: Implement copy */ },
                onPronounce = { /* TODO: Implement TTS pronunciation */ }
            )
            is ActiveOverlay.Ai -> AiAssistantOverlay(
                text = selectedTextForOverlay,
                bookTitle = model.publication.metadata.title ?: "Book",
                chapterTitle = "Current Chapter",
                onDismiss = { activeOverlay = ActiveOverlay.None },
                onSummarizeChapter = { /* TODO: Implement chapter summary */ },
                onExplainSymbolism = { /* TODO: Implement symbolism explanation */ },
                onAskQuestion = { question -> /* TODO: Process AI question */ }
            )
            ActiveOverlay.None -> {
                // Animated Top Bar
                AnimatedVisibility(
                    visible = isSystemUiVisible && !isEditMode,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    // Top Bar (Combined)
                    ReaderTopBar(
                        model = model,
                        onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }, // Updated to OnBackPressedDispatcher
                        onTocClick = { 
                            viewLifecycleOwner.lifecycleScope.launch {
                                model.activityChannel.send(ReaderViewModel.ActivityCommand.OpenOutlineRequested)
                            }
                        },
                        onSettingsClick = { 
                            ReaderSettingsBottomSheet(
                                readerViewModel = model,
                                onSearchClick = { showSearchDialog() }
                            ).show(childFragmentManager, "ReaderSettings")
                        },
                        onBookmarkClick = { model.insertBookmark(navigator.currentLocator.value) },
                        modifier = Modifier
                            .background(androidx.compose.ui.graphics.Color.White) // Ensure background for status bar scrim
                            .statusBarsPadding() // Robust padding for status bar ONLY
                    )
                }
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                navigator.currentLocator
                    .onEach { model.saveProgression(it) }
                    .launchIn(this)
            }
        }

        (navigator as? DecorableNavigator)
            ?.addDecorationListener("highlights", decorationListener)

        viewLifecycleOwner.lifecycleScope.launch {
            setupHighlights(viewLifecycleOwner.lifecycleScope)
            setupSearch(viewLifecycleOwner.lifecycleScope)
            setupTts()
            setupSyncFeedback()
        }
    }

    private suspend fun setupHighlights(scope: CoroutineScope) {
        (navigator as? DecorableNavigator)?.let { navigator ->
            model.highlightDecorations
                .onEach { navigator.applyDecorations(it, "highlights") }
                .launchIn(scope)
        }
    }

    private suspend fun setupSearch(scope: CoroutineScope) {
        (navigator as? DecorableNavigator)?.let { navigator ->
            model.searchDecorations
                .onEach { navigator.applyDecorations(it, "search") }
                .launchIn(scope)
        }
    }

    /**
     * Setup text-to-speech observers, if available.
     */
    private suspend fun setupTts() {
        model.tts?.apply {
            events
                .observeWhenStarted(viewLifecycleOwner) { event ->
                    when (event) {
                        is TtsViewModel.Event.OnError -> {
                            showError(event.error.toUserError())
                        }
                        is TtsViewModel.Event.OnMissingVoiceData ->
                            confirmAndInstallTtsVoice(event.language)
                    }
                }

            // Navigate to the currently spoken word.
            // This will automatically turn pages when needed.
            position
                .filterNotNull()
                // Improve performances by throttling the moves to maximum one per second.
                .throttleLatest(1.seconds)
                .observeWhenStarted(viewLifecycleOwner) { locator ->
                    navigator.go(locator, animated = false)
                }

            // Prevent interacting with the publication (including page turns) while the TTS is
            // playing.
            isPlaying
                .observeWhenStarted(viewLifecycleOwner) { isPlaying ->
                    disableTouches = isPlaying
                }

            // Highlight the currently spoken utterance.
            (navigator as? DecorableNavigator)?.let { navigator ->
                highlight
                    .observeWhenStarted(viewLifecycleOwner) { locator ->
                        val decoration = locator?.let {
                            Decoration(
                                id = "tts",
                                locator = it,
                                style = Decoration.Style.Highlight(tint = Color.RED)
                            )
                        }
                        navigator.applyDecorations(listOfNotNull(decoration), "tts")
                    }
            }
        }
    }

    /**
     * Observe sync status and show feedback to the user.
     */
    private fun setupSyncFeedback() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var currentSnackbar: Snackbar? = null
                
                model.syncState.collect { state ->
                    currentSnackbar?.dismiss()
                    
                    when (state) {
                        is BackupManager.SyncState.Syncing -> {
                            currentSnackbar = Snackbar.make(binding.root, "Syncing data to cloud...", Snackbar.LENGTH_INDEFINITE)
                            currentSnackbar.show()
                        }
                        is BackupManager.SyncState.Success -> {
                            Snackbar.make(binding.root, "Sync successful", Snackbar.LENGTH_SHORT).show()
                        }
                        is BackupManager.SyncState.Error -> {
                            Snackbar.make(binding.root, "Sync failed: ${state.message}", Snackbar.LENGTH_LONG).show()
                        }
                        is BackupManager.SyncState.Idle -> {
                            // No-op
                        }
                    }
                }
            }
        }
    }

    private fun showSearchDialog() {
        val activity = activity ?: return
        val view = layoutInflater.inflate(R.layout.popup_note, null, false) // Reusing popup_note for input
        val input = view.findViewById<EditText>(R.id.note)
        input.hint = "Search query..."
        
        AlertDialog.Builder(activity)
            .setTitle("Find in book")
            .setView(view)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString()
                if (query.isNotBlank()) {
                    model.search(query)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Confirms with the user if they want to download the TTS voice data for the given language.
     */
    private suspend fun confirmAndInstallTtsVoice(language: Language) {
        val activity = activity ?: return
        model.tts ?: return

        if (
            activity.confirmDialog(
                getString(
                    R.string.tts_error_language_support_incomplete,
                    language.locale.displayLanguage
                )
            )
        ) {
            AndroidTtsEngine.requestInstallVoice(activity)
        }
    }

    override fun go(locator: Locator, animated: Boolean) {
        model.tts?.stop()
        super.go(locator, animated)
    }

    override fun onDestroyView() {
        (navigator as? DecorableNavigator)?.removeDecorationListener(decorationListener)
        super.onDestroyView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        setMenuVisibility(!hidden)
        requireActivity().invalidateOptionsMenu()
    }

    // DecorableNavigator.Listener

    private val decorationListener by lazy { DecorationListener() }

    inner class DecorationListener : DecorableNavigator.Listener {
        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
            val decoration = event.decoration
            // We stored the highlight's database ID in the `Decoration.extras` map, for
            // easy retrieval. You can store arbitrary information in the map.
            val id = (decoration.extras["id"] as Long)
                .takeIf { it > 0 } ?: return false

            // This listener will be called when tapping on any of the decorations in the
            // "highlights" group. To differentiate between the page margin icon and the
            // actual highlight, we check for the type of `decoration.style`. But you could
            // use any other information, including the decoration ID or the extras bundle.
            if (decoration.style is DecorationStyleAnnotationMark) {
                showAnnotationPopup(id)
            } else {
                event.rect?.let { rect ->
                    val isUnderline = (decoration.style is Decoration.Style.Underline)
                    showHighlightPopup(
                        rect,
                        style = if (isUnderline) {
                            Highlight.Style.UNDERLINE
                        } else {
                            Highlight.Style.HIGHLIGHT
                        },
                        highlightId = id
                    )
                }
            }

            return true
        }
    }

    // Highlights

    private var popupWindow: PopupWindow? = null
    private var mode: ActionMode? = null

    // Available tint colors for highlight and underline annotations.
    private val highlightTints = mapOf</*@IdRes*/ Int, /*@ColorInt*/ Int>(
        R.id.red to Color.rgb(247, 124, 124),
        R.id.green to Color.rgb(173, 247, 123),
        R.id.blue to Color.rgb(124, 198, 247),
        R.id.yellow to Color.rgb(249, 239, 125),
        R.id.purple to Color.rgb(182, 153, 255)
    )

    val customSelectionActionModeCallback: ActionMode.Callback by lazy { SelectionActionModeCallback() }

    private inner class SelectionActionModeCallback : BaseActionModeCallback() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_action_mode, menu)
            if (navigator is DecorableNavigator) {
                menu.findItem(R.id.highlight).isVisible = true
                menu.findItem(R.id.underline).isVisible = true
                menu.findItem(R.id.note).isVisible = true
                menu.findItem(R.id.define).isVisible = true
                menu.findItem(R.id.translate).isVisible = true
                menu.findItem(R.id.ask_ai).isVisible = true
            }
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.highlight -> showHighlightPopupWithStyle(Highlight.Style.HIGHLIGHT)
                R.id.underline -> showHighlightPopupWithStyle(Highlight.Style.UNDERLINE)
                R.id.note -> showAnnotationPopup()
                R.id.define -> {
                    showDictionaryOverlay(0)
                    mode.finish()
                    return true
                }
                R.id.translate -> {
                    showDictionaryOverlay(1)
                    mode.finish()
                    return true
                }
                R.id.ask_ai -> {
                    showAiOverlay()
                    mode.finish()
                    return true
                }
                else -> return false
            }

            mode.finish()
            return true
        }
    }

    private fun showHighlightPopupWithStyle(style: Highlight.Style) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Get the rect of the current selection to know where to position the highlight
            // popup.
            (navigator as? SelectableNavigator)?.currentSelection()?.rect?.let { selectionRect ->
                showHighlightPopup(selectionRect, style)
            }
        }
    }

    private fun showHighlightPopup(rect: RectF, style: Highlight.Style, highlightId: Long? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (popupWindow?.isShowing == true) return@launch

            model.activeHighlightId.value = highlightId

            val isReverse = (rect.top > 60)
            val popupView = layoutInflater.inflate(
                if (isReverse) R.layout.view_action_mode_reverse else R.layout.view_action_mode,
                null,
                false
            )
            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            popupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                isFocusable = true
                setOnDismissListener {
                    model.activeHighlightId.value = null
                }
            }

            val x = rect.left
            val y = if (isReverse) rect.top else rect.bottom + rect.height()

            popupWindow?.showAtLocation(popupView, Gravity.NO_GRAVITY, x.toInt(), y.toInt())

            val highlight = highlightId?.let { model.highlightById(it) }
            popupView.run {
                findViewById<View>(R.id.notch).run {
                    setX(rect.left * 2)
                }

                fun selectTint(view: View) {
                    val tint = highlightTints[view.id] ?: return
                    selectHighlightTint(highlightId, style, tint)
                }

                findViewById<View>(R.id.red).setOnClickListener(::selectTint)
                findViewById<View>(R.id.green).setOnClickListener(::selectTint)
                findViewById<View>(R.id.blue).setOnClickListener(::selectTint)
                findViewById<View>(R.id.yellow).setOnClickListener(::selectTint)
                findViewById<View>(R.id.purple).setOnClickListener(::selectTint)

                findViewById<View>(R.id.annotation).setOnClickListener {
                    popupWindow?.dismiss()
                    showAnnotationPopup(highlightId)
                }
                findViewById<View>(R.id.del).run {
                    visibility = if (highlight != null) View.VISIBLE else View.GONE
                    setOnClickListener {
                        highlightId?.let {
                            model.deleteHighlight(highlightId)
                        }
                        popupWindow?.dismiss()
                        mode?.finish()
                    }
                }
            }
        }
    }

    private fun selectHighlightTint(
        highlightId: Long? = null,
        style: Highlight.Style,
        @ColorInt tint: Int,
    ) =
        viewLifecycleOwner.lifecycleScope.launch {
            if (highlightId != null) {
                model.updateHighlightStyle(highlightId, style, tint)
            } else {
                (navigator as? SelectableNavigator)?.let { navigator ->
                    navigator.currentSelection()?.let { selection ->
                        model.addHighlight(
                            locator = selection.locator,
                            style = style,
                            tint = tint
                        )
                    }
                    navigator.clearSelection()
                }
            }

            popupWindow?.dismiss()
            mode?.finish()
        }

    private fun showAnnotationPopup(highlightId: Long? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            val activity = activity ?: return@launch
            val view = layoutInflater.inflate(R.layout.popup_note, null, false)
            val note = view.findViewById<EditText>(R.id.note)
            val alert = AlertDialog.Builder(activity)
                .setView(view)
                .create()

            fun dismiss() {
                alert.dismiss()
                mode?.finish()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(
                        note.applicationWindowToken,
                        InputMethodManager.HIDE_NOT_ALWAYS
                    )
            }

            with(view) {
                val highlight = highlightId?.let { model.highlightById(it) }
                if (highlight != null) {
                    note.setText(highlight.annotation)
                    findViewById<View>(R.id.sidemark).setBackgroundColor(highlight.tint)
                    findViewById<TextView>(R.id.select_text).text =
                        highlight.locator.text.highlight

                    findViewById<TextView>(R.id.positive).setOnClickListener {
                        val text = note.text.toString()
                        model.updateHighlightAnnotation(highlight.id, annotation = text)
                        dismiss()
                    }
                } else {
                    val tint = highlightTints.values.random()
                    findViewById<View>(R.id.sidemark).setBackgroundColor(tint)
                    val navigator =
                        navigator as? SelectableNavigator ?: return@launch
                    val selection = navigator.currentSelection() ?: return@launch
                    navigator.clearSelection()
                    findViewById<TextView>(R.id.select_text).text =
                        selection.locator.text.highlight

                    findViewById<TextView>(R.id.positive).setOnClickListener {
                        model.addHighlight(
                            locator = selection.locator,
                            style = Highlight.Style.HIGHLIGHT,
                            tint = tint,
                            annotation = note.text.toString()
                        )
                        dismiss()
                    }
                }

                findViewById<TextView>(R.id.negative).setOnClickListener {
                    dismiss()
                }
            }

            alert.show()
        }
    }

    private fun showFootnotePopup(
        text: CharSequence,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Initialize a new instance of LayoutInflater service
            val inflater =
                requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            // Inflate the custom layout/view
            val customView = inflater.inflate(R.layout.popup_footnote, null)

            // Initialize a new instance of popup window
            val mPopupWindow = PopupWindow(
                customView,
                ListPopupWindow.WRAP_CONTENT,
                ListPopupWindow.WRAP_CONTENT
            )
            mPopupWindow.isOutsideTouchable = true
            mPopupWindow.isFocusable = true

            // Set an elevation value for popup window
            // Call requires API level 21
            mPopupWindow.elevation = 5.0f

            val textView = customView.findViewById(R.id.footnote) as TextView
            textView.text = text

            // Get a reference for the custom view close button
            val closeButton = customView.findViewById(R.id.ib_close) as ImageButton

            // Set a click listener for the popup window close button
            closeButton.setOnClickListener {
                // Dismiss the popup window
                mPopupWindow.dismiss()
            }

            // Finally, show the popup window at the center location of root relative layout
            // FIXME: should anchor on noteref and be scrollable if the note is too long.
            mPopupWindow.showAtLocation(
                requireView(),
                Gravity.CENTER,
                0,
                0
            )
        }
    }

    fun updateSystemUiVisibility() {
        if (navigatorFragment.isHidden) {
            requireActivity().showSystemUi()
        } else {
            requireActivity().hideSystemUi()
        }

        requireView().requestApplyInsets()
    }

    private fun updateSystemUiPadding(container: View, insets: WindowInsets) {
        if (navigatorFragment.isHidden) {
            container.padSystemUi(insets, requireActivity() as AppCompatActivity)
        } else {
            container.clearPadding()
        }
    }

    private fun showDictionaryOverlay(tab: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val text = (navigator as? SelectableNavigator)?.currentSelection()?.locator?.text?.highlight
            if (!text.isNullOrBlank()) {
                selectedTextForOverlay = text
                activeOverlay = ActiveOverlay.Dictionary(tab)
            }
        }
    }

    private fun showAiOverlay() {
        viewLifecycleOwner.lifecycleScope.launch {
            val text = (navigator as? SelectableNavigator)?.currentSelection()?.locator?.text?.highlight
            if (!text.isNullOrBlank()) {
                selectedTextForOverlay = text
                activeOverlay = ActiveOverlay.Ai
            }
        }
    }
}

/**
 * Decoration Style for a page margin icon.
 *
 * This is an example of a custom Decoration Style declaration.
 */
@Parcelize
data class DecorationStyleAnnotationMark(@ColorInt val tint: Int) : Decoration.Style

/**
 * Decoration Style for a page number label.
 *
 * This is an example of a custom Decoration Style declaration.
 *
 * @param label Page number label as declared in the `page-list` link object.
 */
@Parcelize
data class DecorationStylePageNumber(val label: String) : Decoration.Style

/**
 * Style selection button for the drawing toolbar
 */
@Composable
private fun StyleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material.IconButton(
        onClick = onClick,
        modifier = androidx.compose.ui.Modifier
            .size(36.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                if (isSelected) androidx.compose.ui.graphics.Color(0xFF007AFF).copy(alpha = 0.15f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
    ) {
        androidx.compose.material.Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) androidx.compose.ui.graphics.Color(0xFF007AFF)
                   else androidx.compose.ui.graphics.Color.Gray,
            modifier = androidx.compose.ui.Modifier.size(20.dp)
        )
    }
}

/**
 * Color picker circle for the drawing toolbar
 */
@Composable
private fun ColorCircle(
    color: androidx.compose.ui.graphics.Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .size(28.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.Transparent,
                shape = androidx.compose.foundation.shape.CircleShape
            )
            .clickable(onClick = onClick)
    )
}
