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

    /** Long-running client for large XMLTV downloads (100MB+). */
    @Volatile
    private var epgClient: OkHttpClient = buildEpgClient(AppSettings())

    fun client(): OkHttpClient = client

    fun epgClient(): OkHttpClient = epgClient

    fun applySettings(settings: AppSettings) {
        client = buildClient(settings)
        epgClient = buildEpgClient(settings)
    }

    private fun buildClient(settings: AppSettings): OkHttpClient =
        baseBuilder(settings)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()

    private fun buildEpgClient(settings: AppSettings): OkHttpClient =
        baseBuilder(settings)
            .readTimeout(EPG_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(EPG_CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()

    private fun baseBuilder(settings: AppSettings): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .dns(IptvDns)
            .addInterceptor(
                HttpLoggingInterceptor(createSafeHttpLogger())
                    .apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
            .connectTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (settings.useProxy && settings.proxyUrl.isNotBlank()) {
            parseProxy(settings.proxyUrl.trim())?.let { builder.proxy(it) }
        }

        return builder
    }

    private companion object {
        const val EPG_CALL_TIMEOUT_MINUTES = 5L
        const val EPG_READ_TIMEOUT_MINUTES = 5L
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
