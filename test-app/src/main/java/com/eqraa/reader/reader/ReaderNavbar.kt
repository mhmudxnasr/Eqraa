package com.eqraa.reader.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eqraa.reader.utils.extensions.asStateWhenStarted
import com.eqraa.reader.data.ReadingSyncManager
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.ui.draw.rotate

// Minimalist Design Constants
private val BarBackground = Color(0xFFFFFFFF) // Pure White
private val TextColor = Color(0xFF000000) // Pure Black
private val BorderColor = Color(0xFFE0E0E0)
private val IconColor = Color(0xFF000000)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderTopBar(
    model: ReaderViewModel,
    onBackClick: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onStatsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locator by model.currentLocator.asStateWhenStarted()
    val currentPosition by model.currentPosition.asStateWhenStarted(null)
    val totalPositions by model.totalPositions.asStateWhenStarted()
    val title = model.publication.metadata.title ?: ""
    val syncStatus by model.readingSyncStatus.asStateWhenStarted(ReadingSyncManager.SyncStatus.Idle)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BarBackground,
        shadowElevation = 3.dp 
    ) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back & Table of Contents
                Row(
                    modifier = Modifier.width(100.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = IconColor
                        )
                    }
                    IconButton(onClick = onTocClick) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Table of Contents",
                            tint = IconColor
                        )
                    }
                }

                // Center: Title + Page Number
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        color = TextColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // Page Number Display
                    val pageText = if (currentPosition != null && totalPositions > 0) {
                        "$currentPosition / $totalPositions" 
                    } else {
                        val progress = ((locator?.locations?.totalProgression ?: 0.0) * 100).toInt()
                        "$progress%"
                    }
                    
                    Text(
                        text = pageText,
                        color = TextColor.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    
                    // Brilliant Sync Status Indicator
                    SyncStatusIndicator(status = syncStatus)
                }

                // Right: Settings & Bookmark
                Row(
                    modifier = Modifier.width(140.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onStatsClick) {
                       Icon(
                           Icons.Default.DateRange,
                           contentDescription = "Statistics",
                           tint = IconColor
                       ) 
                    }
                    IconButton(onClick = onBookmarkClick) {
                        // Burst Animation Logic
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        
                        // Scale anim on press/click
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.8f else 1.2f, // Slight pop
                             animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
                             label = "bookmark_pop"
                        )
                        
                        Icon(
                            Icons.Default.BookmarkBorder, 
                            contentDescription = "Bookmark", 
                            tint = IconColor,
                            modifier = Modifier.scale(scale)
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings, 
                            contentDescription = "Settings", 
                            tint = IconColor
                        )
                    }
                }
            }
            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
        }
    }
}

@Composable
fun SyncStatusIndicator(status: ReadingSyncManager.SyncStatus) {
    AnimatedVisibility(
        visible = status !is ReadingSyncManager.SyncStatus.Idle,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
            
            LaunchedEffect(status) {
                if (status is ReadingSyncManager.SyncStatus.Syncing) {
                    rotation.animateTo(
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing)
                        )
                    )
                } else {
                    rotation.snapTo(0f)
                }
            }
            
            val (text, icon, color) = when (status) {
                is ReadingSyncManager.SyncStatus.Syncing -> Triple("Syncing...", Icons.Default.Refresh, Color.Gray)
                is ReadingSyncManager.SyncStatus.Success -> Triple("Saved to Cloud", Icons.Default.CloudDone, Color(0xFF4CAF50)) // Green
                is ReadingSyncManager.SyncStatus.Failed -> Triple("Sync Failed", Icons.Default.CloudOff, Color.Red)
                is ReadingSyncManager.SyncStatus.Offline -> Triple("Offline Mode", Icons.Default.CloudOff, Color.Gray)
                else -> Triple("", Icons.Default.CloudDone, Color.Transparent)
            }
            
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .size(10.dp)
                        .then(if (status is ReadingSyncManager.SyncStatus.Syncing) Modifier.rotate(rotation.value) else Modifier)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = text,
                    color = color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
