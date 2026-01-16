/*
 * Copyright 2024 Eqraa. All rights reserved.
 */

package com.eqraa.reader.gamification

import com.eqraa.reader.data.db.BadgeDao
import com.eqraa.reader.data.model.Badge
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Manages badge unlocking logic based on user achievements.
 */
class BadgeManager(
    private val badgeDao: BadgeDao
) {
    
    val allBadges: Flow<List<Badge>> = badgeDao.getAllBadges()
    val earnedBadges: Flow<List<Badge>> = badgeDao.getEarnedBadges()

    /**
     * Seeds the initial badge definitions if not already present.
     */
    suspend fun seedDefaultBadges() {
        val defaults = listOf(
            Badge(
                name = "First Flame",
                iconName = "ic_badge_first_flame",
                description = "Start your first reading streak",
                conditionType = "streak",
                conditionValue = 1
            ),
            Badge(
                name = "Week Warrior",
                iconName = "ic_badge_week_warrior",
                description = "Maintain a 7-day reading streak",
                conditionType = "streak",
                conditionValue = 7
            ),
            Badge(
                name = "Scholar",
                iconName = "ic_badge_scholar",
                description = "Maintain a 30-day reading streak",
                conditionType = "streak",
                conditionValue = 30
            ),
            Badge(
                name = "Bookworm",
                iconName = "ic_badge_bookworm",
                description = "Finish 5 books",
                conditionType = "books_finished",
                conditionValue = 5
            ),
            Badge(
                name = "Night Owl",
                iconName = "ic_badge_night_owl",
                description = "Read after 11 PM",
                conditionType = "night_reading",
                conditionValue = 1
            ),
            Badge(
                name = "Marathon Reader",
                iconName = "ic_badge_marathon",
                description = "Read for 2 hours in a single day",
                conditionType = "daily_reading_minutes",
                conditionValue = 120
            )
        )
        badgeDao.insertAll(defaults)
        Timber.d("BadgeManager: Seeded ${defaults.size} default badges")
    }

    /**
     * Checks and unlocks badges based on streak count.
     */
    suspend fun checkStreakBadges(currentStreak: Int): List<Badge> {
        val unlocked = badgeDao.getUnlockedBadgesForCondition("streak", currentStreak)
        val now = System.currentTimeMillis()
        unlocked.forEach { badge ->
            badge.id?.let { badgeDao.markBadgeEarned(it, now) }
            Timber.d("BadgeManager: Unlocked badge '${badge.name}'!")
        }
        return unlocked
    }

    /**
     * Checks and unlocks badges based on books finished.
     */
    suspend fun checkBooksFinishedBadges(booksFinished: Int): List<Badge> {
        val unlocked = badgeDao.getUnlockedBadgesForCondition("books_finished", booksFinished)
        val now = System.currentTimeMillis()
        unlocked.forEach { badge ->
            badge.id?.let { badgeDao.markBadgeEarned(it, now) }
        }
        return unlocked
    }

    /**
     * Checks and unlocks night owl badge.
     */
    suspend fun checkNightReadingBadge(): List<Badge> {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour >= 23 || hour < 5) {
            val unlocked = badgeDao.getUnlockedBadgesForCondition("night_reading", 1)
            val now = System.currentTimeMillis()
            unlocked.forEach { badge ->
                badge.id?.let { badgeDao.markBadgeEarned(it, now) }
            }
            return unlocked
        }
        return emptyList()
    }

    /**
     * Checks and unlocks badges based on daily reading time.
     */
    suspend fun checkDailyReadingBadges(todayMinutes: Int): List<Badge> {
        val unlocked = badgeDao.getUnlockedBadgesForCondition("daily_reading_minutes", todayMinutes)
        val now = System.currentTimeMillis()
        unlocked.forEach { badge ->
            badge.id?.let { badgeDao.markBadgeEarned(it, now) }
        }
        return unlocked
    }
}
