package com.neuropulse.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

@Composable
fun PlayerSideMenu(
    visible: Boolean,
    channels: List<Channel>,
    currentChannelId: Long?,
    focusedChannelIndex: Int,
    focusedActionIndex: Int,
    onDismiss: () -> Unit,
    onChannelSelected: (Channel) -> Unit,
    onGuide: () -> Unit,
    onRecordings: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val actions = listOf("TV Guide", "Recordings", "Settings")

    LaunchedEffect(focusedChannelIndex, visible) {
        if (visible && focusedActionIndex < 0 && focusedChannelIndex in channels.indices) {
            listState.animateScrollToItem(focusedChannelIndex)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier.fillMaxSize()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(EpgColors.Background.copy(alpha = 0.45f))
            )
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(EpgColors.DetailPanelBg)
                    .border(width = 1.dp, color = EpgColors.BorderSubtle)
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "Channels",
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(channels) { index, channel ->
                        val focused = focusedActionIndex < 0 && index == focusedChannelIndex
                        val selected = channel.id == currentChannelId
                        val bg = when {
                            focused -> EpgColors.ChannelRowFocusBg
                            selected -> EpgColors.Accent.copy(alpha = 0.15f)
                            else -> EpgColors.GridBg
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .background(bg, RoundedCornerShape(6.dp))
                                .then(
                                    if (focused) {
                                        Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = channel.number.toString(),
                                color = EpgColors.TextSecondary,
                                fontFamily = DmSansFamily,
                                fontSize = 12.sp,
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                text = channel.name,
                                color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                                fontFamily = DmSansFamily,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Text(
                                    text = "●",
                                    color = EpgColors.Accent,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Quick actions",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                actions.forEachIndexed { index, label ->
                    val focused = focusedActionIndex == index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .background(
                                if (focused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg,
                                RoundedCornerShape(6.dp)
                            )
                            .then(
                                if (focused) {
                                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp
                        )
                    }
                }
                Text(
                    text = "← Close menu",
                    color = EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }
        }
    }
}
