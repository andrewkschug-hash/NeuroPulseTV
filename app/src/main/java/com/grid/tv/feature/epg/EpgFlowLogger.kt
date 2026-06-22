package com.grid.tv.feature.epg

import android.util.Log

/**
 * Structured lifecycle logs for XMLTV download → parse → DB → guide UI.
 * Filter logcat with tag [TAG] or event prefix `EPG_`.
 */
object EpgFlowLogger {
    const val TAG = "EpgFlow"

    fun downloadStarted(playlistId: Long, playlistName: String, url: String) {
        Log.i(
            TAG,
            "EPG_DOWNLOAD_STARTED playlistId=$playlistId name=$playlistName url=$url " +
                "dispatcher=${Thread.currentThread().name}"
        )
    }

    fun downloadCompleted(playlistId: Long, playlistName: String, httpCode: Int, bytesReceived: Long) {
        Log.i(
            TAG,
            "EPG_DOWNLOAD_COMPLETED playlistId=$playlistId name=$playlistName " +
                "http=$httpCode bytes=$bytesReceived"
        )
    }

    fun parseStarted(playlistId: Long, playlistName: String, url: String) {
        Log.i(
            TAG,
            "EPG_PARSE_STARTED playlistId=$playlistId name=$playlistName url=$url"
        )
    }

    fun parseCompleted(
        playlistId: Long,
        playlistName: String,
        channelsParsed: Int,
        programsParsed: Int
    ) {
        Log.i(
            TAG,
            "EPG_PARSE_COMPLETED playlistId=$playlistId name=$playlistName " +
                "channelsParsed=$channelsParsed programsParsed=$programsParsed"
        )
    }

    fun dbWriteStarted(playlistId: Long, playlistName: String) {
        Log.i(TAG, "EPG_DB_WRITE_STARTED playlistId=$playlistId name=$playlistName")
    }

    fun channelsImported(playlistId: Long, playlistName: String, count: Int, sourceKey: String) {
        Log.i(
            TAG,
            "EPG_CHANNELS_IMPORTED playlistId=$playlistId name=$playlistName " +
                "count=$count source=$sourceKey"
        )
    }

    fun programsImported(playlistId: Long, playlistName: String, count: Int) {
        Log.i(
            TAG,
            "EPG_PROGRAMS_IMPORTED playlistId=$playlistId name=$playlistName count=$count"
        )
    }

    fun dbWriteCompleted(playlistId: Long, playlistName: String) {
        Log.i(TAG, "EPG_DB_WRITE_COMPLETED playlistId=$playlistId name=$playlistName")
    }

    fun importFailed(playlistId: Long, playlistName: String, url: String?, error: Throwable) {
        Log.e(
            TAG,
            "EPG_IMPORT_FAILED playlistId=$playlistId name=$playlistName url=$url " +
                "error=${error.message}",
            error
        )
    }

    fun guideLoaded(programCount: Int, channelCount: Int, matchedChannelCount: Int) {
        Log.i(
            TAG,
            "EPG_GUIDE_LOADED programs=$programCount channels=$channelCount matched=$matchedChannelCount"
        )
    }

    fun revisionBumped(revision: Long) {
        Log.i(TAG, "EPG_DATA_REVISION revision=$revision")
    }
}
