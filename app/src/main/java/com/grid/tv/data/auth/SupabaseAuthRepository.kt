package com.grid.tv.data.auth

import com.grid.tv.domain.model.AuthAccount
import com.grid.tv.domain.model.OAuthProviderId
import com.grid.tv.domain.repository.AuthRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
    private val authPreferences: AuthPreferences
) : AuthRepository {

    override fun hasSkippedSignIn(): Boolean = authPreferences.hasSkippedSignIn()

    override fun setSkippedSignIn(skipped: Boolean) {
        authPreferences.setSkippedSignIn(skipped)
    }

    override val authState: Flow<Boolean>
        get() {
            val client = supabaseClientProvider.clientOrNull() ?: return flowOf(false)
            return client.auth.sessionStatus.map { status ->
                status is SessionStatus.Authenticated
            }
        }

    override suspend fun getCurrentAccount(): AuthAccount? {
        val auth = supabaseClientProvider.clientOrNull()?.auth ?: return null
        val user = auth.currentUserOrNull() ?: return null
        return user.toAuthAccount()
    }

    override suspend fun ensureCloudProfile() {
        val client = supabaseClientProvider.clientOrNull() ?: return
        val user = client.auth.currentUserOrNull() ?: return
        val account = user.toAuthAccount()
        client.postgrest["profiles"].upsert(
            mapOf(
                "id" to account.userId,
                "email" to account.email,
                "display_name" to account.displayName,
                "avatar_url" to account.avatarUrl
            )
        )
    }

    override suspend fun signInWithOAuth(provider: OAuthProviderId) {
        val auth = supabaseClientProvider.clientOrNull()?.auth
            ?: error("Supabase is not configured")
        when (provider) {
            OAuthProviderId.Google -> auth.signInWith(
                Google,
                redirectUrl = SupabaseClientProvider.REDIRECT_URL
            )
        }
    }

    override suspend fun handleOAuthCallback(url: String): Boolean {
        return supabaseClientProvider.clientOrNull()?.auth?.currentUserOrNull() != null
    }

    override suspend fun signOut() {
        supabaseClientProvider.clientOrNull()?.auth?.signOut()
        authPreferences.setSkippedSignIn(false)
    }

    private fun io.github.jan.supabase.auth.user.UserInfo.toAuthAccount(): AuthAccount {
        val metadata = userMetadata
        return AuthAccount(
            userId = id,
            email = email,
            displayName = metadata.readString("full_name")
                ?: metadata.readString("name")
                ?: email?.substringBefore("@"),
            avatarUrl = metadata.readString("avatar_url")
                ?: metadata.readString("picture")
        )
    }

    private fun JsonObject?.readString(key: String): String? =
        this?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
