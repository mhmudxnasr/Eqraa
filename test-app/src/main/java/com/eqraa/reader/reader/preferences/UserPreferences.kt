/*
 * Copyright 2022 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package com.eqraa.reader.reader.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.readium.adapter.exoplayer.audio.ExoPlayerPreferencesEditor
import org.readium.adapter.pdfium.navigator.PdfiumPreferencesEditor
import org.readium.r2.navigator.epub.EpubPreferencesEditor
import org.readium.r2.navigator.preferences.*
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.epub.EpubLayout
import com.eqraa.reader.reader.ReaderViewModel
import com.eqraa.reader.reader.tts.TtsPreferencesEditor

@Composable
fun UserPreferences(
    model: UserPreferencesViewModel<*, *>,
    title: String,
) {
    val editor by model.editor.collectAsState()

    UserPreferences(
        editor = editor,
        commit = model::commit,
        title = title
    )
}

@Composable
private fun <P : Configurable.Preferences<P>, E : PreferencesEditor<P>> UserPreferences(
    editor: E,
    commit: () -> Unit,
    title: String,
) {

    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Button(
            onClick = { editor.clear(); commit() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black)
        ) {
            Text("RESET SETTINGS")
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        when (editor) {
            is EpubPreferencesEditor -> {
                if (editor.layout == EpubLayout.REFLOWABLE) {
                    ReflowablePreferences(editor, commit)
                } else {
                    FixedLayoutPreferences(editor, commit)
                }
            }
            is PdfiumPreferencesEditor -> FixedLayoutPreferences(editor, commit)
            is TtsPreferencesEditor -> TtsPreferences(editor, commit)
            is ExoPlayerPreferencesEditor -> {} 
        }
    }
}

@Composable
private fun ReflowablePreferences(
    editor: EpubPreferencesEditor,
    commit: () -> Unit
) {
    SectionTitle("APPEARANCE")
    editor.theme?.let { themePref ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonoButton("Light", themePref.value == Theme.LIGHT) { themePref.set(Theme.LIGHT); commit() }
            MonoButton("Sepia", themePref.value == Theme.SEPIA) { themePref.set(Theme.SEPIA); commit() }
            MonoButton("Dark", themePref.value == Theme.DARK) { themePref.set(Theme.DARK); commit() }
        }
    }

    SectionTitle("TYPOGRAPHY")
    editor.fontFamily?.let { fontPref ->
        // Simplified font selector
        MonoButton("Original Font", fontPref.value == null) { fontPref.set(null); commit() }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        editor.fontSize?.let { sizePref ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Size", style = MaterialTheme.typography.labelMedium)
                Row {
                    SmallButton("-") { sizePref.decrement(); commit() }
                    Spacer(Modifier.width(8.dp))
                    SmallButton("+") { sizePref.increment(); commit() }
                }
            }
        }
        
        editor.fontWeight?.let { weightPref ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Weight", style = MaterialTheme.typography.labelMedium)
                Row {
                    SmallButton("-") { weightPref.decrement(); commit() }
                    Spacer(Modifier.width(8.dp))
                    SmallButton("+") { weightPref.increment(); commit() }
                }
            }
        }
    }

    SectionTitle("LAYOUT")
    editor.scroll?.let { scrollPref ->
        val isScroll = scrollPref.value ?: false
        MonoButton(if(isScroll) "Scroll: ON" else "Scroll: OFF", isScroll) { 
            scrollPref.set(!isScroll); commit() 
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    editor.textAlign?.let { alignPref ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val current = alignPref.value ?: alignPref.effectiveValue
            MonoButton("L", current == ReadiumTextAlign.LEFT) { alignPref.set(ReadiumTextAlign.LEFT); commit() }
            MonoButton("C", current == ReadiumTextAlign.CENTER) { alignPref.set(ReadiumTextAlign.CENTER); commit() }
            MonoButton("R", current == ReadiumTextAlign.RIGHT) { alignPref.set(ReadiumTextAlign.RIGHT); commit() }
            MonoButton("J", current == ReadiumTextAlign.JUSTIFY) { alignPref.set(ReadiumTextAlign.JUSTIFY); commit() }
        }
    }
}

@Composable
private fun FixedLayoutPreferences(
    editor: PreferencesEditor<*>,
    commit: () -> Unit
) {
    Text("Fixed Layout Settings")
}

@Composable
private fun TtsPreferences(
    editor: TtsPreferencesEditor,
    commit: () -> Unit
) {
    SectionTitle("VOICE SETTINGS")
    editor.speed?.let { 
        Text("Speed")
        Row {
             SmallButton("-") { it.decrement(); commit() }
             Spacer(Modifier.width(16.dp))
             SmallButton("+") { it.increment(); commit() }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun MonoButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.Black else Color.LightGray,
            contentColor = if (isSelected) Color.White else Color.Black
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text)
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.LightGray,
            contentColor = Color.Black
        ),
        modifier = Modifier.size(40.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
