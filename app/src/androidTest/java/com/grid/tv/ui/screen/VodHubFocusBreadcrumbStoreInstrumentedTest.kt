package com.grid.tv.ui.screen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grid.tv.domain.model.VodContentFilter
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VodHubFocusBreadcrumbStoreInstrumentedTest {

    private lateinit var context: Context
    private lateinit var store: VodHubFocusBreadcrumbStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vod_hub_focus_breadcrumbs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = VodHubFocusBreadcrumbStore(context)
    }

    @Test
    fun writeAndRead_restoresPlaylistSnapshot() {
        val ui = VodHubFocusUiState()
        ui.filterFocusIndex = 1
        ui.genreFocusIndex = 2
        ui.rememberGenreFor(VodContentFilter.MOVIES)
        ui.rememberGridFor(
            VodContentFilter.MOVIES,
            VodGridFocusMemory(itemIndex = 15, contentKey = "9_15", scrollIndex = 12, scrollOffset = 40)
        )

        val snapshot = snapshotVodHubFocus(ui, VodContentFilter.MOVIES, VodFocusZone.CONTENT)
        store.write(playlistId = 42L, snapshot)

        val loaded = store.read(42L)
        requireNotNull(loaded)
        assertEquals(VodContentFilter.MOVIES.name, loaded.contentFilter)
        assertEquals(VodFocusZone.CONTENT.name, loaded.focusZone)
        assertEquals(15, loaded.movies.grid.itemIndex)
        assertEquals("9_15", loaded.movies.grid.contentKey)
        assertEquals(2, loaded.movies.genreIndex)
    }
}
