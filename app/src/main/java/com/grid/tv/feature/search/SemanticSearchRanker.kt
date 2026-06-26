package com.grid.tv.feature.search

import com.grid.tv.domain.model.VodItem
import kotlin.math.sqrt

/**
 * Foundation for semantic-style search ranking using on-device content vectors
 * stored in [com.grid.tv.data.db.entity.TitleEnrichmentEntity.contentVector].
 */
object SemanticSearchRanker {
    fun tokenize(query: String): Set<String> =
        query.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .toSet()

    fun scoreItem(query: String, title: String, overview: String?, genres: String?): Float {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return 0f
        val haystack = buildString {
            append(title.lowercase())
            overview?.let { append(' ').append(it.lowercase()) }
            genres?.let { append(' ').append(it.lowercase()) }
        }
        val hits = tokens.count { haystack.contains(it) }
        return hits.toFloat() / tokens.size
    }

    fun rankMovies(query: String, items: List<VodItem>, overviews: Map<Long, String?>): List<VodItem> {
        if (query.isBlank()) return items
        return items
            .map { item ->
                val score = scoreItem(query, item.title, overviews[item.streamId], item.genre)
                item to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA <= 0f || normB <= 0f) return 0f
        return dot / (sqrt(normA) * sqrt(normB))
    }

    fun parseVector(serialized: String?): FloatArray? {
        if (serialized.isNullOrBlank()) return null
        return serialized.split(',')
            .mapNotNull { it.trim().toFloatOrNull() }
            .toFloatArray()
            .takeIf { it.isNotEmpty() }
    }
}
