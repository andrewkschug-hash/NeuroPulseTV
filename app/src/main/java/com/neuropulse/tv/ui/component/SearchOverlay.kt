package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.neuropulse.tv.domain.model.SearchResultItem
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

@Composable
fun SearchOverlay(
    query: String,
    results: List<SearchResultItem>,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onResultSelected: (SearchResultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchFocus = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        searchFocus.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color.Black.copy(alpha = 0.75f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .align(Alignment.TopCenter)
                .background(EpgColors.Background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "⌕", fontSize = 20.sp, color = EpgColors.TextSecondary)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = EpgColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 16.sp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    cursorBrush = SolidColor(EpgColors.Accent),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .focusRequester(searchFocus)
                        .focusable()
                        .background(Color(0xFF13131A), RoundedCornerShape(8.dp))
                        .border(1.5.dp, EpgColors.Accent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search channels and shows…",
                                    color = Color(0xFF555568),
                                    fontFamily = DmSansFamily,
                                    fontSize = 16.sp
                                )
                            }
                            inner()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Surface(onClick = onClear) {
                        Text(
                            text = "✕",
                            color = EpgColors.TextSecondary,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(results) { index, result ->
                    SearchResultRow(
                        result = result,
                        focused = focusedIndex == index,
                        onClick = { onResultSelected(result) },
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> {
                                    focusedIndex = (focusedIndex + 1).coerceAtMost(results.lastIndex)
                                    true
                                }
                                Key.DirectionUp -> {
                                    focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                                    true
                                }
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                    onResultSelected(result)
                                    true
                                }
                                else -> false
                            }
                        }
                    )
                }
            }
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
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (focused) Modifier.border(
                        width = 0.dp,
                        color = Color.Transparent
                    ) else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
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
