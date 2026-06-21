package com.grid.tv.data.network

import android.content.Context
import android.util.Log
import com.grid.tv.data.network.parser.ParsedXmlTv
import com.grid.tv.data.network.parser.XmlTvParser
import com.grid.tv.data.io.DiskIoSerialExecutor
import com.grid.tv.feature.epg.EpgCoroutineDispatchers
import com.grid.tv.feature.epg.EpgFlowLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.GzipSource
import okio.buffer
import okio.sink
import okio.source
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
    private val epgDispatchers: EpgCoroutineDispatchers,
    private val diskIoSerialExecutor: DiskIoSerialExecutor
) {
    suspend fun fetch(rawUrl: String): String = fetchDetailed(rawUrl).body

    /**
     * API fetch that returns null on HTTP/network failure without throwing.
     * Use for on-demand UI loads (e.g. get_series_info) where a provider 5xx must not crash the app.
     */
    suspend fun tryFetch(rawUrl: String): String? = withContext(Dispatchers.IO) {
        val url = normalizeRemoteUrl(rawUrl)
        val logTag = if (isVodCatalogUrl(url)) VOD_FLOW_TAG else EPG_FLOW_TAG
        when (val outcome = executeFetchOutcome(url, selectClient(url), logTag)) {
            is SimpleFetchOutcome.Ok -> outcome.body
            is SimpleFetchOutcome.Http -> {
                logHttpError(logTag, outcome.httpCode, url, outcome.bodyPreview)
                null
            }
            is SimpleFetchOutcome.Network -> {
                logNetworkError(logTag, url, outcome.error)
                null
            }
        }
    }

    /**
     * Viewport get_short_epg fetch — returns null on HTTP/network failure without throwing.
     * Uses a short-timeout client so provider 522s cannot block the guide for 20+ seconds.
     */
    suspend fun tryFetchShortEpg(rawUrl: String): String? = withContext(Dispatchers.IO) {
        val url = normalizeRemoteUrl(rawUrl)
        when (val outcome = executeFetchOutcome(url, appHttpClient.shortEpgClient(), EPG_FLOW_TAG)) {
            is SimpleFetchOutcome.Ok -> outcome.body
            is SimpleFetchOutcome.Http -> {
                logHttpError(EPG_FLOW_TAG, outcome.httpCode, url, outcome.bodyPreview)
                null
            }
            is SimpleFetchOutcome.Network -> {
                logNetworkError(EPG_FLOW_TAG, url, outcome.error)
                null
            }
        }
    }

    suspend fun fetchDetailed(rawUrl: String): RemoteFetchResult = withContext(Dispatchers.IO) {
        val url = normalizeRemoteUrl(rawUrl)
        val logTag = if (isVodCatalogUrl(url)) VOD_FLOW_TAG else EPG_FLOW_TAG
        Log.i(logTag, "HTTP GET $url")
        var lastRetryable: Exception? = null
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                val result = executeFetch(url, logTag)
                Log.i(
                    logTag,
                    "HTTP ${result.httpCode} for $url — ${result.rawBytes} raw bytes, " +
                        "${result.body.length} decoded chars"
                )
                return@withContext result
            } catch (e: Exception) {
                if (isRetryableNetworkError(e) && attempt < MAX_FETCH_ATTEMPTS - 1) {
                    lastRetryable = e
                    Log.w(
                        logTag,
                        "HTTP transient error on attempt ${attempt + 1}/$MAX_FETCH_ATTEMPTS for $url: ${e.message}",
                        e
                    )
                    delay(retryBackoffMs(attempt))
                    return@repeat
                }
                if (e is IllegalStateException && e.message?.startsWith("HTTP request failed") == true) throw e
                Log.e(logTag, "HTTP fetch failed for $url: ${e.message}", e)
                throw e
            }
        }
        throw lastRetryable ?: IllegalStateException("Fetch failed for $url")
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
    ): EpgXmlTvFetchOutcome =
        withContext(epgDispatchers.io) {
            val url = normalizeRemoteUrl(rawUrl)
            EpgFlowLogger.downloadStarted(playlistId, playlistName, url)
            var lastRetryable: Exception? = null
            repeat(MAX_FETCH_ATTEMPTS) { attempt ->
                try {
                    when (val outcome = executeEpgFetch(url, parser, playlistId, playlistName)) {
                        is InternalEpgFetch.Ok -> {
                            EpgFlowLogger.downloadCompleted(
                                playlistId,
                                playlistName,
                                outcome.result.httpCode,
                                outcome.result.rawBytes
                            )
                            return@withContext EpgXmlTvFetchOutcome.Success(outcome.result)
                        }
                        is InternalEpgFetch.Http -> {
                            Log.w(
                                EPG_FLOW_TAG,
                                "EPG unavailable HTTP ${outcome.httpCode} for $url — skipping import " +
                                    "(${outcome.bodyPreview ?: "no body"})"
                            )
                            return@withContext EpgXmlTvFetchOutcome.HttpError(
                                httpCode = outcome.httpCode,
                                url = url,
                                bodyPreview = outcome.bodyPreview
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (isRetryableNetworkError(e) && attempt < MAX_FETCH_ATTEMPTS - 1) {
                        lastRetryable = e
                        Log.w(
                            EPG_FLOW_TAG,
                            "EPG download transient error on attempt ${attempt + 1}/$MAX_FETCH_ATTEMPTS " +
                                "for $url: ${e.message}",
                            e
                        )
                        delay(retryBackoffMs(attempt))
                        return@repeat
                    }
                    Log.e(EPG_FLOW_TAG, "EPG disk-backed fetch failed for $url: ${e.message}", e)
                    EpgFlowLogger.importFailed(playlistId, playlistName, url, e)
                    throw e
                }
            }
            val failure = lastRetryable ?: IllegalStateException("EPG fetch failed for $url")
            EpgFlowLogger.importFailed(playlistId, playlistName, url, failure)
            throw failure
        }

    private sealed interface SimpleFetchOutcome {
        data class Ok(val body: String, val httpCode: Int, val rawBytes: Int) : SimpleFetchOutcome
        data class Http(val httpCode: Int, val bodyPreview: String?) : SimpleFetchOutcome
        data class Network(val error: IOException) : SimpleFetchOutcome
    }

    private fun executeFetch(url: String, logTag: String = EPG_FLOW_TAG): RemoteFetchResult {
        when (val outcome = executeFetchOutcome(url, selectClient(url), logTag)) {
            is SimpleFetchOutcome.Ok -> return RemoteFetchResult(
                httpCode = outcome.httpCode,
                rawBytes = outcome.rawBytes,
                body = outcome.body
            )
            is SimpleFetchOutcome.Http -> {
                logHttpError(logTag, outcome.httpCode, url, outcome.bodyPreview)
                throw IllegalStateException("HTTP request failed (${outcome.httpCode}) for $url")
            }
            is SimpleFetchOutcome.Network -> {
                logNetworkError(logTag, url, outcome.error)
                throw outcome.error
            }
        }
    }

    private fun executeFetchOutcome(
        url: String,
        httpClient: okhttp3.OkHttpClient,
        logTag: String = EPG_FLOW_TAG
    ): SimpleFetchOutcome {
        val requestBuilder = Request.Builder().url(url).get()
        if (isXtreamApiUrl(url)) {
            requestBuilder.header("Accept-Encoding", "identity")
        }
        val request = requestBuilder.build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                if (!response.isSuccessful) {
                    val preview = readAndCloseErrorBody(response.body, response.header("Content-Encoding"))
                    return@use SimpleFetchOutcome.Http(code, preview)
                }
                val bytes = response.body?.bytes() ?: byteArrayOf()
                val body = decodeResponseBody(bytes, response.header("Content-Encoding"), url)
                SimpleFetchOutcome.Ok(body = body, httpCode = code, rawBytes = bytes.size)
            }
        } catch (e: IOException) {
            SimpleFetchOutcome.Network(e)
        }
    }

    private sealed interface InternalEpgFetch {
        data class Ok(val result: EpgParsedFetchResult) : InternalEpgFetch
        data class Http(val httpCode: Int, val bodyPreview: String?) : InternalEpgFetch
    }

    private suspend fun executeEpgFetch(
        url: String,
        parser: XmlTvParser,
        playlistId: Long,
        playlistName: String
    ): InternalEpgFetch = withContext(diskIoSerialExecutor.dispatcher) {
        val cacheFile = createEpgCacheFile(playlistId)
        var contentEncoding: String? = null
        var httpCode = 0
        var rawBytes = 0L

        try {
            ensureCacheSpaceForEpgDownload()
            val request = Request.Builder()
                .url(url)
                .header("Accept-Encoding", ACCEPT_ENCODING_GZIP)
                .get()
                .build()

            appHttpClient.epgClient().newCall(request).execute().use { response ->
                httpCode = response.code
                if (!response.isSuccessful) {
                    val preview = readAndCloseErrorBody(response.body, response.header("Content-Encoding"))
                    logHttpError(EPG_FLOW_TAG, httpCode, url, preview)
                    return@withContext InternalEpgFetch.Http(httpCode, preview)
                }
                val body = response.body
                    ?: throw IllegalStateException("Empty HTTP body for $url")
                contentEncoding = response.header("Content-Encoding")
                Log.i(
                    EPG_FLOW_TAG,
                    "HTTP $httpCode for $url — spooling body to ${cacheFile.name} " +
                        "(Accept-Encoding: $ACCEPT_ENCODING_GZIP)…"
                )
                val spooled = downloadBodyToCacheFile(body, url, cacheFile, contentEncoding)
                rawBytes = spooled.networkBytesRead
                Log.i(
                    EPG_FLOW_TAG,
                    "EPG spool stats for $url — network=${spooled.networkBytesRead} bytes, " +
                        "decompressed=${spooled.decompressedBytesWritten} bytes on disk"
                )
            }

            Log.i(
                EPG_FLOW_TAG,
                "EPG download complete for $url — ${rawBytes} network bytes, " +
                    "${cacheFile.length()} bytes decompressed at ${cacheFile.absolutePath}"
            )

            EpgFlowLogger.parseStarted(playlistId, playlistName, url)
            val parsed = try {
                // Cache file always holds decompressed XML after spooling.
                parser.parseFile(cacheFile, contentEncoding = null, sourceUrl = url)
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
            InternalEpgFetch.Ok(
                EpgParsedFetchResult(
                    httpCode = httpCode,
                    rawBytes = rawBytes,
                    parsed = parsed
                )
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

    private data class SpoolResult(
        val networkBytesRead: Long,
        val decompressedBytesWritten: Long
    )

    private fun downloadBodyToCacheFile(
        body: ResponseBody,
        url: String,
        destination: File,
        contentEncoding: String?
    ): SpoolResult {
        val gzipOnWire = contentEncoding?.contains("gzip", ignoreCase = true) == true
        if (gzipOnWire) {
            Log.i(
                EPG_FLOW_TAG,
                "EPG response Content-Encoding=gzip — decompressing on-the-fly while spooling to " +
                    destination.name
            )
        }

        body.source().use { wireSource ->
            val progressSource = ProgressSource(wireSource, url)
            val decodeSource: okio.Source = if (gzipOnWire) GzipSource(progressSource) else progressSource

            try {
                destination.sink().buffer().use { fileSink ->
                    val readBuf = okio.Buffer()
                    while (true) {
                        val read = decodeSource.read(readBuf, SPOOL_BUFFER_SIZE.toLong())
                        if (read == -1L) break
                        fileSink.write(readBuf, read)
                        readBuf.clear()
                    }
                }
            } catch (e: IOException) {
                destination.delete()
                throw IOException(
                    "Failed writing EPG body to ${destination.absolutePath}: ${e.message}",
                    e
                )
            } finally {
                decodeSource.close()
            }

            if (!destination.exists() || destination.length() <= 0L) {
                destination.delete()
                throw IOException("EPG cache file was not written: ${destination.absolutePath}")
            }
            return SpoolResult(
                networkBytesRead = progressSource.bytesRead,
                decompressedBytesWritten = destination.length()
            )
        }
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

    private fun readAndCloseErrorBody(body: ResponseBody?, contentEncoding: String?): String {
        if (body == null) return "(empty body)"
        return body.use { responseBody ->
            formatHttpErrorPreview(responseBody.bytes(), contentEncoding)
        }
    }

    private fun formatHttpErrorPreview(bytes: ByteArray, contentEncoding: String?): String {
        if (bytes.isEmpty()) return "(empty body)"
        if (looksLikeGzip(bytes, contentEncoding)) {
            return "(gzip-compressed error body, ${bytes.size} bytes)"
        }
        return bytes.decodeToString().take(200).ifBlank { "(binary error body, ${bytes.size} bytes)" }
    }

    private fun looksLikeGzip(bytes: ByteArray, contentEncoding: String?): Boolean =
        contentEncoding?.contains("gzip", ignoreCase = true) == true ||
            (bytes.size >= 2 && bytes[0] == GZIP_MAGIC_0 && bytes[1] == GZIP_MAGIC_1)

    private fun logNetworkError(logTag: String, url: String, error: IOException) {
        Log.w(logTag, "HTTP network error for $url: ${error.message}")
    }

    private fun logHttpError(logTag: String, code: Int, url: String, preview: String?) {
        val detail = preview ?: "(no body)"
        when (code) {
            404 -> Log.w(logTag, "HTTP 404 Not Found for $url — endpoint unavailable ($detail)")
            429 -> Log.e(logTag, "HTTP 429 rate-limited for $url — $detail")
            522 -> Log.w(logTag, "HTTP 522 origin unreachable for $url — provider/CDN down ($detail)")
            in 500..599 -> Log.w(logTag, "HTTP $code server error for $url — $detail")
            else -> Log.w(logTag, "HTTP $code for $url — $detail")
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

    private fun retryBackoffMs(attempt: Int): Long =
        (INITIAL_RETRY_BACKOFF_MS shl attempt.coerceAtMost(3)).coerceAtMost(MAX_RETRY_BACKOFF_MS)

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

    /** Counts wire bytes and logs when the body stream starts and during large downloads. */
    private class ProgressSource(
        private val delegate: okio.Source,
        private val url: String
    ) : okio.Source {
        var bytesRead: Long = 0
            private set
        private var loggedFirstByte = false
        private var lastLoggedMb = 0L

        override fun read(sink: okio.Buffer, byteCount: Long): Long {
            val read = delegate.read(sink, byteCount)
            if (read > 0L) {
                recordBytes(read)
            }
            return read
        }

        override fun timeout(): okio.Timeout = delegate.timeout()

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

    companion object {
        const val EPG_FLOW_TAG = "EpgFlow"
        const val VOD_FLOW_TAG = "VodCatalogPipeline"
        const val MAX_FETCH_ATTEMPTS = 3
        const val INITIAL_RETRY_BACKOFF_MS = 1_500L
        const val MAX_RETRY_BACKOFF_MS = 8_000L
        const val GZIP_MAGIC_0: Byte = 0x1f
        const val GZIP_MAGIC_1: Byte = 0x8b.toByte()
        const val BYTES_PER_LOG_MB = 10L * 1024L * 1024L
        const val BYTES_PER_MB = 1024L * 1024L
        const val MIN_CACHE_HEADROOM_BYTES = 128L * BYTES_PER_MB
        const val SPOOL_BUFFER_SIZE = 131_072
        const val ACCEPT_ENCODING_GZIP = "gzip"

        fun isRetryableErrorMessage(message: String?): Boolean {
            if (message.isNullOrBlank()) return false
            return isRetryableNetworkError(IOException(message))
        }

        /**
         * True for mid-stream disconnects and other errors where a fresh HTTP GET may succeed.
         * Non-retryable: HTTP errors, cache exhaustion, parse failures, empty bodies.
         */
        fun isRetryableNetworkError(error: Throwable): Boolean {
            if (error.message?.contains("Insufficient cache space", ignoreCase = true) == true) {
                return false
            }
            var current: Throwable? = error
            while (current != null) {
                when (current) {
                    is EOFException,
                    is SocketTimeoutException,
                    is SocketException -> return true
                    is IOException -> {
                        val msg = current.message?.lowercase().orEmpty()
                        if (msg.contains("connection abort") ||
                            msg.contains("connection reset") ||
                            msg.contains("unexpected end of stream") ||
                            msg.contains("broken pipe") ||
                            msg.contains("failed to connect") ||
                            msg.contains("connection timed out") ||
                            msg.contains("software caused connection abort")
                        ) {
                            return true
                        }
                    }
                }
                current = current.cause
            }
            return false
        }
    }
}
