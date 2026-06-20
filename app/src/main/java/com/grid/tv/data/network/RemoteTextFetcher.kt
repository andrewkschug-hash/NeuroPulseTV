package com.grid.tv.data.network

import android.content.Context
import android.util.Log
import com.grid.tv.data.network.parser.ParsedXmlTv
import com.grid.tv.data.network.parser.XmlTvParser
import com.grid.tv.feature.epg.EpgCoroutineDispatchers
import com.grid.tv.feature.epg.EpgFlowLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody

data class RemoteFetchResult(
    val httpCode: Int,
    val rawBytes: Int,
    val body: String
)

data class EpgParsedFetchResult(
    val httpCode: Int,
    val rawBytes: Long,
    val parsed: ParsedXmlTv
)

@Singleton
class RemoteTextFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appHttpClient: AppHttpClient,
    private val epgDispatchers: EpgCoroutineDispatchers
) {
    suspend fun fetch(rawUrl: String): String = fetchDetailed(rawUrl).body

    suspend fun fetchDetailed(rawUrl: String): RemoteFetchResult = withContext(Dispatchers.IO) {
        val url = normalizeRemoteUrl(rawUrl)
        val logTag = if (isVodCatalogUrl(url)) VOD_FLOW_TAG else EPG_FLOW_TAG
        Log.i(logTag, "HTTP GET $url")
        var lastEof: EOFException? = null
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                val result = executeFetch(url, logTag)
                Log.i(
                    logTag,
                    "HTTP ${result.httpCode} for $url — ${result.rawBytes} raw bytes, " +
                        "${result.body.length} decoded chars"
                )
                return@withContext result
            } catch (e: EOFException) {
                lastEof = e
                Log.w(EPG_FLOW_TAG, "HTTP EOF on attempt ${attempt + 1}/$MAX_FETCH_ATTEMPTS for $url", e)
                if (attempt == MAX_FETCH_ATTEMPTS - 1) throw e
            } catch (e: Exception) {
                Log.e(logTag, "HTTP fetch failed for $url: ${e.message}", e)
                throw e
            }
        }
        throw lastEof ?: IllegalStateException("Fetch failed for $url")
    }

    /**
     * Downloads XMLTV to a cache file first, then parses from disk so network hiccups during
     * parse cannot abort the remote socket mid-stream.
     */
    suspend fun fetchEpgXmlTv(
        rawUrl: String,
        parser: XmlTvParser,
        playlistId: Long,
        playlistName: String
    ): EpgParsedFetchResult =
        withContext(epgDispatchers.io) {
            val url = normalizeRemoteUrl(rawUrl)
            EpgFlowLogger.downloadStarted(playlistId, playlistName, url)
            var lastEof: EOFException? = null
            repeat(MAX_FETCH_ATTEMPTS) { attempt ->
                try {
                    val result = executeEpgFetch(url, parser, playlistId, playlistName)
                    EpgFlowLogger.downloadCompleted(
                        playlistId,
                        playlistName,
                        result.httpCode,
                        result.rawBytes
                    )
                    return@withContext result
                } catch (e: EOFException) {
                    lastEof = e
                    Log.w(
                        EPG_FLOW_TAG,
                        "EPG download EOF on attempt ${attempt + 1}/$MAX_FETCH_ATTEMPTS for $url",
                        e
                    )
                    if (attempt == MAX_FETCH_ATTEMPTS - 1) {
                        EpgFlowLogger.importFailed(playlistId, playlistName, url, e)
                        throw e
                    }
                } catch (e: Exception) {
                    Log.e(EPG_FLOW_TAG, "EPG disk-backed fetch failed for $url: ${e.message}", e)
                    EpgFlowLogger.importFailed(playlistId, playlistName, url, e)
                    throw e
                }
            }
            val failure = lastEof ?: IllegalStateException("EPG fetch failed for $url")
            EpgFlowLogger.importFailed(playlistId, playlistName, url, failure)
            throw failure
        }

    private fun executeFetch(url: String, logTag: String = EPG_FLOW_TAG): RemoteFetchResult {
        val requestBuilder = Request.Builder().url(url).get()
        if (isXtreamApiUrl(url)) {
            requestBuilder.header("Accept-Encoding", "identity")
        }
        val request = requestBuilder.build()
        return selectClient(url).newCall(request).execute().use { response ->
            val code = response.code
            val bytes = response.body?.bytes() ?: byteArrayOf()
            if (!response.isSuccessful) {
                val bodyPreview = bytes.decodeToString().take(200)
                when (code) {
                    429 -> Log.e(
                        logTag,
                        "HTTP 429 rate-limited for $url — provider may be throttling repeated VOD requests. " +
                            "bodyPreview=$bodyPreview"
                    )
                    in 500..599 -> Log.e(
                        logTag,
                        "HTTP $code server error for $url — ${bytes.size} bytes, bodyPreview=$bodyPreview"
                    )
                    else -> Log.e(
                        logTag,
                        "HTTP $code (unsuccessful) for $url — ${bytes.size} bytes, bodyPreview=$bodyPreview"
                    )
                }
                throw IllegalStateException("HTTP request failed ($code) for $url")
            }
            val body = decodeResponseBody(bytes, response.header("Content-Encoding"), url)
            RemoteFetchResult(httpCode = code, rawBytes = bytes.size, body = body)
        }
    }

    private fun executeEpgFetch(
        url: String,
        parser: XmlTvParser,
        playlistId: Long,
        playlistName: String
    ): EpgParsedFetchResult {
        val cacheFile = createEpgCacheFile(playlistId)
        var contentEncoding: String? = null
        var httpCode = 0
        var rawBytes = 0L

        try {
            ensureCacheSpaceForEpgDownload()
            val request = Request.Builder().url(url).get().build()
            val callClient = appHttpClient.epgClient().newBuilder()
                .readTimeout(EPG_DOWNLOAD_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .callTimeout(EPG_DOWNLOAD_CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .build()

            callClient.newCall(request).execute().use { response ->
                httpCode = response.code
                Log.i(
                    EPG_FLOW_TAG,
                    "HTTP $httpCode for $url — response headers received, spooling body to " +
                        "${cacheFile.name}…"
                )
                if (!response.isSuccessful) {
                    val errorBytes = response.body?.bytes() ?: byteArrayOf()
                    Log.e(EPG_FLOW_TAG, "HTTP $httpCode (unsuccessful) for $url — ${errorBytes.size} bytes in body")
                    throw IllegalStateException("HTTP request failed ($httpCode) for $url")
                }
                val body = response.body
                    ?: throw IllegalStateException("Empty HTTP body for $url")
                contentEncoding = response.header("Content-Encoding")
                rawBytes = downloadBodyToCacheFile(body, url, cacheFile)
            }

            Log.i(
                EPG_FLOW_TAG,
                "EPG download complete for $url — ${rawBytes} bytes written to ${cacheFile.absolutePath}"
            )

            EpgFlowLogger.parseStarted(playlistId, playlistName, url)
            val parsed = try {
                parser.parseFile(cacheFile, contentEncoding, url)
            } catch (e: Exception) {
                Log.e(
                    EPG_FLOW_TAG,
                    "EPG parse from cache failed for $url (${cacheFile.length()} bytes on disk): ${e.message}",
                    e
                )
                throw e
            }
            EpgFlowLogger.parseCompleted(
                playlistId,
                playlistName,
                parsed.channelsById.size,
                parsed.programs.size
            )
            return EpgParsedFetchResult(
                httpCode = httpCode,
                rawBytes = rawBytes,
                parsed = parsed
            )
        } catch (e: IOException) {
            Log.e(
                EPG_FLOW_TAG,
                "EPG cache I/O failed for playlist=$playlistId url=$url: ${e.message}",
                e
            )
            throw e
        } finally {
            deleteEpgCacheFile(cacheFile)
        }
    }

    private fun createEpgCacheFile(playlistId: Long): File {
        context.cacheDir.mkdirs()
        return File(context.cacheDir, "epg_pl${playlistId}_${System.currentTimeMillis()}.xmltv.tmp")
    }

    private fun ensureCacheSpaceForEpgDownload() {
        val usable = context.cacheDir.usableSpace
        if (usable in 1 until MIN_CACHE_HEADROOM_BYTES) {
            throw IOException(
                "Insufficient cache space for EPG download (${usable / BYTES_PER_MB}MB free, " +
                    "need at least ${MIN_CACHE_HEADROOM_BYTES / BYTES_PER_MB}MB headroom)"
            )
        }
    }

    private fun downloadBodyToCacheFile(body: ResponseBody, url: String, destination: File): Long {
        val countingStream = ByteProgressInputStream(body.byteStream(), url)
        try {
            destination.outputStream().buffered(DEFAULT_BUFFER_SIZE).use { output ->
                countingStream.copyTo(output)
            }
        } catch (e: IOException) {
            destination.delete()
            throw IOException("Failed writing EPG body to ${destination.absolutePath}: ${e.message}", e)
        } finally {
            countingStream.close()
        }
        if (!destination.exists() || destination.length() <= 0L) {
            destination.delete()
            throw IOException("EPG cache file was not written: ${destination.absolutePath}")
        }
        return countingStream.bytesRead
    }

    private fun deleteEpgCacheFile(file: File) {
        if (!file.exists()) return
        if (!file.delete()) {
            Log.w(EPG_FLOW_TAG, "Failed to delete EPG cache file ${file.absolutePath}")
        }
    }

    internal fun decodeResponseBody(bytes: ByteArray, contentEncoding: String?, url: String): String {
        val isGzip = contentEncoding?.contains("gzip", ignoreCase = true) == true ||
            url.endsWith(".gz", ignoreCase = true) ||
            url.contains(".xml.gz", ignoreCase = true) ||
            (bytes.size >= 2 && bytes[0] == GZIP_MAGIC_0 && bytes[1] == GZIP_MAGIC_1)
        return if (isGzip) {
            GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }
        } else {
            bytes.toString(Charsets.UTF_8)
        }
    }

    private fun isXtreamApiUrl(url: String): Boolean =
        url.contains("player_api.php", ignoreCase = true)

    private fun isVodCatalogUrl(url: String): Boolean {
        if (!isXtreamApiUrl(url)) return false
        return url.contains("action=get_vod_streams", ignoreCase = true) ||
            url.contains("action=get_vod_categories", ignoreCase = true) ||
            url.contains("action=get_series", ignoreCase = true) ||
            url.contains("action=get_series_categories", ignoreCase = true)
    }

    private fun selectClient(url: String) = when {
        isVodCatalogUrl(url) -> appHttpClient.vodClient()
        else -> appHttpClient.client()
    }

    fun normalizeRemoteUrl(raw: String): String {
        val trimmed = raw.trim()
            .replace("\u200B", "")
            .replace(Regex("""\s+"""), "")
        require(trimmed.isNotBlank()) { "URL is blank" }
        return when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("/") -> throw IllegalArgumentException(
                "URL must include a host, not a path only: $trimmed"
            )
            else -> "http://$trimmed"
        }
    }

    /** Counts network bytes and logs when the body stream starts and during large downloads. */
    private class ByteProgressInputStream(
        private val delegate: InputStream,
        private val url: String
    ) : InputStream() {
        var bytesRead: Long = 0
            private set
        private var loggedFirstByte = false
        private var lastLoggedMb = 0L

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) {
                recordBytes(1)
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = delegate.read(b, off, len)
            if (count > 0) {
                recordBytes(count.toLong())
            }
            return count
        }

        override fun close() {
            delegate.close()
        }

        private fun recordBytes(count: Long) {
            bytesRead += count
            if (!loggedFirstByte) {
                loggedFirstByte = true
                Log.i(EPG_FLOW_TAG, "HTTP body stream started for $url (first $count byte(s) received)")
            }
            val currentMb = bytesRead / BYTES_PER_LOG_MB
            if (currentMb > lastLoggedMb) {
                lastLoggedMb = currentMb
                Log.i(EPG_FLOW_TAG, "EPG download progress for $url: ${currentMb}MB received…")
            }
        }
    }

    private companion object {
        const val EPG_FLOW_TAG = "EpgFlow"
        const val VOD_FLOW_TAG = "VodCatalogPipeline"
        const val MAX_FETCH_ATTEMPTS = 3
        const val GZIP_MAGIC_0: Byte = 0x1f
        const val GZIP_MAGIC_1: Byte = 0x8b.toByte()
        const val BYTES_PER_LOG_MB = 10L * 1024L * 1024L
        const val BYTES_PER_MB = 1024L * 1024L
        const val MIN_CACHE_HEADROOM_BYTES = 128L * BYTES_PER_MB
        const val EPG_DOWNLOAD_READ_TIMEOUT_MINUTES = 2L
        const val EPG_DOWNLOAD_CALL_TIMEOUT_MINUTES = 6L
    }
}
