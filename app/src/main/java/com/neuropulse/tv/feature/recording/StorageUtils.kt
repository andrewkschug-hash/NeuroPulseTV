package com.neuropulse.tv.feature.recording

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

object StorageUtils {
    fun outputDirectory(context: Context): File {
        val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val dir = File(movies, "StreamFlow")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun freeBytes(context: Context): Long {
        val dir = outputDirectory(context)
        return dir.usableSpace
    }

    fun hasAtLeast2Gb(context: Context): Boolean = freeBytes(context) >= 2L * 1024 * 1024 * 1024

    fun isCriticalLowStorage(context: Context): Boolean = freeBytes(context) < 500L * 1024 * 1024
}
