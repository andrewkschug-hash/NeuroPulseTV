package com.grid.tv.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.feature.vod.displayLanguageName
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.VodNetflixColors

private val DialogBg = Color(0xFF1A1A2E)
private val DialogBody = Color(0xFFB0B0C0)
private val RowShape = RoundedCornerShape(10.dp)

@Composable
fun VodLanguagePreferenceDialog(
    availableLanguages: List<String>,
    selectedLanguages: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(selectedLanguages) { mutableStateOf(selectedLanguages) }
    val rowCount = availableLanguages.size + 1
    var focusedRowIndex by remember { mutableIntStateOf(0) }
    val rowFocusRequesters = remember(rowCount) { List(rowCount) { FocusRequester() } }
    val cancelFocusRequester = remember { FocusRequester() }
    val doneFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (rowCount > 0) {
            rowFocusRequesters[0].requestFocusSafelyAfterLayout()
        }
    }

    LaunchedEffect(focusedRowIndex, rowCount) {
        if (rowCount > 0) {
            val index = focusedRowIndex.coerceIn(0, rowCount - 1)
            listState.scrollToItem(index)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Content Languages",
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Show movies and series matching your selected languages. Untagged titles are always shown.",
                    color = DialogBody,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "all_languages") {
                        LanguageOptionRow(
                            label = "All Languages",
                            subtitle = "Show everything in your catalog",
                            selected = draft.isEmpty(),
                            onClick = { draft = emptySet() },
                            modifier = Modifier
                                .focusRequester(rowFocusRequesters[0])
                                .onFocusChanged {
                                    if (it.isFocused) focusedRowIndex = 0
                                }
                        )
                    }
                    if (availableLanguages.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = "No language tags found in your catalog yet.",
                                color = DialogBody,
                                fontFamily = DmSansFamily,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        itemsIndexed(availableLanguages, key = { _, code -> code }) { index, code ->
                            val rowIndex = index + 1
                            val selected = code.uppercase() in draft.map { it.uppercase() }.toSet()
                            LanguageOptionRow(
                                label = displayLanguageName(code),
                                subtitle = code.uppercase(),
                                selected = selected,
                                onClick = {
                                    draft = if (selected) {
                                        draft.filterNot { it.equals(code, ignoreCase = true) }.toSet()
                                    } else {
                                        draft + code.uppercase()
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(rowFocusRequesters[rowIndex])
                                    .onFocusChanged {
                                        if (it.isFocused) focusedRowIndex = rowIndex
                                    }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            GlowFocusButton(
                onClick = {
                    onApply(draft)
                    onDismiss()
                },
                modifier = Modifier.focusRequester(doneFocusRequester)
            ) {
                Text(
                    text = "Done",
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            GlowFocusButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancelFocusRequester)
            ) {
                Text(
                    text = "Cancel",
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp
                )
            }
        }
    )
}

@Composable
private fun LanguageOptionRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rowFocused by remember { mutableStateOf(false) }
    val backgroundColor = when {
        rowFocused -> EpgColors.ChannelRowFocusBg
        selected -> Color(0xFF2A2A2A)
        else -> Color(0xFF141414)
    }
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .onFocusChanged { rowFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = backgroundColor,
            pressedContainerColor = backgroundColor.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (rowFocused) {
                        Modifier.border(2.dp, EpgColors.FocusBorder, RowShape)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = if (rowFocused) EpgColors.TextPrimary else VodNetflixColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    fontWeight = if (rowFocused || selected) FontWeight.SemiBold else FontWeight.Medium
                )
                if (subtitle.isNotBlank() && !subtitle.equals(label, ignoreCase = true)) {
                    Text(
                        text = subtitle,
                        color = if (rowFocused) EpgColors.TextSecondary else VodNetflixColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp
                    )
                }
            }
            if (selected) {
                Text(
                    text = "✓",
                    color = if (rowFocused) EpgColors.Accent else VodNetflixColors.Accent,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
