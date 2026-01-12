package com.eqraa.reader.data.auth

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.eqraa.reader.data.SupabaseService

class AuthRepository {

    private val auth: Auth
        get() = SupabaseService.client.auth

    val sessionStatus: Flow<SessionStatus>
        get() = auth.sessionStatus

    val currentUserId: String?
        get() = auth.currentUserOrNull()?.id

    val isUserLoggedIn: Boolean
        get() = auth.currentUserOrNull() != null

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUpWithEmail(email: String, password: String) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }
}
