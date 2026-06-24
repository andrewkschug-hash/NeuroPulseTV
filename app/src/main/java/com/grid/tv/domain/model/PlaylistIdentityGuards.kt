package com.grid.tv.domain.model

import android.util.Log

/**
 * Runtime guardrails for playlist-scoped content identity.
 * Logs warnings when legacy global lookups are used so regressions are visible in logcat.
 */
object PlaylistIdentityGuards {
    private const val LOG_TAG = "PlaylistIdentity"

    fun requirePlaylistId(playlistId: Long, operation: String): Long {
        if (playlistId <= 0L) {
            Log.w(LOG_TAG, "legacy global identity: $operation (playlistId=0)")
        }
        return playlistId
    }

    fun warnGlobalStreamLookup(source: String, streamId: Long) {
        Log.w(LOG_TAG, "global streamId lookup without playlist scope source=$source streamId=$streamId")
    }

    fun warnGlobalSeriesLookup(source: String, seriesId: Long) {
        Log.w(LOG_TAG, "global seriesId lookup without playlist scope source=$source seriesId=$seriesId")
    }

    fun warnGlobalChannelNumberLookup(source: String, number: Int) {
        Log.w(LOG_TAG, "global channel number lookup without playlist scope source=$source number=$number")
    }

    fun warnBareStreamIdMap(source: String) {
        Log.w(LOG_TAG, "Map keyed by bare streamId without playlist scope source=$source")
    }
}
