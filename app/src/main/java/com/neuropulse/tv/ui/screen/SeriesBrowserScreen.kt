package com.neuropulse.tv.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neuropulse.tv.ui.component.VodCategoryChip
import com.neuropulse.tv.ui.component.VodEmptyState
import com.neuropulse.tv.domain.model.PlaylistType
import com.neuropulse.tv.domain.model.VodPlaybackContext
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.SeriesViewModel
import com.neuropulse.tv.ui.viewmodel.SettingsViewModel

@Composable
fun SeriesBrowserScreen(
    initialSeriesId: Long? = null,
    onPlayUrl: (String, String) -> Unit,
    onBack: () -> Unit = {},
    embedded: Boolean = false,
    contentFocusRequester: FocusRequester? = null,
    onMoveFocusUp: (() -> Unit)? = null,
    viewModel: SeriesViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val shows by viewModel.shows.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val selectedShowId by viewModel.selectedShowId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val playlists by settingsViewModel.playlists.collectAsStateWithLifecycle()
    val isM3uOnly = playlists.isNotEmpty() && playlists.all { it.type == PlaylistType.M3U }

    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var focusedShowId by remember { mutableStateOf<Long?>(null) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }

    val categories = remember(shows) {
        listOf("All") + shows.mapNotNull { it.name.split(" ").firstOrNull() }.distinct().take(8)
    }

    LaunchedEffect(initialSeriesId, shows) {
        if (initialSeriesId != null && shows.any { it.id == initialSeriesId }) {
            viewModel.selectShow(initialSeriesId)
            focusedShowId = initialSeriesId
        }
    }

    val activeShow = shows.find { it.id == selectedShowId }

    if (message != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = { Text("Series recording") },
            text = { Text(message.orEmpty()) },
            confirmButton = {
                Button(onClick = { viewModel.clearMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .padding(if (embedded) 0.dp else 20.dp)
    ) {
        if (!embedded) {
            RowHeader(onBack = onBack)
        }

        if (seasons.isEmpty()) {
            if (embedded) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .then(
                            if (onMoveFocusUp != null) {
                                Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        onMoveFocusUp()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    itemsIndexed(categories) { index, cat ->
                        val selected = cat == selectedCategory
                        VodCategoryChip(
                            label = cat,
                            selected = selected,
                            focused = false,
                            onClick = { selectedCategory = cat },
                            modifier = if (index == 0 && contentFocusRequester != null) {
                                Modifier.focusRequester(contentFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    placeholder = { Text("Search series", fontFamily = DmSansFamily) }
                )
            }
        }

        if (activeShow != null && !isM3uOnly) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .then(
                        if (embedded && onMoveFocusUp != null) {
                            Modifier.onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                    onMoveFocusUp()
                                    true
                                } else {
                                    false
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (embedded && contentFocusRequester != null && seasons.isNotEmpty()) {
                            Modifier.focusRequester(contentFocusRequester)
                        } else {
                            Modifier
                        }
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activeShow.name,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { viewModel.recordSeries(activeShow) }) {
                    Text("Record Series", fontFamily = DmSansFamily)
                }
            }
        }

        if (isM3uOnly) {
            Text(
                text = "Series organization depends on your provider's playlist structure",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }

        if (seasons.isEmpty()) {
            if (!embedded) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    items(categories) { cat ->
                        val selected = cat == selectedCategory
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (selected) Color(0xFF1C3A6B) else Color(0xFF13131A),
                                focusedContainerColor = Color(0xFF1C1C2E)
                            )
                        ) {
                            Text(
                                text = cat,
                                color = if (selected) EpgColors.Accent else EpgColors.TextSecondary,
                                fontFamily = DmSansFamily,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            val filtered = remember(shows, selectedCategory, searchQuery, embedded) {
                val byCategory = if (selectedCategory == "All") shows else shows.filter {
                    it.name.contains(selectedCategory, ignoreCase = true)
                }
                if (!embedded && searchQuery.isNotBlank()) {
                    byCategory.filter { it.name.contains(searchQuery, ignoreCase = true) }
                } else {
                    byCategory
                }
            }

            if (filtered.isEmpty()) {
                VodEmptyState(
                    title = "No series available",
                    message = "Add an Xtream playlist with series in Settings, or use ⌕ Search once titles are loaded."
                )
            } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.id }) { show ->
                    val focused = focusedShowId == show.id
                    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(150), label = "posterScale")
                    Surface(
                        onClick = {
                            viewModel.selectShow(show.id)
                            focusedShowId = show.id
                        },
                        modifier = Modifier.scale(scale),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF13131A))
                                    .then(
                                        if (focused) Modifier.border(2.dp, EpgColors.Accent, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                            ) {
                                AsyncImage(
                                    model = show.coverUrl,
                                    contentDescription = show.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = show.name,
                                color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                                fontFamily = DmSansFamily,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
            }
        } else {
            Text(
                text = seasons.firstOrNull()?.let { "Seasons" } ?: "Select a show",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(seasons) { season ->
                    Button(onClick = { selectedSeason = season.number }) {
                        Text(
                            if (selectedSeason == season.number) "Season ${season.number} ✓" else "Season ${season.number}"
                        )
                    }
                }
            }
            val episodes = seasons.firstOrNull { it.number == selectedSeason }?.episodes
                ?: seasons.firstOrNull()?.episodes
                ?: emptyList()
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(episodes) { episode ->
                    Button(onClick = {
                        val showId = selectedShowId ?: return@Button
                        val show = shows.firstOrNull { it.id == showId }
                        val seasonNum = selectedSeason ?: seasons.firstOrNull()?.number ?: return@Button
                        val episodeNum = episodes.indexOf(episode) + 1
                        VodPlaybackContext.stageSeriesEpisode(
                            posterUrl = show?.coverUrl,
                            streamId = episode.id,
                            seriesId = showId,
                            seasonNumber = seasonNum,
                            episodeNumber = episodeNum
                        )
                        onPlayUrl(episode.streamUrl, episode.title)
                    }) {
                        Text("${episode.title} (${episode.duration ?: "N/A"})")
                    }
                }
            }
        }
    }
}

@Composable
private fun RowHeader(onBack: () -> Unit) {
    Button(onClick = onBack) {
        Text("← Back", fontFamily = DmSansFamily)
    }
    Text(
        text = "Series & Shows",
        color = EpgColors.TextPrimary,
        fontFamily = DmSansFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp)
    )
}
