package com.grid.tv.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.tv.material3.Text
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.feature.guide.GuideCategoryProcessor
import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.component.GuideGroupCategory
import com.grid.tv.ui.component.GuideGroupVisibleRow
import com.grid.tv.ui.component.buildVisibleGuideGroupRows
import com.grid.tv.ui.component.guideFilterRowAction
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.toggleCategoryExpansion
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

@Composable
fun GuideGroupsScreen(
    organized: GuideCategoryProcessor.OrganizedGuideGroups,
    selectedGroups: Set<String>,
    hideAdult: Boolean,
    onApplyFilter: (GuideChannelFilter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = organized.flatCategories
    var expandedCategories by remember(selectedGroups) {
        mutableStateOf(
            categories.indices.filter { idx ->
                categories[idx].groups.any { it in selectedGroups }
            }.toSet()
        )
    }
    var focusIndex by remember { mutableIntStateOf(0) }
    val rows = remember(categories, expandedCategories) {
        buildVisibleGuideGroupRows(categories, expandedCategories)
    }
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocus.requestFocusSafelyAfterLayout()
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onBack()
                        true
                    }
                    Key.DirectionDown -> {
                        if (focusIndex < rows.lastIndex) focusIndex += 1
                        true
                    }
                    Key.DirectionUp -> {
                        if (focusIndex > 0) focusIndex -= 1
                        true
                    }
                    else -> false
                }
            }
    ) {
        Text(
            text = "Channel Groups",
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .focusGroup()
        ) {
            item {
                SectionHeader(GuideCategoryProcessor.SECTION_ALL)
                GroupRow(
                    label = "All Channels (${organized.allChannelCount})",
                    selected = selectedGroups.isEmpty(),
                    focused = focusIndex == 0,
                    modifier = Modifier
                        .focusRequester(firstFocus)
                        .onFocusChanged { if (it.isFocused) focusIndex = 0 },
                    onClick = {
                        onApplyFilter(GuideChannelFilter.All)
                        onBack()
                    }
                )
            }
            if (organized.countryCategories.isNotEmpty()) {
                item { SectionHeader(GuideCategoryProcessor.SECTION_COUNTRIES) }
            }
            organized.countryCategories.forEach { category ->
                item {
                    CategorySection(
                        category = category,
                        categories = categories,
                        expandedCategories = expandedCategories,
                        selectedGroups = selectedGroups,
                        onToggleExpand = { idx ->
                            expandedCategories = toggleCategoryExpansion(expandedCategories, idx)
                        },
                        onApplyFilter = onApplyFilter,
                        onBack = onBack
                    )
                }
            }
            if (organized.contentCategories.isNotEmpty()) {
                item { SectionHeader(GuideCategoryProcessor.SECTION_CONTENT) }
            }
            organized.contentCategories.forEach { category ->
                item {
                    CategorySection(
                        category = category,
                        categories = categories,
                        expandedCategories = expandedCategories,
                        selectedGroups = selectedGroups,
                        onToggleExpand = { idx ->
                            expandedCategories = toggleCategoryExpansion(expandedCategories, idx)
                        },
                        onApplyFilter = onApplyFilter,
                        onBack = onBack
                    )
                }
            }
            if (organized.providerCategories.isNotEmpty()) {
                item { SectionHeader(GuideCategoryProcessor.SECTION_PROVIDER) }
            }
            itemsIndexed(organized.providerCategories) { _, category ->
                CategorySection(
                    category = category,
                    categories = categories,
                    expandedCategories = expandedCategories,
                    selectedGroups = selectedGroups,
                    onToggleExpand = { idx ->
                        expandedCategories = toggleCategoryExpansion(expandedCategories, idx)
                    },
                    onApplyFilter = onApplyFilter,
                    onBack = onBack
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = EpgColors.TextDimmed,
        fontFamily = DmSansFamily,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun CategorySection(
    category: GuideGroupCategory,
    categories: List<GuideGroupCategory>,
    expandedCategories: Set<Int>,
    selectedGroups: Set<String>,
    onToggleExpand: (Int) -> Unit,
    onApplyFilter: (GuideChannelFilter) -> Unit,
    onBack: () -> Unit
) {
    val categoryIndex = categories.indexOfFirst { it.displayName == category.displayName }
    if (categoryIndex < 0) return
    val expanded = categoryIndex in expandedCategories
    GroupRow(
        label = "${category.displayName} (${category.channelCount})",
        selected = category.groups.all { it in selectedGroups },
        focused = false,
        onClick = { onToggleExpand(categoryIndex) }
    )
    if (expanded) {
        category.groups.forEach { groupKey ->
            val name = com.grid.tv.domain.model.ChannelGroupIdentity.groupName(groupKey)
            GroupRow(
                label = name,
                selected = groupKey in selectedGroups,
                focused = false,
                onClick = {
                    onApplyFilter(GuideChannelFilter(setOf(groupKey)))
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun GroupRow(
    label: String,
    selected: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlowFocusButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        externallyFocused = focused
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                color = if (selected) EpgColors.Accent else EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Text(
                    text = "✓",
                    color = EpgColors.Accent,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp
                )
            }
        }
    }
}
