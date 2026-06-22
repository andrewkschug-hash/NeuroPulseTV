package com.grid.tv.player

import android.content.Context

/** Device-tier limits for Split View and MultiView decoder usage. */
object MultiPanePlaybackPolicy {

    fun maxPaneCount(context: Context): Int {
        val profile = DeviceDecoderLimits.profile()
        val caps = context.devicePlaybackCapabilities()
        return when {
            profile.isChromecastGoogleTv -> 2
            caps.isLowEndDevice -> 2
            else -> 4
        }
    }

    /** When true, only the audio-focused pane keeps an active decode pipeline. */
    fun decodeOnlyActiveAudioPane(context: Context): Boolean {
        val profile = DeviceDecoderLimits.profile()
        val caps = context.devicePlaybackCapabilities()
        return profile.isChromecastGoogleTv || caps.isLowEndDevice
    }
}
