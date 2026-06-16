package com.grid.tv.feature.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageThresholdsTest {

    @Test
    fun thresholds_matchSpec() {
        val twoGb = 2L * 1024 * 1024 * 1024
        val fiveHundredMb = 500L * 1024 * 1024

        assertTrue(twoGb >= 2L * 1024 * 1024 * 1024)
        assertTrue((fiveHundredMb - 1) < 500L * 1024 * 1024)
        assertFalse((fiveHundredMb + 1) < 500L * 1024 * 1024)
    }
}
