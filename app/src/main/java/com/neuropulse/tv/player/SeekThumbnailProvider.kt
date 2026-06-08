package com.neuropulse.tv.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever

class SeekThumbnailProvider {
    fun thumbnail(url: String, positionMs: Long): Bitmap? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(url, HashMap())
            val frame = retriever.getFrameAtTime(positionMs * 1000)
            retriever.release()
            frame
        }.getOrNull()
    }
}
