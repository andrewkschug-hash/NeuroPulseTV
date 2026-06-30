package com.grid.tv.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.domain.model.Channel
import com.grid.tv.util.ChannelBrowserMetrics

internal class ChannelBrowserPagingSource(
    private val channelDao: ChannelDao,
    private val filterPlaylistId: Long,
    private val filterGroupName: String?,
    private val searchPrefix: String,
    private val onlyFavorites: Boolean,
    private val profileId: Long,
    private val favoriteGroupId: Long,
    private val matchSports: Boolean,
    private val sportsEpgIds: List<String>,
    private val mapPage: suspend (List<ChannelEntity>) -> List<Channel>
) : PagingSource<Int, Channel>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Channel> {
        val offset = params.key ?: 0
        val limit = params.loadSize
        return try {
            val loadStartedAt = System.currentTimeMillis()
            val entities = channelDao.channelsPageFiltered(
                filterPlaylistId = filterPlaylistId,
                filterGroupName = filterGroupName,
                searchPrefix = searchPrefix,
                onlyFavorites = onlyFavorites,
                profileId = profileId,
                favoriteGroupId = favoriteGroupId,
                matchSports = matchSports,
                sportsEpgIds = sportsEpgIds,
                limit = limit,
                offset = offset
            )
            val channels = mapPage(entities)
            val elapsedMs = System.currentTimeMillis() - loadStartedAt
            if (offset == 0) {
                ChannelBrowserMetrics.logInitialPageLoaded(
                    itemCount = channels.size,
                    elapsedMs = elapsedMs,
                    group = filterGroupName,
                    favoritesOnly = onlyFavorites,
                    matchSports = matchSports,
                    search = searchPrefix
                )
            } else {
                ChannelBrowserMetrics.logPageLoaded(
                    offset = offset,
                    itemCount = channels.size,
                    elapsedMs = elapsedMs
                )
            }
            LoadResult.Page(
                data = channels,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (entities.isEmpty() || entities.size < limit) null else offset + entities.size
            )
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Channel>): Int? {
        val anchor = state.anchorPosition ?: return null
        val closest = state.closestPageToPosition(anchor) ?: return null
        return closest.prevKey?.plus(closest.data.size)
            ?: closest.nextKey?.minus(closest.data.size)
    }
}
