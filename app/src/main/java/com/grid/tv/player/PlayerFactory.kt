package com.grid.tv.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
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
        startupPriority: PlaybackStartupPriority? = null,
        handleAudioFocus: Boolean = true
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

        val extensionMode = when {
            caps.isEmulator -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            preferHardwareDecoding -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val appContext = MediaAttribution.appContext(context, MediaAttribution.MEDIA_PLAYBACK)
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(extensionMode)

        val trackSelector = DefaultTrackSelector(appContext)
        when {
            caps.isEmulator -> {
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
                    .setMaxVideoBitrate(8_000_000)
                    .build()
            }
            caps.isLowEndDevice -> {
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .setMaxVideoSize(1280, 720)
                    .setMaxVideoBitrate(2_500_000)
                    .build()
            }
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(STREAM_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(60_000)
            .setReadTimeoutMs(60_000)

        val hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)

        val mediaSourceFactory = IptvMediaSourceFactory(dataSourceFactory, hlsMediaSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, handleAudioFocus)
            .build()
            .also { exo ->
                exo.addListener(PlayerAudioRecoveryListener(exo))
            }
    }

    /**
     * Routes .m3u8 URLs through a tuned HLS factory; everything else uses the default factory.
     */
    @UnstableApi
    private class IptvMediaSourceFactory(
        dataSourceFactory: DataSource.Factory,
        private val hlsFactory: HlsMediaSource.Factory
    ) : MediaSource.Factory {
        private val defaultFactory = DefaultMediaSourceFactory(dataSourceFactory)

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val uri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            return if (uri.contains(".m3u8", ignoreCase = true)) {
                hlsFactory.createMediaSource(mediaItem)
            } else {
                defaultFactory.createMediaSource(mediaItem)
            }
        }

        override fun setDrmSessionManagerProvider(
            drmSessionManagerProvider: DrmSessionManagerProvider
        ): MediaSource.Factory {
            defaultFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
            hlsFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
            return this
        }

        override fun setLoadErrorHandlingPolicy(
            loadErrorHandlingPolicy: LoadErrorHandlingPolicy
        ): MediaSource.Factory {
            defaultFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            hlsFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            return this
        }

        override fun getSupportedTypes(): IntArray = defaultFactory.supportedTypes
    }
}