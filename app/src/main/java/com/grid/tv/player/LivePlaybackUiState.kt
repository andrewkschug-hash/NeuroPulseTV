package com.grid.tv.player

/**
 * Single UI-facing snapshot of live playback — one [StateFlow] emission per logical change
 * so Compose collects once instead of fanning out across multiple flows.
 */
data class LivePlaybackUiState(
    val status: StreamPlaybackStatus = StreamPlaybackStatus.IDLE,
    val failover: StreamFailoverUiState = StreamFailoverUiState(),
    val timeshift: TimeshiftUiState = TimeshiftUiState(),
    val activeChannelId: Long? = null,
    val activeStreamUrl: String? = null
)
