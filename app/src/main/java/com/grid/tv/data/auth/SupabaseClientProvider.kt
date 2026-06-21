package com.grid.tv.data.auth

import com.grid.tv.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.functions.Functions
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClientProvider @Inject constructor() {

    private val configuredClient: SupabaseClient? by lazy {
        if (!isConfigured) null else buildClient()
    }

    /** True when SUPABASE_URL and SUPABASE_ANON_KEY are present in .env at build time. */
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.trim().isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.trim().isNotBlank()

    val client: SupabaseClient
        get() = configuredClient
            ?: error("Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY to .env")

    fun clientOrNull(): SupabaseClient? = if (isConfigured) {
        runCatching { configuredClient }.getOrNull()
    } else {
        null
    }

    private fun buildClient(): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()

        return createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Auth) {
                scheme = AUTH_SCHEME
                host = AUTH_HOST
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Functions)
            install(ComposeAuth) {
                val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
                if (webClientId.isNotBlank()) {
                    googleNativeLogin(serverClientId = webClientId)
                }
            }
        }
    }

    companion object {
        const val AUTH_SCHEME = "com.grid.tv"
        const val AUTH_HOST = "auth"
        const val REDIRECT_URL = "$AUTH_SCHEME://$AUTH_HOST"
    }
}
