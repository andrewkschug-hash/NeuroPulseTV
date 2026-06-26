package com.grid.tv.player

import com.grid.tv.feature.scanner.ChannelScanner
import io.mockk.mockk
import io.mockk.verify
import javax.inject.Provider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PlaybackScannerIsolationTest {

    private lateinit var channelScanner: ChannelScanner
    private lateinit var isolation: PlaybackScannerIsolation

    @Before
    fun setUp() {
        channelScanner = mockk(relaxed = true)
        isolation = PlaybackScannerIsolation(Provider { channelScanner }).apply {
            executorOverride = PlaybackScannerIsolation.QueuedTestExecutor()
        }
    }

    @Test
    fun acquireAsync_defersScannerSuspendUntilBackground() {
        isolation.acquireAsync()
        verify(exactly = 0) { channelScanner.setPlaybackScanSuspended(true) }

        isolation.awaitIdleForTests()
        verify(exactly = 1) { channelScanner.setPlaybackScanSuspended(true) }
        assertEquals(1, isolation.acquireCountForTests())
    }

    @Test
    fun nestedAcquireRelease_usesRefCount() {
        isolation.acquireAsync()
        isolation.acquireAsync()
        isolation.awaitIdleForTests()
        verify(exactly = 1) { channelScanner.setPlaybackScanSuspended(true) }

        isolation.releaseAsync()
        isolation.awaitIdleForTests()
        verify(exactly = 0) { channelScanner.setPlaybackScanSuspended(false) }

        isolation.releaseAsync()
        isolation.awaitIdleForTests()
        verify(exactly = 1) { channelScanner.setPlaybackScanSuspended(false) }
    }
}
