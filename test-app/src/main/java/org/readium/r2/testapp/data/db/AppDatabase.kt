/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.readium.r2.testapp.data.model.*
import org.readium.r2.testapp.data.model.Book
import org.readium.r2.testapp.data.model.Bookmark
import org.readium.r2.testapp.data.model.Catalog
import org.readium.r2.testapp.data.model.Highlight
import org.readium.r2.testapp.data.model.ReadingSession

@Database(
    entities = [Book::class, Bookmark::class, Highlight::class, Catalog::class, ReadingSession::class, SyncAction::class, SyncLogEntry::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(
    HighlightConverters::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun booksDao(): BooksDao

    abstract fun catalogDao(): CatalogDao

    abstract fun statsDao(): StatsDao

    abstract fun syncDao(): SyncDao

    abstract fun syncLogDao(): SyncLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ${ReadingSession.TABLE_NAME} (
                        ${ReadingSession.ID} INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ${ReadingSession.BOOK_ID} INTEGER NOT NULL,
                        ${ReadingSession.START_TIME} INTEGER NOT NULL,
                        ${ReadingSession.END_TIME} INTEGER NOT NULL,
                        ${ReadingSession.DATE} TEXT NOT NULL,
                        FOREIGN KEY(${ReadingSession.BOOK_ID}) REFERENCES ${Book.TABLE_NAME}(${Book.ID}) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_reading_sessions_book_id ON ${ReadingSession.TABLE_NAME}(${ReadingSession.BOOK_ID})"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ${SyncAction.TABLE_NAME} (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        `key` TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        source TEXT NOT NULL,
                        message TEXT NOT NULL,
                        details TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE books ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}

