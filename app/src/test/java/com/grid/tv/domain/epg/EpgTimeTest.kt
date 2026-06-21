package com.grid.tv.domain.epg

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EpgTimeTest {

    @Test
    fun parseXmlTvLocalWallClock_usesDeviceZone() {
        val expected = LocalDateTime.parse(
            "20250617202000",
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        )
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, EpgTime.parseXmlTvLocalWallClock("20250617202000"))
    }

    @Test
    fun formatWallClock_usesLocalZone() {
        val epoch = EpgTime.parseXmlTvLocalWallClock("20250617202000")
        val expected = java.time.Instant.ofEpochMilli(epoch)
            .atZone(ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()))
        assertEquals(expected, EpgTime.formatWallClock(epoch))
    }

    @Test
    fun anchoredWindowStart_recentersWhenNowNearWindowEnd() {
        val now = 1_000_000_000_000L
        val lookback = 90 * 60 * 1000L
        val duration = 4 * 60 * 60 * 1000L
        val staleStart = now - duration + 60_000L

        val adjusted = EpgTime.anchoredWindowStart(
            currentWindowStart = staleStart,
            windowDurationMs = duration,
            nowMs = now,
            lookbackMs = lookback,
            earliestMs = 0L
        )

        assertEquals(now - lookback, adjusted)
    }
}
