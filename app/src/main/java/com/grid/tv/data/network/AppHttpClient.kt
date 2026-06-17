package com.grid.tv.data.network

import com.grid.tv.domain.model.AppSettings
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Singleton
class AppHttpClient @Inject constructor() {
    @Volatile
    private var client: OkHttpClient = buildClient(AppSettings())

    fun client(): OkHttpClient = client

    fun applySettings(settings: AppSettings) {
        client = buildClient(settings)
    }

    private fun buildClient(settings: AppSettings): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .dns(IptvDns)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(settings.connectionTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (settings.useProxy && settings.proxyUrl.isNotBlank()) {
            parseProxy(settings.proxyUrl.trim())?.let { builder.proxy(it) }
        }

        return builder.build()
    }

    private fun parseProxy(raw: String): Proxy? = runCatching {
        val normalized = when {
            raw.startsWith("http://", ignoreCase = true) -> raw
            raw.startsWith("https://", ignoreCase = true) -> raw
            else -> "http://$raw"
        }
        val url = URL(normalized)
        val host = url.host ?: return@runCatching null
        val port = when {
            url.port != -1 -> url.port
            url.protocol.equals("https", ignoreCase = true) -> 443
            else -> 8080
        }
        Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
    }.getOrNull()
}
