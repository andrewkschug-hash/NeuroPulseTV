package com.neuropulse.tv.player

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerFactory @Inject constructor() {
    fun create(context: Context): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        return ExoPlayer.Builder(context, renderersFactory).build()
    }
}
