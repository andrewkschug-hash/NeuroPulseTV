package com.neuropulse.tv.player

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

object TimeshiftManager {
    var isTimeshifting: Boolean = false
        private set
    var liveEdgePositionMs: Long = 0L
        private set
    var bufferStartMs: Long = 0L
        private set
    var maxBufferMs: Long = 1_800_000L

    private const val LIVE_EDGE_TOLERANCE_MS = 3_000L
    private const val FAST_FORWARD_LIVE_THRESHOLD_MS = 3_000L
    private const val MIN_REWIND_HEADROOM_MS = 2_000L

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
        bufferStartMs = 0L

        if (isAtLiveEdge(player)) {
            isTimeshifting = false
        }
    }

    fun canRewind(player: ExoPlayer): Boolean {
        if (!player.isCurrentMediaItemDynamic) return false
        if (liveEdgePositionMs <= 0L) return false
        return player.currentPosition > bufferStartMs + MIN_REWIND_HEADROOM_MS
    }

    fun canFastForward(player: ExoPlayer): Boolean = !isAtLiveEdge(player)

    fun isAtLiveEdge(player: ExoPlayer): Boolean {
        if (!player.isCurrentMediaItemDynamic) return true
        val liveOffset = player.currentLiveOffset
        if (liveOffset != C.TIME_UNSET && liveOffset >= 0L) {
            return liveOffset <= LIVE_EDGE_TOLERANCE_MS
        }
        if (liveEdgePositionMs <= 0L) return true
        return player.currentPosition >= liveEdgePositionMs - LIVE_EDGE_TOLERANCE_MS
    }

    fun behindLiveMs(player: ExoPlayer): Long {
        if (!player.isCurrentMediaItemDynamic) return 0L
        if (isAtLiveEdge(player)) return 0L
        val liveOffset = player.currentLiveOffset
        if (liveOffset != C.TIME_UNSET && liveOffset >= 0L) {
            return liveOffset
        }
        if (liveEdgePositionMs <= 0L) return 0L
        return (liveEdgePositionMs - player.currentPosition).coerceAtLeast(0L)
    }

    fun availableRewindMs(player: ExoPlayer): Long =
        (player.currentPosition - bufferStartMs).coerceAtLeast(0L)

    fun availableForwardMs(player: ExoPlayer): Long =
        (liveEdgePositionMs - player.currentPosition).coerceAtLeast(0L)

    fun jumpToLive(player: ExoPlayer) {
        player.seekToDefaultPosition()
        player.playWhenReady = true
        isTimeshifting = false
    }

    fun rewind(player: ExoPlayer, ms: Long = 30_000L) {
        if (!canRewind(player)) return
        val target = (player.currentPosition - ms).coerceAtLeast(bufferStartMs)
        player.seekTo(target)
        isTimeshifting = !isAtLiveEdge(player)
    }

    fun fastForward(player: ExoPlayer, ms: Long = 30_000L) {
        if (!canFastForward(player)) return
        val liveOffset = player.currentLiveOffset
        if (liveOffset != C.TIME_UNSET && liveOffset >= 0L) {
            if (liveOffset <= ms + FAST_FORWARD_LIVE_THRESHOLD_MS) {
                jumpToLive(player)
            } else {
                player.seekTo(player.currentPosition + ms)
                isTimeshifting = true
            }
            return
        }
        val target = (player.currentPosition + ms).coerceAtMost(liveEdgePositionMs)
        if (target >= liveEdgePositionMs - FAST_FORWARD_LIVE_THRESHOLD_MS) {
            jumpToLive(player)
        } else {
            player.seekTo(target)
            isTimeshifting = true
        }
    }

    fun seekTo(player: ExoPlayer, positionMs: Long) {
        val duration = player.duration
        val maxPosition = if (duration != C.TIME_UNSET && duration > 0L) {
            duration
        } else {
            liveEdgePositionMs
        }
        val target = positionMs.coerceIn(bufferStartMs, maxPosition)
        player.seekTo(target)
    }

    fun seekRelative(player: ExoPlayer, deltaMs: Long) {
        if (deltaMs < 0 && !canRewind(player)) return
        if (deltaMs > 0 && !canFastForward(player)) return
        if (deltaMs > 0) {
            val liveOffset = player.currentLiveOffset
            if (liveOffset != C.TIME_UNSET && liveOffset >= 0L &&
                liveOffset <= deltaMs + FAST_FORWARD_LIVE_THRESHOLD_MS
            ) {
                jumpToLive(player)
                return
            }
        }
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
