package com.grid.tv.domain.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object CatchupUrlFormatter {

    fun build(program: Program, channel: Channel): String? {
        if (!channel.isCatchupEnabled()) return null

        program.catchupUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val startSec = program.startTime / 1000
        val endSec = program.endTime / 1000
        val durationSec = (program.endTime - program.startTime) / 1000
        val offsetSec = ((System.currentTimeMillis() - program.startTime) / 1000).coerceAtLeast(0)

        channel.catchupSource?.trim()?.takeIf { it.isNotEmpty() }?.let { template ->
            return expandTemplate(
                template = template,
                program = program,
                channel = channel,
                startSec = startSec,
                endSec = endSec,
                durationSec = durationSec,
                offsetSec = offsetSec
            )
        }

        return when (channel.catchupMode?.lowercase(Locale.US)) {
            "append", "default", null -> appendUtcQuery(channel.streamUrl, startSec, endSec)
            "shift" -> null
            "flussonic" -> appendFlussonic(channel.streamUrl, startSec, durationSec)
            else -> appendUtcQuery(channel.streamUrl, startSec, endSec)
        }
    }

    private fun appendUtcQuery(streamUrl: String, startSec: Long, endSec: Long): String? {
        if (streamUrl.isBlank()) return null
        val separator = if (streamUrl.contains('?')) "&" else "?"
        return "${streamUrl}${separator}utc=$startSec&lutc=$endSec"
    }

    private fun appendFlussonic(streamUrl: String, startSec: Long, durationSec: Long): String? {
        if (streamUrl.isBlank()) return null
        val separator = if (streamUrl.contains('?')) "&" else "?"
        return "${streamUrl}${separator}start=$startSec&duration=$durationSec"
    }

    private fun expandTemplate(
        template: String,
        program: Program,
        channel: Channel,
        startSec: Long,
        endSec: Long,
        durationSec: Long,
        offsetSec: Long
    ): String {
        val zoned = Instant.ofEpochMilli(program.startTime).atZone(ZoneOffset.UTC)
        val date = DateTimeFormatter.ofPattern("yyyyMMdd").format(zoned)
        val time = DateTimeFormatter.ofPattern("HHmmss").format(zoned)

        return template
            .replace("{channel}", channel.epgId.orEmpty())
            .replace("{stream}", channel.streamUrl)
            .replace("{start}", program.startTime.toString())
            .replace("{end}", program.endTime.toString())
            .replace("{duration}", durationSec.toString())
            .replace("{offset}", offsetSec.toString())
            .replace("{utc}", startSec.toString())
            .replace("{lutc}", endSec.toString())
            .replace("{Y}", zoned.year.toString())
            .replace("{m}", String.format(Locale.US, "%02d", zoned.monthValue))
            .replace("{d}", String.format(Locale.US, "%02d", zoned.dayOfMonth))
            .replace("{H}", String.format(Locale.US, "%02d", zoned.hour))
            .replace("{M}", String.format(Locale.US, "%02d", zoned.minute))
            .replace("{S}", String.format(Locale.US, "%02d", zoned.second))
            .replace("{date}", date)
            .replace("{time}", time)
    }
}
