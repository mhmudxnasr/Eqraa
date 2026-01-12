package org.readium.r2.testapp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.r2.testapp.data.db.AppDatabase
import org.readium.r2.testapp.data.model.SyncAction
import org.readium.r2.testapp.data.SyncWorker
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.auth.auth
import org.readium.r2.testapp.data.model.UserPreferencesDto
import org.readium.r2.testapp.settings.ReadingPreferences
import timber.log.Timber

/**
 * Manages syncing of user preferences (theme, font size, etc.)
 */
class UserPreferencesSyncManager(
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    private var syncJob: Job? = null
    private val AUTH_TOKEN = SyncConfig.AUTH_TOKEN

    companion object {
        private const val KEY_LAST_SYNC = "last_preferences_sync"
        private const val DEBOUNCE_DELAY = 5000L // 5 seconds
    }

    private val supabase = SupabaseService.client

    private val syncDao = AppDatabase.getDatabase(context).syncDao()

    fun startSync(scope: CoroutineScope) {
        // Initial fetch from server
        scope.launch {
            fetchFromServer()
        }
    }



    suspend fun syncNow() {
        fetchFromServer()
    }

    private suspend fun fetchFromServer() {
        try {
            val serverPrefs = supabase.from("user_preferences").select().decodeSingleOrNull<UserPreferencesDto>()
            if (serverPrefs != null) {
                applyServerPreferences(serverPrefs)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch preferences from server")
        }
    }

    private fun applyServerPreferences(dto: UserPreferencesDto) {
        val localPrefs = ReadingPreferences(context)
        
        // Only apply if server is newer (simple comparison)
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        if (dto.lastUpdated != null && dto.lastUpdated > lastSync) {
            Timber.d("Applying server preferences: $dto")
            
            if (dto.fontSize != null) localPrefs.fontSize = dto.fontSize
            
            if (dto.theme != null) {
                localPrefs.theme = when (dto.theme) {
                    "paper" -> 0
                    "sepia" -> 1
                    "dark" -> 2
                    else -> 0
                }
            }
            
            if (dto.fontFamily != null) localPrefs.fontFamily = dto.fontFamily
            
            prefs.edit().putLong(KEY_LAST_SYNC, dto.lastUpdated).apply()
        }
    }

    fun scheduleSync(scope: CoroutineScope) {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            delay(DEBOUNCE_DELAY)
            performSync()
        }
    }

    private suspend fun performSync() {
        try {
            val localPrefs = ReadingPreferences(context)
            val userId = supabase.auth.currentSessionOrNull()?.user?.id 
                ?: throw IllegalStateException("User must be logged in to sync preferences")
                
            val dto = UserPreferencesDto(
                userId = userId,
                fontSize = localPrefs.fontSize,
                theme = when (localPrefs.theme) {
                    0 -> "paper"
                    1 -> "sepia"
                    2 -> "dark"
                    else -> "paper"
                },
                fontFamily = localPrefs.fontFamily,
                lastUpdated = System.currentTimeMillis()
            )

            // 1. Persist to SQLite Action Queue
            val payload = Json.encodeToString(dto)
            
            // Replace existing pending preferences action if exists
            val existing = syncDao.getActionByTarget(SyncAction.TYPE_PREFERENCE, "global_settings")
            if (existing != null) {
                syncDao.updateAction(existing.copy(payload = payload, timestamp = dto.lastUpdated!!))
            } else {
                syncDao.insertAction(
                    SyncAction(
                        type = SyncAction.TYPE_PREFERENCE,
                        key = "global_settings",
                        payload = payload,
                        timestamp = dto.lastUpdated!!
                    )
                )
            }

            // 2. Trigger Worker
            SyncWorker.enqueue(context)
            
            Timber.d("Preferences sync queued and persisted")

        } catch (e: Exception) {
            Timber.e(e, "Failed to queue preferences sync")
        }
    }
}
