package com.grid.tv.data.auth

import com.grid.tv.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClientProvider @Inject constructor() {
    val client: SupabaseClient by lazy { buildClient() }

    private fun buildClient(): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        require(url.isNotBlank() && key.isNotBlank()) {
            "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY to .env"
        }

        return createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Auth) {
                scheme = AUTH_SCHEME
                host = AUTH_HOST
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(ComposeAuth) {
                // Web OAuth client ID from Google Cloud Console — not the Android client ID.
                val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
                require(webClientId.isNotBlank()) {
                    "Add GOOGLE_WEB_CLIENT_ID (Web client ID) to .env and rebuild."
                }
                googleNativeLogin(serverClientId = webClientId)
            }
        }
    }

    companion object {
        const val AUTH_SCHEME = "com.grid.tv"
        const val AUTH_HOST = "auth"
        const val REDIRECT_URL = "$AUTH_SCHEME://$AUTH_HOST"
    }
}
