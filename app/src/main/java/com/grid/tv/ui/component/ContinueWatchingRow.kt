package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.component.GridFocusSurface
import androidx.tv.material3.Text
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.formatContinueWatchingRemaining
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.TvImageSizing

@Composable
fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    focusedIndex: Int,
    rowFocused: Boolean,
    onSelect: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Continue Watching",
            color = Color(0xFFD4A574),
            fontFamily = DmSansFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(items, key = { _, item -> item.contentKey }) { index, item ->
                ContinueWatchingCard(
                    item = item,
                    isFocused = rowFocused && index == focusedIndex,
                    onClick = { onSelect(item) }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bg = if (isFocused) EpgColors.ChannelRowFocusBg else Color(0xFF1A1A22)
    GridFocusSurface(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .height(210.dp)
            .tvFocusBorder(focused = isFocused, shape = RoundedCornerShape(8.dp)),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2A35)),
                contentAlignment = Alignment.Center
            ) {
                if (item.posterUrl != null) {
                    TvPosterImage(
                        url = item.posterUrl,
                        contentDescription = item.title,
                        kind = PosterImageKind.ContinueWatching,
                        placeholderLetter = item.title,
                        modifier = Modifier.size(120.dp).clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Text(
                        text = item.title.take(2).uppercase(),
                        color = EpgColors.TextSecondary,
                        fontSize = 20.sp
                    )
                }
            }
            Text(
                text = item.title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (item.contentType == ContinueWatchingContentType.SERIES && item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 10.sp
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.progressFraction)
                        .height(4.dp)
                        .background(EpgColors.Accent)
                )
            }
            Text(
                text = formatContinueWatchingRemaining(item.remainingMs),
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

