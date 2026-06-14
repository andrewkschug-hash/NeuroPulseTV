package com.neuropulse.tv.feature.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.neuropulse.tv.R
import com.neuropulse.tv.data.db.dao.RecordedMediaDao
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import com.neuropulse.tv.data.db.entity.RecordedMediaEntity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import com.neuropulse.tv.data.network.AppHttpClient
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

    @Inject
    lateinit var storageManager: RecordingStorageManager

    @Inject
    lateinit var appHttpClient: AppHttpClient

    private val scope = CoroutineScope(Dispatchers.IO)
    private var notificationJob: Job? = null
    private var recordingJob: Job? = null
    private var streamRecorder: TsStreamRecorder? = null
    private var currentOutputPath: String? = null
    private var recordingStartedAt: Long = 0L
    private var currentScheduledId: Long = -1
    private var currentChannelId: Long = -1
    private var currentChannelName: String = ""
    private var currentTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "RecordingService created")
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
                    Log.e(TAG, "ACTION_START missing stream URL — stopping service")
                    stopSelf()
                    return START_NOT_STICKY
                }
                currentScheduledId = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
                currentChannelId = intent.getLongExtra(EXTRA_CHANNEL_ID, -1L)
                currentChannelName = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty()
                currentTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val endAt = intent.getLongExtra(EXTRA_END_AT, 0L)
                Log.i(
                    TAG,
                    "ACTION_START scheduledId=$currentScheduledId channelId=$currentChannelId " +
                        "channel=$currentChannelName title=$currentTitle endAt=$endAt streamUrl=$streamUrl"
                )
                scope.launch { startRecording(streamUrl, endAt) }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun startRecording(streamUrl: String, endAt: Long) {
        val savedDir = storageManager.getSavedRecordingsDir()
        val internalDir = storageManager.internalRecordingsDir()
        val outputDir = savedDir ?: internalDir
        val outputFile = RecordingFilenameUtil.resolveUniqueFile(
            outputDir,
            currentChannelName,
            currentTitle,
            System.currentTimeMillis()
        )

        Log.i(
            TAG,
            "Recording storage savedDir=${savedDir?.absolutePath ?: "null"} " +
                "internalDir=${internalDir.absolutePath} usingDir=${outputDir.absolutePath}"
        )
        Log.i(
            TAG,
            "Recording output file path=${outputFile.absolutePath} exists=${outputFile.exists()} " +
                "parentExists=${outputFile.parentFile?.exists() == true} freeBytes=${outputDir.usableSpace}"
        )

        if (!storageManager.hasAtLeast2Gb()) {
            Log.w(TAG, "Low storage (<2GB free) before recording path=${outputFile.absolutePath}")
            startForeground(NOTIFICATION_ID, buildNotification(0, "Low storage (<2GB). Recording may fail."))
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(0, "Starting recording..."))
        }

        if (storageManager.isCriticalLowStorage()) {
            Log.e(TAG, "Critical low storage — aborting recording path=${outputFile.absolutePath}")
            stopSelf()
            return
        }

        currentOutputPath = outputFile.absolutePath
        recordingStartedAt = System.currentTimeMillis()
        Log.i(TAG, "Recording started at $recordingStartedAt path=$currentOutputPath")

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

        recordingJob?.cancel()
        recordingJob = scope.launch {
            try {
                val recorder = TsStreamRecorder(appHttpClient.client(), outputFile)
                streamRecorder = recorder
                val success = recorder.record(streamUrl)
                streamRecorder = null
                Log.i(
                    TAG,
                    "Recording loop finished success=$success path=${outputFile.absolutePath} " +
                        "exists=${outputFile.exists()} length=${outputFile.length()}"
                )
                finalizeRecording(success, outputDir)
            } catch (e: Exception) {
                Log.e(TAG, "Recording job failed path=${outputFile.absolutePath}", e)
                finalizeRecording(false, outputDir)
            } finally {
                stopSelf()
            }
        }

        notificationJob?.cancel()
        notificationJob = scope.launch {
            while (isActive) {
                val elapsedSec = ((System.currentTimeMillis() - recordingStartedAt) / 1000).coerceAtLeast(0)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(elapsedSec))

                if (storageManager.isCriticalLowStorage()) {
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
        Log.i(TAG, "stopRecording manual=$manual path=$currentOutputPath")
        notificationJob?.cancel()
        streamRecorder?.cancel()
        if (manual) stopSelf()
    }

    private suspend fun finalizeRecording(success: Boolean, outputDir: File) {
        val path = currentOutputPath ?: run {
            Log.w(TAG, "finalizeRecording called with no output path success=$success")
            return
        }
        val file = File(path)
        Log.i(
            TAG,
            "finalizeRecording success=$success path=$path exists=${file.exists()} length=${file.length()}"
        )
        if (currentScheduledId > 0) {
            scheduledRecordingDao.getById(currentScheduledId)?.let {
                scheduledRecordingDao.update(
                    it.copy(status = if (success) RecordingStatus.COMPLETED.name else RecordingStatus.FAILED.name)
                )
            }
        }
        if (success && file.exists()) {
            val duration = System.currentTimeMillis() - recordingStartedAt
            val thumbDir = File(outputDir, ".thumbnails")
            val thumbnailPath = try {
                RecordingThumbnailExtractor.extractThumbnail(path, thumbDir)
            } catch (e: Exception) {
                Log.e(TAG, "Thumbnail extraction failed path=$path", e)
                null
            }
            recordedMediaDao.insert(
                RecordedMediaEntity(
                    channelId = currentChannelId,
                    channelName = currentChannelName,
                    programTitle = currentTitle,
                    filePath = path,
                    durationMs = duration,
                    fileSizeBytes = file.length(),
                    recordedAt = System.currentTimeMillis(),
                    thumbnailPath = thumbnailPath
                )
            )
            Log.i(TAG, "Recorded media saved path=$path size=${file.length()} durationMs=$duration")
        } else if (!success) {
            Log.e(
                TAG,
                "Recording failed — no media saved path=$path exists=${file.exists()} length=${file.length()}"
            )
        }
    }

    override fun onDestroy() {
        Log.i(
            TAG,
            "RecordingService destroyed scheduledId=$currentScheduledId path=$currentOutputPath " +
                "recordingJobActive=${recordingJob?.isActive == true}"
        )
        notificationJob?.cancel()
        streamRecorder?.cancel()
        recordingJob?.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedSec: Long, statusOverride: String? = null): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_STOP }
        val stopPending = android.app.PendingIntent.getService(
            this,
            22101,
            stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val title = "● Recording — ${currentTitle.ifBlank { "Program" }} (${currentChannelName.ifBlank { "Channel" }})"
        val elapsed = statusOverride ?: RecordingCountdown.formatElapsed(elapsedSec)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(elapsed)
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
        private const val TAG = "RecordingService"
    }
}
