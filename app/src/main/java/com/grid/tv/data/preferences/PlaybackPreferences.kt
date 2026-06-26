package com.grid.tv.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.grid.tv.player.ExternalPlayerId
import javax.inject.Inject
import javax.inject.Singleton

/** Playback + sync preferences stored outside Room to avoid schema churn. */
@Singleton
class PlaybackPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var externalPlayer: ExternalPlayerId
        get() = runCatching {
            ExternalPlayerId.valueOf(prefs.getString(KEY_EXTERNAL_PLAYER, ExternalPlayerId.NONE.name)!!)
        }.getOrDefault(ExternalPlayerId.NONE)
        set(value) = prefs.edit().putString(KEY_EXTERNAL_PLAYER, value.name).apply()

    var nextUpAutoPlay: Boolean
        get() = prefs.getBoolean(KEY_NEXT_UP_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_NEXT_UP_AUTO, value).apply()

    var vodSyncIntervalHours: Int
        get() = prefs.getInt(KEY_VOD_SYNC_HOURS, 6).coerceIn(6, 168)
        set(value) = prefs.edit().putInt(KEY_VOD_SYNC_HOURS, value.coerceIn(6, 168)).apply()

    companion object {
        private const val PREFS = "playback_preferences"
        private const val KEY_EXTERNAL_PLAYER = "external_player"
        private const val KEY_NEXT_UP_AUTO = "next_up_auto"
        private const val KEY_VOD_SYNC_HOURS = "vod_sync_hours"
    }
}
