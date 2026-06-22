package com.grid.tv.feature.update

import android.util.Log
import com.grid.tv.BuildConfig
import com.grid.tv.data.network.AppHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseNotes: String?,
    val downloadUrl: String,
    val pageUrl: String
)

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult()
    data class NoReleasePublished(val httpCode: Int) : UpdateCheckResult()
    data class Skipped(val reason: String) : UpdateCheckResult()
    data class Failed(val reason: String, val httpCode: Int? = null) : UpdateCheckResult()
}

@Singleton
class UpdateChecker @Inject constructor(
    private val httpClient: AppHttpClient,
    private val preferences: UpdatePreferences
) {
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val owner = BuildConfig.GITHUB_OWNER
        val repo = BuildConfig.GITHUB_REPO
        val localVersion = BuildConfig.VERSION_NAME
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"

        Log.d(TAG, "checkForUpdate: starting (local=$localVersion owner=$owner repo=$repo)")
        Log.d(TAG, "checkForUpdate: GET $url")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GRID-TV/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        runCatching {
            httpClient.client().newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val rateRemaining = response.header("X-RateLimit-Remaining")
                val rateLimit = response.header("X-RateLimit-Limit")
                Log.d(
                    TAG,
                    "checkForUpdate: response code=${response.code} " +
                        "rateLimit=$rateLimit remaining=$rateRemaining bodyLen=${body.length}"
                )

                when {
                    response.code == 404 -> {
                        Log.w(TAG, "checkForUpdate: no published GitHub release (404)")
                        return@withContext UpdateCheckResult.NoReleasePublished(404)
                    }
                    response.code == 403 -> {
                        Log.e(TAG, "checkForUpdate: GitHub rate limited or forbidden (403): $body")
                        return@withContext UpdateCheckResult.Failed(
                            reason = "GitHub API returned 403 (rate limit or forbidden)",
                            httpCode = 403
                        )
                    }
                    !response.isSuccessful -> {
                        Log.e(TAG, "checkForUpdate: HTTP ${response.code}: $body")
                        return@withContext UpdateCheckResult.Failed(
                            reason = "GitHub API HTTP ${response.code}",
                            httpCode = response.code
                        )
                    }
                }

                val json = JSONObject(body)
                val tagName = json.optString("tag_name").ifBlank { json.optString("name") }
                if (tagName.isBlank()) {
                    Log.e(TAG, "checkForUpdate: release JSON missing tag_name")
                    return@withContext UpdateCheckResult.Failed("Release JSON missing version tag")
                }

                val normalizedRemote = VersionCompare.normalize(tagName)
                val normalizedLocal = VersionCompare.normalize(localVersion)
                Log.d(
                    TAG,
                    "checkForUpdate: comparing remote=$normalizedRemote (raw=$tagName) vs local=$normalizedLocal"
                )

                if (!VersionCompare.isRemoteNewer(tagName, localVersion)) {
                    Log.d(TAG, "checkForUpdate: up to date")
                    return@withContext UpdateCheckResult.UpToDate
                }

                val dismissed = preferences.dismissedVersion()
                if (dismissed != null && VersionCompare.normalize(dismissed) == normalizedRemote) {
                    Log.d(TAG, "checkForUpdate: user dismissed v$normalizedRemote — skipping prompt")
                    return@withContext UpdateCheckResult.Skipped("Dismissed $normalizedRemote")
                }

                val pageUrl = json.optString("html_url")
                val downloadUrl = pickDownloadUrl(json) ?: pageUrl
                if (downloadUrl.isBlank()) {
                    Log.e(TAG, "checkForUpdate: no download or page URL in release")
                    return@withContext UpdateCheckResult.Failed("Release has no download URL")
                }

                val info = AppUpdateInfo(
                    latestVersion = normalizedRemote,
                    releaseNotes = json.optString("body").takeIf { it.isNotBlank() },
                    downloadUrl = downloadUrl,
                    pageUrl = pageUrl.ifBlank { downloadUrl }
                )
                Log.d(TAG, "checkForUpdate: update available → v${info.latestVersion} url=${info.downloadUrl}")
                UpdateCheckResult.UpdateAvailable(info)
            }
        }.getOrElse { error ->
            Log.e(TAG, "checkForUpdate: network/parsing failed", error)
            UpdateCheckResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun pickDownloadUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                Log.d(TAG, "checkForUpdate: found APK asset $name")
                return url
            }
        }
        return null
    }

    companion object {
        const val TAG = "UpdateChecker"
    }
}
