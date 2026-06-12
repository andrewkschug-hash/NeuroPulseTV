package com.neuropulse.tv.ui.component

import android.content.pm.PackageManager
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Tunable TV focus-scroll behavior (safe zone, centering, animation). */
object TvFocusScrollConfig {
    var enabled: Boolean = true
    var safeZoneDp: Dp = 80.dp
    var preferCenter: Boolean = true
    var animateScroll: Boolean = true
}

val LocalTvFocusScrollState = compositionLocalOf<TvFocusScrollState?> { null }

/**
 * Pure scroll-target calculation for focus-aware scrolling.
 * Returns null when the focused item is already inside the safe zone.
 */
fun calculateFocusScrollTarget(
    currentScroll: Int,
    maxScroll: Int,
    viewportHeight: Int,
    itemTop: Float,
    itemBottom: Float,
    safeZonePx: Float,
    preferCenter: Boolean = true,
    preferTopAlign: Boolean = false
): Int? {
    if (viewportHeight <= 0) return null
    val viewportTop = currentScroll.toFloat()
    val viewportBottom = viewportTop + viewportHeight
    val topSafe = viewportTop + safeZonePx
    val bottomSafe = viewportBottom - safeZonePx

    if (!preferTopAlign && itemTop >= topSafe && itemBottom <= bottomSafe) return null

    val rawTarget = when {
        preferTopAlign -> {
            when {
                itemTop < topSafe -> currentScroll + (itemTop - topSafe)
                itemTop > bottomSafe -> currentScroll + (itemTop - topSafe)
                itemBottom > bottomSafe -> currentScroll + (itemTop - topSafe)
                else -> currentScroll.toFloat()
            }
        }
        preferCenter -> {
            val itemCenter = (itemTop + itemBottom) / 2f
            itemCenter - viewportHeight / 2f
        }
        else -> when {
            itemTop < topSafe -> currentScroll + (itemTop - topSafe)
            itemBottom > bottomSafe -> currentScroll + (itemBottom - bottomSafe)
            else -> currentScroll.toFloat()
        }
    }
    val target = rawTarget.roundToInt().coerceIn(0, maxScroll.coerceAtLeast(0))
    return if (target == currentScroll) null else target
}

@Stable
class TvFocusScrollState(
    val scrollState: ScrollState
) {
    var containerCoords: LayoutCoordinates? by mutableStateOf(null)
        private set

    fun updateContainer(coords: LayoutCoordinates) {
        containerCoords = coords
    }

    suspend fun scrollIntoViewIfNeeded(
        itemCoords: LayoutCoordinates,
        safeZonePx: Float,
        preferTopAlign: Boolean = false
    ) {
        if (!TvFocusScrollConfig.enabled) return
        val container = containerCoords ?: return
        if (!container.isAttached || !itemCoords.isAttached) return

        repeat(3) { attempt ->
            val itemTop = container.localPositionOf(itemCoords, Offset.Zero).y
            val itemBottom = itemTop + itemCoords.size.height
            val viewportHeight = container.size.height
            val target = calculateFocusScrollTarget(
                currentScroll = scrollState.value,
                maxScroll = scrollState.maxValue,
                viewportHeight = viewportHeight,
                itemTop = itemTop,
                itemBottom = itemBottom,
                safeZonePx = safeZonePx,
                preferCenter = TvFocusScrollConfig.preferCenter,
                preferTopAlign = preferTopAlign
            ) ?: return

            val delta = target - scrollState.value
            if (delta == 0) return
            if (TvFocusScrollConfig.animateScroll) {
                scrollState.animateScrollBy(delta.toFloat())
            } else {
                scrollState.scrollTo(target)
            }
            if (attempt < 2) delay(32)
        }
    }
}

@Composable
fun rememberTvFocusScrollState(): TvFocusScrollState {
    val scrollState = rememberScrollState()
    return remember(scrollState) { TvFocusScrollState(scrollState) }
}

@Composable
fun isTvDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val pm = context.packageManager
        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }
}

/**
 * Scroll container for TV-first UIs. Descendants that use [tvFocusScrollIntoView] will
 * automatically scroll into view when focused via D-pad. Mouse wheel / drag scrolling
 * on the same [ScrollState] continues to work on desktop.
 */
@Composable
fun TvScrollContainer(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val focusScrollState = rememberTvFocusScrollState()
    CompositionLocalProvider(LocalTvFocusScrollState provides focusScrollState) {
        // Measure the visible viewport (Box), not the full scrollable Column height.
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { focusScrollState.updateContainer(it) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(focusScrollState.scrollState),
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                content = content
            )
        }
    }
}

/**
 * When this element receives focus, scroll the nearest [TvScrollContainer] so the element
 * stays visible inside the safe zone (preferring vertical centering).
 */
@Composable
fun Modifier.tvFocusScrollIntoView(
    scrollState: TvFocusScrollState? = LocalTvFocusScrollState.current,
    enabled: Boolean = true
): Modifier {
    if (scrollState == null || !enabled) return this

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val safeZonePx = with(density) { TvFocusScrollConfig.safeZoneDp.toPx() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    return this
        .onGloballyPositioned { coordinates = it }
        .onFocusChanged { focus ->
            if (focus.isFocused) {
                val coords = coordinates
                if (coords != null) {
                    scope.launch {
                        // Allow layout to settle after programmatic focus moves (D-pad chains).
                        delay(16)
                        scrollState.scrollIntoViewIfNeeded(coords, safeZonePx)
                    }
                }
            }
        }
}

/**
 * Scrolls this element into view when [active] becomes true (e.g. section highlight without
 * moving focus to a child). Uses top-align so card headers and content below stay visible.
 */
@Composable
fun Modifier.tvScrollIntoViewWhen(
    active: Boolean,
    scrollState: TvFocusScrollState? = LocalTvFocusScrollState.current,
    preferTopAlign: Boolean = true,
    enabled: Boolean = true
): Modifier {
    if (!enabled || scrollState == null || !active) return this

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val safeZonePx = with(density) { TvFocusScrollConfig.safeZoneDp.toPx() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(active, coordinates) {
        if (!active) return@LaunchedEffect
        val coords = coordinates ?: return@LaunchedEffect
        delay(16)
        scrollState.scrollIntoViewIfNeeded(coords, safeZonePx, preferTopAlign = preferTopAlign)
    }

    return this.onGloballyPositioned { coordinates = it }
}

/** Wrapper for arbitrary focusable TV controls. */
@Composable
fun TvFocusable(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.tvFocusScrollIntoView(enabled = enabled)
    ) {
        content()
    }
}
