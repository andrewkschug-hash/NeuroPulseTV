package com.grid.tv.feature.vod

import com.grid.tv.domain.model.SeriesDetail
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.domain.model.SeriesSeason

/**
 * Normalizes Xtream/EPG series episode titles for consistent language in the series UI.
 * Applied at mapping time before cache storage.
 */
object SeriesEpisodeTitleNormalizer {

    private val ENGLISH_CODES = setOf("EN", "US", "GB", "UK")
    private val FRENCH_HINT = Regex(
        """[àâäéèêëïîôùûüç]|(?i)\b(le|la|les|l'|d'|un|une|des|épisode)\b"""
    )

    fun normalizeSeriesDetail(detail: SeriesDetail): SeriesDetail {
        if (detail.seasons.isEmpty()) return detail
        return detail.copy(
            seasons = detail.seasons.map { season ->
                season.copy(
                    number = season.number,
                    episodes = normalizeSeasonEpisodes(season.number, season.episodes)
                )
            }
        )
    }

    fun normalizeSeasonEpisodes(seasonNumber: Int, episodes: List<SeriesEpisode>): List<SeriesEpisode> {
        if (episodes.isEmpty()) return episodes
        val deduped = dedupePreferEnglish(episodes)
        val displayTitles = deduped.map { episodeDisplayTitle(it) }
        if (hasMixedLanguages(deduped, displayTitles)) {
            return deduped.map { episode ->
                episode.copy(
                    title = genericEpisodeTitle(
                        seasonNumber = seasonNumber,
                        episodeNumber = episode.episodeNumber ?: 1
                    )
                )
            }
        }
        return deduped.mapIndexed { index, episode ->
            episode.copy(title = displayTitles[index])
        }
    }

    internal fun genericEpisodeTitle(seasonNumber: Int, episodeNumber: Int): String {
        val seasonLabel = seasonNumber.toString().padStart(2, '0')
        val episodeLabel = episodeNumber.toString().padStart(2, '0')
        return "S${seasonLabel}E$episodeLabel • Episode $episodeNumber"
    }

    private fun dedupePreferEnglish(episodes: List<SeriesEpisode>): List<SeriesEpisode> =
        episodes
            .groupBy { it.episodeNumber ?: it.id }
            .values
            .map { group -> group.minWith(compareBy({ englishPreferenceRank(it.title) }, { it.id })) }
            .sortedWith(compareBy({ it.episodeNumber ?: Int.MAX_VALUE }, { it.id }))

    private fun englishPreferenceRank(title: String): Int {
        val code = parseVodContentLanguageCode(title)?.uppercase()
        return when {
            code != null && code in ENGLISH_CODES -> 0
            code == null -> 1
            else -> 2
        }
    }

    private fun episodeDisplayTitle(episode: SeriesEpisode): String {
        val stripped = stripVodLanguageMarkers(episode.title)
        return stripped.ifBlank { episode.title.trim() }.ifBlank { "Episode ${episode.episodeNumber ?: episode.id}" }
    }

    private fun hasMixedLanguages(episodes: List<SeriesEpisode>, displayTitles: List<String>): Boolean {
        val explicitCodes = episodes.mapNotNull { parseVodContentLanguageCode(it.title) }.distinct()
        if (explicitCodes.size > 1) return true

        val implicitFrench = displayTitles.count { looksFrench(it) }
        val implicitNonFrench = displayTitles.size - implicitFrench
        if (implicitFrench > 0 && implicitNonFrench > 0) return true

        val hasExplicitEnglish = explicitCodes.any { it.uppercase() in ENGLISH_CODES }
        val hasExplicitNonEnglish = explicitCodes.any { it.uppercase() !in ENGLISH_CODES }
        if (hasExplicitEnglish && hasExplicitNonEnglish) return true

        if (hasExplicitNonEnglish && implicitNonFrench > 0 && implicitFrench > 0) return true

        return false
    }

    private fun looksFrench(title: String): Boolean = FRENCH_HINT.containsMatchIn(title)
}
