package com.grid.tv.data.network.parser

import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.data.db.entity.ProgramEntity
import com.grid.tv.domain.model.EpgResolutionStatus
import com.grid.tv.domain.model.SeriesDetail
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodItem
import java.net.URI
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class XtreamParser {

    companion object {
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

    fun parseVodCategories(raw: String, playlistId: Long = 0L): List<com.grid.tv.domain.model.VodCategory> {
        val arr = parseJsonArray(raw, CATEGORY_ARRAY_WRAPPER_KEYS) ?: return emptyList()
        val out = ArrayList<com.grid.tv.domain.model.VodCategory>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = optCategoryId(item) ?: continue
            val name = optCategoryName(item, fallback = "Movies")
            out += com.grid.tv.domain.model.VodCategory(id, name, playlistId)
        }
        return com.grid.tv.domain.model.VodCategoryNameResolver.normalizeList(out)
    }

    fun parseSeriesCategories(raw: String, playlistId: Long = 0L): List<com.grid.tv.domain.model.VodCategory> {
        val arr = parseJsonArray(raw, CATEGORY_ARRAY_WRAPPER_KEYS) ?: return emptyList()
        val out = ArrayList<com.grid.tv.domain.model.VodCategory>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = optCategoryId(item) ?: continue
            val name = optCategoryName(item, fallback = "Series")
            out += com.grid.tv.domain.model.VodCategory(id, name, playlistId)
        }
        return com.grid.tv.domain.model.VodCategoryNameResolver.normalizeList(out)
    }

    fun parseVodArrayLength(raw: String): Int = parseJsonArray(raw, VOD_ARRAY_WRAPPER_KEYS)?.length() ?: 0

    fun parseSeriesArrayLength(raw: String): Int = parseJsonArray(raw, SERIES_ARRAY_WRAPPER_KEYS)?.length() ?: 0

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
                obj.has("user_info") && parseJsonArray(trimmed, VOD_ARRAY_WRAPPER_KEYS) == null ->
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
        val arr = parseJsonArray(raw, VOD_ARRAY_WRAPPER_KEYS) ?: return 0
        val batch = ArrayList<VodItem>(batchSize)
        for (i in 0 until arr.length()) {
            val parsed = parseVodItem(arr.optJSONObject(i), username, password, serverUrl, playlistId)
                ?: continue
            batch += parsed
            if (batch.size >= batchSize) {
                onBatch(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
        }
        return arr.length()
    }

    private fun parseVodItem(
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
        return VodItem(
            id = id,
            title = title,
            streamId = id,
            streamUrl = url,
            posterUrl = item.optString("stream_icon").ifBlank { null },
            plot = plotOrDescription(item),
            cast = item.optString("cast").ifBlank { null },
            director = item.optString("director").ifBlank { null },
            genre = item.optString("genre").ifBlank { null },
            rating = item.optString("rating").ifBlank { null },
            duration = item.optString("duration").ifBlank { null },
            categoryId = optCategoryId(item),
            addedEpochSec = item.optString("added").toLongOrNull(),
            playlistId = playlistId
        )
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
        val arr = parseJsonArray(raw, SERIES_ARRAY_WRAPPER_KEYS) ?: return 0
        val batch = ArrayList<SeriesShow>(batchSize)
        for (i in 0 until arr.length()) {
            val parsed = parseSeriesItem(arr.optJSONObject(i), playlistId) ?: continue
            batch += parsed
            if (batch.size >= batchSize) {
                onBatch(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
        }
        return arr.length()
    }

    private fun parseSeriesItem(item: JSONObject?, playlistId: Long): SeriesShow? {
        item ?: return null
        val id = optLongId(item, "series_id") ?: return null
        return SeriesShow(
            id = id,
            name = item.optString("name").ifBlank { "Series $id" },
            coverUrl = item.optString("cover").ifBlank { null },
            categoryId = optCategoryId(item),
            genre = item.optString("genre").ifBlank { null },
            plot = item.optString("plot").ifBlank { null },
            playlistId = playlistId
        )
    }

    fun parseSeriesInfo(raw: String, username: String, password: String, serverUrl: String): SeriesDetail {
        val root = JSONObject(raw)
        val plot = root.optJSONObject("info")?.optString("plot")?.ifBlank { null }
        val episodes = root.optJSONObject("episodes") ?: return SeriesDetail(seasons = emptyList(), plot = plot)
        val seasons = mutableListOf<SeriesSeason>()
        val keys = episodes.keys()
        while (keys.hasNext()) {
            val seasonKey = keys.next()
            val arr = episodes.optJSONArray(seasonKey) ?: continue
            val episodeRows = mutableListOf<SeriesEpisode>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id").toLongOrNull() ?: continue
                val info = item.optJSONObject("info")
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
                    plot = info?.optString("plot")?.ifBlank { null },
                    duration = info?.optString("duration")?.ifBlank { null },
                    episodeNumber = episodeNumber
                )
            }
            val seasonNo = seasonKey.toIntOrNull() ?: (seasons.size + 1)
            seasons += SeriesSeason(number = seasonNo, episodes = episodeRows)
        }
        return SeriesDetail(seasons = seasons.sortedBy { it.number }, plot = plot)
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
        windowEnd: Long
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
            val title = row.optString("title").ifBlank { "Untitled" }
            val description = row.optString("description").ifBlank {
                row.optString("desc")
            }.trim()
            val genre = normalizeShortEpgGenre(row.optString("category"))
            out += ProgramEntity(
                id = XmlTvParser.stableProgramId(channelEpgId, startMs),
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
        item.optJSONObject("info")?.let { info ->
            info.optString("plot").takeIf { it.isNotBlank() }?.let { return it }
            info.optString("description").takeIf { it.isNotBlank() }?.let { return it }
            info.optString("synopsis").takeIf { it.isNotBlank() }?.let { return it }
            info.optString("overview").takeIf { it.isNotBlank() }?.let { return it }
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
        if (!item.has("category_id")) return null
        return when (val value = item.opt("category_id")) {
            is Number -> value.toLong().toString()
            is String -> value.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun optCategoryName(item: JSONObject, fallback: String): String {
        val id = optCategoryId(item)
        val candidates = listOf(
            item.optString("category_name"),
            item.optString("name"),
            item.optString("title"),
            item.optString("category")
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
