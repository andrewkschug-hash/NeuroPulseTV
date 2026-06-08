package com.neuropulse.tv.feature.health

import com.neuropulse.tv.domain.model.StreamHealth
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHealthEngineTest {
    @Test
    fun reliabilityScore_dropsWhenFailuresAndBufferIncrease() {
        val engine = StreamHealthEngine()
        val prev = StreamHealth(channelId = 1, reliabilityScore = 95, averageLoadTimeMs = 500, bufferEventsPerSession = 0.5f, lastSuccessfulLoad = 1)
        val result = engine.compute(previous = prev, loadMs = 5000, bufferEvents = 6, success = false)
        assertTrue(result.reliabilityScore < 95)
    }
}
