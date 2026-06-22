package com.grid.tv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHttpFailureTest {

    @Test
    fun isRetriableHttpStatus_allowsTransientServerErrors() {
        assertTrue(PlaybackHttpFailure.isRetriableHttpStatus(503))
        assertTrue(PlaybackHttpFailure.isRetriableHttpStatus(429))
        assertTrue(PlaybackHttpFailure.isRetriableHttpStatus(408))
    }

    @Test
    fun isRetriableHttpStatus_rejectsProviderClientErrorsIncluding458() {
        assertFalse(PlaybackHttpFailure.isRetriableHttpStatus(458))
        assertFalse(PlaybackHttpFailure.isRetriableHttpStatus(404))
        assertFalse(PlaybackHttpFailure.isRetriableHttpStatus(403))
    }

    @Test
    fun isFatalHttpStatus_matchesInverseOfRetriableFor4xxAnd5xx() {
        assertTrue(PlaybackHttpFailure.isFatalHttpStatus(458))
        assertFalse(PlaybackHttpFailure.isFatalHttpStatus(503))
    }
}
