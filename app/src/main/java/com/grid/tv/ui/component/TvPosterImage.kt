package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.VodNetflixColors
import com.grid.tv.util.TvImageSizing

enum class PosterImageKind {
    VodGrid,
    VodLandscape,
    ContinueWatching,
    ChannelLogo,
    Backdrop,
    Custom
}

/**
 * Visible-item poster loader — Coil cancels the request when this composable leaves composition.
 * Uses decode-size-limited thumbnails on low-end devices with placeholders to avoid scroll jank.
 */
@Composable
fun TvPosterImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    kind: PosterImageKind = PosterImageKind.VodGrid,
    widthPx: Int? = null,
    heightPx: Int? = null,
    placeholderLetter: String? = null,
    placeholderFontSize: TextUnit = 18.sp,
    placeholderColor: Color = VodNetflixColors.CardPlaceholder,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val letter = placeholderLetter?.take(2)?.uppercase().orEmpty()
    if (url.isNullOrBlank()) {
        PosterPlaceholder(
            letter = letter,
            modifier = modifier,
            background = placeholderColor,
            fontSize = placeholderFontSize
        )
        return
    }

    val (targetW, targetH) = when {
        widthPx != null && heightPx != null -> widthPx to heightPx
        kind == PosterImageKind.VodGrid -> TvImageSizing.vodPosterSize(context)
        kind == PosterImageKind.VodLandscape -> TvImageSizing.vodLandscapeSize(context)
        kind == PosterImageKind.ContinueWatching -> {
            val px = TvImageSizing.continueWatchingPosterPx(context)
            px to px
        }
        kind == PosterImageKind.ChannelLogo -> {
            val px = TvImageSizing.channelLogoPx(context)
            px to px
        }
        kind == PosterImageKind.Backdrop -> TvImageSizing.vodBackdropSize(context)
        else -> TvImageSizing.vodPosterSize(context)
    }

    val request = TvImageSizing.sizedRequest(
        context = context,
        data = TvImageSizing.posterThumbnailUrl(url, context),
        widthPx = targetW,
        heightPx = targetH
    )

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            PosterPlaceholder(
                letter = letter,
                modifier = Modifier.fillMaxSize(),
                background = placeholderColor,
                fontSize = placeholderFontSize
            )
        },
        error = {
            PosterPlaceholder(
                letter = letter,
                modifier = Modifier.fillMaxSize(),
                background = placeholderColor,
                fontSize = placeholderFontSize
            )
        }
    )
}

@Composable
fun TvPosterImage(
    request: ImageRequest,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderLetter: String? = null,
    placeholderFontSize: TextUnit = 18.sp,
    placeholderColor: Color = VodNetflixColors.CardPlaceholder,
    contentScale: ContentScale = ContentScale.Crop
) {
    val letter = placeholderLetter?.take(2)?.uppercase().orEmpty()
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            PosterPlaceholder(
                letter = letter,
                modifier = Modifier.fillMaxSize(),
                background = placeholderColor,
                fontSize = placeholderFontSize
            )
        },
        error = {
            PosterPlaceholder(
                letter = letter,
                modifier = Modifier.fillMaxSize(),
                background = placeholderColor,
                fontSize = placeholderFontSize
            )
        }
    )
}

@Composable
private fun PosterPlaceholder(
    letter: String,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF1A1A22),
    fontSize: TextUnit = 18.sp
) {
    Box(
        modifier = modifier.background(background),
        contentAlignment = Alignment.Center
    ) {
        if (letter.isNotBlank()) {
            Text(
                text = letter,
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
