package com.grid.tv.data.network.parser

import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.domain.model.EpgResolutionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class M3uParser {

    fun parseAsFlow(playlistId: Long, content: String): Flow<M3uParseProgress> = flow {
        val lines = content.lineSequence().toList()
        val extinfLines = lines.count { it.trim().startsWith("#EXTINF", ignoreCase = true) }
        val channels = mutableListOf<ChannelEntity>()

        var name = "Unknown"
        var tvgName: String? = null
        var group = "General"
        var logo: String? = null
        var epgId: String? = null
        var backupUrl: String? = null
        var backupUrl2: String? = null
        var backupUrl3: String? = null
        var catchup: String? = null
        var catchupSource: String? = null
        var catchupDays = 0

        lines.forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                name = line.substringAfterLast(",", "Unknown").trim().ifBlank { "Unknown" }
                tvgName = attr(line, "tvg-name")
                group = attr(line, "group-title") ?: "General"
                logo = attr(line, "tvg-logo")
                epgId = attr(line, "tvg-id")
                backupUrl = attr(line, "backup-url")
                backupUrl2 = attr(line, "backup-url-2")
                backupUrl3 = attr(line, "backup-url-3")
                catchup = attr(line, "catchup")
                catchupSource = attr(line, "catchup-source")
                catchupDays = attr(line, "catchup-days")?.toIntOrNull() ?: 0
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                val pipeBackups = backupUrl?.split('|')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
                val channel = ChannelEntity(
                    number = channels.size + 1,
                    name = (tvgName ?: name).ifBlank { "Channel ${channels.size + 1}" },
                    groupName = group.ifBlank { "General" },
                    logoUrl = logo,
                    epgId = epgId,
                    streamUrl = line,
                    backupStreamUrl = pipeBackups.getOrNull(0) ?: backupUrl?.substringBefore('|')?.trim()?.takeIf { it.isNotBlank() },
                    backupStreamUrl2 = pipeBackups.getOrNull(1) ?: backupUrl2,
                    backupStreamUrl3 = pipeBackups.getOrNull(2) ?: backupUrl3,
                    playlistId = playlistId,
                    catchupMode = catchup,
                    catchupSource = catchupSource,
                    catchupDays = catchupDays,
                    epgResolutionStatus = if (!epgId.isNullOrBlank()) EpgResolutionStatus.CONFIRMED.name else EpgResolutionStatus.UNRESOLVED.name,
                    epgResolutionConfidence = if (!epgId.isNullOrBlank()) 100 else 0,
                    epgResolutionSource = if (!epgId.isNullOrBlank()) "m3u" else null
                )
                channels += channel
                emit(
                    M3uParseProgress(
                        parsedCount = channels.size,
                        totalKnown = extinfLines,
                        latest = channel
                    )
                )
            }
        }

        emit(M3uParseProgress(parsedCount = channels.size, totalKnown = extinfLines, done = true, channels = channels))
    }

    private fun attr(line: String, key: String): String? {
        val token = "$key=\""
        val start = line.indexOf(token)
        if (start == -1) return null
        val from = start + token.length
        val end = line.indexOf('"', from)
        if (end == -1) return null
        return line.substring(from, end).takeIf { it.isNotBlank() }
    }
}
