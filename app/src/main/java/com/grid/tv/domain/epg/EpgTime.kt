package com.grid.tv.domain.epg

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Converts provider EPG timestamps to absolute epoch millis and formats them for the
 * device local wall clock. All grid layout math uses epoch millis; only UI labels call
 * [formatWallClock].
 */
object EpgTime {
    val localZone: ZoneId get() = ZoneId.systemDefault()

    private val xmlTvLocalPattern = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US)
    private val xmlTvWithOffsetPattern = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z", Locale.US)

    fun localNowMs(): Long = System.currentTimeMillis()

    /**
     * Parses timezone-less XMLTV timestamps as local wall clock on this device
     * (e.g. `20250617202000` → 8:20 PM in America/New_York).
     */
    fun parseXmlTvLocalWallClock(dateTime: String): Long {
        if (dateTime.length < 14) return 0L
        return runCatching {
            LocalDateTime.parse(dateTime.take(14), xmlTvLocalPattern)
                .atZone(localZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    /** Parses XMLTV timestamps that include an explicit offset (`+0000`, `-0500`, etc.). */
    fun parseXmlTvWithOffset(normalized: String): Long =
        runCatching {
            ZonedDateTime.parse(normalized, xmlTvWithOffsetPattern)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)

    fun formatWallClock(epochMs: Long, pattern: String = "h:mm a"): String {
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
        return Instant.ofEpochMilli(epochMs).atZone(localZone).format(formatter)
    }

    fun formatWallClockDay(epochMs: Long): String {
        val zoned = Instant.ofEpochMilli(epochMs).atZone(localZone)
        val today = localNowMs().let { Instant.ofEpochMilli(it).atZone(localZone).toLocalDate() }
        val date = zoned.toLocalDate()
        val datePart = formatWallClock(epochMs, "M/d")
        return if (date == today) {
            "Today $datePart"
        } else {
            formatWallClock(epochMs, "EEE M/d")
        }
    }

    /**
     * Horizontal pixel math for the live tracker uses absolute millis:
     * `(nowMs - windowStartMs) * dpPerMs`.
     */
    fun anchoredWindowStart(
        currentWindowStart: Long,
        windowDurationMs: Long,
        nowMs: Long = localNowMs(),
        lookbackMs: Long = 90 * 60 * 1000L,
        earliestMs: Long = nowMs - 7 * 24 * 60 * 60 * 1000L
    ): Long {
        val windowEnd = currentWindowStart + windowDurationMs
        val marginMs = 30 * 60 * 1000L
        return if (nowMs >= windowEnd - marginMs || nowMs < currentWindowStart) {
            (nowMs - lookbackMs).coerceAtLeast(earliestMs)
        } else {
            currentWindowStart
        }
    }
}
