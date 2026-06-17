package com.grid.tv.feature.health.intelligence.simulation

import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHealthSimulatorTest {
    @Test
    fun run_validatesAggregationAndRanking() {
        val report = StreamHealthSimulator().run(
            sessionCount = 10_000,
            channelCount = 200,
            providerCount = 5
        )
        assertTrue(report.sessionsGenerated == 10_000)
        assertTrue(report.aggregationValidated)
        assertTrue(report.rankingConsistent)
        assertTrue(report.averageHealthScore in 0.0..100.0)
        assertTrue(report.healthiestChannels.first().healthScore >= report.leastReliableChannels.first().healthScore)
    }
}
