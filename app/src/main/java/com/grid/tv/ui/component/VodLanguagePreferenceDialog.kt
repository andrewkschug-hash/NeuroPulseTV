package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grid.tv.feature.vod.displayLanguageName
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.VodNetflixColors

private val DialogBg = Color(0xFF1A1A2E)
private val DialogBody = Color(0xFFB0B0C0)

@Composable
fun VodLanguagePreferenceDialog(
    availableLanguages: List<String>,
    selectedLanguages: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(selectedLanguages) { mutableStateOf(selectedLanguages) }
    var focusIndex by remember { mutableIntStateOf(0) }
    val optionCount = availableLanguages.size + 2
    val doneFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusIndex = 0
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
                    text = "Show movies and series matching your selected languages. Choose All Languages to disable filtering.",
                    color = DialogBody,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp
                )
                LazyColumn(
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
                            focused = focusIndex == 0,
                            onClick = {
                                draft = emptySet()
                                focusIndex = 0
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
                                focused = focusIndex == rowIndex,
                                onClick = {
                                    focusIndex = rowIndex
                                    draft = if (selected) {
                                        draft.filterNot { it.equals(code, ignoreCase = true) }.toSet()
                                    } else {
                                        draft + code.uppercase()
                                    }
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
            GlowFocusButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp
                )
            }
        }
    )

    LaunchedEffect(Unit) {
        doneFocusRequester.requestFocusSafelyAfterLayout()
    }
}

@Composable
private fun LanguageOptionRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = when {
        focused -> VodNetflixColors.Accent
        selected -> Color.White.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val backgroundColor = when {
        selected -> Color(0xFF2A2A2A)
        focused -> Color(0xFF333333)
        else -> Color(0xFF141414)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor, shape)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = VodNetflixColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            if (subtitle.isNotBlank() && !subtitle.equals(label, ignoreCase = true)) {
                Text(
                    text = subtitle,
                    color = VodNetflixColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp
                )
            }
        }
        if (selected) {
            Text(
                text = "✓",
                color = VodNetflixColors.Accent,
                fontFamily = DmSansFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
