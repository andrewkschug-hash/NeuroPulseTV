package com.grid.tv.domain.model

/** Playlist-scoped live TV channel group identity (replaces bare groupName keys). */
object ChannelGroupIdentity {
    private const val SEPARATOR = '\u001F'

    fun groupKey(playlistId: Long, groupName: String): String =
        if (playlistId <= 0L) groupName.trim()
        else "$playlistId$SEPARATOR${groupName.trim()}"

    fun parseGroupKey(key: String): Pair<Long, String> {
        val trimmed = key.trim()
        val sep = trimmed.indexOf(SEPARATOR)
        if (sep <= 0) return 0L to trimmed
        val playlistId = trimmed.substring(0, sep).toLongOrNull() ?: 0L
        return playlistId to trimmed.substring(sep + 1)
    }

    fun groupName(key: String): String = parseGroupKey(key).second

    fun displayLabel(key: String, playlistName: String? = null): String {
        val (playlistId, name) = parseGroupKey(key)
        if (playlistId > 0L && !playlistName.isNullOrBlank()) {
            return "$playlistName · $name"
        }
        return name
    }

    fun matches(channel: Channel, selectedKey: String): Boolean =
        groupKey(channel.playlistId, channel.group) == selectedKey

    data class Filter(val playlistId: Long = -1L, val groupName: String? = null)

    fun parseFilter(groupKey: String?): Filter {
        if (groupKey.isNullOrBlank()) return Filter()
        val (playlistId, groupName) = parseGroupKey(groupKey)
        return Filter(
            playlistId = playlistId.takeIf { it > 0L } ?: -1L,
            groupName = groupName.takeIf { it.isNotBlank() }
        )
    }
}
