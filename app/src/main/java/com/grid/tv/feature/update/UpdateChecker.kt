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
    /** Settings → About label; uses 24h cached GitHub [tag_name] when available. */
    suspend fun resolveAboutVersionLabel(): String = withContext(Dispatchers.IO) {
        val localFallback = BuildConfig.VERSION_NAME
        val nowMs = System.currentTimeMillis()
        preferences.cachedReleaseTag()?.let { cached ->
            if (preferences.isReleaseCacheFresh(cached.fetchedAtMs, nowMs)) {
                return@withContext formatGitHubReleaseLabel(cached.tag)
            }
        }
        when (val tag = fetchLatestReleaseTag()) {
            null -> localFallback
            else -> {
                preferences.saveCachedReleaseTag(tag, nowMs)
                formatGitHubReleaseLabel(tag)
            }
        }
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val localVersion = BuildConfig.VERSION_NAME
        Log.d(
            TAG,
            "checkForUpdate: starting (local=$localVersion owner=${BuildConfig.GITHUB_OWNER} repo=${BuildConfig.GITHUB_REPO})"
        )
        Log.d(TAG, "checkForUpdate: GET ${latestReleaseApiUrl()}")

        runCatching {
            httpClient.client().newCall(latestReleaseRequest()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(
                    TAG,
                    "checkForUpdate: response code=${response.code} " +
                        "remaining=${response.header("X-RateLimit-Remaining")} bodyLen=${body.length}"
                )

                when {
                    response.code == 404 -> {
                        Log.w(TAG, "checkForUpdate: no published GitHub release (404)")
                        return@withContext UpdateCheckResult.NoReleasePublished(404)
                    }
                    response.code == 403 -> {
                        return@withContext UpdateCheckResult.Failed(
                            reason = "GitHub API returned 403 (rate limit or forbidden)",
                            httpCode = 403
                        )
                    }
                    !response.isSuccessful -> {
                        return@withContext UpdateCheckResult.Failed(
                            reason = "GitHub API HTTP ${response.code}",
                            httpCode = response.code
                        )
                    }
                }

                val json = JSONObject(body)
                val tagName = readReleaseTag(json)
                if (tagName.isBlank()) {
                    return@withContext UpdateCheckResult.Failed("Release JSON missing version tag")
                }

                preferences.saveCachedReleaseTag(tagName)

                val normalizedRemote = VersionCompare.normalize(tagName)
                if (!VersionCompare.isRemoteNewer(tagName, localVersion)) {
                    return@withContext UpdateCheckResult.UpToDate
                }

                val dismissed = preferences.dismissedVersion()
                if (dismissed != null && VersionCompare.normalize(dismissed) == normalizedRemote) {
                    return@withContext UpdateCheckResult.Skipped("Dismissed $normalizedRemote")
                }

                val pageUrl = json.optString("html_url")
                val downloadUrl = pickDownloadUrl(json) ?: pageUrl
                if (downloadUrl.isBlank()) {
                    return@withContext UpdateCheckResult.Failed("Release has no download URL")
                }

                UpdateCheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        latestVersion = normalizedRemote,
                        releaseNotes = json.optString("body").takeIf { it.isNotBlank() },
                        downloadUrl = downloadUrl,
                        pageUrl = pageUrl.ifBlank { downloadUrl }
                    )
                )
            }
        }.getOrElse { error ->
            UpdateCheckResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun fetchLatestReleaseTag(): String? =
        fetchLatestReleaseJson()?.let(::readReleaseTag)?.takeIf { it.isNotBlank() }

    private fun fetchLatestReleaseJson(): JSONObject? {
        val url = latestReleaseApiUrl()
        Log.d(TAG, "fetchLatestRelease: GET $url")
        val request = latestReleaseRequest()
        return runCatching {
            httpClient.client().newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(
                    TAG,
                    "fetchLatestRelease: code=${response.code} bodyLen=${body.length} " +
                        "remaining=${response.header("X-RateLimit-Remaining")}"
                )
                when {
                    response.code == 404 -> null
                    response.code == 403 -> null
                    !response.isSuccessful -> null
                    body.isBlank() -> null
                    else -> JSONObject(body)
                }
            }
        }.getOrNull()
    }

    private fun latestReleaseRequest(): Request =
        Request.Builder()
            .url(latestReleaseApiUrl())
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GRID-TV/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

    private fun latestReleaseApiUrl(): String =
        "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"

    private fun readReleaseTag(json: JSONObject): String =
        json.optString("tag_name").ifBlank { json.optString("name") }.trim()

    private fun formatGitHubReleaseLabel(tagName: String): String =
        "${VersionCompare.normalize(tagName)} (GitHub Release)"

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
