package com.grid.tv.feature.search

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchResultType
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.UnifiedSearchResults
import com.grid.tv.domain.model.VodItem
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory search index. Built once per catalog snapshot; queries avoid database scans.
 */
class UnifiedSearchIndex {

    data class Snapshot(
        val channels: List<Channel> = emptyList(),
        val movies: List<VodItem> = emptyList(),
        val series: List<SeriesShow> = emptyList(),
        val episodes: List<IndexedEpisode> = emptyList(),
        val actors: List<IndexedActor> = emptyList(),
        val genres: List<IndexedGenre> = emptyList(),
        val programs: List<Program> = emptyList(),
        val channelByEpgId: Map<String, Channel> = emptyMap(),
        val popularityByChannelId: Map<Long, Int> = emptyMap(),
        val lastWatchedChannelIds: List<Long> = emptyList(),
        val lastWatchedVodStreamIds: List<Long> = emptyList(),
        val lastWatchedTitles: List<String> = emptyList()
    )

    data class IndexedEpisode(
        val series: SeriesShow,
        val seasonNumber: Int,
        val episode: SeriesEpisode
    )

    data class IndexedActor(
        val name: String,
        val knownTitles: List<String>,
        val posterUrl: String?
    )

    data class IndexedGenre(
        val name: String,
        val sourceLabel: String
    )

    private data class IndexedEntry(
        val result: SearchResultItem,
        val normalized: String,
        val tokens: Set<String>,
        val popularity: Int,
        val lastWatchedAt: Long
    )

    private val snapshotRef = AtomicReference(Snapshot())
    private val entriesRef = AtomicReference<List<IndexedEntry>>(emptyList())
    private val tokenIndexRef = AtomicReference<Map<String, List<Int>>>(emptyMap())

    fun rebuild(snapshot: Snapshot) {
        snapshotRef.set(snapshot)
        val built = buildEntries(snapshot)
        entriesRef.set(built)
        tokenIndexRef.set(buildTokenIndex(built))
    }

    fun search(
        query: String,
        limitPerSection: Int = 5,
        recentSearches: List<String> = emptyList(),
        trendingSearches: List<String> = SearchHistoryStore.DEFAULT_TRENDING
    ): UnifiedSearchResults {
        val q = query.trim()
        if (q.isEmpty()) {
            return UnifiedSearchResults(
                recentSearches = recentSearches,
                trendingSearches = trendingSearches
            )
        }

        val normalizedQuery = normalize(q)
        val queryTokens = tokenize(q)
        val candidateIndices = candidateIndicesFor(queryTokens, normalizedQuery)
        val entries = entriesRef.get()
        val ranked = candidateIndices
            .asSequence()
            .mapNotNull { idx -> entries.getOrNull(idx) }
            .distinctBy { it.result.id }
            .map { entry -> entry to scoreEntry(normalizedQuery, queryTokens, entry) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first.result }
            .toList()

        return UnifiedSearchResults(
            query = q,
            channels = ranked.filter { it.type == SearchResultType.CHANNEL }.take(limitPerSection),
            movies = ranked.filter { it.type == SearchResultType.VOD }.take(limitPerSection),
            series = ranked.filter { it.type == SearchResultType.SERIES }.take(limitPerSection),
            episodes = ranked.filter { it.type == SearchResultType.EPISODE }.take(limitPerSection),
            actors = ranked.filter { it.type == SearchResultType.ACTOR }.take(limitPerSection),
            genres = ranked.filter { it.type == SearchResultType.GENRE }.take(limitPerSection),
            programs = ranked.filter { it.type == SearchResultType.PROGRAM }.take(limitPerSection),
            recentSearches = recentSearches,
            trendingSearches = trendingSearches
        )
    }

    private fun candidateIndicesFor(queryTokens: List<String>, normalizedQuery: String): Set<Int> {
        val tokenIndex = tokenIndexRef.get()
        val fromTokens = if (queryTokens.isEmpty()) {
            emptySet()
        } else if (queryTokens.size == 1) {
            tokenIndex[queryTokens.first()]?.toSet() ?: emptySet()
        } else {
            queryTokens
                .mapNotNull { tokenIndex[it]?.toSet() }
                .reduceOrNull { acc, set -> acc.intersect(set) }
                ?: emptySet()
        }

        if (fromTokens.isNotEmpty()) return fromTokens

        val prefix = normalizedQuery.firstOrNull() ?: return entriesRef.get().indices.toSet()
        return entriesRef.get().withIndex()
            .filter { (_, entry) ->
                entry.normalized.contains(normalizedQuery) ||
                    entry.tokens.any { it.startsWith(normalizedQuery) }
            }
            .map { it.index }
            .toSet()
            .ifEmpty { entriesRef.get().indices.toSet() }
    }

    private fun scoreEntry(
        normalizedQuery: String,
        queryTokens: List<String>,
        entry: IndexedEntry
    ): Int {
        val text = entry.normalized
        var score = when {
            text == normalizedQuery -> 10_000
            text.startsWith(normalizedQuery) -> 8_000
            queryTokens.isNotEmpty() && queryTokens.all { text.contains(it) } -> 6_000
            text.contains(normalizedQuery) -> 4_000
            else -> FuzzySearch.score(normalizedQuery, text)?.score ?: 0
        }
        score += entry.popularity
        if (entry.lastWatchedAt > 0) score += 500
        if (entry.result.isLive) score += 300
        return score
    }

    private fun buildEntries(snapshot: Snapshot): List<IndexedEntry> {
        val now = System.currentTimeMillis()
        val recentChannelRank = snapshot.lastWatchedChannelIds.withIndex().associate { (i, id) -> id to (100 - i) }
        val recentVodRank = snapshot.lastWatchedVodStreamIds.withIndex().associate { (i, id) -> id to (100 - i) }
        val entries = mutableListOf<IndexedEntry>()

        snapshot.channels.forEach { ch ->
            val popularity = recentChannelRank[ch.id] ?: snapshot.popularityByChannelId[ch.id] ?: 0
            val result = SearchResultItem(
                id = "ch-${ch.id}",
                primaryTitle = ch.name,
                secondaryLine = "Channel ${ch.number}${ch.group.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                imageUrl = ch.logoUrl,
                type = SearchResultType.CHANNEL,
                channelId = ch.id
            )
            entries += indexOf(result, ch.name, popularity, if (ch.id in snapshot.lastWatchedChannelIds) now else 0L)
        }

        snapshot.movies.forEach { movie ->
            val popularity = recentVodRank[movie.streamId] ?: movie.addedEpochSec?.let { (it / 86_400).toInt() } ?: 0
            val watched = if (movie.streamId in snapshot.lastWatchedVodStreamIds) now else 0L
            val result = SearchResultItem(
                id = "vod-${movie.id}",
                primaryTitle = movie.title,
                secondaryLine = listOfNotNull("Movie", movie.genre, movie.rating?.let { "★ $it" })
                    .joinToString(" · "),
                imageUrl = movie.posterUrl,
                type = SearchResultType.VOD,
                vodItem = movie
            )
            entries += indexOf(result, movie.title, popularity, watched)
            movie.cast?.split(",")?.map { it.trim() }?.filter { it.length >= 2 }?.forEach { actor ->
                // Actor linkage handled in dedicated actor entries
            }
        }

        snapshot.series.forEach { show ->
            val result = SearchResultItem(
                id = "series-${show.id}",
                primaryTitle = show.name,
                secondaryLine = listOfNotNull("Series", show.genre).joinToString(" · "),
                imageUrl = show.coverUrl,
                type = SearchResultType.SERIES,
                seriesShow = show
            )
            entries += indexOf(result, show.name, 0, 0L)
        }

        snapshot.episodes.forEach { indexed ->
            val label = "S${indexed.seasonNumber}E${indexed.episode.episodeNumber ?: "?"} · ${indexed.series.name}"
            val result = SearchResultItem(
                id = "ep-${indexed.series.id}-${indexed.seasonNumber}-${indexed.episode.id}",
                primaryTitle = indexed.episode.title.ifBlank { indexed.series.name },
                secondaryLine = label,
                imageUrl = indexed.series.coverUrl,
                type = SearchResultType.EPISODE,
                seriesShow = indexed.series,
                seriesEpisode = indexed.episode,
                seriesSeasonNumber = indexed.seasonNumber
            )
            val combined = "${indexed.series.name} ${indexed.episode.title}"
            entries += indexOf(result, combined, 0, 0L)
        }

        snapshot.actors.forEach { actor ->
            val titles = actor.knownTitles.take(3).joinToString(", ")
            val result = SearchResultItem(
                id = "actor-${normalize(actor.name)}",
                primaryTitle = actor.name,
                secondaryLine = if (titles.isNotBlank()) "Actor · $titles" else "Actor",
                imageUrl = actor.posterUrl,
                type = SearchResultType.ACTOR,
                actorName = actor.name
            )
            entries += indexOf(result, actor.name, actor.knownTitles.size * 10, 0L)
        }

        snapshot.genres.forEach { genre ->
            val result = SearchResultItem(
                id = "genre-${normalize(genre.name)}",
                primaryTitle = genre.name,
                secondaryLine = genre.sourceLabel,
                imageUrl = null,
                type = SearchResultType.GENRE,
                genreName = genre.name
            )
            entries += indexOf(result, genre.name, 0, 0L)
        }

        val channelByEpg = snapshot.channelByEpgId
        snapshot.programs.forEach { prog ->
            val ch = channelByEpg[prog.channelEpgId]
            val isLive = now in prog.startTime..prog.endTime
            val result = SearchResultItem(
                id = "pg-${prog.id}",
                primaryTitle = prog.title,
                secondaryLine = "${ch?.name ?: prog.channelEpgId}${if (isLive) " · Live" else ""}",
                imageUrl = ch?.logoUrl,
                type = SearchResultType.PROGRAM,
                channelId = ch?.id,
                program = prog,
                isLive = isLive
            )
            entries += indexOf(result, prog.title, if (isLive) 50 else 0, 0L)
        }

        snapshot.lastWatchedTitles.forEachIndexed { index, title ->
            if (title.isBlank()) return@forEachIndexed
            val result = SearchResultItem(
                id = "hist-$index-${normalize(title)}",
                primaryTitle = title,
                secondaryLine = "Recently watched",
                imageUrl = null,
                type = SearchResultType.VOD
            )
            entries += indexOf(result, title, 80 - index, now)
        }

        return entries.distinctBy { it.result.id }
    }

    private fun indexOf(
        result: SearchResultItem,
        searchableText: String,
        popularity: Int,
        lastWatchedAt: Long
    ): IndexedEntry {
        val normalized = normalize(searchableText)
        return IndexedEntry(
            result = result,
            normalized = normalized,
            tokens = tokenize(searchableText).toSet(),
            popularity = popularity,
            lastWatchedAt = lastWatchedAt
        )
    }

    private fun buildTokenIndex(entries: List<IndexedEntry>): Map<String, List<Int>> {
        val map = mutableMapOf<String, MutableList<Int>>()
        entries.forEachIndexed { index, entry ->
            entry.tokens.forEach { token ->
                map.getOrPut(token) { mutableListOf() }.add(index)
            }
            val prefix = entry.normalized.take(3)
            if (prefix.isNotBlank()) {
                map.getOrPut(prefix) { mutableListOf() }.add(index)
            }
        }
        return map
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun tokenize(text: String): List<String> =
        normalize(text).split(" ").filter { it.length >= 2 }
}
