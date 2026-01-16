package com.eqraa.reader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eqraa.reader.data.model.WordCard
import kotlinx.coroutines.flow.Flow

@Dao
interface WordCardDao {
    @Query("SELECT * FROM word_cards ORDER BY nextReviewAt ASC")
    fun getAllCards(): Flow<List<WordCard>>

    @Query("SELECT * FROM word_cards WHERE nextReviewAt <= :currentTime")
    suspend fun getCardsDueForReview(currentTime: Long): List<WordCard>

    @Query("SELECT * FROM word_cards WHERE word = :word LIMIT 1")
    suspend fun getCardByWord(word: String): WordCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: WordCard): Long

    @Update
    suspend fun update(card: WordCard)

    @Delete
    suspend fun delete(card: WordCard)
    
    @Query("SELECT COUNT(*) FROM word_cards")
    fun getCount(): Flow<Int>
}
