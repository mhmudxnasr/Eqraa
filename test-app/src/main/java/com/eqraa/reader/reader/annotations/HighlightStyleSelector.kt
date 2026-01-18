/*
 * Highlight Tag Selector
 * Premium UI for selecting tags for highlights
 */

package com.eqraa.reader.reader.annotations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eqraa.reader.annotations.DefaultHighlightTags
import com.eqraa.reader.annotations.HighlightTag
import com.eqraa.reader.ui.components.GlassBottomSheet
import com.eqraa.reader.ui.components.GlassChip
import com.eqraa.reader.ui.theme.MonochromeTheme

/**
 * Bottom sheet for selecting highlight style and tags
 */
@Composable
fun HighlightStyleSelector(
    visible: Boolean,
    currentStyle: String, // "highlight" or "underline"
    selectedTags: Set<String>,
    onStyleSelected: (String) -> Unit,
    onTagToggled: (String) -> Unit,
    onDismiss: () -> Unit,
    theme: MonochromeTheme = MonochromeTheme.PureWhite
) {
    if (!visible) return
    
    GlassBottomSheet(theme = theme) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Title
            Text(
                text = "Highlight Style",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = theme.text
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Style selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassChip(
                    selected = currentStyle == "highlight",
                    onClick = { onStyleSelected("highlight") },
                    theme = theme,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Highlight",
                        color = theme.text,
                        fontWeight = if (currentStyle == "highlight") FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                GlassChip(
                    selected = currentStyle == "underline",
                    onClick = { onStyleSelected("underline") },
                    theme = theme,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Underline",
                        color = theme.text,
                        fontWeight = if (currentStyle == "underline") FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Tags section
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = theme.text
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Tag chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DefaultHighlightTags.all()) { tag ->
                    GlassChip(
                        selected = selectedTags.contains(tag.id),
                        onClick = { onTagToggled(tag.id) },
                        theme = theme
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = tag.icon)
                            Text(
                                text = tag.name,
                                color = theme.text,
                                fontWeight = if (selectedTags.contains(tag.id)) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Done button
            com.eqraa.reader.ui.components.GlassButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                theme = theme
            ) {
                Text(
                    text = "Done",
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text
                )
            }
        }
    }
}
