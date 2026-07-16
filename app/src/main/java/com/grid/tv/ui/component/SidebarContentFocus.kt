package com.grid.tv.ui.component

import androidx.compose.ui.input.key.Key

/**
 * Shared D-pad contract for layouts with an optional header, optional icon rail, a left sidebar,
 * and a main content region.
 *
 * ## Rules (applied consistently on VOD Hub and Live EPG)
 *
 * **Header / tab bar**
 * - Left / Right: move among header controls (handled per screen).
 * - Down: enter **main content** (grid / channel list / wall) — never the sidebar.
 *
 * **Main content**
 * - Left at the **leading edge** (first column or channel column): move focus one lane left
 *   (EPG: icon rail; VOD: genre sidebar). Pure focus move — never open an overlay.
 * - Right / Up / Down: navigate within content (handled per screen).
 * - Up from the first row may return to the header (VOD browse grid).
 *
 * **Sidebar panels** (VOD genres, EPG channel-groups overlay, …)
 * - Up / Down: move among sidebar items (handled per screen).
 * - **Right: return to main content** at the last-focused position — no extra activation.
 * - Left: optional step to the icon rail when present (EPG).
 * - Enter: activate / commit the highlighted sidebar item (handled per screen).
 *
 * **EPG icon rail**
 * - Receiving focus must never open Channel Groups (or any overlay).
 * - Overlays open only on explicit OK/Select (or an intentional Right-into-panel contract).
 *
 * Focus ≠ activate. Do not open panels from onFocus handlers.
 */
object SidebarContentFocus {

    /** True when [index] is in the first column of a fixed-column grid. */
    fun isLeadingGridColumn(index: Int, columnCount: Int): Boolean =
        columnCount > 0 && index % columnCount == 0

    /** True when [index] is in the first row of a fixed-column grid. */
    fun isFirstGridRow(index: Int, columnCount: Int): Boolean =
        columnCount > 0 && index < columnCount

    enum class SidebarHorizontalResult {
        /** Sidebar → content: restore last content focus only. */
        ReturnToContent,
        /** Sidebar → icon rail (EPG). */
        OpenRail,
        None,
    }

    /**
     * Horizontal keys while focus is in the sidebar panel.
     * @param allowLeftToRail When true, Left opens the nav icon rail instead of doing nothing.
     */
    fun sidebarHorizontalResult(key: Key, allowLeftToRail: Boolean): SidebarHorizontalResult? =
        when (key) {
            Key.DirectionRight -> SidebarHorizontalResult.ReturnToContent
            Key.DirectionLeft -> if (allowLeftToRail) {
                SidebarHorizontalResult.OpenRail
            } else {
                SidebarHorizontalResult.None
            }
            else -> null
        }

    /** Left from content at the leading edge should enter the sidebar when one exists. */
    fun shouldEnterSidebarFromContent(key: Key, atLeadingEdge: Boolean, hasSidebar: Boolean): Boolean =
        key == Key.DirectionLeft && atLeadingEdge && hasSidebar

    /** Down from the header/tab bar should enter main content, not the sidebar. */
    fun shouldEnterContentFromHeader(key: Key): Boolean = key == Key.DirectionDown
}
