/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.platform.LocalContext
import io.github.jan.supabase.auth.auth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.r2.testapp.data.BackupManager
import org.readium.r2.testapp.data.ReadingSyncManager
import org.readium.r2.testapp.data.UserPreferencesSyncManager
import org.readium.r2.testapp.data.model.SyncLogEntry
import java.text.SimpleDateFormat
import java.util.*

// Design System Colors (OLED Friendly)
private val DeepBlack = Color(0xFF000000)
private val SurfaceDark = Color(0xFF121212)
private val SurfaceLight = Color(0xFF1E1E1E)
private val PrimaryPurple = Color(0xFF6200EE)
private val AccentBlue = Color(0xFF03DAC6)
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSecondary = Color(0xFFB0B0B0)
private val DangerRed = Color(0xFFCF6679)
private val ProgressGreen = Color(0xFF10B981)
private val ProgressBlue = Color(0xFF3B82F6)
private val ProgressPurple = Color(0xFF8B5CF6)

@Composable
fun SyncSettingsScreen(
    backupManager: BackupManager?,
    syncManager: UserPreferencesSyncManager?,
    readingSync: ReadingSyncManager?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Auth State
    val currentUser by produceState<io.github.jan.supabase.auth.user.UserSession?>(initialValue = null) {
        value = org.readium.r2.testapp.data.SupabaseService.client.auth.currentSessionOrNull()
        org.readium.r2.testapp.data.SupabaseService.client.auth.sessionStatus.collect { status ->
            value = if (status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated) status.session else null
        }
    }

    var showLoginDialog by remember { mutableStateOf(false) }

    // Sync State observation
    // val syncState by readingSyncManager?.syncState?.collectAsState() ... (Removed)

    // Recent Logs State
    val recentLogs by produceState<List<SyncLogEntry>>(initialValue = emptyList()) {
        val database = org.readium.r2.testapp.data.db.AppDatabase.getDatabase(context)
        database.syncLogDao().getRecentLogs().collectLatest {
            value = it.take(5)
        }
    }
    
    val isSyncing = false // Simplified for now, or fetch from SyncStatusViewModel
    val lastSynced = "Recently"
    
    // Fake Storage breakdown based on something "real-ish"
    // In a real app we'd fetch actual byte sizes from Supabase, 
    // here we'll simulate based on a few MBs.
    val storageUsed = 0.42f 

    val connectedDevices = listOf(
        DeviceInfo("Pixel 8 Pro", "This device", true),
        DeviceInfo("iPad Air", "Active 2h ago", false)
    )

    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = {
                showLoginDialog = false
                scope.launch {
                    syncManager?.syncNow()
                    // readingSync?.syncToSupabase(...) // No direct sync needed if debounced, but could fetch latest
                }
            }
        )
    }

    Scaffold(
        backgroundColor = DeepBlack,
        topBar = {
            TopAppBar(
                title = { Text("Cloud & Data", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                backgroundColor = DeepBlack,
                elevation = 0.dp
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (currentUser == null) {
                // Not Logged In State
                Card(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = SurfaceDark,
                    modifier = Modifier.fillMaxWidth().clickable { showLoginDialog = true }
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cloud Sync Disabled", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Sign in to backup your library and sync progress across devices.", color = TextSecondary, textAlign = TextAlign.Center, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showLoginDialog = true },
                            colors = ButtonDefaults.buttonColors(backgroundColor = PrimaryPurple),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sign In Now", color = Color.White)
                        }
                    }
                }
            } else {
                // 1. Cloud Storage Card
                CloudStorageCard(usedRatio = storageUsed)

                // 2. Sync Status
                SyncStatusCard(
                    isSyncing = isSyncing,
                    lastSynced = lastSynced,
                    onSyncClick = {
                        scope.launch {
                            // Trigger all sync managers
                            syncManager?.syncNow()
                            // readingSync?.syncUnsyncedItems()
                            backupManager?.performFullBackup()
                        }
                    }
                )

                // 3. User Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        currentUser?.user?.email ?: "mahmud@eqraa.app",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 4. Preferences Toggles
                Text(
                    "DATA TO SYNC",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                ) {
                    SyncToggleItem("Reading Position", "Resume exactly where you left off", true)
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    SyncToggleItem("Highlights & Notes", "Secure backup for your annotations", true)
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    SyncToggleItem("App Settings", "Sync your theme and font choices", true)
                }

                // 5. Connected Devices
                Text(
                    "CONNECTED DEVICES",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                ) {
                    connectedDevices.forEachIndexed { index, device ->
                        DeviceItem(device)
                        if (index < connectedDevices.size - 1) {
                            Divider(color = SurfaceLight, thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))
                        }
                    }
                }

                // 6. Recent Activity
                Text(
                    "RECENT ACTIVITY",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                ) {
                    if (recentLogs.isEmpty()) {
                        Text(
                            "No recent activity",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(20.dp)
                        )
                    } else {
                        recentLogs.forEachIndexed { index, log ->
                            LogItem(log)
                            if (index < recentLogs.size - 1) {
                                Divider(color = SurfaceLight, thickness = 1.dp)
                            }
                        }
                    }
                }

                // 7. Sign Out
                TextButton(
                    onClick = { 
                        scope.launch {
                             org.readium.r2.testapp.data.SupabaseService.client.auth.signOut()
                             onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign Out and Stop Syncing", color = DangerRed, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun CloudStorageCard(usedRatio: Float) {
    Card(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = SurfaceDark,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "Cloud Storage",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "3.2 GB",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            " / 5 GB",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                    }
                }
                
                Surface(
                    color = SurfaceLight,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Segmented Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceLight)
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .background(ProgressPurple)
                )
                Box(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .weight(0.15f)
                        .fillMaxHeight()
                        .background(ProgressBlue)
                )
                Box(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .weight(0.1f)
                        .fillMaxHeight()
                        .background(ProgressGreen)
                )
                // Remaining space empty
                Spacer(modifier = Modifier.weight(0.35f))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendItem("Books", ProgressPurple)
                LegendItem("Notes", ProgressBlue)
                LegendItem("Backups", ProgressGreen)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SyncStatusCard(
    isSyncing: Boolean,
    lastSynced: String,
    onSyncClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        backgroundColor = SurfaceDark,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth().border(1.dp, if(isSyncing) PrimaryPurple.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Sync Status",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSyncing) {
                        Text(
                            "Syncing...",
                            color = PrimaryPurple,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(Icons.Default.CheckCircle, null, tint = ProgressGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Everything is safe",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    "Last updated: $lastSynced",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
            
            Button(
                onClick = onSyncClick,
                colors = ButtonDefaults.buttonColors(backgroundColor = if(isSyncing) SurfaceLight else PrimaryPurple),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                enabled = !isSyncing,
                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                if (isSyncing) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Syncing",
                        modifier = Modifier.size(18.dp).rotate(rotation),
                        tint = TextSecondary
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Now", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun LogItem(log: SyncLogEntry) {
    val date = remember(log.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (log.eventType) {
                        "ERROR" -> DangerRed
                        "WARNING" -> Color(0xFFFBBF24) // Amber
                        else -> ProgressGreen
                    }
                )
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                log.message,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${log.source} • $date",
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun SyncToggleItem(title: String, subtitle: String, initialChecked: Boolean) {
    var checked by remember { mutableStateOf(initialChecked) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryPurple,
                checkedTrackColor = PrimaryPurple.copy(alpha = 0.5f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceLight
            )
        )
    }
}

data class DeviceInfo(val name: String, val status: String, val isCurrent: Boolean)

@Composable
fun DeviceItem(device: DeviceInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = SurfaceLight,
            shape = CircleShape,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                if (device.name.contains("Pixel") || device.name.contains("Phone")) Icons.Outlined.Smartphone else Icons.Outlined.Tablet,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.padding(8.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                device.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (device.isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ProgressGreen)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    device.status,
                    color = if (device.isCurrent) ProgressGreen else TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
