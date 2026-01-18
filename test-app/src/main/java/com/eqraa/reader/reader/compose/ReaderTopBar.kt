package com.eqraa.reader.reader.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eqraa.reader.reader.ReaderViewModel

@Composable
fun ReaderTopBar(
    model: ReaderViewModel,
    onBackClick: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalReaderTheme.current
    
    TopAppBar(
        backgroundColor = theme.surface.copy(alpha = theme.surfaceAlpha),
        contentColor = theme.text,
        elevation = 0.dp,
        modifier = modifier,
        title = {},
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onTocClick) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Table of Contents")
            }
            IconButton(onClick = onStatsClick) {
                Icon(Icons.Default.BarChart, contentDescription = "Stats")
            }
            IconButton(onClick = onBookmarkClick) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmark")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    )
}
