package com.eqraa.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
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

// Minimalist Design Constants
private val BarBackground = Color(0xFFFFFFFF) // Pure White
private val TextColor = Color(0xFF000000) // Pure Black
private val BorderColor = Color(0xFFE0E0E0)
private val IconColor = Color(0xFF000000)

@Composable
fun ReaderTopBar(
    model: ReaderViewModel,
    onBackClick: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locator by model.currentLocator.asStateWhenStarted()
    val currentPosition by model.currentPosition.asStateWhenStarted(null)
    val totalPositions by model.totalPositions.asStateWhenStarted()
    val title = model.publication.metadata.title ?: ""

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BarBackground,
        shadowElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp), // Slightly reduced vertical padding for tighter look
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back & Table of Contents
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, // Updated to AutoMirrored
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        color = TextColor,
                        fontSize = 15.sp, // Slightly larger
                        fontWeight = FontWeight.SemiBold, // Clean bold
                        fontFamily = FontFamily.Serif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
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
                        color = TextColor.copy(alpha = 0.6f), // Slightly more subtle
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                // Right: Settings & Bookmark
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBookmarkClick) {
                        Icon(
                            Icons.Default.BookmarkBorder, 
                            contentDescription = "Bookmark", 
                            tint = IconColor
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
            HorizontalDivider(color = BorderColor, thickness = 1.dp) // Updated from Divider
        }
    }
}

