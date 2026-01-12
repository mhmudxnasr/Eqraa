/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.readium.r2.testapp.data.model.ReadingSession

@Dao
interface StatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSession): Long

    @Query("SELECT * FROM ${ReadingSession.TABLE_NAME} WHERE ${ReadingSession.BOOK_ID} = :bookId ORDER BY ${ReadingSession.START_TIME} DESC")
    fun getSessionsForBook(bookId: Long): Flow<List<ReadingSession>>

    @Query("SELECT * FROM ${ReadingSession.TABLE_NAME} ORDER BY ${ReadingSession.START_TIME} DESC")
    fun getAllSessions(): Flow<List<ReadingSession>>

    @Query("SELECT COALESCE(SUM(${ReadingSession.END_TIME} - ${ReadingSession.START_TIME}), 0) FROM ${ReadingSession.TABLE_NAME}")
    fun getTotalReadingTimeMs(): Flow<Long>

    @Query("SELECT * FROM ${ReadingSession.TABLE_NAME} WHERE ${ReadingSession.DATE} BETWEEN :startDate AND :endDate ORDER BY ${ReadingSession.DATE} ASC")
    fun getSessionsForDateRange(startDate: String, endDate: String): Flow<List<ReadingSession>>

    @Query("SELECT DISTINCT ${ReadingSession.DATE} FROM ${ReadingSession.TABLE_NAME} WHERE ${ReadingSession.DATE} LIKE :monthPrefix ORDER BY ${ReadingSession.DATE} ASC")
    fun getActiveDaysForMonth(monthPrefix: String): Flow<List<String>>
}
