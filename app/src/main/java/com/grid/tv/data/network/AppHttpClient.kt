package com.grid.tv.data.network

import com.grid.tv.domain.model.AppSettings
import com.grid.tv.util.connectionTimeoutMs
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class AppHttpClient(
    private val startupNetworkGate: StartupNetworkGateInterceptor
) {
    private val clientLock = Any()

    @Volatile
    private var settingsSnapshot: AppSettings = AppSettings()

    @Volatile
    private var client: OkHttpClient? = null

    /** Long-running client for large XMLTV downloads (100MB+). */
    @Volatile
    private var epgClient: OkHttpClient? = null

    /** Long-running client for large Xtream VOD/series catalog JSON. */
    @Volatile
    private var vodClient: OkHttpClient? = null

    /** Tight timeouts for Xtream get_short_epg viewport hydration — must not block the guide. */
    @Volatile
    private var shortEpgClient: OkHttpClient? = null

    /** Bounded-concurrency client for IPTV channel HEAD/range probes. */
    @Volatile
    private var probeClient: OkHttpClient? = null

    /** Long-lived reads for live/VOD ExoPlayer streams — shares proxy, DNS, and connect timeout with AppHttpClient. */
    @Volatile
    private var playbackClient: OkHttpClient? = null

    fun client(): OkHttpClient = client ?: synchronized(clientLock) {
        client ?: buildClient(settingsSnapshot).also { client = it }
    }

    fun probeClient(): OkHttpClient = probeClient ?: synchronized(clientLock) {
        probeClient ?: buildProbeClient(settingsSnapshot).also { probeClient = it }
    }

    fun epgClient(): OkHttpClient = epgClient ?: synchronized(clientLock) {
        epgClient ?: buildEpgClient(settingsSnapshot).also { epgClient = it }
    }

    /** ExoPlayer stream segments — honors proxy, [IptvDns], and connection timeout from settings. */
    fun playbackClient(): OkHttpClient = playbackClient ?: synchronized(clientLock) {
        playbackClient ?: buildPlaybackClient(settingsSnapshot).also { playbackClient = it }
    }

    /** Large Xtream VOD/series catalog JSON can be tens of MB — use extended timeouts. */
    fun vodClient(): OkHttpClient = vodClient ?: synchronized(clientLock) {
        vodClient ?: buildVodClient(settingsSnapshot).also { vodClient = it }
    }

    /** Viewport get_short_epg — fail fast so Cloudflare 522s do not stall the guide. */
    fun shortEpgClient(): OkHttpClient = shortEpgClient ?: synchronized(clientLock) {
        shortEpgClient ?: buildShortEpgClient(settingsSnapshot).also { shortEpgClient = it }
    }

    /** Cancel in-flight playback HTTP so a new tune does not overlap with the previous stream. */
    fun cancelInFlightPlaybackRequests(): Int {
        val playback = playbackClient ?: return 0
        val inFlight = playback.dispatcher.runningCallsCount()
        playback.dispatcher.cancelAll()
        return inFlight
    }

    /** Cancel in-flight channel HEAD/range probes during playback tune. */
    fun cancelInFlightProbeRequests(): Int {
        val probe = probeClient ?: return 0
        val inFlight = probe.dispatcher.runningCallsCount()
        probe.dispatcher.cancelAll()
        return inFlight
    }

    fun applySettings(settings: AppSettings) {
        synchronized(clientLock) {
            settingsSnapshot = settings
            client = buildClient(settings)
            probeClient = buildProbeClient(settings)
            epgClient = buildEpgClient(settings)
            vodClient = buildVodClient(settings)
            shortEpgClient = buildShortEpgClient(settings)
            playbackClient = buildPlaybackClient(settings)
        }
    }

    private fun buildProbeClient(settings: AppSettings): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = PROBE_MAX_REQUESTS
            maxRequestsPerHost = PROBE_MAX_REQUESTS_PER_HOST
        }
        return baseBuilder(settings, connectTimeoutSeconds = 5)
            .dispatcher(dispatcher)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun buildClient(settings: AppSettings): OkHttpClient {
        val timeoutSeconds = settings.connectionTimeoutSeconds
        val timeoutMs = connectionTimeoutMs(timeoutSeconds)
        return baseBuilder(settings, timeoutSeconds)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs * 2, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun buildEpgClient(settings: AppSettings): OkHttpClient =
        baseBuilder(settings)
            .connectTimeout(EPG_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(EPG_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(EPG_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(EPG_CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()

    private fun buildVodClient(settings: AppSettings): OkHttpClient =
        baseBuilder(settings)
            .readTimeout(VOD_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(VOD_CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()

    private fun buildShortEpgClient(settings: AppSettings): OkHttpClient =
        baseBuilder(settings, connectTimeoutSeconds = 5)
            .readTimeout(SHORT_EPG_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(SHORT_EPG_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(SHORT_EPG_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    /**
     * Live/VOD playback: shorter connect timeout for faster failure/recovery on dead origins;
     * infinite read timeout for open segment streams.
     */
    private fun buildPlaybackClient(settings: AppSettings): OkHttpClient =
        baseBuilder(settings, connectTimeoutSeconds = PLAYBACK_CONNECT_TIMEOUT_SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(PLAYBACK_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            // Media3 load-error policy owns segment retries; avoid stacking OkHttp connection retries.
            .retryOnConnectionFailure(false)
            .connectionPool(PLAYBACK_CONNECTION_POOL)
            .build()

    private fun baseBuilder(
        settings: AppSettings,
        connectTimeoutSeconds: Int = settings.connectionTimeoutSeconds
    ): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .dns(IptvDns)
            .addInterceptor(startupNetworkGate)
            .addInterceptor(
                HttpLoggingInterceptor(createSafeHttpLogger())
                    .apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
            .connectTimeout(connectionTimeoutMs(connectTimeoutSeconds), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)

        if (settings.useProxy && settings.proxyUrl.isNotBlank()) {
            parseProxy(settings.proxyUrl.trim())?.let { builder.proxy(it) }
        }

        return builder
    }

    private companion object {
        const val EPG_CONNECT_TIMEOUT_SECONDS = 30L
        const val EPG_CALL_TIMEOUT_MINUTES = 8L
        const val EPG_READ_TIMEOUT_MINUTES = 5L
        const val EPG_WRITE_TIMEOUT_SECONDS = 60L
        const val VOD_CALL_TIMEOUT_MINUTES = 10L
        const val VOD_READ_TIMEOUT_MINUTES = 10L
        const val SHORT_EPG_CALL_TIMEOUT_SECONDS = 10L
        const val SHORT_EPG_READ_TIMEOUT_SECONDS = 8L
        const val SHORT_EPG_WRITE_TIMEOUT_SECONDS = 8L
        const val PROBE_MAX_REQUESTS = 1
        const val PROBE_MAX_REQUESTS_PER_HOST = 1
        const val PLAYBACK_WRITE_TIMEOUT_SECONDS = 30L
        /** Fail dead IPTV origins quickly so ExoPlayer/load-error policy can retry or failover. */
        const val PLAYBACK_CONNECT_TIMEOUT_SECONDS = 30
        val PLAYBACK_CONNECTION_POOL = ConnectionPool(
            maxIdleConnections = 8,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        )
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
