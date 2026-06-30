package com.grid.tv.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.UiMotion

@Composable
fun rememberShimmerAlpha(
    label: String = "shimmer",
    minAlpha: Float = 0.28f,
    maxAlpha: Float = 0.55f,
): Float {
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(animation = tween(UiMotion.ShimmerDurationMs), repeatMode = RepeatMode.Reverse),
        label = label,
    ).value
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1E1E28),
    shimmerAlpha: Float = rememberShimmerAlpha(),
) {
    Box(
        modifier = modifier.background(color.copy(alpha = shimmerAlpha))
    )
}

@Composable
fun SearchResultSkeletonRow(
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberShimmerAlpha(label = "searchRow")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShimmerBox(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(4.dp)),
            shimmerAlpha = shimmer,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp)),
                shimmerAlpha = shimmer,
            )
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp)),
                shimmerAlpha = shimmer,
            )
        }
    }
}

@Composable
fun EpgGuideSkeletonChannelList(
    rowCount: Int = 8,
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberShimmerAlpha(label = "epgGuide")
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(rowCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .width(140.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    shimmerAlpha = shimmer,
                )
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    shimmerAlpha = shimmer,
                )
            }
        }
    }
}

@Composable
fun ChannelBrowserSkeletonGrid(
    cellCount: Int = 12,
    columns: Int = 4,
    cellHeight: Dp = 160.dp,
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberShimmerAlpha(label = "channelBrowser")
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(cellCount, key = { "browser-sk-$it" }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cellHeight)
                        .clip(RoundedCornerShape(8.dp)),
                    shimmerAlpha = shimmer,
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    shimmerAlpha = shimmer,
                )
            }
        }
    }
}

@Composable
fun VodSearchSkeletonRow(
    posterCount: Int = 6,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        userScrollEnabled = false,
    ) {
        items(posterCount, key = { "vod-search-sk-$it" }) {
            VodPosterSkeleton()
        }
    }
}

@Composable
fun HomeScreenSkeletonOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EpgColors.Background),
    ) {
        EpgGuideSkeletonChannelList(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
        )
    }
}
