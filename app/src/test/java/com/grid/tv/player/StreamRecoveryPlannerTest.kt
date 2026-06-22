package com.grid.tv.player

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.allStreamUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamRecoveryPlannerTest {

    private val channel = Channel(
        id = 1,
        number = 1,
        name = "ESPN",
        group = "Sports",
        logoUrl = null,
        epgId = "espn",
        streamUrl = "http://primary",
        backupStreamUrl = "http://backup1",
        backupStreamUrl2 = "http://backup2",
        backupStreamUrl3 = "http://backup3",
        playlistId = 1,
        isFavorite = false
    )

    @Test
    fun buildSteps_includesReconnectAndBackups_whenMaxAllowsAll() {
        val steps = StreamRecoveryPlanner.buildSteps(
            streamUrls = channel.allStreamUrls(),
            activeUrl = "http://primary",
            maxAttempts = 5
        )
        assertEquals(4, steps.size)
        assertEquals(StreamRecoveryPlanner.Step.Reconnect, steps[0])
        assertEquals(StreamRecoveryPlanner.Step.SwitchUrl("http://backup1"), steps[1])
        assertEquals(StreamRecoveryPlanner.Step.SwitchUrl("http://backup2"), steps[2])
        assertEquals(StreamRecoveryPlanner.Step.SwitchUrl("http://backup3"), steps[3])
    }

    @Test
    fun buildSteps_respectsStreamRetriesLimit() {
        val steps = StreamRecoveryPlanner.buildSteps(
            streamUrls = channel.allStreamUrls(),
            activeUrl = "http://primary",
            maxAttempts = 2
        )
        assertEquals(2, steps.size)
        assertEquals(StreamRecoveryPlanner.Step.Reconnect, steps[0])
        assertEquals(StreamRecoveryPlanner.Step.SwitchUrl("http://backup1"), steps[1])
    }

    @Test
    fun buildSteps_returnsEmptyWhenRetriesZero() {
        val steps = StreamRecoveryPlanner.buildSteps(
            streamUrls = channel.allStreamUrls(),
            activeUrl = "http://primary",
            maxAttempts = 0
        )
        assertEquals(0, steps.size)
    }
}
