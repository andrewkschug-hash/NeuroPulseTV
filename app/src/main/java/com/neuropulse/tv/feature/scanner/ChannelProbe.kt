package com.neuropulse.tv.feature.scanner

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class ProbeResult {
    LIVE,
    DEAD,
    UNKNOWN
}

class ChannelProbe(baseClient: OkHttpClient) {
    private val client = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun probe(url: String): ProbeResult {
        if (url.isBlank()) return ProbeResult.DEAD

        val headResult = runCatching { headCheck(url) }.getOrNull()
        if (headResult == ProbeResult.LIVE) return ProbeResult.LIVE
        if (headResult == ProbeResult.UNKNOWN) return ProbeResult.UNKNOWN

        if (url.contains(".m3u8", ignoreCase = true)) {
            return runCatching { hlsManifestCheck(url) }.getOrDefault(ProbeResult.DEAD)
        }

        return runCatching { rangeGetCheck(url) }.getOrDefault(ProbeResult.DEAD)
    }

    private fun headCheck(url: String): ProbeResult {
        val request = Request.Builder().url(url).head().build()
        client.newCall(request).execute().use { response ->
            return when {
                response.isSuccessful -> ProbeResult.LIVE
                response.code in 400..499 -> ProbeResult.DEAD
                else -> ProbeResult.UNKNOWN
            }
        }
    }

    private fun hlsManifestCheck(url: String): ProbeResult {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ProbeResult.DEAD
            val body = response.body?.string().orEmpty()
            return if (body.contains("#EXTINF", ignoreCase = true)) {
                ProbeResult.LIVE
            } else {
                ProbeResult.DEAD
            }
        }
    }

    private fun rangeGetCheck(url: String): ProbeResult {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-1")
            .build()
        client.newCall(request).execute().use { response ->
            return when {
                response.isSuccessful || response.code == 206 -> ProbeResult.LIVE
                response.code in 400..499 -> ProbeResult.DEAD
                else -> ProbeResult.UNKNOWN
            }
        }
    }
}
