package com.grid.tv.data.network.parser

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodItem
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Streams Xtream VOD/series catalog JSON without allocating a full JSONArray or catalog List.
 * Reads one object at a time, emits fixed-size batches, and discards each batch after the callback.
 */
object XtreamCatalogStreamParser {
    const val DEFAULT_BATCH_SIZE = 100

    private val VOD_ARRAY_WRAPPER_KEYS = setOf(
        "vod_streams",
        "movies",
        "movie_data",
        "streams",
        "data",
        "js"
    )
    private val SERIES_ARRAY_WRAPPER_KEYS = setOf(
        "series",
        "series_list",
        "data",
        "js"
    )

    data class StreamParseResult(
        val parsedCount: Int,
        val skippedCount: Int = 0,
        val foundArray: Boolean = false
    )

    suspend fun parseVodCatalogStream(
        input: InputStream,
        charset: Charset = Charsets.UTF_8,
        username: String,
        password: String,
        serverUrl: String,
        playlistId: Long,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        parser: XtreamParser = XtreamParser(),
        onBatch: suspend (batch: List<VodItem>, batchIndex: Int, parsedSoFar: Int) -> Unit
    ): StreamParseResult {
        return parseCatalogStream(
            input = input,
            charset = charset,
            wrapperKeys = VOD_ARRAY_WRAPPER_KEYS,
            batchSize = batchSize,
            mapItem = { reader ->
                parser.parseVodItemFromJsonReader(reader, username, password, serverUrl, playlistId)
            },
            onBatch = onBatch
        )
    }

    suspend fun parseSeriesCatalogStream(
        input: InputStream,
        charset: Charset = Charsets.UTF_8,
        playlistId: Long,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        parser: XtreamParser = XtreamParser(),
        onBatch: suspend (batch: List<SeriesShow>, batchIndex: Int, parsedSoFar: Int) -> Unit
    ): StreamParseResult {
        return parseCatalogStream(
            input = input,
            charset = charset,
            wrapperKeys = SERIES_ARRAY_WRAPPER_KEYS,
            batchSize = batchSize,
            mapItem = { reader -> parser.parseSeriesItemFromJsonReader(reader, playlistId) },
            onBatch = onBatch
        )
    }

    private suspend fun <T> parseCatalogStream(
        input: InputStream,
        charset: Charset,
        wrapperKeys: Set<String>,
        batchSize: Int,
        mapItem: (JsonReader) -> T?,
        onBatch: suspend (batch: List<T>, batchIndex: Int, parsedSoFar: Int) -> Unit
    ): StreamParseResult {
        val batch = ArrayList<T>(batchSize.coerceAtMost(256))
        var parsedCount = 0
        var skippedCount = 0
        var batchIndex = 0
        var foundArray = false

        InputStreamReader(input, charset).use { reader ->
            JsonReader(reader).use { json ->
                if (!seekCatalogArray(json, wrapperKeys)) {
                    return StreamParseResult(parsedCount = 0, skippedCount = 0, foundArray = false)
                }
                foundArray = true
                json.beginArray()
                while (json.hasNext()) {
                    if (json.peek() != JsonToken.BEGIN_OBJECT) {
                        json.skipValue()
                        skippedCount++
                        continue
                    }
                    val mapped = mapItem(json) ?: run {
                        skippedCount++
                        continue
                    }
                    batch += mapped
                    parsedCount++
                    if (batch.size >= batchSize) {
                        onBatch(batch.toList(), batchIndex, parsedCount)
                        batch.clear()
                        batchIndex++
                    }
                }
                json.endArray()
            }
        }

        if (batch.isNotEmpty()) {
            onBatch(batch.toList(), batchIndex, parsedCount)
            batch.clear()
        }

        return StreamParseResult(
            parsedCount = parsedCount,
            skippedCount = skippedCount,
            foundArray = foundArray
        )
    }

    /**
     * Positions [reader] at the start of the catalog array (before [JsonReader.beginArray]).
     */
    internal fun seekCatalogArray(reader: JsonReader, wrapperKeys: Set<String>): Boolean {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> true
            JsonToken.BEGIN_OBJECT -> seekCatalogArrayInObject(reader, wrapperKeys, depth = 0)
            else -> {
                reader.skipValue()
                false
            }
        }
    }

    private fun seekCatalogArrayInObject(
        reader: JsonReader,
        wrapperKeys: Set<String>,
        depth: Int
    ): Boolean {
        if (depth > 4) {
            reader.skipValue()
            return false
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    if (name in wrapperKeys) {
                        return true
                    }
                    reader.skipValue()
                }
                JsonToken.BEGIN_OBJECT -> {
                    if (name == "data" || name in wrapperKeys) {
                        if (seekCatalogArrayInObject(reader, wrapperKeys, depth + 1)) {
                            return true
                        }
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return false
    }
}
