package com.grid.tv.feature.recording

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
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
        private const val TWO_GB = 2L * 1024 * 1024 * 1024
        private const val CRITICAL_MB = 500L * 1024 * 1024
    }

    fun internalRecordingsDir(): File {
        val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        return File(movies, RECORDINGS_SUBDIR).also { it.mkdirs() }
    }

    fun enumerateOptions(): List<StorageOption> {
        val options = linkedMapOf<String, StorageOption>()

        fun addOption(id: String, type: StorageType, label: String, dir: File) {
            if (!dir.exists() && !dir.mkdirs()) return
            val key = dir.canonicalPath
            if (options.containsKey(key)) return
            options[key] = StorageOption(
                id = id,
                type = type,
                label = label,
                recordingsDir = dir,
                freeBytes = dir.usableSpace
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            storageManager.storageVolumes.forEach { volume ->
                if (!volume.state.equals(Environment.MEDIA_MOUNTED, ignoreCase = true)) return@forEach
                val root = volume.directory ?: return@forEach
                if (root.absolutePath.contains("emulated", ignoreCase = true)) return@forEach

                val description = volume.getDescription(context)
                val isUsb = description.contains("USB", ignoreCase = true) ||
                    root.absolutePath.contains("usb", ignoreCase = true)
                val type = if (isUsb) StorageType.USB else StorageType.SD_CARD
                val recordingsDir = File(root, RECORDINGS_SUBDIR)
                addOption(volume.uuid ?: root.name, type, description, recordingsDir)
            }
        } else {
            context.getExternalFilesDirs(null).drop(1).forEachIndexed { index, dir ->
                dir ?: return@forEachIndexed
                addOption("external_$index", StorageType.SD_CARD, "SD Card", File(dir, RECORDINGS_SUBDIR))
            }
        }

        addOption("internal", StorageType.INTERNAL, "Internal Storage", internalRecordingsDir())

        return options.values.sortedWith(
            compareBy<StorageOption> {
                when (it.type) {
                    StorageType.USB -> 0
                    StorageType.SD_CARD -> 1
                    StorageType.INTERNAL -> 2
                }
            }.thenBy { it.label }
        )
    }

    suspend fun getSavedRecordingsDir(): File? {
        val profileId = profileDao.activeProfile()?.profileId ?: return null
        val path = profileSettingsDao.get(profileId)?.recordingStoragePath ?: return null
        if (path.isBlank()) return null
        return File(path).also { it.mkdirs() }
    }

    suspend fun hasConfiguredLocation(): Boolean = getSavedRecordingsDir() != null

    suspend fun saveLocation(recordingsDir: File) {
        val profileId = profileDao.activeProfile()?.profileId ?: return
        val old = profileSettingsDao.get(profileId) ?: ProfileSettingsEntity(profileId = profileId)
        profileSettingsDao.upsert(
            old.copy(recordingStoragePath = recordingsDir.absolutePath)
        )
    }

    suspend fun saveLocationById(optionId: String) {
        val option = enumerateOptions().firstOrNull { it.id == optionId } ?: return
        saveLocation(option.recordingsDir)
    }

    suspend fun currentStorageLabel(): String? {
        val dir = getSavedRecordingsDir() ?: return null
        return enumerateOptions().firstOrNull { it.recordingsDir.canonicalPath == dir.canonicalPath }?.label
            ?: dir.absolutePath
    }

    suspend fun freeBytes(): Long = getSavedRecordingsDir()?.usableSpace ?: internalRecordingsDir().usableSpace

    suspend fun hasAtLeast2Gb(): Boolean = freeBytes() >= TWO_GB

    suspend fun isCriticalLowStorage(): Boolean = freeBytes() < CRITICAL_MB

    suspend fun freeStorageSummaryLine(): String {
        val label = currentStorageLabel() ?: "Internal Storage"
        return "${StorageFormat.formatFreeSpace(freeBytes())} on $label"
    }

    suspend fun isStorageAvailable(dir: File): Boolean {
        return try {
            (dir.exists() || dir.mkdirs()) && dir.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun currentStorageHealth(dir: File): StorageHealth {
        val free = runCatching { dir.usableSpace }.getOrDefault(0L)
        val available = isStorageAvailable(dir)
        val criticalLow = free < CRITICAL_MB
        return StorageHealth(
            available = available,
            freeBytes = free,
            isCriticalLow = criticalLow
        )
    }

    suspend fun lowStorageWarning(): String? =
        if (!hasAtLeast2Gb()) "Storage is below 2 GB free. Recording may fail." else null

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
