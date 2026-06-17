package com.grid.tv.feature.vod.personalization.simulation

import org.junit.Assert.assertTrue
import org.junit.Test

class VodPersonalizationSimulatorTest {
    @Test
    fun run_loveIslandScenarioAndMetrics() {
        val report = VodPersonalizationSimulator().run(
            userCount = 10_000,
            seriesCount = 5_000,
            episodeCount = 100_000
        )
        assertTrue(report.loveIslandScenarioPassed)
        assertTrue(report.detectionAccuracy >= 0.95)
        assertTrue(report.notificationsGenerated > 0)
        assertTrue(report.feedQualityScore >= 70.0)
    }
}
