package com.grid.tv.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.UnifiedSearchResults
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.TvTextInputSession

private enum class SearchFocusZone { FIELD, MIC, RECENT, RESULTS }

private sealed class SearchListRow {
    data class Header(val title: String) : SearchListRow()
    data class Chip(val label: String, val isRecent: Boolean) : SearchListRow()
    data class Result(val item: SearchResultItem, val flatIndex: Int) : SearchListRow()
}

@Composable
fun SearchOverlay(
    query: String,
    unifiedResults: UnifiedSearchResults,
    flatResults: List<SearchResultItem>,
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
    val modalTrapFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }
    val micFocusRequester = remember { FocusRequester() }
    var focusZone by remember { mutableStateOf(SearchFocusZone.FIELD) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var recentChipIndex by remember { mutableIntStateOf(0) }

    val recentSearches = unifiedResults.recentSearches
    val showRecentChips = query.isBlank() && recentSearches.isNotEmpty()

    val rows = remember(unifiedResults, query) {
        buildSearchRows(unifiedResults, query)
    }
    val selectableRows = remember(rows) { rows.filterIsInstance<SearchListRow.Result>() }

    val pulseTransition = rememberInfiniteTransition(label = "micPulse")
    val micPulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "micPulseScale"
    )

    val fieldBorderColor = when (searchBarState) {
        SearchBarState.DEFAULT -> EpgColors.Accent
        SearchBarState.LISTENING -> Color(0xFFFF3B3B)
        SearchBarState.CONFIRMED -> Color(0xFF3DDC84)
    }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) { fieldFocusRequester.requestFocusSafelyAfterLayout() }
    LaunchedEffect(searchBarState, unifiedResults, flatResults) {
        when (focusZone) {
            SearchFocusZone.FIELD -> fieldFocusRequester.requestFocusSafelyAfterLayout()
            SearchFocusZone.MIC -> micFocusRequester.requestFocusSafelyAfterLayout()
            SearchFocusZone.RECENT, SearchFocusZone.RESULTS ->
                modalTrapFocusRequester.requestFocusSafelyAfterLayout()
        }
    }
    LaunchedEffect(selectableRows) { focusedIndex = if (selectableRows.isNotEmpty()) 0 else -1 }
    LaunchedEffect(recentSearches) { recentChipIndex = 0 }
    LaunchedEffect(focusZone) {
        when (focusZone) {
            SearchFocusZone.FIELD -> fieldFocusRequester.requestFocusSafelyAfterLayout()
            SearchFocusZone.MIC -> micFocusRequester.requestFocusSafelyAfterLayout()
            SearchFocusZone.RECENT, SearchFocusZone.RESULTS ->
                modalTrapFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun selectAt(index: Int) {
        selectableRows.getOrNull(index)?.item?.let(onResultSelected)
    }

    fun moveFocusToSearchResults() {
        focusZone = when {
            selectableRows.isNotEmpty() -> SearchFocusZone.RESULTS
            showRecentChips -> SearchFocusZone.RECENT
            else -> SearchFocusZone.MIC
        }
        if (focusZone == SearchFocusZone.RESULTS && focusedIndex < 0) {
            focusedIndex = 0
        }
    }

    fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        return when (event.key) {
            Key.Back, Key.Escape -> { onDismiss(); true }
            Key.DirectionDown -> {
                when (focusZone) {
                    SearchFocusZone.FIELD -> focusZone = when {
                        showRecentChips -> SearchFocusZone.RECENT
                        else -> SearchFocusZone.MIC
                    }
                    SearchFocusZone.RECENT -> focusZone = SearchFocusZone.MIC
                    SearchFocusZone.MIC -> if (selectableRows.isNotEmpty()) {
                        focusZone = SearchFocusZone.RESULTS
                        if (focusedIndex < 0) focusedIndex = 0
                    }
                    SearchFocusZone.RESULTS -> if (focusedIndex < selectableRows.lastIndex) {
                        focusedIndex += 1
                    }
                }
                true
            }
            Key.DirectionUp -> {
                when (focusZone) {
                    SearchFocusZone.RESULTS -> {
                        if (focusedIndex <= 0) {
                            focusZone = when {
                                showRecentChips -> SearchFocusZone.RECENT
                                else -> SearchFocusZone.MIC
                            }
                        } else {
                            focusedIndex -= 1
                        }
                    }
                    SearchFocusZone.MIC -> focusZone = when {
                        showRecentChips -> SearchFocusZone.RECENT
                        else -> SearchFocusZone.FIELD
                    }
                    SearchFocusZone.RECENT -> focusZone = SearchFocusZone.FIELD
                    SearchFocusZone.FIELD -> Unit
                }
                true
            }
            Key.DirectionRight -> when (focusZone) {
                SearchFocusZone.FIELD -> { focusZone = SearchFocusZone.MIC; true }
                SearchFocusZone.RECENT -> if (recentChipIndex < recentSearches.size) {
                    recentChipIndex += 1; true
                } else false
                else -> false
            }
            Key.DirectionLeft -> when (focusZone) {
                SearchFocusZone.MIC -> { focusZone = SearchFocusZone.FIELD; true }
                SearchFocusZone.RECENT -> if (recentChipIndex > 0) {
                    recentChipIndex -= 1; true
                } else false
                else -> false
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> when (focusZone) {
                SearchFocusZone.MIC -> { onMicClick(); true }
                SearchFocusZone.RECENT -> when {
                    recentChipIndex == recentSearches.size -> {
                        onClearHistory()
                        true
                    }
                    else -> {
                        recentSearches.getOrNull(recentChipIndex)?.let(onSuggestionSelected)
                        true
                    }
                }
                SearchFocusZone.RESULTS -> if (selectableRows.isNotEmpty()) {
                    selectAt(focusedIndex.coerceAtLeast(0)); true
                } else false
                SearchFocusZone.FIELD -> false
            }
            else -> false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color.Black.copy(alpha = 0.75f))
            .focusRequester(modalTrapFocusRequester)
            .focusable()
            .onPreviewKeyEvent { handleKey(it) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .align(Alignment.TopCenter)
                .background(EpgColors.Background)
        ) {
            SearchInputRow(
                query = query,
                searchBarState = searchBarState,
                fieldBorderColor = fieldBorderColor,
                fieldFocusRequester = fieldFocusRequester,
                micFocusRequester = micFocusRequester,
                focusZone = focusZone,
                micPulse = micPulse,
                flatResults = flatResults,
                focusedIndex = focusedIndex,
                onQueryChange = onQueryChange,
                onClear = onClear,
                onMicClick = onMicClick,
                onSelectAt = ::selectAt,
                onImeSubmitted = ::moveFocusToSearchResults,
                handleKey = ::handleKey
            )

            if (showRecentChips) {
                RecentSearchesSection(
                    recentSearches = recentSearches,
                    focusZone = focusZone,
                    focusedChipIndex = recentChipIndex,
                    onSuggestionSelected = onSuggestionSelected,
                    onClearHistory = onClearHistory
                )
            }

            if (query.isNotBlank() && unifiedResults.isEmpty && searchBarState != SearchBarState.LISTENING) {
                Text(
                    text = "No results for \"$query\"",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(rows) { _, row ->
                    when (row) {
                        is SearchListRow.Header -> SearchSectionHeader(row.title)
                        is SearchListRow.Chip -> Unit
                        is SearchListRow.Result -> SearchResultRow(
                            result = row.item,
                            focused = focusZone == SearchFocusZone.RESULTS && focusedIndex == row.flatIndex,
                            onClick = { onResultSelected(row.item) }
                        )
                    }
                }
            }

            if (flatResults.isNotEmpty()) {
                Text(
                    text = "↓ results  ·  Enter to open",
                    color = EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        }
    }
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
            itemsIndexed(recentSearches) { index, term ->
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
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        color = EpgColors.Accent,
        fontFamily = DmSansFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

private fun buildSearchRows(unified: UnifiedSearchResults, query: String): List<SearchListRow> {
    if (query.isBlank()) return emptyList()
    val rows = mutableListOf<SearchListRow>()
    var flatIndex = 0
    fun addSection(title: String, items: List<SearchResultItem>) {
        if (items.isEmpty()) return
        rows += SearchListRow.Header(title)
        items.forEach { item ->
            rows += SearchListRow.Result(item, flatIndex++)
        }
    }
    addSection("Channels", unified.channels)
    addSection("Movies", unified.movies)
    addSection("Series", unified.series)
    addSection("Episodes", unified.episodes)
    addSection("Actors", unified.actors)
    addSection("Genres", unified.genres)
    if (unified.programs.isNotEmpty()) addSection("Live & Upcoming", unified.programs)
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
    flatResults: List<SearchResultItem>,
    focusedIndex: Int,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onMicClick: () -> Unit,
    onSelectAt: (Int) -> Unit,
    onImeSubmitted: () -> Unit,
    handleKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean
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
            onPreviewKeyEvent = handleKey,
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
                .onPreviewKeyEvent { handleKey(it) }
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
            if (result.imageUrl != null) {
                AsyncImage(
                    model = result.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1A1A22)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = result.primaryTitle.take(1),
                        color = EpgColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = result.primaryTitle,
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
