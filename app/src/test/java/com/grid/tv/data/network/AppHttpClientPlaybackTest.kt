package com.grid.tv.data.network

import com.grid.tv.domain.model.AppSettings
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Test

class AppHttpClientPlaybackTest {

    @Test
    fun applySettings_rebuildsPlaybackClient() {
        val client = testAppHttpClient()
        val direct = client.playbackClient()
        client.applySettings(AppSettings(useProxy = true, proxyUrl = "http://127.0.0.1:8888"))
        val proxied = client.playbackClient()
        assertNotSame(direct, proxied)
        assertNotNull(proxied.proxy)
    }

    @Test
    fun networkPlaybackConfig_reflectsSettings() {
        val config = AppSettings(
            useProxy = true,
            proxyUrl = "http://proxy.local:8080",
            connectionTimeoutSeconds = 120
        ).toNetworkPlaybackConfig()
        assert(config.useProxy)
        assert(config.proxyUrl.contains("proxy.local"))
        assert(config.connectionTimeoutSeconds == 120)
        assert(config.toLogLine().contains("proxy=on"))
    }
}
