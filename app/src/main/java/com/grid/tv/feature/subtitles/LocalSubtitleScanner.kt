package com.grid.tv.feature.subtitles

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSubtitleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scan(mediaUrl: String, title: String): List<LocalSubtitleCandidate> {
        val results = linkedMapOf<String, LocalSubtitleCandidate>()
        candidatePaths(mediaUrl, title).forEach { path ->
            val file = File(path)
            if (!file.exists() || !file.isFile) return@forEach
            val language = inferLanguage(file.name)
            val mimeType = mimeTypeFor(file)
            results.putIfAbsent(
                "${language}_${file.absolutePath}",
                LocalSubtitleCandidate(
                    file = file,
                    language = language,
                    mimeType = mimeType,
                    label = file.nameWithoutExtension
                )
            )
        }
        return results.values.toList()
    }

    private fun candidatePaths(mediaUrl: String, title: String): List<String> {
        val paths = mutableListOf<String>()
        val sanitizedTitle = title.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
        val subtitleDir = File(context.filesDir, "subtitles")

        when {
            mediaUrl.startsWith("file://", ignoreCase = true) -> {
                val mediaFile = File(Uri.parse(mediaUrl).path.orEmpty())
                if (mediaFile.exists()) {
                    val base = mediaFile.absolutePath.substringBeforeLast('.')
                    SUBTITLE_EXTENSIONS.forEach { ext -> paths += "$base$ext" }
                    mediaFile.parentFile?.let { parent ->
                        SUBTITLE_EXTENSIONS.forEach { ext ->
                            paths += File(parent, "${mediaFile.nameWithoutExtension}$ext").absolutePath
                        }
                    }
                }
            }
            mediaUrl.startsWith("http", ignoreCase = true) -> {
                val withoutQuery = mediaUrl.substringBefore('?')
                val base = withoutQuery.substringBeforeLast('.')
                val fileName = withoutQuery.substringAfterLast('/').substringBeforeLast('.')
                SUBTITLE_EXTENSIONS.forEach { ext ->
                    paths += "$base$ext"
                    paths += File(subtitleDir, "$fileName$ext").absolutePath
                }
            }
        }

        if (sanitizedTitle.isNotBlank()) {
            SUBTITLE_EXTENSIONS.forEach { ext ->
                paths += File(subtitleDir, "$sanitizedTitle$ext").absolutePath
            }
        }
        return paths.distinct()
    }

    private fun inferLanguage(fileName: String): String {
        val lower = fileName.lowercase()
        LANGUAGE_HINTS.forEach { (token, code) ->
            if (lower.contains(token)) return code
        }
        Regex("""\.([a-z]{2,3})\.(srt|vtt|ass|ssa)$""", RegexOption.IGNORE_CASE)
            .find(lower)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        return "und"
    }

    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> "application/x-subrip"
    }

    companion object {
        private val SUBTITLE_EXTENSIONS = listOf(".srt", ".vtt", ".ass", ".ssa")
        private val LANGUAGE_HINTS = listOf(
            ".en." to "en",
            ".eng." to "en",
            ".es." to "es",
            ".spa." to "es",
            ".fr." to "fr",
            ".de." to "de",
            ".it." to "it",
            ".pt." to "pt"
        )
    }
}

data class LocalSubtitleCandidate(
    val file: File,
    val language: String,
    val mimeType: String,
    val label: String
)
