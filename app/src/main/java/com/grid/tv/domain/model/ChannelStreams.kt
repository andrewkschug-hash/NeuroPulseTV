package com.grid.tv.domain.model

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
