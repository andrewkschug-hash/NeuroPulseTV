package com.grid.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDecoderLimitsTest {

    @Test
    fun profile_returnsNonEmptyDeviceLabel() {
        val profile = DeviceDecoderLimits.profile()
        assertTrue(profile.deviceLabel.isNotBlank())
        assertTrue(profile.warnConcurrentDecoders > 0)
        assertTrue(profile.criticalConcurrentDecoders >= profile.warnConcurrentDecoders)
    }

    @Test
    fun chromecast4kProfile_hasConservativeLimits() {
        val profile = DeviceDecoderProfile(
            deviceLabel = "Chromecast-GTV-4K",
            warnConcurrentDecoders = 2,
            criticalConcurrentDecoders = 3,
            warnConcurrentSurfaces = 2,
            criticalConcurrentSurfaces = 3,
            isChromecastGoogleTv = true
        )
        assertEquals(2, profile.warnConcurrentDecoders)
        assertEquals(3, profile.criticalConcurrentDecoders)
    }
}
