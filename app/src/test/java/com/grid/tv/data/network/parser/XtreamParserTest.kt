package com.grid.tv.data.network.parser

import kotlinx.coroutines.runBlocking
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
    fun parseVodBatchedEmitsIncrementalBatches() = runBlocking {
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
    fun parseVodBatchedAcceptsNumericStreamId() = runBlocking {
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
    fun parseVodBatchedAcceptsWrappedArray() = runBlocking {
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

    @Test
    fun parseVodBatchedAcceptsNestedDataWrapper() = runBlocking {
        val raw = """{"data":{"vod_streams":[{"stream_id":"9","name":"Nested Movie","container_extension":"mp4"}]}}"""
        val items = parser.parseVod(
            raw = raw,
            username = "user",
            password = "pass",
            serverUrl = "http://example.com:8080"
        )
        assertEquals(1, items.size)
        assertEquals(9L, items.first().streamId)
    }

    @Test
    fun diagnoseVodResponse_detectsHtml() {
        assertEquals(
            "Provider returned HTML instead of JSON (check server URL).",
            parser.diagnoseVodResponse("<html><body>Login</body></html>")
        )
    }

    @Test
    fun diagnoseVodResponse_detectsEmptyBody() {
        assertEquals(
            "Provider returned an empty response body.",
            parser.diagnoseVodResponse("   ")
        )
    }

    @Test
    fun parseSeriesItemReadsNumericCategoryId() = runBlocking {
        val raw = """[{"series_id":42,"name":"Test Show","category_id":7}]"""
        val shows = parser.parseSeries(raw, playlistId = 1L)
        assertEquals(1, shows.size)
        assertEquals("7", shows.first().categoryId)
    }

    @Test
    fun parseSeriesCategoriesReadsNumericCategoryId() {
        val raw = """[{"category_id":12,"category_name":"Drama"}]"""
        val categories = parser.parseSeriesCategories(raw, playlistId = 1L)
        assertEquals(1, categories.size)
        assertEquals("12", categories.first().id)
        assertEquals("Drama", categories.first().name)
    }

    @Test
    fun parseVodCategoriesUsesNameFieldWhenCategoryNameMissing() {
        val raw = """[{"category_id":1006,"name":"NETFLIX ASIA"}]"""
        val categories = parser.parseVodCategories(raw, playlistId = 1L)
        assertEquals(1, categories.size)
        assertEquals("1006", categories.first().id)
        assertEquals("NETFLIX ASIA", categories.first().name)
    }

    @Test
    fun parseVodCategoriesReadsMovieCategoriesWrapper() {
        val raw = """{"movie_categories":[{"category_id":"1054","category_name":"Action"},{"category_id":"1056","category_name":"Comedy"}]}"""
        val categories = parser.parseVodCategories(raw, playlistId = 9L)
        assertEquals(2, categories.size)
        assertEquals("1054", categories[0].id)
        assertEquals("Action", categories[0].name)
        assertEquals("1056", categories[1].id)
        assertEquals("Comedy", categories[1].name)
    }
}
