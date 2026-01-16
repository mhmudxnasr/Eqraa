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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.ui.draw.rotate
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
import com.eqraa.reader.R

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
    // val ttsModel = readerViewModel.tts // Removed as per request (Cleanup)
    
    // Sync State


    // Staggered Entrance State
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    // Brightness Control
    val context = androidx.compose.ui.platform.LocalContext.current
    var brightness by remember { 
        val activity = context as? android.app.Activity
        mutableStateOf(activity?.window?.attributes?.screenBrightness ?: -1f) 
    }

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
        StaggeredItem(visible = visible, index = 0) {
            SearchBarTrigger(onClick = onSearchClick)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 2. Brightness Section
        StaggeredItem(visible = visible, index = 1) {
            BrightnessSection(
                currentBrightness = if (brightness == -1f) 0.5f else brightness, // Default visual position
                onBrightnessChange = { newBrightness ->
                    brightness = newBrightness
                    val activity = context as? android.app.Activity
                    val layoutParams = activity?.window?.attributes
                    layoutParams?.screenBrightness = newBrightness
                    activity?.window?.attributes = layoutParams
                }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // 3. Appearance Section
        if (settingsModel != null) {
            val editor by settingsModel.editor.collectAsState()
            StaggeredItem(visible = visible, index = 2) {
                AppearanceSection(editor = editor, commit = settingsModel::commit)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 4. Font Family Section (LazyRow)
            StaggeredItem(visible = visible, index = 3) {
                FontFamilySection(editor = editor, commit = settingsModel::commit)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 5. Advanced Layout Section
            StaggeredItem(visible = visible, index = 4) {
                AdvancedSettingsSection(editor = editor, commit = settingsModel::commit) 
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun BrightnessSection(
    currentBrightness: Float,
    onBrightnessChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(androidx.compose.ui.res.painterResource(id = R.drawable.ic_baseline_brightness_medium_24), contentDescription = "Low Brightness", tint = Color.Gray, modifier = Modifier.size(20.dp))
        
        Slider(
            value = currentBrightness,
            onValueChange = onBrightnessChange,
            valueRange = 0.01f..1f,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF007AFF),
                activeTrackColor = Color(0xFF007AFF),
                inactiveTrackColor = Color(0xFFD0E4FF)
            )
        )
        
        Icon(androidx.compose.ui.res.painterResource(id = R.drawable.ic_baseline_brightness_medium_24), contentDescription = "High Brightness", tint = Color.Black, modifier = Modifier.size(28.dp))
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
                     Row(
                         modifier = Modifier.weight(1f),
                         verticalAlignment = Alignment.CenterVertically,
                         horizontalArrangement = Arrangement.SpaceBetween
                     ) {
                         IconButton(onClick = { sizePref.decrement(); commit() }) { Text("A", style = MaterialTheme.typography.bodySmall) }
                         Box(modifier = Modifier.weight(1f).height(4.dp).background(Color(0xFFD0E4FF), RoundedCornerShape(2.dp)))
                         IconButton(onClick = { sizePref.increment(); commit() }) { Text("A", style = MaterialTheme.typography.titleLarge) }
                     }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Theme Chips (LazyRow)
        if (editor is EpubPreferencesEditor) {
             editor.theme?.let { themePref ->
                 androidx.compose.foundation.lazy.LazyRow(
                     horizontalArrangement = Arrangement.spacedBy(12.dp),
                     modifier = Modifier.fillMaxWidth()
                 ) {
                    item {
                        ThemeChip(name = "Light", selected = themePref.value == Theme.LIGHT, color = Color.White, onClick = { themePref.set(Theme.LIGHT); commit() })
                    }
                    item {
                        ThemeChip(name = "Sepia", selected = themePref.value == Theme.SEPIA, color = Color(0xFFFAF4E8), onClick = { themePref.set(Theme.SEPIA); commit() })
                    }
                    item {
                        ThemeChip(name = "Dark", selected = themePref.value == Theme.DARK, color = Color(0xFF121212), textColor = Color.White, onClick = { themePref.set(Theme.DARK); commit() })
                    }
                 }
             }
        }
    }
}

@Composable
fun FontFamilySection(
    editor: PreferencesEditor<*>?,
    commit: () -> Unit
) {
    Column {
        Text("Font Family", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (editor is EpubPreferencesEditor) {
            val fontPref = editor.fontFamily
            if (fontPref.isEffective) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val fonts = listOf(
                        null to "System",
                        org.readium.r2.navigator.preferences.FontFamily("serif") to "Serif",
                        org.readium.r2.navigator.preferences.FontFamily("Literata") to "Literata",
                        org.readium.r2.navigator.preferences.FontFamily("Lora") to "Lora", // New Premium
                        org.readium.r2.navigator.preferences.FontFamily("Amiri") to "Amiri", // New Arabic
                        org.readium.r2.navigator.preferences.FontFamily("Vazirmatn") to "Vazirmatn", // New Sans
                        org.readium.r2.navigator.preferences.FontFamily("Atkinson-Hyperlegible") to "Atkinson", // Accessibility
                        org.readium.r2.navigator.preferences.FontFamily("OpenDyslexic") to "Dyslexic"
                    )
                    
                    items(fonts.size) { i ->
                        val (fontFamily, label) = fonts[i]
                        FontChip(
                            name = label,
                            selected = fontPref.value == fontFamily,
                            onClick = { fontPref.set(fontFamily); commit() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FontChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50), // Fully rounded pill
        color = if (selected) Color.Black else Color(0xFFF5F5F5),
        border = if (selected) androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
        modifier = Modifier.height(40.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Text(
                name, 
                color = if (selected) Color.White else Color.Black, 
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StaggeredItem(
    visible: Boolean,
    index: Int,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { -100 },
            animationSpec = tween(300, delayMillis = index * 50)
        ) + fadeIn(animationSpec = tween(300, delayMillis = index * 50))
    ) {
        content()
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
fun AdvancedSettingsSection(
    editor: PreferencesEditor<*>,
    commit: () -> Unit
) {
    Column {
        // Divider
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = Color(0x1A000000))
        Spacer(modifier = Modifier.height(24.dp))

        // Section Title
        Text("Layout & Spacing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (editor is EpubPreferencesEditor) {
            // 0. Publisher Styles (Book Styles) Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Book Styles", tint = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Use Book Styles", style = MaterialTheme.typography.bodyMedium)
                }
                
                Switch(
                    checked = editor.publisherStyles.value ?: true,
                    onCheckedChange = { editor.publisherStyles.set(it); commit() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF007AFF), checkedTrackColor = Color(0xFFD0E4FF))
                )
            }
            
            Text("Turn off to customize spacing", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(24.dp))

            // Utility to auto-disable publisher styles
            val autoCommit: () -> Unit = {
                if (editor.publisherStyles.value != false) {
                    editor.publisherStyles.set(false)
                }
                commit()
            }

            
            // 1. Text Alignment (Justify vs Start)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = if (editor.textAlign?.value == org.readium.r2.navigator.preferences.TextAlign.JUSTIFY) R.drawable.ic_baseline_format_align_justify_24 else R.drawable.ic_baseline_format_align_left_24),
                        contentDescription = "Alignment",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Alignment", style = MaterialTheme.typography.bodyMedium)
                }
                
                Row(
                   modifier = Modifier
                       .height(36.dp)
                       .clip(RoundedCornerShape(8.dp))
                       .background(Color(0xFFF0F0F0))
                       .padding(2.dp)
                ) {
                    val current = editor.textAlign?.value ?: org.readium.r2.navigator.preferences.TextAlign.START
                    
                    IconButton(
                        onClick = { editor.textAlign?.set(org.readium.r2.navigator.preferences.TextAlign.START); autoCommit() },
                        modifier = Modifier.size(32.dp).background(if (current == org.readium.r2.navigator.preferences.TextAlign.START) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                    ) {
                        Icon(androidx.compose.ui.res.painterResource(id = R.drawable.ic_baseline_format_align_left_24), contentDescription = "Left", modifier = Modifier.size(20.dp))
                    }
                    
                    IconButton(
                        onClick = { editor.textAlign?.set(org.readium.r2.navigator.preferences.TextAlign.JUSTIFY); autoCommit() },
                        modifier = Modifier.size(32.dp).background(if (current == org.readium.r2.navigator.preferences.TextAlign.JUSTIFY) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                    ) {
                        Icon(androidx.compose.ui.res.painterResource(id = R.drawable.ic_baseline_format_align_justify_24), contentDescription = "Justify", modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))

            // 2. Line Height
            editor.lineHeight?.let { lineHeightPref ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(androidx.compose.ui.res.painterResource(id = R.drawable.ic_baseline_format_line_spacing_24), contentDescription = "Spacing", tint = Color.Gray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Line Spacing", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         val currentValue = lineHeightPref.value ?: 1.4
                         // Compact
                         SelectableIconButton(
                             selected = currentValue < 1.3,
                             onClick = { lineHeightPref.set(1.0); autoCommit() }
                         ) { Text("1.0", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
                         
                         // Normal
                         SelectableIconButton(
                             selected = currentValue >= 1.3 && currentValue <= 1.5,
                             onClick = { lineHeightPref.set(1.4); autoCommit() }
                         ) { Text("1.4", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
                         
                         // Loose
                         SelectableIconButton(
                             selected = currentValue > 1.5,
                             onClick = { lineHeightPref.set(1.8); autoCommit() }
                         ) { Text("1.8", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 3. Word Spacing (NEW)
            editor.wordSpacing?.let { wordSpacingPref ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SpaceBar, contentDescription = "Word Spacing", tint = Color.Gray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Word Spacing", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Row(
                        modifier = Modifier.width(140.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { wordSpacingPref.decrement(); autoCommit() }) { 
                             Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                        
                        // Visual Indicator
                        Box(modifier = Modifier.width(40.dp).height(4.dp).background(Color(0xFFD0E4FF), RoundedCornerShape(2.dp)))

                        IconButton(onClick = { wordSpacingPref.increment(); autoCommit() }) { 
                             Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
             
            Spacer(modifier = Modifier.height(20.dp))

            // 4. Font Weight / Bolding (NEW)
            editor.fontWeight?.let { weightPref ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatBold, contentDescription = "Bold", tint = Color.Gray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Bolding", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Row(
                        modifier = Modifier.width(140.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                         IconButton(onClick = { weightPref.decrement(); autoCommit() }) { 
                             Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                        
                        // Visual Indicator
                        Box(modifier = Modifier.width(40.dp).height(4.dp).background(Color(0xFFD0E4FF), RoundedCornerShape(2.dp)))

                        IconButton(onClick = { weightPref.increment(); autoCommit() }) { 
                             Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 5. Margins
            editor.pageMargins?.let { marginPref ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(androidx.compose.ui.res.painterResource(id = R.drawable.ic_baseline_swap_horiz_24), contentDescription = "Margins", tint = Color.Gray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Margins", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         val current = marginPref.value ?: 1.0
                         // Narrow
                         SelectableIconButton(
                             selected = current < 0.8,
                             onClick = { marginPref.set(0.5); autoCommit() }
                         ) { Icon(androidx.compose.ui.res.painterResource(id = R.drawable.ic_baseline_swap_horiz_24), contentDescription = null, modifier = Modifier.size(16.dp)) } // Reuse icon but smaller or use text "S"
                         
                         // Normal
                         SelectableIconButton(
                             selected = current >= 0.8 && current <= 1.3,
                             onClick = { marginPref.set(1.0); autoCommit() }
                         ) { Text("M", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
                         
                         // Wide
                         SelectableIconButton(
                             selected = current > 1.3,
                             onClick = { marginPref.set(1.8); autoCommit() }
                         ) { Text("L", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 6. Scroll Mode
             editor.scroll?.let { scrollPref ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Scroll", tint = Color.Gray)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Scroll Mode", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Switch(
                        checked = scrollPref.value ?: false,
                        onCheckedChange = { scrollPref.set(it); commit() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF007AFF), checkedTrackColor = Color(0xFFD0E4FF))
                    )
                }
            }
        }
    }
}

@Composable
fun SelectableIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFFE0E0E0) else Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color.Gray) else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
        modifier = Modifier.size(width = 48.dp, height = 32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
