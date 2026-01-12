/*
 * Copyright 2022 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp

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
import org.readium.r2.testapp.BuildConfig.DEBUG
import org.readium.r2.testapp.data.BookRepository
import org.readium.r2.testapp.data.StatsRepository
import org.readium.r2.testapp.data.db.AppDatabase
import org.readium.r2.testapp.domain.Bookshelf
import org.readium.r2.testapp.domain.CoverStorage
import io.github.jan.supabase.auth.auth
// import org.readium.r2.testapp.data.AuthManager (Removed)
// import org.readium.r2.testapp.data.FirestoreSyncManager (Removed)
import org.readium.r2.testapp.data.ReadingProgressSyncManager
import org.readium.r2.testapp.data.BackupManager
import org.readium.r2.testapp.data.CloudLibraryManager
import org.readium.r2.testapp.data.UserPreferencesSyncManager
import org.readium.r2.testapp.data.RealtimeSyncManager
import org.readium.r2.testapp.data.HighlightSyncManager
// import org.readium.r2.testapp.data.EqraaLibrarySyncManager (Removed)
// import org.readium.r2.testapp.data.StorageManager (Removed)
// import org.readium.r2.testapp.data.api.ApiClient (Removed)
import org.readium.r2.testapp.data.SupabaseService
import org.readium.r2.testapp.settings.ReadingPreferences
import org.readium.r2.testapp.domain.PublicationRetriever
import org.readium.r2.testapp.reader.ReaderRepository
import org.readium.r2.testapp.utils.tryOrLog
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.readium.r2.testapp.data.SyncWorker
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

    private val coroutineScope: CoroutineScope =
        MainScope()

    private val Context.navigatorPreferences: DataStore<Preferences>
        by preferencesDataStore(name = "navigator-preferences")

    override fun onCreate() {
        if (DEBUG) {
            enableStrictMode()
            Timber.plant(Timber.DebugTree())
        }

        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)

        readium = Readium(this)

        storageDir = computeStorageDir()

        val database = AppDatabase.getDatabase(this)

        bookRepository = BookRepository(database.booksDao())

        statsRepository = StatsRepository(database.statsDao())

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
        coroutineScope.launch {
            try {
                // Hardcoded user for "Single User: Mahmud" scenario (Legacy/Local usage)
                val userId = "mahmud"
                
                readingProgressSyncManager = ReadingProgressSyncManager(userId)
                readingProgressSyncManager?.initialize(this@Application, coroutineScope, database.booksDao())
                
                statsRepository.initialize(userId, coroutineScope)
                
                // Initialize Backup Manager
                backupManager = BackupManager(this@Application, database.booksDao(), database.statsDao(), coroutineScope)
                bookRepository.backupManager = backupManager
                
                // Initialize Cloud Library Manager
                cloudLibraryManager = CloudLibraryManager(this@Application, coroutineScope, storageDir)
                
                // Initialize User Preferences Sync Manager
                userPreferencesSyncManager = UserPreferencesSyncManager(this@Application)

                // Initialize Realtime Sync Manager
                realtimeSyncManager = RealtimeSyncManager(this@Application, coroutineScope)
                
                // Initialize Highlight Sync Manager
                highlightSyncManager = HighlightSyncManager(this@Application, coroutineScope)
                bookRepository.highlightSyncManager = highlightSyncManager

                // Check for valid session before syncing to avoid 401 errors
                val session = SupabaseService.client.auth.currentSessionOrNull()
                if (session != null) {
                    Timber.d("User authenticated. Starting sync.")
                    cloudLibraryManager?.fetchCloudLibrary()
                    userPreferencesSyncManager?.startSync(coroutineScope)
                    realtimeSyncManager?.startListening() // Start listening to realtime changes
                    scheduleBackgroundSync()
                } else {
                     Timber.w("Sync skipped: User not logged in. Please sign in via Settings.")
                }

                Timber.d("Sync managers initialized for user: $userId")
                
            } catch (e: Exception) {
                Timber.e(e, "Sync init failed")
            }
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
        val properties = Properties()
        val inputStream = assets.open("configs/config.properties")
        properties.load(inputStream)
        val useExternalFileDir =
            properties.getProperty("useExternalFileDir", "false")!!.toBoolean()

        return File(
            if (useExternalFileDir) {
                getExternalFilesDir(null)?.path + "/"
            } else {
                filesDir?.path + "/"
            }
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
