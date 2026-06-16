package com.grid.tv.feature.recommendation

import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodItem
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import kotlin.math.sqrt

private const val VECTOR_SIZE = 68
private const val TASTE_WEIGHT = 0.30
private const val NARRATIVE_WEIGHT = 0.35

data class ScoredVod(
    val item: VodItem,
    val score: Double
)

// TODO: replace with server-side equivalent when scaling
class TasteGenomeEngine {

    fun topPicks(
        catalog: List<VodItem>,
        continueWatching: List<ContinueWatchingItem>,
        enrichmentByProviderKey: Map<String, TitleEnrichmentEntity> = emptyMap(),
        limit: Int = 20
    ): List<VodItem> {
        if (catalog.isEmpty()) return emptyList()
        val tasteProfile = buildTasteProfile(continueWatching, enrichmentByProviderKey)
        return catalog
            .asSequence()
            .map { item ->
                ScoredVod(item, score(item, tasteProfile, continueWatching, enrichmentByProviderKey, exploreBoost = false))
            }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.item }
            .toList()
    }

    fun trendingNow(
        catalog: List<VodItem>,
        enrichmentByProviderKey: Map<String, TitleEnrichmentEntity> = emptyMap(),
        limit: Int = 20
    ): List<VodItem> {
        if (catalog.isEmpty()) return emptyList()
        return catalog
            .asSequence()
            .map { item ->
                val enrichment = enrichmentForItem(item, enrichmentByProviderKey)
                val popularity = enrichment?.popularity ?: 0.0
                val rating = enrichment?.rating ?: parseProviderRating(item.rating)
                val recency = item.addedEpochSec?.toDouble() ?: 0.0
                val score = popularity * 0.55 + rating * 0.30 + recency * 0.000_000_15
                ScoredVod(item, score)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.item }
            .toList()
    }

    fun somethingDifferent(
        catalog: List<VodItem>,
        continueWatching: List<ContinueWatchingItem>,
        enrichmentByProviderKey: Map<String, TitleEnrichmentEntity> = emptyMap(),
        limit: Int = 20
    ): List<VodItem> {
        if (catalog.isEmpty()) return emptyList()
        val tasteProfile = buildTasteProfile(continueWatching, enrichmentByProviderKey)
        return catalog
            .asSequence()
            .map { item ->
                ScoredVod(item, score(item, tasteProfile, continueWatching, enrichmentByProviderKey, exploreBoost = true))
            }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.item }
            .toList()
    }

    private data class TasteProfile(
        val genres: Set<String>,
        val keywords: Set<String>,
        val cast: Set<String>,
        val directors: Set<String>
    )

    private fun score(
        item: VodItem,
        tasteProfile: TasteProfile,
        continueWatching: List<ContinueWatchingItem>,
        enrichmentByProviderKey: Map<String, TitleEnrichmentEntity>,
        exploreBoost: Boolean
    ): Double {
        val enrichment = enrichmentForItem(item, enrichmentByProviderKey)
        val narrativeVector = buildNarrativeVector(item, enrichment)
        val profileVector = buildProfileVector(tasteProfile)
        val tasteSimilarity = cosineSimilarity(profileVector, narrativeVector).coerceIn(0.0, 1.0)
        val narrativeSimilarity = keywordNarrativeSimilarity(item, enrichment, continueWatching)

        val base = (tasteSimilarity * TASTE_WEIGHT) + (narrativeSimilarity * NARRATIVE_WEIGHT)
        val directorBonus = if (matchesDirector(item, enrichment, tasteProfile)) 0.10 else 0.0
        val castBonus = if (matchesCast(item, enrichment, tasteProfile)) 0.10 else 0.0
        val genreBonus = genreOverlapBonus(item, enrichment, tasteProfile)
        val keywordBonus = keywordBonus(item, enrichment, tasteProfile)

        val affinity = base + directorBonus + castBonus + genreBonus + keywordBonus
        return if (exploreBoost) {
            (1.0 - tasteSimilarity) * 0.45 + narrativeSimilarity * 0.35 + genreBonus + keywordBonus
        } else {
            affinity
        }
    }

    private fun buildTasteProfile(
        continueWatching: List<ContinueWatchingItem>,
        enrichmentByProviderKey: Map<String, TitleEnrichmentEntity>
    ): TasteProfile {
        val genres = linkedSetOf<String>()
        val keywords = linkedSetOf<String>()
        val cast = linkedSetOf<String>()
        val directors = linkedSetOf<String>()

        continueWatching.forEach { item ->
            val key = TitleEnrichmentRepository.continueWatchingKey(item)
            val enrichment = enrichmentByProviderKey[key]
            genres += tokenizeCsv(enrichment?.genres ?: "")
            keywords += tokenizeCsv(enrichment?.keywords ?: "")
            cast += tokenizeCsv(enrichment?.cast ?: "")
            directors += tokenizeCsv(enrichment?.directors ?: "")
            genres += tokenize(item.title)
        }

        return TasteProfile(genres, keywords, cast, directors)
    }

    private fun buildProfileVector(profile: TasteProfile): DoubleArray {
        val seed = (profile.genres + profile.keywords + profile.cast + profile.directors).joinToString("|")
        return seededVector(seed.ifBlank { "default" })
    }

    private fun buildNarrativeVector(item: VodItem, enrichment: TitleEnrichmentEntity?): DoubleArray {
        val seed = buildString {
            append(item.title, "|", item.plot ?: "", "|", item.genre ?: "")
            enrichment?.genres?.let { append("|", it) }
            enrichment?.keywords?.let { append("|", it) }
            enrichment?.cast?.let { append("|", it) }
        }
        return seededVector(seed)
    }

    private fun enrichmentForItem(
        item: VodItem,
        enrichmentByProviderKey: Map<String, TitleEnrichmentEntity>
    ): TitleEnrichmentEntity? {
        if (item.playlistId <= 0L) return null
        return enrichmentByProviderKey[TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId)]
    }

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

    private fun keywordNarrativeSimilarity(
        item: VodItem,
        enrichment: TitleEnrichmentEntity?,
        continueWatching: List<ContinueWatchingItem>
    ): Double {
        val pool = continueWatching.joinToString(" ") { "${it.title} ${it.subtitle}" }.lowercase()
        if (pool.isBlank()) return 0.0
        val words = tokenize("${item.title} ${item.plot ?: ""} ${item.genre ?: ""} ${enrichment?.overview ?: ""}")
        if (words.isEmpty()) return 0.0
        val hits = words.count { pool.contains(it) }
        return (hits.toDouble() / words.size).coerceIn(0.0, 1.0)
    }

    private fun matchesDirector(
        item: VodItem,
        enrichment: TitleEnrichmentEntity?,
        profile: TasteProfile
    ): Boolean {
        val directors = tokenizeCsv(enrichment?.directors ?: item.director ?: return false)
        return directors.any { it in profile.directors }
    }

    private fun matchesCast(
        item: VodItem,
        enrichment: TitleEnrichmentEntity?,
        profile: TasteProfile
    ): Boolean {
        val cast = tokenizeCsv(enrichment?.cast ?: item.cast ?: return false)
        return cast.any { it in profile.cast }
    }

    private fun genreOverlapBonus(
        item: VodItem,
        enrichment: TitleEnrichmentEntity?,
        profile: TasteProfile
    ): Double {
        val genres = tokenizeCsv(enrichment?.genres ?: item.genre ?: return 0.0)
        if (genres.isEmpty() || profile.genres.isEmpty()) return 0.0
        val overlap = genres.count { it in profile.genres }
        return (overlap.coerceAtMost(3) * 0.04).coerceIn(0.0, 0.12)
    }

    private fun keywordBonus(
        item: VodItem,
        enrichment: TitleEnrichmentEntity?,
        profile: TasteProfile
    ): Double {
        val keywords = tokenizeCsv(enrichment?.keywords ?: "") +
            tokenize("${item.title} ${item.plot ?: ""}")
        if (keywords.isEmpty()) return 0.0
        val matches = keywords.count { it in profile.keywords }
        return (matches.coerceAtMost(4) * 0.02).coerceIn(0.0, 0.08)
    }

    private fun tokenizeCsv(raw: String): Set<String> =
        raw.split(',')
            .map { it.trim().lowercase() }
            .filter { it.length >= 2 }
            .toSet()

    private fun tokenize(raw: String): Set<String> =
        raw.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .toSet()

    private fun parseProviderRating(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        return raw.trim().toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: 0.0
    }
}
