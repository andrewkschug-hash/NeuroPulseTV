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
        val lowEnd = LowEndDeviceMode.profile(context)
        if (lowEnd.decodeOnlyMultiPaneAudio) return true
        // Chromecast GTV hardware cannot reliably decode multiple HD live streams.
        return DeviceDecoderLimits.profile().isChromecastGoogleTv
    }
}
