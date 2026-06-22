package com.grid.tv.feature.recommendation

import com.grid.tv.data.db.entity.FeaturedBannerStatsEntity
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodItem
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import java.util.Calendar
import kotlin.math.absoluteValue

data class FeaturedSelection(
    val carousel: List<VodItem>,
    val heroIndex: Int
)

/**
 * Scores and selects curated hero / featured carousel items from the local VOD catalog.
 *
 * Priority tiers:
 * 1. Feed-tagged featured / trending items (category labels or metadata markers)
 * 2. Top recent releases when no explicit curation tags exist
 * 3. Weighted by stream quality, genre affinity, and banner fatigue signals
 */
class FeaturedContentRanker(
    private val clock: Calendar = Calendar.getInstance()
) {
    fun selectFeaturedContent(
        catalog: List<VodItem>,
        categories: List<VodCategory>,
        enrichmentByProviderKey: Map<String, TitleEnrichmentEntity>,
        genreAffinities: Map<String, Int>,
        bannerStats: Map<String, FeaturedBannerStatsEntity>,
        sessionSeed: Long,
        carouselSize: Int = 5,
        randomPoolSize: Int = 10
    ): FeaturedSelection {
        if (catalog.isEmpty()) return FeaturedSelection(emptyList(), 0)

        val categoryNames = categories.associate { "${it.playlistId}_${it.id}" to it.name }
        val currentYear = clock.get(Calendar.YEAR)

        val eligible = catalog.filter { item ->
            isEligibleForFeatured(
                item = item,
                enrichment = enrichmentForItem(item, enrichmentByProviderKey)
            )
        }
        if (eligible.isEmpty()) return FeaturedSelection(emptyList(), 0)

        val curatedPool = eligible.filter { item ->
            isExplicitlyCurated(
                item = item,
                categoryName = categoryName(item, categoryNames),
                enrichment = enrichmentForItem(item, enrichmentByProviderKey)
            )
        }

        val candidatePool = if (curatedPool.isNotEmpty()) {
            curatedPool
        } else {
            eligible
                .sortedByDescending { releaseYear(it, enrichmentForItem(it, enrichmentByProviderKey)) ?: 0 }
                .take(randomPoolSize)
        }

        val scored = candidatePool
            .map { item ->
                val enrichment = enrichmentForItem(item, enrichmentByProviderKey)
                ScoredFeatured(
                    item = item,
                    score = scoreFeaturedItem(
                        item = item,
                        enrichment = enrichment,
                        categoryName = categoryName(item, categoryNames),
                        genreAffinities = genreAffinities,
                        bannerStats = bannerStats,
                        currentYear = currentYear
                    )
                )
            }
            .sortedByDescending { it.score }

        if (scored.isEmpty()) return FeaturedSelection(emptyList(), 0)

        val randomPool = scored.take(randomPoolSize.coerceAtLeast(1))
        val sessionHero = randomPool[stableRandomIndex(sessionSeed, randomPool.size)].item

        val carousel = buildList {
            add(sessionHero)
            scored.asSequence()
                .map { it.item }
                .filter { it != sessionHero }
                .take((carouselSize - 1).coerceAtLeast(0))
                .forEach(::add)
        }

        return FeaturedSelection(carousel = carousel, heroIndex = 0)
    }

    fun isEligibleForFeatured(item: VodItem, enrichment: TitleEnrichmentEntity?): Boolean {
        val poster = enrichment?.posterUrl?.takeIf { it.isNotBlank() } ?: item.posterUrl?.takeIf { it.isNotBlank() }
        if (poster.isNullOrBlank()) return false
        if (item.streamUrl.isBlank()) return false
        if (looksLikePoorStream(item.title)) return false
        return true
    }

    fun isExplicitlyCurated(
        item: VodItem,
        categoryName: String?,
        enrichment: TitleEnrichmentEntity?
    ): Boolean {
        if (categoryLooksCurated(categoryName)) return true
        val haystack = buildString {
            append(item.title, ' ')
            append(item.genre.orEmpty(), ' ')
            append(item.plot.orEmpty(), ' ')
            enrichment?.keywords?.let { append(it, ' ') }
        }.lowercase()
        return CURATED_MARKERS.any { marker -> haystack.contains(marker) }
    }

    private fun scoreFeaturedItem(
        item: VodItem,
        enrichment: TitleEnrichmentEntity?,
        categoryName: String?,
        genreAffinities: Map<String, Int>,
        bannerStats: Map<String, FeaturedBannerStatsEntity>,
        currentYear: Int
    ): Double {
        var score = 0.0

        if (isExplicitlyCurated(item, categoryName, enrichment)) {
            score += EXPLICIT_CURATION_WEIGHT
        }

        when (val year = releaseYear(item, enrichment)) {
            currentYear -> score += CURRENT_YEAR_WEIGHT
            currentYear - 1 -> score += RECENT_YEAR_WEIGHT
            in (currentYear - 3)..currentYear -> score += 15.0
        }

        score += streamQualityBonus(item.title)
        score += genreAffinityBonus(item, enrichment, genreAffinities)
        score += bannerFatiguePenalty(item, bannerStats)
        score += (item.addedEpochSec ?: 0L) * ADDED_EPOCH_WEIGHT
        return score
    }

    private fun genreAffinityBonus(
        item: VodItem,
        enrichment: TitleEnrichmentEntity?,
        genreAffinities: Map<String, Int>
    ): Double {
        val genres = genresForItem(item, enrichment)
        if (genres.isEmpty() || genreAffinities.isEmpty()) return 0.0
        return genres.sumOf { genre -> (genreAffinities[genre] ?: 0) * GENRE_AFFINITY_WEIGHT }
    }

    private fun bannerFatiguePenalty(
        item: VodItem,
        bannerStats: Map<String, FeaturedBannerStatsEntity>
    ): Double {
        val stats = bannerStats[contentKey(item)] ?: return 0.0
        if (stats.clickCount > 0) return 0.0
        if (stats.impressionCount < BANNER_FATIGUE_IMPRESSIONS) return 0.0
        return BANNER_FATIGUE_PENALTY
    }

    private fun genresForItem(item: VodItem, enrichment: TitleEnrichmentEntity?): List<String> {
        val raw = enrichment?.genres?.takeIf { it.isNotBlank() } ?: item.genre.orEmpty()
        return raw.split(',')
            .map { it.trim().lowercase() }
            .filter { it.length >= 2 }
    }

    private fun categoryName(item: VodItem, categoryNames: Map<String, String>): String? {
        val categoryId = item.categoryId ?: return null
        return categoryNames["${item.playlistId}_$categoryId"]
    }

    private fun enrichmentForItem(
        item: VodItem,
        enrichmentByProviderKey: Map<String, TitleEnrichmentEntity>
    ): TitleEnrichmentEntity? {
        if (item.playlistId <= 0L) return null
        return enrichmentByProviderKey[TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId)]
    }

    private fun releaseYear(item: VodItem, enrichment: TitleEnrichmentEntity?): Int? {
        enrichment?.releaseDate?.take(4)?.toIntOrNull()?.let { return it }
        return YEAR_REGEX.find(item.title)?.value?.toIntOrNull()
    }

    private fun streamQualityBonus(title: String): Double {
        val upper = title.uppercase()
        var bonus = 0.0
        if (upper.contains("4K") || upper.contains("UHD") || upper.contains("2160P")) bonus += 30.0
        if (upper.contains("5.1") || upper.contains("DOLBY") || upper.contains("ATMOS")) bonus += 15.0
        if (upper.contains("FHD") || upper.contains("1080P")) bonus += 5.0
        return bonus
    }

    private fun looksLikePoorStream(title: String): Boolean {
        val lower = title.lowercase()
        return POOR_STREAM_MARKERS.any { marker -> lower.contains(marker) }
    }

    private fun categoryLooksCurated(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return lower.contains("featured") ||
            lower.contains("trending") ||
            lower.contains("spotlight") ||
            lower.contains("editor")
    }

    private fun contentKey(item: VodItem): String = "${item.playlistId}_${item.streamId}"

    private fun stableRandomIndex(seed: Long, size: Int): Int {
        if (size <= 1) return 0
        return ((seed xor (seed ushr 33)).absoluteValue % size).toInt()
    }

    private data class ScoredFeatured(
        val item: VodItem,
        val score: Double
    )

    private companion object {
        private val YEAR_REGEX = Regex("\\b(19\\d{2}|20\\d{2})\\b")
        private val CURATED_MARKERS = listOf(
            "tvg-featured",
            "featured",
            "editor's pick",
            "editors pick",
            "spotlight",
            "trending now",
            "trending"
        )
        private val POOR_STREAM_MARKERS = listOf(
            " cam",
            "cam)",
            "hdts",
            " ts ",
            "telecine",
            "dvdscr",
            " hdcam"
        )

        private const val EXPLICIT_CURATION_WEIGHT = 1_000.0
        private const val CURRENT_YEAR_WEIGHT = 80.0
        private const val RECENT_YEAR_WEIGHT = 40.0
        private const val GENRE_AFFINITY_WEIGHT = 4.0
        private const val ADDED_EPOCH_WEIGHT = 0.000_000_15
        private const val BANNER_FATIGUE_IMPRESSIONS = 5
        private const val BANNER_FATIGUE_PENALTY = -250.0
    }
}
