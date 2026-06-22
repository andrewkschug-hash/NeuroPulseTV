package com.grid.tv.feature.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostFailureTrackerTest {

    @Test
    fun blacklistsHostAfterThreshold() {
        val tracker = HostFailureTracker(blacklistThreshold = 3)
        repeat(3) { tracker.recordDnsFailure("bad.cdn.example") }
        assertFalse(tracker.isBlacklisted("bad.cdn.example"))
        tracker.recordDnsFailure("bad.cdn.example")
        assertTrue(tracker.isBlacklisted("bad.cdn.example"))
    }

    @Test
    fun resetSessionClearsBlacklist() {
        val tracker = HostFailureTracker(blacklistThreshold = 1)
        tracker.recordDnsFailure("bad.cdn.example")
        tracker.recordDnsFailure("bad.cdn.example")
        assertTrue(tracker.isBlacklisted("bad.cdn.example"))
        tracker.resetSession()
        assertFalse(tracker.isBlacklisted("bad.cdn.example"))
    }
}
