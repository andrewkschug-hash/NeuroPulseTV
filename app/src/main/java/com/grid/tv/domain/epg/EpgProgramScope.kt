package com.grid.tv.domain.epg

/** Composite keys for playlist-isolated EPG programme storage and lookup. */
object EpgProgramScope {
    private const val SEPARATOR = '\u001F'

    fun rawLookupKey(playlistId: Long, channelEpgId: String): String =
        "$playlistId$SEPARATOR${channelEpgId.lowercase()}"

    fun normalizedLookupKey(playlistId: Long, channelEpgId: String): String {
        val normalized = EpgIdNormalizer.normalize(channelEpgId)
        return if (normalized.isEmpty()) {
            rawLookupKey(playlistId, channelEpgId)
        } else {
            "$playlistId$SEPARATOR$normalized"
        }
    }

    fun channelLookupKey(playlistId: Long, lookupKey: String): String =
        rawLookupKey(playlistId, lookupKey)
}
