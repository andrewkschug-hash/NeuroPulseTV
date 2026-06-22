package com.grid.tv.data.network.opensubtitles

import com.grid.tv.BuildConfig
import com.grid.tv.data.network.AppHttpClient
import com.grid.tv.data.security.SecureCredentialStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun searchByImdbId(imdbId: String, language: String = "en"): List<OpenSubtitleMatch> =
        search("$BASE_URL/subtitles?imdb_id=$imdbId&languages=$language")

    suspend fun searchByQuery(query: String, language: String = "en", year: Int? = null): List<OpenSubtitleMatch> {
        val encodedQuery = java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val yearParam = year?.let { "&year=$it" }.orEmpty()
        return search("$BASE_URL/subtitles?query=$encodedQuery&languages=$language$yearParam")
    }

    private suspend fun search(url: String): List<OpenSubtitleMatch> = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) return@withContext emptyList()
        val request = Request.Builder()
            .url(url)
            .header("Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string().orEmpty()
            val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            buildList {
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

    suspend fun downloadSubtitleFile(subtitleId: String): ByteArray? = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) return@withContext null
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
            if (link.isBlank()) return@withContext null
            val fileRequest = Request.Builder().url(link).get().build()
            client.newCall(fileRequest).execute().use { fileResponse ->
                if (!fileResponse.isSuccessful) return@withContext null
                fileResponse.body?.bytes()
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
        private const val USER_AGENT = "GRID v2.1"
    }
}
