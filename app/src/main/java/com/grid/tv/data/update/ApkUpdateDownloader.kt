package com.grid.tv.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Singleton
class ApkUpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun downloadApk(
        url: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val destFile = apkDestinationFile()
        if (destFile.exists()) {
            destFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("GRID update")
            .setDescription("Downloading app update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                APK_FILE_NAME
            )

        val downloadId = downloadManager.enqueue(request)
        Log.i(TAG, "APK download enqueued id=$downloadId url=$url")
        pollDownload(downloadId, onProgress)
    }

    private suspend fun pollDownload(
        downloadId: Long,
        onProgress: (Int) -> Unit
    ): Result<File> {
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            var terminalResult: Result<File>? = null
            downloadManager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use
                }
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        onProgress(100)
                        val file = apkDestinationFile()
                        terminalResult = if (file.exists()) {
                            Log.i(TAG, "APK download complete path=${file.absolutePath}")
                            Result.success(file)
                        } else {
                            Result.failure(
                                IllegalStateException("Download finished but APK file is missing")
                            )
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = cursor.getInt(reasonIndex)
                        Log.e(TAG, "APK download failed reason=$reason")
                        terminalResult = Result.failure(
                            IllegalStateException("Download failed (reason=$reason)")
                        )
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        val downloadedIndex =
                            cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val downloaded = cursor.getLong(downloadedIndex)
                        val total = cursor.getLong(totalIndex)
                        if (total > 0L) {
                            onProgress(((downloaded * 100L) / total).toInt().coerceIn(0, 99))
                        }
                    }
                }
            }
            terminalResult?.let { return it }
            delay(POLL_INTERVAL_MS)
        }
    }

    fun launchInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority(), apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        Log.i(TAG, "Launched package installer for ${apkFile.absolutePath}")
    }

    fun apkDestinationFile(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(dir, APK_FILE_NAME)
    }

    private fun fileProviderAuthority(): String = "${context.packageName}.fileprovider"

    companion object {
        private const val TAG = "ApkUpdateDownloader"
        const val APK_FILE_NAME = "app-update.apk"
        private const val POLL_INTERVAL_MS = 400L
    }
}
