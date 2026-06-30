package com.grid.tv.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.ui.component.TvTextField
import com.grid.tv.ui.component.tvFocusBorder
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

private val RowShape = RoundedCornerShape(8.dp)
private val CategoryColumnWidth = 240.dp

@Composable
fun SettingsTwoColumnLayout(
    categoryIndex: Int,
    categoryZoneActive: Boolean,
    optionIndex: Int,
    optionZoneActive: Boolean,
    categories: List<SettingsCategory>,
    optionRows: List<SettingsRowModel>,
    categoryFocusRequesters: List<FocusRequester>,
    optionFocusRequesters: List<FocusRequester>,
    onCategoryFocused: (Int) -> Unit,
    onOptionFocused: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        SettingsCategoryColumn(
            categories = categories,
            selectedIndex = categoryIndex,
            zoneActive = categoryZoneActive,
            focusRequesters = categoryFocusRequesters,
            onItemFocused = onCategoryFocused,
            modifier = Modifier
                .width(CategoryColumnWidth)
                .fillMaxHeight(),
        )
        Column(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(EpgColors.BorderSubtle),
        ) {}
        SettingsOptionsColumn(
            rows = optionRows,
            focusedIndex = optionIndex,
            zoneActive = optionZoneActive,
            focusRequesters = optionFocusRequesters,
            onItemFocused = onOptionFocused,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun SettingsCategoryColumn(
    categories: List<SettingsCategory>,
    selectedIndex: Int,
    zoneActive: Boolean,
    focusRequesters: List<FocusRequester>,
    onItemFocused: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, zoneActive) {
        if (!zoneActive) return@LaunchedEffect
        listState.animateScrollToItem(selectedIndex.coerceIn(0, (categories.size - 1).coerceAtLeast(0)))
    }
    Column(modifier = modifier.padding(vertical = 20.dp, horizontal = 20.dp)) {
        Text(
            text = "SETTINGS",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(categories, key = { _, item -> item.name }) { index, category ->
                val focused = zoneActive && index == selectedIndex
                SettingsCategoryRow(
                    title = category.title,
                    selected = index == selectedIndex,
                    focused = focused,
                    focusRequester = focusRequesters.getOrNull(index),
                    onFocused = { onItemFocused(index) },
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    title: String,
    selected: Boolean,
    focused: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
) {
    val bg = when {
        focused -> EpgColors.Accent.copy(alpha = 0.18f)
        selected -> Color.White.copy(alpha = 0.04f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RowShape)
            .then(
                if (focused) {
                    Modifier.border(1.dp, EpgColors.FocusBorder, RowShape)
                } else {
                    Modifier
                }
            )
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocusBorder(focused = focused, shape = RowShape, unfocusedColor = Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected) {
            Text(
                text = "▶",
                color = EpgColors.Accent,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
            )
        }
        Text(
            text = title,
            color = if (focused || selected) EpgColors.TextPrimary else EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsOptionsColumn(
    rows: List<SettingsRowModel>,
    focusedIndex: Int,
    zoneActive: Boolean,
    focusRequesters: List<FocusRequester>,
    onItemFocused: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusedIndex, zoneActive) {
        if (!zoneActive) return@LaunchedEffect
        if (rows.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(focusedIndex.coerceIn(0, rows.lastIndex))
    }
    LazyColumn(
        state = listState,
        modifier = modifier.padding(vertical = 20.dp, horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
            val focused = zoneActive && index == focusedIndex && row.focusable
            when (row) {
                is SettingsRowModel.Toggle -> SettingsToggleRow(
                    label = row.label,
                    subtitle = row.subtitle,
                    checked = row.checked,
                    focused = focused,
                    focusRequester = focusRequesters.getOrNull(index),
                    onFocused = { onItemFocused(index) },
                )
                is SettingsRowModel.Selection -> SettingsSelectionRow(
                    label = row.label,
                    value = row.options.getOrNull(row.selectedIndex) ?: "",
                    focused = focused,
                    focusRequester = focusRequesters.getOrNull(index),
                    onFocused = { onItemFocused(index) },
                )
                is SettingsRowModel.Action -> SettingsActionRow(
                    label = row.label,
                    subtitle = row.subtitle,
                    value = row.value,
                    destructive = row.destructive,
                    enabled = row.enabled,
                    focused = focused,
                    focusRequester = focusRequesters.getOrNull(index),
                    onFocused = { onItemFocused(index) },
                )
                is SettingsRowModel.Info -> SettingsInfoRow(
                    label = row.label,
                    value = row.value,
                )
                is SettingsRowModel.TextInput -> SettingsTextInputRow(
                    label = row.label,
                    value = row.value,
                    onValueChange = row.onValueChange,
                    placeholder = row.placeholder,
                    isPassword = row.isPassword,
                    focused = focused,
                    focusRequester = focusRequesters.getOrNull(index),
                    onFocused = { onItemFocused(index) },
                )
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    focused: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    SettingsBaseRow(
        label = label,
        subtitle = subtitle,
        value = if (checked) "On" else "Off",
        valueColor = if (checked) EpgColors.Accent else EpgColors.TextDimmed,
        focused = focused,
        focusRequester = focusRequester,
        onFocused = onFocused,
        modifier = modifier,
    )
}

@Composable
fun SettingsSelectionRow(
    label: String,
    value: String,
    focused: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsBaseRow(
        label = label,
        value = value,
        valueColor = EpgColors.TextSecondary,
        focused = focused,
        focusRequester = focusRequester,
        onFocused = onFocused,
        modifier = modifier,
    )
}

@Composable
fun SettingsActionRow(
    label: String,
    focused: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    SettingsBaseRow(
        label = label,
        subtitle = subtitle,
        value = value,
        valueColor = when {
            destructive -> EpgColors.Accent
            else -> EpgColors.TextDimmed
        },
        focused = focused,
        focusRequester = focusRequester,
        onFocused = onFocused,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    SettingsBaseRow(
        label = label,
        value = value,
        valueColor = EpgColors.TextSecondary,
        focused = false,
        focusRequester = null,
        onFocused = {},
        enabled = false,
        modifier = modifier,
    )
}

@Composable
fun SettingsTextInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
        )
        TvTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            label = null,
            singleLine = true,
            isPassword = isPassword,
            imeAction = ImeAction.Next,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focused) {
                        Modifier.border(1.dp, EpgColors.FocusBorder, RowShape)
                    } else {
                        Modifier
                    }
                ),
        )
    }
}

@Composable
private fun SettingsBaseRow(
    label: String,
    value: String?,
    valueColor: Color,
    focused: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val bg = if (focused) EpgColors.Accent.copy(alpha = 0.12f) else Color.Transparent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RowShape)
            .then(
                if (focused) {
                    Modifier.border(1.dp, EpgColors.FocusBorder, RowShape)
                } else {
                    Modifier
                }
            )
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusable(enabled = enabled)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocusBorder(focused = focused, shape = RowShape, unfocusedColor = Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (enabled) EpgColors.TextPrimary else EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    color = valueColor,
                    fontFamily = DmSansFamily,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 16.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
