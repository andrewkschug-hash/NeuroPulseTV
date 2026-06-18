package com.grid.tv.data.network

import android.util.Log
import java.io.EOFException
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

    private fun executeFetch(url: String): RemoteFetchResult {
        val requestBuilder = Request.Builder().url(url).get()
        if (isXtreamApiUrl(url)) {
            requestBuilder.header("Accept-Encoding", "identity")
        }
        val request = requestBuilder.build()
        return appHttpClient.client().newCall(request).execute().use { response ->
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

    private companion object {
        const val EPG_FLOW_TAG = "EpgFlow"
        const val MAX_FETCH_ATTEMPTS = 3
        const val GZIP_MAGIC_0: Byte = 0x1f
        const val GZIP_MAGIC_1: Byte = 0x8b.toByte()
    }
}
