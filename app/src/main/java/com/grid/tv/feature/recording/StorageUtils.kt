package com.grid.tv.feature.recording

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import java.io.File

object StorageUtils {
    const val TWO_GB_BYTES = 2L * 1024 * 1024 * 1024
    const val FOUR_GB_BYTES = 4L * 1024 * 1024 * 1024
    const val CRITICAL_BYTES = 500L * 1024 * 1024
    const val USB_REQUIRED_MESSAGE =
        "Please insert a USB flash drive formatted as FAT32/exFAT to configure storage options."

    private const val BYTES_PER_GB = 1024L * 1024L * 1024L

    /**
     * Returns the app-specific external files directory on a removable USB volume, if mounted.
     * Index 0 from [ContextCompat.getExternalFilesDirs] is primary (internal) storage; later
     * entries are secondary volumes such as USB sticks.
     */
    fun getExternalUsbStoragePath(context: Context): File? {
        val dirs = ContextCompat.getExternalFilesDirs(context, null) ?: return null
        if (dirs.size <= 1) return null
        for (index in 1 until dirs.size) {
            val dir = dirs[index] ?: continue
            if (Environment.isExternalStorageRemovable(dir)) {
                return dir
            }
        }
        return null
    }

    fun freeBytes(dir: File): Long {
        return runCatching {
            val stats = StatFs(dir.path)
            stats.availableBlocksLong * stats.blockSizeLong
        }.getOrElse { dir.usableSpace }
    }

    fun isUsbStorageReady(context: Context): Boolean {
        val usbDir = getExternalUsbStoragePath(context) ?: return false
        val bytesAvailable = freeBytes(usbDir)
        val gigaBytesFree = bytesAvailable / BYTES_PER_GB
        return usbDir.canWrite() && gigaBytesFree >= 4
    }

    fun usbStorageStatusLine(context: Context): String? {
        val usbDir = getExternalUsbStoragePath(context) ?: return null
        if (!usbDir.canWrite()) return null
        val gigaBytesFree = freeBytes(usbDir).toDouble() / BYTES_PER_GB
        return "Storage Target: USB Drive (${String.format("%.1f GB Free", gigaBytesFree)})"
    }

    fun hasAtLeast2Gb(dir: File): Boolean = freeBytes(dir) >= TWO_GB_BYTES

    fun hasAtLeast4Gb(dir: File): Boolean = freeBytes(dir) >= FOUR_GB_BYTES

    fun isCriticalLowStorage(dir: File): Boolean = freeBytes(dir) < CRITICAL_BYTES
}
