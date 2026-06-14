package com.neuropulse.tv.data.network.parser

import com.neuropulse.tv.data.db.entity.ChannelEntity
import com.neuropulse.tv.domain.model.EpgResolutionStatus
import com.neuropulse.tv.domain.model.SeriesEpisode
import com.neuropulse.tv.domain.model.SeriesSeason
import com.neuropulse.tv.domain.model.SeriesShow
import com.neuropulse.tv.domain.model.VodItem
import org.json.JSONArray
import org.json.JSONObject

class XtreamParser {

    data class AuthPayload(
        val status: String,
        val expiryDateEpochSec: Long?,
        val maxConnections: Int?,
        val serverUrl: String
    )

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

    fun parseLiveCategories(raw: String): Map<String, String> {
        val arr = JSONArray(raw)
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
        val arr = JSONArray(raw)
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
            val streamUrl = "$serverUrl/$username/$password/$streamId"
            out += ChannelEntity(
                number = number,
                name = name,
                groupName = group,
                logoUrl = logo,
                epgId = epgId,
                streamUrl = streamUrl,
                playlistId = playlistId,
                epgResolutionStatus = if (!epgId.isNullOrBlank()) EpgResolutionStatus.CONFIRMED.name else EpgResolutionStatus.UNRESOLVED.name,
                epgResolutionConfidence = if (!epgId.isNullOrBlank()) 100 else 0,
                epgResolutionSource = if (!epgId.isNullOrBlank()) "xtream" else null
            )
        }
        return out
    }

    fun parseVodCategories(raw: String, playlistId: Long = 0L): List<com.neuropulse.tv.domain.model.VodCategory> {
        val arr = JSONArray(raw)
        val out = ArrayList<com.neuropulse.tv.domain.model.VodCategory>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("category_id")
            val name = item.optString("category_name").ifBlank { "Movies" }
            if (id.isNotBlank()) out += com.neuropulse.tv.domain.model.VodCategory(id, name, playlistId)
        }
        return out
    }

    fun parseVod(raw: String, username: String, password: String, serverUrl: String, playlistId: Long = 0L): List<VodItem> {
        val arr = JSONArray(raw)
        val out = ArrayList<VodItem>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("stream_id").toLongOrNull() ?: continue
            val ext = item.optString("container_extension").ifBlank { "mp4" }
            val url = "$serverUrl/$username/$password/$id.$ext"
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
        val arr = JSONArray(raw)
        val out = ArrayList<SeriesShow>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("series_id").toLongOrNull() ?: continue
            out += SeriesShow(
                id = id,
                name = item.optString("name").ifBlank { "Series $id" },
                coverUrl = item.optString("cover").ifBlank { null },
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
                val url = "$serverUrl/series/$username/$password/$id.$ext"
                episodeRows += SeriesEpisode(
                    id = id,
                    title = title,
                    extension = ext,
                    streamUrl = url,
                    plot = info?.optString("plot")?.ifBlank { null },
                    duration = info?.optString("duration")?.ifBlank { null }
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
}
