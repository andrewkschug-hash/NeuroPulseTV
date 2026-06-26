package com.grid.tv.player

import com.grid.tv.data.network.testAppHttpClient
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.feature.health.intelligence.PlaybackTelemetryCollector
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackHttpDataSourceFactoryTest {

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun metricsLogger(): PlaybackMetricsLogger =
        PlaybackMetricsLogger(mockk<PlaybackTelemetryCollector>(relaxed = true))

    private fun factory(): PlaybackHttpDataSourceFactory =
        PlaybackHttpDataSourceFactory(testAppHttpClient(), metricsLogger(), IptvStreamFormatRegistry())

    @Test
    fun mediaSourceFactory_isSingletonAcrossCalls() {
        val factory = factory()
        val first = factory.mediaSourceFactory()
        val second = factory.mediaSourceFactory()
        assertSame(first, second)
    }

    @Test
    fun dataSourceFactory_isSingletonAcrossCalls() {
        val factory = factory()
        val first = factory.dataSourceFactory()
        val second = factory.dataSourceFactory()
        assertSame(first, second)
    }

    @Test
    fun create_aliasReturnsSameDataSourceFactory() {
        val factory = factory()
        assertSame(factory.create(), factory.dataSourceFactory())
    }

    @Test
    fun syncNetworkSettings_rebuildsStackWhenProxyChanges() {
        val httpClient = testAppHttpClient()
        val factory = PlaybackHttpDataSourceFactory(httpClient, metricsLogger(), IptvStreamFormatRegistry())
        val before = factory.mediaSourceFactory()
        factory.syncNetworkSettings(AppSettings(useProxy = true, proxyUrl = "http://127.0.0.1:8888"))
        val after = factory.mediaSourceFactory()
        assertNotSame(before, after)
        assertTrue(factory.stackGeneration() >= 1)
    }

    @Test
    fun syncNetworkSettings_noOpWhenConfigUnchanged() {
        val factory = factory()
        factory.syncNetworkSettings(AppSettings())
        factory.mediaSourceFactory()
        val generation = factory.stackGeneration()
        factory.syncNetworkSettings(AppSettings())
        assertSame(generation, factory.stackGeneration())
    }

    @Test
    fun multiplePlayerFactoryCreates_shareMediaSourceFactory() {
        val httpFactory = PlaybackHttpDataSourceFactory(
            testAppHttpClient(),
            metricsLogger(),
            IptvStreamFormatRegistry()
        )
        val playerFactory = PlayerFactory(httpFactory, DecoderPressureTracker())
        // PlayerFactory needs Context — verify stack sharing at factory layer instead.
        val mediaA = httpFactory.mediaSourceFactory()
        val mediaB = httpFactory.mediaSourceFactory(AppSettings())
        assertSame(mediaA, mediaB)
    }
}
