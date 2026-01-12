/*
 * Sync Status Indicator
 *
 * Compose UI component showing sync status with visual feedback.
 */

package org.readium.r2.testapp.ui.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class representing sync status for display.
 */
data class SyncStatusInfo(
    val state: State,
    val pendingCount: Int = 0,
    val lastSyncTime: Long? = null,
    val message: String? = null
) {
    enum class State {
        SYNCED,     // ✅ All synced
        SYNCING,    // 🔄 Currently syncing
        PENDING,    // ⏳ Has pending changes
        OFFLINE,    // 📴 No network
        ERROR       // ❌ Sync error
    }
}

/**
 * Compact sync status icon for toolbar.
 */
@Composable
fun SyncStatusIcon(
    status: SyncStatusInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (status.state) {
            SyncStatusInfo.State.SYNCED -> {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Synced",
                    tint = Color(0xFF4CAF50), // Green
                    modifier = Modifier.size(24.dp)
                )
            }
            SyncStatusInfo.State.SYNCING -> {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Syncing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation)
                )
            }
            SyncStatusInfo.State.PENDING -> {
                BadgedBox(
                    badge = {
                        if (status.pendingCount > 0) {
                            Badge {
                                Text(status.pendingCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Pending sync",
                        tint = Color(0xFFFFA726), // Orange
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            SyncStatusInfo.State.OFFLINE -> {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Offline",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
            SyncStatusInfo.State.ERROR -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Sync error",
                    tint = Color(0xFFE53935), // Red
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Expanded sync status card with details.
 */
@Composable
fun SyncStatusCard(
    status: SyncStatusInfo,
    onRetryClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SyncStatusIcon(status = status, onClick = {})
                    Text(
                        text = when (status.state) {
                            SyncStatusInfo.State.SYNCED -> "All Synced"
                            SyncStatusInfo.State.SYNCING -> "Syncing..."
                            SyncStatusInfo.State.PENDING -> "${status.pendingCount} Pending"
                            SyncStatusInfo.State.OFFLINE -> "Offline"
                            SyncStatusInfo.State.ERROR -> "Sync Error"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            // Last sync time
            if (status.lastSyncTime != null) {
                val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                Text(
                    text = "Last synced: ${formatter.format(Date(status.lastSyncTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Error message
            if (status.state == SyncStatusInfo.State.ERROR && status.message != null) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            // Action buttons
            if (status.state == SyncStatusInfo.State.ERROR || status.state == SyncStatusInfo.State.PENDING) {
                Button(
                    onClick = onRetryClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sync Now")
                }
            }
        }
    }
}

/**
 * Floating sync status indicator that can be shown as an overlay.
 */
@Composable
fun SyncStatusOverlay(
    status: SyncStatusInfo,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Compact icon
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SyncStatusIcon(
                status = status,
                onClick = { onExpandChange(true) }
            )
        }
        
        // Expanded card
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SyncStatusCard(
                status = status,
                onRetryClick = onRetryClick,
                onDismiss = { onExpandChange(false) }
            )
        }
    }
}

/**
 * Toast-like notification for realtime updates.
 */
@Composable
fun SyncToast(
    message: String,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
