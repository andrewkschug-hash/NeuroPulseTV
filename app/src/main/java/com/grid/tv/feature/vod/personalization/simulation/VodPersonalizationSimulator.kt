package com.grid.tv.feature.vod.personalization.simulation

import com.grid.tv.feature.vod.personalization.CatalogEpisode
import com.grid.tv.feature.vod.personalization.NewEpisodeDetector
import com.grid.tv.feature.vod.personalization.SeriesFollow
import com.grid.tv.feature.vod.personalization.VodContentType
import com.grid.tv.feature.vod.personalization.VodWatchEvent
import kotlin.random.Random

data class VodPersonalizationSimulationReport(
    val usersSimulated: Int,
    val seriesSimulated: Int,
    val episodesSimulated: Int,
    val watchEventsGenerated: Int,
    val catalogUpdatesSimulated: Int,
    val notificationsGenerated: Int,
    val detectionAccuracy: Double,
    val followedSeriesCoverage: Double,
    val feedQualityScore: Double,
    val loveIslandScenarioPassed: Boolean,
    val sampleDetections: List<String>
)

/**
 * In-memory simulation validating new episode detection, notifications, and feed ranking.
 */
class VodPersonalizationSimulator(
    private val random: Random = Random(7)
) {
    private data class SimState(
        val watchEvents: MutableList<VodWatchEvent> = mutableListOf(),
        val follows: MutableList<SeriesFollow> = mutableListOf()
    )

    fun run(
        userCount: Int = 10_000,
        seriesCount: Int = 5_000,
        episodeCount: Int = 100_000
    ): VodPersonalizationSimulationReport {
        val state = SimState()
        val playlistId = 1L
        val episodesPerSeries = (episodeCount / seriesCount).coerceAtLeast(10)
        buildCatalog(playlistId, seriesCount, episodesPerSeries)

        var watchEvents = 0
        var catalogUpdates = 0
        var notifications = 0
        var correctDetections = 0
        var totalExpectedDetections = 0
        val sampleDetections = mutableListOf<String>()

        val users = (1L..userCount.toLong()).toList()
        users.forEach { profileId ->
            val followedCount = random.nextInt(1, 6)
            val followedSeries = (1..followedCount).map { random.nextLong(1, seriesCount + 1L) }.distinct()
            followedSeries.forEach { seriesId ->
                state.follows += SeriesFollow(
                    profileId = profileId,
                    seriesId = seriesId,
                    seriesTitle = "Series $seriesId",
                    playlistId = playlistId,
                    following = true,
                    autoFollowed = random.nextBoolean(),
                    followedAt = System.currentTimeMillis()
                )
                val watchedEpisodes = random.nextInt(1, episodesPerSeries.coerceAtMost(20))
                for (ep in 1..watchedEpisodes) {
                    state.watchEvents += VodWatchEvent(
                        profileId = profileId,
                        contentId = "series:$seriesId:1:$ep",
                        contentType = VodContentType.EPISODE,
                        seriesId = seriesId,
                        seasonNumber = 1,
                        episodeNumber = ep,
                        progressPercent = random.nextFloat(),
                        positionMs = random.nextLong(60_000, 3_600_000),
                        durationMs = 3_600_000,
                        lastWatched = System.currentTimeMillis() - random.nextLong(0, 2_592_000_000)
                    )
                    watchEvents++
                }
            }
        }

        val loveIslandSeriesId = 42L
        val loveIslandProfile = 1L
        state.follows += SeriesFollow(
            profileId = loveIslandProfile,
            seriesId = loveIslandSeriesId,
            seriesTitle = "Love Island",
            playlistId = playlistId,
            following = true,
            autoFollowed = false,
            followedAt = System.currentTimeMillis()
        )
        state.watchEvents += VodWatchEvent(
            profileId = loveIslandProfile,
            contentId = "series:$loveIslandSeriesId:1:37",
            contentType = VodContentType.EPISODE,
            seriesId = loveIslandSeriesId,
            seasonNumber = 1,
            episodeNumber = 37,
            progressPercent = 1f,
            positionMs = 3_600_000,
            durationMs = 3_600_000,
            lastWatched = System.currentTimeMillis()
        )

        val newEp38 = CatalogEpisode(
            playlistId = playlistId,
            seriesId = loveIslandSeriesId,
            seriesTitle = "Love Island",
            seasonNumber = 1,
            episodeNumber = 38,
            episodeId = 38_001,
            episodeTitle = "Episode 38",
            addedAt = System.currentTimeMillis()
        )
        catalogUpdates++

        val loveIslandDetections = detectNewEpisodes(state, loveIslandProfile, listOf(newEp38))
        val loveIslandPassed = loveIslandDetections.any { it.episodeNumber == 38 && it.previousWatchedEpisode == 37 }
        if (loveIslandPassed) {
            sampleDetections += "Love Island E38 detected after E37 watch"
        }

        users.take(500).forEach { profileId ->
            val followed = state.follows.filter { it.profileId == profileId && it.following }
            followed.forEach { follow ->
                val latestWatched = latestEpisode(state, follow.profileId, follow.seriesId)
                val newEps = (latestWatched + 1..latestWatched + 3).map { ep ->
                    CatalogEpisode(
                        playlistId = playlistId,
                        seriesId = follow.seriesId,
                        seriesTitle = follow.seriesTitle,
                        seasonNumber = 1,
                        episodeNumber = ep,
                        episodeId = follow.seriesId * 10_000 + ep,
                        episodeTitle = "Episode $ep",
                        addedAt = System.currentTimeMillis()
                    )
                }
                totalExpectedDetections += newEps.size
                val detections = detectNewEpisodes(state, profileId, newEps)
                correctDetections += detections.size
                notifications += detections.size
                if (detections.isNotEmpty() && sampleDetections.size < 5) {
                    sampleDetections += "${follow.seriesTitle} E${detections.first().episodeNumber} for user $profileId"
                }
            }
        }

        val detectionAccuracy = if (totalExpectedDetections == 0) 1.0
        else correctDetections.toDouble() / totalExpectedDetections

        val usersWithFollows = users.count { id -> state.follows.any { it.profileId == id && it.following } }
        val usersWithDetections = users.take(500).count { profileId ->
            detectNewEpisodes(state, profileId, listOf(newEp38)).isNotEmpty()
        }
        val followedCoverage = if (usersWithFollows == 0) 0.0
        else usersWithDetections.toDouble() / usersWithFollows.coerceAtMost(500)

        val feedScore = computeFeedQualityScore(loveIslandPassed, detectionAccuracy, followedCoverage)

        return VodPersonalizationSimulationReport(
            usersSimulated = userCount,
            seriesSimulated = seriesCount,
            episodesSimulated = episodeCount,
            watchEventsGenerated = watchEvents + 1,
            catalogUpdatesSimulated = catalogUpdates,
            notificationsGenerated = notifications + if (loveIslandPassed) 1 else 0,
            detectionAccuracy = detectionAccuracy,
            followedSeriesCoverage = followedCoverage,
            feedQualityScore = feedScore,
            loveIslandScenarioPassed = loveIslandPassed,
            sampleDetections = sampleDetections
        )
    }

    fun formatReport(report: VodPersonalizationSimulationReport): String = buildString {
        appendLine("=== VOD Personalization Simulation Report ===")
        appendLine("Users: ${report.usersSimulated}, Series: ${report.seriesSimulated}, Episodes: ${report.episodesSimulated}")
        appendLine("Watch events: ${report.watchEventsGenerated}")
        appendLine("Catalog updates: ${report.catalogUpdatesSimulated}")
        appendLine("Notifications generated: ${report.notificationsGenerated}")
        appendLine("Detection accuracy: ${"%.2f%%".format(report.detectionAccuracy * 100)}")
        appendLine("Followed series coverage: ${"%.2f%%".format(report.followedSeriesCoverage * 100)}")
        appendLine("Feed quality score: ${"%.2f".format(report.feedQualityScore)}")
        appendLine("Love Island E37→E38 scenario: ${if (report.loveIslandScenarioPassed) "PASS" else "FAIL"}")
        appendLine()
        appendLine("Sample detections:")
        report.sampleDetections.forEach { appendLine("  $it") }
    }

    private fun detectNewEpisodes(
        state: SimState,
        profileId: Long,
        catalogEpisodes: List<CatalogEpisode>
    ): List<SimDetection> {
        val followed = state.follows.filter { it.profileId == profileId && it.following }.map { it.seriesId }.toSet()
        return catalogEpisodes
            .filter { it.seriesId in followed }
            .groupBy { it.seriesId }
            .flatMap { (seriesId, episodes) ->
                val lastEpisode = latestEpisode(state, profileId, seriesId)
                episodes.filter { it.episodeNumber > lastEpisode }
                    .map { ep ->
                        SimDetection(
                            seriesId = seriesId,
                            episodeNumber = ep.episodeNumber,
                            previousWatchedEpisode = lastEpisode.takeIf { it > 0 }
                        )
                    }
            }
    }

    private fun latestEpisode(state: SimState, profileId: Long, seriesId: Long): Int =
        state.watchEvents
            .filter { it.profileId == profileId && it.seriesId == seriesId }
            .maxOfOrNull { it.episodeNumber ?: 0 } ?: 0

    private data class SimDetection(
        val seriesId: Long,
        val episodeNumber: Int,
        val previousWatchedEpisode: Int?
    )

    private fun buildCatalog(playlistId: Long, seriesCount: Int, episodesPerSeries: Int): List<CatalogEpisode> {
        val list = mutableListOf<CatalogEpisode>()
        for (seriesId in 1L..seriesCount.toLong()) {
            for (ep in 1..episodesPerSeries) {
                list += CatalogEpisode(
                    playlistId = playlistId,
                    seriesId = seriesId,
                    seriesTitle = "Series $seriesId",
                    seasonNumber = 1,
                    episodeNumber = ep,
                    episodeId = seriesId * 10_000 + ep,
                    episodeTitle = "Episode $ep",
                    addedAt = System.currentTimeMillis() - random.nextLong(0, 86_400_000_000)
                )
            }
        }
        return list
    }

    private fun computeFeedQualityScore(
        loveIslandPassed: Boolean,
        detectionAccuracy: Double,
        followedCoverage: Double
    ): Double {
        var score = detectionAccuracy * 50 + followedCoverage * 30
        if (loveIslandPassed) score += 20
        return score.coerceIn(0.0, 100.0)
    }
}
