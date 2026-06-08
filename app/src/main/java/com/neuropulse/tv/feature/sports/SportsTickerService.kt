package com.neuropulse.tv.feature.sports

import okhttp3.OkHttpClient
import okhttp3.Request

class SportsTickerService(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchTicker(): String {
        return runCatching {
            val request = Request.Builder()
                .url("https://www.thesportsdb.com/api/v1/json/3/livescore.php?s=Soccer")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use "Live scores unavailable"
                val body = response.body?.string().orEmpty()
                if (body.contains("events")) "Live Scores: Soccer events available" else "No live games now"
            }
        }.getOrDefault("Live scores unavailable")
    }
}
