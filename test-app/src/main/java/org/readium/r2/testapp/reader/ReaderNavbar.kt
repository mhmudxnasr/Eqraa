package org.readium.r2.testapp.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.readium.r2.testapp.utils.extensions.asStateWhenStarted

@Composable
fun ReaderNavbar(
    model: ReaderViewModel,
    modifier: Modifier = Modifier
) {
    val locator by model.currentLocator.asStateWhenStarted()
    val currentPosition by model.currentPosition.asStateWhenStarted(null)
    val totalPositions by model.totalPositions.asStateWhenStarted()
    
    // Calculate progress percentage
    val progress = locator?.locations?.totalProgression?.toFloat() 
        ?: (currentPosition?.let { it.toFloat() / totalPositions.coerceAtLeast(1) })
        ?: 0f

    val chapterTitle = locator?.title ?: ""

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.7f),
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 0.dp) // Progress bar will be at the very bottom
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (chapterTitle.isNotBlank()) {
                    Text(
                        text = chapterTitle,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPosition != null && totalPositions > 0) {
                        Text(
                            text = "Page $currentPosition of $totalPositions",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val percent = (progress * 100).toInt()
                        Text(
                            text = "$percent% completed",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // Premium Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFF007AFF), // iOS Style Blue
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}
