package com.grid.tv.util

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EpgStabilityMetricsTest {

    @Before
    fun reset() {
        PerformanceAudit.resetEpgMetricsForTests()
    }

    @Test
    fun epgSnapshotEmission_tracksGenerationChangesOnly() {
        PerformanceAudit.logEpgSnapshotEmission(generation = 1L, fingerprint = 100L)
        PerformanceAudit.logEpgSnapshotEmission(generation = 1L, fingerprint = 100L)
        PerformanceAudit.logEpgSnapshotEmission(generation = 2L, fingerprint = 200L)

        assertEquals(3, PerformanceAudit.epgSnapshotEmissionCountForTests())
    }

    @Test
    fun gridAndScreenRecompositionCounters_areIndependent() {
        PerformanceAudit.logEpgRecomposition("HomeEpgScreen")
        PerformanceAudit.logGridSectionRecomposition("HomeEpgChannelList")
        PerformanceAudit.logGridSectionRecomposition("HomeEpgChannelList")

        assertEquals(1, PerformanceAudit.epgRecompositionCountForTests())
        assertEquals(2, PerformanceAudit.gridSectionRecompositionCountForTests())
    }

    @Test
    fun epgSnapshotEmissionsDuringWindow_countsRecentEvents() {
        val nowMs = 1_000_000L
        PerformanceAudit.logEpgSnapshotEmission(1L, 10L)
        assertEquals(1, PerformanceAudit.epgSnapshotEmissionsDuringWindowMs(60_000L, nowMs = nowMs))
    }
}
