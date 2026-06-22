package com.grid.tv.data.cache

import android.content.Context
import android.util.Log
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodItem
import com.grid.tv.data.io.DiskIoSerialExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists VOD movie, category, and series lists per playlist so the catalog survives app restarts.
 * Uses JSON files under [Context.getCacheDir]/vod_catalog/.
 */
@Singleton
class VodCatalogDiskCache @Inject constructor(
    @ApplicationContext context: Context,
    private val diskIoSerialExecutor: DiskIoSerialExecutor
) {
    companion object {
        private const val TAG = "VodCatalogPipeline"
        private const val DIR_NAME = "vod_catalog"
        private const val KEY_SAVED_AT = "savedAtMs"
        private const val KEY_ITEMS = "items"
    }

    private val cacheDir: File = File(context.cacheDir, DIR_NAME).also { it.mkdirs() }

    data class PlaylistSnapshot(
        val savedAtMs: Long,
        val movies: List<VodItem>,
        val categories: List<VodCategory>,
        val series: List<SeriesShow>
    )

    suspend fun load(playlistId: Long): PlaylistSnapshot? = withContext(diskIoSerialExecutor.dispatcher) {
        loadInternal(playlistId)
    }

    private fun loadInternal(playlistId: Long): PlaylistSnapshot? {
        val moviesFile = fileFor(playlistId, "movies")
        val categoriesFile = fileFor(playlistId, "categories")
        val seriesFile = fileFor(playlistId, "series")
        if (!moviesFile.exists() && !seriesFile.exists()) return null

        return runCatching {
            val moviesPayload = moviesFile.takeIf { it.exists() }?.readText()?.let(::parseMoviesPayload)
            val categoriesPayload = categoriesFile.takeIf { it.exists() }?.readText()?.let(::parseCategoriesPayload)
            val seriesPayload = seriesFile.takeIf { it.exists() }?.readText()?.let(::parseSeriesPayload)
            val savedAtMs = listOfNotNull(
                moviesPayload?.first,
                categoriesPayload?.first,
                seriesPayload?.first
            ).maxOrNull() ?: return null

            PlaylistSnapshot(
                savedAtMs = savedAtMs,
                movies = moviesPayload?.second.orEmpty(),
                categories = categoriesPayload?.second.orEmpty(),
                series = seriesPayload?.second.orEmpty()
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to load VOD disk cache playlist=$playlistId: ${error.message}", error)
        }.getOrNull()
    }

    suspend fun saveMovies(playlistId: Long, movies: List<VodItem>, savedAtMs: Long = System.currentTimeMillis()) =
        withContext(diskIoSerialExecutor.dispatcher) {
            if (movies.isEmpty()) return@withContext
            writePayload(fileFor(playlistId, "movies"), savedAtMs, movies.map(::vodItemToJson))
            Log.i(TAG, "VOD disk cache saved movies playlist=$playlistId count=${movies.size} savedAt=$savedAtMs")
        }

    suspend fun saveCategories(
        playlistId: Long,
        categories: List<VodCategory>,
        savedAtMs: Long = System.currentTimeMillis()
    ) = withContext(diskIoSerialExecutor.dispatcher) {
        if (categories.isEmpty()) return@withContext
        writePayload(fileFor(playlistId, "categories"), savedAtMs, categories.map(::categoryToJson))
        Log.i(TAG, "VOD disk cache saved categories playlist=$playlistId count=${categories.size}")
    }

    suspend fun saveSeries(
        playlistId: Long,
        series: List<SeriesShow>,
        savedAtMs: Long = System.currentTimeMillis()
    ) = withContext(diskIoSerialExecutor.dispatcher) {
        if (series.isEmpty()) return@withContext
        writePayload(fileFor(playlistId, "series"), savedAtMs, series.map(::seriesToJson))
        Log.i(TAG, "VOD disk cache saved series playlist=$playlistId count=${series.size}")
    }

    suspend fun clear(playlistId: Long) = withContext(diskIoSerialExecutor.dispatcher) {
        listOf("movies", "categories", "series").forEach { kind ->
            fileFor(playlistId, kind).delete()
        }
    }

    suspend fun clearAll() = withContext(diskIoSerialExecutor.dispatcher) {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(playlistId: Long, kind: String): File =
        File(cacheDir, "playlist_${playlistId}_$kind.json")

    private fun writePayload(file: File, savedAtMs: Long, items: List<JSONObject>) {
        val root = JSONObject()
            .put(KEY_SAVED_AT, savedAtMs)
            .put(KEY_ITEMS, JSONArray(items))
        file.writeText(root.toString())
    }

    private fun parseMoviesPayload(raw: String): Pair<Long, List<VodItem>>? = parsePayload(raw) { json ->
        VodItem(
            id = json.optLong("id"),
            title = json.optString("title"),
            streamId = json.optLong("streamId"),
            streamUrl = json.optString("streamUrl"),
            posterUrl = json.optString("posterUrl").takeIf { it.isNotBlank() },
            plot = json.optString("plot").takeIf { it.isNotBlank() },
            cast = json.optString("cast").takeIf { it.isNotBlank() },
            director = json.optString("director").takeIf { it.isNotBlank() },
            genre = json.optString("genre").takeIf { it.isNotBlank() },
            rating = json.optString("rating").takeIf { it.isNotBlank() },
            duration = json.optString("duration").takeIf { it.isNotBlank() },
            categoryId = json.optString("categoryId").takeIf { it.isNotBlank() },
            addedEpochSec = json.optLong("addedEpochSec").takeIf { it > 0L },
            playlistId = json.optLong("playlistId")
        )
    }

    private fun parseCategoriesPayload(raw: String): Pair<Long, List<VodCategory>>? = parsePayload(raw) { json ->
        VodCategory(
            id = json.optString("id"),
            name = json.optString("name"),
            playlistId = json.optLong("playlistId")
        )
    }

    private fun parseSeriesPayload(raw: String): Pair<Long, List<SeriesShow>>? = parsePayload(raw) { json ->
        SeriesShow(
            id = json.optLong("id"),
            name = json.optString("name"),
            coverUrl = json.optString("coverUrl").takeIf { it.isNotBlank() },
            categoryId = json.optString("categoryId").takeIf { it.isNotBlank() },
            genre = json.optString("genre").takeIf { it.isNotBlank() },
            playlistId = json.optLong("playlistId")
        )
    }

    private fun <T> parsePayload(raw: String, mapItem: (JSONObject) -> T): Pair<Long, List<T>>? {
        val root = JSONObject(raw)
        val savedAt = root.optLong(KEY_SAVED_AT)
        if (savedAt <= 0L) return null
        val array = root.optJSONArray(KEY_ITEMS) ?: return savedAt to emptyList()
        val items = buildList {
            for (index in 0 until array.length()) {
                add(mapItem(array.getJSONObject(index)))
            }
        }
        return savedAt to items
    }

    private fun vodItemToJson(item: VodItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("title", item.title)
        .put("streamId", item.streamId)
        .put("streamUrl", item.streamUrl)
        .put("posterUrl", item.posterUrl.orEmpty())
        .put("plot", item.plot.orEmpty())
        .put("cast", item.cast.orEmpty())
        .put("director", item.director.orEmpty())
        .put("genre", item.genre.orEmpty())
        .put("rating", item.rating.orEmpty())
        .put("duration", item.duration.orEmpty())
        .put("categoryId", item.categoryId.orEmpty())
        .put("addedEpochSec", item.addedEpochSec ?: 0L)
        .put("playlistId", item.playlistId)

    private fun categoryToJson(category: VodCategory): JSONObject = JSONObject()
        .put("id", category.id)
        .put("name", category.name)
        .put("playlistId", category.playlistId)

    private fun seriesToJson(show: SeriesShow): JSONObject = JSONObject()
        .put("id", show.id)
        .put("name", show.name)
        .put("coverUrl", show.coverUrl.orEmpty())
        .put("categoryId", show.categoryId.orEmpty())
        .put("genre", show.genre.orEmpty())
        .put("playlistId", show.playlistId)
}
