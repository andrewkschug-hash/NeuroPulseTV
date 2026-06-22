package com.grid.tv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamFailoverControllerTest {

    @Test
    fun failoverMessages_matchUserFacingCopy() {
        assertEquals("Recovering stream...", StreamFailoverController.RECOVERING_MESSAGE)
        assertEquals("Stream restored", StreamFailoverController.RESTORED_MESSAGE)
    }

    @Test
    fun defaultStreamRetries_matchesAppSettingsDefault() {
        assertEquals(3, StreamFailoverController.DEFAULT_STREAM_RETRIES)
    }
}
