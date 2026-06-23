package com.grid.tv.data.update

import android.util.Log
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
            val remoteNorm = normalizeVersionLabel(tagName)
            val currentNorm = normalizeVersionLabel(current)
            logVersionConfigWarnings(current, currentNorm)
            Log.d(
                TAG,
                "Installed=$currentNorm Remote=$remoteNorm (raw tag=$tagName) normalized comparison running"
            )
            if (!isNewerVersion(tagName, current)) {
                Log.d(TAG, "No update: remote $remoteNorm is not newer than installed $currentNorm")
                return null
            }
            Log.d(TAG, "Update available: $remoteNorm > $currentNorm")
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
                if (ReleaseDownloadAssets.isInstallableAsset(name) && url.isNotBlank()) {
                    Log.d(TAG, "Resolved install asset: $name")
                    return url
                }
            }
        }
        return json.optString("html_url").trim().takeIf { it.isNotBlank() }
    }

    private fun logVersionConfigWarnings(rawVersion: String, normalizedVersion: String) {
        if (!BuildConfig.DEBUG) return
        when {
            rawVersion.isBlank() ->
                Log.w(TAG, "VERSION_NAME missing in BuildConfig — update checks will fail")
            parseVersionParts(normalizedVersion).isEmpty() ->
                Log.w(
                    TAG,
                    "VERSION_NAME unparseable: raw='$rawVersion' normalized='$normalizedVersion' " +
                        "— align with GitHub tags (e.g. 1.03)"
                )
        }
    }

    internal companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/gridtvsupport-wq/GRID/releases/latest"

        fun normalizeVersionLabel(version: String): String =
            version.trim().removePrefix("v").removePrefix("V")

        fun isNewerVersion(remote: String, current: String): Boolean {
            val remoteParts = parseVersionParts(normalizeVersionLabel(remote))
            val currentParts = parseVersionParts(normalizeVersionLabel(current))
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

        private fun parseVersionParts(normalizedVersion: String): List<Int> =
            normalizedVersion
                .split(".", "-", "_")
                .mapNotNull { segment ->
                    segment.takeWhile { it.isDigit() }.toIntOrNull()
                }

        private const val TAG = "UpdateChecker"
    }
}
