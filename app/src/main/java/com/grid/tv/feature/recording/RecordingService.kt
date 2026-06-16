package com.grid.tv.feature.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.grid.tv.R
import com.grid.tv.data.db.dao.RecordedMediaDao
import com.grid.tv.data.db.dao.ScheduledRecordingDao
import com.grid.tv.data.db.entity.RecordedMediaEntity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import com.grid.tv.data.network.AppHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var scheduledRecordingDao: ScheduledRecordingDao
    @Inject lateinit var recordedMediaDao: RecordedMediaDao
    @Inject lateinit var storageManager: RecordingStorageManager
    @Inject lateinit var appHttpClient: AppHttpClient
    @Inject lateinit var activeSession: ActiveRecordingSession

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
    private var currentHealth: RecordingHealth = RecordingHealth.IDLE
    @Volatile private var pauseForStorage = false
    private var currentOutputDir: File? = null
    private var preflightEstimateText: String? = null

    private var wakeLock: PowerManager.WakeLock? = null

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
                Log.i(TAG, "ACTION_START scheduledId=$currentScheduledId title=$currentTitle")
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
            outputDir, currentChannelName, currentTitle, System.currentTimeMillis()
        )
        currentOutputDir = outputDir
        preflightEstimateText = null

        if (storageManager.currentStorageHealth(outputDir).isCriticalLow) {
            Log.e(TAG, "Critical low storage at start — aborting recording")
            stopSelf()
            return
        }

        acquireWakeLock()

        if (!storageManager.hasAtLeast2Gb()) {
            Log.w(TAG, "Low storage (<2 GB free)")
        }

        startForeground(NOTIFICATION_ID, buildNotification(0))

        currentOutputPath = outputFile.absolutePath
        recordingStartedAt = System.currentTimeMillis()
        val estimatedBytes = estimateRecordingBytes(endAt)
        preflightEstimateText = if (estimatedBytes != null) {
            val free = storageManager.currentStorageHealth(outputDir).freeBytes
            "Est. ${StorageFormat.formatFileSize(estimatedBytes)} · Free ${StorageFormat.formatFreeSpace(free)}"
        } else {
            null
        }

        if (currentScheduledId > 0) {
            scheduledRecordingDao.getById(currentScheduledId)?.let {
                scheduledRecordingDao.update(
                    it.copy(status = RecordingStatus.RECORDING.name, outputPath = currentOutputPath)
                )
            }
        }

        recordingJob?.cancel()
        recordingJob = scope.launch {
            try {
                val recorder = TsStreamRecorder(
                    client = appHttpClient.client(),
                    outputFile = outputFile,
                    onHealthChanged = { health ->
                        currentHealth = health
                        activeSession.setHealth(health)
                    },
                    shouldPauseForStorage = { pauseForStorage }
                )
                streamRecorder = recorder
                val outcome = recorder.record(streamUrl)
                streamRecorder = null
                Log.i(TAG, "Recording loop finished $outcome path=${outputFile.absolutePath}")
                finalizeRecording(outcome, outputDir, endAt)
            } catch (e: Exception) {
                Log.e(TAG, "Recording job failed", e)
                finalizeRecording(
                    RecordingOutcome(bytesWritten = 0, hadDropouts = true, signalLost = true),
                    outputDir,
                    endAt
                )
            } finally {
                releaseWakeLock()
                activeSession.clear()
                stopSelf()
            }
        }

        notificationJob?.cancel()
        notificationJob = scope.launch {
            while (isActive) {
                val elapsedSec = ((System.currentTimeMillis() - recordingStartedAt) / 1000).coerceAtLeast(0)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(elapsedSec))

                refreshStoragePauseState()
                if (endAt > 0 && System.currentTimeMillis() >= endAt) {
                    stopRecording(manual = false)
                    break
                }
                delay(1_000)
            }
        }
    }

    private fun stopRecording(manual: Boolean) {
        Log.i(TAG, "stopRecording manual=$manual path=$currentOutputPath")
        notificationJob?.cancel()
        streamRecorder?.cancel()
        if (manual) stopSelf()
    }

    private suspend fun finalizeRecording(outcome: RecordingOutcome, outputDir: File, scheduledEndMs: Long) {
        val path = currentOutputPath ?: return
        val file = File(path)

        val resolvedStatus = when {
            !outcome.saved -> RecordingStatus.FAILED
            else -> RecordingStatus.COMPLETED
        }

        if (currentScheduledId > 0) {
            scheduledRecordingDao.getById(currentScheduledId)?.let {
                scheduledRecordingDao.update(it.copy(status = resolvedStatus.name))
            }
        }

        if (!outcome.saved) {
            Log.e(TAG, "Recording failed — file empty or missing path=$path")
            sendNotification("Recording failed — ${currentTitle.ifBlank { "Program" }}", NOTIFICATION_ID_EVENTS)
            return
        }

        val duration = System.currentTimeMillis() - recordingStartedAt
        val expectedMs = if (scheduledEndMs > recordingStartedAt) scheduledEndMs - recordingStartedAt else duration

        val integrity = RecordingIntegrityChecker.validate(file, expectedMs, outcome)
        Log.i(TAG, "Integrity check: ${integrity.status} — ${integrity.message}")

        val thumbDir = File(outputDir, ".thumbnails")
        val thumbnailPath = runCatching { RecordingThumbnailExtractor.extractThumbnail(path, thumbDir) }
            .onFailure { Log.e(TAG, "Thumbnail extraction failed path=$path", it) }
            .getOrNull()

        recordedMediaDao.insert(
            RecordedMediaEntity(
                channelId = currentChannelId,
                channelName = currentChannelName,
                programTitle = currentTitle,
                filePath = path,
                durationMs = duration,
                fileSizeBytes = file.length(),
                recordedAt = System.currentTimeMillis(),
                thumbnailPath = thumbnailPath,
                integrityStatus = integrity.status.name
            )
        )
        Log.i(TAG, "Recorded media saved path=$path size=${file.length()} durationMs=$duration integrity=${integrity.status}")

        val eventMsg = if (integrity.status == RecordingIntegrityStatus.OK) {
            "Recording saved — ${currentTitle.ifBlank { "Program" }}"
        } else {
            "Recording saved with issues (${integrity.status.name.lowercase().replace('_', ' ')}) — ${currentTitle.ifBlank { "Program" }}"
        }
        sendNotification(eventMsg, NOTIFICATION_ID_EVENTS)
    }

    private fun sendNotification(text: String, notificationId: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, CHANNEL_ID_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("GRID Recording")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(notificationId, n)
    }

    override fun onDestroy() {
        Log.i(TAG, "RecordingService destroyed scheduledId=$currentScheduledId path=$currentOutputPath")
        notificationJob?.cancel()
        streamRecorder?.cancel()
        recordingJob?.cancel()
        releaseWakeLock()
        activeSession.clear()
        pauseForStorage = false
        currentOutputDir = null
        super.onDestroy()
    }

    private suspend fun refreshStoragePauseState() {
        val dir = currentOutputDir ?: return
        val health = storageManager.currentStorageHealth(dir)
        val shouldPause = !health.available || health.isCriticalLow
        if (shouldPause != pauseForStorage) {
            pauseForStorage = shouldPause
            currentHealth = if (shouldPause) RecordingHealth.STORAGE_PAUSED else RecordingHealth.RECORDING
            activeSession.setHealth(currentHealth)
            if (shouldPause) {
                sendNotification(
                    "Recording paused — ${if (!health.available) "storage unavailable" else "storage full"}",
                    NOTIFICATION_ID_EVENTS
                )
            } else {
                sendNotification("Recording resumed — storage available", NOTIFICATION_ID_EVENTS)
            }
        }
    }

    private fun estimateRecordingBytes(endAt: Long): Long? {
        if (endAt <= 0L || recordingStartedAt <= 0L) return null
        val durationMs = (endAt - recordingStartedAt).coerceAtLeast(0L)
        if (durationMs <= 0L) return null
        return RecordingBitrateEstimator.estimateBytes(durationMs)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "grid:recording").apply {
            acquire(4 * 60 * 60 * 1_000L) // max 4-hour safety timeout
        }
        Log.i(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_EVENTS, "Recording Events", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun buildNotification(elapsedSec: Long): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_STOP }
        val stopPending = android.app.PendingIntent.getService(
            this, 22101, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val statusText: String = when (currentHealth) {
            RecordingHealth.RECONNECTING -> "⟳  Reconnecting… (${currentTitle.ifBlank { "Program" }})"
            RecordingHealth.SIGNAL_LOST  -> "✕  Signal lost  (${currentTitle.ifBlank { "Program" }})"
            RecordingHealth.STORAGE_PAUSED -> "⏸  Paused — storage unavailable/full"
            else -> {
                val elapsed = RecordingCountdown.formatElapsed(elapsedSec)
                val estimate = preflightEstimateText?.let { " · $it" }.orEmpty()
                "● Recording — ${currentTitle.ifBlank { "Program" }} ($elapsed)$estimate"
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("GRID Recording")
            .setContentText(statusText)
            .setOngoing(true)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    companion object {
        const val ACTION_START = "com.grid.tv.recording.START"
        const val ACTION_STOP  = "com.grid.tv.recording.STOP"
        const val EXTRA_SCHEDULED_ID  = "extra_scheduled_id"
        const val EXTRA_CHANNEL_ID    = "extra_channel_id"
        const val EXTRA_CHANNEL_NAME  = "extra_channel_name"
        const val EXTRA_TITLE         = "extra_title"
        const val EXTRA_STREAM_URL    = "extra_stream_url"
        const val EXTRA_END_AT        = "extra_end_at"

        private const val CHANNEL_ID        = "recording_channel"
        private const val CHANNEL_ID_EVENTS = "recording_events_channel"
        private const val NOTIFICATION_ID   = 22100
        private const val NOTIFICATION_ID_EVENTS = 22102
        private const val TAG = "RecordingService"
    }
}
