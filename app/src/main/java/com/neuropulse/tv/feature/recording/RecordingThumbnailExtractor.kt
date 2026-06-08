package com.neuropulse.tv.feature.recording

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

object RecordingThumbnailExtractor {
    private const val FRAME_TIME_US = 5_000_000L

    fun extractThumbnail(videoPath: String, outputDir: File): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val frame = retriever.getFrameAtTime(FRAME_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.frameAtTime
                ?: return null
            outputDir.mkdirs()
            val thumbFile = File(outputDir, "${File(videoPath).nameWithoutExtension}_thumb.jpg")
            thumbFile.outputStream().use { out ->
                frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            thumbFile.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
