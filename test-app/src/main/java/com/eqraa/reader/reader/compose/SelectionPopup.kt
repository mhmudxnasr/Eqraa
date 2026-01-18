package com.eqraa.reader.reader.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Custom selection popup for the native EPUB reader.
 * Displays AI, Translate, and Define action buttons.
 */
@Composable
fun SelectionPopup(
    selectedText: String,
    onTranslate: () -> Unit,
    onAI: () -> Unit,
    onDefine: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = 8.dp,
        color = Color(0xFF1E1E1E)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ActionButton(
                icon = Icons.Default.Translate,
                label = "Translate",
                onClick = {
                    onTranslate()
                    onDismiss()
                }
            )
            ActionButton(
                icon = Icons.Default.AutoAwesome,
                label = "AI",
                onClick = {
                    onAI()
                    onDismiss()
                }
            )
            ActionButton(
                icon = Icons.Default.MenuBook,
                label = "Define",
                onClick = {
                    onDefine()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp
        )
    }
}
