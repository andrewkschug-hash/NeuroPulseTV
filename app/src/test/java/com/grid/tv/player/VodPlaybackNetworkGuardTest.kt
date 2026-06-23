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
    private lateinit var scannerIsolation: PlaybackScannerIsolation
    private lateinit var liveExclusivity: PlaybackNetworkExclusivity
    private lateinit var guard: VodPlaybackNetworkGuard

    @Before
    fun setUp() {
        channelScanner = mockk(relaxed = true)
        every { channelScanner.isPlaybackScanSuspended } returns false
        scannerIsolation = PlaybackScannerIsolation(channelScanner).apply {
            executorOverride = PlaybackScannerIsolation.QueuedTestExecutor()
        }
        liveExclusivity = PlaybackNetworkExclusivity(scannerIsolation)
        guard = VodPlaybackNetworkGuard(liveExclusivity, scannerIsolation)
    }

    @Test
    fun beginSession_registersPreflightImmediatelyAndSuspendsScannerAsync() {
        guard.beginSession("http://vod.example/movie.mkv")

        assertTrue(guard.shouldSkipPreflightProbe("http://vod.example/movie.mkv"))
        assertFalse(guard.shouldSkipPreflightProbe("http://other.example/stream.mkv"))
        verify(exactly = 0) { channelScanner.setPlaybackScanSuspended(true) }

        scannerIsolation.awaitIdleForTests()
        verify { channelScanner.setPlaybackScanSuspended(true) }
    }

    @Test
    fun endSession_resumesScannerWhenLiveInactive() {
        guard.beginSession("http://vod.example/movie.mkv")
        scannerIsolation.awaitIdleForTests()
        guard.endSession()
        scannerIsolation.awaitIdleForTests()

        verify { channelScanner.setPlaybackScanSuspended(false) }
        assertFalse(guard.hasActiveVodSession())
    }

    @Test
    fun endSession_doesNotResumeScannerWhenLiveActive() {
        liveExclusivity.registerStream("http://live.example/stream.m3u8")
        scannerIsolation.awaitIdleForTests()
        guard.beginSession("http://vod.example/movie.mkv")
        scannerIsolation.awaitIdleForTests()
        guard.endSession()
        scannerIsolation.awaitIdleForTests()

        verify(exactly = 0) { channelScanner.setPlaybackScanSuspended(false) }
    }
}
