package com.grid.tv.player

import com.grid.tv.data.network.AppHttpClient
import com.grid.tv.domain.model.AppSettings
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHttpDataSourceFactoryTest {

    @Test
    fun mediaSourceFactory_isSingletonAcrossCalls() {
        val factory = PlaybackHttpDataSourceFactory(AppHttpClient())
        val first = factory.mediaSourceFactory()
        val second = factory.mediaSourceFactory()
        assertSame(first, second)
    }

    @Test
    fun dataSourceFactory_isSingletonAcrossCalls() {
        val factory = PlaybackHttpDataSourceFactory(AppHttpClient())
        val first = factory.dataSourceFactory()
        val second = factory.dataSourceFactory()
        assertSame(first, second)
    }

    @Test
    fun create_aliasReturnsSameDataSourceFactory() {
        val factory = PlaybackHttpDataSourceFactory(AppHttpClient())
        assertSame(factory.create(), factory.dataSourceFactory())
    }

    @Test
    fun syncNetworkSettings_rebuildsStackWhenProxyChanges() {
        val httpClient = AppHttpClient()
        val factory = PlaybackHttpDataSourceFactory(httpClient)
        val before = factory.mediaSourceFactory()
        factory.syncNetworkSettings(AppSettings(useProxy = true, proxyUrl = "http://127.0.0.1:8888"))
        val after = factory.mediaSourceFactory()
        assertNotSame(before, after)
        assertTrue(factory.stackGeneration() >= 1)
    }

    @Test
    fun syncNetworkSettings_noOpWhenConfigUnchanged() {
        val factory = PlaybackHttpDataSourceFactory(AppHttpClient())
        factory.mediaSourceFactory()
        val generation = factory.stackGeneration()
        factory.syncNetworkSettings(AppSettings())
        assertSame(generation, factory.stackGeneration())
    }

    @Test
    fun multiplePlayerFactoryCreates_shareMediaSourceFactory() {
        val httpFactory = PlaybackHttpDataSourceFactory(AppHttpClient())
        val playerFactory = PlayerFactory(httpFactory)
        // PlayerFactory needs Context — verify stack sharing at factory layer instead.
        val mediaA = httpFactory.mediaSourceFactory()
        val mediaB = httpFactory.mediaSourceFactory(AppSettings())
        assertSame(mediaA, mediaB)
    }
}
