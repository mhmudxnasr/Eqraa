package com.eqraa.reader.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import com.eqraa.reader.settings.ReadingPreferences

@Composable
fun AiAssistantOverlay(
    text: String,
    bookTitle: String = "Current Book",
    chapterTitle: String = "Chapter",
    onDismiss: () -> Unit,
    onSummarizeChapter: () -> Unit = {},
    onExplainSymbolism: () -> Unit = {},
    onAskQuestion: (String) -> Unit = {},
    initialAction: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ReadingPreferences(context) }
    var aiProvider by remember { mutableStateOf(prefs.aiProvider) }
    var aiService by remember { mutableStateOf(AiServiceFactory.getService(context)) }
    
    var inputText by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showResponse by remember { mutableStateOf(false) }

    // Auto-trigger initial action
    LaunchedEffect(initialAction) {
        if (initialAction != null) {
            showResponse = true
            isLoading = true
            aiResponse = ""
            val result = when(initialAction) {
                // Map "ExplainArabic" to translateToArabic for the structured output
                "ExplainArabic" -> aiService.translateToArabic(text) 
                "Summarize" -> aiService.summarizeChapter(text, bookTitle, chapterTitle)
                "Explain" -> aiService.breakDownSelection(text, bookTitle, chapterTitle)
                else -> Result.failure(Exception("Unknown"))
            }
            aiResponse = result.getOrElse { "Operation failed." }
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)) // Darker dim for contrast
                .clickable(onClick = onDismiss)
        ) {
            
            // 1. RESPONSE CARD (Centered)
            if (showResponse) {
                AnimatedVisibility(
                    visible = showResponse,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 140.dp) // Leave room for dock
                ) {
                    FloatingResponseCard(
                        response = aiResponse,
                        isLoading = isLoading,
                        providerIcon = when (aiProvider) {
                            3 -> Icons.Default.AutoAwesome // Gemini/Gemma
                            4 -> Icons.Default.Memory // Cerebras
                            else -> Icons.Default.Bolt // Groq
                        }
                    )
                }
            }

            // 2. STACKED DOCK (Bottom)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                StackedFloatingDock(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onSend = {
                        scope.launch {
                            showResponse = true
                            isLoading = true
                            aiResponse = ""
                            val result = aiService.askQuestion(inputText, text, bookTitle, chapterTitle)
                            aiResponse = result.getOrElse { "I couldn't find an answer." }
                            isLoading = false
                            inputText = ""
                        }
                    },
                    currentProvider = aiProvider,
                    onProviderSelect = { newProvider ->
                        aiProvider = newProvider
                        prefs.aiProvider = newProvider
                        aiService = AiServiceFactory.getService(context)
                    },
                    onAction = { actionType ->
                        scope.launch {
                            showResponse = true
                            isLoading = true
                            aiResponse = ""
                            val result = when(actionType) {
                                "ExplainArabic" -> aiService.translateToArabic(text)
                                "Summarize" -> aiService.summarizeChapter(text, bookTitle, chapterTitle)
                                "Explain" -> aiService.breakDownSelection(text, bookTitle, chapterTitle)
                                else -> Result.failure(Exception("Unknown"))
                            }
                            aiResponse = result.getOrElse { "Operation failed." }
                            isLoading = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingResponseCard(
    response: String,
    isLoading: Boolean,
    providerIcon: ImageVector
) {
    // Typewriter effect: display characters one by one
    var displayedText by remember { mutableStateOf("") }
    var lastResponse by remember { mutableStateOf("") }
    
    LaunchedEffect(response) {
        if (response != lastResponse && response.isNotEmpty() && !isLoading) {
            lastResponse = response
            displayedText = ""
            response.forEachIndexed { index, _ ->
                kotlinx.coroutines.delay(15) // 15ms per character
                displayedText = response.substring(0, index + 1)
            }
        } else if (isLoading) {
            displayedText = ""
        }
    }
    
    Card(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0xFFF2F2F2),
        elevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .heightIn(min = 200.dp, max = 500.dp)
            .clickable(enabled = false) {}
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Centered Icon with pulse animation
            val infiniteTransition = rememberInfiniteTransition(label = "icon_pulse")
            val iconScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isLoading) 1.15f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "icon_scale"
            )
            
            Icon(
                imageVector = providerIcon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                // Animated loading dots
                val dotCount by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 3f,
                    animationSpec = infiniteRepeatable(tween(800)),
                    label = "dots"
                )
                Text(
                    text = "Thinking" + ".".repeat(dotCount.toInt() + 1),
                    fontSize = 16.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.SansSerif
                )
            } else {
                // Typewriter text with cursor blink
                val showCursor by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                    label = "cursor"
                )
                
                Text(
                    text = displayedText + if (displayedText.length < response.length) "▌" else "",
                    fontSize = 17.sp,
                    lineHeight = 28.sp,
                    color = Color.Black,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun StackedFloatingDock(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    currentProvider: Int,
    onProviderSelect: (Int) -> Unit,
    onAction: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        backgroundColor = Color(0xFF1E1E1E), // Dark dock
        elevation = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = false) {}
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            // 1. Top: Input Field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2C2C2C))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Ask anything...",
                        color = Color.Gray,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.SansSerif
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Bottom: Icon Row (Providers + Actions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Provider Group (Visual separator)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DockIcon(Icons.Default.AutoAwesome, currentProvider == 3) { onProviderSelect(3) } // Gemma
                    DockIcon(Icons.Default.Bolt, currentProvider == 0) { onProviderSelect(0) } // Groq
                    DockIcon(Icons.Default.Memory, currentProvider == 4) { onProviderSelect(4) } // Cerebras
                }

                Spacer(modifier = Modifier.width(16.dp))
                
                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Color.Gray.copy(alpha = 0.3f))
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Right: Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DockIcon(Icons.Default.Translate, false) { onAction("ExplainArabic") }
                    DockIcon(Icons.Default.Description, false) { onAction("Summarize") }
                    
                    if (inputText.isNotBlank()) {
                         // Send Button (Replaces Bulb when typing)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = onSend),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        DockIcon(Icons.Default.Psychology, false) { onAction("Explain") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockIcon(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isSelected) Color.White else Color.Gray
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(26.dp)
            .clickable(onClick = onClick)
    )
}
