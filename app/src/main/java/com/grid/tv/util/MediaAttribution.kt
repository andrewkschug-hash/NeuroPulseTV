package com.grid.tv.util

import android.content.Context
import android.os.Build

object MediaAttribution {
    const val MEDIA_PLAYBACK = "mediaPlayback"
    const val RECORDING = "recording"
    const val VOICE_SEARCH = "voiceSearch"

    fun appContext(context: Context, tag: String): Context {
        val app = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.createAttributionContext(tag)
        } else {
            app
        }
    }
}
