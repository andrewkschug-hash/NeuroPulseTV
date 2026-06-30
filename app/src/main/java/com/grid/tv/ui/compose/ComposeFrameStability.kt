package com.grid.tv.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ChannelScanSnapshot
import com.grid.tv.domain.model.ChannelScanStatus
import com.grid.tv.domain.model.Program
import com.grid.tv.ui.component.formatLastChecked

typealias ProgramsForChannel = (Channel) -> List<Program>

@Immutable
data class EpgGuideRowCache(
    val programsByChannelId: Map<Long, List<Program>>,
    val scanStatusByChannelId: Map<Long, ChannelScanStatus?>,
    val scanLabelByChannelId: Map<Long, String?>,
    val lastChannelIndex: Int,
)

@Composable
fun rememberEpgGuideRowCache(
    displayChannels: List<Channel>,
    windowStart: Long,
    windowDurationMs: Long,
    channelScanStatuses: Map<Long, ChannelScanSnapshot>,
    gridNow: Long,
    programsForChannel: ProgramsForChannel,
): EpgGuideRowCache {
    val channelIds = remember(displayChannels) { displayChannels.map { it.id } }
    return remember(channelIds, windowStart, windowDurationMs, channelScanStatuses, gridNow) {
        val programs = HashMap<Long, List<Program>>(displayChannels.size)
        val scanStatuses = HashMap<Long, ChannelScanStatus?>(displayChannels.size)
        val scanLabels = HashMap<Long, String?>(displayChannels.size)
        displayChannels.forEach { channel ->
            programs[channel.id] = programsForChannel(channel)
            val snapshot = channelScanStatuses[channel.id]
            scanStatuses[channel.id] = snapshot?.status
            scanLabels[channel.id] = formatLastChecked(snapshot?.lastCheckedAt, gridNow)
        }
        EpgGuideRowCache(
            programsByChannelId = programs,
            scanStatusByChannelId = scanStatuses,
            scanLabelByChannelId = scanLabels,
            lastChannelIndex = displayChannels.lastIndex.coerceAtLeast(0),
        )
    }
}
