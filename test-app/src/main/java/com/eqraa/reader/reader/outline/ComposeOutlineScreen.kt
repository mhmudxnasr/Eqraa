/*
 * Compose Outline Screen - Premium Edition
 * 
 * A unified Compose implementation of the Outline screen with premium UX.
 * Features: Smooth animations, polished visuals, intuitive navigation.
 */

package com.eqraa.reader.reader.outline

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eqraa.reader.data.model.Bookmark
import com.eqraa.reader.data.model.Highlight
import com.eqraa.reader.reader.annotations.AnnotationsListScreen
import com.eqraa.reader.reader.annotations.MonoColors
import org.readium.r2.shared.publication.Link

enum class OutlineTab(val title: String, val icon: ImageVector) {
    CONTENTS("Contents", Icons.AutoMirrored.Filled.List),
    ANNOTATIONS("Annotations", Icons.Default.Bookmark)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeOutlineScreen(
    title: String,
    tableOfContents: List<Link>,
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    onLinkSelected: (Link) -> Unit,
    onBookmarkSelected: (Bookmark) -> Unit,
    onBookmarkDelete: (Bookmark) -> Unit,
    onHighlightSelected: (Highlight) -> Unit,
    onHighlightDelete: (Highlight) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(OutlineTab.CONTENTS) }
    
    // Flatten TOC structure for display
    val flattenedToc = remember(tableOfContents) {
        flattenLinks(tableOfContents)
    }

    Scaffold(
        topBar = {
            // Premium Top Bar with subtle gradient
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MonoColors.White,
                shadowElevation = 2.dp
            ) {
                Column {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close",
                                tint = MonoColors.Black
                            )
                        }
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonoColors.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${flattenedToc.size} chapters · ${bookmarks.size} bookmarks",
                                fontSize = 12.sp,
                                color = MonoColors.Gray,
                                maxLines = 1
                            )
                        }
                    }
                    
                    // Premium Tab Row
                    PremiumTabRow(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        },
        containerColor = MonoColors.OffWhite
    ) { paddingValues ->
        // Content with smooth crossfade
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.padding(paddingValues),
            transitionSpec = {
                fadeIn(animationSpec = tween(250)) + 
                slideInVertically(initialOffsetY = { 20 }) togetherWith
                fadeOut(animationSpec = tween(200))
            },
            label = "OutlineContent"
        ) { tab ->
            when (tab) {
                OutlineTab.CONTENTS -> PremiumContentsList(
                    links = flattenedToc,
                    onLinkSelected = onLinkSelected
                )
                OutlineTab.ANNOTATIONS -> AnnotationsListScreen(
                    bookmarks = bookmarks,
                    highlights = highlights,
                    onBookmarkClick = onBookmarkSelected,
                    onBookmarkDelete = onBookmarkDelete,
                    onHighlightClick = onHighlightSelected,
                    onHighlightDelete = onHighlightDelete
                )
            }
        }
    }
}

@Composable
private fun PremiumTabRow(
    selectedTab: OutlineTab,
    onTabSelected: (OutlineTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlineTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            val animatedBackground by animateColorAsState(
                targetValue = if (isSelected) MonoColors.Black else Color.Transparent,
                animationSpec = tween(200),
                label = "tabBg"
            )
            val animatedTextColor by animateColorAsState(
                targetValue = if (isSelected) MonoColors.White else MonoColors.Gray,
                animationSpec = tween(200),
                label = "tabText"
            )
            
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(tab) },
                color = animatedBackground,
                shape = RoundedCornerShape(12.dp),
                border = if (!isSelected) ButtonDefaults.outlinedButtonBorder else null
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = animatedTextColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tab.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = animatedTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumContentsList(
    links: List<Pair<Int, Link>>,
    onLinkSelected: (Link) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(links) { index, (indentation, link) ->
            // Staggered entrance animation
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index.toLong() * 30)
                visible = true
            }
            
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(300)) + slideInVertically(
                    initialOffsetY = { 20 },
                    animationSpec = tween(300)
                )
            ) {
                PremiumTocItem(
                    title = link.title ?: "",
                    indentation = indentation,
                    isChapter = indentation == 0,
                    onClick = { onLinkSelected(link) }
                )
            }
        }
    }
}

@Composable
private fun PremiumTocItem(
    title: String,
    indentation: Int,
    isChapter: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indentation * 20).dp)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        color = if (isChapter) MonoColors.White else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = if (isChapter) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent bar for chapters
            if (isChapter) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .background(
                            MonoColors.Black,
                            RoundedCornerShape(1.5.dp)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Text(
                text = title,
                fontSize = if (isChapter) 15.sp else 14.sp,
                fontWeight = if (isChapter) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isChapter) MonoColors.Black else MonoColors.DarkGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Helper to flatten TOC hierarchy with indentation levels
private fun flattenLinks(links: List<Link>, level: Int = 0): List<Pair<Int, Link>> {
    val result = mutableListOf<Pair<Int, Link>>()
    for (link in links) {
        result.add(level to link)
        if (link.children.isNotEmpty()) {
            result.addAll(flattenLinks(link.children, level + 1))
        }
    }
    return result
}
