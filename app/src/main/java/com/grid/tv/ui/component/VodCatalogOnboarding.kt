package com.grid.tv.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

enum class VodOnboardingStep(val label: String) {
    PREPARING("Preparing your library"),
    SCANNING("Scanning playlists"),
    ORGANIZING_MOVIES("Organizing movies"),
    ORGANIZING_SERIES("Organizing series"),
    BUILDING_RECOMMENDATIONS("Building recommendations"),
    FINALIZING("Finalizing")
}

data class VodCatalogOnboardingInputs(
    val catalogLoading: Boolean,
    val progress: VodCatalogProgress,
    val tab: VodCatalogOnboardingTab,
    val browseRowCount: Int,
    val categoryCount: Int,
    val wallRowCount: Int = 0,
    val nonPersonalWallRowCount: Int = 0,
    val wallItemCount: Int = 0,
    val pagedItemCount: Int = 0,
    val catalogTotalCount: Int = 0,
)

private const val MIN_ALL_WALL_ROWS = 1
private const val MIN_ALL_WALL_ITEMS = 1
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
                inputs.wallItemCount >= MIN_ALL_WALL_ITEMS
        VodCatalogOnboardingTab.MOVIES ->
            inputs.pagedItemCount >= MIN_PAGED_ITEMS_WITH_CATEGORY && inputs.categoryCount >= 1 ||
                inputs.browseRowCount >= MIN_BROWSE_ROWS ||
                (progress.moviesPhaseFinished && inputs.pagedItemCount >= MIN_PAGED_ITEMS_PHASE_COMPLETE)
        VodCatalogOnboardingTab.SERIES ->
            inputs.pagedItemCount >= MIN_PAGED_ITEMS_WITH_CATEGORY && inputs.categoryCount >= 1 ||
                inputs.browseRowCount >= MIN_BROWSE_ROWS ||
                (progress.seriesPhaseFinished && inputs.pagedItemCount >= MIN_PAGED_ITEMS_PHASE_COMPLETE)
    }
}

/** Best-known catalog size — UI counts can lag behind ingest progress. */
fun VodCatalogOnboardingInputs.effectiveCatalogCount(): Int {
    val progressCount = when (tab) {
        VodCatalogOnboardingTab.ALL -> progress.moviesLoaded + progress.seriesLoaded
        VodCatalogOnboardingTab.MOVIES -> progress.moviesLoaded
        VodCatalogOnboardingTab.SERIES -> progress.seriesLoaded
    }
    return maxOf(catalogTotalCount, progressCount)
}

fun resolveVodOnboardingStep(inputs: VodCatalogOnboardingInputs): VodOnboardingStep {
    val progress = inputs.progress
    return when {
        inputs.catalogLoading && progress.moviesLoaded == 0 && progress.seriesLoaded == 0 ->
            VodOnboardingStep.PREPARING
        progress.isLoading && !progress.moviesPhaseFinished && progress.moviesLoaded == 0 ->
            VodOnboardingStep.SCANNING
        !progress.moviesPhaseFinished ->
            VodOnboardingStep.ORGANIZING_MOVIES
        !progress.seriesPhaseFinished ->
            VodOnboardingStep.ORGANIZING_SERIES
        inputs.effectiveCatalogCount() > 0 && !isVodCatalogContentReady(inputs) ->
            VodOnboardingStep.BUILDING_RECOMMENDATIONS
        else -> VodOnboardingStep.FINALIZING
    }
}

fun onboardingStepProgressFraction(step: VodOnboardingStep, inputs: VodCatalogOnboardingInputs): Float {
    val progress = inputs.progress
    return when (step) {
        VodOnboardingStep.PREPARING -> 0.05f
        VodOnboardingStep.SCANNING -> 0.15f
        VodOnboardingStep.ORGANIZING_MOVIES -> {
            val movieFrac = progress.moviesProgressFraction().takeIf { it > 0f } ?: 0.35f
            0.2f + movieFrac * 0.25f
        }
        VodOnboardingStep.ORGANIZING_SERIES -> {
            val seriesFrac = progress.seriesProgressFraction().takeIf { it > 0f } ?: 0.35f
            0.5f + seriesFrac * 0.25f
        }
        VodOnboardingStep.BUILDING_RECOMMENDATIONS -> 0.85f
        VodOnboardingStep.FINALIZING -> 0.95f
    }.coerceIn(0f, 1f)
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

private fun isBuildingRecommendationsPhase(inputs: VodCatalogOnboardingInputs): Boolean {
    if (!isVodCatalogPipelineComplete(inputs.tab, inputs.progress)) return false
    if (inputs.catalogLoading || inputs.progress.isLoading) return false
    if (inputs.effectiveCatalogCount() <= 0) return false
    return !isVodCatalogContentReady(inputs)
}

/**
 * True when the catalog is genuinely empty after a successful sync — not while loading.
 */
fun shouldShowVodCatalogEmptyState(
    catalogLoading: Boolean,
    progress: VodCatalogProgress,
    tab: VodCatalogOnboardingTab,
    catalogTotalCount: Int,
    hasContinueWatching: Boolean
): Boolean {
    if (catalogLoading || progress.isLoading) return false
    if (!isVodCatalogPipelineComplete(tab, progress)) return false
    if (hasContinueWatching) return false
    return catalogTotalCount == 0
}

/**
 * Presentation-only gate for the first-time / in-progress VOD catalog build.
 * Uses existing [VodCatalogProgress] and row/category counts — does not trigger loads.
 */
fun shouldShowVodCatalogOnboarding(inputs: VodCatalogOnboardingInputs): Boolean {
    if (isVodCatalogContentReady(inputs)) return false
    if (isVodCatalogPipelineStillRunning(inputs.tab, inputs.catalogLoading, inputs.progress)) return true
    if (isBuildingRecommendationsPhase(inputs)) return true
    return false
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
    catalogTotalCount: Int = 0,
): Boolean = shouldShowVodCatalogOnboarding(
    VodCatalogOnboardingInputs(
        catalogLoading = catalogLoading,
        progress = progress,
        tab = tab,
        browseRowCount = browseRowCount,
        categoryCount = categoryCount,
        wallRowCount = wallRowCount,
        nonPersonalWallRowCount = nonPersonalWallRowCount,
        pagedItemCount = pagedItemCount,
        catalogTotalCount = catalogTotalCount
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
    progress: VodCatalogProgress? = null,
    onboardingInputs: VodCatalogOnboardingInputs? = null
) {
    val inputs = onboardingInputs ?: VodCatalogOnboardingInputs(
        catalogLoading = progress?.isLoading == true,
        progress = progress ?: VodCatalogProgress(),
        tab = VodCatalogOnboardingTab.ALL,
        browseRowCount = 0,
        categoryCount = 0
    )
    val currentStep = resolveVodOnboardingStep(inputs)
    val targetFraction = onboardingStepProgressFraction(currentStep, inputs)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 350),
        label = "vodOnboardingProgress"
    )
    var displayedStepIndex by remember { mutableIntStateOf(currentStep.ordinal) }
    LaunchedEffect(currentStep) {
        if (currentStep.ordinal >= displayedStepIndex) {
            displayedStepIndex = currentStep.ordinal
        }
    }

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
        Spacer(modifier = Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(4.dp),
            color = VodNetflixColors.Accent,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VodOnboardingStep.entries.forEachIndexed { index, step ->
                val reached = index <= displayedStepIndex
                val active = step == currentStep
                OnboardingStepRow(
                    label = step.label,
                    reached = reached,
                    active = active
                )
            }
        }
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

@Composable
private fun OnboardingStepRow(
    label: String,
    reached: Boolean,
    active: Boolean
) {
    val alpha = when {
        active -> 1f
        reached -> 0.65f
        else -> 0.28f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .background(
                if (active) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (reached) "●" else "○",
            color = if (active) VodNetflixColors.Accent else VodNetflixColors.TextSecondary,
            fontSize = 12.sp
        )
        Text(
            text = label,
            color = if (active) VodNetflixColors.TextPrimary else VodNetflixColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
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
