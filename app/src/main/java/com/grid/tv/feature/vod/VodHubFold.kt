package com.grid.tv.feature.vod

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Lume-inspired fold zones for the VOD hub hero + content rows. */
enum class VodHubFoldZone {
    EXPANDED,
    STRIP,
    ROWS
}

object VodHubFoldMetrics {
    val HeroExpandedHeight: Dp = 420.dp
    val HeroStripHeight: Dp = 200.dp
    val RowPeek: Dp = 120.dp
}

fun resolveVodHubFoldZone(
    scrollOffsetPx: Int,
    heroExpandedPx: Float
): VodHubFoldZone {
    if (heroExpandedPx <= 0f) return VodHubFoldZone.ROWS
    val collapsed = heroExpandedPx - VodHubFoldMetrics.HeroStripHeight.value * 3f // approx px at mdpi scale
    return when {
        scrollOffsetPx < collapsed * 0.5f -> VodHubFoldZone.EXPANDED
        scrollOffsetPx < heroExpandedPx - 40f -> VodHubFoldZone.STRIP
        else -> VodHubFoldZone.ROWS
    }
}

@Composable
fun rememberVodHubFoldScroller(
    listState: LazyListState,
    heroExpandedPx: Float
): VodHubFoldScroller {
    val scope = rememberCoroutineScope()
    return VodHubFoldScroller(
        listState = listState,
        heroExpandedPx = heroExpandedPx,
        scope = scope
    )
}

class VodHubFoldScroller(
    private val listState: LazyListState,
    private val heroExpandedPx: Float,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    fun currentZone(): VodHubFoldZone =
        resolveVodHubFoldZone(listState.firstVisibleItemScrollOffset, heroExpandedPx)

    fun snapDownFromHero() {
        val collapsed = (heroExpandedPx - VodHubFoldMetrics.HeroStripHeight.value * 3f).coerceAtLeast(0f)
        scope.launch {
            val delta = collapsed - listState.firstVisibleItemScrollOffset
            if (delta > 0f) listState.animateScrollBy(delta)
        }
    }

    fun snapToExpanded() {
        scope.launch {
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            if (offset > 0f) listState.animateScrollBy(-offset)
        }
    }

    fun snapToRows() {
        scope.launch {
            val target = heroExpandedPx.coerceAtLeast(0f)
            val delta = target - listState.firstVisibleItemScrollOffset
            if (delta > 0f) listState.animateScrollBy(delta)
        }
    }
}
