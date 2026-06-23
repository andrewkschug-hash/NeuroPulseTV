package com.grid.tv.util

import android.util.Log

object GuideNavigationLogger {
    private const val TAG = "GuideNavigation"

    fun backPressed(
        zone: String,
        channelIndex: Int,
        onChannelColumn: Boolean,
        detailExpanded: Boolean,
        branch: String
    ) {
        Log.i(
            TAG,
            "BACK_PRESSED zone=$zone channelIndex=$channelIndex onChannelColumn=$onChannelColumn " +
                "detailExpanded=$detailExpanded branch=$branch"
        )
    }

    fun focusRestoreCurrent(channelIndex: Int) {
        Log.i(TAG, "FOCUS_RESTORE_CURRENT channelIndex=$channelIndex")
    }

    fun focusRestoreTop() {
        Log.i(TAG, "FOCUS_RESTORE_TOP")
    }

    fun scrollToTop(index: Int) {
        Log.i(TAG, "SCROLL_TO_TOP index=$index")
    }

    fun currentChannelIndex(index: Int) {
        Log.i(TAG, "CURRENT_CHANNEL_INDEX index=$index")
    }
}
