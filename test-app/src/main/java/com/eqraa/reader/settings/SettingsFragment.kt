/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import kotlinx.coroutines.launch
import com.eqraa.reader.Application
import com.eqraa.reader.data.BackupManager
import com.eqraa.reader.data.ReadingSyncManager
import com.eqraa.reader.data.UserPreferencesSyncManager
import com.eqraa.reader.data.db.AppDatabase
import android.os.Environment
import android.widget.Toast
import java.io.File
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.window.Dialog

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    SettingsScreen()
                }
            }
        }
    }
}

// Elegant monochrome color palette
private val PureWhite = Color(0xFFFFFFFF)
private val OffWhite = Color(0xFFFAFAFA)
private val LightGray = Color(0xFFF3F4F6)
private val BorderGray = Color(0xFFE5E7EB)
private val MediumGray = Color(0xFF9CA3AF)
private val DarkGray = Color(0xFF6B7280)
private val CharcoalGray = Color(0xFF374151)
private val PureBlack = Color(0xFF1A1A1A)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { ReadingPreferences(context) }
    val syncManager = remember { (context.applicationContext as Application).userPreferencesSyncManager }
    val backupManager = remember { (context.applicationContext as Application).backupManager }
    val readingSync = remember<ReadingSyncManager?> { (context.applicationContext as Application).readingSyncManager }
    val scope = rememberCoroutineScope()
    
    var showSyncDashboard by remember { mutableStateOf(false) }

    if (showSyncDashboard) {
        SyncSettingsScreen(
            backupManager = backupManager,
            syncManager = syncManager,
            readingSync = readingSync,
            onBack = { showSyncDashboard = false }
        )
        return
    }
    
    // State
    var fontSize by remember { mutableStateOf(prefs.fontSize.toFloat()) }
    var fontWeight by remember { mutableStateOf(prefs.fontWeight.toFloat()) }
    var selectedFont by remember { mutableStateOf(prefs.fontFamily) }
    var marginSize by remember { mutableStateOf(prefs.marginSize) }
    var lineHeight by remember { mutableStateOf(prefs.lineHeight) }
    var textAlign by remember { mutableStateOf(prefs.textAlign) }
    var selectedTheme by remember { mutableStateOf(prefs.theme) }
    var keepScreenOn by remember { mutableStateOf(prefs.keepScreenOn) }
    var volumePageTurn by remember { mutableStateOf(prefs.volumePageTurn) }
    var aiEnabled by remember { mutableStateOf(prefs.aiEnabled) }
    var aiProvider by remember { mutableStateOf(prefs.aiProvider) }
    var groqApiKey by remember { mutableStateOf(prefs.groqApiKey) }
    var ollamaUrl by remember { mutableStateOf(prefs.ollamaUrl) }
    
    val fonts = listOf("Literata", "Amiri", "Vazirmatn", "Lora", "Atkinson", "Inter")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
    ) {
        // Elegant Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.5).sp,
                color = PureBlack
            )
            TextButton(
                onClick = {
                    prefs.resetToDefaults()
                    fontSize = prefs.fontSize.toFloat()
                    fontWeight = prefs.fontWeight.toFloat()
                    selectedFont = prefs.fontFamily
                }
            ) {
                Text(
                    "Reset",
                    color = DarkGray,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
        
        Divider(color = BorderGray, thickness = 0.5.dp)
        
        // Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Appearance Section
            SettingsSection(
                icon = Icons.Outlined.Palette,
                title = "Appearance"
            ) {
                // Theme Selector
                Text(
                    "Theme",
                    fontSize = 13.sp,
                    color = DarkGray,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ThemeButton(
                        name = "Paper",
                        bgColor = PureWhite,
                        textColor = PureBlack,
                        isSelected = selectedTheme == 0,
                        onClick = { 
                            selectedTheme = 0; 
                            prefs.theme = 0; 
                            syncManager?.scheduleSync(scope) 
                        }
                    )
                    ThemeButton(
                        name = "Sepia",
                        bgColor = Color(0xFFF5F0E1),
                        textColor = Color(0xFF5B4636),
                        isSelected = selectedTheme == 1,
                        onClick = { 
                            selectedTheme = 1; 
                            prefs.theme = 1; 
                            syncManager?.scheduleSync(scope) 
                        }
                    )
                    ThemeButton(
                        name = "Dark",
                        bgColor = PureBlack,
                        textColor = Color(0xFFE5E5E5),
                        isSelected = selectedTheme == 2,
                        onClick = { 
                            selectedTheme = 2; 
                            prefs.theme = 2; 
                            syncManager?.scheduleSync(scope) 
                        }
                    )
                }
            }
            
            // Typography Section
            SettingsSection(
                icon = Icons.Outlined.TextFields,
                title = "Typography"
            ) {
                // Font Family
                Text(
                    "Font Family",
                    fontSize = 13.sp,
                    color = DarkGray,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(fonts) { font ->
                        FontCard(
                            fontName = font,
                            isSelected = selectedFont == font,
                            onClick = { 
                                selectedFont = font; 
                                prefs.fontFamily = font;
                                syncManager?.scheduleSync(scope)
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Font Size
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.FormatSize,
                            contentDescription = null,
                            tint = DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Size",
                            fontSize = 13.sp,
                            color = DarkGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "${fontSize.toInt()}pt",
                        fontSize = 15.sp,
                        color = PureBlack,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OffWhite, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("A", fontSize = 12.sp, color = MediumGray)
                    Slider(
                        value = fontSize,
                        onValueChange = { 
                            fontSize = it
                            prefs.fontSize = it.toInt()
                            syncManager?.scheduleSync(scope)
                        },
                        valueRange = 12f..32f,
                        colors = SliderDefaults.colors(
                            thumbColor = PureBlack,
                            activeTrackColor = PureBlack,
                            inactiveTrackColor = BorderGray
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Text("A", fontSize = 20.sp, color = PureBlack, fontWeight = FontWeight.Medium)
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Font Weight
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.FormatBold,
                            contentDescription = null,
                            tint = DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Weight",
                            fontSize = 13.sp,
                            color = DarkGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        getWeightName(fontWeight.toInt()),
                        fontSize = 14.sp,
                        color = CharcoalGray
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OffWhite, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Aa", fontSize = 14.sp, color = MediumGray, fontWeight = FontWeight.Light)
                    Slider(
                        value = fontWeight,
                        onValueChange = { 
                            fontWeight = it
                            prefs.fontWeight = it.toInt()
                        },
                        valueRange = 100f..900f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = PureBlack,
                            activeTrackColor = PureBlack,
                            inactiveTrackColor = BorderGray
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Text("Aa", fontSize = 14.sp, color = PureBlack, fontWeight = FontWeight.Black)
                }
            }
            
            // Layout Section
            SettingsSection(
                icon = Icons.Outlined.GridView,
                title = "Layout"
            ) {
                // Margins
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.FitScreen,
                        contentDescription = null,
                        tint = DarkGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Margins",
                        fontSize = 13.sp,
                        color = DarkGray,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LayoutButton(
                        icon = Icons.Outlined.UnfoldLess,
                        label = "Small",
                        isSelected = marginSize == 0,
                        onClick = { marginSize = 0; prefs.marginSize = 0; syncManager?.scheduleSync(scope) }
                    )
                    LayoutButton(
                        icon = Icons.Outlined.UnfoldMore,
                        label = "Medium",
                        isSelected = marginSize == 1,
                        onClick = { marginSize = 1; prefs.marginSize = 1; syncManager?.scheduleSync(scope) }
                    )
                    LayoutButton(
                        icon = Icons.Outlined.OpenInFull,
                        label = "Large",
                        isSelected = marginSize == 2,
                        onClick = { marginSize = 2; prefs.marginSize = 2; syncManager?.scheduleSync(scope) }
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Line Height
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.FormatLineSpacing,
                        contentDescription = null,
                        tint = DarkGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Line Height",
                        fontSize = 13.sp,
                        color = DarkGray,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LayoutButton(
                        icon = Icons.Outlined.DensitySmall,
                        label = "Compact",
                        isSelected = lineHeight == 0,
                        onClick = { lineHeight = 0; prefs.lineHeight = 0; syncManager?.scheduleSync(scope) }
                    )
                    LayoutButton(
                        icon = Icons.Outlined.DensityMedium,
                        label = "Normal",
                        isSelected = lineHeight == 1,
                        onClick = { lineHeight = 1; prefs.lineHeight = 1; syncManager?.scheduleSync(scope) }
                    )
                    LayoutButton(
                        icon = Icons.Outlined.DensityLarge,
                        label = "Relaxed",
                        isSelected = lineHeight == 2,
                        onClick = { lineHeight = 2; prefs.lineHeight = 2; syncManager?.scheduleSync(scope) }
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Text Alignment
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.FormatAlignLeft,
                        contentDescription = null,
                        tint = DarkGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Alignment",
                        fontSize = 13.sp,
                        color = DarkGray,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OffWhite, RoundedCornerShape(10.dp))
                        .padding(4.dp)
                ) {
                    val leftBg by animateColorAsState(
                        targetValue = if (textAlign == 0) PureWhite else Color.Transparent,
                        animationSpec = tween(200)
                    )
                    val rightBg by animateColorAsState(
                        targetValue = if (textAlign == 1) PureWhite else Color.Transparent,
                        animationSpec = tween(200)
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(leftBg)
                            .clickable { textAlign = 0; prefs.textAlign = 0; syncManager?.scheduleSync(scope) }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FormatAlignLeft,
                            null,
                            tint = if (textAlign == 0) PureBlack else MediumGray
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(rightBg)
                            .clickable { textAlign = 1; prefs.textAlign = 1; syncManager?.scheduleSync(scope) }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FormatAlignJustify,
                            null,
                            tint = if (textAlign == 1) PureBlack else MediumGray
                        )
                    }
                }
            }
            
            // Reading Section
            SettingsSection(
                icon = Icons.Outlined.MenuBook,
                title = "Reading"
            ) {
                SettingsToggle(
                    icon = Icons.Outlined.LightMode,
                    title = "Keep Screen On",
                    subtitle = "Prevent screen from dimming",
                    checked = keepScreenOn,
                    onCheckedChange = { keepScreenOn = it; prefs.keepScreenOn = it }
                )
                
                Divider(color = BorderGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
                
                SettingsToggle(
                    icon = Icons.Outlined.VolumeUp,
                    title = "Volume Page Turn",
                    subtitle = "Use volume buttons to turn pages",
                    checked = volumePageTurn,
                    onCheckedChange = { volumePageTurn = it; prefs.volumePageTurn = it }
                )
                
                Divider(color = BorderGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
                
                SettingsToggle(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "AI Companion",
                    subtitle = "Enable AI-powered reading assistance",
                    checked = aiEnabled,
                    onCheckedChange = { aiEnabled = it; prefs.aiEnabled = it }
                )
            }
            
            if (aiEnabled) {
                SettingsSection(
                    icon = Icons.Outlined.Psychology,
                    title = "AI Provider"
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AIProviderButton(
                            name = "Groq",
                            isSelected = aiProvider == 0,
                            onClick = { aiProvider = 0; prefs.aiProvider = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        AIProviderButton(
                            name = "OpenRouter",
                            isSelected = aiProvider == 1,
                            onClick = { aiProvider = 1; prefs.aiProvider = 1 },
                            modifier = Modifier.weight(1f)
                        )
                        AIProviderButton(
                            name = "Ollama",
                            isSelected = aiProvider == 2,
                            onClick = { aiProvider = 2; prefs.aiProvider = 2 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    if (aiProvider == 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "API Key (Optional)",
                            fontSize = 12.sp,
                            color = DarkGray,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = groqApiKey,
                            onValueChange = { groqApiKey = it; prefs.groqApiKey = it },
                            placeholder = { Text("Enter your API key", color = MediumGray) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = PureBlack,
                                unfocusedBorderColor = BorderGray,
                                backgroundColor = OffWhite
                            ),
                            singleLine = true
                        )
                    }
                    
                    if (aiProvider == 2) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Ollama Base URL",
                            fontSize = 12.sp,
                            color = DarkGray,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ollamaUrl,
                            onValueChange = { ollamaUrl = it; prefs.ollamaUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = PureBlack,
                                unfocusedBorderColor = BorderGray,
                                backgroundColor = OffWhite
                            ),
                            singleLine = true
                        )
                        Text(
                            "Default: http://10.0.2.2:11434",
                            fontSize = 11.sp,
                            color = MediumGray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // Account Section
            SettingsSection(
                icon = Icons.Outlined.Person,
                title = "Account"
            ) {
                var showLoginDialog by remember { mutableStateOf(false) }
                val currentUser by produceState<io.github.jan.supabase.auth.user.UserSession?>(initialValue = null) {
                    // Initial check
                    value = com.eqraa.reader.data.SupabaseService.client.auth.currentSessionOrNull()
                    
                    // Listen for auth changes
                    com.eqraa.reader.data.SupabaseService.client.auth.sessionStatus.collect { status: io.github.jan.supabase.auth.status.SessionStatus ->
                        value = if (status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated) status.session else null
                    }
                }

                if (currentUser == null) {
                    SettingsButton(
                        icon = Icons.Default.Login,
                        title = "Cloud Sync & Backup",
                        subtitle = "Sign in to keep your library in sync",
                        onClick = { showSyncDashboard = true }
                    )
                } else {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                             Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981), // Green
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Signed in as",
                                fontSize = 12.sp,
                                color = DarkGray
                            )
                        }
                        
                        Text(
                            text = currentUser?.user?.email ?: "Unknown User",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = PureBlack,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        SettingsButton(
                            icon = Icons.Default.CloudQueue,
                            title = "Sync Dashboard",
                            subtitle = "Manage cloud storage and devices",
                            onClick = { showSyncDashboard = true }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsButton(
                            icon = Icons.Default.Logout,
                            title = "Sign Out",
                            subtitle = "Stop syncing data",
                            onClick = {
                                scope.launch {
                                    try {
                                        com.eqraa.reader.data.SupabaseService.client.auth.signOut()
                                        Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                         Toast.makeText(context, "Error signing out", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                if (showLoginDialog) {
                    LoginDialog(
                        onDismiss = { showLoginDialog = false },
                        onLoginSuccess = {
                            showLoginDialog = false
                            // Trigger sync on login
                            scope.launch {
                                (context.applicationContext as? com.eqraa.reader.Application)?.let { app ->
                                     app.userPreferencesSyncManager?.scheduleSync(this)
                                     app.cloudLibraryManager?.fetchCloudLibrary()
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // Add spacing before About section

            // About Section
            Card(
                shape = RoundedCornerShape(14.dp),
                backgroundColor = OffWhite,
                elevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, BorderGray, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "أقراء Reader",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PureBlack
                        )
                        Text(
                            "Version 1.0.0 • Built with Readium",
                            fontSize = 12.sp,
                            color = DarkGray
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        // Section Header with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PureBlack,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = CharcoalGray,
                letterSpacing = 1.5.sp
            )
        }
        
        Card(
            shape = RoundedCornerShape(14.dp),
            backgroundColor = PureWhite,
            elevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, BorderGray, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun ThemeButton(
    name: String,
    bgColor: Color,
    textColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) PureBlack else BorderGray,
        animationSpec = tween(200)
    )
    val borderWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = tween(200)
    )
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(borderWidth, borderColor, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text("Aa", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            name,
            fontSize = 12.sp,
            color = if (isSelected) PureBlack else DarkGray,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSignUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            backgroundColor = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSignUp) "Create Account" else "Welcome Back",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureBlack
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = PureBlack,
                        cursorColor = PureBlack,
                        focusedLabelColor = PureBlack
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = PureBlack,
                        cursorColor = PureBlack,
                        focusedLabelColor = PureBlack
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please fill in all fields"
                            return@Button
                        }
                        
                        isLoading = true
                        errorMessage = null
                        
                        scope.launch {
                            try {
                                val supabase = com.eqraa.reader.data.SupabaseService.client
                                if (isSignUp) {
                                    supabase.auth.signUpWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                                        this.email = email
                                        this.password = password
                                    }
                                    Toast.makeText(context, "Account created! Please check your email to confirm.", Toast.LENGTH_LONG).show()
                                    onDismiss() // Close dialog, user needs to confirm email usually, or might be auto-logged in depending on config
                                } else {
                                    supabase.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                                        this.email = email
                                        this.password = password
                                    }
                                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Authentication failed"
                                if (e.message?.contains("json") == true) {
                                     errorMessage = "Network error or invalid response. Check connection."
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = PureBlack),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(20.dp))
                    } else {
                        Text(if (isSignUp) "Sign Up" else "Sign In", color = PureWhite)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = { isSignUp = !isSignUp; errorMessage = null }) {
                    Text(
                        text = if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                        color = DarkGray,
                        fontSize = 14.sp
                    )
                }
                
                TextButton(onClick = onDismiss) {
                     Text("Cancel", color = MediumGray, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun FontCard(
    fontName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) PureWhite else OffWhite,
        animationSpec = tween(200)
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) PureBlack else BorderGray,
        animationSpec = tween(200)
    )
    
    Box(
        modifier = Modifier
            .width(88.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Aa",
                fontSize = 22.sp,
                color = if (isSelected) PureBlack else DarkGray,
                fontWeight = FontWeight.Medium
            )
            Text(
                fontName,
                fontSize = 10.sp,
                color = if (isSelected) PureBlack else MediumGray,
                fontWeight = FontWeight.Medium
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(PureBlack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = PureWhite, modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
fun LayoutButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) PureBlack else OffWhite,
        animationSpec = tween(200)
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) PureWhite else DarkGray,
        animationSpec = tween(200)
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(
                    width = if (isSelected) 0.dp else 0.5.dp,
                    color = BorderGray,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (isSelected) PureBlack else MediumGray,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = DarkGray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 15.sp, color = PureBlack, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 12.sp, color = DarkGray)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureBlack,
                checkedTrackColor = CharcoalGray,
                uncheckedThumbColor = MediumGray,
                uncheckedTrackColor = BorderGray
            )
        )
    }
}

@Composable
fun AIProviderButton(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) PureBlack else OffWhite,
        animationSpec = tween(200)
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) PureWhite else DarkGray,
        animationSpec = tween(200)
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 0.dp else 0.5.dp,
                color = BorderGray,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name,
            color = textColor,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

fun getWeightName(weight: Int): String {
    return when {
        weight <= 200 -> "Thin"
        weight <= 300 -> "Light"
        weight <= 400 -> "Regular"
        weight <= 500 -> "Medium"
        weight <= 600 -> "SemiBold"
        weight <= 700 -> "Bold"
        weight <= 800 -> "ExtraBold"
        else -> "Black"
    }
}

@Composable
fun SettingsButton(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    loading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PureBlack,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    color = PureBlack,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MediumGray
                    )
                }
            }
        }
        
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = PureBlack
            )
        } else {
             Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MediumGray
            )
       }
    }
}
