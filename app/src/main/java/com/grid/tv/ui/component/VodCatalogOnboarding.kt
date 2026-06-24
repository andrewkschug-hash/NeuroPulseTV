package com.grid.tv.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.VodNetflixColors
import kotlinx.coroutines.delay

/** Which VOD surface applies onboarding readiness rules. */
enum class VodCatalogOnboardingTab {
    ALL,
    MOVIES,
    SERIES
}

data class VodCatalogOnboardingInputs(
    val catalogLoading: Boolean,
    val progress: VodCatalogProgress,
    val tab: VodCatalogOnboardingTab,
    val browseRowCount: Int,
    val categoryCount: Int,
    val wallRowCount: Int = 0,
    val nonPersonalWallRowCount: Int = 0,
    val pagedItemCount: Int = 0,
)

private const val MIN_ALL_WALL_ROWS = 3
private const val MIN_ALL_NON_PERSONAL_WALL_ROWS = 2
private const val MIN_PAGED_ITEMS_WITH_CATEGORY = 16
private const val MIN_PAGED_ITEMS_PHASE_COMPLETE = 8
private const val MIN_BROWSE_ROWS = 2
private const val READINESS_STABILIZATION_MS = 400L

/**
 * Rows that reflect watch history rather than catalog discovery content.
 * ALL-tab readiness requires enough rows beyond these.
 */
fun isPersonalHistoryWallRow(row: VodWallRow): Boolean {
    if (row.id == "continue_watching") return true
    val title = row.title.trim().lowercase()
    return title == "continue watching" ||
        title == "resume watching" ||
        title == "recently viewed"
}

fun countNonPersonalWallRows(wallRows: List<VodWallRow>): Int =
    wallRows.count { !isPersonalHistoryWallRow(it) }

fun isVodCatalogPipelineComplete(tab: VodCatalogOnboardingTab, progress: VodCatalogProgress): Boolean =
    when (tab) {
        VodCatalogOnboardingTab.ALL ->
            progress.moviesPhaseFinished && progress.seriesPhaseFinished
        VodCatalogOnboardingTab.MOVIES ->
            progress.moviesPhaseFinished
        VodCatalogOnboardingTab.SERIES ->
            progress.seriesPhaseFinished
    }

fun isVodCatalogContentReady(inputs: VodCatalogOnboardingInputs): Boolean {
    val progress = inputs.progress
    return when (inputs.tab) {
        VodCatalogOnboardingTab.ALL ->
            inputs.wallRowCount >= MIN_ALL_WALL_ROWS &&
                inputs.nonPersonalWallRowCount >= MIN_ALL_NON_PERSONAL_WALL_ROWS
        VodCatalogOnboardingTab.MOVIES ->
            (inputs.pagedItemCount >= MIN_PAGED_ITEMS_WITH_CATEGORY && inputs.categoryCount >= 1) ||
                inputs.browseRowCount >= MIN_BROWSE_ROWS ||
                (progress.moviesPhaseFinished && inputs.pagedItemCount >= MIN_PAGED_ITEMS_PHASE_COMPLETE)
        VodCatalogOnboardingTab.SERIES ->
            (inputs.pagedItemCount >= MIN_PAGED_ITEMS_WITH_CATEGORY && inputs.categoryCount >= 1) ||
                inputs.browseRowCount >= MIN_BROWSE_ROWS ||
                (progress.seriesPhaseFinished && inputs.pagedItemCount >= MIN_PAGED_ITEMS_PHASE_COMPLETE)
    }
}

private fun isVodCatalogPipelineStillRunning(
    tab: VodCatalogOnboardingTab,
    catalogLoading: Boolean,
    progress: VodCatalogProgress
): Boolean {
    if (catalogLoading || progress.isLoading) return true
    return when (tab) {
        VodCatalogOnboardingTab.ALL ->
            !progress.moviesPhaseFinished ||
                (progress.isMoviesPhaseComplete && !progress.seriesPhaseFinished)
        VodCatalogOnboardingTab.MOVIES ->
            !progress.moviesPhaseFinished
        VodCatalogOnboardingTab.SERIES ->
            !progress.moviesPhaseFinished || !progress.seriesPhaseFinished
    }
}

/**
 * Presentation-only gate for the first-time / in-progress VOD catalog build.
 * Uses existing [VodCatalogProgress] and row/category counts — does not trigger loads.
 */
fun shouldShowVodCatalogOnboarding(inputs: VodCatalogOnboardingInputs): Boolean {
    if (isVodCatalogPipelineComplete(inputs.tab, inputs.progress)) return false
    if (isVodCatalogContentReady(inputs)) return false
    return isVodCatalogPipelineStillRunning(inputs.tab, inputs.catalogLoading, inputs.progress)
}

/** @see shouldShowVodCatalogOnboarding */
fun shouldShowVodCatalogOnboarding(
    catalogLoading: Boolean,
    progress: VodCatalogProgress,
    browseRowCount: Int,
    categoryCount: Int,
    wallRowCount: Int = 0,
    pagedItemCount: Int = 0,
    requiresSeriesPhase: Boolean = true,
    tab: VodCatalogOnboardingTab = VodCatalogOnboardingTab.ALL,
    nonPersonalWallRowCount: Int = 0,
): Boolean = shouldShowVodCatalogOnboarding(
    VodCatalogOnboardingInputs(
        catalogLoading = catalogLoading,
        progress = progress,
        tab = tab,
        browseRowCount = browseRowCount,
        categoryCount = categoryCount,
        wallRowCount = wallRowCount,
        nonPersonalWallRowCount = nonPersonalWallRowCount,
        pagedItemCount = pagedItemCount
    )
)

/**
 * Debounces onboarding dismissal so brief row-count flicker during browse-row assembly
 * does not flash the main catalog UI.
 */
@Composable
fun rememberVodCatalogOnboardingVisible(
    inputs: VodCatalogOnboardingInputs,
    stabilizationMs: Long = READINESS_STABILIZATION_MS
): Boolean {
    val shouldShow = shouldShowVodCatalogOnboarding(inputs)
    var visible by remember { mutableStateOf(shouldShow) }
    LaunchedEffect(shouldShow, stabilizationMs) {
        if (shouldShow) {
            visible = true
        } else {
            delay(stabilizationMs)
            visible = false
        }
    }
    return visible
}

@Composable
fun VodCatalogOnboardingPanel(
    modifier: Modifier = Modifier,
    progress: VodCatalogProgress? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VodNetflixColors.Background)
            .padding(horizontal = 40.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = VodNetflixColors.Accent,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Getting your movies and series ready",
            color = VodNetflixColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "We're organizing your content and building your library. This may take a few minutes the first time.",
            color = VodNetflixColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        progress?.let { p ->
            val statusLine = buildOnboardingStatusLine(p)
            if (statusLine != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusLine,
                    color = Color.White.copy(alpha = 0.45f),
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun buildOnboardingStatusLine(progress: VodCatalogProgress): String? {
    val parts = buildList {
        when {
            progress.moviesTotal > 0 -> add(
                "Movies: ${formatVodCatalogCount(progress.moviesLoaded)} / ${formatVodCatalogCount(progress.moviesTotal)}"
            )
            progress.moviesLoaded > 0 -> add("Movies: ${formatVodCatalogCount(progress.moviesLoaded)} loaded")
        }
        if (progress.isMoviesPhaseComplete) {
            when {
                progress.seriesTotal > 0 -> add(
                    "Series: ${formatVodCatalogCount(progress.seriesLoaded)} / ${formatVodCatalogCount(progress.seriesTotal)}"
                )
                progress.seriesLoaded > 0 -> add("Series: ${formatVodCatalogCount(progress.seriesLoaded)} loaded")
            }
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}
