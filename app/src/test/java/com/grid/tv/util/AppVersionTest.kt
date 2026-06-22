package com.grid.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun semverToVersionCode_twoDigitMinor_beatsSingleDigitMinor() {
        assertTrue(
            "2.10.0 must exceed 2.2.0",
            AppVersion.semverToVersionCode("2.10.0") > AppVersion.semverToVersionCode("2.2.0")
        )
        assertEquals(21_000, AppVersion.semverToVersionCode("2.10.0"))
        assertEquals(20_200, AppVersion.semverToVersionCode("2.2.0"))
    }

    @Test
    fun semverToVersionCode_isMonotonicAcrossPatchBumps() {
        assertTrue(AppVersion.semverToVersionCode("2.2.1") > AppVersion.semverToVersionCode("2.2.0"))
        assertTrue(AppVersion.semverToVersionCode("3.0.0") > AppVersion.semverToVersionCode("2.99.99"))
    }

    @Test
    fun semverToVersionCode_stripsVersionPrefix() {
        assertEquals(10_000, AppVersion.semverToVersionCode("v1.0.0"))
        assertEquals(10_000, AppVersion.semverToVersionCode("V1.0.0"))
    }
}
