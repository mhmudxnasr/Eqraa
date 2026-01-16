/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.data

import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.eqraa.reader.data.db.StatsDao
import com.eqraa.reader.data.model.ReadingSession
import timber.log.Timber

class StatsRepository(
    private val statsDao: StatsDao,
    private val sessionSyncManager: ReadingSessionSyncManager? = null
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var userId: String? = null
    private var scope: kotlinx.coroutines.CoroutineScope? = null

    fun initialize(userId: String, scope: kotlinx.coroutines.CoroutineScope) {
        this.userId = userId
        this.scope = scope
    }

    suspend fun insertSession(bookId: Long, startTime: Long, endTime: Long) {
        val session = ReadingSession(
            bookId = bookId,
            startTime = startTime,
            endTime = endTime,
            date = dateFormat.format(Date(startTime))
        )
        statsDao.insertSession(session)
        
        // Sync to Supabase
        scope?.launch {
            sessionSyncManager?.syncSession(session)
        }
    }

    private fun syncStatsToServer() {
        // Handled by ReadingSessionSyncManager
    }

    fun totalReadingTimeMs(): Flow<Long> = statsDao.getTotalReadingTimeMs()

    fun readingTimeThisWeekMs(): Flow<Long> {
        val calendar = Calendar.getInstance()
        val endDate = dateFormat.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = dateFormat.format(calendar.time)

        return statsDao.getSessionsForDateRange(startDate, endDate).map { sessions ->
            sessions.sumOf { it.durationMs }
        }
    }

    fun readingTimeLastWeekMs(): Flow<Long> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val endDate = dateFormat.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = dateFormat.format(calendar.time)

        return statsDao.getSessionsForDateRange(startDate, endDate).map { sessions ->
            sessions.sumOf { it.durationMs }
        }
    }

    fun activityForLast7Days(): Flow<List<Long>> {
        val calendar = Calendar.getInstance()
        val dates = mutableListOf<String>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            dates.add(dateFormat.format(cal.time))
        }
        val startDate = dates.first()
        val endDate = dates.last()

        return statsDao.getSessionsForDateRange(startDate, endDate).map { sessions ->
            dates.map { date ->
                sessions.filter { it.date == date }.sumOf { it.durationMs }
            }
        }
    }

    suspend fun currentStreak(): Int {
        val allSessions = statsDao.getAllSessions().first()
        if (allSessions.isEmpty()) return 0

        val uniqueDates = allSessions.map { it.date }.distinct().sortedDescending()
        if (uniqueDates.isEmpty()) return 0

        val today = dateFormat.format(Date())
        val yesterday = run {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            dateFormat.format(cal.time)
        }

        // Streak only counts if read today or yesterday
        if (uniqueDates.first() != today && uniqueDates.first() != yesterday) {
            return 0
        }

        var streak = 0
        val calendar = Calendar.getInstance()
        if (uniqueDates.first() == yesterday) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        for (date in uniqueDates) {
            val expected = dateFormat.format(calendar.time)
            if (date == expected) {
                streak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else if (date < expected) {
                break
            }
        }

        return streak
    }

    fun activeDaysForMonth(year: Int, month: Int): Flow<List<Int>> {
        val monthPrefix = String.format(Locale.US, "%04d-%02d", year, month)
        return statsDao.getActiveDaysForMonth("$monthPrefix%").map { dates ->
            dates.mapNotNull { date ->
                date.split("-").getOrNull(2)?.toIntOrNull()
            }
        }
    }
}
