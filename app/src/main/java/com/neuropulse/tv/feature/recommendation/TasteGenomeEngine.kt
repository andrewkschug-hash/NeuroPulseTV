package com.neuropulse.tv.feature.recommendation

import com.neuropulse.tv.domain.model.ContinueWatchingItem
import com.neuropulse.tv.domain.model.VodItem
import kotlin.math.sqrt

private const val VECTOR_SIZE = 68
private const val TASTE_WEIGHT = 0.30
private const val NARRATIVE_WEIGHT = 0.35

data class ScoredVod(
    val item: VodItem,
    val score: Double
)

class TasteGenomeEngine {

    fun topPicks(
        catalog: List<VodItem>,
        continueWatching: List<ContinueWatchingItem>,
        limit: Int = 20
    ): List<VodItem> {
        if (catalog.isEmpty()) return emptyList()
        val tasteVector = buildProfileTasteVector(continueWatching)
        return catalog
            .asSequence()
            .map { item -> ScoredVod(item, score(item, tasteVector, continueWatching, exploreBoost = false)) }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.item }
            .toList()
    }

    fun somethingDifferent(
        catalog: List<VodItem>,
        continueWatching: List<ContinueWatchingItem>,
        limit: Int = 20
    ): List<VodItem> {
        if (catalog.isEmpty()) return emptyList()
        val tasteVector = buildProfileTasteVector(continueWatching)
        return catalog
            .asSequence()
            .map { item -> ScoredVod(item, score(item, tasteVector, continueWatching, exploreBoost = true)) }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.item }
            .toList()
    }

    private fun score(
        item: VodItem,
        profileTaste: DoubleArray,
        continueWatching: List<ContinueWatchingItem>,
        exploreBoost: Boolean
    ): Double {
        val narrativeVector = buildNarrativeVector(item)
        val tasteSimilarity = cosineSimilarity(profileTaste, narrativeVector).coerceIn(0.0, 1.0)
        val narrativeSimilarity = keywordNarrativeSimilarity(item, continueWatching)

        val base = (tasteSimilarity * TASTE_WEIGHT) + (narrativeSimilarity * NARRATIVE_WEIGHT)
        val directorBonus = if (matchesDirector(item, continueWatching)) 0.08 else 0.0
        val castBonus = if (matchesCast(item, continueWatching)) 0.08 else 0.0
        val keywordBonus = keywordBonus(item, continueWatching)
        val subGenreBonus = if (matchesSubGenre(item, continueWatching)) 0.05 else 0.0

        val affinity = base + directorBonus + castBonus + keywordBonus + subGenreBonus
        return if (exploreBoost) {
            // Favor adjacent but less similar items for "Something Different".
            (1.0 - tasteSimilarity) * 0.45 + narrativeSimilarity * 0.35 + keywordBonus + subGenreBonus
        } else {
            affinity
        }
    }

    private fun buildProfileTasteVector(continueWatching: List<ContinueWatchingItem>): DoubleArray {
        if (continueWatching.isEmpty()) return DoubleArray(VECTOR_SIZE) { 1.0 / VECTOR_SIZE }
        val accumulator = DoubleArray(VECTOR_SIZE)
        continueWatching.forEach { item ->
            val seed = "${item.title}|${item.contentType}|${item.subtitle ?: ""}"
            val vector = seededVector(seed)
            for (i in 0 until VECTOR_SIZE) {
                accumulator[i] += vector[i]
            }
        }
        val norm = sqrt(accumulator.sumOf { it * it }).takeIf { it > 0.0 } ?: 1.0
        return DoubleArray(VECTOR_SIZE) { idx -> accumulator[idx] / norm }
    }

    private fun buildNarrativeVector(item: VodItem): DoubleArray =
        seededVector("${item.title}|${item.plot ?: ""}|${item.genre ?: ""}")

    private fun seededVector(seed: String): DoubleArray {
        val vector = DoubleArray(VECTOR_SIZE)
        val normalized = seed.lowercase().ifBlank { "unknown" }
        normalized.forEachIndexed { index, ch ->
            val slot = (ch.code + index * 13) % VECTOR_SIZE
            vector[slot] += 1.0
        }
        val norm = sqrt(vector.sumOf { it * it }).takeIf { it > 0.0 } ?: 1.0
        return DoubleArray(VECTOR_SIZE) { idx -> vector[idx] / norm }
    }

    private fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0
        var aNorm = 0.0
        var bNorm = 0.0
        for (i in 0 until VECTOR_SIZE) {
            dot += a[i] * b[i]
            aNorm += a[i] * a[i]
            bNorm += b[i] * b[i]
        }
        if (aNorm <= 0.0 || bNorm <= 0.0) return 0.0
        return dot / (sqrt(aNorm) * sqrt(bNorm))
    }

    private fun keywordNarrativeSimilarity(item: VodItem, continueWatching: List<ContinueWatchingItem>): Double {
        val pool = continueWatching.joinToString(" ") { "${it.title} ${it.subtitle ?: ""}" }.lowercase()
        if (pool.isBlank()) return 0.0
        val words = tokenize("${item.title} ${item.plot ?: ""} ${item.genre ?: ""}")
        if (words.isEmpty()) return 0.0
        val hits = words.count { pool.contains(it) }
        return (hits.toDouble() / words.size.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun matchesDirector(item: VodItem, continueWatching: List<ContinueWatchingItem>): Boolean {
        val director = item.director?.trim()?.lowercase().orEmpty()
        if (director.isBlank()) return false
        return continueWatching.any { it.subtitle?.lowercase()?.contains(director) == true }
    }

    private fun matchesCast(item: VodItem, continueWatching: List<ContinueWatchingItem>): Boolean {
        val castTokens = tokenize(item.cast ?: return false)
        if (castTokens.isEmpty()) return false
        val pool = continueWatching.joinToString(" ") { "${it.title} ${it.subtitle ?: ""}" }.lowercase()
        return castTokens.any { pool.contains(it) }
    }

    private fun matchesSubGenre(item: VodItem, continueWatching: List<ContinueWatchingItem>): Boolean {
        val itemGenre = item.genre?.lowercase()?.trim().orEmpty()
        if (itemGenre.isBlank()) return false
        return continueWatching.any { it.subtitle?.lowercase()?.contains(itemGenre) == true }
    }

    private fun keywordBonus(item: VodItem, continueWatching: List<ContinueWatchingItem>): Double {
        val itemKeywords = tokenize("${item.title} ${item.plot ?: ""} ${item.genre ?: ""}")
        if (itemKeywords.isEmpty()) return 0.0
        val pool = continueWatching.joinToString(" ") { "${it.title} ${it.subtitle ?: ""}" }.lowercase()
        val matches = itemKeywords.count { pool.contains(it) }
        return (matches.coerceAtMost(4) * 0.02).coerceIn(0.0, 0.08)
    }

    private fun tokenize(raw: String): Set<String> =
        raw.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .toSet()
}

