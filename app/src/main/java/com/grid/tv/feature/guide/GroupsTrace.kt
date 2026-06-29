package com.grid.tv.feature.guide

import android.util.Log

/** Channel Groups ANR investigation — filter logcat: `adb logcat -s GROUPS_TRACE` */
object GroupsTrace {

    const val TAG = "GROUPS_TRACE"

    fun logMetadataLoaded(
        source: String,
        groupCount: Int,
        durationMs: Long,
        cached: Boolean = false,
        thread: String = Thread.currentThread().name
    ) {
        Log.i(
            TAG,
            "[GROUPS_TRACE]\n" +
                "operation=metadataLoaded\n" +
                "source=$source\n" +
                "thread=$thread\n" +
                "groupCount=$groupCount\n" +
                "durationMs=$durationMs\n" +
                "cached=$cached"
        )
    }

    fun logOrganize(
        rawGroupCount: Int,
        durationMs: Long,
        flatCategoryCount: Int,
        thread: String = Thread.currentThread().name
    ) {
        Log.i(
            TAG,
            "[GROUPS_TRACE]\n" +
                "operation=organizeGroups\n" +
                "thread=$thread\n" +
                "rawGroupCount=$rawGroupCount\n" +
                "flatCategoryCount=$flatCategoryCount\n" +
                "durationMs=$durationMs"
        )
    }

    fun logVisibleRows(
        visibleRowCount: Int,
        focusRequesterCount: Int,
        thread: String = Thread.currentThread().name
    ) {
        Log.i(
            TAG,
            "[GROUPS_TRACE]\n" +
                "operation=visibleRows\n" +
                "thread=$thread\n" +
                "visibleRowCount=$visibleRowCount\n" +
                "focusRequesterCount=$focusRequesterCount"
        )
    }
}
