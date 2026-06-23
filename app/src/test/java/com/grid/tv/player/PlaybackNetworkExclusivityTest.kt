package com.grid.tv.player

import com.grid.tv.feature.scanner.ChannelScanner
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackNetworkExclusivityTest {

    private lateinit var channelScanner: ChannelScanner
    private lateinit var exclusivity: PlaybackNetworkExclusivity

    @Before
    fun setUp() {
        channelScanner = mockk(relaxed = true)
        val isolation = PlaybackScannerIsolation(channelScanner).apply {
            executorOverride = PlaybackScannerIsolation.QueuedTestExecutor()
        }
        exclusivity = PlaybackNetworkExclusivity(isolation)
    }

    @Test
    fun registerStream_marksUrlActiveBeforeScannerSuspend() {
        exclusivity.registerStream("http://live.example/stream.m3u8")

        assertTrue(exclusivity.shouldSkipPreflightProbe("http://live.example/stream.m3u8"))
        verify(exactly = 0) { channelScanner.setPlaybackScanSuspended(true) }
    }
}
