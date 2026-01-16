/*
 * Copyright 2024 Eqraa. All rights reserved.
 */

package com.eqraa.reader.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an achievement badge that users can earn.
 */
@Entity(tableName = Badge.TABLE_NAME)
data class Badge(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    var id: Long? = null,

    @ColumnInfo(name = NAME)
    val name: String,

    @ColumnInfo(name = ICON_NAME)
    val iconName: String,

    @ColumnInfo(name = DESCRIPTION)
    val description: String,

    @ColumnInfo(name = EARNED_AT)
    val earnedAt: Long? = null,

    @ColumnInfo(name = CONDITION_TYPE)
    val conditionType: String, // "streak", "books_finished", "reading_time", "night_reading"

    @ColumnInfo(name = CONDITION_VALUE)
    val conditionValue: Int // e.g., 7 for 7-day streak, 5 for 5 books
) {
    companion object {
        const val TABLE_NAME = "badges"
        const val ID = "id"
        const val NAME = "name"
        const val ICON_NAME = "icon_name"
        const val DESCRIPTION = "description"
        const val EARNED_AT = "earned_at"
        const val CONDITION_TYPE = "condition_type"
        const val CONDITION_VALUE = "condition_value"
    }
}
