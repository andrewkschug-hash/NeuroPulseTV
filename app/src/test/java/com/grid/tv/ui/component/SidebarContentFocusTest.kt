package com.grid.tv.ui.component

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarContentFocusTest {

    @Test
    fun leadingColumn_detectsFirstColumnOnly() {
        assertTrue(SidebarContentFocus.isLeadingGridColumn(0, 4))
        assertTrue(SidebarContentFocus.isLeadingGridColumn(4, 4))
        assertFalse(SidebarContentFocus.isLeadingGridColumn(1, 4))
    }

    @Test
    fun sidebarRight_returnsToContent() {
        assertEquals(
            SidebarContentFocus.SidebarHorizontalResult.ReturnToContent,
            SidebarContentFocus.sidebarHorizontalResult(Key.DirectionRight, allowLeftToRail = false)
        )
    }

    @Test
    fun sidebarLeft_opensRailWhenAllowed() {
        assertEquals(
            SidebarContentFocus.SidebarHorizontalResult.OpenRail,
            SidebarContentFocus.sidebarHorizontalResult(Key.DirectionLeft, allowLeftToRail = true)
        )
        assertEquals(
            SidebarContentFocus.SidebarHorizontalResult.None,
            SidebarContentFocus.sidebarHorizontalResult(Key.DirectionLeft, allowLeftToRail = false)
        )
    }

    @Test
    fun contentLeadingLeft_entersSidebarWhenPresent() {
        assertTrue(SidebarContentFocus.shouldEnterSidebarFromContent(Key.DirectionLeft, atLeadingEdge = true, hasSidebar = true))
        assertFalse(SidebarContentFocus.shouldEnterSidebarFromContent(Key.DirectionLeft, atLeadingEdge = false, hasSidebar = true))
        assertFalse(SidebarContentFocus.shouldEnterSidebarFromContent(Key.DirectionLeft, atLeadingEdge = true, hasSidebar = false))
    }

    @Test
    fun headerDown_entersContent() {
        assertTrue(SidebarContentFocus.shouldEnterContentFromHeader(Key.DirectionDown))
        assertFalse(SidebarContentFocus.shouldEnterContentFromHeader(Key.DirectionRight))
    }
}
