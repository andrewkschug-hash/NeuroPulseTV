package com.grid.tv.data.update

import com.grid.tv.BuildConfig
import com.grid.tv.data.network.AppHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class GitHubReleaseChecker @Inject constructor(
    appHttpClient: AppHttpClient
) {
    private val client: OkHttpClient = appHttpClient.client()
    private var checkedThisSession = false

    suspend fun checkForUpdate(): AppUpdateInfo? {
        if (checkedThisSession) return null
        checkedThisSession = true
        return withContext(Dispatchers.IO) {
            runCatching { fetchLatestRelease() }.getOrNull()
        }
    }

    private fun fetchLatestRelease(): AppUpdateInfo? {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GRID-TV/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = JSONObject(body)
            val tagName = json.optString("tag_name").trim()
            if (tagName.isBlank()) return null
            val current = BuildConfig.VERSION_NAME
            if (!isNewerVersion(tagName, current)) return null
            val releaseNotes = json.optString("body").trim().takeIf { it.isNotBlank() }
            val downloadUrl = resolveDownloadUrl(json) ?: return null
            return AppUpdateInfo(
                versionName = normalizeVersionLabel(tagName),
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl
            )
        }
    }

    private fun resolveDownloadUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    return url
                }
            }
        }
        return json.optString("html_url").trim().takeIf { it.isNotBlank() }
    }

    internal companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/gridtvsupport-wq/GRID/releases/latest"

        fun normalizeVersionLabel(version: String): String =
            version.trim().removePrefix("v").removePrefix("V")

        fun isNewerVersion(remote: String, current: String): Boolean {
            val remoteParts = parseVersionParts(remote)
            val currentParts = parseVersionParts(current)
            if (remoteParts.isEmpty() || currentParts.isEmpty()) return false
            val max = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until max) {
                val remotePart = remoteParts.getOrElse(i) { 0 }
                val currentPart = currentParts.getOrElse(i) { 0 }
                if (remotePart != currentPart) {
                    return remotePart > currentPart
                }
            }
            return false
        }

        private fun parseVersionParts(version: String): List<Int> =
            normalizeVersionLabel(version)
                .split(".", "-", "_")
                .mapNotNull { segment ->
                    segment.takeWhile { it.isDigit() }.toIntOrNull()
                }
    }
}
