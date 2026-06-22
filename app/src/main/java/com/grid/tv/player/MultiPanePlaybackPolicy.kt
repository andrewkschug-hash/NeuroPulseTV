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
        val profile = DeviceDecoderLimits.profile()
        val lowEnd = LowEndDeviceMode.profile(context)
        return profile.isChromecastGoogleTv || lowEnd.decodeOnlyMultiPaneAudio || lowEnd.active
    }
}
