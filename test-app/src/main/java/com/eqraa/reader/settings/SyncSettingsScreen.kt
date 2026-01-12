/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.settings

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
import com.eqraa.reader.data.BackupManager
import com.eqraa.reader.data.ReadingSyncManager
import com.eqraa.reader.data.UserPreferencesSyncManager
import com.eqraa.reader.data.model.SyncLogEntry
import java.text.SimpleDateFormat
import java.util.*

// Minimalist Design System Colors (White & Black)
private val PureWhite = Color(0xFFFFFFFF)
private val PureBlack = Color(0xFF000000)
private val SurfaceGray = Color(0xFFF7F7F7) // Very light gray for cards
private val TextPrimary = Color(0xFF000000)
private val TextSecondary = Color(0xFF666666)
private val BorderColor = Color(0xFFE0E0E0)
private val SeparatorColor = Color(0xFFEEEEEE)

// Monochrome Accents
private val AccentBlack = Color(0xFF000000)
private val AccentDarkGray = Color(0xFF333333)
private val AccentMediumGray = Color(0xFF888888)
private val AccentLightGray = Color(0xFFCCCCCC)

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
        value = com.eqraa.reader.data.SupabaseService.client.auth.currentSessionOrNull()
        com.eqraa.reader.data.SupabaseService.client.auth.sessionStatus.collect { status ->
            value = if (status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated) status.session else null
        }
    }

    var showLoginDialog by remember { mutableStateOf(false) }

    // Sync State observation
    // using produceState to simulate or fetch real sync state if available
    var isSyncing by remember { mutableStateOf(false) }

    // Recent Logs State
    val recentLogs by produceState<List<SyncLogEntry>>(initialValue = emptyList()) {
        val database = com.eqraa.reader.data.db.AppDatabase.getDatabase(context)
        database.syncLogDao().getRecentLogs().collectLatest {
            value = it.take(5)
        }
    }
    
    val lastSynced = "Recently"
    
    // Fake Storage breakdown (Visuals updated to monochrome)
    val storageUsed = 0.64f // Example: 64% used

    val connectedDevices = listOf(
        DeviceInfo("Pixel 8 Pro", "This device", true),
        DeviceInfo("iPad Air", "Active 2h ago", false)
    )

    // Toggles Persistence
    // In a real app, these would wrap SharedPreferences or DataStore
    val prefs = remember { context.getSharedPreferences("sync_config", android.content.Context.MODE_PRIVATE) }
    
    var syncPosition by remember { mutableStateOf(prefs.getBoolean("sync_position", true)) }
    var syncHighlights by remember { mutableStateOf(prefs.getBoolean("sync_highlights", true)) }
    var syncSettings by remember { mutableStateOf(prefs.getBoolean("sync_settings", true)) }

    fun updateToggle(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        when(key) {
            "sync_position" -> syncPosition = value
            "sync_highlights" -> syncHighlights = value
            "sync_settings" -> syncSettings = value
        }
    }

    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = {
                showLoginDialog = false
                scope.launch {
                    isSyncing = true
                    syncManager?.syncNow()
                    delay(1000)
                    isSyncing = false
                }
            }
        )
    }

    Scaffold(
        backgroundColor = PureWhite,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Cloud & Data", 
                        color = TextPrimary, 
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontSize = 20.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                backgroundColor = PureWhite,
                elevation = 0.dp
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            if (currentUser == null) {
                // Not Logged In State (Minimalist)
                Card(
                    shape = RoundedCornerShape(0.dp), // Sharp corners
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                    backgroundColor = PureWhite,
                    elevation = 0.dp,
                    modifier = Modifier.fillMaxWidth().clickable { showLoginDialog = true }
                ) {
                    Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Sync Paused", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Sign in to resume synchronization.", color = TextSecondary, textAlign = TextAlign.Center, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showLoginDialog = true },
                            colors = ButtonDefaults.buttonColors(backgroundColor = PureBlack),
                            shape = RoundedCornerShape(0.dp), // Sharp corners
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
                        ) {
                            Text("SIGN IN", color = PureWhite, letterSpacing = 1.sp)
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
                            isSyncing = true
                            // Trigger all sync managers
                            if (syncSettings) syncManager?.syncNow()
                            // if (syncPosition) readingSync?.syncUnsyncedItems()
                            backupManager?.performFullBackup()
                            delay(1500) // Fake delay for UX
                            isSyncing = false
                        }
                    }
                )

                // 3. User Info (Minimalist)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    // Minimalist user icon
                    Box(modifier = Modifier.size(24.dp).border(1.dp, TextPrimary, CircleShape), contentAlignment = Alignment.Center) {
                         Text(
                             currentUser?.user?.email?.take(1)?.uppercase() ?: "U",
                             fontSize = 12.sp,
                             fontWeight = FontWeight.Bold
                         )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        currentUser?.user?.email ?: "mahmud@eqraa.app",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 4. Preferences Toggles
                SectionHeader("DATA TO SYNC")
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(0.dp))
                        .background(PureWhite)
                ) {
                    SyncToggleItem(
                        "Reading Position", 
                        "Resume exactly where you left off", 
                        syncPosition,
                        onCheckedChange = { updateToggle("sync_position", it) }
                    )
                    Divider(color = SeparatorColor, thickness = 1.dp)
                    SyncToggleItem(
                        "Highlights & Notes", 
                        "Secure backup for your annotations", 
                        syncHighlights,
                        onCheckedChange = { updateToggle("sync_highlights", it) }
                    )
                    Divider(color = SeparatorColor, thickness = 1.dp)
                    SyncToggleItem(
                        "App Settings", 
                        "Sync your theme and font choices", 
                        syncSettings,
                        onCheckedChange = { updateToggle("sync_settings", it) }
                    )
                }

                // 5. Connected Devices
                SectionHeader("CONNECTED DEVICES")
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(0.dp))
                        .background(PureWhite)
                ) {
                    connectedDevices.forEachIndexed { index, device ->
                        DeviceItem(device)
                        if (index < connectedDevices.size - 1) {
                            Divider(color = SeparatorColor, thickness = 1.dp)
                        }
                    }
                }

                // 6. Recent Activity
                SectionHeader("RECENT ACTIVITY")
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(0.dp)) // Sharp
                        .background(PureWhite)
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
                                Divider(color = SeparatorColor, thickness = 1.dp)
                            }
                        }
                    }
                }

                // 7. Sign Out
                OutlinedButton(
                    onClick = { 
                        scope.launch {
                             com.eqraa.reader.data.SupabaseService.client.auth.signOut()
                             onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TextPrimary)
                ) {
                    Text("SIGN OUT", color = TextPrimary, fontSize = 13.sp, letterSpacing = 1.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        color = TextPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
fun CloudStorageCard(usedRatio: Float) {
    Card(
        shape = RoundedCornerShape(0.dp), // Sharp
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        backgroundColor = PureWhite,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "3.2 GB",
                            color = TextPrimary,
                            fontSize = 28.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            " / 5 GB",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                    }
                }
                
                Icon(
                    Icons.Outlined.CloudQueue,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Segmented Progress Bar (Monochrome)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp) // Thinner
                    .background(SurfaceGray)
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .background(AccentBlack)
                )
                Box(modifier = Modifier.width(1.dp))
                Box(
                    modifier = Modifier
                        .weight(0.15f)
                        .fillMaxHeight()
                        .background(AccentDarkGray)
                )
                Box(modifier = Modifier.width(1.dp))
                Box(
                    modifier = Modifier
                        .weight(0.1f)
                        .fillMaxHeight()
                        .background(AccentMediumGray)
                )
                // Remaining space empty
                Spacer(modifier = Modifier.weight(0.35f))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                LegendItem("Books", AccentBlack)
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem("Notes", AccentDarkGray)
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem("Backups", AccentMediumGray)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color) // Square dot
        )
        Spacer(modifier = Modifier.width(8.dp))
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
        shape = RoundedCornerShape(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        backgroundColor = PureWhite,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "STATUS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSyncing) {
                        Text(
                            "Syncing...",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                        )
                    } else {
                        Icon(Icons.Default.Check, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Everything is safe",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                        )
                    }
                }
            }
            
            // Minimalist Button
            Button(
                onClick = onSyncClick,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if(isSyncing) PureWhite else PureBlack,
                    contentColor = if(isSyncing) PureBlack else PureWhite
                ),
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                enabled = !isSyncing,
                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                border = if(isSyncing) androidx.compose.foundation.BorderStroke(1.dp, PureBlack) else null
            ) {
                if (isSyncing) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Syncing",
                        modifier = Modifier.size(16.dp).rotate(rotation),
                        tint = PureBlack
                    )
                } else {
                    Text("SYNC", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun LogItem(log: SyncLogEntry) {
    val date = remember(log.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp))
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator (Monochrome)
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    when (log.eventType) {
                        "ERROR" -> PureBlack
                        "WARNING" -> AccentMediumGray
                        else -> AccentLightGray
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
        }
        
        Text(
            date,
            color = TextSecondary,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
fun SyncToggleItem(
    title: String, 
    subtitle: String, 
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
            )
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = PureBlack,
                uncheckedThumbColor = AccentLightGray,
                uncheckedTrackColor = SurfaceGray,
                checkedTrackAlpha = 1.0f
            )
        )
    }
}

@Composable
fun DeviceItem(device: DeviceInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (device.name.contains("Pixel") || device.name.contains("Phone")) Icons.Outlined.Smartphone else Icons.Outlined.Tablet,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(20.dp))
        
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
                            .size(4.dp)
                            .background(PureBlack)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    device.status,
                    color = if (device.isCurrent) TextPrimary else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (device.isCurrent) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

data class DeviceInfo(val name: String, val status: String, val isCurrent: Boolean)
