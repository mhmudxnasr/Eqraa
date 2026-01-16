package com.eqraa.reader.reader

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle

import com.eqraa.reader.settings.ReadingPreferences

// Color constants for consistent theming
// Color constants for Light Theme
private val LightBackground = Color(0xFFFFFFFF) // Pure White
private val LightSurface = Color(0xFFF5F5F5) // Very light gray for backgrounds
private val AccentBlue = Color(0xFF007AFF) // iOS Blue
private val TextPrimary = Color(0xFF000000) // Black
private val TextSecondary = Color(0xFF8E8E93) // Gray
private val DividerColor = Color(0xFFE5E5EA) // Light Divider

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
    
    // AI Translation state
    var aiTranslation by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }
    
    // TTS helper
    val ttsHelper = remember { TtsHelper(context) }
    val prefs = remember { ReadingPreferences(context) }
    
    // Initialize AI Service (Forced to Groq as per user request)
    // We instantiate GroqService directly to ensure it is always used for translations
    // regardless of the user's "AI Provider" setting for other features.
    val aiService = remember { GroqService(prefs.groqApiKey) }
    
    // Load dictionary on launch
    LaunchedEffect(text) {
        isLoadingDictionary = true
        val result = DictionaryService.lookup(text)
        dictionaryEntry = result.getOrNull()
        isLoadingDictionary = false
    }
    
    // Load AI Arabic translation when translate tab is selected
    LaunchedEffect(selectedTab, text) {
        if (selectedTab == 1 && aiTranslation.isEmpty()) {
            isAiLoading = true
            // Use AI Service for translation
            // Note: We use the generic translateToArabic which uses the active service
            val result = aiService.translateToArabic(text)
            aiTranslation = result.getOrElse { "Could not generate translation." }
            isAiLoading = false
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
            shape = RoundedCornerShape(24.dp),
            backgroundColor = LightBackground,
            contentColor = TextPrimary,
            elevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFE0E0E0))
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Word Display
                Text(
                    text = dictionaryEntry?.word?.replaceFirstChar { it.uppercase() } 
                        ?: text.replaceFirstChar { it.uppercase() },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // Pronunciation
                Text(
                    text = dictionaryEntry?.phonetic ?: "/.../",
                    fontSize = 16.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Spacer(modifier = Modifier.height(24.dp))

                // Tab Selector (Segmented Control)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LightSurface)
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

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons Row (Minimalist)
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
                        icon = Icons.Default.BookmarkBorder, // Outline style
                        label = "Save",
                        onClick = { 
                            onSave(text)
                            android.widget.Toast.makeText(context, "Word saved!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )

                }

                Spacer(modifier = Modifier.height(20.dp))

                Divider(color = DividerColor, thickness = 1.dp)

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
                        if (isAiLoading) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AccentBlue)
                            }
                        } else {
                             // Plain Text AI Response
                            // Render with Basic Markdown (Bold support)
                             val annotatedString = remember(aiTranslation) {
                                 buildAnnotatedString {
                                     val parts = aiTranslation.split("**")
                                     parts.forEachIndexed { index, part ->
                                         if (index % 2 == 1) {
                                             withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                 append(part)
                                             }
                                         } else {
                                             append(part)
                                         }
                                     }
                                 }
                             }
                             
                             Text(
                                 text = annotatedString,
                                 fontSize = 18.sp,
                                 lineHeight = 28.sp,
                                 color = TextPrimary,
                                 fontFamily = FontFamily.Serif,
                                 modifier = Modifier.fillMaxWidth()
                             )
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
                .size(48.dp)
                .clip(CircleShape)
                .background(LightSurface) // Light gray circle
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
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
                Divider(color = DividerColor, thickness = 1.dp)
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
                        color = DividerColor,
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
            .background(LightSurface)
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
