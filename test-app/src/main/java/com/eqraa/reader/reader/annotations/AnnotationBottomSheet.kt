/*
 * Annotation Bottom Sheet
 * 
 * A unified Compose sheet for creating and editing highlights and notes.
 * Uses monochrome design matching the app's aesthetic.
 */

package com.eqraa.reader.reader.annotations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.eqraa.reader.data.model.Highlight

// Monochrome Design System
object MonoColors {
    val Black = Color(0xFF000000)
    val DarkGray = Color(0xFF333333)
    val Gray = Color(0xFF666666)
    val LightGray = Color(0xFFE0E0E0)
    val OffWhite = Color(0xFFF5F5F5)
    val White = Color(0xFFFFFFFF)
    val DeleteRed = Color(0xFFE53935)
}

// Highlight intensity levels (monochrome)
enum class HighlightIntensity(val alpha: Float, val label: String) {
    LIGHT(0.10f, "Light"),
    MEDIUM(0.25f, "Medium"),
    BOLD(0.40f, "Bold"),
    UNDERLINE(0f, "Underline") // Special case: underline only
}

class AnnotationBottomSheet(
    private val selectedText: String,
    private val existingHighlight: Highlight? = null,
    private val onSave: (HighlightIntensity, String) -> Unit,
    private val onDelete: (() -> Unit)? = null,
    private val onDismiss: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AnnotationEditorContent(
                    selectedText = selectedText,
                    existingHighlight = existingHighlight,
                    onSave = { intensity, note ->
                        onSave(intensity, note)
                        dismiss()
                    },
                    onDelete = onDelete?.let { callback ->
                        {
                            callback()
                            dismiss()
                        }
                    },
                    onDismiss = {
                        onDismiss()
                        dismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun AnnotationEditorContent(
    selectedText: String,
    existingHighlight: Highlight?,
    onSave: (HighlightIntensity, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var selectedIntensity by remember {
        mutableStateOf(
            if (existingHighlight?.style == Highlight.Style.UNDERLINE) {
                HighlightIntensity.UNDERLINE
            } else {
                HighlightIntensity.MEDIUM
            }
        )
    }
    var noteText by remember { mutableStateOf(existingHighlight?.annotation ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MonoColors.White)
            .padding(24.dp)
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MonoColors.LightGray)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (existingHighlight != null) "Edit Annotation" else "New Annotation",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MonoColors.Black
            )
            
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MonoColors.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Text Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MonoColors.OffWhite, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "\"$selectedText\"",
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif,
                color = MonoColors.DarkGray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Intensity Selector
        Text(
            text = "Highlight Style",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MonoColors.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HighlightIntensity.entries.forEach { intensity ->
                IntensityChip(
                    intensity = intensity,
                    isSelected = selectedIntensity == intensity,
                    onClick = { selectedIntensity = intensity }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Note Input
        Text(
            text = "Note (optional)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MonoColors.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            placeholder = {
                Text(
                    "Add your thoughts...",
                    color = MonoColors.LightGray
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MonoColors.Black,
                unfocusedBorderColor = MonoColors.LightGray,
                cursorColor = MonoColors.Black
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Delete Button (only for existing highlights)
            if (onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MonoColors.Black
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MonoColors.LightGray)
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete")
                }
            }

            // Save Button
            Button(
                onClick = { onSave(selectedIntensity, noteText) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MonoColors.Black,
                    contentColor = MonoColors.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun IntensityChip(
    intensity: HighlightIntensity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) MonoColors.Black else MonoColors.White
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MonoColors.Black else MonoColors.LightGray,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Intensity Preview
            if (intensity != HighlightIntensity.UNDERLINE) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            MonoColors.Black.copy(alpha = intensity.alpha),
                            CircleShape
                        )
                        .border(1.dp, MonoColors.Gray, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else {
                // Underline preview
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(MonoColors.Black)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = intensity.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MonoColors.White else MonoColors.DarkGray
            )
        }
    }
}
