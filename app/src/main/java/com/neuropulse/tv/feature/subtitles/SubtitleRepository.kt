package com.neuropulse.tv.feature.subtitles

import android.content.Context
import com.neuropulse.tv.data.db.dao.SubtitleCacheDao
import com.neuropulse.tv.data.db.entity.SubtitleCacheEntity
import com.neuropulse.tv.data.network.opensubtitles.OpenSubtitlesService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

// TODO: replace with server-side equivalent when scaling
@Singleton
class SubtitleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openSubtitlesService: OpenSubtitlesService,
    private val subtitleCacheDao: SubtitleCacheDao
) {
    suspend fun getOrDownloadSubtitle(imdbId: String, language: String = "en"): File? {
        val normalizedImdb = imdbId.trim()
        if (normalizedImdb.isBlank()) return null

        subtitleCacheDao.get(normalizedImdb, language)?.let { cached ->
            val file = File(cached.filePath)
            if (file.exists()) return file
        }

        val match = openSubtitlesService.searchByImdbId(normalizedImdb, language).firstOrNull() ?: return null
        val bytes = openSubtitlesService.downloadSubtitleFile(match.subtitleId) ?: return null
        val srtBytes = unzipIfNeeded(bytes, match.fileName)
        val outFile = File(context.filesDir, "subtitles/${normalizedImdb}_$language.srt").apply {
            parentFile?.mkdirs()
        }
        outFile.writeBytes(srtBytes)
        subtitleCacheDao.upsert(
            SubtitleCacheEntity(
                imdbId = normalizedImdb,
                language = language,
                filePath = outFile.absolutePath,
                sourceSubtitleId = match.subtitleId
            )
        )
        return outFile
    }

    private fun unzipIfNeeded(bytes: ByteArray, fileName: String): ByteArray {
        if (!fileName.endsWith(".gz", ignoreCase = true)) return bytes
        return GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    }
}
