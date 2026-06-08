package com.neuropulse.tv.feature.recording

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingFilenameUtil {
    private val badChars = Regex("[\\\\/:*?\"<>|]")

    fun sanitize(input: String): String {
        return input.replace(badChars, "_").replace(Regex("\\s+"), " ").trim().ifBlank { "Unknown" }
    }

    fun buildFileName(channel: String, title: String, epochMs: Long): String {
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(epochMs))
        return "${sanitize(channel)}_${sanitize(title)}_$date.mp4"
    }
}
