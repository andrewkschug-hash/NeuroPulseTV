package com.grid.tv.player



import android.content.Context

import androidx.media3.common.AudioAttributes

import androidx.media3.common.C

import androidx.media3.common.util.UnstableApi

import androidx.media3.exoplayer.DefaultLoadControl

import androidx.media3.exoplayer.DefaultRenderersFactory

import androidx.media3.exoplayer.ExoPlayer

import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

import com.grid.tv.domain.model.AppSettings

import com.grid.tv.domain.model.BufferSize

import com.grid.tv.util.MediaAttribution

import javax.inject.Inject

import javax.inject.Singleton



@Singleton

class PlayerFactory @Inject constructor(

    private val playbackHttpDataSourceFactory: PlaybackHttpDataSourceFactory,

    private val decoderPressureTracker: DecoderPressureTracker

) {



    @UnstableApi

    fun create(

        context: Context,

        bufferSize: BufferSize = BufferSize.MEDIUM,

        preferHardwareDecoding: Boolean = true,

        startupPriority: PlaybackStartupPriority? = null,

        handleAudioFocus: Boolean = true,

        networkSettings: AppSettings? = null,

        decoderOwner: String = "playback"

    ): ExoPlayer {

        val caps = context.devicePlaybackCapabilities()

        val profile = IptvBufferProfiles.resolve(

            bufferSize = bufferSize,

            startupPriority = startupPriority,

            isLowEndDevice = caps.isLowEndDevice

        )



        com.grid.tv.util.PerformanceAudit.logBufferProfileApplied(

            profile = profile,

            legacyBaseline = IptvBufferProfiles.LEGACY_TV_STABLE_MEDIUM

        )



        val loadControl = DefaultLoadControl.Builder()

            .setBufferDurationsMs(

                profile.minBufferMs,

                profile.maxBufferMs,

                profile.bufferForPlaybackMs,

                profile.bufferForPlaybackAfterRebufferMs

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



        val mediaSourceFactory = playbackHttpDataSourceFactory.mediaSourceFactory(networkSettings)



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

                decoderPressureTracker.registerPlayer(decoderOwner, exo)

            }

    }

}

