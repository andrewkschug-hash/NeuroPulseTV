package com.grid.tv.feature.recording

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingFilenameUtil {
    private val badChars = Regex("[\\\\/:*?\"<>|]")

    fun sanitize(input: String): String {
        return input.replace(badChars, "_").replace(Regex("\\s+"), " ").trim().ifBlank { "Unknown" }
    }

    fun buildFileName(channel: String, title: String, epochMs: Long): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
        return "${sanitize(channel)} - ${sanitize(title)} - $date.ts"
    }

    fun resolveUniqueFile(directory: File, channel: String, title: String, epochMs: Long): File {
        val baseName = buildFileName(channel, title, epochMs)
        var file = File(directory, baseName)
        if (!file.exists()) return file
        val nameWithoutExt = baseName.removeSuffix(".ts")
        var counter = 2
        while (file.exists()) {
            file = File(directory, "$nameWithoutExt ($counter).ts")
            counter++
        }
        return file
    }
}
