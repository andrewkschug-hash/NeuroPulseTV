package com.grid.tv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CumulativeBufferingTrackerTest {

    @Test
    fun intermittentBuffering_exceedsBudgetWithinWindow() {
        val tracker = CumulativeBufferingTracker(
            windowMs = 60_000L,
            budgetMs = 45_000L,
            startupGraceMs = 0L,
            healthyResetMs = 60_000L
        )
        tracker.onTuneStarted(0L)
        tracker.onFirstFrameRendered(0L)

        var now = 0L
        repeat(3) {
            tracker.onBufferingStarted(now)
            now += 10_000L
            tracker.onBufferingEnded(now)
            now += 2_000L
        }
        tracker.onBufferingStarted(now)
        now += 15_000L

        assertTrue(
            tracker.isBudgetExceeded(
                nowMs = now,
                requireFirstFrame = true,
                hasRenderedFirstFrame = true
            )
        )
    }

    @Test
    fun closedBufferingEpisodes_exceedRollingBudget() {
        val tracker = CumulativeBufferingTracker(
            windowMs = 60_000L,
            budgetMs = 40_000L,
            startupGraceMs = 0L,
            healthyResetMs = 60_000L
        )
        tracker.onTuneStarted(0L)
        tracker.onFirstFrameRendered(0L)
        tracker.onBufferingStarted(0L)
        tracker.onBufferingEnded(15_000L)
        tracker.onBufferingStarted(17_000L)
        tracker.onBufferingEnded(32_000L)
        tracker.onBufferingStarted(34_000L)
        tracker.onBufferingEnded(49_000L)

        assertTrue(
            tracker.isBudgetExceeded(
                nowMs = 50_000L,
                requireFirstFrame = true,
                hasRenderedFirstFrame = true
            )
        )
    }

    @Test
    fun startupBuffering_doesNotViolateBudgetBeforeGraceEnds() {
        val tracker = CumulativeBufferingTracker(
            windowMs = 60_000L,
            budgetMs = 45_000L,
            startupGraceMs = 12_000L,
            healthyResetMs = 10_000L
        )
        var now = 0L
        tracker.onTuneStarted(now)
        tracker.onBufferingStarted(now)
        now = 10_000L
        assertFalse(
            tracker.isBudgetExceeded(
                nowMs = now,
                requireFirstFrame = false,
                hasRenderedFirstFrame = false
            )
        )
    }

    @Test
    fun healthyPlayback_resetsBudgetAfterStablePeriod() {
        val tracker = CumulativeBufferingTracker(
            windowMs = 60_000L,
            budgetMs = 45_000L,
            startupGraceMs = 0L,
            healthyResetMs = 10_000L
        )
        var now = 0L
        tracker.onTuneStarted(now)
        tracker.onFirstFrameRendered(now)
        tracker.onBufferingStarted(now)
        now += 20_000L
        tracker.onBufferingEnded(now)
        tracker.onHealthyPlayback(now)
        now += 11_000L
        tracker.onHealthyPlayback(now)
        assertFalse(tracker.isBudgetExceeded(nowMs = now, hasRenderedFirstFrame = true))
    }
}
