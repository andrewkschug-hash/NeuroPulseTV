package com.grid.tv.player

import android.util.Log
import com.grid.tv.data.network.AppHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Runtime HEAD/manifest sniff at tune time for URLs with unknown format.
 * Populates [IptvStreamFormatRegistry] before ExoPlayer [prepare].
 */
class IptvStreamFormatProber(
    private val appHttpClient: AppHttpClient,
    private val registry: IptvStreamFormatRegistry,
    private val playbackNetworkCoordinator: PlaybackNetworkCoordinator
) {
    private val client by lazy {
        appHttpClient.probeClient().newBuilder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Probes when detection is ambiguous; returns resolved format for playback routing.
     */
    suspend fun probeAndRegister(url: String): IptvStreamFormat = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext IptvStreamFormat.UNKNOWN

        val cached = registry.get(url)
        val patternGuess = IptvStreamFormatDetector.detect(url, registry = registry)
        if (cached != null && patternGuess != IptvStreamFormat.UNKNOWN) {
            return@withContext IptvStreamFormatDetector.resolveForPlayback(url, registry = registry)
        }
        if (playbackNetworkCoordinator.isProbeBlocked(url)) {
            val resolved = IptvStreamFormatDetector.resolveForPlayback(url, registry = registry)
            if (resolved != IptvStreamFormat.UNKNOWN) {
                registry.put(url, resolved, IptvStreamFormatRegistry.Source.URL_PATTERN)
            }
            return@withContext resolved
        }
        if (!needsRuntimeProbe(url, patternGuess)) {
            val resolved = IptvStreamFormatDetector.resolveForPlayback(url, registry = registry)
            registry.put(url, resolved, IptvStreamFormatRegistry.Source.URL_PATTERN)
            return@withContext resolved
        }

        runCatching {
            headContentType(url)?.let { registry.putContentType(url, it) }
            if (shouldSniffManifest(url)) {
                sniffManifest(url)?.let { registry.putManifestSnippet(url, it) }
            }
        }.onFailure { error ->
            Log.w(TAG, "probe failed url=${url.take(96)} msg=${error.message}")
        }

        IptvStreamFormatDetector.resolveForPlayback(url, registry = registry).also { resolved ->
            registry.put(url, resolved, IptvStreamFormatRegistry.Source.CONTENT_TYPE)
            Log.d(TAG, "probe resolved format=$resolved url=${url.take(96)}")
        }
    }

    private fun needsRuntimeProbe(url: String, patternGuess: IptvStreamFormat): Boolean {
        if (registry.get(url) != null) return false
        if (patternGuess == IptvStreamFormat.PROGRESSIVE) return false
        if (patternGuess == IptvStreamFormat.HLS && url.contains(".m3u8", ignoreCase = true)) {
            return false
        }
        return patternGuess == IptvStreamFormat.UNKNOWN ||
            IptvStreamFormatDetector.looksLikeLiveIptvUrl(url)
    }

    private fun shouldSniffManifest(url: String): Boolean {
        registry.get(url)?.let { return it == IptvStreamFormat.HLS }
        return IptvStreamFormatDetector.matchesIptvHlsPattern(url) ||
            IptvStreamFormatDetector.looksLikeLiveIptvUrl(url)
    }

    private fun headContentType(url: String): String? {
        if (playbackNetworkCoordinator.isProbeBlocked(url)) return null
        val request = Request.Builder().url(url).head().build()
        client.newCall(request).execute().use { response ->
            return response.header("Content-Type")
        }
    }

    private fun sniffManifest(url: String): String? {
        if (playbackNetworkCoordinator.isProbeBlocked(url)) return null
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.byteStream()?.use { stream ->
                val buffer = ByteArray(MANIFEST_PEEK_BYTES)
                val read = stream.read(buffer).coerceAtLeast(0)
                String(buffer, 0, read)
            }
        }
    }

    companion object {
        private const val TAG = "IptvStreamFormatProber"
        private const val PROBE_TIMEOUT_SECONDS = 4L
        private const val MANIFEST_PEEK_BYTES = 4_096
    }
}
