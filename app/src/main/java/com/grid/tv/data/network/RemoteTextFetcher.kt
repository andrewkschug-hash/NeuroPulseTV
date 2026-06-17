package com.grid.tv.data.network

import java.io.EOFException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

@Singleton
class RemoteTextFetcher @Inject constructor(
    private val appHttpClient: AppHttpClient
) {
    suspend fun fetch(rawUrl: String): String = withContext(Dispatchers.IO) {
        val url = normalizeRemoteUrl(rawUrl)
        var lastEof: EOFException? = null
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                return@withContext executeFetch(url)
            } catch (e: EOFException) {
                lastEof = e
                if (attempt == MAX_FETCH_ATTEMPTS - 1) throw e
            }
        }
        throw lastEof ?: IllegalStateException("Fetch failed for $url")
    }

    private fun executeFetch(url: String): String {
        val requestBuilder = Request.Builder().url(url).get()
        if (isXtreamApiUrl(url)) {
            requestBuilder.header("Accept-Encoding", "identity")
        }
        val request = requestBuilder.build()
        appHttpClient.client().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP request failed (${response.code}) for $url")
            }
            response.body?.string().orEmpty()
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
        const val MAX_FETCH_ATTEMPTS = 3
    }
}
