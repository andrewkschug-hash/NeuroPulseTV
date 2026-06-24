package com.grid.tv.data.network.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamCatalogStreamParserTest {
    private val parser = XtreamParser()

    @Test
    fun parseVodCatalogStream_emitsIncrementalBatches() = runBlocking {
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
        val result = XtreamCatalogStreamParser.parseVodCatalogStream(
            input = raw.byteInputStream(),
            username = "user",
            password = "pass",
            serverUrl = "http://example.com:8080",
            playlistId = 0L,
            batchSize = 50,
            parser = parser
        ) { batch, _, _ ->
            batches += batch
        }
        assertEquals(120, result.parsedCount)
        assertTrue(result.foundArray)
        assertEquals(3, batches.size)
        assertEquals(50, batches[0].size)
        assertEquals(50, batches[1].size)
        assertEquals(20, batches[2].size)
    }

    @Test
    fun parseVodCatalogStream_acceptsWrappedArray() = runBlocking {
        val raw =
            """{"vod_streams":[{"stream_id":"7","name":"Wrapped Movie","container_extension":"mp4"}]}"""
        var parsed = 0
        XtreamCatalogStreamParser.parseVodCatalogStream(
            input = raw.byteInputStream(),
            username = "user",
            password = "pass",
            serverUrl = "http://example.com:8080",
            playlistId = 0L,
            parser = parser
        ) { batch, _, _ ->
            parsed += batch.size
        }
        assertEquals(1, parsed)
    }

    @Test
    fun parseVodCatalogStream_acceptsNestedDataWrapper() = runBlocking {
        val raw =
            """{"data":{"vod_streams":[{"stream_id":"9","name":"Nested Movie","container_extension":"mp4"}]}}"""
        var parsed = 0
        XtreamCatalogStreamParser.parseVodCatalogStream(
            input = raw.byteInputStream(),
            username = "user",
            password = "pass",
            serverUrl = "http://example.com:8080",
            playlistId = 0L,
            parser = parser
        ) { batch, _, _ ->
            parsed += batch.size
        }
        assertEquals(1, parsed)
    }

    @Test
    fun parseSeriesCatalogStream_readsNumericCategoryId() = runBlocking {
        val raw = """[{"series_id":42,"name":"Test Show","category_id":7}]"""
        val shows = mutableListOf<com.grid.tv.domain.model.SeriesShow>()
        XtreamCatalogStreamParser.parseSeriesCatalogStream(
            input = raw.byteInputStream(),
            playlistId = 1L,
            parser = parser
        ) { batch, _, _ ->
            shows += batch
        }
        assertEquals(1, shows.size)
        assertEquals("7", shows.first().categoryId)
    }
}
