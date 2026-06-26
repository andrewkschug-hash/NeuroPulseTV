package com.grid.tv.feature.scanner

import com.grid.tv.player.IptvStreamFormat
import com.grid.tv.data.network.AppHttpClient
import com.grid.tv.player.IptvStreamFormatRegistry
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response

enum class ProbeResult {
  LIVE,
  DEAD,
  UNKNOWN
}

enum class ProbeFailureKind {
  DNS,
  HTTP_403,
  HTTP_429,
  HTTP_520,
  HTTP_503,
  TIMEOUT,
  BLACKLISTED,
  MANIFEST_REJECTED,
  OTHER
}

/** Minimal probe outcome — no response bodies retained. */
data class ProbeDetail(
  val result: ProbeResult,
  val responseCode: Int? = null,
  val latencyMs: Long = 0L,
  val failureKind: ProbeFailureKind? = null,
  val failureReason: String? = null
) {
  val isRetryable: Boolean
    get() = result == ProbeResult.UNKNOWN && failureKind in RETRYABLE_FAILURES

  companion object {
    private val RETRYABLE_FAILURES = setOf(
      ProbeFailureKind.DNS,
      ProbeFailureKind.HTTP_429,
      ProbeFailureKind.HTTP_520,
      ProbeFailureKind.HTTP_503,
      ProbeFailureKind.TIMEOUT,
      ProbeFailureKind.OTHER
    )
  }
}

@Singleton
class ChannelProbe @Inject constructor(
  appHttpClient: AppHttpClient,
  private val hostTracker: HostFailureTracker,
  private val metrics: ScanMetricsLogger,
  private val streamFormatRegistry: IptvStreamFormatRegistry
) {
  private val client = appHttpClient.probeClient().newBuilder()
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
        failureKind = ProbeFailureKind.BLACKLISTED,
        failureReason = "host_blacklisted"
      )
    }

    return executeWithRetry(url, host) {
      if (shouldRunHlsManifestCheck(url)) {
        runCatching { hlsManifestCheck(url, host) }
          .getOrElse { mapException(it, url, host) }
      } else {
        runCatching { rangeGetCheck(url, host) }
          .getOrElse { mapException(it, url, host) }
      }
    }
  }

  private fun shouldRunHlsManifestCheck(url: String): Boolean {
    streamFormatRegistry.get(url)?.let { return it == IptvStreamFormat.HLS }
    return url.contains(".m3u8", ignoreCase = true)
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
    return last.copy(result = ProbeResult.UNKNOWN)
  }

  private fun hlsManifestCheck(url: String, host: String?): ProbeDetail {
    val started = System.currentTimeMillis()
    metrics.onRequestStarted()
    return try {
      val request = ChannelProbeRequests.buildGet(url)
      client.newCall(request).execute().use { response ->
        val latency = System.currentTimeMillis() - started
        evaluateHttpStatus(url, response.code, latency)?.let { return it }

        val snippet = readSnippet(response, MANIFEST_PEEK_BYTES)
        response.header("Content-Type")?.let { contentType ->
          streamFormatRegistry.putContentType(url, contentType)
        }
        when {
          isHlsManifestSnippet(snippet) -> {
            streamFormatRegistry.putManifestSnippet(url, snippet)
            ProbeDetail(ProbeResult.LIVE, response.code, latency)
          }
          else -> {
            metrics.logManifestRejected(url, response.code, "m3u8_without_manifest")
            ProbeDetail(
              result = ProbeResult.DEAD,
              responseCode = response.code,
              latencyMs = latency,
              failureKind = ProbeFailureKind.MANIFEST_REJECTED,
              failureReason = "m3u8_without_manifest"
            )
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
      val request = ChannelProbeRequests.buildGet(url)
      client.newCall(request).execute().use { response ->
        val latency = System.currentTimeMillis() - started
        evaluateHttpStatus(url, response.code, latency)?.let { return it }
        response.header("Content-Type")?.let { contentType ->
          streamFormatRegistry.putContentType(url, contentType)
        }
        ProbeDetail(ProbeResult.LIVE, response.code, latency)
      }
    } finally {
      metrics.onRequestFinished()
    }
  }

  /**
   * Validates HTTP status before any manifest/body parsing.
   * Returns a terminal [ProbeDetail] for error codes, or null when the body may be read.
   */
  private fun evaluateHttpStatus(url: String, code: Int, latency: Long): ProbeDetail? = when (code) {
    200, 206 -> null
    403 -> {
      metrics.logHttp403(url, code)
      ProbeDetail(ProbeResult.DEAD, code, latency, ProbeFailureKind.HTTP_403, "http_403")
    }
    429 -> {
      metrics.logHttp429(url, code)
      ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.HTTP_429, "http_429_rate_limited")
    }
    in 400..499 -> {
      metrics.logProbeHttpFailure(url, code, "client_error")
      ProbeDetail(ProbeResult.DEAD, code, latency, failureReason = "http_$code")
    }
    503 -> {
      metrics.logHttp503(url)
      ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.HTTP_503, "http_503")
    }
    520 -> {
      metrics.logHttp520(url)
      ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.HTTP_520, "http_520")
    }
    in 500..599 -> {
      metrics.logProbeHttpFailure(url, code, "server_error")
      ProbeDetail(ProbeResult.UNKNOWN, code, latency, ProbeFailureKind.OTHER, "http_$code")
    }
    else -> {
      metrics.logProbeHttpFailure(url, code, "unexpected_status")
      ProbeDetail(ProbeResult.UNKNOWN, code, latency, failureReason = "http_$code")
    }
  }

  private fun readSnippet(response: Response, maxBytes: Int): String {
    if (!response.isSuccessful && response.code != 206) return ""
    return response.body?.byteStream()?.use { stream ->
      val buffer = ByteArray(maxBytes)
      val read = stream.read(buffer).coerceAtLeast(0)
      String(buffer, 0, read)
    }.orEmpty()
  }

  private fun isHlsManifestSnippet(snippet: String): Boolean {
    if (snippet.isBlank()) return false
    return snippet.contains("#EXTM3U", ignoreCase = true) ||
      snippet.contains("#EXTINF", ignoreCase = true)
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
        ProbeDetail(ProbeResult.UNKNOWN, failureKind = ProbeFailureKind.DNS, failureReason = "dns_failure")
      }
      is SocketTimeoutException -> {
        ProbeDetail(ProbeResult.UNKNOWN, failureKind = ProbeFailureKind.TIMEOUT, failureReason = "timeout")
      }
      else -> ProbeDetail(ProbeResult.UNKNOWN, failureKind = ProbeFailureKind.OTHER, failureReason = error.message)
    }
  }

  private companion object {
    const val MAX_ATTEMPTS = 3
    val RETRY_BACKOFF_MS = longArrayOf(500L, 1_000L, 2_000L)
    const val MANIFEST_PEEK_BYTES = 1_024
  }
}
