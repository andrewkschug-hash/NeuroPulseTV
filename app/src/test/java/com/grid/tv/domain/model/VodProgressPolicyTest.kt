package com.grid.tv.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodProgressPolicyTest {
    @Test
    fun watchedAtNinetyPercent() {
        assertTrue(VodProgressPolicy.isWatched(9000, 10_000))
        assertFalse(VodProgressPolicy.isWatched(8000, 10_000))
    }

    @Test
    fun nextUpTriggersNearEnd() {
        assertTrue(VodProgressPolicy.shouldOfferNextUp(9200, 10_000))
        assertFalse(VodProgressPolicy.shouldOfferNextUp(5000, 10_000))
    }
}
