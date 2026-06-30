package com.grid.tv.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.grid.tv.domain.model.SearchResultType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchUiState
import androidx.compose.foundation.lazy.items
import com.grid.tv.domain.model.SearchSectionSnapshot
import com.grid.tv.domain.model.SearchSectionUi
import com.grid.tv.ui.focus.TvScreenFocusRoot
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.UiMotion
import com.grid.tv.util.TvImageSizing

private sealed class SearchListRow {
    data class Header(val title: String, val sectionKey: String) : SearchListRow()
    data class Chip(val label: String, val isRecent: Boolean) : SearchListRow()
    data class Result(val item: SearchResultItem, val flatIndex: Int) : SearchListRow()
    data class Skeleton(val sectionKey: String, val index: Int) : SearchListRow()
}

@Composable
fun SearchOverlay(
    searchUiState: SearchUiState,
    searchBarState: SearchBarState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onMicClick: () -> Unit,
    onResultSelected: (SearchResultItem) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query = searchUiState.query
    val sections by remember {
        derivedStateOf { SearchSectionUi.snapshot(searchUiState) }
    }
    val flatResults = sections.flatSelectableResults
    val focusUi = remember { SearchFocusUiState() }
    val controller = remember(focusUi) { SearchFocusController(focusUi) }
    val modalTrapFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }
    val micFocusRequester = remember { FocusRequester() }

    val recentSearches = searchUiState.results.recentSearches
    val showRecentChips = query.isBlank() && recentSearches.isNotEmpty()

    val rows by remember {
        derivedStateOf { buildSearchRows(sections, query) }
    }
    val selectableResults by remember {
        derivedStateOf {
            rows.filterIsInstance<SearchListRow.Result>().map { it.item }
        }
    }
    val listState = rememberLazyListState()

    controller.bind(
        remember(
            onDismiss,
            onMicClick,
            onSuggestionSelected,
            onClearHistory,
            onResultSelected,
            selectableResults,
            showRecentChips,
            recentSearches,
        ) {
            SearchFocusDeps(
                onDismiss = onDismiss,
                onMicClick = onMicClick,
                onSuggestionSelected = onSuggestionSelected,
                onClearHistory = onClearHistory,
                onResultSelected = onResultSelected,
                selectableResults = { selectableResults },
                showRecentChips = { showRecentChips },
                recentSearches = { recentSearches },
            )
        }
    )

    SearchFocusDispatcher(
        ui = focusUi,
        fieldFocusRequester = fieldFocusRequester,
        micFocusRequester = micFocusRequester,
        modalTrapFocusRequester = modalTrapFocusRequester,
        searchBarState = searchBarState,
    )

    LaunchedEffect(focusUi.focusedIndex, focusUi.focusZone, rows) {
        if (focusUi.focusZone != SearchFocusZone.RESULTS || focusUi.focusedIndex < 0) return@LaunchedEffect
        val targetRowIndex = rows.indexOfFirst { row ->
            row is SearchListRow.Result && row.flatIndex == focusUi.focusedIndex
        }
        if (targetRowIndex >= 0) {
            listState.animateScrollToItem(targetRowIndex)
        }
    }

    val stickySectionTitle by remember(rows, listState) {
        derivedStateOf {
            val visibleIndex = listState.firstVisibleItemIndex
            if (visibleIndex < 0 || rows.isEmpty()) return@derivedStateOf null
            val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (firstVisible?.index == visibleIndex &&
                rows.getOrNull(visibleIndex) is SearchListRow.Header &&
                firstVisible.offset >= 0
            ) {
                return@derivedStateOf null
            }
            rows.take(visibleIndex + 1)
                .filterIsInstance<SearchListRow.Header>()
                .lastOrNull()
                ?.title
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "micPulse")
    val micPulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(UiMotion.MicPulseDurationMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseScale"
    )

    val fieldBorderColor = when (searchBarState) {
        SearchBarState.DEFAULT -> EpgColors.Accent
        SearchBarState.LISTENING -> Color(0xFFFF3B3B)
        SearchBarState.CONFIRMED -> Color(0xFF3DDC84)
    }

    LaunchedEffect(selectableResults) {
        focusUi.focusedIndex = if (selectableResults.isNotEmpty()) 0 else -1
    }
    LaunchedEffect(recentSearches) {
        focusUi.recentChipIndex = 0
    }

    TvScreenFocusRoot(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(EpgColors.Background)
            .focusRequester(modalTrapFocusRequester)
            .focusable(
                enabled = focusUi.focusZone == SearchFocusZone.RECENT ||
                    focusUi.focusZone == SearchFocusZone.RESULTS
            ),
        onKey = controller::handleKey,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter)
        ) {
            SearchInputRow(
                query = query,
                searchBarState = searchBarState,
                fieldBorderColor = fieldBorderColor,
                fieldFocusRequester = fieldFocusRequester,
                micFocusRequester = micFocusRequester,
                focusZone = focusUi.focusZone,
                micPulse = micPulse,
                onQueryChange = onQueryChange,
                onClear = onClear,
                onMicClick = onMicClick,
                onImeSubmitted = controller::moveFocusToSearchResults,
            )

            if (showRecentChips) {
                RecentSearchesSection(
                    recentSearches = recentSearches,
                    focusZone = focusUi.focusZone,
                    focusedChipIndex = focusUi.recentChipIndex,
                    onSuggestionSelected = onSuggestionSelected,
                    onClearHistory = onClearHistory
                )
            }

            if (searchUiState.shouldShowNoResults &&
                searchBarState != SearchBarState.LISTENING &&
                !sections.hasRenderableContent
            ) {
                Text(
                    text = "No results for \"$query\"",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SearchOverlayResultsList(
                    rows = rows,
                    flatResultsCount = flatResults.size,
                    listState = listState,
                    focusZone = focusUi.focusZone,
                    focusedIndex = focusUi.focusedIndex,
                    onResultSelected = onResultSelected,
                )
                if (stickySectionTitle != null) {
                    SearchSectionHeader(
                        title = stickySectionTitle!!,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchOverlayResultsList(
    rows: List<SearchListRow>,
    flatResultsCount: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusZone: SearchFocusZone,
    focusedIndex: Int,
    onResultSelected: (SearchResultItem) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = rows,
            key = { row ->
                when (row) {
                    is SearchListRow.Header -> "hdr-${row.sectionKey}"
                    is SearchListRow.Result -> "res-${row.item.id}"
                    is SearchListRow.Skeleton -> "sk-${row.sectionKey}-${row.index}"
                    is SearchListRow.Chip -> "chip-${row.label}"
                }
            },
            contentType = { row ->
                when (row) {
                    is SearchListRow.Header -> 0
                    is SearchListRow.Result -> 1
                    is SearchListRow.Skeleton -> 2
                    is SearchListRow.Chip -> 3
                }
            }
        ) { row ->
            when (row) {
                is SearchListRow.Header -> SearchSectionHeader(title = row.title)
                is SearchListRow.Result -> SearchResultRowItem(
                    result = row.item,
                    focused = focusZone == SearchFocusZone.RESULTS && focusedIndex == row.flatIndex,
                    onClick = { onResultSelected(row.item) }
                )
                is SearchListRow.Skeleton -> SearchResultSkeletonRow()
                is SearchListRow.Chip -> Unit
            }
        }
        if (flatResultsCount > 0) {
            item(key = "search-results-hint", contentType = 4) {
                Text(
                    text = "↓ results  ·  Enter to open",
                    color = EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchResultRowItem(
    result: SearchResultItem,
    focused: Boolean,
    onClick: () -> Unit,
) {
    SearchResultRow(
        result = result,
        focused = focused,
        onClick = onClick,
    )
}

@Composable
private fun RecentSearchesSection(
    recentSearches: List<String>,
    focusZone: SearchFocusZone,
    focusedChipIndex: Int,
    onSuggestionSelected: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
    ) {
        Text(
            text = "Recent searches",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                recentSearches,
                key = { _, term -> "recent-$term" }
            ) { index, term ->
                RecentSearchChip(
                    label = term,
                    focused = focusZone == SearchFocusZone.RECENT && focusedChipIndex == index,
                    onClick = { onSuggestionSelected(term) }
                )
            }
            item {
                ClearHistoryChip(
                    focused = focusZone == SearchFocusZone.RECENT &&
                        focusedChipIndex == recentSearches.size,
                    onClick = onClearHistory
                )
            }
        }
    }
}

@Composable
private fun RecentSearchChip(label: String, focused: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    val borderColor = if (focused) EpgColors.Accent else Color.White.copy(alpha = 0.12f)
    val backgroundColor = when {
        focused -> EpgColors.Accent.copy(alpha = 0.16f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    GridFocusSurface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 72.dp, max = 240.dp)
            .border(1.5.dp, borderColor, shape)
            .drawBehind {
                drawRoundRect(
                    color = backgroundColor,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "↩",
                color = if (focused) EpgColors.Accent else EpgColors.TextDimmed,
                fontSize = 13.sp
            )
            Text(
                text = label,
                color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ClearHistoryChip(focused: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    val borderColor = when {
        focused -> Color(0xFFFF6B6B)
        else -> Color(0xFFFF6B6B).copy(alpha = 0.35f)
    }
    val backgroundColor = when {
        focused -> Color(0xFFFF6B6B).copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    GridFocusSurface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .defaultMinSize(minWidth = 120.dp)
            .border(1.5.dp, borderColor, shape)
            .drawBehind {
                drawRoundRect(
                    color = backgroundColor,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "✕",
                color = if (focused) Color(0xFFFF8A8A) else Color(0xFFFF6B6B).copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Clear history",
                color = if (focused) Color(0xFFFFB4B4) else Color(0xFFFF8A8A),
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(EpgColors.Background)
    ) {
        Text(
            text = title,
            color = EpgColors.Accent,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

private fun buildSearchRows(sections: SearchSectionSnapshot, query: String): List<SearchListRow> {
    if (query.isBlank()) return emptyList()
    val rows = mutableListOf<SearchListRow>()
    var flatIndex = 0

    fun addSection(
        sectionKey: String,
        title: String,
        items: List<SearchResultItem>,
        showSkeleton: Boolean,
        skeletonCount: Int,
    ) {
        if (!showSkeleton && items.isEmpty()) return
        rows += SearchListRow.Header(title, sectionKey)
        if (showSkeleton) {
            repeat(skeletonCount) { index ->
                rows += SearchListRow.Skeleton(sectionKey, index)
            }
        } else {
            items.forEach { item ->
                rows += SearchListRow.Result(item, flatIndex++)
            }
        }
    }

    addSection(
        sectionKey = "channels",
        title = "Channels",
        items = sections.channels,
        showSkeleton = sections.showChannelsSkeleton,
        skeletonCount = SearchSectionUi.CHANNEL_SKELETON_COUNT,
    )
    addSection(
        sectionKey = "movies",
        title = "Movies",
        items = sections.movies,
        showSkeleton = sections.showVodSkeleton,
        skeletonCount = SearchSectionUi.VOD_SKELETON_COUNT,
    )
    addSection(
        sectionKey = "series",
        title = "Series",
        items = sections.series,
        showSkeleton = sections.showSeriesSkeleton,
        skeletonCount = SearchSectionUi.SERIES_SKELETON_COUNT,
    )
    addSection(
        sectionKey = "episodes",
        title = "Episodes",
        items = sections.episodes,
        showSkeleton = false,
        skeletonCount = 0,
    )
    addSection(
        sectionKey = "actors",
        title = "Actors",
        items = sections.actors,
        showSkeleton = false,
        skeletonCount = 0,
    )
    addSection(
        sectionKey = "genres",
        title = "Genres",
        items = sections.genres,
        showSkeleton = false,
        skeletonCount = 0,
    )
    addSection(
        sectionKey = "programs",
        title = "Live & Upcoming",
        items = sections.programs,
        showSkeleton = false,
        skeletonCount = 0,
    )
    return rows
}

@Composable
private fun SearchInputRow(
    query: String,
    searchBarState: SearchBarState,
    fieldBorderColor: Color,
    fieldFocusRequester: FocusRequester,
    micFocusRequester: FocusRequester,
    focusZone: SearchFocusZone,
    micPulse: Float,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onMicClick: () -> Unit,
    onImeSubmitted: () -> Unit,
) {
    val leadingIcon = when (searchBarState) {
        SearchBarState.DEFAULT -> "⌕"
        SearchBarState.LISTENING -> null
        SearchBarState.CONFIRMED -> "✓"
    }
    val leadingIconColor = when (searchBarState) {
        SearchBarState.DEFAULT -> EpgColors.TextSecondary
        SearchBarState.LISTENING -> Color(0xFFFF3B3B)
        SearchBarState.CONFIRMED -> Color(0xFF3DDC84)
    }
    val searchPlaceholder = when (searchBarState) {
        SearchBarState.LISTENING -> "Listening…"
        SearchBarState.CONFIRMED -> "Search confirmed"
        SearchBarState.DEFAULT -> "Search channels, movies, series, actors…"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leadingIcon != null) {
            Text(text = leadingIcon, fontSize = 20.sp, color = leadingIconColor)
        } else {
            VoiceInputGlyph(
                listening = true,
                color = leadingIconColor,
                modifier = if (searchBarState == SearchBarState.LISTENING) Modifier.scale(micPulse) else Modifier
            )
        }
        TvDialogSearchBar(
            value = query,
            onValueChange = onQueryChange,
            placeholder = searchPlaceholder,
            focusRequester = fieldFocusRequester,
            label = "Search",
            confirmLabel = "Search",
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            borderColorFocused = fieldBorderColor,
            borderColorUnfocused = fieldBorderColor.copy(alpha = 0.6f),
            onImeSubmitted = onImeSubmitted
        )
        MicButton(
            listening = searchBarState == SearchBarState.LISTENING,
            focused = focusZone == SearchFocusZone.MIC,
            pulseScale = micPulse,
            onClick = onMicClick,
            modifier = Modifier
                .focusRequester(micFocusRequester)
                .focusable()
        )
        if (query.isNotEmpty()) {
            GridFocusSurface(onClick = onClear) {
                Text("✕", color = EpgColors.TextSecondary, fontSize = 20.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun VoiceInputGlyph(
    listening: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.size(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 9.dp, height = 14.dp)
                .then(
                    if (listening) Modifier.background(color.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                    else Modifier
                )
                .border(1.5.dp, color, RoundedCornerShape(5.dp))
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(modifier = Modifier.width(1.5.dp).height(4.dp).background(color))
        Spacer(modifier = Modifier.height(1.dp))
        Box(modifier = Modifier.width(12.dp).height(1.5.dp).background(color))
    }
}

@Composable
private fun MicButton(
    listening: Boolean,
    focused: Boolean,
    pulseScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = when {
        listening -> Color(0x33FF3B3B)
        focused -> Color(0xFF1C1C2E)
        else -> Color(0xFF13131A)
    }
    val border = when {
        listening -> Color(0xFFFF3B3B)
        focused -> EpgColors.FocusBorder
        else -> Color(0xFF2A2A38)
    }
    val iconColor = if (listening) Color(0xFFFF3B3B) else EpgColors.TextSecondary
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .size(52.dp)
            .scale(if (listening) pulseScale else 1f)
            .border(1.5.dp, border, RoundedCornerShape(8.dp)),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            VoiceInputGlyph(listening = listening, color = iconColor)
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResultItem,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logoPx = remember(context) { TvImageSizing.searchResultLogoPx(context) }
    val displayTitle = remember(result.primaryTitle, result.type) {
        when (result.type) {
            SearchResultType.VOD, SearchResultType.SERIES, SearchResultType.EPISODE ->
                cleanVodDisplayTitle(result.primaryTitle)
            else -> result.primaryTitle
        }
    }
    val bg = if (focused) Color(0xFF1C1C2E) else Color.Transparent
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (focused) Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(0.dp))
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (focused) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxSize()
                        .background(EpgColors.Accent)
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                TvPosterImage(
                    url = result.imageUrl,
                    contentDescription = null,
                    kind = PosterImageKind.Custom,
                    widthPx = logoPx,
                    heightPx = logoPx,
                    placeholderLetter = displayTitle,
                    placeholderFontSize = 14.sp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = displayTitle,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.secondaryLine,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (result.isLive) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .background(Color(0x26FF3B3B), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color(0xFFFF3B3B),
                        fontFamily = DmSansFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
