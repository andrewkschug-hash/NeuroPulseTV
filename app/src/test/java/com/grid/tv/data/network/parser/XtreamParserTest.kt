package com.grid.tv.data.network.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamParserTest {
    private val parser = XtreamParser()

    @Test
    fun buildLiveStreamUrlUsesXtreamLivePath() {
        val url = parser.buildLiveStreamUrl(
            serverUrl = "http://example.com:8080/c",
            username = "user",
            password = "pass",
            streamId = "12345"
        )
        assertEquals("http://example.com:8080/c/live/user/pass/12345.m3u8", url)
    }

    @Test
    fun buildMovieStreamUrlUsesXtreamMoviePath() {
        val url = parser.buildMovieStreamUrl(
            serverUrl = "http://example.com:8080",
            username = "user",
            password = "pass",
            streamId = "99",
            extension = "mkv"
        )
        assertEquals("http://example.com:8080/movie/user/pass/99.mkv", url)
    }

    @Test
    fun resolveServerUrlPreservesPortalPathFromUserInput() {
        val auth = XtreamParser.AuthPayload(
            status = "Active",
            expiryDateEpochSec = null,
            maxConnections = null,
            serverUrl = "http://cdn.internal:8080"
        )
        val resolved = parser.resolveServerUrl("http://example.com:8080/c/", auth)
        assertEquals("http://example.com:8080/c", resolved)
    }

    @Test
    fun resolveServerUrlKeepsUserHostWhenAuthReturnsDifferentHost() {
        val auth = XtreamParser.AuthPayload(
            status = "Active",
            expiryDateEpochSec = null,
            maxConnections = null,
            serverUrl = "http://unreachable.panel.host:8080"
        )
        val resolved = parser.resolveServerUrl("http://52123328.97qaz.com", auth)
        assertEquals("http://52123328.97qaz.com:8080", resolved)
    }

    @Test
    fun buildLiveStreamUrlPrefersDirectSource() {
        val url = parser.buildLiveStreamUrl(
            serverUrl = "http://example.com:8080",
            username = "user",
            password = "pass",
            streamId = "1",
            directSource = "http://cdn.example.com/live/news.m3u8"
        )
        assertEquals("http://cdn.example.com/live/news.m3u8", url)
    }

    @Test
    fun buildLiveStreamUrlIgnoresInvalidDirectSource() {
        val url = parser.buildLiveStreamUrl(
            serverUrl = "http://example.com:8080",
            username = "user",
            password = "pass",
            streamId = "1",
            directSource = "0"
        )
        assertEquals("http://example.com:8080/live/user/pass/1.m3u8", url)
    }

    @Test
    fun buildLiveStreamUrlEncodesSpecialCharactersInCredentials() {
        val url = parser.buildLiveStreamUrl(
            serverUrl = "http://example.com:8080",
            username = "user",
            password = "p@ss/word",
            streamId = "99"
        )
        assertEquals("http://example.com:8080/live/user/p%40ss%2Fword/99.m3u8", url)
    }

    @Test
    fun parseVodBatchedEmitsIncrementalBatches() {
        val raw = buildString {
            append('[')
            repeat(120) { index ->
                if (index > 0) append(',')
                append(
                    """{"stream_id":"$index","name":"Movie $index","container_extension":"mp4"}"""
                )
            }
            append(']')
        }
        val batches = mutableListOf<List<com.grid.tv.domain.model.VodItem>>()
        val total = parser.parseVodBatched(
            raw = raw,
            username = "user",
            password = "pass",
            serverUrl = "http://example.com:8080",
            batchSize = 50
        ) { batch ->
            batches += batch
        }
        assertEquals(120, total)
        assertEquals(3, batches.size)
        assertEquals(50, batches[0].size)
        assertEquals(50, batches[1].size)
        assertEquals(20, batches[2].size)
    }

    @Test
    fun parseVodBatchedAcceptsNumericStreamId() {
        val raw = """[{"stream_id":42,"name":"Numeric Movie","container_extension":"mp4"}]"""
        val items = parser.parseVod(
            raw = raw,
            username = "user",
            password = "pass",
            serverUrl = "http://example.com:8080"
        )
        assertEquals(1, items.size)
        assertEquals(42L, items.first().streamId)
    }

    @Test
    fun parseVodBatchedAcceptsWrappedArray() {
        val raw = """{"vod_streams":[{"stream_id":"7","name":"Wrapped Movie","container_extension":"mp4"}]}"""
        val items = parser.parseVod(
            raw = raw,
            username = "user",
            password = "pass",
            serverUrl = "http://example.com:8080"
        )
        assertEquals(1, items.size)
        assertEquals(7L, items.first().streamId)
    }
}
