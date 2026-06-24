package com.grid.tv.feature.vod

import org.junit.Assert.assertEquals
import org.junit.Test

class VodResumeResolverTest {
    @Test
    fun applyThresholdRejectsShortProgress() {
        assertEquals(0L, VodResumeResolver.applyThreshold(4_999L, 120_000L))
    }

    @Test
    fun applyThresholdKeepsMidPlayback() {
        assertEquals(60_000L, VodResumeResolver.applyThreshold(60_000L, 120_000L))
    }

    @Test
    fun applyThresholdRejectsNearCompletion() {
        assertEquals(0L, VodResumeResolver.applyThreshold(96_000L, 100_000L))
    }

    @Test
    fun applyThresholdAllowsUnknownDuration() {
        assertEquals(30_000L, VodResumeResolver.applyThreshold(30_000L, 0L))
    }
}
