package com.grid.tv.feature.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun stripsLeadingV() {
        assertTrue(VersionCompare.isRemoteNewer("v2.2.0", "2.1.0"))
        assertTrue(VersionCompare.isRemoteNewer("V3.0.0", "2.9.9"))
    }

    @Test
    fun equalVersionsAreNotNewer() {
        assertFalse(VersionCompare.isRemoteNewer("2.1.0", "2.1.0"))
        assertFalse(VersionCompare.isRemoteNewer("v2.1.0", "2.1.0"))
    }

    @Test
    fun olderRemoteIsNotNewer() {
        assertFalse(VersionCompare.isRemoteNewer("2.0.0", "2.1.0"))
        assertFalse(VersionCompare.isRemoteNewer("1.9.9", "2.0.0"))
    }

    @Test
    fun patchBumpIsNewer() {
        assertTrue(VersionCompare.isRemoteNewer("2.1.1", "2.1.0"))
    }

    @Test
    fun handlesMissingPatchSegment() {
        assertTrue(VersionCompare.isRemoteNewer("2.2", "2.1.0"))
        assertFalse(VersionCompare.isRemoteNewer("2.1", "2.1.0"))
    }
}
