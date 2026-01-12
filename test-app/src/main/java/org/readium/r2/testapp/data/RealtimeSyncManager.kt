/*
 * Realtime Sync Manager
 *
 * Listens to Supabase Realtime channels for:
 * - Reading progress updates from other devices
 * - User preferences updates from other devices
 * - Highlights updates from other devices
 *
 * Emits events that the app can observe to update UI.
 */

package org.readium.r2.testapp.data

import android.content.Context
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.readium.r2.testapp.data.model.ReadingProgressDto
import org.readium.r2.testapp.data.model.UserPreferencesDto
import timber.log.Timber

/**
 * Manages real-time synchronization via Supabase Realtime.
 * Listens for changes from other devices and emits events.
 */
class RealtimeSyncManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val supabase = SupabaseService.client
    
    // Channel references
    private var readingProgressChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var preferencesChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var highlightsChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    
    // Subscription jobs
    private var subscriptionJob: Job? = null
    
    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Events
    sealed class RealtimeEvent {
        data class ReadingProgressUpdated(
            val bookId: String,
            val cfi: String,
            val percentage: Float,
            val timestamp: Long
        ) : RealtimeEvent()
        
        data class PreferencesUpdated(
            val fontSize: Int?,
            val theme: String?,
            val fontFamily: String?
        ) : RealtimeEvent()
        
        data class HighlightUpdated(
            val bookId: String,
            val action: String // "INSERT", "UPDATE", "DELETE"
        ) : RealtimeEvent()
        
        data class Error(val message: String) : RealtimeEvent()
    }
    
    private val _events = MutableSharedFlow<RealtimeEvent>()
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    /**
     * Start listening to realtime channels.
     */
    fun startListening() {
        if (subscriptionJob?.isActive == true) {
            Timber.d("RealtimeSyncManager: Already listening")
            return
        }
        
        subscriptionJob = scope.launch {
            try {
                Timber.d("RealtimeSyncManager: Starting realtime subscriptions...")
                
                // Subscribe to reading_progress table
                subscribeToReadingProgress()
                
                // Subscribe to user_preferences table
                subscribeToPreferences()
                
                // Subscribe to highlights table (if exists)
                subscribeToHighlights()
                
                _isConnected.value = true
                Timber.d("RealtimeSyncManager: All subscriptions active")
                
            } catch (e: Exception) {
                Timber.e(e, "RealtimeSyncManager: Failed to start subscriptions")
                _events.emit(RealtimeEvent.Error(e.message ?: "Unknown error"))
                _isConnected.value = false
            }
        }
    }
    
    /**
     * Stop listening and disconnect.
     */
    fun stopListening() {
        scope.launch {
            try {
                readingProgressChannel?.unsubscribe()
                preferencesChannel?.unsubscribe()
                highlightsChannel?.unsubscribe()
                subscriptionJob?.cancel()
                _isConnected.value = false
                Timber.d("RealtimeSyncManager: Stopped listening")
            } catch (e: Exception) {
                Timber.e(e, "RealtimeSyncManager: Error stopping")
            }
        }
    }
    
    private suspend fun subscribeToReadingProgress() {
        try {
            readingProgressChannel = supabase.realtime.channel("reading_progress_channel")
            
            val changeFlow = readingProgressChannel!!.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "reading_progress"
            }
            
            changeFlow.onEach { action ->
                when (action) {
                    is PostgresAction.Insert -> handleReadingProgressChange(action.record)
                    is PostgresAction.Update -> handleReadingProgressChange(action.record)
                    else -> { /* Ignore deletes for now */ }
                }
            }.launchIn(scope)
            
            readingProgressChannel!!.subscribe()
            Timber.d("Subscribed to reading_progress")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to subscribe to reading_progress")
        }
    }
    
    private suspend fun subscribeToPreferences() {
        try {
            preferencesChannel = supabase.realtime.channel("preferences_channel")
            
            val changeFlow = preferencesChannel!!.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "user_preferences"
            }
            
            changeFlow.onEach { action ->
                when (action) {
                    is PostgresAction.Insert -> handlePreferencesChange(action.record)
                    is PostgresAction.Update -> handlePreferencesChange(action.record)
                    else -> { /* Ignore */ }
                }
            }.launchIn(scope)
            
            preferencesChannel!!.subscribe()
            Timber.d("Subscribed to user_preferences")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to subscribe to user_preferences")
        }
    }
    
    private suspend fun subscribeToHighlights() {
        try {
            highlightsChannel = supabase.realtime.channel("highlights_channel")
            
            val changeFlow = highlightsChannel!!.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "highlights"
            }
            
            changeFlow.onEach { action ->
                val bookId = when (action) {
                    is PostgresAction.Insert -> action.record["book_id"]?.jsonPrimitive?.content
                    is PostgresAction.Update -> action.record["book_id"]?.jsonPrimitive?.content
                    is PostgresAction.Delete -> action.oldRecord["book_id"]?.jsonPrimitive?.content
                    else -> null
                }
                
                val actionType = when (action) {
                    is PostgresAction.Insert -> "INSERT"
                    is PostgresAction.Update -> "UPDATE"
                    is PostgresAction.Delete -> "DELETE"
                    else -> "UNKNOWN"
                }
                
                if (bookId != null) {
                    _events.emit(RealtimeEvent.HighlightUpdated(bookId, actionType))
                }
            }.launchIn(scope)
            
            highlightsChannel!!.subscribe()
            Timber.d("Subscribed to highlights")
            
        } catch (e: Exception) {
            // Highlights table might not exist yet, that's ok
            Timber.w(e, "Failed to subscribe to highlights (table may not exist)")
        }
    }
    
    private suspend fun handleReadingProgressChange(record: kotlinx.serialization.json.JsonObject) {
        try {
            val bookId = record["book_id"]?.jsonPrimitive?.content ?: return
            val cfi = record["cfi"]?.jsonPrimitive?.content ?: return
            val percentage = record["percentage"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
            val timestamp = record["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            
            Timber.d("Realtime: Reading progress updated for book $bookId")
            
            _events.emit(
                RealtimeEvent.ReadingProgressUpdated(
                    bookId = bookId,
                    cfi = cfi,
                    percentage = percentage,
                    timestamp = timestamp
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing reading progress change")
        }
    }
    
    private suspend fun handlePreferencesChange(record: kotlinx.serialization.json.JsonObject) {
        try {
            val fontSize = record["font_size"]?.jsonPrimitive?.content?.toIntOrNull()
            val theme = record["theme"]?.jsonPrimitive?.content
            val fontFamily = record["font_family"]?.jsonPrimitive?.content
            
            Timber.d("Realtime: Preferences updated")
            
            _events.emit(
                RealtimeEvent.PreferencesUpdated(
                    fontSize = fontSize,
                    theme = theme,
                    fontFamily = fontFamily
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing preferences change")
        }
    }
}
