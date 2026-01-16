/*
 * Copyright 2022 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader

import android.content.Context
import android.os.Build
import android.os.StrictMode
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.material.color.DynamicColors
import java.io.File
import java.util.Properties
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.eqraa.reader.BuildConfig.DEBUG
import com.eqraa.reader.data.BookRepository
import com.eqraa.reader.data.StatsRepository
import com.eqraa.reader.data.db.AppDatabase
import com.eqraa.reader.domain.Bookshelf
import com.eqraa.reader.domain.CoverStorage
import io.github.jan.supabase.auth.auth
// import com.eqraa.reader.data.AuthManager (Removed)
// import com.eqraa.reader.data.FirestoreSyncManager (Removed)
import com.eqraa.reader.data.ReadingProgressSyncManager
import com.eqraa.reader.data.ReadingProgressRepository
import com.eqraa.reader.data.ReadingSyncManager
import com.eqraa.reader.data.BackupManager
import com.eqraa.reader.data.CloudLibraryManager
import com.eqraa.reader.data.UserPreferencesSyncManager
import com.eqraa.reader.data.RealtimeSyncManager
import com.eqraa.reader.data.HighlightSyncManager
import com.eqraa.reader.data.BookmarkSyncManager
// import com.eqraa.reader.data.EqraaLibrarySyncManager (Removed)
// import com.eqraa.reader.data.StorageManager (Removed)
// import com.eqraa.reader.data.api.ApiClient (Removed)
import com.eqraa.reader.data.SupabaseService
import com.eqraa.reader.data.ReadingSessionSyncManager
import com.eqraa.reader.settings.ReadingPreferences
import com.eqraa.reader.domain.PublicationRetriever
import com.eqraa.reader.reader.ReaderRepository
import com.eqraa.reader.utils.tryOrLog
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.eqraa.reader.data.SyncWorker
import timber.log.Timber

class Application : android.app.Application() {

    lateinit var readium: Readium
        private set

    lateinit var storageDir: File

    lateinit var bookRepository: BookRepository
        private set

    lateinit var statsRepository: StatsRepository
        private set

    lateinit var bookshelf: Bookshelf
        private set

    lateinit var readerRepository: ReaderRepository
        private set

    var readingProgressSyncManager: ReadingProgressSyncManager? = null
        private set

    lateinit var readingProgressRepository: ReadingProgressRepository
        private set

    lateinit var readingSyncManager: ReadingSyncManager
        private set

    var backupManager: BackupManager? = null
        private set

    var cloudLibraryManager: CloudLibraryManager? = null
        private set

    var userPreferencesSyncManager: UserPreferencesSyncManager? = null
        private set

    var realtimeSyncManager: RealtimeSyncManager? = null
        private set

    var highlightSyncManager: HighlightSyncManager? = null
        private set

    var bookmarkSyncManager: BookmarkSyncManager? = null
        private set

    var readingSessionSyncManager: ReadingSessionSyncManager? = null
        private set

    var badgeManager: com.eqraa.reader.gamification.BadgeManager? = null
        private set

    private val coroutineScope: CoroutineScope =
        MainScope()

    private val Context.navigatorPreferences: DataStore<Preferences>
        by preferencesDataStore(name = "navigator-preferences")

    override fun onCreate() {
        if (DEBUG) {
            // enableStrictMode()
            Timber.plant(Timber.DebugTree())
        }

        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)

        readium = Readium(this)

        storageDir = computeStorageDir()

        val database = AppDatabase.getDatabase(this)

        bookRepository = BookRepository(database.booksDao())

        statsRepository = StatsRepository(
            statsDao = database.statsDao(),
            sessionSyncManager = null // Will be set after sync init if needed or we init sync manager earlier
        )

        val downloadsDir = File(cacheDir, "downloads")

        // Cleans the download dir.
        tryOrLog { downloadsDir.delete() }

        val publicationRetriever =
            PublicationRetriever(
                context = applicationContext,
                assetRetriever = readium.assetRetriever,
                bookshelfDir = storageDir,
                tempDir = downloadsDir,
                httpClient = readium.httpClient,
                lcpService = readium.lcpService.getOrNull()
            )

        bookshelf =
            Bookshelf(
                bookRepository,
                CoverStorage(storageDir, httpClient = readium.httpClient),
                readium.publicationOpener,
                readium.assetRetriever,
                publicationRetriever
            )

        readerRepository = ReaderRepository(
            this@Application,
            readium,
            bookRepository,
            navigatorPreferences
        )

        // Supabase Initialization
        SupabaseService.initialize(this)

        // Sync Initialization (Custom Backend)
        try {
            // Hardcoded user for "Single User: Mahmud" scenario (Legacy/Local usage)
            val userId = "mahmud"
            
            // Initialize Managers Synchronously (Avoid race conditions with UI)
            cloudLibraryManager = CloudLibraryManager(this@Application, coroutineScope, storageDir)
            
            readingProgressRepository = ReadingProgressRepository(
                booksDao = database.booksDao(),
                context = this@Application
            )
            
            readingSyncManager = ReadingSyncManager(
                supabase = SupabaseService.client,
                context = this@Application,
                booksDao = database.booksDao(),
                scope = coroutineScope
            )
            
            userPreferencesSyncManager = UserPreferencesSyncManager(this@Application)

            realtimeSyncManager = RealtimeSyncManager(this@Application, coroutineScope)
            
            highlightSyncManager = HighlightSyncManager(this@Application, coroutineScope)
            bookRepository.highlightSyncManager = highlightSyncManager
            
            bookmarkSyncManager = BookmarkSyncManager(this@Application, coroutineScope)
            bookRepository.bookmarkSyncManager = bookmarkSyncManager
            
            readingSessionSyncManager = ReadingSessionSyncManager(this@Application, coroutineScope)
            
            // Re-init stats repository with sync manager
            statsRepository = StatsRepository(
                statsDao = database.statsDao(),
                sessionSyncManager = readingSessionSyncManager
            )

            // Gamification
            badgeManager = com.eqraa.reader.gamification.BadgeManager(database.badgeDao())
            coroutineScope.launch {
                badgeManager?.seedDefaultBadges()
            }

            Timber.d("Sync managers initialized for user: $userId")

            // Start Sync Logic Asynchronously
            coroutineScope.launch {
                // Check for valid session before syncing to avoid 401 errors
                val session = SupabaseService.client.auth.currentSessionOrNull()
                if (session != null) {
                    Timber.d("User authenticated. Starting sync.")
                    cloudLibraryManager?.fetchCloudLibrary()
                    userPreferencesSyncManager?.startSync(coroutineScope)
                    realtimeSyncManager?.startListening() // Start listening to realtime changes
                    readingSyncManager.startRealtimeSync() // Start reading progress realtime sync
                    scheduleBackgroundSync()
                } else {
                     Timber.w("Sync skipped: User not logged in. Please sign in via Settings.")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Sync init failed")
        }
    }

    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "EqraaBackgroundSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        Timber.d("Background sync scheduled (every 15m)")
    }

    private fun computeStorageDir(): File {
        // Force internal storage to avoid asset reading issues and simplify
        // val properties = Properties()
        // val inputStream = assets.open("configs/config.properties")
        // properties.load(inputStream)
        // val useExternalFileDir =
        //     properties.getProperty("useExternalFileDir", "false")!!.toBoolean()

        return File(
            // if (useExternalFileDir) {
            //     getExternalFilesDir(null)?.path + "/"
            // } else {
                filesDir?.path + "/"
            // }
        )
    }

    /**
     * Strict mode will log violation of VM and threading policy.
     * Use it to make sure the app doesn't do too much work on the main thread.
     */
    private fun enableStrictMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }

        val executor = Executors.newSingleThreadExecutor()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
            .detectAll()
            .penaltyListener(executor) { violation ->
                Timber.e(violation, "Thread policy violation")
            }
//                .penaltyDeath()
            .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
            .detectAll()
            .penaltyListener(executor) { violation ->
                Timber.e(violation, "VM policy violation")
            }
//                .penaltyDeath()
            .build()
        )
    }
}
