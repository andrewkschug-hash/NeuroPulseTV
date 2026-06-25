package com.grid.tv.feature.vod

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.domain.model.VodItem
import com.grid.tv.ui.component.VodAmbientBackdrop
import com.grid.tv.ui.component.VodHeroSection
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.viewmodel.VodHubViewModel
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.VodPerfLogger

/** Handles hero carousel keys without reading [heroIndex] in the parent screen. */
fun handleVodHubHeroKeyEvent(
    event: KeyEvent,
    carouselSize: Int,
    onStepCarousel: (delta: Int) -> Unit,
    onNavigateDown: () -> Unit,
    onNavigateUp: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
    return when (event.key) {
        Key.DirectionLeft -> {
            if (carouselSize > 1) onStepCarousel(-1)
            true
        }
        Key.DirectionRight -> {
            if (carouselSize > 1) onStepCarousel(1)
            true
        }
        Key.DirectionDown -> {
            onNavigateDown()
            true
        }
        Key.DirectionUp -> {
            onNavigateUp()
            true
        }
        else -> false
    }
}

/**
 * Volatile hero island — collects [VodHubViewModel.heroIndex] locally so carousel
 * changes do not recompose the VOD wall.
 */
@Composable
fun VodHubHeroIsland(
    hubViewModel: VodHubViewModel,
    featuredCarousel: List<VodItem>,
    enrichmentMap: Map<String, TitleEnrichmentEntity>,
    inputActive: Boolean,
    requestPlayFocus: Boolean,
    onPlay: (VodItem) -> Unit,
    onMoreInfo: (VodItem) -> Unit,
    onNavigateDown: () -> Unit,
    onNavigateUp: () -> Unit,
    playFocusRequester: FocusRequester,
    moreInfoFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val heroIndex by hubViewModel.heroIndex.collectAsStateWithLifecycle()
    val heroMovie = remember(featuredCarousel, heroIndex) {
        VodHubHeroResolver.itemAt(featuredCarousel, heroIndex)
    }
    val heroEnrichment = remember(heroMovie, enrichmentMap) {
        VodHubHeroResolver.enrichmentFor(heroMovie, enrichmentMap)
    }

    LaunchedEffect(requestPlayFocus, heroMovie?.streamId) {
        if (requestPlayFocus && heroMovie != null) {
            playFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    SideEffect {
        VodPerfLogger.logEmission("VodHubHeroIsland", "index=$heroIndex carousel=${featuredCarousel.size}")
    }

    val hero = heroMovie ?: return

    Box(
        modifier = modifier.onPreviewKeyEvent { event ->
            inputActive && handleVodHubHeroKeyEvent(
                event = event,
                carouselSize = featuredCarousel.size,
                onStepCarousel = hubViewModel::stepHeroCarousel,
                onNavigateDown = onNavigateDown,
                onNavigateUp = onNavigateUp
            )
        }
    ) {
        VodHeroSection(
            movie = hero,
            enrichment = heroEnrichment,
            carouselSize = featuredCarousel.size,
            carouselIndex = heroIndex,
            onPlay = { onPlay(hero) },
            onMoreInfo = { onMoreInfo(hero) },
            playFocusRequester = playFocusRequester,
            moreInfoFocusRequester = moreInfoFocusRequester
        )
    }
}

/** Ambient poster driven by hero carousel index — isolated from wall recomposition. */
@Composable
fun VodHubHeroAmbientPoster(
    hubViewModel: VodHubViewModel,
    featuredCarousel: List<VodItem>,
    posterUrlFor: (VodItem) -> String?,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (featuredCarousel.isEmpty()) return
    val heroIndex by hubViewModel.heroIndex.collectAsStateWithLifecycle()
    val posterUrl = remember(featuredCarousel, heroIndex) {
        VodHubHeroResolver.itemAt(featuredCarousel, heroIndex)?.let(posterUrlFor)
    }
    VodAmbientBackdrop(posterUrl = posterUrl, modifier = modifier)
}
