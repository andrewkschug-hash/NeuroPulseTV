package com.grid.tv.player

import com.grid.tv.data.network.AppHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Single lightweight GET before ExoPlayer prepare — reads the first ~2 KB to classify the stream.
 *
 * Live: UNKNOWN is fatal. VOD sniff returns raw detection; final override is applied in [VodStreamResolver].
 */
class StreamTypePreflightSniffer(
    private val appHttpClient: AppHttpClient
) {
    private val client by lazy {
        appHttpClient.playbackClient().newBuilder()
            .connectTimeout(PREFLIGHT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PREFLIGHT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(PREFLIGHT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Live IPTV preflight — UNKNOWN blocks playback. */
    suspend fun detect(url: String): StreamTypeDetector.Detection = withContext(Dispatchers.IO) {
        detectInternal(url, onDemandPlayback = false)
    }

    /** VOD preflight sniff — does not apply UNKNOWN→progressive override. */
    suspend fun detectForVod(url: String): StreamTypeDetector.Detection = withContext(Dispatchers.IO) {
        detectInternal(url, onDemandPlayback = true)
    }

    private fun detectInternal(
        url: String,
        onDemandPlayback: Boolean
    ): StreamTypeDetector.Detection {
        if (url.isBlank()) {
            return StreamTypeDetector.Detection(
                format = IptvStreamFormat.UNKNOWN,
                reason = "blank_url"
            )
        }

        if (StreamTypeDetector.isTsUrl(url)) {
            return StreamTypeDetector.Detection(
                format = IptvStreamFormat.PROGRESSIVE,
                reason = "ts_url"
            ).also(StreamTypeDetector::logDetection)
        }

        if (onDemandPlayback && StreamTypeDetector.isVodProgressiveUrl(url)) {
            return StreamTypeDetector.Detection(
                format = IptvStreamFormat.PROGRESSIVE,
                reason = "vod_url_extension"
            ).also(StreamTypeDetector::logDetection)
        }

        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "*/*")
                .build()
            client.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type")
                val bodyPrefix = response.body?.byteStream()?.use { stream ->
                    val buffer = ByteArray(PREFLIGHT_BYTES)
                    val read = stream.read(buffer).coerceAtLeast(0)
                    String(buffer, 0, read, Charsets.UTF_8)
                }.orEmpty()

                if (onDemandPlayback) {
                    resolveVodDetection(url, response.code, contentType, bodyPrefix)
                } else {
                    StreamTypeDetector.classify(url, contentType, bodyPrefix)
                }
            }
        }.getOrElse { error ->
            StreamTypeDetector.Detection(
                format = IptvStreamFormat.UNKNOWN,
                reason = "preflight_failed:${error.message?.take(80)}"
            ).also(StreamTypeDetector::logDetection)
        }
    }

    private fun resolveVodDetection(
        url: String,
        httpCode: Int,
        contentType: String?,
        bodyPrefix: String
    ): StreamTypeDetector.Detection {
        if (StreamTypeDetector.isFatalVodPreflightBlock(httpCode, contentType, bodyPrefix)) {
            val reason = when {
                httpCode in 400..599 -> "http_$httpCode"
                bodyPrefix.isEmpty() -> "empty_body"
                else -> "html_blocked"
            }
            return StreamTypeDetector.Detection(
                format = IptvStreamFormat.UNKNOWN,
                contentType = contentType,
                firstBytesSignature = bodyPrefix.take(48),
                reason = reason
            ).also(StreamTypeDetector::logDetection)
        }

        if (StreamTypeDetector.isVodProgressiveContentType(contentType)) {
            return StreamTypeDetector.Detection(
                format = IptvStreamFormat.PROGRESSIVE,
                contentType = contentType,
                firstBytesSignature = bodyPrefix.take(48),
                reason = "vod_content_type"
            ).also(StreamTypeDetector::logDetection)
        }

        return StreamTypeDetector.classify(url, contentType, bodyPrefix)
    }

    companion object {
        private const val PREFLIGHT_BYTES = 2_048
        private const val PREFLIGHT_TIMEOUT_SECONDS = 8L
    }
}
