package org.readium.r2.testapp.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.readium.r2.testapp.settings.ReadingPreferences

/**
 * AI Assistant Overlay - Clean minimal design matching provided mockup
 */
@Composable
fun AiAssistantOverlay(
    text: String,
    bookTitle: String = "Current Book",
    chapterTitle: String = "Chapter",
    onDismiss: () -> Unit,
    onSummarizeChapter: () -> Unit = {},
    onExplainSymbolism: () -> Unit = {},
    onAskQuestion: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ReadingPreferences(context) }
    var aiService by remember { mutableStateOf(AiServiceFactory.getService(context)) }
    var inputText by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showResponse by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            backgroundColor = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
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

                // Header with AI Icon
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "AI Assistant",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    Text(
                        text = "Context loaded",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                // Action Buttons
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    // Translate Button
                    ActionButton(
                        icon = Icons.Default.Translate,
                        title = "Translate",
                        subtitle = "Convert text language",
                        onClick = {
                            scope.launch {
                                isLoading = true
                                showResponse = true
                                val result = aiService.translateToArabic(text)
                                aiResponse = result.getOrElse { "Translation failed" }
                                isLoading = false
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Summarize Button
                    ActionButton(
                        icon = Icons.Default.Summarize,
                        title = "Summarize",
                        subtitle = "Get a brief summary",
                        onClick = {
                            scope.launch {
                                isLoading = true
                                showResponse = true
                                val result = aiService.summarizeChapter(text, bookTitle, chapterTitle)
                                aiResponse = result.getOrElse { "Unable to summarize" }
                                isLoading = false
                            }
                            onSummarizeChapter()
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Explain Button
                    ActionButton(
                        icon = Icons.Default.Lightbulb,
                        title = "Explain",
                        subtitle = "Understand the meaning",
                        onClick = {
                            scope.launch {
                                isLoading = true
                                showResponse = true
                                val result = aiService.breakDownSelection(text, bookTitle, chapterTitle)
                                aiResponse = result.getOrElse { "Unable to explain" }
                                isLoading = false
                            }
                            onExplainSymbolism()
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Explain in Arabic Button
                    ActionButton(
                        icon = Icons.Default.Language,
                        title = "شرح بالعربي",
                        subtitle = "Explain selected text in Arabic",
                        onClick = {
                            scope.launch {
                                isLoading = true
                                showResponse = true
                                val result = aiService.explainInArabic(text, bookTitle, chapterTitle)
                                aiResponse = result.getOrElse { "تعذر الشرح" }
                                isLoading = false
                            }
                        }
                    )

                    // Response Area
                    if (showResponse) {
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            Card(
                                backgroundColor = Color(0xFFF9F9F9),
                                shape = RoundedCornerShape(16.dp),
                                elevation = 0.dp
                            ) {
                                Text(
                                    text = aiResponse,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = Color.Black,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Input Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Context Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "❝",
                                fontSize = 18.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = text.take(50) + if (text.length > 50) "..." else "",
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                fontStyle = FontStyle.Italic,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chat Input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = "Ask anything...",
                                    color = Color.Gray,
                                    fontSize = 15.sp
                                )
                            },
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            singleLine = true
                        )

                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    scope.launch {
                                        isLoading = true
                                        showResponse = true
                                        val result = aiService.askQuestion(inputText, text, bookTitle, chapterTitle)
                                        aiResponse = result.getOrElse { "Unable to answer" }
                                        isLoading = false
                                        inputText = ""
                                    }
                                    onAskQuestion(inputText)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Color.Black else Color.LightGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color.White,
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, Color(0xFFF0F0F0), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                tint = Color(0xFFD0D0D0),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
