package com.eqraa.reader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "word_cards")
data class WordCard(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val definition: String?,
    val contextSentence: String?, // The sentence where the word was found
    val translation: String?,
    val sourceBookId: Long?, // FK to Book
    val createdAt: Long = System.currentTimeMillis(),
    
    // Spaced Repetition Fields
    val repetitionLevel: Int = 0, // 0 = New, 1-5 = Graduated
    val nextReviewAt: Long = 0,
    val easeFactor: Float = 2.5f 
) : Serializable
