package com.neuropulse.tv.data.network.tmdb

import com.neuropulse.tv.BuildConfig
import com.neuropulse.tv.data.network.AppHttpClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class TmdbService @Inject constructor(
    appHttpClient: AppHttpClient
) {
    private val client: OkHttpClient = appHttpClient.client()
    private val rateLimitMutex = Mutex()
    private val requestTimesMs = ArrayDeque<Long>()

    private val apiKey: String = BuildConfig.TMDB_API_KEY.trim()
    private val baseUrl: String = BuildConfig.TMDB_BASE_URL.trim().ifBlank { DEFAULT_BASE_URL }
    private val imageBaseUrl: String = BuildConfig.TMDB_IMAGE_BASE_URL.trim().ifBlank { DEFAULT_IMAGE_BASE_URL }

    init {
        if (apiKey.isBlank()) {
            throw IllegalStateException("TMDB API key is missing. Set TMDB_API_KEY in .env")
        }
    }

    suspend fun searchMovie(title: String, year: Int? = null): JSONObject? {
        val queryParams = mutableMapOf("query" to title, "include_adult" to "false")
        year?.let { queryParams["year"] = it.toString() }
        val json = getJson("/search/movie", queryParams)
        return json.optJSONArray("results")?.optJSONObject(0)
    }

    suspend fun searchTV(title: String, year: Int? = null): JSONObject? {
        val queryParams = mutableMapOf("query" to title, "include_adult" to "false")
        year?.let { queryParams["first_air_date_year"] = it.toString() }
        val json = getJson("/search/tv", queryParams)
        return json.optJSONArray("results")?.optJSONObject(0)
    }

    suspend fun getMovieDetails(tmdbId: Long): JSONObject {
        return getJson(
            "/movie/$tmdbId",
            mapOf(
                "append_to_response" to "credits,keywords,external_ids,release_dates"
            )
        )
    }

    suspend fun getTVDetails(tmdbId: Long): JSONObject {
        return getJson(
            "/tv/$tmdbId",
            mapOf(
                "append_to_response" to "credits,keywords,external_ids,content_ratings"
            )
        )
    }

    suspend fun findByIMDbId(imdbId: String): JSONObject? {
        val json = getJson(
            "/find/$imdbId",
            mapOf("external_source" to "imdb_id")
        )
        json.optJSONArray("movie_results")?.optJSONObject(0)?.let { return it.put("media_type", "movie") }
        json.optJSONArray("tv_results")?.optJSONObject(0)?.let { return it.put("media_type", "tv") }
        return null
    }

    suspend fun getEpisodeDetails(tvId: Long, season: Int, episode: Int): JSONObject {
        return getJson(
            "/tv/$tvId/season/$season/episode/$episode",
            mapOf("append_to_response" to "credits,external_ids")
        )
    }

    fun getImageUrl(path: String?, size: String = "w500"): String? {
        if (path.isNullOrBlank()) return null
        return "${imageBaseUrl.trimEnd('/')}/$size/${path.trimStart('/')}"
    }

    suspend fun enrichMovieFromTitle(title: String, year: Int?): TmdbEnrichment? {
        val found = searchMovie(title, year) ?: return null
        val id = found.optLong("id", -1L).takeIf { it > 0 } ?: return null
        return mapMovieDetails(getMovieDetails(id))
    }

    suspend fun enrichTvFromTitle(title: String, year: Int?): TmdbEnrichment? {
        val found = searchTV(title, year) ?: return null
        val id = found.optLong("id", -1L).takeIf { it > 0 } ?: return null
        return mapTvDetails(getTVDetails(id))
    }

    suspend fun enrichByImdb(imdbId: String): TmdbEnrichment? {
        val found = findByIMDbId(imdbId) ?: return null
        val id = found.optLong("id", -1L).takeIf { it > 0 } ?: return null
        return when (found.optString("media_type")) {
            "movie" -> mapMovieDetails(getMovieDetails(id))
            "tv" -> mapTvDetails(getTVDetails(id))
            else -> null
        }
    }

    private suspend fun getJson(path: String, query: Map<String, String>): JSONObject {
        val response = executeWithRetry(path, query)
        return JSONObject(response)
    }

    private suspend fun executeWithRetry(path: String, query: Map<String, String>): String {
        var backoffMs = INITIAL_BACKOFF_MS
        repeat(MAX_RETRIES) { attempt ->
            try {
                return execute(path, query)
            } catch (e: IOException) {
                if (attempt == MAX_RETRIES - 1) throw e
                delay(backoffMs)
                backoffMs *= 2
            }
        }
        throw IOException("TMDB request failed after $MAX_RETRIES attempts")
    }

    private suspend fun execute(path: String, query: Map<String, String>): String {
        awaitRateLimitSlot()
        val urlBuilder = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}".toHttpUrl().newBuilder()
        urlBuilder.addQueryParameter("api_key", apiKey)
        query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val request = Request.Builder().url(urlBuilder.build()).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when {
                response.code == 401 -> throw IllegalStateException("TMDB API key is invalid or unauthorized")
                response.code == 404 -> return "{}"
                response.code == 429 -> throw IOException("TMDB rate limit exceeded")
                !response.isSuccessful -> throw IOException("TMDB request failed (${response.code})")
            }
            return body
        }
    }

    private suspend fun awaitRateLimitSlot() {
        while (true) {
            val waitMs = rateLimitMutex.withLock {
                val now = System.currentTimeMillis()
                while (requestTimesMs.isNotEmpty() && now - requestTimesMs.first() >= WINDOW_MS) {
                    requestTimesMs.removeFirst()
                }
                if (requestTimesMs.size < MAX_REQUESTS_PER_WINDOW) {
                    requestTimesMs.addLast(now)
                    0L
                } else {
                    (WINDOW_MS - (now - requestTimesMs.first())).coerceAtLeast(25L)
                }
            }
            if (waitMs <= 0L) return
            delay(waitMs)
        }
    }

    private fun mapMovieDetails(details: JSONObject): TmdbEnrichment {
        val keywords = details.optJSONObject("keywords")?.optJSONArray("keywords") ?: JSONArray()
        val credits = details.optJSONObject("credits") ?: JSONObject()
        val releaseDates = details.optJSONObject("release_dates")?.optJSONArray("results") ?: JSONArray()
        return TmdbEnrichment(
            tmdbId = details.optLong("id"),
            imdbId = details.optJSONObject("external_ids")?.optString("imdb_id")?.ifBlank { null },
            mediaType = "movie",
            title = details.optString("title").ifBlank { null },
            overview = details.optString("overview").ifBlank { null },
            tagline = details.optString("tagline").ifBlank { null },
            releaseDate = details.optString("release_date").ifBlank { null },
            runtimeMinutes = details.optInt("runtime").takeIf { it > 0 },
            genres = csv(details.optJSONArray("genres"), "name"),
            keywords = csv(keywords, "name"),
            voteAverage = details.optDouble("vote_average").takeIf { !it.isNaN() },
            voteCount = details.optInt("vote_count").takeIf { it > 0 },
            popularity = details.optDouble("popularity").takeIf { !it.isNaN() },
            posterUrl = getImageUrl(details.optString("poster_path").ifBlank { null }),
            backdropUrl = getImageUrl(details.optString("backdrop_path").ifBlank { null }),
            cast = topCast(credits),
            directors = crewNames(credits, "Director"),
            writers = crewNames(credits, "Writer", "Screenplay"),
            spokenLanguages = csv(details.optJSONArray("spoken_languages"), "english_name"),
            originCountry = csv(details.optJSONArray("origin_country")),
            status = details.optString("status").ifBlank { null },
            ageCertification = movieCertification(releaseDates)
        )
    }

    private fun mapTvDetails(details: JSONObject): TmdbEnrichment {
        val keywords = details.optJSONObject("keywords")?.optJSONArray("results") ?: JSONArray()
        val credits = details.optJSONObject("credits") ?: JSONObject()
        val contentRatings = details.optJSONObject("content_ratings")?.optJSONArray("results") ?: JSONArray()
        return TmdbEnrichment(
            tmdbId = details.optLong("id"),
            imdbId = details.optJSONObject("external_ids")?.optString("imdb_id")?.ifBlank { null },
            mediaType = "tv",
            title = details.optString("name").ifBlank { null },
            overview = details.optString("overview").ifBlank { null },
            tagline = details.optString("tagline").ifBlank { null },
            releaseDate = details.optString("first_air_date").ifBlank { null },
            runtimeMinutes = details.optJSONArray("episode_run_time")?.optInt(0)?.takeIf { it > 0 },
            genres = csv(details.optJSONArray("genres"), "name"),
            keywords = csv(keywords, "name"),
            voteAverage = details.optDouble("vote_average").takeIf { !it.isNaN() },
            voteCount = details.optInt("vote_count").takeIf { it > 0 },
            popularity = details.optDouble("popularity").takeIf { !it.isNaN() },
            posterUrl = getImageUrl(details.optString("poster_path").ifBlank { null }),
            backdropUrl = getImageUrl(details.optString("backdrop_path").ifBlank { null }),
            cast = topCast(credits),
            directors = crewNames(credits, "Director"),
            writers = crewNames(credits, "Writer", "Screenplay"),
            spokenLanguages = csv(details.optJSONArray("spoken_languages"), "english_name"),
            originCountry = csv(details.optJSONArray("origin_country")),
            status = details.optString("status").ifBlank { null },
            ageCertification = tvCertification(contentRatings),
            numberOfSeasons = details.optInt("number_of_seasons").takeIf { it >= 0 },
            numberOfEpisodes = details.optInt("number_of_episodes").takeIf { it >= 0 },
            episodeRunTime = csv(details.optJSONArray("episode_run_time"))
        )
    }

    private fun topCast(credits: JSONObject): String? =
        csv(credits.optJSONArray("cast"), "name", limit = 10)

    private fun crewNames(credits: JSONObject, vararg jobs: String): String? {
        val arr = credits.optJSONArray("crew") ?: return null
        val names = linkedSetOf<String>()
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            if (jobs.contains(row.optString("job"))) {
                row.optString("name").takeIf { it.isNotBlank() }?.let { names += it }
            }
        }
        return names.take(10).joinToString(", ").ifBlank { null }
    }

    private fun movieCertification(results: JSONArray): String? {
        for (i in 0 until results.length()) {
            val row = results.optJSONObject(i) ?: continue
            if (row.optString("iso_3166_1") != "US") continue
            val releases = row.optJSONArray("release_dates") ?: continue
            for (j in 0 until releases.length()) {
                val cert = releases.optJSONObject(j)?.optString("certification").orEmpty()
                if (cert.isNotBlank()) return cert
            }
        }
        return null
    }

    private fun tvCertification(results: JSONArray): String? {
        for (i in 0 until results.length()) {
            val row = results.optJSONObject(i) ?: continue
            if (row.optString("iso_3166_1") == "US") {
                return row.optString("rating").ifBlank { null }
            }
        }
        return null
    }

    private fun csv(arr: JSONArray?, field: String? = null, limit: Int = Int.MAX_VALUE): String? {
        if (arr == null) return null
        val values = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            if (values.size >= limit) break
            val value = when {
                field != null -> arr.optJSONObject(i)?.optString(field).orEmpty()
                else -> arr.opt(i)?.toString().orEmpty()
            }.trim()
            if (value.isNotBlank()) values += value
        }
        return values.joinToString(", ").ifBlank { null }
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.themoviedb.org/3"
        private const val DEFAULT_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
        private const val MAX_REQUESTS_PER_WINDOW = 40
        private const val WINDOW_MS = 10_000L
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 300L
    }
}

data class TmdbEnrichment(
    val tmdbId: Long,
    val imdbId: String?,
    val mediaType: String,
    val title: String?,
    val overview: String?,
    val tagline: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val genres: String?,
    val keywords: String?,
    val voteAverage: Double?,
    val voteCount: Int?,
    val popularity: Double?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val cast: String?,
    val directors: String?,
    val writers: String?,
    val spokenLanguages: String?,
    val originCountry: String?,
    val status: String?,
    val ageCertification: String?,
    val numberOfSeasons: Int? = null,
    val numberOfEpisodes: Int? = null,
    val episodeRunTime: String? = null
)
