package com.neuropulse.tv.feature.enrichment

import com.neuropulse.tv.BuildConfig
import com.neuropulse.tv.data.security.SecureCredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds third-party API keys from build-time defaults into encrypted storage on first launch.
 * Keys can later be rotated via settings / remote config without rebuilding the APK.
 */
@Singleton
class ApiKeyBootstrap @Inject constructor(
    private val secureCredentialStore: SecureCredentialStore
) {
    fun ensureKeysInstalled() {
        if (secureCredentialStore.getTmdbApiKey().isNullOrBlank()) {
            BuildConfig.TMDB_API_KEY.trim().takeIf { it.isNotBlank() }?.let {
                secureCredentialStore.saveTmdbApiKey(it)
            }
        }
        if (secureCredentialStore.getOpenSubtitlesApiKey().isNullOrBlank()) {
            BuildConfig.OPENSUBTITLES_API_KEY.trim().takeIf { it.isNotBlank() }?.let {
                secureCredentialStore.saveOpenSubtitlesApiKey(it)
            }
        }
    }
}
