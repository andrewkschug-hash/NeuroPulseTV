package com.grid.tv.feature.network.introdb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class IntroSkipWindow(
    val introStartSec: Double,
    val introEndSec: Double,
    val recapStartSec: Double? = null,
    val recapEndSec: Double? = null
) {
    fun skipTargetAt(positionSec: Double): Double? {
        if (positionSec in introStartSec..introEndSec) return introEndSec
        val rStart = recapStartSec ?: return null
        val rEnd = recapEndSec ?: return null
        if (positionSec in rStart..rEnd) return rEnd
        return null
    }

    fun isInSkipWindow(positionSec: Double): Boolean = skipTargetAt(positionSec) != null
}

/**
 * IntroDB lookup for skip-intro / skip-recap windows during series playback.
 * API key optional — unauthenticated read works for many titles.
 */
class IntroDbClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val apiKey: String? = null
) {
    private val cache = mutableMapOf<String, IntroSkipWindow?>()

    suspend fun lookup(
        tmdbId: Long?,
        imdbId: String?,
        season: Int?,
        episode: Int?
    ): IntroSkipWindow? = withContext(Dispatchers.IO) {
        val cacheKey = "${tmdbId ?: ""}|${imdbId ?: ""}|$season|$episode"
        cache[cacheKey]?.let { return@withContext it }
        if (tmdbId == null && imdbId.isNullOrBlank()) return@withContext null
        if (season == null || episode == null) return@withContext null
        runCatching {
            val idPart = when {
                tmdbId != null -> "tmdb:$tmdbId"
                else -> "imdb:$imdbId"
            }
            val url = buildString {
                append("https://api.introdb.app/show/$idPart/season/$season/episode/$episode")
                if (!apiKey.isNullOrBlank()) append("?api_key=$apiKey")
            }
            val request = Request.Builder().url(url).get().build()
            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body?.string().orEmpty()
            }
            if (body.isBlank()) return@runCatching null
            parseWindow(body)
        }.getOrNull().also { cache[cacheKey] = it }
    }

    private fun parseWindow(json: String): IntroSkipWindow? {
        val root = JSONObject(json)
        val intro = root.optJSONObject("intro") ?: return null
        val start = intro.optDouble("start", -1.0)
        val end = intro.optDouble("end", -1.0)
        if (start < 0 || end <= start) return null
        val recap = root.optJSONObject("recap")
        return IntroSkipWindow(
            introStartSec = start,
            introEndSec = end,
            recapStartSec = recap?.optDouble("start")?.takeIf { it >= 0 },
            recapEndSec = recap?.optDouble("end")?.takeIf { it > 0 }
        )
    }

    companion object {
        private const val TAG = "IntroDbClient"
    }
}
