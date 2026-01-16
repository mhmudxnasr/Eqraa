/*
 * Selection Toolbar
 * 
 * A floating Compose toolbar that appears when text is selected.
 * Replaces the old popup_selection.xml layout.
 * Uses monochrome design matching the app's aesthetic.
 */

package com.eqraa.reader.reader.annotations

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Floating toolbar that appears when text is selected.
 * 
 * @param visible Whether the toolbar should be visible
 * @param onHighlight Called when highlight action is selected
 * @param onNote Called when note action is selected
 * @param onDefine Called when define action is selected
 * @param onTranslate Called when translate action is selected
 * @param onAskAi Called when AI action is selected
 * @param modifier Modifier for positioning
 */
@Composable
fun SelectionToolbar(
    visible: Boolean,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onDefine: () -> Unit,
    onTranslate: () -> Unit,
    onAskAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = scaleOut(animationSpec = tween(150)) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                    spotColor = Color.Black.copy(alpha = 0.15f)
                )
                .clip(RoundedCornerShape(12.dp))
                .background(MonoColors.White)
                .border(1.dp, MonoColors.LightGray, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ToolbarAction(
                    icon = Icons.Default.Edit,
                    label = "Highlight",
                    onClick = onHighlight
                )
                
                ToolbarDivider()
                
                ToolbarAction(
                    icon = Icons.Default.NoteAdd,
                    label = "Note",
                    onClick = onNote
                )
                
                ToolbarDivider()
                
                ToolbarAction(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    label = "Define",
                    onClick = onDefine
                )
                
                ToolbarDivider()
                
                ToolbarAction(
                    icon = Icons.Default.Translate,
                    label = "Translate",
                    onClick = onTranslate
                )
                
                ToolbarDivider()
                
                ToolbarAction(
                    icon = Icons.Default.AutoAwesome,
                    label = "AI",
                    onClick = onAskAi
                )
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MonoColors.Black,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MonoColors.DarkGray
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(MonoColors.LightGray)
    )
}

/**
 * Compact version of the toolbar with icons only.
 * Use when space is limited.
 */
@Composable
fun SelectionToolbarCompact(
    visible: Boolean,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onDefine: () -> Unit,
    onTranslate: () -> Unit,
    onAskAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)) + scaleIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(100)) + scaleOut(animationSpec = tween(100)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = Color.Black.copy(alpha = 0.15f)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(MonoColors.White)
                .border(1.dp, MonoColors.LightGray, RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompactToolbarIcon(Icons.Default.Edit, "Highlight", onHighlight)
                CompactToolbarIcon(Icons.Default.NoteAdd, "Note", onNote)
                CompactToolbarIcon(Icons.AutoMirrored.Filled.MenuBook, "Define", onDefine)
                CompactToolbarIcon(Icons.Default.Translate, "Translate", onTranslate)
                CompactToolbarIcon(Icons.Default.AutoAwesome, "AI", onAskAi)
            }
        }
    }
}

@Composable
private fun CompactToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MonoColors.Black,
            modifier = Modifier.size(18.dp)
        )
    }
}
