package com.grid.tv.data.network.parser

import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.domain.model.EpgResolutionStatus
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodItem
import java.net.URI
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class XtreamParser {

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
            val epgId = item.optString("epg_channel_id").ifBlank { null }
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
                epgResolutionStatus = if (!epgId.isNullOrBlank()) EpgResolutionStatus.CONFIRMED.name else EpgResolutionStatus.UNRESOLVED.name,
                epgResolutionConfidence = if (!epgId.isNullOrBlank()) 100 else 0,
                epgResolutionSource = if (!epgId.isNullOrBlank()) "xtream" else null
            )
        }
        return out
    }

    fun parseVodCategories(raw: String, playlistId: Long = 0L): List<com.grid.tv.domain.model.VodCategory> {
        val arr = parseJsonArray(raw) ?: return emptyList()
        val out = ArrayList<com.grid.tv.domain.model.VodCategory>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("category_id")
            val name = item.optString("category_name").ifBlank { "Movies" }
            if (id.isNotBlank()) out += com.grid.tv.domain.model.VodCategory(id, name, playlistId)
        }
        return out
    }

    fun parseVod(raw: String, username: String, password: String, serverUrl: String, playlistId: Long = 0L): List<VodItem> {
        val arr = parseJsonArray(raw) ?: return emptyList()
        val out = ArrayList<VodItem>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("stream_id").toLongOrNull() ?: continue
            val ext = item.optString("container_extension").ifBlank { "mp4" }
            val directSource = item.optString("direct_source").ifBlank { null }
            val url = buildMovieStreamUrl(serverUrl, username, password, id.toString(), ext, directSource)
            val title = item.optString("name").ifBlank { "VOD $id" }
            out += VodItem(
                id = id,
                title = title,
                streamId = id,
                streamUrl = url,
                posterUrl = item.optString("stream_icon").ifBlank { null },
                plot = item.optString("plot").ifBlank { null },
                cast = item.optString("cast").ifBlank { null },
                director = item.optString("director").ifBlank { null },
                genre = item.optString("genre").ifBlank { null },
                rating = item.optString("rating").ifBlank { null },
                duration = item.optString("duration").ifBlank { null },
                categoryId = item.optString("category_id").ifBlank { null },
                addedEpochSec = item.optString("added").toLongOrNull(),
                playlistId = playlistId
            )
        }
        return out
    }

    fun parseSeries(raw: String, playlistId: Long = 0L): List<SeriesShow> {
        val arr = parseJsonArray(raw) ?: return emptyList()
        val out = ArrayList<SeriesShow>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("series_id").toLongOrNull() ?: continue
            out += SeriesShow(
                id = id,
                name = item.optString("name").ifBlank { "Series $id" },
                coverUrl = item.optString("cover").ifBlank { null },
                categoryId = item.optString("category_id").ifBlank { null },
                genre = item.optString("genre").ifBlank { null },
                playlistId = playlistId
            )
        }
        return out
    }

    fun parseSeriesInfo(raw: String, username: String, password: String, serverUrl: String): List<SeriesSeason> {
        val root = JSONObject(raw)
        val episodes = root.optJSONObject("episodes") ?: return emptyList()
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
                val title = item.optString("title").ifBlank { "Episode $id" }
                val url = "$serverUrl/series/${encodePath(username)}/${encodePath(password)}/$id.$ext"
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
        return seasons.sortedBy { it.number }
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

    private fun parseJsonArray(raw: String): JSONArray? {
        if (raw.isBlank()) return null
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            null
        }
    }
}
