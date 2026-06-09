package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neuropulse.tv.domain.model.ContinueWatchingItem
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

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
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(items, key = { _, item -> item.channel.id }) { index, item ->
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
    val title = item.programTitle ?: item.channel.name
    val bg = if (isFocused) EpgColors.ChannelRowFocusBg else Color(0xFF1A1A22)
    val borderMod = if (isFocused) {
        Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
    } else {
        Modifier
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(64.dp)
            .then(borderMod),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = EpgColors.ChannelRowFocusBg
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item.channel.logoUrl != null) {
                AsyncImage(
                    model = item.channel.logoUrl,
                    contentDescription = item.channel.name,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2A2A35)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.channel.name.take(2).uppercase(), color = EpgColors.TextSecondary, fontSize = 12.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.channel.name,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("▶", color = EpgColors.Accent, fontSize = 16.sp)
        }
    }
}
