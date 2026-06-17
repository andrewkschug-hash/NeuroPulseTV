package com.grid.tv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.grid.tv.domain.model.BufferSize
import com.grid.tv.util.MediaAttribution
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerFactory @Inject constructor() {

    companion object {
        private const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 GridTV/1.0"
    }

    @UnstableApi
    fun create(
        context: Context,
        bufferSize: BufferSize = BufferSize.MEDIUM,
        preferHardwareDecoding: Boolean = true,
        startupPriority: PlaybackStartupPriority? = null
    ): ExoPlayer {
        val caps = context.devicePlaybackCapabilities()
        val priority = startupPriority ?: caps.startupPriority

        var maxBufferMs = TimeshiftManager.maxBufferMsFor(bufferSize).toInt()
        if (caps.isLowEndDevice) {
            maxBufferMs = maxBufferMs.coerceAtMost(20 * 60 * 1000)
        }

        val (minBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs) = when (priority) {
            PlaybackStartupPriority.FAST -> Triple(15_000, 1_500, 3_000)
            PlaybackStartupPriority.BALANCED -> Triple(30_000, 2_500, 5_000)
            PlaybackStartupPriority.STABLE -> Triple(45_000, 3_500, 7_000)
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs
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

        val trackSelector = DefaultTrackSelector(appContext)
        if (caps.isLowEndDevice) {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(2_500_000)
                .build()
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(STREAM_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        return ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
    }
}
