package com.neuropulse.tv.data.network.opensubtitles

import com.neuropulse.tv.BuildConfig
import com.neuropulse.tv.data.network.AppHttpClient
import com.neuropulse.tv.data.security.SecureCredentialStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class OpenSubtitleMatch(
    val subtitleId: String,
    val fileName: String,
    val language: String,
    val downloadCount: Int
)

// TODO: replace with server-side equivalent when scaling
@Singleton
class OpenSubtitlesService @Inject constructor(
    appHttpClient: AppHttpClient,
    private val secureCredentialStore: SecureCredentialStore
) {
    private val client: OkHttpClient = appHttpClient.client()

    private fun resolveApiKey(): String =
        secureCredentialStore.getOpenSubtitlesApiKey()?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENSUBTITLES_API_KEY.trim()

    suspend fun searchByImdbId(imdbId: String, language: String = "en"): List<OpenSubtitleMatch> {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) return emptyList()
        val request = Request.Builder()
            .url("$BASE_URL/subtitles?imdb_id=$imdbId&languages=$language")
            .header("Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
            return buildList {
                for (i in 0 until data.length()) {
                    val row = data.optJSONObject(i) ?: continue
                    val attrs = row.optJSONObject("attributes") ?: continue
                    val files = attrs.optJSONArray("files") ?: continue
                    val file = files.optJSONObject(0) ?: continue
                    add(
                        OpenSubtitleMatch(
                            subtitleId = row.optString("id"),
                            fileName = file.optString("file_name"),
                            language = attrs.optString("language"),
                            downloadCount = attrs.optInt("download_count")
                        )
                    )
                }
            }.sortedByDescending { it.downloadCount }
        }
    }

    suspend fun downloadSubtitleFile(subtitleId: String): ByteArray? {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) return null
        val request = Request.Builder()
            .url("$BASE_URL/download")
            .header("Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post("""{"file_id":$subtitleId}""".toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("OpenSubtitles download failed (${response.code})")
            val link = JSONObject(response.body?.string().orEmpty()).optString("link")
            if (link.isBlank()) return null
            val fileRequest = Request.Builder().url(link).get().build()
            client.newCall(fileRequest).execute().use { fileResponse ->
                if (!fileResponse.isSuccessful) return null
                return fileResponse.body?.bytes()
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
        private const val USER_AGENT = "NeuroPulseTV v2.1"
    }
}
