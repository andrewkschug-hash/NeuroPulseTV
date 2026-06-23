package com.grid.tv.player

import android.util.Log

/** Per-tune timing markers for live channel startup profiling. */
object TuneLatencyTracker {
    private const val TAG = "TuneLatency"

    @Volatile
    private var sessionId: Int = 0

    @Volatile
    private var channelId: Long = 0L

    @Volatile
    private var channelSelectedAtMs: Long = 0L

    private val loggedEvents = LinkedHashSet<String>()

    @Synchronized
    fun beginTune(selectedChannelId: Long) {
        sessionId++
        channelId = selectedChannelId
        channelSelectedAtMs = System.currentTimeMillis()
        loggedEvents.clear()
        logEvent("CHANNEL_SELECTED", "channelId=$selectedChannelId session=$sessionId")
    }

    @Synchronized
    fun logEvent(event: String, detail: String = "") {
        if (!loggedEvents.add(event)) return
        val elapsed = System.currentTimeMillis() - channelSelectedAtMs
        val suffix = if (detail.isBlank()) "" else " $detail"
        Log.i(TAG, "$event elapsedMs=$elapsed channelId=$channelId session=$sessionId$suffix")
        if (event == "FIRST_VIDEO_FRAME") {
            Log.i(
                TAG,
                "TUNE_TOTAL channelId=$channelId session=$sessionId " +
                    "selectedToFirstFrameMs=$elapsed"
            )
        }
    }
}
