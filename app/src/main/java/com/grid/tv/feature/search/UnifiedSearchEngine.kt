package com.grid.tv.feature.search

import com.grid.tv.data.db.dao.ProfileWatchHistoryDao
import com.grid.tv.data.db.dao.TitleEnrichmentDao
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.UnifiedSearchResults
import com.grid.tv.domain.model.VodItem
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
                repository.channels(group = null, search = "", favoritesOnly = false),
                repository.vodStreams(),
                repository.seriesShows(),
                repository.playlists()
            ) { channels, vod, series, playlists ->
                Triple(channels, vod, series) to playlists
            }.collect { (catalog, playlists) ->
                rebuildIndex(catalog.first, catalog.second, catalog.third, playlists.isNotEmpty())
            }
        }
    }

    suspend fun search(query: String): UnifiedSearchResults = withContext(Dispatchers.Default) {
        val recent = historyStore.recentSearches()
        val trending = buildTrending()
        index.search(
            query = query,
            limitPerSection = 5,
            recentSearches = recent,
            trendingSearches = trending
        )
    }

    fun recordSearch(query: String) {
        historyStore.recordSearch(query)
    }

    private suspend fun rebuildIndex(
        channels: List<Channel>,
        vod: List<VodItem>,
        series: List<SeriesShow>,
        hasPlaylists: Boolean
    ) {
        activeProfileId = repository.activeProfileId()
        val usePlaceholder = channels.isEmpty() && hasPlaylists
        val resolvedChannels = if (usePlaceholder) EpgPlaceholderData.channels() else channels
        val now = System.currentTimeMillis()
        val programs = if (usePlaceholder) {
            EpgPlaceholderData.programs(now - 4 * 60 * 60 * 1000, now + 4 * 60 * 60 * 1000)
        } else {
            repository.programs(
                resolvedChannels.mapNotNull { it.epgId },
                now - 6 * 60 * 60 * 1000
            ).first()
        }

        val watchRows = watchHistoryDao.observeRecent(activeProfileId, 40).first()
        val popularityByChannel = watchRows
            .groupingBy { it.channelId }
            .eachCount()
            .mapValues { (_, count) -> count * 20 }

        val actors = buildActors(vod)
        val genres = buildGenres(vod, series, resolvedChannels)

        index.rebuild(
            UnifiedSearchIndex.Snapshot(
                channels = resolvedChannels,
                movies = vod,
                series = series,
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

    private suspend fun buildActors(movies: List<VodItem>): List<UnifiedSearchIndex.IndexedActor> {
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

        movies.forEach { movie ->
            movie.cast?.split(",")?.forEach { actor -> addActor(actor, movie.title, movie.posterUrl) }
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
        movies: List<VodItem>,
        series: List<SeriesShow>,
        channels: List<Channel>
    ): List<UnifiedSearchIndex.IndexedGenre> {
        val genres = linkedMapOf<String, Pair<String, String>>()
        movies.mapNotNull { it.genre }.flatMap { it.split(",") }.forEach { g ->
            val name = g.trim()
            if (name.isNotBlank()) genres.putIfAbsent(name.lowercase(), name to "Movies & Series")
        }
        series.mapNotNull { it.genre }.flatMap { it.split(",") }.forEach { g ->
            val name = g.trim()
            if (name.isNotBlank()) genres.putIfAbsent(name.lowercase(), name to "TV Series")
        }
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
