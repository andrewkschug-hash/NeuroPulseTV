package com.grid.tv.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.grid.tv.ui.component.GuideGroupVisibleRow
import com.grid.tv.ui.component.guideGroupVisibleRowKey
import com.grid.tv.ui.component.isFocusableGroupRow

/** Screen-owned channel-group row focus targets (EPG sidebar panel pattern). */
class GuideChannelGroupsFocusRegistry {
    private val requesters = mutableMapOf<String, FocusRequester>()

    fun requesterFor(row: GuideGroupVisibleRow): FocusRequester =
        requesters.getOrPut(guideGroupVisibleRowKey(row)) { FocusRequester() }

    fun requesterForIndex(visibleRows: List<GuideGroupVisibleRow>, index: Int): FocusRequester? {
        val row = visibleRows.getOrNull(index) ?: return null
        if (!row.isFocusableGroupRow()) return null
        return requesterFor(row)
    }
}

@Composable
fun rememberGuideChannelGroupsFocusRegistry(): GuideChannelGroupsFocusRegistry =
    remember { GuideChannelGroupsFocusRegistry() }
