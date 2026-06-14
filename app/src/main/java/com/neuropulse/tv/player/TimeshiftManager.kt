package com.neuropulse.tv.player

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs

object TimeshiftManager {
    var isTimeshifting: Boolean = false
        private set
    var liveEdgePositionMs: Long = 0L
        private set
    var bufferStartMs: Long = 0L
        private set
    var maxBufferMs: Long = 1_800_000L

    private const val LIVE_EDGE_TOLERANCE_MS = 10_000L
    private const val FAST_FORWARD_LIVE_THRESHOLD_MS = 5_000L
    private const val MIN_REWIND_HEADROOM_MS = 10_000L

    fun reset() {
        isTimeshifting = false
        liveEdgePositionMs = 0L
        bufferStartMs = 0L
    }

    fun updateLiveEdge(player: ExoPlayer) {
        if (!player.isCurrentMediaItemDynamic) return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        liveEdgePositionMs = duration
        bufferStartMs = maxOf(0L, liveEdgePositionMs - maxBufferMs)
        if (isAtLiveEdge(player)) {
            isTimeshifting = false
        }
    }

    fun canRewind(player: ExoPlayer): Boolean {
        return player.currentPosition > bufferStartMs + MIN_REWIND_HEADROOM_MS
    }

    fun isAtLiveEdge(player: ExoPlayer): Boolean {
        if (!player.isCurrentMediaItemDynamic) return true
        if (liveEdgePositionMs <= 0L) return true
        return player.currentPosition >= liveEdgePositionMs - LIVE_EDGE_TOLERANCE_MS
    }

    fun behindLiveMs(player: ExoPlayer): Long {
        if (liveEdgePositionMs <= 0L) return 0L
        return (liveEdgePositionMs - player.currentPosition).coerceAtLeast(0L)
    }

    fun jumpToLive(player: ExoPlayer) {
        player.seekToDefaultPosition()
        player.playWhenReady = true
        isTimeshifting = false
    }

    fun rewind(player: ExoPlayer, ms: Long = 30_000L) {
        val target = maxOf(bufferStartMs, player.currentPosition - ms)
        player.seekTo(target)
        isTimeshifting = true
    }

    fun fastForward(player: ExoPlayer, ms: Long = 30_000L) {
        val target = player.currentPosition + ms
        if (target >= liveEdgePositionMs - FAST_FORWARD_LIVE_THRESHOLD_MS) {
            jumpToLive(player)
        } else {
            player.seekTo(target)
            isTimeshifting = true
        }
    }

    fun seekTo(player: ExoPlayer, positionMs: Long) {
        val target = positionMs.coerceIn(bufferStartMs, liveEdgePositionMs)
        player.seekTo(target)
        isTimeshifting = !isAtLiveEdge(player)
    }

    fun seekRelative(player: ExoPlayer, deltaMs: Long) {
        seekTo(player, player.currentPosition + deltaMs)
    }

    fun togglePlayPause(player: ExoPlayer) {
        if (player.isPlaying) {
            player.playWhenReady = false
            if (isAtLiveEdge(player)) {
                isTimeshifting = true
            }
        } else {
            player.playWhenReady = true
            if (isAtLiveEdge(player)) {
                isTimeshifting = false
            }
        }
    }

    fun maxBufferMsFor(bufferSize: com.neuropulse.tv.domain.model.BufferSize): Long = when (bufferSize) {
        com.neuropulse.tv.domain.model.BufferSize.LOW -> 600_000L
        com.neuropulse.tv.domain.model.BufferSize.MEDIUM -> 1_800_000L
        com.neuropulse.tv.domain.model.BufferSize.HIGH -> 3_600_000L
    }
}
