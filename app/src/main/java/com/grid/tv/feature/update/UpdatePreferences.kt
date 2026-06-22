package com.grid.tv.feature.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun dismissedVersion(): String? = prefs.getString(KEY_DISMISSED_VERSION, null)

    fun dismissVersion(version: String) {
        prefs.edit().putString(KEY_DISMISSED_VERSION, version).apply()
    }

    fun cachedReleaseTag(): CachedReleaseTag? {
        val tag = prefs.getString(KEY_CACHED_RELEASE_TAG, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val fetchedAtMs = prefs.getLong(KEY_CACHED_RELEASE_AT_MS, 0L)
        if (fetchedAtMs <= 0L) return null
        return CachedReleaseTag(tag = tag, fetchedAtMs = fetchedAtMs)
    }

    fun saveCachedReleaseTag(tag: String, fetchedAtMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_CACHED_RELEASE_TAG, tag.trim())
            .putLong(KEY_CACHED_RELEASE_AT_MS, fetchedAtMs)
            .apply()
    }

    fun isReleaseCacheFresh(fetchedAtMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - fetchedAtMs < RELEASE_CACHE_TTL_MS

    data class CachedReleaseTag(val tag: String, val fetchedAtMs: Long)

    companion object {
        internal const val RELEASE_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        private const val PREFS_NAME = "app_update"
        private const val KEY_DISMISSED_VERSION = "dismissed_version"
        private const val KEY_CACHED_RELEASE_TAG = "cached_release_tag"
        private const val KEY_CACHED_RELEASE_AT_MS = "cached_release_at_ms"
    }
}
