/*
 * Copyright 2024 Eqraa. All rights reserved.
 */

package com.eqraa.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.eqraa.reader.data.model.Badge

@Dao
interface BadgeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBadge(badge: Badge): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(badges: List<Badge>)

    @Update
    suspend fun updateBadge(badge: Badge)

    @Query("SELECT * FROM ${Badge.TABLE_NAME} ORDER BY ${Badge.EARNED_AT} DESC")
    fun getAllBadges(): Flow<List<Badge>>

    @Query("SELECT * FROM ${Badge.TABLE_NAME} WHERE ${Badge.EARNED_AT} IS NOT NULL ORDER BY ${Badge.EARNED_AT} DESC")
    fun getEarnedBadges(): Flow<List<Badge>>

    @Query("SELECT * FROM ${Badge.TABLE_NAME} WHERE ${Badge.CONDITION_TYPE} = :type AND ${Badge.CONDITION_VALUE} <= :value AND ${Badge.EARNED_AT} IS NULL")
    suspend fun getUnlockedBadgesForCondition(type: String, value: Int): List<Badge>

    @Query("UPDATE ${Badge.TABLE_NAME} SET ${Badge.EARNED_AT} = :timestamp WHERE ${Badge.ID} = :badgeId")
    suspend fun markBadgeEarned(badgeId: Long, timestamp: Long)
}
