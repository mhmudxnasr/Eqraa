/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = ReadingSession.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = [Book.ID],
            childColumns = [ReadingSession.BOOK_ID],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ReadingSession(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    val id: Long = 0,
    
    @ColumnInfo(name = BOOK_ID, index = true)
    val bookId: Long,
    
    @ColumnInfo(name = START_TIME)
    val startTime: Long,
    
    @ColumnInfo(name = END_TIME)
    val endTime: Long,
    
    @ColumnInfo(name = DATE)
    val date: String // Format: YYYY-MM-DD for easy grouping
) {
    val durationMs: Long get() = endTime - startTime
    
    companion object {
        const val TABLE_NAME = "reading_sessions"
        const val ID = "id"
        const val BOOK_ID = "book_id"
        const val START_TIME = "start_time"
        const val END_TIME = "end_time"
        const val DATE = "date"
    }
}
