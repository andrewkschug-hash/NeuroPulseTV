package com.grid.tv.feature.scanner

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanMetricsLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var activeRequests = 0

    fun onRequestStarted() {
        activeRequests++
        logActiveRequests()
    }

    fun onRequestFinished() {
        activeRequests = (activeRequests - 1).coerceAtLeast(0)
    }

    fun logBatchStart(batchIndex: Int, batchSize: Int, totalRemaining: Int) {
        Log.i(
            TAG,
            "SCAN_BATCH_START batch=$batchIndex size=$batchSize remaining=$totalRemaining " +
                "ACTIVE_REQUESTS=$activeRequests MEMORY_USAGE_MB=${memoryUsageMb()}"
        )
    }

    fun logBatchEnd(batchIndex: Int, batchSize: Int) {
        Log.i(
            TAG,
            "SCAN_BATCH_END batch=$batchIndex size=$batchSize " +
                "ACTIVE_REQUESTS=$activeRequests MEMORY_USAGE_MB=${memoryUsageMb()}"
        )
    }

    fun logDnsFailure(hostname: String) {
        Log.w(TAG, "FAILED_DNS host=$hostname ACTIVE_REQUESTS=$activeRequests")
    }

    fun logHttp520(url: String) {
        Log.w(TAG, "FAILED_520 url=${url.take(120)} ACTIVE_REQUESTS=$activeRequests")
    }

    fun logHttp503(url: String) {
        Log.w(TAG, "FAILED_503 url=${url.take(120)} ACTIVE_REQUESTS=$activeRequests")
    }

    fun logHostBlacklisted(hostname: String, failureCount: Int) {
        Log.w(TAG, "HOST_BLACKLISTED host=$hostname failures=$failureCount")
    }

    private fun logActiveRequests() {
        Log.d(TAG, "ACTIVE_REQUESTS=$activeRequests MEMORY_USAGE_MB=${memoryUsageMb()}")
    }

    fun memoryUsageMb(): Long {
        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val processInfo = am.runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }
            if (processInfo != null) {
                val pssKb = runCatching {
                    am.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                        .firstOrNull()?.totalPss?.toLong() ?: 0L
                }.getOrDefault(0L)
                if (pssKb > 0) return pssKb / 1024
            }
        }
        return usedBytes / (1024 * 1024)
    }

    companion object {
        private const val TAG = "ChannelScanMetrics"
    }
}
