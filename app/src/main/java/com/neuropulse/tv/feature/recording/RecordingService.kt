package com.neuropulse.tv.feature.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.neuropulse.tv.R
import com.neuropulse.tv.data.db.dao.RecordedMediaDao
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import com.neuropulse.tv.data.db.entity.RecordedMediaEntity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject
    lateinit var scheduledRecordingDao: ScheduledRecordingDao

    @Inject
    lateinit var recordedMediaDao: RecordedMediaDao

    private val scope = CoroutineScope(Dispatchers.IO)
    private var notificationJob: Job? = null
    private var currentOutputPath: String? = null
    private var recordingStartedAt: Long = 0L
    private var ffmpegSessionId: Long = -1
    private var currentScheduledId: Long = -1
    private var currentChannelId: Long = -1
    private var currentChannelName: String = ""
    private var currentTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording(manual = true)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
                if (streamUrl.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                currentScheduledId = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
                currentChannelId = intent.getLongExtra(EXTRA_CHANNEL_ID, -1L)
                currentChannelName = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty()
                currentTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val endAt = intent.getLongExtra(EXTRA_END_AT, 0L)
                startRecording(streamUrl, endAt)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording(streamUrl: String, endAt: Long) {
        if (!StorageUtils.hasAtLeast2Gb(this)) {
            startForeground(NOTIFICATION_ID, buildNotification("Low storage (<2GB). Recording may fail."))
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Starting recording..."))
        }

        if (StorageUtils.isCriticalLowStorage(this)) {
            stopSelf()
            return
        }

        val outputFile = File(
            StorageUtils.outputDirectory(this),
            RecordingFilenameUtil.buildFileName(currentChannelName, currentTitle, System.currentTimeMillis())
        )
        currentOutputPath = outputFile.absolutePath
        recordingStartedAt = System.currentTimeMillis()

        scope.launch {
            if (currentScheduledId > 0) {
                scheduledRecordingDao.getById(currentScheduledId)?.let {
                    scheduledRecordingDao.update(
                        it.copy(
                            status = RecordingStatus.RECORDING.name,
                            outputPath = currentOutputPath
                        )
                    )
                }
            }
        }

        val whitelist = if (streamUrl.contains(".m3u8", ignoreCase = true)) {
            "-protocol_whitelist file,http,https,tcp,tls,crypto"
        } else {
            ""
        }
        val command = listOf(
            whitelist,
            "-i", "\"$streamUrl\"",
            "-c", "copy",
            "-movflags", "+faststart",
            "\"${outputFile.absolutePath}\""
        ).filter { it.isNotBlank() }.joinToString(" ")

        val session = FFmpegKit.executeAsync(command) { completed ->
            val ok = ReturnCode.isSuccess(completed.returnCode)
            scope.launch { finalizeRecording(ok) }
            stopSelf()
        }
        ffmpegSessionId = session.sessionId

        notificationJob?.cancel()
        notificationJob = scope.launch {
            while (isActive) {
                val elapsed = ((System.currentTimeMillis() - recordingStartedAt) / 1000).coerceAtLeast(0)
                val sizeBytes = outputFile.length()
                val status = "REC ${elapsed}s | ${(sizeBytes / (1024 * 1024)).coerceAtLeast(0)}MB"
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(status))

                if (StorageUtils.isCriticalLowStorage(this@RecordingService)) {
                    stopRecording(manual = false)
                    break
                }
                if (endAt > 0 && System.currentTimeMillis() >= endAt) {
                    stopRecording(manual = false)
                    break
                }
                delay(1000)
            }
        }
    }

    private fun stopRecording(manual: Boolean) {
        notificationJob?.cancel()
        if (ffmpegSessionId > 0) FFmpegKit.cancel(ffmpegSessionId)
        if (manual) stopSelf()
    }

    private suspend fun finalizeRecording(success: Boolean) {
        val path = currentOutputPath ?: return
        val file = File(path)
        if (currentScheduledId > 0) {
            scheduledRecordingDao.getById(currentScheduledId)?.let {
                scheduledRecordingDao.update(
                    it.copy(status = if (success) RecordingStatus.COMPLETED.name else RecordingStatus.FAILED.name)
                )
            }
        }
        if (success && file.exists()) {
            val duration = System.currentTimeMillis() - recordingStartedAt
            recordedMediaDao.insert(
                RecordedMediaEntity(
                    channelId = currentChannelId,
                    channelName = currentChannelName,
                    programTitle = currentTitle,
                    filePath = path,
                    durationMs = duration,
                    fileSizeBytes = file.length(),
                    recordedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        if (ffmpegSessionId > 0) FFmpegKit.cancel(ffmpegSessionId)
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_STOP }
        val stopPending = android.app.PendingIntent.getService(
            this,
            22101,
            stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Recording ${currentChannelName.ifBlank { "Stream" }}")
            .setContentText(text)
            .setOngoing(true)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    companion object {
        const val ACTION_START = "com.neuropulse.tv.recording.START"
        const val ACTION_STOP = "com.neuropulse.tv.recording.STOP"
        const val EXTRA_SCHEDULED_ID = "extra_scheduled_id"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_END_AT = "extra_end_at"

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 22100
    }
}
