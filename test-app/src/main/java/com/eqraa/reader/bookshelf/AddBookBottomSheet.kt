package com.eqraa.reader.bookshelf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddBookBottomSheet(
    private val onImportAppStorage: () -> Unit,
    private val onConnectCalibre: () -> Unit,
    private val onVocabularyBuilder: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AddBookScreen(
                    onImportAppStorage = { dismiss(); onImportAppStorage() },
                    onConnectCalibre = { dismiss(); onConnectCalibre() },
                    onVocabularyBuilder = { dismiss(); onVocabularyBuilder() }
                )
            }
        }
    }
}

@Composable
fun AddBookScreen(
    onImportAppStorage: () -> Unit,
    onConnectCalibre: () -> Unit,
    onVocabularyBuilder: () -> Unit
) {
    // Glassy White Theme
    val whiteGlass = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.95f),
            Color(0xFFF0F0F0).copy(alpha = 0.98f)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(whiteGlass)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Gray.copy(alpha = 0.3f))
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Add a Book",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Options
        GlassyOptionCard(
            title = "Import to app storage",
            subtitle = "Copy books to your local library",
            icon = Icons.Default.FileUpload,
            onClick = onImportAppStorage
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassyOptionCard(
            title = "Connect to Calibre",
            subtitle = "Sync from your local Calibre server",
            icon = Icons.Default.Devices,
            onClick = onConnectCalibre
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassyOptionCard(
            title = "Vocabulary Builder",
            subtitle = "Practice words and flashcards",
            icon = Icons.Default.Spellcheck,
            onClick = onVocabularyBuilder
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun GlassyOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.04f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.9f)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.Black.copy(alpha = 0.5f)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.2f),
            modifier = Modifier.size(20.dp)
        )
    }
}
