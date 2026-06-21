package com.grid.tv.feature.recording

import android.content.Context
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.db.dao.ProfileSettingsDao
import com.grid.tv.data.db.entity.ProfileSettingsEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class StorageType { USB, SD_CARD, INTERNAL }

data class StorageOption(
    val id: String,
    val type: StorageType,
    val label: String,
    val recordingsDir: File,
    val freeBytes: Long
) {
    fun displayLine(): String = "${label} — ${StorageFormat.formatFreeSpace(freeBytes)}"
}

object StorageFormat {
    private const val GB = 1024L * 1024L * 1024L
    private const val MB = 1024L * 1024L

    fun formatFreeSpace(bytes: Long): String {
        val gb = bytes.toDouble() / GB
        return if (gb >= 1.0) {
            String.format("%.1f GB free", gb)
        } else {
            String.format("%.0f MB free", bytes.toDouble() / MB)
        }
    }

    fun formatFileSize(bytes: Long): String {
        val gb = bytes.toDouble() / GB
        return if (gb >= 1.0) {
            String.format("%.1f GB", gb)
        } else {
            String.format("%.0f MB", bytes.toDouble() / MB)
        }
    }
}

@Singleton
class RecordingStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileDao: ProfileDao,
    private val profileSettingsDao: ProfileSettingsDao
) {
    companion object {
        const val RECORDINGS_SUBDIR = "GRID/Recordings"
        private const val USB_OPTION_ID = "usb"
    }

    fun isUsbStorageReady(): Boolean = StorageUtils.isUsbStorageReady(context)

    fun usbStorageStatusLine(): String? = StorageUtils.usbStorageStatusLine(context)

    fun getUsbRecordingsDir(): File? {
        val usbRoot = StorageUtils.getExternalUsbStoragePath(context) ?: return null
        return File(usbRoot, RECORDINGS_SUBDIR).also { it.mkdirs() }
    }

    fun enumerateOptions(): List<StorageOption> {
        if (!isUsbStorageReady()) return emptyList()
        val dir = getUsbRecordingsDir() ?: return emptyList()
        return listOf(
            StorageOption(
                id = USB_OPTION_ID,
                type = StorageType.USB,
                label = "USB Drive",
                recordingsDir = dir,
                freeBytes = StorageUtils.freeBytes(dir)
            )
        )
    }

    suspend fun getSavedRecordingsDir(): File? {
        if (!isUsbStorageReady()) return null
        return getUsbRecordingsDir()
    }

    suspend fun hasConfiguredLocation(): Boolean = isUsbStorageReady()

    suspend fun ensureUsbLocationSaved() {
        if (!isUsbStorageReady()) return
        val dir = getUsbRecordingsDir() ?: return
        saveLocation(dir)
    }

    suspend fun saveLocation(recordingsDir: File) {
        val profileId = profileDao.activeProfile()?.profileId ?: return
        val old = profileSettingsDao.get(profileId) ?: ProfileSettingsEntity(profileId = profileId)
        profileSettingsDao.upsert(
            old.copy(recordingStoragePath = recordingsDir.absolutePath)
        )
    }

    suspend fun saveLocationById(optionId: String) {
        if (optionId != USB_OPTION_ID) return
        val option = enumerateOptions().firstOrNull { it.id == optionId } ?: return
        saveLocation(option.recordingsDir)
    }

    suspend fun currentStorageLabel(): String? {
        if (!isUsbStorageReady()) return null
        return "USB Drive"
    }

    suspend fun freeBytes(): Long {
        val dir = getSavedRecordingsDir() ?: return 0L
        return StorageUtils.freeBytes(dir)
    }

    suspend fun hasAtLeast4Gb(): Boolean {
        val dir = getSavedRecordingsDir() ?: return false
        return StorageUtils.hasAtLeast4Gb(dir)
    }

    suspend fun isCriticalLowStorage(): Boolean {
        val dir = getSavedRecordingsDir() ?: return true
        return StorageUtils.isCriticalLowStorage(dir)
    }

    suspend fun freeStorageSummaryLine(): String {
        val free = freeBytes()
        return "${StorageFormat.formatFreeSpace(free)} on USB Drive"
    }

    suspend fun isStorageAvailable(dir: File): Boolean {
        return try {
            (dir.exists() || dir.mkdirs()) && dir.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun currentStorageHealth(dir: File): StorageHealth {
        val free = runCatching { StorageUtils.freeBytes(dir) }.getOrDefault(0L)
        val available = isStorageAvailable(dir)
        val criticalLow = StorageUtils.isCriticalLowStorage(dir)
        return StorageHealth(
            available = available,
            freeBytes = free,
            isCriticalLow = criticalLow
        )
    }

    suspend fun lowStorageWarning(): String? =
        if (!hasAtLeast4Gb()) "USB storage is below 4 GB free. Recording may fail." else null

    suspend fun insufficientSpaceWarning(estimatedBytes: Long): String? {
        val free = freeBytes()
        return if (estimatedBytes > free) {
            "Recording needs ${StorageFormat.formatFileSize(estimatedBytes)} but only ${StorageFormat.formatFreeSpace(free)} available."
        } else {
            null
        }
    }
}

data class StorageHealth(
    val available: Boolean,
    val freeBytes: Long,
    val isCriticalLow: Boolean
)
