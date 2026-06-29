package com.grid.tv.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.domain.model.ChannelGroupIdentity
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

@Composable
fun GuideGroupFavoriteMenuDialog(
    groupKey: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryLabel = if (isFavorite) "Remove from Favourites" else "Add to Favourites"
    val actionFocusRequesters = remember { List(2) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        actionFocusRequesters[0].requestFocusSafelyAfterLayout()
    }

    GridModal(
        onDismiss = onDismiss,
        showCloseButton = false,
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionDown -> {
                    if (focusedIndex < 1) {
                        focusedIndex += 1
                        actionFocusRequesters[focusedIndex].requestFocusSafely()
                    }
                    true
                }
                Key.DirectionUp -> {
                    if (focusedIndex > 0) {
                        focusedIndex -= 1
                        actionFocusRequesters[focusedIndex].requestFocusSafely()
                    }
                    true
                }
                else -> false
            }
        },
        width = 420.dp
    ) {
        Text(
            text = ChannelGroupIdentity.displayLabel(groupKey),
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Channel group",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            GuideGroupFavoriteMenuAction(
                label = primaryLabel,
                focusRequester = actionFocusRequesters[0],
                onFocused = { focusedIndex = 0 },
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                }
            )
            GuideGroupFavoriteMenuAction(
                label = "Cancel",
                focusRequester = actionFocusRequesters[1],
                onFocused = { focusedIndex = 1 },
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun GuideGroupFavoriteMenuAction(
    label: String,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rowFocused by remember { androidx.compose.runtime.mutableStateOf(false) }
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                rowFocused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (rowFocused) EpgColors.LiveGuideFocusBg else EpgColors.GridBg,
            focusedContainerColor = EpgColors.LiveGuideFocusBg
        )
    ) {
        Text(
            text = label,
            color = if (rowFocused) EpgColors.LiveGuideFocus else EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            fontWeight = if (rowFocused) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
