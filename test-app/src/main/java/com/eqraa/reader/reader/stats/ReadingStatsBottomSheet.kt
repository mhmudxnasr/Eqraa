/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.reader.stats

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.eqraa.reader.reader.ReaderViewModel
import com.eqraa.reader.utils.extensions.asStateWhenStarted
import java.util.concurrent.TimeUnit

class ReadingStatsBottomSheet(
    private val readerViewModel: ReaderViewModel
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ReadingStatsScreen(
                    viewModel = readerViewModel,
                    onClose = { dismiss() }
                )
            }
        }
    }
}

@Composable
fun ReadingStatsScreen(
    viewModel: ReaderViewModel,
    onClose: () -> Unit
) {
    val totalTimeMs by viewModel.totalReadingTime.asStateWhenStarted(initialValue = 0L)
    val streak by viewModel.currentStreak.collectAsState(initial = 0)
    val weeklyActivity by viewModel.weeklyActivity.asStateWhenStarted(initialValue = emptyList<Long>())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.LightGray)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Reading Stats",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Key Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard(
                icon = Icons.Default.LocalFireDepartment,
                iconTint = Color(0xFFFF5722),
                label = "Current Streak",
                value = "$streak Days"
            )
            
            StatCard(
                icon = Icons.Default.Timer,
                iconTint = Color(0xFF2196F3),
                label = "Total Time",
                value = formatDuration(totalTimeMs)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Weekly Chart
        Text(
            text = "Last 7 Days",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        WeeklyChart(activity = weeklyActivity)
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    delay: Int = 0
) {
    // Scale animation on entrance
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "stat_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, delayMillis = delay),
        label = "stat_alpha"
    )
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(140.dp)
            .padding(8.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
fun WeeklyChart(activity: List<Long>) {
    if (activity.isEmpty()) return

    val maxVal = activity.maxOrNull()?.toFloat() ?: 1f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        activity.forEachIndexed { index, value ->
            val targetHeight = if (maxVal > 0) (value / maxVal).coerceIn(0.1f, 1f) else 0.1f
            
            // Animated height with staggered delay
            var animatedHeight by remember { mutableStateOf(0f) }
            val animatedValue by animateFloatAsState(
                targetValue = animatedHeight,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
                label = "bar_height_$index"
            )
            
            LaunchedEffect(targetHeight) {
                kotlinx.coroutines.delay((index * 80).toLong()) // Stagger by 80ms per bar
                animatedHeight = targetHeight
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(animatedValue)
                    .background(
                        if (value > 0) Color(0xFF4CAF50) else Color(0xFFE0E0E0),
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                    )
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(millis)
    val hours = TimeUnit.MILLISECONDS.toHours(millis) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

