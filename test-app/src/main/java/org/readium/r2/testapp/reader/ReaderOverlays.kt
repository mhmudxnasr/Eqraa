package org.readium.r2.testapp.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.readium.r2.testapp.settings.ReadingPreferences

// Color constants for consistent theming
private val DarkBackground = Color(0xFF1C1C1E)
private val DarkSurface = Color(0xFF2C2C2E)
private val AccentBlue = Color(0xFF007AFF)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF8E8E93)
private val LightBackground = Color(0xFFF2F2F7)

/**
 * Enhanced Dictionary/Translate Overlay with real API integration.
 */
@Composable
fun DictionaryOverlay(
    text: String,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit = {},
    onAddNote: (String) -> Unit = {},
    onCopy: (String) -> Unit = {},
    onPronounce: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(initialTab) }
    
    // Dictionary state
    var dictionaryEntry by remember { mutableStateOf<DictionaryEntry?>(null) }
    var isLoadingDictionary by remember { mutableStateOf(true) }
    
    // Translation state
    var translations by remember { mutableStateOf<List<Translation>>(emptyList()) }
    var isLoadingTranslation by remember { mutableStateOf(true) }
    
    // TTS helper
    val ttsHelper = remember { TtsHelper(context) }
    
    // Load dictionary on launch
    LaunchedEffect(text) {
        isLoadingDictionary = true
        val result = DictionaryService.lookup(text)
        dictionaryEntry = result.getOrNull()
        isLoadingDictionary = false
    }
    
    // Load Arabic translation when translate tab is selected
    LaunchedEffect(selectedTab, text) {
        if (selectedTab == 1 && translations.isEmpty()) {
            isLoadingTranslation = true
            val result = TranslationService.translateToArabic(text)
            result.onSuccess { translation ->
                translations = listOf(translation)
            }
            isLoadingTranslation = false
        }
    }
    
    // Cleanup TTS on dispose
    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            backgroundColor = DarkBackground,
            contentColor = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color(0xFF3A3A3C))
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Word Display
                Text(
                    text = dictionaryEntry?.word?.replaceFirstChar { it.uppercase() } 
                        ?: text.replaceFirstChar { it.uppercase() },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // Pronunciation
                Text(
                    text = dictionaryEntry?.phonetic ?: "",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .padding(4.dp)
                ) {
                    TabButton(
                        text = "Dictionary",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        text = "Translate",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionIconButton(
                        icon = Icons.Default.VolumeUp,
                        label = "Pronounce",
                        onClick = { 
                            ttsHelper.speak(text)
                        }
                    )
                    ActionIconButton(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy",
                        onClick = { 
                            ClipboardHelper.copyToClipboard(context, text)
                        }
                    )
                    ActionIconButton(
                        icon = Icons.Default.Bookmark,
                        label = "Save",
                        onClick = { 
                            onSave(text)
                            android.widget.Toast.makeText(context, "Word saved!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    ActionIconButton(
                        icon = Icons.Default.Edit,
                        label = "Note",
                        onClick = { 
                            onAddNote(text)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Divider(color = DarkSurface, thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                // Content Area (scrollable)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedTab == 0) {
                        if (isLoadingDictionary) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AccentBlue)
                            }
                        } else {
                            DictionaryContent(dictionaryEntry)
                        }
                    } else {
                        if (isLoadingTranslation) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AccentBlue)
                            }
                        } else {
                            TranslateContent(translations)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (selected) AccentBlue else Color.Transparent,
            contentColor = if (selected) Color.White else TextSecondary
        ),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(36.dp)
    ) {
        Text(text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(DarkSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun DictionaryContent(entry: DictionaryEntry?) {
    if (entry == null) {
        Text(
            text = "No definition found",
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        return
    }
    
    Column {
        entry.meanings.forEachIndexed { index, meaning ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = DarkSurface, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            meaning.definitions.forEachIndexed { defIndex, definition ->
                DefinitionSection(
                    partOfSpeech = if (defIndex == 0) meaning.partOfSpeech else "",
                    definitionNumber = defIndex + 1,
                    definition = definition.definition,
                    example = definition.example
                )
                if (defIndex < meaning.definitions.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        if (entry.synonyms.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SYNONYMS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entry.synonyms) { synonym ->
                    SynonymChip(text = synonym)
                }
            }
        }
    }
}

@Composable
private fun DefinitionSection(
    partOfSpeech: String,
    definitionNumber: Int,
    definition: String,
    example: String? = null
) {
    Column {
        if (partOfSpeech.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = partOfSpeech,
                    color = AccentBlue,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = definitionNumber.toString(),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = definition,
            fontSize = 16.sp,
            lineHeight = 24.sp
        )

        if (example != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp)
                    .border(
                        width = 2.dp,
                        color = DarkSurface,
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                Text(
                    text = "\"$example\"",
                    fontStyle = FontStyle.Italic,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SynonymChip(text: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .clickable { 
                ClipboardHelper.copyToClipboard(context, text, "Synonym")
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = TextPrimary
        )
    }
}

@Composable
private fun TranslateContent(translations: List<Translation>) {
    if (translations.isEmpty()) {
        Text(
            text = "No translations available",
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        return
    }
    
    Column {
        translations.forEach { translation ->
            TranslationEntry(
                language = getLanguageName(translation.targetLanguage),
                translation = translation.translatedText
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun getLanguageName(code: String): String {
    return when (code) {
        "fr" -> "French"
        "ar" -> "Arabic"
        "es" -> "Spanish"
        "de" -> "German"
        "it" -> "Italian"
        "pt" -> "Portuguese"
        "zh" -> "Chinese"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        "ru" -> "Russian"
        else -> code.uppercase()
    }
}

@Composable
private fun TranslationEntry(language: String, translation: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.clickable {
            ClipboardHelper.copyToClipboard(context, translation, "Translation")
        }
    ) {
        Text(
            text = language,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = translation,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
