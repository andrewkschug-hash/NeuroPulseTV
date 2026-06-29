package com.grid.tv.feature.vod

import com.grid.tv.domain.model.SeriesCatalogHydrationState
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesCatalogHydrationStateTest {

    @Test
    fun resolveSeriesHydrationState_diskRowsWin() {
        assertEquals(
            SeriesCatalogHydrationState.POPULATED,
            resolveSeriesHydrationState(SeriesCatalogHydrationState.EMPTY, seriesCountOnDisk = 1),
        )
    }

    @Test
    fun resolveSeriesHydrationState_preservesPersistedWhenNoRows() {
        assertEquals(
            SeriesCatalogHydrationState.EMPTY,
            resolveSeriesHydrationState(SeriesCatalogHydrationState.EMPTY, seriesCountOnDisk = 0),
        )
        assertEquals(
            SeriesCatalogHydrationState.NEVER_FETCHED,
            resolveSeriesHydrationState(SeriesCatalogHydrationState.NEVER_FETCHED, seriesCountOnDisk = 0),
        )
    }
}
