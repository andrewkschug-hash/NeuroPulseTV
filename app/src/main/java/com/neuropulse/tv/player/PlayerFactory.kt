package com.neuropulse.tv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.neuropulse.tv.domain.model.BufferSize
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerFactory @Inject constructor() {
    @UnstableApi
    fun create(
        context: Context,
        bufferSize: BufferSize = BufferSize.MEDIUM,
        preferHardwareDecoding: Boolean = true
    ): ExoPlayer {
        val minBufferMs = when (bufferSize) {
            BufferSize.LOW -> 15_000
            BufferSize.MEDIUM -> 30_000
            BufferSize.HIGH -> 60_000
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                minBufferMs * 2,
                2_500,
                5_000
            )
            .build()

        val extensionMode = if (preferHardwareDecoding) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(extensionMode)

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
    }
}
