package com.eqraa.reader.flashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.eqraa.reader.data.db.AppDatabase
import com.eqraa.reader.data.model.WordCard
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class FlashcardActivity : ComponentActivity() {

    private val wordCardDao by lazy { AppDatabase.getDatabase(this).wordCardDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            FlashcardScreen(
                onBack = { finish() }
            )
        }
    }

    @Composable
    fun FlashcardScreen(onBack: () -> Unit) {
        var cards by remember { mutableStateOf<List<WordCard>>(emptyList()) }
        var currentCardIndex by remember { mutableStateOf(0) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            // Load cards due for review (or new ones)
            // Ideally should be reviewQueue + newQueue. simpler for now:
            val allCards = wordCardDao.getCardsDueForReview(System.currentTimeMillis())
            cards = allCards
            isLoading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Vocabulary Builder") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    backgroundColor = Color.White,
                    contentColor = Color.Black,
                    elevation = 0.dp
                )
            },
            backgroundColor = Color(0xFFF5F5F5)
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (cards.isEmpty()) {
                    EmptyState()
                } else if (currentCardIndex < cards.size) {
                    val card = cards[currentCardIndex]
                    ReviewSession(
                        card = card,
                        onResult = { quality ->
                            updateCard(card, quality)
                            currentCardIndex++
                        }
                    )
                } else {
                    FinishedState(onBack)
                }
            }
        }
    }

    private fun updateCard(card: WordCard, quality: Int) {
        lifecycleScope.launch {
            // SM-2 Algorithm Simplified
            // quality: 0 (Again), 3 (Hard), 4 (Good), 5 (Easy)
            
            val newRepetition: Int
            val newEase: Float
            val intervalDays: Int

            if (quality < 3) {
                newRepetition = 0
                intervalDays = 0 // Review today/now
                newEase = card.easeFactor // Unchanged or penalty?
            } else {
                newRepetition = card.repetitionLevel + 1
                newEase = card.easeFactor + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f))
                
                intervalDays = when (newRepetition) {
                    1 -> 1
                    2 -> 6
                    else -> Math.round(card.repetitionLevel * newEase).toInt() // Simplified interval calc
                }
            }

            val nextReview = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(intervalDays.toLong())

            val updatedCard = card.copy(
                repetitionLevel = newRepetition,
                easeFactor = Math.max(1.3f, newEase), // Minimum ease
                nextReviewAt = nextReview
            )
            
            wordCardDao.update(updatedCard)
        }
    }

    @Composable
    fun ReviewSession(card: WordCard, onResult: (Int) -> Unit) {
        var isFlipped by remember { mutableStateOf(false) }
        val rotation by animateFloatAsState(
            targetValue = if (isFlipped) 180f else 0f,
            animationSpec = tween(400)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Card Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .clickable { isFlipped = !isFlipped }
            ) {
                if (rotation <= 90f) {
                    // FRONT
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = Color.White,
                        elevation = 8.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = card.word,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // BACK
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = Color.White,
                        elevation = 8.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f } // Fix text mirroring
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = card.word,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            if (!card.definition.isNullOrBlank()) {
                                Text(
                                    text = card.definition,
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                            
                            if (!card.contextSentence.isNullOrBlank()) {
                                Text(
                                    text = "\"${card.contextSentence}\"",
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Buttons
            if (isFlipped) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RatingButton("Again", Color(0xFFFF5252)) { onResult(0) }
                    RatingButton("Hard", Color(0xFFFF9800)) { onResult(3) }
                    RatingButton("Good", Color(0xFF4CAF50)) { onResult(4) }
                    RatingButton("Easy", Color(0xFF2196F3)) { onResult(5) }
                }
            } else {
                Text(
                    "Tap card to show answer",
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }

    @Composable
    fun RatingButton(label: String, color: Color, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(backgroundColor = color),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(80.dp)
        ) {
            Text(label, color = Color.White, fontSize = 12.sp)
        }
    }

    @Composable
    fun EmptyState() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("No cards due for review!", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("Go read more books to add words.", color = Color.Gray)
        }
    }
    
    @Composable
    fun FinishedState(onBack: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉 Session Complete!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) {
                Text("Return to Library")
            }
        }
    }
}
