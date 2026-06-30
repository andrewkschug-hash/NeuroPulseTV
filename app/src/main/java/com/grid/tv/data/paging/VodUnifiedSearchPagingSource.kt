package com.grid.tv.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.grid.tv.data.db.dao.SeriesShowDao
import com.grid.tv.data.db.dao.VodSearchDao
import com.grid.tv.data.db.dao.VodStreamDao
import com.grid.tv.data.db.mapper.toDomain
import com.grid.tv.domain.model.VodSearchEntry

class VodUnifiedSearchPagingSource(
    private val vodSearchDao: VodSearchDao,
    private val vodStreamDao: VodStreamDao,
    private val seriesShowDao: SeriesShowDao,
    private val searchPrefix: String,
    private val playlistScoped: Boolean,
    private val playlistId: Long,
    private val matchAll: Boolean,
    private val categoryIds: List<String>
) : PagingSource<Int, VodSearchEntry>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, VodSearchEntry> {
        return try {
            val offset = params.key ?: 0
            val hits = vodSearchDao.unifiedSearchHits(
                searchPrefix = searchPrefix,
                playlistScoped = playlistScoped,
                playlistId = playlistId,
                matchAll = matchAll,
                categoryIds = categoryIds,
                limit = params.loadSize,
                offset = offset
            )
            if (hits.isEmpty()) {
                return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }
            val movieRowIds = hits.filter { it.contentType == CONTENT_MOVIE }.map { it.rowId }
            val seriesRowIds = hits.filter { it.contentType == CONTENT_SERIES }.map { it.rowId }
            val moviesByRowId = if (movieRowIds.isEmpty()) {
                emptyMap()
            } else {
                vodStreamDao.byRowIds(movieRowIds).associateBy { it.rowId }
            }
            val seriesByRowId = if (seriesRowIds.isEmpty()) {
                emptyMap()
            } else {
                seriesShowDao.byRowIds(seriesRowIds).associateBy { it.rowId }
            }
            val entries = hits.mapNotNull { hit ->
                when (hit.contentType) {
                    CONTENT_MOVIE -> moviesByRowId[hit.rowId]?.let { VodSearchEntry.Movie(it.toDomain()) }
                    CONTENT_SERIES -> seriesByRowId[hit.rowId]?.let { VodSearchEntry.Series(it.toDomain()) }
                    else -> null
                }
            }
            val nextKey = if (hits.size < params.loadSize) null else offset + hits.size
            val prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0)
            LoadResult.Page(data = entries, prevKey = prevKey, nextKey = nextKey)
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, VodSearchEntry>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(state.config.pageSize)
        }

    companion object {
        private const val CONTENT_MOVIE = "movie"
        private const val CONTENT_SERIES = "series"
    }
}
