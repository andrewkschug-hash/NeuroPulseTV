package com.grid.tv.data.network.tmdb

import kotlin.math.abs
import kotlin.math.ln
import org.json.JSONArray
import org.json.JSONObject

/** Scores TMDB search hits and picks the best candidate instead of blindly using results[0]. */
object TmdbMatchRanker {

    const val MIN_ACCEPT_SCORE = 0.42

    enum class MediaType {
        MOVIE,
        TV,
        MULTI,
    }

    fun pickBest(
        queryTitle: String,
        queryYear: Int?,
        results: JSONArray,
        mediaType: MediaType,
    ): JSONObject? {
        if (results.length() == 0) return null
        val normalizedQuery = TmdbTitleNormalizer.normalizeForComparison(queryTitle)
        var best: JSONObject? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in 0 until results.length()) {
            val row = results.optJSONObject(i) ?: continue
            if (mediaType == MediaType.MULTI) {
                val type = row.optString("media_type")
                if (type != "movie" && type != "tv") continue
            }
            val score = scoreCandidate(normalizedQuery, queryYear, row, mediaType)
            if (score > bestScore) {
                bestScore = score
                best = row
            }
        }
        return best?.takeIf { bestScore >= MIN_ACCEPT_SCORE }
    }

    fun scoreCandidate(
        normalizedQuery: String,
        queryYear: Int?,
        result: JSONObject,
        mediaType: MediaType,
    ): Double {
        val resolvedType = when (mediaType) {
            MediaType.MOVIE -> "movie"
            MediaType.TV -> "tv"
            MediaType.MULTI -> result.optString("media_type")
        }
        val titleField = if (resolvedType == "tv") "name" else "title"
        val dateField = if (resolvedType == "tv") "first_air_date" else "release_date"
        val candidateTitle = result.optString(titleField)
        val titleScore = titleSimilarity(normalizedQuery, TmdbTitleNormalizer.normalizeForComparison(candidateTitle))
        val resultYear = TmdbYearParser.yearFromTmdbDate(result.optString(dateField))
        val yearScore = yearMatchScore(queryYear, resultYear)
        val popularity = result.optDouble("popularity")
        val popularityScore = popularityBoost(popularity)
        return titleScore * 0.70 + yearScore * 0.20 + popularityScore * 0.10
    }

    internal fun titleSimilarity(normalizedQuery: String, normalizedCandidate: String): Double {
        if (normalizedQuery.isEmpty() || normalizedCandidate.isEmpty()) return 0.0
        if (normalizedQuery == normalizedCandidate) return 1.0
        if (normalizedQuery.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedQuery)) {
            return 0.88
        }
        val queryTokens = normalizedQuery.split(' ').filter { it.isNotBlank() }.toSet()
        val candidateTokens = normalizedCandidate.split(' ').filter { it.isNotBlank() }.toSet()
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
        val intersection = queryTokens.intersect(candidateTokens).size
        val union = queryTokens.union(candidateTokens).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    internal fun yearMatchScore(queryYear: Int?, resultYear: Int?): Double {
        if (queryYear == null || resultYear == null) return 0.55
        return when (abs(queryYear - resultYear)) {
            0 -> 1.0
            1 -> 0.65
            2 -> 0.35
            else -> 0.0
        }
    }

    internal fun popularityBoost(popularity: Double): Double {
        if (popularity.isNaN() || popularity <= 0.0) return 0.0
        return (ln(popularity + 1.0) / 12.0).coerceIn(0.0, 1.0)
    }
}
