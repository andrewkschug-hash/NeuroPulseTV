package com.grid.tv.ui.component

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.grid.tv.util.TvImageSizing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DefaultCenter = Color(0xFF1A1A2E)
private val EdgeColor = Color(0xFF000000)

@Composable
fun VodAmbientBackdrop(
    posterUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var extractedCenter by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(posterUrl) {
        extractedCenter = if (posterUrl.isNullOrBlank()) {
            null
        } else {
            VodPosterColorExtractor.extract(context, posterUrl)
        }
    }

    val centerColor by animateColorAsState(
        targetValue = extractedCenter ?: DefaultCenter,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "vodAmbientCenter"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(centerColor, EdgeColor),
                    radius = 1400f
                )
            )
    )
}

internal object VodPosterColorExtractor {
    suspend fun extract(context: android.content.Context, imageUrl: String): Color? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(TvImageSizing.posterThumbnailUrl(imageUrl, context))
                    .size(48, 72)
                    .allowRgb565(true)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result !is SuccessResult) return@runCatching null
                val drawable = result.drawable
                val bitmap = Bitmap.createBitmap(24, 36, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(canvas)
                dominantColor(bitmap)?.forAmbientBackdrop()
            }.getOrNull()
        }

    private fun dominantColor(bitmap: Bitmap): Color? {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0
        val stepX = (bitmap.width / 6).coerceAtLeast(1)
        val stepY = (bitmap.height / 6).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(pixel)
                if (alpha < 40) {
                    x += stepX
                    continue
                }
                red += android.graphics.Color.red(pixel)
                green += android.graphics.Color.green(pixel)
                blue += android.graphics.Color.blue(pixel)
                count++
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return null
        return Color(
            red = (red / count).toInt(),
            green = (green / count).toInt(),
            blue = (blue / count).toInt()
        )
    }

    private fun Color.forAmbientBackdrop(): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        hsv[1] = hsv[1].coerceIn(0.25f, 0.75f)
        hsv[2] = hsv[2].coerceIn(0.04f, 0.15f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
