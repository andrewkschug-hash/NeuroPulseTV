package com.neuropulse.tv.feature.health

import com.neuropulse.tv.domain.model.StreamHealth

class StreamHealthEngine {
    fun compute(previous: StreamHealth?, loadMs: Long, bufferEvents: Int, success: Boolean): StreamHealth {
        val sessions = ((previous?.channelId?.let { 0 } ?: 0) + 1)
        val prevLoad = previous?.averageLoadTimeMs ?: loadMs
        val avgLoad = ((prevLoad + loadMs) / 2).coerceAtLeast(1)
        val prevBuffer = previous?.bufferEventsPerSession ?: bufferEvents.toFloat()
        val avgBuffer = (prevBuffer + bufferEvents) / 2f

        val base = 100
        val loadPenalty = (avgLoad / 400).toInt().coerceAtMost(40)
        val bufferPenalty = (avgBuffer * 8).toInt().coerceAtMost(40)
        val failPenalty = if (success) 0 else 20
        val score = (base - loadPenalty - bufferPenalty - failPenalty).coerceIn(0, 100)

        return StreamHealth(
            channelId = previous?.channelId ?: 0,
            reliabilityScore = score,
            averageLoadTimeMs = avgLoad,
            bufferEventsPerSession = avgBuffer,
            lastSuccessfulLoad = if (success) System.currentTimeMillis() else (previous?.lastSuccessfulLoad ?: 0)
        )
    }
}
