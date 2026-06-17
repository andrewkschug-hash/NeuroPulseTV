package com.grid.tv.feature.subtitles

import com.grid.tv.data.db.dao.SubtitleCacheDao
import com.grid.tv.data.db.entity.SubtitleCacheEntity
import com.grid.tv.data.network.opensubtitles.OpenSubtitlesService
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import java.io.File
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SubtitleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openSubtitlesService: OpenSubtitlesService,
    private val subtitleCacheDao: SubtitleCacheDao
) {
    suspend fun resolveForPlayback(
        title: String,
        imdbId: String?,
        languages: List<String>,
        releaseYear: Int? = null
    ): ExternalSubtitleFile? = withContext(Dispatchers.IO) {
        for (language in languages) {
            getOrDownloadSubtitle(
                title = title,
                imdbId = imdbId,
                language = language,
                releaseYear = releaseYear
            )?.let { file ->
                return@withContext ExternalSubtitleFile(
                    file = file,
                    language = language,
                    mimeType = "application/x-subrip"
                )
            }
        }
        null
    }

    suspend fun prefetchInBackground(
        title: String,
        imdbId: String?,
        languages: List<String>,
        releaseYear: Int? = null
    ) {
        withContext(Dispatchers.IO) {
            languages.forEach { language ->
                runCatching {
                    getOrDownloadSubtitle(title, imdbId, language, releaseYear)
                }
            }
        }
    }

    suspend fun getOrDownloadSubtitle(
        title: String,
        imdbId: String?,
        language: String = "en",
        releaseYear: Int? = null
    ): File? = withContext(Dispatchers.IO) {
        val normalizedLanguage = SubtitleLanguageResolver.normalizeCode(language) ?: "en"
        val normalizedImdb = imdbId?.trim().orEmpty()

        if (normalizedImdb.isNotBlank()) {
            subtitleCacheDao.get(normalizedImdb, normalizedLanguage)?.let { cached ->
                val file = File(cached.filePath)
                if (file.exists()) return@withContext file
            }
        }

        val match = when {
            normalizedImdb.isNotBlank() ->
                openSubtitlesService.searchByImdbId(normalizedImdb, normalizedLanguage).firstOrNull()
            title.isNotBlank() ->
                openSubtitlesService.searchByQuery(title, normalizedLanguage, releaseYear).firstOrNull()
            else -> null
        } ?: return@withContext null

        val bytes = openSubtitlesService.downloadSubtitleFile(match.subtitleId) ?: return@withContext null
        val srtBytes = unzipIfNeeded(bytes, match.fileName)
        val cacheKey = if (normalizedImdb.isNotBlank()) normalizedImdb else sanitize(title)
        val outFile = File(context.filesDir, "subtitles/${cacheKey}_$normalizedLanguage.srt").apply {
            parentFile?.mkdirs()
        }
        outFile.writeBytes(srtBytes)
        if (normalizedImdb.isNotBlank()) {
            subtitleCacheDao.upsert(
                SubtitleCacheEntity(
                    imdbId = normalizedImdb,
                    language = normalizedLanguage,
                    filePath = outFile.absolutePath,
                    sourceSubtitleId = match.subtitleId
                )
            )
        }
        outFile
    }

    private fun unzipIfNeeded(bytes: ByteArray, fileName: String): ByteArray {
        if (!fileName.endsWith(".gz", ignoreCase = true)) return bytes
        return GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    }

    private fun sanitize(value: String): String =
        value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").take(48)
}
