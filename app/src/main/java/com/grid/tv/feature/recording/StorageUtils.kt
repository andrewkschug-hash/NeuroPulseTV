package com.grid.tv.feature.recording

import java.io.File

object StorageUtils {
    const val TWO_GB_BYTES = 2L * 1024 * 1024 * 1024
    const val CRITICAL_BYTES = 500L * 1024 * 1024

    fun freeBytes(dir: File): Long = dir.usableSpace

    fun hasAtLeast2Gb(dir: File): Boolean = freeBytes(dir) >= TWO_GB_BYTES

    fun isCriticalLowStorage(dir: File): Boolean = freeBytes(dir) < CRITICAL_BYTES
}
