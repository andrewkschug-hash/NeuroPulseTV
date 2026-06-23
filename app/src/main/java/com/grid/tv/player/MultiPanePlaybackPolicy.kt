package com.grid.tv.player

import android.content.Context

/** Device-tier limits for Split View and MultiView decoder usage. */
object MultiPanePlaybackPolicy {

    fun maxPaneCount(context: Context): Int {
        val lowEnd = LowEndDeviceMode.profile(context)
        val profile = DeviceDecoderLimits.profile()
        return when {
            profile.isChromecastGoogleTv -> 2.coerceAtMost(lowEnd.maxPaneCount)
            else -> lowEnd.maxPaneCount
        }
    }

    fun decodeOnlyActiveAudioPane(context: Context): Boolean {
        // Chromecast GTV hardware cannot reliably decode multiple HD live streams.
        // Other devices decode every pane and mute non-audio panes instead of showing blanks.
        return DeviceDecoderLimits.profile().isChromecastGoogleTv
    }
}
