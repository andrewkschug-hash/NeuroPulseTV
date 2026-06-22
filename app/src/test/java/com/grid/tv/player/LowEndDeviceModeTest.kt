package com.grid.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowEndDeviceModeTest {

    @Test
    fun highEndDefault_isNotActive() {
        val profile = LowEndDeviceMode.current()
        assertFalse(profile.active)
        assertEquals(4, profile.maxPaneCount)
        assertEquals(48L * 1024 * 1024, profile.coilMemoryCacheBytes)
    }

    @Test
    fun activeProfile_reducesCachesAndBuffers() {
        val profile = LowEndDeviceMode.Profile(
            active = true,
            totalRamMb = 2048,
            isLowRamDevice = true,
            maxPaneCount = 2,
            coilMemoryCacheBytes = 24L * 1024 * 1024,
            coilDiskCacheBytes = 25L * 1024 * 1024,
            liveStartupPriority = PlaybackStartupPriority.BALANCED,
            maxBufferMsCap = 60_000,
            telemetryVerbose = false,
            performanceAuditEnabled = false,
            epgStartupDelaySec = 45L,
            watchdogPollIntervalMs = 4_000L,
            watchDurationTickMs = 10_000L,
            decodeOnlyMultiPaneAudio = true,
            deferChannelHealthProbe = true
        )
        assertTrue(profile.active)
        assertEquals(2, profile.maxPaneCount)
        assertFalse(profile.telemetryVerbose)
        assertFalse(profile.performanceAuditEnabled)
    }
}
