package com.neuropulse.tv.domain.model

data class ContinueWatchingItem(
    val channel: Channel,
    val lastPosition: Long,
    val lastWatched: Long,
    val programTitle: String?
)
