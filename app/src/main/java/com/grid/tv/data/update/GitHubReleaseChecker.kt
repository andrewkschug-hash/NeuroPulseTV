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

    /** Manual update check only — never called automatically by the app. */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val localVersion = BuildConfig.VERSION_NAME
        Log.d(
            TAG,
            "checkForUpdate: local=$localVersion owner=${BuildConfig.GITHUB_OWNER} repo=${BuildConfig.GITHUB_REPO}"
        )
        runCatching {
            fetchLatestRelease(localVersion)
        }.getOrElse { error ->
            UpdateCheckResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun fetchLatestRelease(localVersion: String): UpdateCheckResult {
        val request = Request.Builder()
            .url(latestReleaseApiUrl())
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GRID-TV/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            Log.d(
                TAG,
                "checkForUpdate: HTTP ${response.code} remaining=${response.header("X-RateLimit-Remaining")}"
            )
            when {
                response.code == 404 -> return UpdateCheckResult.NoReleasePublished(404)
                response.code == 403 ->
                    return UpdateCheckResult.Failed(
                        reason = "GitHub API rate limit or access denied (403)",
                        httpCode = 403
                    )
                !response.isSuccessful ->
                    return UpdateCheckResult.Failed(
                        reason = "GitHub API HTTP ${response.code}",
                        httpCode = response.code
                    )
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return UpdateCheckResult.Failed("Empty GitHub release response")
            }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name").trim()
            if (tagName.isBlank()) {
                return UpdateCheckResult.Failed("Release JSON missing version tag")
            }
            val remoteNorm = normalizeVersionLabel(tagName)
            val currentNorm = normalizeVersionLabel(localVersion)
            logVersionConfigWarnings(localVersion, currentNorm)
            Log.d(TAG, "Installed=$currentNorm Remote=$remoteNorm (raw tag=$tagName)")
            if (!isNewerVersion(tagName, localVersion)) {
                return UpdateCheckResult.UpToDate
            }
            val downloadUrl = resolveDownloadUrl(json)
                ?: return UpdateCheckResult.Failed("Release has no installable APK asset")
            val releaseNotes = json.optString("body").trim().takeIf { it.isNotBlank() }
            return UpdateCheckResult.UpdateAvailable(
                AppUpdateInfo(
                    versionName = remoteNorm,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl
                )
            )
        }
    }

    private fun latestReleaseApiUrl(): String =
        "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"

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
        return null
    }

    private fun logVersionConfigWarnings(rawVersion: String, normalizedVersion: String) {
        if (!BuildConfig.DEBUG) return
        when {
            rawVersion.isBlank() ->
                Log.w(TAG, "VERSION_NAME missing in BuildConfig — update checks will fail")
            parseVersionParts(normalizedVersion).isEmpty() ->
                Log.w(
                    TAG,
                    "VERSION_NAME unparseable: raw='$rawVersion' normalized='$normalizedVersion'"
                )
        }
    }

    internal companion object {
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
