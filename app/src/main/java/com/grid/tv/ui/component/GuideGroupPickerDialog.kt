package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

@Composable
fun GuideGroupPickerDialog(
    channelGroups: List<String>,
    initialSelection: Set<String>,
    title: String = "Choose your channels",
    subtitle: String = "Pick the groups you want in the live guide. You can change this later in Settings.",
    confirmLabel: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var focusIndex by remember { mutableIntStateOf(0) }
    var selection by remember { mutableStateOf(initialSelection) }
    val listState = rememberLazyListState()
    val saveFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    val menuCount = guideFilterMenuItemCount(channelGroups)
    val actionIndexSave = menuCount
    val actionIndexCancel = menuCount + 1

    LaunchedEffect(focusIndex) {
        if (focusIndex < menuCount) {
            val lazyIndex = focusIndex.coerceIn(0, channelGroups.size)
            listState.animateScrollToItem(lazyIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EpgColors.Background.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .background(EpgColors.DetailPanelBg, RoundedCornerShape(12.dp))
                    .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(24.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> {
                                focusIndex = (focusIndex + 1).coerceAtMost(actionIndexCancel)
                                true
                            }
                            Key.DirectionUp -> {
                                focusIndex = (focusIndex - 1).coerceAtLeast(0)
                                true
                            }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                when (focusIndex) {
                                    in 0 until menuCount -> {
                                        selection = guideFilterForMenuSelection(
                                            channelGroups,
                                            selection,
                                            focusIndex
                                        ).selectedGroups
                                    }
                                    actionIndexSave -> onConfirm(selection)
                                    actionIndexCancel -> onDismiss()
                                }
                                true
                            }
                            Key.Back, Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                Text(
                    text = title,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    item {
                        GuideGroupPickerRow(
                            label = "All channels",
                            checked = selection.isEmpty(),
                            focused = focusIndex == 0,
                            onClick = {
                                selection = emptySet()
                                focusIndex = 0
                            }
                        )
                    }
                    itemsIndexed(channelGroups, key = { _, name -> name }) { index, group ->
                        val menuIndex = index + 1
                        GuideGroupPickerRow(
                            label = group,
                            checked = group in selection,
                            focused = focusIndex == menuIndex,
                            onClick = {
                                selection = guideFilterForMenuSelection(
                                    channelGroups,
                                    selection,
                                    menuIndex
                                ).selectedGroups
                                focusIndex = menuIndex
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    GridOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier
                            .focusRequester(cancelFocusRequester)
                            .focusable()
                            .then(
                                if (focusIndex == actionIndexCancel) {
                                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            )
                    )
                    GridPrimaryButton(
                        text = confirmLabel,
                        onClick = { onConfirm(selection) },
                        modifier = Modifier
                            .focusRequester(saveFocusRequester)
                            .focusable()
                            .then(
                                if (focusIndex == actionIndexSave) {
                                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideGroupPickerRow(
    label: String,
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    GridFocusSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(
                if (focused) Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (focused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg,
            focusedContainerColor = if (focused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg
        )
    ) {
        Text(
            text = buildString {
                if (checked) append("✓ ")
                append(label)
            },
            color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
