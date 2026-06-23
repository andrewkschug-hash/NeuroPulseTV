package com.grid.tv.player

import com.grid.tv.data.network.AppHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Single lightweight GET before ExoPlayer prepare — reads the first ~2 KB to classify the stream.
 */
@Singleton
class StreamTypePreflightSniffer @Inject constructor(
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

    suspend fun detect(url: String): StreamTypeDetector.Detection = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext StreamTypeDetector.Detection(
                format = IptvStreamFormat.UNKNOWN,
                reason = "blank_url"
            )
        }

        if (StreamTypeDetector.isTsUrl(url)) {
            return@withContext StreamTypeDetector.Detection(
                format = IptvStreamFormat.PROGRESSIVE,
                reason = "ts_url"
            ).also(StreamTypeDetector::logDetection)
        }

        runCatching {
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
                StreamTypeDetector.classify(url, contentType, bodyPrefix)
            }
        }.getOrElse { error ->
            StreamTypeDetector.Detection(
                format = IptvStreamFormat.UNKNOWN,
                reason = "preflight_failed:${error.message?.take(80)}"
            ).also(StreamTypeDetector::logDetection)
        }
    }

    companion object {
        private const val PREFLIGHT_BYTES = 2_048
        private const val PREFLIGHT_TIMEOUT_SECONDS = 8L
    }
}
