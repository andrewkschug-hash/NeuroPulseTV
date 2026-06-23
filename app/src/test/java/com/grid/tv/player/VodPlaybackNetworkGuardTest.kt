package com.grid.tv.player

import com.grid.tv.feature.scanner.ChannelScanner
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VodPlaybackNetworkGuardTest {

    private lateinit var channelScanner: ChannelScanner
    private lateinit var liveExclusivity: PlaybackNetworkExclusivity
    private lateinit var guard: VodPlaybackNetworkGuard

    @Before
    fun setUp() {
        channelScanner = mockk(relaxed = true)
        liveExclusivity = mockk(relaxed = true)
        every { liveExclusivity.hasActivePlayback } returns false
        every { channelScanner.isPlaybackScanSuspended } returns false
        guard = VodPlaybackNetworkGuard(channelScanner, liveExclusivity)
    }

    @Test
    fun beginSession_suspendsScannerAndBlocksPreflight() {
        guard.beginSession("http://vod.example/movie.mkv")

        verify { channelScanner.setPlaybackScanSuspended(true) }
        assertTrue(guard.shouldSkipPreflightProbe("http://vod.example/movie.mkv"))
        assertFalse(guard.shouldSkipPreflightProbe("http://other.example/stream.mkv"))
    }

    @Test
    fun endSession_resumesScannerWhenLiveInactive() {
        guard.beginSession("http://vod.example/movie.mkv")
        guard.endSession()

        verify { channelScanner.setPlaybackScanSuspended(false) }
        assertFalse(guard.hasActiveVodSession())
    }

    @Test
    fun endSession_doesNotResumeScannerWhenLiveActive() {
        guard.beginSession("http://vod.example/movie.mkv")
        every { liveExclusivity.hasActivePlayback } returns true
        guard.endSession()

        verify(exactly = 0) { channelScanner.setPlaybackScanSuspended(false) }
    }
}
