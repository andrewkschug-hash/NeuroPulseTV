package com.neuropulse.tv.data.network.stalker

import com.neuropulse.tv.data.db.entity.ChannelEntity
import com.neuropulse.tv.domain.model.EpgResolutionStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StalkerPortalClient @Inject constructor(
    private val client: OkHttpClient
) {
    data class Session(
        val portalBase: String,
        val mac: String,
        val token: String
    )

    suspend fun connect(portalUrl: String, mac: String): Session {
        val base = normalizePortalBase(portalUrl)
        val handshake = request(
            base = base,
            mac = mac,
            path = "server/load.php?type=stb&action=handshake&token=&prehash=false&JsHttpRequest=1-xml"
        )
        val token = extractToken(handshake)
            ?: throw IllegalStateException("Portal handshake failed")
        return Session(base, mac.uppercase(), token)
    }

    suspend fun fetchChannels(session: Session, playlistId: Long): List<ChannelEntity> {
        val raw = request(
            base = session.portalBase,
            mac = session.mac,
            token = session.token,
            path = "server/load.php?type=itv&action=get_all_channels&force_ch_link_check=&JsHttpRequest=1-xml"
        )
        return parseChannels(raw, session, playlistId)
    }

    private fun request(
        base: String,
        mac: String,
        path: String,
        token: String? = null
    ): String {
        val url = if (base.endsWith("/")) "$base$path" else "$base/$path"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MAG_USER_AGENT)
            .header("X-User-Agent", "Model: MAG250; Link: Ethernet")
            .header("Cookie", buildCookie(mac, token))
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Portal request failed (${response.code})")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun buildCookie(mac: String, token: String?): String {
        val parts = mutableListOf(
            "mac=${mac.uppercase()}",
            "stb_lang=en",
            "timezone=GMT"
        )
        if (!token.isNullOrBlank()) parts += "token=$token"
        return parts.joinToString("; ")
    }

    private fun extractToken(raw: String): String? {
        val json = unwrapJsResponse(raw) ?: return null
        return json.optString("token").ifBlank { null }
    }

    private fun parseChannels(raw: String, session: Session, playlistId: Long): List<ChannelEntity> {
        val root = unwrapJsResponse(raw) ?: throw IllegalStateException("Invalid channel response")
        val data = root.optJSONArray("data") ?: JSONArray()
        val out = ArrayList<ChannelEntity>(data.length())
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val name = item.optString("name").ifBlank { continue }
            val number = item.optString("number").toIntOrNull() ?: (i + 1)
            val cmd = item.optString("cmd").ifBlank { item.optString("id") }
            if (cmd.isBlank()) continue
            val logo = item.optString("logo").ifBlank { null }
            val streamUrl = resolveStreamUrl(session, cmd)
            out += ChannelEntity(
                number = number,
                name = name,
                groupName = "Live",
                logoUrl = logo,
                epgId = null,
                streamUrl = streamUrl,
                playlistId = playlistId,
                epgResolutionStatus = EpgResolutionStatus.UNRESOLVED.name
            )
        }
        if (out.isEmpty()) throw IllegalStateException("No channels returned from portal")
        return out
    }

    private fun resolveStreamUrl(session: Session, cmd: String): String {
        if (cmd.startsWith("http", ignoreCase = true)) return cmd
        val encoded = java.net.URLEncoder.encode(cmd, Charsets.UTF_8.name())
        val path = "server/load.php?type=itv&action=create_link&cmd=$encoded&series=&forced_storage=0&disable_ad=0&download=0&force_ch_link_check=0&JsHttpRequest=1-xml"
        val raw = request(session.portalBase, session.mac, path, session.token)
        val json = unwrapJsResponse(raw)
        val link = json?.optString("cmd").orEmpty()
        if (link.startsWith("http", ignoreCase = true)) return link
        if (link.startsWith("ffmpeg ", ignoreCase = true)) {
            return link.removePrefix("ffmpeg ").trim()
        }
        return link.ifBlank { cmd }
    }

    private fun unwrapJsResponse(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val obj = JSONObject(trimmed)
            if (obj.has("js")) obj.optJSONObject("js") else obj
        }.getOrNull()
    }

    private fun normalizePortalBase(url: String): String {
        var base = url.trim().removeSuffix("/")
        if (!base.startsWith("http")) base = "http://$base"
        base = base
            .replace(Regex("/stalker_portal/c/?$"), "")
            .replace(Regex("/c/?$"), "")
        return base.removeSuffix("/")
    }

    companion object {
        private const val MAG_USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
    }
}
