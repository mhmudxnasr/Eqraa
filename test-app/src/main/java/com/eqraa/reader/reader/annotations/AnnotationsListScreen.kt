/*
 * Annotations List Screen - Premium Edition
 * 
 * A unified Compose screen for viewing Bookmarks, Highlights, and Notes.
 * Features: Swipe-to-delete, search, staggered animations, premium cards.
 */

package com.eqraa.reader.reader.annotations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eqraa.reader.data.model.Bookmark
import com.eqraa.reader.data.model.Highlight
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// Note: MonoColors and HighlightIntensity are defined in AnnotationBottomSheet.kt

enum class AnnotationTab(val title: String, val icon: ImageVector) {
    BOOKMARKS("Bookmarks", Icons.Default.Bookmark),
    HIGHLIGHTS("Highlights", Icons.Default.Edit),
    NOTES("Notes", Icons.Default.NoteAlt)
}

/**
 * Unified screen for viewing all annotations with premium UX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsListScreen(
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkDelete: (Bookmark) -> Unit,
    onHighlightClick: (Highlight) -> Unit,
    onHighlightDelete: (Highlight) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(AnnotationTab.BOOKMARKS) }
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MonoColors.OffWhite)
    ) {
        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Premium Pill Tab Row
        PremiumAnnotationTabs(
            selectedTab = selectedTab,
            bookmarkCount = bookmarks.size,
            highlightCount = highlights.size,
            noteCount = highlights.count { it.annotation.isNotBlank() },
            onTabSelected = { selectedTab = it }
        )

        // Filtered Content
        val filteredBookmarks = remember(bookmarks, searchQuery) {
            if (searchQuery.isBlank()) bookmarks
            else bookmarks.filter { 
                it.resourceTitle.contains(searchQuery, ignoreCase = true)
            }
        }
        
        val filteredHighlights = remember(highlights, searchQuery) {
            if (searchQuery.isBlank()) highlights
            else highlights.filter { 
                it.text.highlight?.contains(searchQuery, ignoreCase = true) == true ||
                it.annotation.contains(searchQuery, ignoreCase = true)
            }
        }

        // Content with smooth crossfade
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(tween(200)) + slideInHorizontally { it / 4 } togetherWith
                fadeOut(tween(150))
            },
            label = "TabContent"
        ) { tab ->
            when (tab) {
                AnnotationTab.BOOKMARKS -> BookmarksList(
                    bookmarks = filteredBookmarks,
                    onClick = onBookmarkClick,
                    onDelete = onBookmarkDelete
                )
                AnnotationTab.HIGHLIGHTS -> HighlightsList(
                    highlights = filteredHighlights,
                    onClick = onHighlightClick,
                    onDelete = onHighlightDelete
                )
                AnnotationTab.NOTES -> NotesList(
                    highlights = filteredHighlights.filter { it.annotation.isNotBlank() },
                    onClick = onHighlightClick,
                    onDelete = onHighlightDelete
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MonoColors.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MonoColors.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = MonoColors.Black
                ),
                cursorBrush = SolidColor(MonoColors.Black),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search annotations...",
                                fontSize = 14.sp,
                                color = MonoColors.LightGray
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MonoColors.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumAnnotationTabs(
    selectedTab: AnnotationTab,
    bookmarkCount: Int,
    highlightCount: Int,
    noteCount: Int,
    onTabSelected: (AnnotationTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnnotationTab.entries.forEach { tab ->
            val count = when (tab) {
                AnnotationTab.BOOKMARKS -> bookmarkCount
                AnnotationTab.HIGHLIGHTS -> highlightCount
                AnnotationTab.NOTES -> noteCount
            }
            val isSelected = selectedTab == tab
            
            PillTab(
                title = tab.title,
                count = count,
                icon = tab.icon,
                isSelected = isSelected,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PillTab(
    title: String,
    count: Int,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) MonoColors.Black else MonoColors.White,
        animationSpec = tween(200),
        label = "pillBg"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (isSelected) MonoColors.White else MonoColors.Gray,
        animationSpec = tween(200),
        label = "pillText"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.95f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pillScale"
    )
    
    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = animatedBg,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = animatedTextColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = animatedTextColor
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = animatedTextColor.copy(alpha = 0.8f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksList(
    bookmarks: List<Bookmark>,
    onClick: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit
) {
    if (bookmarks.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.BookmarkBorder,
            message = "No bookmarks yet",
            hint = "Tap the bookmark icon while reading to save your place"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(bookmarks, key = { _, b -> b.id ?: 0 }) { index, bookmark ->
                AnimatedCard(index = index) {
                    SwipeableCard(
                        onDelete = { onDelete(bookmark) }
                    ) {
                        BookmarkCardContent(
                            bookmark = bookmark,
                            onClick = { onClick(bookmark) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightsList(
    highlights: List<Highlight>,
    onClick: (Highlight) -> Unit,
    onDelete: (Highlight) -> Unit
) {
    if (highlights.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Edit,
            message = "No highlights yet",
            hint = "Select text while reading to create highlights"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(highlights, key = { _, h -> h.id }) { index, highlight ->
                AnimatedCard(index = index) {
                    SwipeableCard(
                        onDelete = { onDelete(highlight) }
                    ) {
                        HighlightCardContent(
                            highlight = highlight,
                            onClick = { onClick(highlight) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesList(
    highlights: List<Highlight>,
    onClick: (Highlight) -> Unit,
    onDelete: (Highlight) -> Unit
) {
    if (highlights.isEmpty()) {
        EmptyState(
            icon = Icons.Default.NoteAlt,
            message = "No notes yet",
            hint = "Add notes to your highlights while reading"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(highlights, key = { _, h -> h.id }) { index, highlight ->
                AnimatedCard(index = index) {
                    SwipeableCard(
                        onDelete = { onDelete(highlight) }
                    ) {
                        NoteCardContent(
                            highlight = highlight,
                            onClick = { onClick(highlight) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedCard(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.toLong() * 50)
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(
            initialOffsetY = { 30 },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + scaleIn(initialScale = 0.95f, animationSpec = tween(300))
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCard(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MonoColors.DeleteRed)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MonoColors.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        content = { content() }
    )
}

@Composable
private fun BookmarkCardContent(
    bookmark: Bookmark,
    onClick: () -> Unit
) {
    PremiumCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bookmark icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MonoColors.OffWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MonoColors.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.resourceTitle.ifBlank { "Bookmark" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MonoColors.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatProgress(bookmark.locator.locations.totalProgression),
                    fontSize = 13.sp,
                    color = MonoColors.Gray
                )
            }
            
            Text(
                text = formatRelativeTime(bookmark.creation ?: 0L),
                fontSize = 11.sp,
                color = MonoColors.LightGray
            )
        }
    }
}

@Composable
private fun HighlightCardContent(
    highlight: Highlight,
    onClick: () -> Unit
) {
    PremiumCard(onClick = onClick) {
        Column {
            // Chapter title
            Text(
                text = highlight.title ?: "Highlight",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MonoColors.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Quote with intensity bar
            Row {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(40.dp)
                        .background(
                            MonoColors.Black.copy(
                                alpha = if (highlight.style == Highlight.Style.UNDERLINE) 1f else 0.3f
                            ),
                            RoundedCornerShape(1.5.dp)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "\"${highlight.text.highlight ?: ""}\"",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    color = MonoColors.DarkGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = formatRelativeTime(highlight.creation ?: 0L),
                fontSize = 11.sp,
                color = MonoColors.LightGray
            )
        }
    }
}

@Composable
private fun NoteCardContent(
    highlight: Highlight,
    onClick: () -> Unit
) {
    PremiumCard(onClick = onClick) {
        Column {
            // Note icon + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NoteAlt,
                    contentDescription = null,
                    tint = MonoColors.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = highlight.title ?: "Note",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MonoColors.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Note content
            Text(
                text = highlight.annotation,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MonoColors.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Quote reference
            if (!highlight.text.highlight.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\"${highlight.text.highlight}\"",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = MonoColors.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = formatRelativeTime(highlight.creation ?: 0L),
                fontSize = 11.sp,
                color = MonoColors.LightGray
            )
        }
    }
}

@Composable
private fun PremiumCard(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = MonoColors.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    message: String,
    hint: String
) {
    // Animated entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "emptyScale"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale)
        ) {
            // Pulsating icon
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulse by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAnim"
            )
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulse)
                    .background(MonoColors.OffWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MonoColors.LightGray,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = message,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MonoColors.DarkGray
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = hint,
                fontSize = 13.sp,
                color = MonoColors.Gray
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

private fun formatProgress(progression: Double?): String {
    if (progression == null) return ""
    return "${(progression * 100).toInt()}% through book"
}
