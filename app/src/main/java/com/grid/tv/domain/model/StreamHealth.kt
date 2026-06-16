package com.grid.tv.domain.model

data class StreamHealth(
    val channelId: Long,
    val reliabilityScore: Int,
    val averageLoadTimeMs: Long,
    val bufferEventsPerSession: Float,
    val lastSuccessfulLoad: Long
)
