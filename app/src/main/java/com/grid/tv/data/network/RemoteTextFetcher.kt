package com.grid.tv.data.network

import android.util.Log
import com.grid.tv.data.network.parser.ParsedXmlTv
import com.grid.tv.data.network.parser.XmlTvParser
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

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
    private val appHttpClient: AppHttpClient
) {
    suspend fun fetch(rawUrl: String): String = fetchDetailed(rawUrl).body

    suspend fun fetchDetailed(rawUrl: String): RemoteFetchResult = withContext(Dispatchers.IO) {
        val url = normalizeRemoteUrl(rawUrl)
        Log.i(EPG_FLOW_TAG, "HTTP GET $url")
        var lastEof: EOFException? = null
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                val result = executeFetch(url)
                Log.i(
                    EPG_FLOW_TAG,
                    "HTTP ${result.httpCode} for $url — ${result.rawBytes} raw bytes, " +
                        "${result.body.length} decoded chars"
                )
                return@withContext result
            } catch (e: EOFException) {
                lastEof = e
                Log.w(EPG_FLOW_TAG, "HTTP EOF on attempt ${attempt + 1}/$MAX_FETCH_ATTEMPTS for $url", e)
                if (attempt == MAX_FETCH_ATTEMPTS - 1) throw e
            } catch (e: Exception) {
                Log.e(EPG_FLOW_TAG, "HTTP fetch failed for $url: ${e.message}", e)
                throw e
            }
        }
        throw lastEof ?: IllegalStateException("Fetch failed for $url")
    }

    /**
     * Downloads XMLTV over the EPG-tuned HTTP client and parses incrementally from the response
     * stream (no full-body [String] allocation).
     */
    suspend fun fetchEpgXmlTv(rawUrl: String, parser: XmlTvParser): EpgParsedFetchResult =
        withContext(Dispatchers.IO) {
            val url = normalizeRemoteUrl(rawUrl)
            Log.i(EPG_FLOW_TAG, "HTTP GET (EPG stream) $url")
            var lastEof: EOFException? = null
            repeat(MAX_FETCH_ATTEMPTS) { attempt ->
                try {
                    val result = executeEpgFetch(url, parser)
                    Log.i(
                        EPG_FLOW_TAG,
                        "HTTP ${result.httpCode} for $url — ${result.rawBytes} raw bytes streamed, " +
                            "${result.parsed.channelsById.size} channels, " +
                            "${result.parsed.programs.size} programmes parsed"
                    )
                    return@withContext result
                } catch (e: EOFException) {
                    lastEof = e
                    Log.w(
                        EPG_FLOW_TAG,
                        "EPG stream EOF on attempt ${attempt + 1}/$MAX_FETCH_ATTEMPTS for $url",
                        e
                    )
                    if (attempt == MAX_FETCH_ATTEMPTS - 1) throw e
                } catch (e: Exception) {
                    Log.e(EPG_FLOW_TAG, "EPG stream fetch failed for $url: ${e.message}", e)
                    throw e
                }
            }
            throw lastEof ?: IllegalStateException("EPG fetch failed for $url")
        }

    private fun executeFetch(url: String): RemoteFetchResult {
        val requestBuilder = Request.Builder().url(url).get()
        if (isXtreamApiUrl(url)) {
            requestBuilder.header("Accept-Encoding", "identity")
        }
        val request = requestBuilder.build()
        return selectClient(url).newCall(request).execute().use { response ->
            val code = response.code
            val bytes = response.body?.bytes() ?: byteArrayOf()
            if (!response.isSuccessful) {
                Log.e(EPG_FLOW_TAG, "HTTP $code (unsuccessful) for $url — ${bytes.size} bytes in body")
                throw IllegalStateException("HTTP request failed ($code) for $url")
            }
            val body = decodeResponseBody(bytes, response.header("Content-Encoding"), url)
            RemoteFetchResult(httpCode = code, rawBytes = bytes.size, body = body)
        }
    }

    private fun executeEpgFetch(url: String, parser: XmlTvParser): EpgParsedFetchResult {
        val request = Request.Builder().url(url).get().build()
        return appHttpClient.epgClient().newCall(request).execute().use { response ->
            val code = response.code
            Log.i(
                EPG_FLOW_TAG,
                "HTTP $code for $url — response headers received, waiting for body stream…"
            )
            if (!response.isSuccessful) {
                val errorBytes = response.body?.bytes() ?: byteArrayOf()
                Log.e(EPG_FLOW_TAG, "HTTP $code (unsuccessful) for $url — ${errorBytes.size} bytes in body")
                throw IllegalStateException("HTTP request failed ($code) for $url")
            }
            val body = response.body
                ?: throw IllegalStateException("Empty HTTP body for $url")
            val countingStream = ByteProgressInputStream(body.byteStream(), url)
            val decodedStream = openDecompressedStream(countingStream, response.header("Content-Encoding"), url)
            val parsed = parser.parse(decodedStream)
            EpgParsedFetchResult(
                httpCode = code,
                rawBytes = countingStream.bytesRead,
                parsed = parsed
            )
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

    private fun openDecompressedStream(
        stream: InputStream,
        contentEncoding: String?,
        url: String
    ): InputStream {
        if (contentEncoding?.contains("gzip", ignoreCase = true) == true ||
            url.endsWith(".gz", ignoreCase = true) ||
            url.contains(".xml.gz", ignoreCase = true)
        ) {
            Log.i(EPG_FLOW_TAG, "EPG response for $url is gzip-encoded — decompressing while streaming")
            return GZIPInputStream(BufferedInputStream(stream))
        }
        val buffered = BufferedInputStream(stream)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        return if (first == GZIP_MAGIC_0.toInt() && second == GZIP_MAGIC_1.toInt()) {
            Log.i(EPG_FLOW_TAG, "EPG response for $url has gzip magic — decompressing while streaming")
            GZIPInputStream(buffered)
        } else {
            buffered
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
        const val MAX_FETCH_ATTEMPTS = 3
        const val GZIP_MAGIC_0: Byte = 0x1f
        const val GZIP_MAGIC_1: Byte = 0x8b.toByte()
        const val BYTES_PER_LOG_MB = 10L * 1024L * 1024L
    }
}
