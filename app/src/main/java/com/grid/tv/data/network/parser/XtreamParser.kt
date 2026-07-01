package com.grid.tv.data.network.parser

import android.util.Log
import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.data.db.entity.ProgramEntity
import com.grid.tv.domain.model.EpgResolutionStatus
import com.grid.tv.domain.model.SeriesDetail
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodItem
import com.grid.tv.feature.epg.EpgProgramTextDecoder
import com.grid.tv.feature.search.SearchTitleNormalizer
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.net.URI
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class XtreamParser {

    companion object {
        private const val TAG = "XtreamParser"
        const val VOD_CATALOG_BATCH_SIZE = 75
        private val VOD_ARRAY_WRAPPER_KEYS = listOf(
            "vod_streams",
            "movies",
            "movie_data",
            "streams",
            "data",
            "js"
        )
        private val SERIES_ARRAY_WRAPPER_KEYS = listOf(
            "series",
            "series_list",
            "data",
            "js"
        )
        private val CATEGORY_ARRAY_WRAPPER_KEYS = listOf(
            "categories",
            "vod_categories",
            "series_categories",
            "data",
            "js"
        )
    }

    data class AuthPayload(
        val status: String,
        val expiryDateEpochSec: Long?,
        val maxConnections: Int?,
        val serverUrl: String
    )

    fun isAuthSuccessful(raw: String): Boolean {
        if (raw.isBlank()) return false
        return try {
            val root = JSONObject(raw)
            val user = root.optJSONObject("user_info") ?: return false
            if (user.has("auth")) {
                when (user.optInt("auth", -1)) {
                    1 -> return true
                    0 -> return false
                }
            }
            user.optString("status").equals("Active", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    fun parseAuth(raw: String): AuthPayload {
        val root = JSONObject(raw)
        val user = root.optJSONObject("user_info") ?: JSONObject()
        val server = root.optJSONObject("server_info") ?: JSONObject()
        val status = user.optString("status").ifBlank { "Unknown" }
        val exp = user.optString("exp_date").toLongOrNull()
        val maxConn = user.optString("max_connections").toIntOrNull()
        val protocol = server.optString("server_protocol").ifBlank { "http" }
        val host = server.optString("url")
        val port = server.optString("port")
        val serverUrl = if (host.isNotBlank() && port.isNotBlank()) "$protocol://$host:$port" else ""
        return AuthPayload(status, exp, maxConn, serverUrl)
    }

    fun normalizeServerUrl(raw: String): String {
        val trimmed = raw.trim()
            .replace("\u200B", "")
            .replace(Regex("""\s+"""), "")
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed.removeSuffix("/")
        } else {
            "http://${trimmed.removeSuffix("/")}"
        }
    }

    fun resolveServerUrl(userEntered: String, auth: AuthPayload): String {
        val entered = normalizeServerUrl(userEntered)
        val enteredUri = runCatching { URI(entered) }.getOrNull()
        val authUri = auth.serverUrl.takeIf { it.isNotBlank() }?.let {
            runCatching { URI(normalizeServerUrl(it)) }.getOrNull()
        }
        val host = enteredUri?.host ?: authUri?.host ?: return entered
        val protocol = enteredUri?.scheme ?: authUri?.scheme ?: "http"
        val port = when {
            enteredUri?.port != null && enteredUri.port > 0 -> enteredUri.port
            authUri?.port != null && authUri.port > 0 -> authUri.port
            else -> -1
        }
        val portPart = if (port > 0) ":$port" else ""
        val pathSuffix = enteredUri?.path?.takeIf { it.isNotBlank() && it != "/" }.orEmpty()
        return "$protocol://$host$portPart$pathSuffix".trimEnd('/')
    }

    fun buildLiveStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        extension: String = "m3u8",
        directSource: String? = null
    ): String {
        sanitizeDirectSource(directSource)?.let { return it }
        val base = serverUrl.trimEnd('/')
        val ext = extension.trim().removePrefix(".").ifBlank { "m3u8" }
        return "$base/live/${encodePath(username)}/${encodePath(password)}/$streamId.$ext"
    }

    fun buildLiveStreamUrlTs(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        directSource: String? = null
    ): String = buildLiveStreamUrl(
        serverUrl = serverUrl,
        username = username,
        password = password,
        streamId = streamId,
        extension = "ts",
        directSource = directSource
    )

    fun sanitizeDirectSource(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed == "0" || trimmed.equals("null", ignoreCase = true)) return null
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return trimmed
    }

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    fun buildMovieStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        extension: String,
        directSource: String? = null
    ): String {
        sanitizeDirectSource(directSource)?.let { return it }
        val base = serverUrl.trimEnd('/')
        val ext = extension.ifBlank { "mp4" }
        return "$base/movie/${encodePath(username)}/${encodePath(password)}/$streamId.$ext"
    }

    fun buildSeriesStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        extension: String,
        directSource: String? = null
    ): String {
        sanitizeDirectSource(directSource)?.let { return it }
        val base = serverUrl.trimEnd('/')
        val ext = extension.ifBlank { "mp4" }
        return "$base/series/${encodePath(username)}/${encodePath(password)}/$streamId.$ext"
    }

    fun parseLiveCategories(raw: String): Map<String, String> {
        val arr = parseJsonArray(raw) ?: return emptyMap()
        val out = linkedMapOf<String, String>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("category_id")
            val name = item.optString("category_name").ifBlank { "Live" }
            if (id.isNotBlank()) out[id] = name
        }
        return out
    }

    fun parseLiveChannels(
        playlistId: Long,
        raw: String,
        username: String,
        password: String,
        serverUrl: String,
        categories: Map<String, String>
    ): List<ChannelEntity> {
        val arr = parseJsonArray(raw) ?: return emptyList()
        val out = ArrayList<ChannelEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val streamId = item.optString("stream_id")
            if (streamId.isBlank()) continue
            val name = item.optString("name").ifBlank { "Live $streamId" }
            val logo = item.optString("stream_icon").ifBlank { null }
            val epgChannelId = item.optString("epg_channel_id").ifBlank { null }
            val epgId = epgChannelId ?: streamId
            val epgSource = if (epgChannelId != null) "xtream" else "xtream:stream_id"
            val catId = item.optString("category_id")
            val group = categories[catId] ?: item.optString("category_name").ifBlank { "Live" }
            val number = item.optString("num").toIntOrNull() ?: (i + 1)
            val directSource = sanitizeDirectSource(item.optString("direct_source"))
            val containerExt = item.optString("container_extension").ifBlank { "m3u8" }
            val streamUrl = buildLiveStreamUrl(
                serverUrl, username, password, streamId, containerExt, directSource
            )
            val backupExt = if (containerExt.equals("ts", ignoreCase = true)) "m3u8" else "ts"
            val backupUrl = if (directSource == null) {
                buildLiveStreamUrl(serverUrl, username, password, streamId, backupExt, null)
            } else {
                null
            }
            out += ChannelEntity(
                number = number,
                name = name,
                searchTitle = SearchTitleNormalizer.normalize(name),
                groupName = group,
                logoUrl = logo,
                epgId = epgId,
                streamUrl = streamUrl,
                backupStreamUrl = backupUrl?.takeIf { it != streamUrl },
                playlistId = playlistId,
                epgResolutionStatus = EpgResolutionStatus.CONFIRMED.name,
                epgResolutionConfidence = 100,
                epgResolutionSource = epgSource
            )
        }
        return out
    }

    /**
     * Parses live channels in fixed-size batches to avoid building the full catalog list in memory.
     */
    suspend fun parseLiveChannelsBatched(
        playlistId: Long,
        raw: String,
        username: String,
        password: String,
        serverUrl: String,
        categories: Map<String, String>,
        batchSize: Int = 100,
        onBatch: suspend (List<ChannelEntity>) -> Unit,
    ): LiveChannelParseStats {
        val arr = parseJsonArray(raw) ?: return LiveChannelParseStats()
        val batch = ArrayList<ChannelEntity>(batchSize)
        var parsedCount = 0
        var epgFromProvider = 0
        var epgFromStreamId = 0
        suspend fun flush() {
            if (batch.isEmpty()) return
            onBatch(batch.toList())
            batch.clear()
        }
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val streamId = item.optString("stream_id")
            if (streamId.isBlank()) continue
            val name = item.optString("name").ifBlank { "Live $streamId" }
            val logo = item.optString("stream_icon").ifBlank { null }
            val epgChannelId = item.optString("epg_channel_id").ifBlank { null }
            val epgId = epgChannelId ?: streamId
            val epgSource = if (epgChannelId != null) "xtream" else "xtream:stream_id"
            if (epgChannelId != null) epgFromProvider++ else epgFromStreamId++
            parsedCount++
            val catId = item.optString("category_id")
            val group = categories[catId] ?: item.optString("category_name").ifBlank { "Live" }
            val number = item.optString("num").toIntOrNull() ?: (i + 1)
            val directSource = sanitizeDirectSource(item.optString("direct_source"))
            val containerExt = item.optString("container_extension").ifBlank { "m3u8" }
            val streamUrl = buildLiveStreamUrl(
                serverUrl, username, password, streamId, containerExt, directSource
            )
            val backupExt = if (containerExt.equals("ts", ignoreCase = true)) "m3u8" else "ts"
            val backupUrl = if (directSource == null) {
                buildLiveStreamUrl(serverUrl, username, password, streamId, backupExt, null)
            } else {
                null
            }
            batch += ChannelEntity(
                number = number,
                name = name,
                searchTitle = SearchTitleNormalizer.normalize(name),
                groupName = group,
                logoUrl = logo,
                epgId = epgId,
                streamUrl = streamUrl,
                backupStreamUrl = backupUrl?.takeIf { it != streamUrl },
                playlistId = playlistId,
                epgResolutionStatus = EpgResolutionStatus.CONFIRMED.name,
                epgResolutionConfidence = 100,
                epgResolutionSource = epgSource
            )
            if (batch.size >= batchSize) flush()
        }
        flush()
        return LiveChannelParseStats(
            parsedCount = parsedCount,
            epgFromProvider = epgFromProvider,
            epgFromStreamId = epgFromStreamId,
        )
    }

    data class LiveChannelParseStats(
        val parsedCount: Int = 0,
        val epgFromProvider: Int = 0,
        val epgFromStreamId: Int = 0,
    )

    fun parseVodCategories(
        raw: String,
        playlistId: Long = 0L,
        requestId: String? = null,
    ): List<com.grid.tv.domain.model.VodCategory> {
        val arr = parseJsonArray(raw, CATEGORY_ARRAY_WRAPPER_KEYS) ?: return emptyList()
        val req = reqPrefix(requestId)
        arr.optJSONObject(0)?.let { sample ->
            val handled = setOf(
                "category_id", "categoryId", "categoryid", "cat_id", "id", "vod_category_id", "series_category_id",
                "category_ids", "category_name", "cat_name", "genre_name", "name", "title", "category", "display_name"
            )
            val keys = jsonObjectKeys(sample)
            val ignored = keys - handled
            Log.d(TAG, "${req}get_vod_categories FIELD_TRACE sampleKeys=$keys ignoredKeys=$ignored")
        }
        val seenIds = mutableSetOf<String>()
        var hardDropMalformed = 0
        var hardDropMissingId = 0
        var softDropMissingNameRecovered = 0
        var softDropDuplicateId = 0
        var unknownSchema = 0
        val out = ArrayList<com.grid.tv.domain.model.VodCategory>(arr.length())
        for (i in 0 until arr.length()) {
            val node = arr.opt(i)
            val item = node as? JSONObject
            if (item == null) {
                hardDropMalformed += 1
                continue
            }
            val id = optCategoryId(item)
            if (id == null) {
                hardDropMissingId += 1
                continue
            }
            if (id.isBlank()) {
                hardDropMissingId += 1
                continue
            }
            if (!seenIds.add(id)) {
                softDropDuplicateId += 1
                continue
            }
            if (extractRawCategoryName(item).isNullOrBlank()) {
                softDropMissingNameRecovered += 1
            }
            val handled = setOf(
                "category_id", "categoryId", "categoryid", "cat_id", "id", "vod_category_id", "series_category_id",
                "category_ids", "category_name", "cat_name", "genre_name", "name", "title", "category", "display_name"
            )
            if ((jsonObjectKeys(item) - handled).isNotEmpty()) unknownSchema += 1
            val name = optCategoryName(item, fallback = "Movies")
            out += com.grid.tv.domain.model.VodCategory(id, name, playlistId)
        }
        Log.d(
            TAG,
            "${req}get_vod_categories DROP_CLASSIFICATION " +
                "hard_drop_malformed=$hardDropMalformed hard_drop_missing_id=$hardDropMissingId " +
                "soft_drop_missing_name_recovered=$softDropMissingNameRecovered soft_drop_duplicate_id=$softDropDuplicateId " +
                "unknown_schema=$unknownSchema input=${arr.length()} output=${out.size}"
        )
        return com.grid.tv.domain.model.VodCategoryNameResolver.normalizeList(out)
    }

    fun parseSeriesCategories(
        raw: String,
        playlistId: Long = 0L,
        requestId: String? = null,
    ): List<com.grid.tv.domain.model.VodCategory> {
        val arr = parseJsonArray(raw, CATEGORY_ARRAY_WRAPPER_KEYS) ?: return emptyList()
        val req = reqPrefix(requestId)
        arr.optJSONObject(0)?.let { sample ->
            val handled = setOf(
                "category_id", "categoryId", "categoryid", "cat_id", "id", "vod_category_id", "series_category_id",
                "category_ids", "category_name", "cat_name", "genre_name", "name", "title", "category", "display_name"
            )
            val keys = jsonObjectKeys(sample)
            val ignored = keys - handled
            Log.d(TAG, "${req}get_series_categories FIELD_TRACE sampleKeys=$keys ignoredKeys=$ignored")
        }
        val seenIds = mutableSetOf<String>()
        var hardDropMalformed = 0
        var hardDropMissingId = 0
        var softDropMissingNameRecovered = 0
        var softDropDuplicateId = 0
        var unknownSchema = 0
        val out = ArrayList<com.grid.tv.domain.model.VodCategory>(arr.length())
        for (i in 0 until arr.length()) {
            val node = arr.opt(i)
            val item = node as? JSONObject
            if (item == null) {
                hardDropMalformed += 1
                continue
            }
            val id = optCategoryId(item)
            if (id == null) {
                hardDropMissingId += 1
                continue
            }
            if (id.isBlank()) {
                hardDropMissingId += 1
                continue
            }
            if (!seenIds.add(id)) {
                softDropDuplicateId += 1
                continue
            }
            if (extractRawCategoryName(item).isNullOrBlank()) {
                softDropMissingNameRecovered += 1
            }
            val handled = setOf(
                "category_id", "categoryId", "categoryid", "cat_id", "id", "vod_category_id", "series_category_id",
                "category_ids", "category_name", "cat_name", "genre_name", "name", "title", "category", "display_name"
            )
            if ((jsonObjectKeys(item) - handled).isNotEmpty()) unknownSchema += 1
            val name = optCategoryName(item, fallback = "Series")
            out += com.grid.tv.domain.model.VodCategory(id, name, playlistId)
        }
        Log.d(
            TAG,
            "${req}get_series_categories DROP_CLASSIFICATION " +
                "hard_drop_malformed=$hardDropMalformed hard_drop_missing_id=$hardDropMissingId " +
                "soft_drop_missing_name_recovered=$softDropMissingNameRecovered soft_drop_duplicate_id=$softDropDuplicateId " +
                "unknown_schema=$unknownSchema input=${arr.length()} output=${out.size}"
        )
        return com.grid.tv.domain.model.VodCategoryNameResolver.normalizeList(out)
    }

    /** Human-readable hint when a VOD payload cannot be parsed into a stream list. */
    fun diagnoseVodResponse(raw: String): String? {
        val trimmed = sanitizeJsonPayload(raw)
        if (trimmed.isBlank()) return "Provider returned an empty response body."
        if (trimmed.startsWith("<")) return "Provider returned HTML instead of JSON (check server URL)."
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            return "Unexpected response format: ${trimmed.take(80)}"
        }
        return try {
            if (trimmed.startsWith("[")) return null
            val obj = JSONObject(trimmed)
            when {
                obj.has("user_info") && !previewHasCatalogArray(trimmed) ->
                    "Provider returned account info instead of a movie list (check server URL or credentials)."
                obj.optString("error").isNotBlank() ->
                    "Provider error: ${obj.optString("error")}"
                else -> null
            }
        } catch (_: Exception) {
            "Response is not valid JSON."
        }
    }

    suspend fun parseVod(raw: String, username: String, password: String, serverUrl: String, playlistId: Long = 0L): List<VodItem> {
        val out = ArrayList<VodItem>()
        parseVodBatched(raw, username, password, serverUrl, playlistId) { batch ->
            out.addAll(batch)
        }
        return out
    }

    suspend fun parseVodBatched(
        raw: String,
        username: String,
        password: String,
        serverUrl: String,
        playlistId: Long = 0L,
        batchSize: Int = VOD_CATALOG_BATCH_SIZE,
        onBatch: suspend (List<VodItem>) -> Unit
    ): Int {
        val result = XtreamCatalogStreamParser.parseVodCatalogStream(
            input = raw.byteInputStream(Charsets.UTF_8),
            username = username,
            password = password,
            serverUrl = serverUrl,
            playlistId = playlistId,
            batchSize = batchSize,
            parser = this
        ) { batch, _, _ ->
            onBatch(batch)
        }
        return result.parsedCount
    }

    internal fun parseVodItemFromJson(
        item: JSONObject?,
        username: String,
        password: String,
        serverUrl: String,
        playlistId: Long
    ): VodItem? {
        item ?: return null
        val id = optLongId(item, "stream_id", "movie_id", "id", "num") ?: return null
        val ext = item.optString("container_extension").ifBlank { "mp4" }
        val directSource = item.optString("direct_source").ifBlank { null }
        val url = buildMovieStreamUrl(serverUrl, username, password, id.toString(), ext, directSource)
        val title = item.optString("name").ifBlank { "VOD $id" }
        val info = parseInfoObject(item.opt("info"))
        val infoPlot = info?.optString("plot")
            ?.ifBlank { info.optString("description") }
            ?.ifBlank { info.optString("synopsis") }
            ?.ifBlank { info.optString("overview") }
            ?.ifBlank { info.optString("storyline") }
            ?.ifBlank { null }
        val infoCast = info?.optString("cast")
            ?.ifBlank { info.optString("actors") }
            ?.ifBlank { null }
        val infoDirector = info?.optString("director")
            ?.ifBlank { info.optString("directors") }
            ?.ifBlank { null }
        val infoGenre = info?.optString("genre")
            ?.ifBlank { info.optString("genres") }
            ?.ifBlank { null }
        val infoRating = info?.optString("rating")
            ?.ifBlank { info.optString("vote_average") }
            ?.ifBlank { info.optString("tmdb_rating") }
            ?.ifBlank { null }
        val infoDuration = info?.optString("duration")
            ?.ifBlank { info.optString("runtime") }
            ?.ifBlank { info.optString("duration_secs").takeIf { it.toLongOrNull() != null }?.let(::secondsToRuntimeLabel) }
            ?.ifBlank { null }
        return VodItem(
            id = id,
            title = title,
            streamId = id,
            streamUrl = url,
            posterUrl = item.optString("stream_icon").ifBlank { null },
            plot = plotOrDescription(item) ?: infoPlot,
            cast = item.optString("cast").ifBlank { null } ?: infoCast,
            director = item.optString("director").ifBlank { null } ?: infoDirector,
            genre = item.optString("genre").ifBlank { null } ?: infoGenre,
            rating = item.optString("rating").ifBlank { null } ?: infoRating,
            duration = item.optString("duration").ifBlank { null } ?: infoDuration,
            categoryId = optCategoryId(item),
            addedEpochSec = item.optString("added").toLongOrNull(),
            playlistId = playlistId
        )
    }

    internal fun parseVodItemFromJsonReader(
        reader: JsonReader,
        username: String,
        password: String,
        serverUrl: String,
        playlistId: Long
    ): VodItem? {
        var streamId: Long? = null
        var movieId: Long? = null
        var genericId: Long? = null
        var numId: Long? = null
        var name: String? = null
        var extension = "mp4"
        var directSource: String? = null
        var posterUrl: String? = null
        var plot: String? = null
        var cast: String? = null
        var director: String? = null
        var genre: String? = null
        var rating: String? = null
        var duration: String? = null
        var categoryId: String? = null
        var addedEpochSec: Long? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "stream_id" -> streamId = readLongToken(reader)
                "movie_id" -> movieId = readLongToken(reader)
                "id" -> genericId = readLongToken(reader)
                "num" -> numId = readLongToken(reader)
                "name" -> name = readStringToken(reader)
                "container_extension" -> extension = readStringToken(reader)?.ifBlank { null } ?: extension
                "direct_source" -> directSource = readStringToken(reader)?.ifBlank { null }
                "stream_icon" -> posterUrl = readStringToken(reader)?.ifBlank { null }
                "plot" -> plot = readStringToken(reader)?.ifBlank { null } ?: plot
                "description" -> if (plot.isNullOrBlank()) plot = readStringToken(reader)?.ifBlank { null }
                "synopsis" -> if (plot.isNullOrBlank()) plot = readStringToken(reader)?.ifBlank { null }
                "overview" -> if (plot.isNullOrBlank()) plot = readStringToken(reader)?.ifBlank { null }
                "cast" -> cast = readStringToken(reader)?.ifBlank { null }
                "director" -> director = readStringToken(reader)?.ifBlank { null }
                "genre" -> genre = readStringToken(reader)?.ifBlank { null }
                "rating" -> rating = readStringToken(reader)?.ifBlank { null }
                "duration" -> duration = readStringToken(reader)?.ifBlank { null }
                "category_id", "categoryId", "vod_category_id", "series_category_id" -> {
                    categoryId = readCategoryIdToken(reader) ?: categoryId
                }
                "added" -> addedEpochSec = readLongToken(reader)
                "info" -> {
                    val info = readVodInfoFieldsFromReader(reader)
                    if (plot.isNullOrBlank()) plot = info.plot
                    if (cast.isNullOrBlank()) cast = info.cast
                    if (director.isNullOrBlank()) director = info.director
                    if (genre.isNullOrBlank()) genre = info.genre
                    if (rating.isNullOrBlank()) rating = info.rating
                    if (duration.isNullOrBlank()) duration = info.duration
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val id = streamId ?: movieId ?: genericId ?: numId ?: return null
        val url = buildMovieStreamUrl(serverUrl, username, password, id.toString(), extension, directSource)
        val title = name?.ifBlank { null } ?: "VOD $id"
        return VodItem(
            id = id,
            title = title,
            streamId = id,
            streamUrl = url,
            posterUrl = posterUrl,
            plot = plot,
            cast = cast,
            director = director,
            genre = genre,
            rating = rating,
            duration = duration,
            categoryId = categoryId,
            addedEpochSec = addedEpochSec,
            playlistId = playlistId
        )
    }

    internal fun parseSeriesItemFromJsonReader(reader: JsonReader, playlistId: Long): SeriesShow? {
        var seriesId: Long? = null
        var name: String? = null
        var coverUrl: String? = null
        var categoryId: String? = null
        var genre: String? = null
        var plot: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "series_id" -> seriesId = readLongToken(reader)
                "name" -> name = readStringToken(reader)
                "cover" -> coverUrl = readStringToken(reader)?.ifBlank { null }
                "category_id", "categoryId", "vod_category_id", "series_category_id" -> {
                    categoryId = readCategoryIdToken(reader) ?: categoryId
                }
                "genre" -> genre = readStringToken(reader)?.ifBlank { null }
                "plot" -> plot = readStringToken(reader)?.ifBlank { null }
                "info" -> {
                    val info = readSeriesInfoFieldsFromReader(reader)
                    if (plot.isNullOrBlank()) plot = info.plot
                    if (genre.isNullOrBlank()) genre = info.genre
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val id = seriesId ?: return null
        return SeriesShow(
            id = id,
            name = name?.ifBlank { null } ?: "Series $id",
            coverUrl = coverUrl,
            categoryId = categoryId,
            genre = genre,
            plot = plot,
            playlistId = playlistId
        )
    }

    private fun readPlotFromInfoReader(reader: JsonReader): String? {
        var plot: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "plot", "description", "synopsis", "overview" -> {
                    val value = readStringToken(reader)?.ifBlank { null }
                    if (plot.isNullOrBlank() && value != null) plot = value
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return plot
    }

    private data class SeriesInfoFields(
        val plot: String? = null,
        val genre: String? = null,
    )

    private fun readSeriesInfoFieldsFromReader(reader: JsonReader): SeriesInfoFields {
        var plot: String? = null
        var genre: String? = null
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "plot", "description", "synopsis", "overview", "storyline" -> {
                            val value = readStringToken(reader)?.ifBlank { null }
                            if (plot.isNullOrBlank() && value != null) plot = value
                        }
                        "genre", "genres" -> {
                            val value = readStringToken(reader)?.ifBlank { null }
                            if (genre.isNullOrBlank() && value != null) genre = value
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            JsonToken.STRING -> {
                val raw = reader.nextString()
                val info = parseInfoObject(raw)
                plot = info?.optString("plot")
                    ?.ifBlank { info.optString("description") }
                    ?.ifBlank { info.optString("synopsis") }
                    ?.ifBlank { info.optString("overview") }
                    ?.ifBlank { info.optString("storyline") }
                    ?.ifBlank { null }
                genre = info?.optString("genre")
                    ?.ifBlank { info.optString("genres") }
                    ?.ifBlank { null }
            }
            else -> reader.skipValue()
        }
        return SeriesInfoFields(plot = plot, genre = genre)
    }

    private data class VodInfoFields(
        val plot: String? = null,
        val cast: String? = null,
        val director: String? = null,
        val genre: String? = null,
        val rating: String? = null,
        val duration: String? = null,
    )

    private fun readVodInfoFieldsFromReader(reader: JsonReader): VodInfoFields {
        var plot: String? = null
        var cast: String? = null
        var director: String? = null
        var genre: String? = null
        var rating: String? = null
        var duration: String? = null
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "plot", "description", "synopsis", "overview", "storyline" -> {
                            val value = readStringToken(reader)?.ifBlank { null }
                            if (plot.isNullOrBlank() && value != null) plot = value
                        }
                        "cast", "actors" -> {
                            val value = readStringToken(reader)?.ifBlank { null }
                            if (cast.isNullOrBlank() && value != null) cast = value
                        }
                        "director", "directors" -> {
                            val value = readStringToken(reader)?.ifBlank { null }
                            if (director.isNullOrBlank() && value != null) director = value
                        }
                        "genre", "genres" -> {
                            val value = readStringToken(reader)?.ifBlank { null }
                            if (genre.isNullOrBlank() && value != null) genre = value
                        }
                        "rating", "vote_average", "tmdb_rating" -> {
                            val value = readStringToken(reader)?.ifBlank { null }
                            if (rating.isNullOrBlank() && value != null) rating = value
                        }
                        "duration", "runtime" -> {
                            val value = readStringToken(reader)?.ifBlank { null }
                            if (duration.isNullOrBlank() && value != null) duration = value
                        }
                        "duration_secs" -> {
                            val seconds = readLongToken(reader)
                            if (duration.isNullOrBlank() && seconds != null) {
                                duration = secondsToRuntimeLabel(seconds.toString())
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            JsonToken.STRING -> {
                val raw = reader.nextString()
                val info = parseInfoObject(raw)
                plot = info?.optString("plot")
                    ?.ifBlank { info.optString("description") }
                    ?.ifBlank { info.optString("synopsis") }
                    ?.ifBlank { info.optString("overview") }
                    ?.ifBlank { info.optString("storyline") }
                    ?.ifBlank { null }
                cast = info?.optString("cast")
                    ?.ifBlank { info.optString("actors") }
                    ?.ifBlank { null }
                director = info?.optString("director")
                    ?.ifBlank { info.optString("directors") }
                    ?.ifBlank { null }
                genre = info?.optString("genre")
                    ?.ifBlank { info.optString("genres") }
                    ?.ifBlank { null }
                rating = info?.optString("rating")
                    ?.ifBlank { info.optString("vote_average") }
                    ?.ifBlank { info.optString("tmdb_rating") }
                    ?.ifBlank { null }
                duration = info?.optString("duration")
                    ?.ifBlank { info.optString("runtime") }
                    ?.ifBlank {
                        info.optString("duration_secs")
                            .takeIf { it.toLongOrNull() != null }
                            ?.let(::secondsToRuntimeLabel)
                    }
                    ?.ifBlank { null }
            }
            else -> reader.skipValue()
        }
        return VodInfoFields(
            plot = plot,
            cast = cast,
            director = director,
            genre = genre,
            rating = rating,
            duration = duration,
        )
    }

    private fun readStringToken(reader: JsonReader): String? =
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            else -> {
                reader.skipValue()
                null
            }
        }

    private fun readLongToken(reader: JsonReader): Long? =
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.NUMBER -> reader.nextLong()
            JsonToken.STRING -> reader.nextString().toLongOrNull()
            else -> {
                reader.skipValue()
                null
            }
        }

    private fun readCategoryIdToken(reader: JsonReader): String? =
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.NUMBER -> canonicalizeCategoryId(reader.nextLong().toString())
            JsonToken.STRING -> canonicalizeCategoryId(reader.nextString())
            else -> {
                reader.skipValue()
                null
            }
        }

    suspend fun parseSeries(raw: String, playlistId: Long = 0L): List<SeriesShow> {
        val out = ArrayList<SeriesShow>()
        parseSeriesBatched(raw, playlistId) { batch ->
            out.addAll(batch)
        }
        return out
    }

    suspend fun parseSeriesBatched(
        raw: String,
        playlistId: Long = 0L,
        batchSize: Int = VOD_CATALOG_BATCH_SIZE,
        onBatch: suspend (List<SeriesShow>) -> Unit
    ): Int {
        val result = XtreamCatalogStreamParser.parseSeriesCatalogStream(
            input = raw.byteInputStream(Charsets.UTF_8),
            playlistId = playlistId,
            batchSize = batchSize,
            parser = this
        ) { batch, _, _ ->
            onBatch(batch)
        }
        return result.parsedCount
    }

    internal fun parseSeriesItemFromJson(item: JSONObject?, playlistId: Long): SeriesShow? {
        item ?: return null
        val id = optLongId(item, "series_id") ?: return null
        val info = parseInfoObject(item.opt("info"))
        val infoPlot = info?.optString("plot")
            ?.ifBlank { info.optString("description") }
            ?.ifBlank { info.optString("synopsis") }
            ?.ifBlank { info.optString("overview") }
            ?.ifBlank { info.optString("storyline") }
            ?.ifBlank { null }
        val infoGenre = info?.optString("genre")
            ?.ifBlank { info.optString("genres") }
            ?.ifBlank { null }
        return SeriesShow(
            id = id,
            name = item.optString("name").ifBlank { "Series $id" },
            coverUrl = item.optString("cover").ifBlank { null },
            categoryId = optCategoryId(item),
            genre = item.optString("genre").ifBlank { null } ?: infoGenre,
            plot = item.optString("plot").ifBlank { null } ?: infoPlot,
            playlistId = playlistId
        )
    }

    fun parseSeriesInfo(
        raw: String,
        username: String,
        password: String,
        serverUrl: String,
        requestId: String? = null,
    ): SeriesDetail {
        val root = JSONObject(raw)
        val rootInfo = parseInfoObject(root.opt("info"))
        val req = reqPrefix(requestId)
        Log.d(TAG, "${req}get_series_info PARSER begin")
        logSeriesInfoFieldCoverage(root, rootInfo, requestId)
        val plot = rootInfo?.optString("plot")
            ?.ifBlank { rootInfo.optString("description") }
            ?.ifBlank { rootInfo.optString("synopsis") }
            ?.ifBlank { rootInfo.optString("overview") }
            ?.ifBlank { rootInfo.optString("storyline") }
            ?.ifBlank { null }
        val episodes = root.optJSONObject("episodes") ?: run {
            val arr = root.optJSONArray("episodes")
            if (arr != null) {
                JSONObject().put("1", arr)
            } else {
                return SeriesDetail(seasons = emptyList(), plot = plot)
            }
        }
        val seasons = mutableListOf<SeriesSeason>()
        val keys = episodes.keys()
        while (keys.hasNext()) {
            val seasonKey = keys.next()
            val arr = when (val seasonNode = episodes.opt(seasonKey)) {
                is JSONArray -> seasonNode
                is JSONObject -> seasonNode.optJSONArray("episodes")
                    ?: seasonNode.optJSONArray("data")
                    ?: seasonNode.optJSONArray("items")
                else -> null
            } ?: continue
            val episodeRows = mutableListOf<SeriesEpisode>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id").toLongOrNull() ?: continue
                val info = parseInfoObject(item.opt("info"))
                val ext = item.optString("container_extension").ifBlank { "mp4" }
                val title = resolveEpisodeTitle(item, info)
                val directSource = sanitizeDirectSource(item.optString("direct_source"))
                val url = buildSeriesStreamUrl(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    streamId = id.toString(),
                    extension = ext,
                    directSource = directSource
                )
                val episodeNumber = item.optInt("episode_num", -1).takeIf { it > 0 }
                    ?: info?.optInt("episode_num", -1)?.takeIf { it > 0 }
                    ?: (i + 1)
                episodeRows += SeriesEpisode(
                    id = id,
                    title = title,
                    extension = ext,
                    streamUrl = url,
                    plot = info?.optString("plot")
                        ?.ifBlank { info.optString("description") }
                        ?.ifBlank { info.optString("synopsis") }
                        ?.ifBlank { info.optString("overview") }
                        ?.ifBlank { info.optString("storyline") }
                        ?.ifBlank { null },
                    duration = info?.optString("duration")
                        ?.ifBlank { info.optString("runtime") }
                        ?.ifBlank {
                            info.optString("duration_secs")
                                .takeIf { it.toLongOrNull() != null }
                                ?.let(::secondsToRuntimeLabel)
                        }
                        ?.ifBlank { null },
                    episodeNumber = episodeNumber
                )
            }
            val seasonNo = seasonKey.toIntOrNull() ?: (seasons.size + 1)
            seasons += SeriesSeason(number = seasonNo, episodes = episodeRows)
        }
        val detail = SeriesDetail(seasons = seasons.sortedBy { it.number }, plot = plot)
        Log.d(TAG, "${req}get_series_info PARSER parsed seasons=${detail.seasons.size} plot=${!detail.plot.isNullOrBlank()}")
        return detail
    }

    fun parseVodInfo(
        raw: String,
        username: String,
        password: String,
        serverUrl: String,
        playlistId: Long = 0L,
        fallbackStreamId: Long? = null,
        requestId: String? = null,
    ): VodItem? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val movieData = parseInfoObject(root.opt("movie_data"))
            ?: parseInfoObject(root.opt("movie"))
            ?: JSONObject()
        val info = parseInfoObject(root.opt("info")) ?: JSONObject()
        val req = reqPrefix(requestId)
        Log.d(TAG, "${req}get_vod_info PARSER begin")
        logVodInfoFieldCoverage(root, movieData, info, requestId)

        val streamId = optLongId(movieData, "stream_id", "movie_id", "vod_id", "id")
            ?: optLongId(info, "stream_id", "movie_id", "vod_id", "id")
            ?: optLongId(root, "stream_id", "movie_id", "vod_id", "id")
            ?: fallbackStreamId
            ?: return null

        val extension = sequenceOf(
            movieData.optString("container_extension"),
            info.optString("container_extension")
        ).firstOrNull { it.isNotBlank() } ?: "mp4"
        val directSource = sequenceOf(
            movieData.optString("direct_source"),
            info.optString("direct_source")
        ).map { it.ifBlank { null } }
            .firstOrNull { !it.isNullOrBlank() }

        val plot = sequenceOf(
            info.optString("plot"),
            info.optString("description"),
            info.optString("synopsis"),
            info.optString("overview"),
            info.optString("storyline"),
            movieData.optString("plot"),
            movieData.optString("description"),
            movieData.optString("synopsis"),
            movieData.optString("overview"),
            movieData.optString("storyline")
        ).map { it.trim() }
            .firstOrNull { it.isNotBlank() }

        val cast = sequenceOf(
            info.optString("cast"),
            info.optString("actors"),
            movieData.optString("cast"),
            movieData.optString("actors")
        ).map { it.trim() }
            .firstOrNull { it.isNotBlank() }

        val director = sequenceOf(
            info.optString("director"),
            info.optString("directors"),
            movieData.optString("director"),
            movieData.optString("directors")
        ).map { it.trim() }
            .firstOrNull { it.isNotBlank() }

        val genre = sequenceOf(
            info.optString("genre"),
            info.optString("genres"),
            movieData.optString("genre"),
            movieData.optString("genres")
        ).map { it.trim() }
            .firstOrNull { it.isNotBlank() }

        val rating = sequenceOf(
            info.optString("rating"),
            info.optString("vote_average"),
            info.optString("tmdb_rating"),
            movieData.optString("rating"),
            movieData.optString("vote_average")
        ).map { it.trim() }
            .firstOrNull { it.isNotBlank() }

        val duration = sequenceOf(
            info.optString("duration"),
            info.optString("runtime"),
            info.optString("duration_secs").takeIf { it.toLongOrNull() != null }?.let(::secondsToRuntimeLabel),
            movieData.optString("duration"),
            movieData.optString("runtime"),
            movieData.optString("duration_secs").takeIf { it.toLongOrNull() != null }?.let(::secondsToRuntimeLabel)
        ).mapNotNull { it?.trim() }
            .firstOrNull { it.isNotBlank() }

        val title = sequenceOf(
            movieData.optString("name"),
            movieData.optString("title"),
            info.optString("name"),
            info.optString("title")
        ).map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: "VOD $streamId"

        val categoryId = optCategoryId(movieData)
            ?: optCategoryId(info)
            ?: optCategoryId(root)

        val addedEpochSec = sequenceOf(
            movieData.optString("added").toLongOrNull(),
            info.optString("added").toLongOrNull(),
            root.optString("added").toLongOrNull()
        ).firstOrNull { it != null }

        logReleaseDateShadowRecovery(root, movieData, info, addedEpochSec, requestId)

        val poster = sequenceOf(
            movieData.optString("stream_icon"),
            movieData.optString("movie_image"),
            info.optString("movie_image"),
            info.optString("cover"),
            info.optString("poster")
        ).map { it.trim() }
            .firstOrNull { it.isNotBlank() }

        val item = VodItem(
            id = streamId,
            title = title,
            streamId = streamId,
            streamUrl = buildMovieStreamUrl(
                serverUrl = serverUrl,
                username = username,
                password = password,
                streamId = streamId.toString(),
                extension = extension,
                directSource = directSource
            ),
            posterUrl = poster,
            plot = plot,
            cast = cast,
            director = director,
            genre = genre,
            rating = rating,
            duration = duration,
            categoryId = categoryId,
            addedEpochSec = addedEpochSec,
            playlistId = playlistId
        )
        Log.d(TAG, "${req}get_vod_info PARSER parsed streamId=${item.streamId} title=${item.title.take(80)}")
        return item
    }

    private fun logVodInfoFieldCoverage(root: JSONObject, movieData: JSONObject, info: JSONObject, requestId: String?) {
        val rootHandled = setOf("movie_data", "movie", "info", "stream_id", "movie_id", "vod_id", "id", "added")
        val movieHandled = setOf(
            "stream_id", "movie_id", "vod_id", "id", "container_extension", "direct_source",
            "name", "title", "stream_icon", "movie_image", "category_id", "categoryId", "categoryid",
            "vod_category_id", "series_category_id", "category_ids", "added", "plot", "description",
            "synopsis", "overview", "storyline", "cast", "actors", "director", "directors", "genre", "genres",
            "rating", "vote_average", "duration", "runtime", "duration_secs"
        )
        val infoHandled = setOf(
            "stream_id", "movie_id", "vod_id", "id", "container_extension", "direct_source",
            "name", "title", "movie_image", "cover", "poster", "category_id", "categoryId", "categoryid",
            "vod_category_id", "series_category_id", "category_ids", "added", "plot", "description",
            "synopsis", "overview", "storyline", "cast", "actors", "director", "directors", "genre", "genres",
            "rating", "vote_average", "tmdb_rating", "duration", "runtime", "duration_secs"
        )
        val rootKeys = jsonObjectKeys(root)
        val movieKeys = jsonObjectKeys(movieData)
        val infoKeys = jsonObjectKeys(info)
        Log.d(
            TAG,
            "${reqPrefix(requestId)}get_vod_info FIELD_TRACE rootKeys=$rootKeys ignoredRoot=${rootKeys - rootHandled} " +
                "movieKeys=$movieKeys ignoredMovie=${movieKeys - movieHandled} " +
                "infoKeys=$infoKeys ignoredInfo=${infoKeys - infoHandled}"
        )
    }

    private fun logSeriesInfoFieldCoverage(root: JSONObject, info: JSONObject?, requestId: String?) {
        val rootHandled = setOf("info", "episodes")
        val infoHandled = setOf("plot", "description", "synopsis", "overview", "storyline")
        val rootKeys = jsonObjectKeys(root)
        val infoKeys = jsonObjectKeys(info)
        val episodeKeys = mutableSetOf<String>()
        val episodes = root.opt("episodes")
        when (episodes) {
            is JSONObject -> {
                val seasonKeys = episodes.keys()
                if (seasonKeys.hasNext()) {
                    val firstSeasonKey = seasonKeys.next()
                    when (val seasonNode = episodes.opt(firstSeasonKey)) {
                        is JSONArray -> {
                            val firstEpisode = seasonNode.optJSONObject(0)
                            episodeKeys += jsonObjectKeys(firstEpisode)
                            episodeKeys += jsonObjectKeys(parseInfoObject(firstEpisode?.opt("info")))
                        }
                        is JSONObject -> {
                            val arr = seasonNode.optJSONArray("episodes")
                                ?: seasonNode.optJSONArray("data")
                                ?: seasonNode.optJSONArray("items")
                            val firstEpisode = arr?.optJSONObject(0)
                            episodeKeys += jsonObjectKeys(firstEpisode)
                            episodeKeys += jsonObjectKeys(parseInfoObject(firstEpisode?.opt("info")))
                        }
                    }
                }
            }
            is JSONArray -> {
                val firstEpisode = episodes.optJSONObject(0)
                episodeKeys += jsonObjectKeys(firstEpisode)
                episodeKeys += jsonObjectKeys(parseInfoObject(firstEpisode?.opt("info")))
            }
        }
        val episodeHandled = setOf(
            "id", "container_extension", "title", "title_en", "titles", "name", "direct_source",
            "episode_num", "info", "plot", "description", "synopsis", "overview", "storyline",
            "duration", "runtime", "duration_secs"
        )
        Log.d(
            TAG,
            "${reqPrefix(requestId)}get_series_info FIELD_TRACE rootKeys=$rootKeys ignoredRoot=${rootKeys - rootHandled} " +
                "infoKeys=$infoKeys ignoredInfo=${infoKeys - infoHandled} " +
                "episodeSampleKeys=$episodeKeys ignoredEpisode=${episodeKeys - episodeHandled}"
        )
    }

    private fun logReleaseDateShadowRecovery(
        root: JSONObject,
        movieData: JSONObject,
        info: JSONObject,
        addedEpochSec: Long?,
        requestId: String?,
    ) {
        fun firstNonBlank(vararg values: String): String? = values
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }

        val releaseDate = firstNonBlank(
            info.optString("releaseDate"),
            info.optString("releasedate"),
            info.optString("release_date"),
            movieData.optString("releaseDate"),
            movieData.optString("releasedate"),
            movieData.optString("release_date"),
            root.optString("releaseDate"),
            root.optString("releasedate"),
            root.optString("release_date")
        )
        val added = firstNonBlank(
            movieData.optString("added"),
            info.optString("added"),
            root.optString("added")
        )
        val normalized = releaseDate ?: added
        val recovery = when {
            !releaseDate.isNullOrBlank() -> "release_date -> release_date"
            !added.isNullOrBlank() -> "release_date -> added (fallback)"
            else -> "release_date -> <missing>"
        }
        Log.d(
            TAG,
            "${reqPrefix(requestId)}get_vod_info FIELD_RECOVERY normalized_release_date=$normalized added_epoch=$addedEpochSec"
        )
        Log.d(TAG, "${reqPrefix(requestId)}get_vod_info FIELD_RECOVERY $recovery")
    }

    private fun jsonObjectKeys(obj: JSONObject?): Set<String> {
        if (obj == null) return emptySet()
        val out = linkedSetOf<String>()
        val keys = obj.keys()
        while (keys.hasNext()) out += keys.next()
        return out
    }

    private fun resolveEpisodeTitle(item: JSONObject, info: JSONObject?): String {
        sequenceOf(
            item.optString("title_en"),
            info?.optString("title_en"),
            item.optJSONObject("titles")?.optString("en"),
            item.optJSONObject("titles")?.optString("en-US"),
            info?.optJSONObject("titles")?.optString("en"),
            info?.optJSONObject("titles")?.optString("en-US"),
        ).firstOrNull { !it.isNullOrBlank() }?.let { return it.trim() }

        val candidates = buildList {
            item.optString("title").trim().takeIf { it.isNotEmpty() }?.let(::add)
            info?.optString("title")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            item.optString("name").trim().takeIf { it.isNotEmpty() }?.let(::add)
            info?.optString("name")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        }
        if (candidates.isEmpty()) {
            val id = item.optString("id").ifBlank { "?" }
            return "Episode $id"
        }
        return candidates.minWith(compareBy({ titleEnglishPreferenceRank(it) }, { it.length }))
    }

    private fun titleEnglishPreferenceRank(title: String): Int {
        val code = com.grid.tv.feature.vod.parseVodContentLanguageCode(title)?.uppercase()
        return when (code) {
            null -> 1
            "EN", "US", "GB", "UK" -> 0
            else -> 2
        }
    }

    fun parseSimpleDataTable(raw: String): List<Pair<Long, Long>> {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("epg_listings") ?: JSONArray()
        val out = mutableListOf<Pair<Long, Long>>()
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val start = row.optString("start_timestamp").toLongOrNull() ?: continue
            val end = row.optString("stop_timestamp").toLongOrNull() ?: continue
            out += start * 1000L to end * 1000L
        }
        return out
    }

    /**
     * Parses Xtream `get_short_epg` JSON into programme rows keyed by [channelEpgId]
     * (the playlist channel's tvg-id / epg id, not the provider xmltv id).
     */
    fun parseShortEpg(
        raw: String,
        channelEpgId: String,
        windowStart: Long,
        windowEnd: Long,
        playlistId: Long = 0L
    ): List<ProgramEntity> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<ProgramEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val startSec = row.optString("start_timestamp").toLongOrNull()
                ?: row.optString("start").toLongOrNull()
            val endSec = row.optString("stop_timestamp").toLongOrNull()
                ?: row.optString("end").toLongOrNull()
            if (startSec == null || endSec == null) continue
            val startMs = if (startSec > 1_000_000_000_000L) startSec else startSec * 1000L
            val endMs = if (endSec > 1_000_000_000_000L) endSec else endSec * 1000L
            if (endMs <= windowStart || startMs >= windowEnd) continue
            val title = EpgProgramTextDecoder.decode(row.optString("title")).ifBlank { "Untitled" }
            val description = EpgProgramTextDecoder.decode(
                row.optString("description").ifBlank { row.optString("desc") }
            ).trim()
            val genre = normalizeShortEpgGenre(row.optString("category"))
            out += ProgramEntity(
                id = XmlTvParser.stableProgramId(playlistId, channelEpgId, startMs),
                playlistId = playlistId,
                channelEpgId = channelEpgId,
                title = title,
                description = description,
                startTime = startMs,
                endTime = endMs,
                genre = genre
            )
        }
        return out
    }

    private fun normalizeShortEpgGenre(value: String): String {
        val lower = value.lowercase()
        return when {
            "news" in lower -> "NEWS"
            "sport" in lower -> "SPORTS"
            "movie" in lower || "film" in lower -> "MOVIES"
            "kids" in lower || "children" in lower -> "KIDS"
            else -> "GENERAL"
        }
    }

    private fun plotOrDescription(item: JSONObject): String? {
        item.optString("plot").takeIf { it.isNotBlank() }?.let { return it }
        item.optString("description").takeIf { it.isNotBlank() }?.let { return it }
        item.optString("synopsis").takeIf { it.isNotBlank() }?.let { return it }
        item.optString("overview").takeIf { it.isNotBlank() }?.let { return it }
        item.optString("storyline").takeIf { it.isNotBlank() }?.let { return it }
        item.optJSONObject("info")?.let { info ->
            info.optString("plot").takeIf { it.isNotBlank() }?.let { return it }
            info.optString("description").takeIf { it.isNotBlank() }?.let { return it }
            info.optString("synopsis").takeIf { it.isNotBlank() }?.let { return it }
            info.optString("overview").takeIf { it.isNotBlank() }?.let { return it }
            info.optString("storyline").takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun optLongId(item: JSONObject, vararg keys: String): Long? {
        for (key in keys) {
            if (!item.has(key)) continue
            when (val value = item.opt(key)) {
                is Number -> return value.toLong()
                is String -> value.toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun optCategoryId(item: JSONObject): String? {
        val keys = listOf(
            "category_id",
            "categoryId",
            "categoryid",
            "cat_id",
            "id",
            "vod_category_id",
            "series_category_id"
        )
        keys.forEach { key ->
            if (!item.has(key)) return@forEach
            val normalized = normalizeCategoryIdValue(item.opt(key))
            if (!normalized.isNullOrBlank()) return normalized
        }
        if (item.has("category_ids")) {
            normalizeCategoryIdValue(item.opt("category_ids"))?.let { return it }
        }
        return null
    }

    private fun optCategoryName(item: JSONObject, fallback: String): String {
        val id = optCategoryId(item)
        val candidates = listOf(
            item.optString("category_name"),
            item.optString("cat_name"),
            item.optString("genre_name"),
            item.optString("name"),
            item.optString("title"),
            item.optString("category"),
            item.optString("display_name")
        )
        for (candidate in candidates) {
            val trimmed = candidate.trim()
            if (trimmed.isBlank()) continue
            if (id != null && trimmed == id) continue
            if (trimmed.all { it.isDigit() }) continue
            return trimmed
        }
        return fallback
    }

    private fun extractRawCategoryName(item: JSONObject): String? {
        val candidates = listOf(
            item.optString("category_name"),
            item.optString("cat_name"),
            item.optString("genre_name"),
            item.optString("name"),
            item.optString("title"),
            item.optString("category"),
            item.optString("display_name")
        )
        return candidates
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun reqPrefix(requestId: String?): String =
        if (requestId.isNullOrBlank()) "" else "[REQ_${requestId.take(8)}] "

    private fun normalizeCategoryIdValue(value: Any?): String? {
        return when (value) {
            null -> null
            is Number -> canonicalizeCategoryId(value.toLong().toString())
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isBlank()) {
                    null
                } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    val arr = runCatching { JSONArray(trimmed) }.getOrNull()
                    if (arr == null) {
                        null
                    } else {
                        var parsed: String? = null
                        for (i in 0 until arr.length()) {
                            val candidate = normalizeCategoryIdValue(arr.opt(i))
                            if (!candidate.isNullOrBlank()) {
                                parsed = candidate
                                break
                            }
                        }
                        parsed
                    }
                } else {
                    trimmed.split(',', ';')
                        .mapNotNull { canonicalizeCategoryId(it) }
                        .firstOrNull()
                }
            }
            is JSONArray -> {
                var parsed: String? = null
                for (i in 0 until value.length()) {
                    val candidate = normalizeCategoryIdValue(value.opt(i))
                    if (!candidate.isNullOrBlank()) {
                        parsed = candidate
                        break
                    }
                }
                parsed
            }
            else -> null
        }
    }

    private fun canonicalizeCategoryId(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        val normalized = if (trimmed.endsWith(".0") && trimmed.dropLast(2).all { it.isDigit() }) {
            trimmed.dropLast(2)
        } else {
            trimmed
        }
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun parseInfoObject(value: Any?): JSONObject? = when (value) {
        is JSONObject -> value
        is String -> {
            val trimmed = value.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                runCatching { JSONObject(trimmed) }.getOrNull()
            } else {
                null
            }
        }
        else -> null
    }

    private fun secondsToRuntimeLabel(rawSeconds: String): String? {
        val totalSeconds = rawSeconds.toLongOrNull() ?: return null
        if (totalSeconds <= 0L) return null
        val totalMinutes = totalSeconds / 60L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) {
            if (minutes > 0L) "${hours}h ${minutes}m" else "${hours}h"
        } else {
            "${minutes}m"
        }
    }

    private fun previewHasCatalogArray(preview: String): Boolean =
        runCatching {
            java.io.InputStreamReader(preview.byteInputStream(), Charsets.UTF_8).use { reader ->
                com.google.gson.stream.JsonReader(reader).use { json ->
                    XtreamCatalogStreamParser.seekCatalogArray(
                        json,
                        setOf(
                            "vod_streams",
                            "movies",
                            "movie_data",
                            "streams",
                            "data",
                            "js"
                        )
                    )
                }
            }
        }.getOrDefault(false)

    private fun sanitizeJsonPayload(raw: String): String =
        raw.trim().removePrefix("\uFEFF").trim()

    private fun parseJsonArray(raw: String, wrapperKeys: List<String>): JSONArray? {
        val trimmed = sanitizeJsonPayload(raw)
        if (trimmed.isBlank()) return null
        return try {
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> findWrappedJsonArray(JSONObject(trimmed), wrapperKeys)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findWrappedJsonArray(obj: JSONObject, wrapperKeys: List<String>): JSONArray? {
        for (key in wrapperKeys) {
            if (obj.has(key) && obj.opt(key) is JSONArray) {
                return obj.getJSONArray(key)
            }
        }
        val nested = obj.optJSONObject("data") ?: return null
        for (key in wrapperKeys) {
            if (nested.has(key) && nested.opt(key) is JSONArray) {
                return nested.getJSONArray(key)
            }
        }
        return if (nested.opt("data") is JSONArray) nested.getJSONArray("data") else null
    }

    /** @deprecated internal callers should pass explicit wrapper keys */
    private fun parseJsonArray(raw: String): JSONArray? = parseJsonArray(raw, VOD_ARRAY_WRAPPER_KEYS)
}
