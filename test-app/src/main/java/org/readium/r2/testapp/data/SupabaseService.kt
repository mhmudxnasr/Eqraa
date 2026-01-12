package org.readium.r2.testapp.data

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.testapp.BuildConfig

object SupabaseService {
    
    private var _client: SupabaseClient? = null
    
    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException("SupabaseService not initialized. Call initialize(context) first.")

    fun initialize(context: Context) {
        if (_client != null) return

        _client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            httpEngine = io.ktor.client.engine.okhttp.OkHttp.create()

            install(Auth) {
                sessionManager = AndroidSessionManager(context)
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    private class AndroidSessionManager(context: Context) : SessionManager {
        private val prefs: SharedPreferences = context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE)

        override suspend fun saveSession(session: io.github.jan.supabase.auth.user.UserSession) = withContext(Dispatchers.IO) {
            val sessionStr = kotlinx.serialization.json.Json.encodeToString(io.github.jan.supabase.auth.user.UserSession.serializer(), session)
            prefs.edit().putString("session", sessionStr).apply()
        }

        override suspend fun loadSession(): io.github.jan.supabase.auth.user.UserSession? = withContext(Dispatchers.IO) {
            val sessionStr = prefs.getString("session", null) ?: return@withContext null
            try {
                kotlinx.serialization.json.Json.decodeFromString(io.github.jan.supabase.auth.user.UserSession.serializer(), sessionStr)
            } catch (e: Exception) {
                null
            }
        }

        override suspend fun deleteSession() = withContext(Dispatchers.IO) {
            prefs.edit().remove("session").apply()
        }
    }
}
