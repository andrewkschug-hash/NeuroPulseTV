package com.neuropulse.tv.player.multiview

import com.neuropulse.tv.domain.model.Channel

enum class MultiViewLayout(val panelCount: Int) {
    TWO(2),
    FOUR(4)
}

data class MultiViewPanelState(
    val index: Int,
    val channel: Channel? = null
)

data class MultiViewState(
    val layout: MultiViewLayout = MultiViewLayout.FOUR,
    val panels: List<MultiViewPanelState> = emptyList(),
    val activeAudioPanelIndex: Int = 0,
    val focusedPanelIndex: Int = 0,
    val fullscreenPanelIndex: Int? = null,
    val replacingPanelIndex: Int? = null
) {
    val isFullscreen: Boolean get() = fullscreenPanelIndex != null

    companion object {
        fun initial(layout: MultiViewLayout, seedChannel: Channel?): MultiViewState {
            val panels = List(layout.panelCount) { index ->
                MultiViewPanelState(index = index, channel = if (index == 0) seedChannel else null)
            }
            return MultiViewState(layout = layout, panels = panels)
        }
    }
}
