package com.neuropulse.tv.data.network

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
        val request = Request.Builder().url(url).get().build()
        appHttpClient.client().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP request failed (${response.code}) for $url")
            }
            response.body?.string().orEmpty()
        }
    }

    fun normalizeRemoteUrl(raw: String): String {
        val trimmed = raw.trim()
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
}
