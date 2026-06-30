package com.grid.tv.data.network.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMatchRankerTest {

    @Test
    fun titleSimilarity_exactAndPartialMatch() {
        assertEquals(1.0, TmdbMatchRanker.titleSimilarity("inception", "inception"), 0.001)
        assertTrue(TmdbMatchRanker.titleSimilarity("inception", "the inception") >= 0.85)
        assertTrue(TmdbMatchRanker.titleSimilarity("inception", "totally different") < 0.3)
    }

    @Test
    fun yearMatchScore_softMatching() {
        assertEquals(1.0, TmdbMatchRanker.yearMatchScore(2010, 2010), 0.001)
        assertEquals(0.65, TmdbMatchRanker.yearMatchScore(2010, 2011), 0.001)
        assertEquals(0.0, TmdbMatchRanker.yearMatchScore(2010, 1990), 0.001)
        assertEquals(0.55, TmdbMatchRanker.yearMatchScore(null, 2010), 0.001)
    }

    @Test
    fun popularityBoost_capsContribution() {
        assertEquals(0.0, TmdbMatchRanker.popularityBoost(0.0), 0.001)
        assertTrue(TmdbMatchRanker.popularityBoost(50.0) > 0.0)
        assertTrue(TmdbMatchRanker.popularityBoost(500.0) <= 1.0)
    }
}
