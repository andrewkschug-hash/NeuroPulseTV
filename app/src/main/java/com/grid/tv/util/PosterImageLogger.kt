package com.grid.tv.util

import android.util.Log
import coil.request.ImageRequest
import coil.size.Size

/** Filter logcat: `adb logcat -s PosterImage` */
object PosterImageLogger {
    const val TAG = "PosterImage"

    fun requested(request: ImageRequest, size: Size?) {
        Log.d(TAG, "Poster requested url=${urlSummary(request)} ${sizeLabel(size)}")
    }

    fun cacheHit(request: ImageRequest, size: Size?, source: String) {
        Log.d(TAG, "Poster cache hit ($source) url=${urlSummary(request)} ${sizeLabel(size)}")
    }

    fun cacheMiss(request: ImageRequest, size: Size?) {
        Log.d(TAG, "Poster cache miss url=${urlSummary(request)} ${sizeLabel(size)}")
    }

    fun decoded(request: ImageRequest, size: Size?, durationMs: Long) {
        Log.d(TAG, "Poster decoded ${durationMs}ms url=${urlSummary(request)} ${sizeLabel(size)}")
    }

    private fun sizeLabel(size: Size?): String =
        if (size == null) {
            "size=?"
        } else {
            "size=${size.width}x${size.height}"
        }

    private fun urlSummary(request: ImageRequest): String {
        val data = request.data?.toString().orEmpty()
        return if (data.length <= 96) data else data.take(80) + "…"
    }
}
