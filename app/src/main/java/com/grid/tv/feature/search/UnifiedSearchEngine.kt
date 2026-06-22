package com.grid.tv.feature.search

import com.grid.tv.data.db.dao.ProfileWatchHistoryDao
import com.grid.tv.data.db.dao.TitleEnrichmentDao
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchResultType
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.UnifiedSearchResults
import com.grid.tv.domain.model.VodItem
import com.grid.tv.ui.component.buildMovieSearchSecondaryLine
import com.grid.tv.ui.component.cleanVodDisplayTitle
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.epg.ChannelCategoryPresets
import com.grid.tv.feature.epg.EpgPlaceholderData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class UnifiedSearchEngine @Inject constructor(
    private val repository: IptvRepository,
    private val watchHistoryDao: ProfileWatchHistoryDao,
    private val titleEnrichmentDao: TitleEnrichmentDao,
    private val historyStore: SearchHistoryStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val index = UnifiedSearchIndex()

    private val _indexReady = MutableStateFlow(false)
    val indexReady: StateFlow<Boolean> = _indexReady.asStateFlow()

    private var activeProfileId: Long = 1L

    init {
        scope.launch {
            combine(
                repository.vodCatalogRevision(),
                repository.playlists()
            ) { _, playlists ->
                playlists.isNotEmpty()
            }.collect { hasPlaylists ->
                rebuildIndex(hasPlaylists)
            }
        }
    }

    suspend fun search(query: String): UnifiedSearchResults = withContext(Dispatchers.Default) {
        val recent = historyStore.recentSearches()
        val trending = buildTrending()
        val base = index.search(
            query = query,
            recentSearches = recent,
            trendingSearches = trending
        )
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext base
        val channelMatches = repository.searchChannels(trimmed, limit = 500).map(::channelSearchResult)
        val vodMatches = repository.searchVod(trimmed, limit = 500).map(::vodSearchResult)
        val seriesMatches = repository.searchSeriesShows(trimmed, limit = 500).map(::seriesSearchResult)
        base.copy(
            channels = (channelMatches + base.channels).distinctBy { it.id },
            movies = (vodMatches + base.movies).distinctBy { it.id },
            series = (seriesMatches + base.series).distinctBy { it.id }
        )
    }

    private fun channelSearchResult(channel: com.grid.tv.domain.model.Channel): SearchResultItem =
        SearchResultItem(
            id = "ch-${channel.id}",
            primaryTitle = channel.name,
            secondaryLine = "Channel ${channel.number}${channel.group.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
            imageUrl = channel.logoUrl,
            type = SearchResultType.CHANNEL,
            channelId = channel.id,
            isLive = true
        )

    private fun vodSearchResult(movie: VodItem): SearchResultItem =
        SearchResultItem(
            id = "vod-${movie.id}",
            primaryTitle = cleanVodDisplayTitle(movie.title),
            secondaryLine = buildMovieSearchSecondaryLine(movie.genre, movie.rating),
            imageUrl = movie.posterUrl,
            type = SearchResultType.VOD,
            vodItem = movie
        )

    private fun seriesSearchResult(show: SeriesShow): SearchResultItem =
        SearchResultItem(
            id = "series-${show.id}",
            primaryTitle = cleanVodDisplayTitle(show.name),
            secondaryLine = listOfNotNull("Series", show.genre).joinToString(" · "),
            imageUrl = show.coverUrl,
            type = SearchResultType.SERIES,
            seriesShow = show
        )

    fun recordSearch(query: String) {
        historyStore.recordSearch(query)
    }

    suspend fun clearRecentHistory() {
        historyStore.clearRecent()
    }

    private suspend fun rebuildIndex(hasPlaylists: Boolean) {
        activeProfileId = repository.activeProfileId()
        val hasChannelsInDb = repository.hasChannels().first()
        val usePlaceholder = !hasChannelsInDb && hasPlaylists
        val resolvedChannels = if (usePlaceholder) EpgPlaceholderData.channels() else emptyList()
        val now = System.currentTimeMillis()
        val programs = if (usePlaceholder) {
            EpgPlaceholderData.programs(now - 4 * 60 * 60 * 1000, now + 4 * 60 * 60 * 1000)
        } else {
            emptyList()
        }

        val watchRows = watchHistoryDao.observeRecent(activeProfileId, 40).first()
        val popularityByChannel = watchRows
            .groupingBy { it.channelId }
            .eachCount()
            .mapValues { (_, count) -> count * 20 }

        val actors = buildActors()
        val genres = buildGenres(resolvedChannels)

        index.rebuild(
            UnifiedSearchIndex.Snapshot(
                channels = resolvedChannels,
                movies = emptyList(),
                series = emptyList(),
                episodes = emptyList(),
                actors = actors,
                genres = genres,
                programs = programs,
                channelByEpgId = resolvedChannels.associateBy { it.epgId.orEmpty() },
                popularityByChannelId = popularityByChannel,
                lastWatchedChannelIds = watchRows.map { it.channelId }.distinct(),
                lastWatchedVodStreamIds = watchRows.filter { it.channelId < 0 }
                    .map { -it.channelId }
                    .distinct(),
                lastWatchedTitles = watchRows.mapNotNull { it.lastProgramTitle?.takeIf { t -> t.isNotBlank() } }
            )
        )
        _indexReady.value = true
    }

    private suspend fun buildActors(): List<UnifiedSearchIndex.IndexedActor> {
        val byActor = linkedMapOf<String, MutableList<String>>()
        val posterByActor = mutableMapOf<String, String?>()
        val displayNameByKey = mutableMapOf<String, String>()

        fun addActor(raw: String?, title: String, poster: String?) {
            val name = raw?.trim()?.takeIf { it.length >= 2 } ?: return
            val key = name.lowercase()
            displayNameByKey.putIfAbsent(key, name)
            byActor.getOrPut(key) { mutableListOf() }.add(title)
            if (posterByActor[key] == null && poster != null) posterByActor[key] = poster
        }

        val enrichments = titleEnrichmentDao.topByPopularity(120)
        enrichments.forEach { row ->
            row.cast?.split(",")?.forEach { actor ->
                addActor(actor, row.title ?: row.normalizedTitle, row.posterUrl)
            }
        }

        return byActor.map { (key, titles) ->
            UnifiedSearchIndex.IndexedActor(
                name = displayNameByKey[key] ?: key,
                knownTitles = titles.distinct().take(5),
                posterUrl = posterByActor[key]
            )
        }
    }

    private fun buildGenres(
        channels: List<com.grid.tv.domain.model.Channel>
    ): List<UnifiedSearchIndex.IndexedGenre> {
        val genres = linkedMapOf<String, Pair<String, String>>()
        channels.map { it.group }.distinct().take(40).forEach { group ->
            val name = group.trim()
            if (name.isNotBlank()) genres.putIfAbsent(name.lowercase(), name to "Channel group")
        }
        ChannelCategoryPresets.presets.forEach { preset ->
            genres.putIfAbsent(preset.label.lowercase(), preset.label to "Category")
        }
        return genres.values.map { (name, label) ->
            UnifiedSearchIndex.IndexedGenre(name = name, sourceLabel = label)
        }
    }

    private suspend fun buildTrending(): List<String> {
        val fromHistory = watchHistoryDao.observeTop(activeProfileId, 6).first()
            .mapNotNull { it.lastProgramTitle?.takeIf { t -> t.isNotBlank() } }
        return (fromHistory + SearchHistoryStore.DEFAULT_TRENDING).distinct().take(8)
    }
}
