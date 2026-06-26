package com.grid.tv.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.TvImagePipeline
import com.grid.tv.util.TvImageSizing
import kotlinx.coroutines.delay

/**
 * Full-width TMDB backdrop with crossfade — sits behind VOD hub hero.
 * Falls back to [VodAmbientBackdrop] color extraction when no backdrop URL exists.
 */
@Composable
fun VodHeroBackdrop(
    backdropUrl: String?,
    posterUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (w, h) = TvImageSizing.vodBackdropSize(context)
    var displayedUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(backdropUrl) {
        if (backdropUrl.isNullOrBlank()) {
            displayedUrl = null
            return@LaunchedEffect
        }
        delay(50)
        displayedUrl = backdropUrl
        TvImagePipeline.prefetch(context, listOf(backdropUrl), w, h)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (displayedUrl.isNullOrBlank()) {
            VodAmbientBackdrop(posterUrl = posterUrl, modifier = Modifier.fillMaxSize())
        } else {
            Crossfade(
                targetState = displayedUrl,
                animationSpec = tween(600),
                label = "heroBackdrop"
            ) { url ->
                if (url.isNullOrBlank()) {
                    VodAmbientBackdrop(posterUrl = posterUrl, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize()) {
                        val cached = remember(url) {
                            TvImagePipeline.peekCached(context, url, w, h)
                        }
                        TvPosterImage(
                            url = url,
                            contentDescription = null,
                            kind = PosterImageKind.Backdrop,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            EpgColors.Background.copy(alpha = 0.35f),
                                            EpgColors.Background.copy(alpha = 0.88f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
