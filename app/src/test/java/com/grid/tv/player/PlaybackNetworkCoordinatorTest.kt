package com.grid.tv.player

import com.grid.tv.data.network.AppHttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

class PlaybackNetworkCoordinatorTest {

    private lateinit var appHttpClient: AppHttpClient
    private lateinit var scannerIsolation: PlaybackScannerIsolation
    private lateinit var channelScanner: com.grid.tv.feature.scanner.ChannelScanner
    private lateinit var liveExclusivity: PlaybackNetworkExclusivity
    private lateinit var vodGuard: VodPlaybackNetworkGuard
    private lateinit var coordinator: PlaybackNetworkCoordinator

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        appHttpClient = mockk(relaxed = true)
        every { appHttpClient.cancelInFlightPlaybackRequests() } returns 0
        every { appHttpClient.cancelInFlightProbeRequests() } returns 0
        channelScanner = mockk(relaxed = true)
        scannerIsolation = PlaybackScannerIsolation(Provider { channelScanner }).apply {
            executorOverride = PlaybackScannerIsolation.QueuedTestExecutor()
        }
        liveExclusivity = PlaybackNetworkExclusivity(scannerIsolation)
        vodGuard = VodPlaybackNetworkGuard(liveExclusivity, scannerIsolation)
        coordinator = PlaybackNetworkCoordinator(
            appHttpClient,
            liveExclusivity,
            vodGuard,
            IptvStreamFormatRegistry()
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun beginLiveTune_cancelsPlaybackAndProbeAndRegistersUrl() {
        coordinator.beginLiveTune(
            streamUrl = "http://live.example/stream.m3u8",
            tuneGeneration = 7
        )

        verify { appHttpClient.cancelInFlightPlaybackRequests() }
        verify { appHttpClient.cancelInFlightProbeRequests() }
        assertTrue(coordinator.isProbeBlocked("http://live.example/stream.m3u8"))
        assertTrue(coordinator.isTuneLockActive())
    }

    @Test
    fun isFailoverBlocked_whileTuneLockActive() {
        coordinator.beginLiveTune("http://live.example/stream.m3u8", tuneGeneration = 1)
        assertTrue(coordinator.isFailoverBlocked())
    }

    @Test
    fun beginVodSession_blocksScannerAndProbe() {
        coordinator.beginVodSession("http://vod.example/movie.mkv")
        assertTrue(coordinator.shouldBlockScanner())
        assertTrue(coordinator.isProbeBlocked("http://vod.example/movie.mkv"))
    }

    @Test
    fun endVodSession_clearsVodProbeBlock() {
        coordinator.beginVodSession("http://vod.example/movie.mkv")
        coordinator.endVodSession()
        assertFalse(vodGuard.hasActiveVodSession())
    }
}
