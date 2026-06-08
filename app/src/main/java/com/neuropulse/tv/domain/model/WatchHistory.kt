package com.neuropulse.tv.domain.model

data class WatchHistory(
    val channelId: Long,
    val lastPosition: Long,
    val lastWatched: Long
)
