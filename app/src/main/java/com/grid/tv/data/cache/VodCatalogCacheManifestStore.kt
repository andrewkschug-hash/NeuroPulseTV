package com.grid.tv.data.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists [VodPlaylistCacheManifest] for instant VOD startup and background diff-sync. */
@Singleton
class VodCatalogCacheManifestStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(playlistId: Long): VodPlaylistCacheManifest? {
        if (playlistId <= 0L) return null
        val savedAtMs = prefs.getLong(savedAtKey(playlistId), 0L)
        if (savedAtMs <= 0L) return null
        return VodPlaylistCacheManifest(
            playlistId = playlistId,
            playlistVersionKey = prefs.getString(versionKey(playlistId), null).orEmpty(),
            savedAtMs = savedAtMs,
            moviesCount = prefs.getInt(moviesCountKey(playlistId), 0),
            seriesCount = prefs.getInt(seriesCountKey(playlistId), 0),
            moviesContentFingerprint = prefs.getString(moviesFingerprintKey(playlistId), null),
            seriesContentFingerprint = prefs.getString(seriesFingerprintKey(playlistId), null),
        )
    }

    fun write(manifest: VodPlaylistCacheManifest) {
        if (manifest.playlistId <= 0L) return
        prefs.edit()
            .putString(versionKey(manifest.playlistId), manifest.playlistVersionKey)
            .putLong(savedAtKey(manifest.playlistId), manifest.savedAtMs)
            .putInt(moviesCountKey(manifest.playlistId), manifest.moviesCount)
            .putInt(seriesCountKey(manifest.playlistId), manifest.seriesCount)
            .putString(moviesFingerprintKey(manifest.playlistId), manifest.moviesContentFingerprint)
            .putString(seriesFingerprintKey(manifest.playlistId), manifest.seriesContentFingerprint)
            .apply()
    }

    fun merge(
        playlistId: Long,
        playlistVersionKey: String,
        savedAtMs: Long = System.currentTimeMillis(),
        moviesCount: Int? = null,
        seriesCount: Int? = null,
        moviesContentFingerprint: String? = null,
        seriesContentFingerprint: String? = null,
    ) {
        val existing = read(playlistId)
        write(
            VodPlaylistCacheManifest(
                playlistId = playlistId,
                playlistVersionKey = playlistVersionKey,
                savedAtMs = savedAtMs,
                moviesCount = moviesCount ?: existing?.moviesCount ?: 0,
                seriesCount = seriesCount ?: existing?.seriesCount ?: 0,
                moviesContentFingerprint = moviesContentFingerprint ?: existing?.moviesContentFingerprint,
                seriesContentFingerprint = seriesContentFingerprint ?: existing?.seriesContentFingerprint,
            )
        )
    }

    fun latestSavedAtMs(): Long {
        var latest = 0L
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(PREFIX_SAVED_AT) && value is Long) {
                latest = maxOf(latest, value)
            }
        }
        return latest
    }

    fun clear(playlistId: Long) {
        if (playlistId <= 0L) return
        prefs.edit()
            .remove(versionKey(playlistId))
            .remove(savedAtKey(playlistId))
            .remove(moviesCountKey(playlistId))
            .remove(seriesCountKey(playlistId))
            .remove(moviesFingerprintKey(playlistId))
            .remove(seriesFingerprintKey(playlistId))
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun versionKey(playlistId: Long) = "${PREFIX_VERSION}$playlistId"
    private fun savedAtKey(playlistId: Long) = "$PREFIX_SAVED_AT$playlistId"
    private fun moviesCountKey(playlistId: Long) = "${PREFIX_MOVIES_COUNT}$playlistId"
    private fun seriesCountKey(playlistId: Long) = "${PREFIX_SERIES_COUNT}$playlistId"
    private fun moviesFingerprintKey(playlistId: Long) = "${PREFIX_MOVIES_FP}$playlistId"
    private fun seriesFingerprintKey(playlistId: Long) = "${PREFIX_SERIES_FP}$playlistId"

    companion object {
        private const val PREFS_NAME = "vod_catalog_cache_manifest"
        private const val PREFIX_VERSION = "version_"
        private const val PREFIX_SAVED_AT = "saved_at_"
        private const val PREFIX_MOVIES_COUNT = "movies_count_"
        private const val PREFIX_SERIES_COUNT = "series_count_"
        private const val PREFIX_MOVIES_FP = "movies_fp_"
        private const val PREFIX_SERIES_FP = "series_fp_"
    }
}
