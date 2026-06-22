package com.grid.tv.feature.scanner

import com.grid.tv.player.IptvStreamFormat
import com.grid.tv.player.IptvStreamFormatDetector
import com.grid.tv.player.IptvStreamFormatRegistry
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

enum class ProbeResult {
    LIVE,
    DEAD,
    UNKNOWN
}

enum class ProbeFailureKind {
    DNS,
    HTTP_520,
    HTTP_503,
    TIMEOUT,
    BLACKLISTED,
    OTHER
}

/** Minimal probe outcome — no response bodies retained. */
data class ProbeDetail(
    val result: ProbeResult,
    val responseCode: Int? = null,
    val latencyMs: Long = 0L,
    val failureKind: ProbeFailureKind? = null
) {
    val isRetryable: Boolean
        get() = result == ProbeResult.UNKNOWN && failureKind in RETRYABLE_FAILURES

    companion object {
        private val RETRYABLE_FAILURES = setOf(
            ProbeFailureKind.DNS,
            ProbeFailureKind.HTTP_520,
            ProbeFailureKind.HTTP_503,
            ProbeFailureKind.TIMEOUT,
            ProbeFailureKind.OTHER
        )
    }
}

class ChannelProbe(
    baseClient: OkHttpClient,
    private val hostTracker: HostFailureTracker,
    private val metrics: ScanMetricsLogger,
    private val streamFormatRegistry: IptvStreamFormatRegistry? = null
) {
    private val client = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun probe(url: String): ProbeDetail {
        if (url.isBlank()) {
            return ProbeDetail(ProbeResult.DEAD, responseCode = null, latencyMs = 0L)
        }

        val host = url.toHttpUrlOrNull()?.host
        if (host != null && hostTracker.isBlacklisted(host)) {
            return ProbeDetail(
                result = ProbeResult.UNKNOWN,
                failureKind = ProbeFailureKind.BLACKLISTED
            )
        }

        return executeWithRetry(url, host) {
            val headResult = runCatching { headCheck(url, host) }.getOrElse { mapException(it, url, host) }
            if (headResult.result == ProbeResult.LIVE) return@executeWithRetry headResult
            if (headResult.isRetryable) return@executeWithRetry headResult
            if (headResult.result == ProbeResult.UNKNOWN && headResult.failureKind == ProbeFailureKind.BLACKLISTED) {
                return@executeWithRetry headResult
            }

            if (shouldRunHlsManifestCheck(url)) {
                return@executeWithRetry runCatching { hlsManifestCheck(url, host) }
                    .getOrElse { mapException(it, url, host) }
            }

            runCatching { rangeGetCheck(url, host) }
                .getOrElse { mapException(it, url, host) }
        }
    }

    private fun shouldRunHlsManifestCheck(url: String): Boolean {
        streamFormatRegistry?.get(url)?.let { return it == IptvStreamFormat.HLS }
        return IptvStreamFormatDetector.resolveForPlayback(url, registry = streamFormatRegistry) == IptvStreamFormat.HLS
    }

    private inline fun executeWithRetry(
        url: String,
        host: String?,
        block: () -> ProbeDetail
    ): ProbeDetail {
        var last = ProbeDetail(ProbeResult.UNKNOWN)
        repeat(MAX_ATTEMPTS) { attempt ->
            last = block()
            if (!last.isRetryable) return last
            if (attempt < MAX_ATTEMPTS - 1) {
                Thread.sleep(RETRY_BACKOFF_MS[attempt])
            }
        }
        // Transient failures stay UNKNOWN — never permanently mark DEAD.
        return last.copy(result = ProbeResult.UNKNOWN)
    }

    private fun headCheck(url: String, host: String?): ProbeDetail {
        val started = System.currentTimeMillis()
        metrics.onRequestStarted()
        return try {
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - started
                val code = response.code
                response.header("Content-Type")?.let { contentType ->
                    streamFormatRegistry?.putContentType(url, contentType)
                }
                when {
                    response.isSuccessful -> ProbeDetail(ProbeResult.LIVE, code, latency)
                    code in 400..499 -> ProbeDetail(ProbeResult.DEAD, code, latency)
                    code == 503 -> {
                        metrics.logHttp503(url)
                        ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.HTTP_503)
                    }
                    code == 520 -> {
                        metrics.logHttp520(url)
                        ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.HTTP_520)
                    }
                    code >= 500 -> ProbeDetail(
                        ProbeResult.UNKNOWN,
                        code,
                        latency,
                        ProbeFailureKind.OTHER
                    )
                    else -> ProbeDetail(ProbeResult.UNKNOWN, code, latency)
                }
            }
        } finally {
            metrics.onRequestFinished()
        }
    }

    private fun hlsManifestCheck(url: String, host: String?): ProbeDetail {
        val started = System.currentTimeMillis()
        metrics.onRequestStarted()
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - started
                val code = response.code
                when {
                    !response.isSuccessful && code == 503 -> {
                        metrics.logHttp503(url)
                        ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.HTTP_503)
                    }
                    !response.isSuccessful && code == 520 -> {
                        metrics.logHttp520(url)
                        ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.HTTP_520)
                    }
                    !response.isSuccessful && code >= 500 -> {
                        ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.OTHER)
                    }
                    !response.isSuccessful -> ProbeDetail(ProbeResult.DEAD, code, latency)
                    else -> {
                        val snippet = response.body?.byteStream()?.use { stream ->
                            val buffer = ByteArray(HLS_PEEK_BYTES)
                            val read = stream.read(buffer).coerceAtLeast(0)
                            String(buffer, 0, read)
                        }.orEmpty()
                        if (snippet.contains("#EXTINF", ignoreCase = true)) {
                            streamFormatRegistry?.putManifestSnippet(url, snippet)
                            ProbeDetail(ProbeResult.LIVE, code, latency)
                        } else {
                            ProbeDetail(ProbeResult.DEAD, code, latency)
                        }
                    }
                }
            }
        } finally {
            metrics.onRequestFinished()
        }
    }

    private fun rangeGetCheck(url: String, host: String?): ProbeDetail {
        val started = System.currentTimeMillis()
        metrics.onRequestStarted()
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Range", "bytes=0-1")
                .build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - started
                val code = response.code
                when {
                    response.isSuccessful || code == 206 -> ProbeDetail(ProbeResult.LIVE, code, latency)
                    code in 400..499 -> ProbeDetail(ProbeResult.DEAD, code, latency)
                    code == 503 -> {
                        metrics.logHttp503(url)
                        ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.HTTP_503)
                    }
                    code == 520 -> {
                        metrics.logHttp520(url)
                        ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.HTTP_520)
                    }
                    code >= 500 -> ProbeDetail(
                        ProbeResult.UNKNOWN,
                        code,
                        latency,
                        ProbeFailureKind.OTHER
                    )
                    else -> ProbeDetail(ProbeResult.UNKNOWN, code, latency)
                }
            }
        } finally {
            metrics.onRequestFinished()
        }
    }

    private fun mapException(error: Throwable, url: String, host: String?): ProbeDetail {
        return when (error) {
            is UnknownHostException -> {
                host?.let {
                    hostTracker.recordDnsFailure(it)
                    metrics.logDnsFailure(it)
                    if (hostTracker.isBlacklisted(it)) {
                        metrics.logHostBlacklisted(it, HostFailureTracker.DEFAULT_BLACKLIST_THRESHOLD + 1)
                    }
                }
                ProbeDetail(ProbeResult.UNKNOWN, failureKind = ProbeFailureKind.DNS)
            }
            is SocketTimeoutException -> {
                ProbeDetail(ProbeResult.UNKNOWN, failureKind = ProbeFailureKind.TIMEOUT)
            }
            else -> ProbeDetail(ProbeResult.UNKNOWN, failureKind = ProbeFailureKind.OTHER)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        val RETRY_BACKOFF_MS = longArrayOf(500L, 1_000L, 2_000L)
        const val HLS_PEEK_BYTES = 4_096
    }
}
