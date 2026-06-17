package com.grid.tv.player

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.allStreamUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamFailoverControllerTest {

    @Test
    fun buildRecoverySteps_includesReconnectPrimaryAndBackups() {
        val channel = Channel(
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
        val steps = buildRecoveryStepsForTest(channel)
        assertEquals(5, steps.size)
        assertEquals("reconnect", steps[0])
        assertEquals("http://primary", steps[1])
        assertEquals("http://backup1", steps[2])
        assertEquals("http://backup2", steps[3])
        assertEquals("http://backup3", steps[4])
    }

    @Test
    fun failoverMessages_matchUserFacingCopy() {
        assertEquals("Recovering stream...", StreamFailoverController.RECOVERING_MESSAGE)
        assertEquals("Stream restored", StreamFailoverController.RESTORED_MESSAGE)
    }

    private fun buildRecoveryStepsForTest(channel: Channel): List<String> {
        val urls = channel.allStreamUrls()
        val steps = mutableListOf("reconnect")
        steps += urls
        return steps
    }
}
