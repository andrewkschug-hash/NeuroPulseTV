package com.grid.tv.player

import com.grid.tv.domain.model.BufferSize
import org.junit.Assert.assertEquals
import org.junit.Test

class IptvBufferProfilesTest {

    @Test
    fun fastProfile_matchesIptvStartingPoint() {
        val profile = IptvBufferProfiles.forPriority(PlaybackStartupPriority.FAST)
        assertEquals(5_000, profile.minBufferMs)
        assertEquals(60_000, profile.maxBufferMs)
    }

    @Test
    fun balancedProfile_matchesIptvStartingPoint() {
        val profile = IptvBufferProfiles.forPriority(PlaybackStartupPriority.BALANCED)
        assertEquals(10_000, profile.minBufferMs)
        assertEquals(120_000, profile.maxBufferMs)
    }

    @Test
    fun stableProfile_matchesIptvStartingPoint() {
        val profile = IptvBufferProfiles.forPriority(PlaybackStartupPriority.STABLE)
        assertEquals(15_000, profile.minBufferMs)
        assertEquals(300_000, profile.maxBufferMs)
    }

    @Test
    fun bufferSizeSetting_mapsToProfile() {
        assertEquals("FAST", IptvBufferProfiles.resolve(BufferSize.LOW, null, false).profileName)
        assertEquals("BALANCED", IptvBufferProfiles.resolve(BufferSize.MEDIUM, null, false).profileName)
        assertEquals("STABLE", IptvBufferProfiles.resolve(BufferSize.HIGH, null, false).profileName)
    }

    @Test
    fun legacyBaseline_documentsOldTvDefaults() {
        val legacy = IptvBufferProfiles.LEGACY_TV_STABLE_MEDIUM
        assertEquals(45_000, legacy.minBufferMs)
        assertEquals(1_800_000, legacy.maxBufferMs)
    }
}
