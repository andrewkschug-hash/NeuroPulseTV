package com.grid.tv.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerformanceAuditZapTest {

    @Before
    @After
    fun reset() {
        PerformanceAudit.resetZapMetricsForTests()
    }

    @Test
    fun completeZap_recordsReadyLatencyForMatchingChannel() {
        PerformanceAudit.beginZap(channelId = 42L, playerHash = 1001)
        PerformanceAudit.completeZap(playerHash = 1001, channelId = 42L)

        assertEquals(1, PerformanceAudit.zapReadySamplesForTests().size)
        assertTrue(PerformanceAudit.zapReadySamplesForTests().single() >= 0L)
    }

    @Test
    fun completeZap_ignoresMismatchedChannel() {
        PerformanceAudit.beginZap(channelId = 42L, playerHash = 1001)
        PerformanceAudit.completeZap(playerHash = 1001, channelId = 99L)

        assertTrue(PerformanceAudit.zapReadySamplesForTests().isEmpty())
    }
}
