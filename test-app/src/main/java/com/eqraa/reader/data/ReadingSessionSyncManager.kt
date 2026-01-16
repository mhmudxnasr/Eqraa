/*
 * Reading Session Sync Manager
 *
 * Synchronizes reading sessions (analytics) to Supabase.
 * Uses the sync_reading_session RPC for safe insertion.
 */

package com.eqraa.reader.data

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.eqraa.reader.data.db.AppDatabase
import com.eqraa.reader.data.model.ReadingSession
import timber.log.Timber

@Serializable
data class ReadingSessionSyncDto(
    @SerialName("p_book_id") val bookId: String,
    @SerialName("p_start_time") val startTime: Long,
    @SerialName("p_end_time") val endTime: Long,
    @SerialName("p_date") val date: String,
    @SerialName("p_duration_seconds") val durationSeconds: Int,
    @SerialName("p_device_id") val deviceId: String
)

class ReadingSessionSyncManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    
    private val supabase by lazy { SupabaseService.client }
    private val booksDao = AppDatabase.getDatabase(context).booksDao()
    private val statsDao = AppDatabase.getDatabase(context).statsDao()
    
    // We reuse the same device ID logic as ReadingSyncManager
    val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        id!!
    }

    suspend fun syncSession(session: ReadingSession) = withContext(Dispatchers.IO) {
        try {
            // map local ID to Global ID
            val globalBookId = booksDao.getIdentifierByBookId(session.bookId) 
                ?: session.bookId.toString()

            val durationSeconds = (session.durationMs / 1000).toInt()
            
            val params = ReadingSessionSyncDto(
                bookId = globalBookId,
                startTime = session.startTime,
                endTime = session.endTime,
                date = session.date,
                durationSeconds = durationSeconds,
                deviceId = deviceId
            )
            
            // Call RPC
            // Note: rpc call is "fire and forget" mostly for analytics, but we could check result
            supabase.postgrest.rpc("sync_reading_session", params)
            
            Timber.d("ReadingSessionSyncManager: Synced session for book $globalBookId")
            
        } catch (e: Exception) {
            Timber.e(e, "ReadingSessionSyncManager: Failed to sync session")
        }
    }
}
