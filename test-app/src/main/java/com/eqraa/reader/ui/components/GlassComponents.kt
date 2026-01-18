/*
 * Glassmorphism UI Components
 * Premium frosted glass effects for monochrome theme
 */

package com.eqraa.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eqraa.reader.ui.theme.MonochromeTheme

/**
 * Glass Surface - Premium glassmorphism container
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    theme: MonochromeTheme,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(theme.glassSurface)
            .border(
                width = theme.glassStrokeWidth,
                color = theme.glassStroke,
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        content()
    }
}

/**
 * Glass Card - Elevated glassmorphic card
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    theme: MonochromeTheme,
    cornerRadius: Dp = 20.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        theme = theme,
        cornerRadius = cornerRadius
    ) {
        Column(
            modifier = Modifier.padding(padding)
        ) {
            content()
        }
    }
}

/**
 * Glass Button - Interactive glassmorphic button
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    theme: MonochromeTheme,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)),
        color = if (enabled) theme.glassSurface else theme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = theme.glassStrokeWidth,
                    color = theme.glassStroke,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            content()
        }
    }
}

/**
 * Glass Chip - Compact glassmorphic chip/tag
 */
@Composable
fun GlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    theme: MonochromeTheme,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp)),
        color = if (selected) theme.accent.copy(alpha = 0.12f) else theme.glassSurface,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = if (selected) 1.5.dp else theme.glassStrokeWidth,
                    color = if (selected) theme.accent else theme.glassStroke,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            content()
        }
    }
}

/**
 * Glass Toolbar - Glassmorphic toolbar overlay
 */
@Composable
fun GlassToolbar(
    modifier: Modifier = Modifier,
    theme: MonochromeTheme,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
        color = theme.glassSurface,
        shadowElevation = theme.elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

/**
 * Glass Bottom Sheet - Glassmorphic bottom sheet container
 */
@Composable
fun GlassBottomSheet(
    modifier: Modifier = Modifier,
    theme: MonochromeTheme,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = theme.glassSurface,
        shadowElevation = theme.elevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(theme.divider)
                    .align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            content()
        }
    }
}
