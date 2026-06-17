package com.grid.tv.feature.subtitles

import java.io.File
import java.util.Locale

object SubtitleFileOffsetter {
    fun withDelay(source: File, delayMs: Long, cacheDir: File): File {
        if (delayMs == 0L) return source
        val ext = source.extension.lowercase(Locale.US)
        if (ext != "srt") return source

        val out = File(cacheDir, "${source.nameWithoutExtension}_${delayMs}.srt")
        if (out.exists() && out.lastModified() >= source.lastModified()) return out

        val shifted = shiftSrt(source.readText(), delayMs)
        out.parentFile?.mkdirs()
        out.writeText(shifted)
        return out
    }

    private fun shiftSrt(content: String, delayMs: Long): String {
        val timePattern = Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3})""")
        return timePattern.replace(content) { match ->
            val hours = match.groupValues[1].toInt()
            val minutes = match.groupValues[2].toInt()
            val seconds = match.groupValues[3].toInt()
            val millis = match.groupValues[4].toInt()
            val totalMs = ((hours * 3_600L + minutes * 60L + seconds) * 1_000L + millis + delayMs)
                .coerceAtLeast(0L)
            formatSrtTime(totalMs)
        }
    }

    private fun formatSrtTime(totalMs: Long): String {
        val hours = totalMs / 3_600_000
        val minutes = (totalMs % 3_600_000) / 60_000
        val seconds = (totalMs % 60_000) / 1_000
        val millis = totalMs % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}
