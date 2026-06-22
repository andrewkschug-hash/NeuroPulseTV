package com.grid.tv.domain.model

import com.grid.tv.feature.health.intelligence.StreamFailoverRanking
import com.grid.tv.feature.health.intelligence.StreamSourceId

/**
 * Ordered stream URLs for a channel: primary first, then up to three backups.
 */
fun Channel.allStreamUrls(): List<String> = buildList {
    if (streamUrl.isNotBlank()) add(streamUrl.trim())
    listOf(backupStreamUrl, backupStreamUrl2, backupStreamUrl3).forEach { candidate ->
        val url = candidate?.trim().orEmpty()
        if (url.isNotBlank() && url !in this) add(url)
    }
}

fun Channel.primaryStreamUrl(): String = streamUrl.trim()

/** Maps each non-blank stream URL to its stable health telemetry source id. */
fun Channel.streamUrlsWithSourceIds(): List<Pair<String, String>> = buildList {
    if (streamUrl.isNotBlank()) {
        add(streamUrl.trim() to StreamSourceId.PRIMARY.storageKey)
    }
    backupStreamUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
        add(it to StreamSourceId.BACKUP_1.storageKey)
    }
    backupStreamUrl2?.trim()?.takeIf { it.isNotBlank() }?.let {
        add(it to StreamSourceId.BACKUP_2.storageKey)
    }
    backupStreamUrl3?.trim()?.takeIf { it.isNotBlank() }?.let {
        add(it to StreamSourceId.BACKUP_3.storageKey)
    }
}

fun Channel.sourceIdForUrl(url: String): String {
    val trimmed = url.trim()
    return streamUrlsWithSourceIds()
        .firstOrNull { it.first == trimmed }
        ?.second
        ?: StreamSourceId.PRIMARY.storageKey
}

/**
 * Reorders URLs using health-ranked source ids; unknown ids append in default order.
 */
fun Channel.orderStreamUrlsByHealthRanking(ranking: StreamFailoverRanking): List<String> {
    val urlBySourceId = streamUrlsWithSourceIds().toMap()
    val ordered = ranking.orderedStreamIds.mapNotNull { sourceId -> urlBySourceId[sourceId] }
    val remaining = allStreamUrls().filter { it !in ordered }
    return (ordered + remaining).distinct()
}

/**
 * Reorders URLs using health scores keyed by source id or raw URL (legacy telemetry).
 */
fun Channel.orderStreamUrlsByHealthScores(scoresByKey: Map<String, Int>): List<String> {
    if (scoresByKey.isEmpty()) return allStreamUrls()
    return allStreamUrls().sortedByDescending { url ->
        val sourceId = sourceIdForUrl(url)
        scoresByKey[url] ?: scoresByKey[sourceId] ?: Int.MIN_VALUE
    }
}
