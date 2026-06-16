package com.grid.tv.feature.recording

import com.grid.tv.domain.model.RecordQuality

object RecordingStreamUrlResolver {

    private val xtreamPath = Regex(
        """^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(?:\.\w+)?/?$""",
        RegexOption.IGNORE_CASE
    )

    fun supportsQualitySelector(streamUrl: String): Boolean {
        if (streamUrl.contains(".m3u8", ignoreCase = true)) return true
        return parseXtream(streamUrl) != null
    }

    fun availableQualities(streamUrl: String): List<RecordQuality> =
        if (supportsQualitySelector(streamUrl)) {
            listOf(RecordQuality.ORIGINAL, RecordQuality.P720, RecordQuality.P480)
        } else {
            emptyList()
        }

    fun resolveUrl(streamUrl: String, quality: RecordQuality): String {
        if (quality == RecordQuality.ORIGINAL) return streamUrl
        parseXtream(streamUrl)?.let { parts ->
            return when (quality) {
                RecordQuality.P720 -> parts.adaptiveUrl()
                RecordQuality.P480 -> parts.transcodeUrl(bitrateKbps = 800)
                RecordQuality.ORIGINAL -> parts.originalUrl()
            }
        }
        return streamUrl
    }

    private fun parseXtream(streamUrl: String): XtreamParts? {
        val match = xtreamPath.matchEntire(streamUrl.trim()) ?: return null
        val (base, user, pass, streamId) = match.destructured
        if (user.isBlank() || pass.isBlank() || streamId.isBlank()) return null
        return XtreamParts(base.trimEnd('/'), user, pass, streamId)
    }

    private data class XtreamParts(
        val base: String,
        val user: String,
        val pass: String,
        val streamId: String
    ) {
        fun originalUrl(): String = "$base/live/$user/$pass/$streamId.ts"

        fun adaptiveUrl(): String = "$base/live/$user/$pass/$streamId.m3u8"

        fun transcodeUrl(bitrateKbps: Int): String =
            "$base/streaming/clients_live.php?username=$user&password=$pass&stream=$streamId&extension=ts&bitrate=$bitrateKbps"
    }
}
