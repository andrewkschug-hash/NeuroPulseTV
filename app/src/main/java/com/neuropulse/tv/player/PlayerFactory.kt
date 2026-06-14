package com.neuropulse.tv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.neuropulse.tv.domain.model.BufferSize
import com.neuropulse.tv.util.MediaAttribution
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
        val maxBufferMs = TimeshiftManager.maxBufferMsFor(bufferSize).toInt()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                maxBufferMs,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .build()

        val extensionMode = if (preferHardwareDecoding) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val appContext = MediaAttribution.appContext(context, MediaAttribution.MEDIA_PLAYBACK)
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(extensionMode)

        return ExoPlayer.Builder(appContext, renderersFactory)
            .setLoadControl(loadControl)
            .build()
    }
}
