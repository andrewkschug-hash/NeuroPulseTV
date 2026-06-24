package com.grid.tv.domain.model

enum class ContinueWatchingContentType {
    MOVIE,
    SERIES;

    companion object {
        fun fromStored(raw: String): ContinueWatchingContentType =
            entries.firstOrNull { it.name == raw } ?: MOVIE
    }
}

data class ContinueWatchingItem(
    val contentKey: String,
    val contentType: ContinueWatchingContentType,
    val title: String,
    val posterUrl: String?,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val playlistId: Long = 0L,
    val streamId: Long? = null,
    val seriesId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(0L)

    val subtitle: String
        get() = when {
            contentType == ContinueWatchingContentType.SERIES &&
                seasonNumber != null && episodeNumber != null ->
                "S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"
            else -> ""
        }
}
