package com.neuropulse.tv.player

import androidx.compose.ui.graphics.Color
import com.neuropulse.tv.ui.theme.EpgColors

enum class StreamPlaybackStatus {
    IDLE,
    LOADING,
    PLAYING,
    AUDIO_ONLY,
    NO_SIGNAL,
    STALLED,
    ERROR,
    UNAVAILABLE
}

fun StreamPlaybackStatus.userLabel(): String = when (this) {
    StreamPlaybackStatus.IDLE -> ""
    StreamPlaybackStatus.LOADING -> "Loading…"
    StreamPlaybackStatus.PLAYING -> "Live"
    StreamPlaybackStatus.AUDIO_ONLY -> "Audio only"
    StreamPlaybackStatus.NO_SIGNAL -> "No signal"
    StreamPlaybackStatus.STALLED -> "Frozen"
    StreamPlaybackStatus.ERROR -> "Stream error"
    StreamPlaybackStatus.UNAVAILABLE -> "Offline"
}

fun StreamPlaybackStatus.isHealthy(): Boolean =
    this == StreamPlaybackStatus.PLAYING || this == StreamPlaybackStatus.AUDIO_ONLY || this == StreamPlaybackStatus.LOADING

/** Full-screen blocking overlay — only for confirmed failures, not buffering. */
fun StreamPlaybackStatus.shouldShowBlockingOverlay(): Boolean = when (this) {
    StreamPlaybackStatus.ERROR,
    StreamPlaybackStatus.UNAVAILABLE,
    StreamPlaybackStatus.NO_SIGNAL,
    StreamPlaybackStatus.STALLED -> true
    else -> false
}

fun StreamPlaybackStatus.badgeColor(): Color = when (this) {
    StreamPlaybackStatus.PLAYING -> Color(0xFF2ECC71)
    StreamPlaybackStatus.AUDIO_ONLY -> Color(0xFF5B9BD5)
    StreamPlaybackStatus.LOADING -> EpgColors.TextDimmed
    StreamPlaybackStatus.NO_SIGNAL, StreamPlaybackStatus.STALLED -> Color(0xFFFFB020)
    StreamPlaybackStatus.ERROR, StreamPlaybackStatus.UNAVAILABLE -> Color(0xFFFF3B3B)
    StreamPlaybackStatus.IDLE -> EpgColors.TextDimmed
}
