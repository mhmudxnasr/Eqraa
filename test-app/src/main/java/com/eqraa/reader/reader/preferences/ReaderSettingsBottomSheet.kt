/*
 * Copyright 2022 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.reader.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.readium.r2.navigator.epub.EpubPreferencesEditor
import org.readium.r2.navigator.preferences.PreferencesEditor
import org.readium.r2.navigator.preferences.Theme
import com.eqraa.reader.data.BackupManager
import com.eqraa.reader.reader.ReaderViewModel
import com.eqraa.reader.reader.tts.TtsViewModel
import com.eqraa.reader.utils.extensions.asStateWhenStarted

class ReaderSettingsBottomSheet(
    private val readerViewModel: ReaderViewModel,
    private val onSearchClick: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ReaderSettingsScreen(
                    readerViewModel = readerViewModel,
                    onSearchClick = {
                        dismiss()
                        onSearchClick()
                    }
                )
            }
        }
    }
}

@Composable
fun ReaderSettingsScreen(
    readerViewModel: ReaderViewModel,
    onSearchClick: () -> Unit
) {
    val settingsModel = readerViewModel.settings
    val ttsModel = readerViewModel.tts
    
    // Sync State
    val syncState by readerViewModel.syncState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.LightGray)
                .padding(bottom = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // 1. Search Bar
        SearchBarTrigger(onClick = onSearchClick)
        
        Spacer(modifier = Modifier.height(32.dp))

        // 2. Appearance Section
        if (settingsModel != null) {
            val editor by settingsModel.editor.collectAsState()
            AppearanceSection(editor = editor, commit = settingsModel::commit)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 3. Audio / TTS Section
        if (ttsModel != null) {
            AudioSection(ttsModel = ttsModel)
            Spacer(modifier = Modifier.height(32.dp))
        }

        // 4. Sync & Backup Section
        SyncSection(
            syncState = syncState,
            onSyncClick = { readerViewModel.syncBook() },
            onBackupClick = { readerViewModel.performFullBackup() }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SearchBarTrigger(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(30.dp),
        color = Color(0xFFF0F0F0), // Light gray background
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color.Gray)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Find in book...", color = Color.Gray)
        }
    }
}

@Composable
fun AppearanceSection(
    editor: PreferencesEditor<*>,
    commit: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TextFormat, contentDescription = "Appearance", modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            
            // Size Slider
            if (editor is EpubPreferencesEditor) {
                editor.fontSize?.let { sizePref ->
                     // Simplified slider simulation using buttons for now as range isn't exposed easily
                     // In a real implementation with Exposed range, we'd use Slider
                     Row(
                         modifier = Modifier.weight(1f),
                         verticalAlignment = Alignment.CenterVertically,
                         horizontalArrangement = Arrangement.SpaceBetween
                     ) {
                         IconButton(onClick = { sizePref.decrement(); commit() }) { Text("A", style = MaterialTheme.typography.bodySmall) }
                         
                         // Visual Slider Track Representation
                         Box(modifier = Modifier.weight(1f).height(4.dp).background(Color(0xFFD0E4FF), RoundedCornerShape(2.dp)))
                         
                         IconButton(onClick = { sizePref.increment(); commit() }) { Text("A", style = MaterialTheme.typography.titleLarge) }
                     }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Theme Chips
        if (editor is EpubPreferencesEditor) {
             editor.theme?.let { themePref ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeChip(
                        name = "Light", 
                        selected = themePref.value == Theme.LIGHT, 
                        color = Color.White,
                        onClick = { themePref.set(Theme.LIGHT); commit() }
                    )
                    ThemeChip(
                        name = "Sepia", 
                        selected = themePref.value == Theme.SEPIA, 
                        color = Color(0xFFFAF4E8),
                        onClick = { themePref.set(Theme.SEPIA); commit() }
                    )
                    ThemeChip(
                        name = "Dark", 
                        selected = themePref.value == Theme.DARK, 
                        color = Color(0xFF121212),
                        textColor = Color.White,
                        onClick = { themePref.set(Theme.DARK); commit() }
                    )
                }
             }
        }
    }
}

@Composable
fun ThemeChip(
    name: String,
    selected: Boolean,
    color: Color,
    textColor: Color = Color.Black,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(30.dp),
        color = color,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF007AFF)) else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
        modifier = Modifier.height(40.dp).width(100.dp) // Fixed width for uniformity
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(name, color = textColor, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AudioSection(ttsModel: TtsViewModel) {
    val isPlaying by ttsModel.isPlaying.asStateWhenStarted()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Headphones, contentDescription = "Audio")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Headphones", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Simulated Waveform
            Row(modifier = Modifier.weight(1f).height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(40) {
                    Box(modifier = Modifier
                        .width(3.dp)
                        .height((10..32).random().dp)
                        .background(if(isPlaying) Color(0xFF007AFF) else Color.LightGray, RoundedCornerShape(1.dp)))
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Play/Pause Button
            FilledIconButton(
                onClick = { if (isPlaying) ttsModel.pause() else ttsModel.play() },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFD0E4FF), contentColor = Color.Black)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun SyncSection(
    syncState: BackupManager.SyncState,
    onSyncClick: () -> Unit,
    onBackupClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, contentDescription = "Cloud")
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Cloud", fontWeight = FontWeight.Bold)
                    Text(
                        when (syncState) {
                            is BackupManager.SyncState.Idle -> "Synced"
                            is BackupManager.SyncState.Syncing -> "Syncing..."
                            is BackupManager.SyncState.Success -> "Synced just now"
                            is BackupManager.SyncState.Error -> "Sync failed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncState is BackupManager.SyncState.Error) Color.Red else Color.Gray
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSyncClick) {
                    Icon(
                        Icons.Default.Refresh, 
                        contentDescription = "Sync",
                        modifier = Modifier.rotate(if (syncState is BackupManager.SyncState.Syncing) 360f else 0f) // Add simple animation if needed but compose needs state for that
                    )
                }
                
                Button(
                    onClick = onBackupClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0E4FF), contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Full Backup")
                }
            }
        }
    }
}

private fun Modifier.rotate(degrees: Float) = this // Placeholder for actual rotation modifier if needed
