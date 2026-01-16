/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eqraa.reader.data.model.*
import com.eqraa.reader.data.model.Book
import com.eqraa.reader.data.model.Bookmark
import com.eqraa.reader.data.model.Catalog
import com.eqraa.reader.data.model.Highlight
import com.eqraa.reader.data.model.ReadingSession

@Database(
    entities = [Book::class, Bookmark::class, Highlight::class, Catalog::class, ReadingSession::class, SyncAction::class, SyncLogEntry::class, WordCard::class, Badge::class],
    version = 11,
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
    
    abstract fun wordCardDao(): WordCardDao

    abstract fun badgeDao(): BadgeDao

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
        // ... (Previous migrations preserved)
        
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
         
         private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS word_cards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL,
                        definition TEXT,
                        contextSentence TEXT,
                        translation TEXT,
                        sourceBookId INTEGER,
                        createdAt INTEGER NOT NULL,
                        repetitionLevel INTEGER NOT NULL,
                        nextReviewAt INTEGER NOT NULL,
                        easeFactor REAL NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS badges (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        icon_name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        earned_at INTEGER,
                        condition_type TEXT NOT NULL,
                        condition_value INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE highlights ADD COLUMN CLOUD_ID TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE bookmarks ADD COLUMN CLOUD_ID TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Keep for history
                try {
                    database.execSQL("ALTER TABLE books ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Might already exist
                }
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE books ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Ignore if already added in a previous dev run
                }
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}

