package com.grid.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IptvOnDemandFormatDetectorTest {

    @Test
    fun vodMovie_mp4_isProgressive_notLiveHls() {
        val url = "http://host:8080/movie/user/pass/12345.mp4"
        assertEquals(
            IptvStreamFormat.PROGRESSIVE,
            IptvStreamFormatDetector.resolveForOnDemandPlayback(
                url = url,
                contentKind = IptvOnDemandContentKind.VOD_MOVIE
            )
        )
        assertFalse(
            IptvStreamFormatDetector.resolveForPlayback(url).isHls()
        )
    }

    @Test
    fun vodSeries_mkv_isProgressive() {
        assertEquals(
            IptvStreamFormat.PROGRESSIVE,
            IptvStreamFormatDetector.resolveForOnDemandPlayback(
                url = "http://host/series/user/pass/999.mkv",
                contentKind = IptvOnDemandContentKind.VOD_SERIES
            )
        )
    }

    @Test
    fun catchupWithUtcQuery_isHls() {
        assertEquals(
            IptvStreamFormat.HLS,
            IptvStreamFormatDetector.resolveForOnDemandPlayback(
                url = "http://host/live/user/pass/1.ts?utc=1700000000&lutc=1700003600",
                contentKind = IptvOnDemandContentKind.CATCHUP
            )
        )
    }

    @Test
    fun vodMovie_m3u8_isHls() {
        assertEquals(
            IptvStreamFormat.HLS,
            IptvStreamFormatDetector.resolveForOnDemandPlayback(
                url = "http://host/movie/u/p/1.m3u8",
                contentKind = IptvOnDemandContentKind.VOD_MOVIE
            )
        )
    }
}
