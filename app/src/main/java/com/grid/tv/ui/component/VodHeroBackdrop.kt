package com.grid.tv.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.VodNetflixColors
import com.grid.tv.util.TvImagePipeline
import com.grid.tv.util.TvImageSizing

/**
 * Full-width TMDB backdrop with non-blocking placeholder — sits behind VOD hub hero.
 * Renders a dark gradient immediately; high-res image fades in when ready.
 */
@Composable
fun VodHeroBackdrop(
    backdropUrl: String?,
    posterUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (w, h) = TvImageSizing.vodBackdropSize(context)
    var imageReady by remember(backdropUrl) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (imageReady) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "heroBackdropAlpha"
    )

    LaunchedEffect(backdropUrl) {
        imageReady = false
        if (backdropUrl.isNullOrBlank()) return@LaunchedEffect
        TvImagePipeline.prefetch(context, listOf(backdropUrl), w, h)
        imageReady = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        VodAmbientBackdrop(posterUrl = posterUrl, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF101018),
                            Color(0xFF0A0A10),
                            EpgColors.Background.copy(alpha = 0.92f)
                        )
                    )
                )
        )
        if (!backdropUrl.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize().alpha(imageAlpha)) {
                TvPosterImage(
                    url = backdropUrl,
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

/** Placeholder hero shell so wall rows can render while featured metadata resolves. */
@Composable
fun VodHeroSkeleton(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1C1C28),
                        Color(0xFF12121A),
                        VodNetflixColors.Background
                    )
                ),
                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .padding(start = 56.dp, bottom = 24.dp)
                .fillMaxWidth(0.42f)
                .height(28.dp)
                .align(androidx.compose.ui.Alignment.BottomStart)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
        )
        Box(
            modifier = Modifier
                .padding(start = 56.dp, bottom = 64.dp)
                .fillMaxWidth(0.55f)
                .height(18.dp)
                .align(androidx.compose.ui.Alignment.BottomStart)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
        )
    }
}
