package com.grid.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.util.MediaAttribution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared ExoPlayer pool for Split View and MultiView (max 4 pane slots).
 * Owned by [PlaybackOrchestrator]; released on session exit and background teardown.
 */
@Singleton
class MultiPanePlaybackPool @Inject constructor(
    private val playerFactory: PlayerFactory,
    private val decoderPressureTracker: DecoderPressureTracker
) {
    private val players = mutableMapOf<Int, ExoPlayer>()
    private var activeAudioPaneIndex: Int = 0

    @UnstableApi
    fun getOrCreatePlayer(context: Context, paneIndex: Int, owner: String): ExoPlayer {
        return players.getOrPut(paneIndex) {
            val appContext = MediaAttribution.appContext(context, MediaAttribution.MEDIA_PLAYBACK)
            playerFactory.create(
                context = appContext,
                handleAudioFocus = false,
                decoderOwner = "${owner}_pane_$paneIndex"
            )
        }
    }

    fun playerForPane(paneIndex: Int): ExoPlayer? = players[paneIndex]

    fun tunePane(context: Context, paneIndex: Int, streamUrl: String, owner: String) {
        if (streamUrl.isBlank()) return
        val player = getOrCreatePlayer(context, paneIndex, owner)
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == streamUrl) {
            if (!player.playWhenReady) player.playWhenReady = true
            return
        }
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Keeps pane slots [0, paneCount) in sync with [streamUrlsByPane].
     * Releases players for removed slots.
     */
    fun syncFromStreamUrls(
        context: Context,
        streamUrlsByPane: List<String?>,
        audioPaneIndex: Int,
        decodeOnlyAudioPane: Boolean,
        owner: String
    ) {
        activeAudioPaneIndex = audioPaneIndex.coerceAtLeast(0)
        val paneCount = streamUrlsByPane.size
        (players.keys - (0 until paneCount).toSet()).toList().forEach(::releasePane)

        streamUrlsByPane.forEachIndexed { index, url ->
            if (decodeOnlyAudioPane && index != activeAudioPaneIndex) {
                players[index]?.let(::pauseAndClear)
                return@forEachIndexed
            }
            url?.let { tunePane(context, index, it, owner) }
        }
        applyAudioVolumes(decodeOnlyAudioPane)
    }

    fun syncDecodePolicy(decodeOnlyAudioPane: Boolean, activeAudioPanelIndex: Int) {
        activeAudioPaneIndex = activeAudioPanelIndex.coerceAtLeast(0)
        players.forEach { (index, player) ->
            val shouldDecode = !decodeOnlyAudioPane || index == activeAudioPaneIndex
            if (shouldDecode) {
                player.volume = if (index == activeAudioPaneIndex) 1f else 0f
                if (player.mediaItemCount > 0) {
                    player.playWhenReady = true
                }
            } else {
                pauseAndClear(player)
            }
        }
    }

    fun setActiveAudioPane(paneIndex: Int) {
        activeAudioPaneIndex = paneIndex
        applyAudioVolumes(decodeOnlyAudioPane = false)
    }

    fun releasePane(paneIndex: Int) {
        players.remove(paneIndex)?.let { player ->
            decoderPressureTracker.unregisterPlayer(player)
            player.release()
        }
    }

    fun releaseAll() {
        players.values.forEach { player ->
            decoderPressureTracker.unregisterPlayer(player)
            player.release()
        }
        players.clear()
        activeAudioPaneIndex = 0
    }

    fun activePlayer(): ExoPlayer? = players[activeAudioPaneIndex]

    fun pauseAll() {
        players.values.forEach { it.playWhenReady = false }
    }

    fun resumeAll() {
        players.values.forEach { player ->
            if (player.mediaItemCount > 0) player.playWhenReady = true
        }
    }

    private fun applyAudioVolumes(decodeOnlyAudioPane: Boolean) {
        if (decodeOnlyAudioPane) return
        players.forEach { (index, player) ->
            if (player.mediaItemCount > 0) {
                player.volume = if (index == activeAudioPaneIndex) 1f else 0f
            }
        }
    }

    private fun pauseAndClear(player: ExoPlayer) {
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        player.volume = 0f
    }
}
