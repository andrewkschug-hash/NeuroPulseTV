# TV Focus Architecture Report

Generated after the deterministic focus refactor (June 2026).

## Target architecture

| Rule | Implementation |
|------|----------------|
| One `FocusController` per screen | `TvFocusController<Zone>` interface; `VodHubFocusController` implements it |
| One D-pad handler per screen | `VodHubScreen` root `onPreviewKeyEvent` → `focusController.handleKey()` |
| One focus dispatcher per screen | `TvFocusDispatcher` composable; screen-specific wrappers `VodHubFocusDispatcher`, `HomeEpgFocusDispatcher` |
| No duplicate `requestFocus()` | Controllers only call `transitionToZone()`; dispatchers call `dispatchFocus()` |
| No duplicate `onPreviewKeyEvent` | Removed from `GuideNavDrawer`, `VodLibraryNavPanel`, genre/language panels, wall `LazyColumn` (VOD) |
| No directional `focusProperties` | Removed L/R from VOD content column, library panel, genre/language entry columns |
| Shared nav-rail targets | `GuideNavDrawerFocusTargets` hoisted to screen level |

Shared package: `com.grid.tv.ui.focus`

- `TvFocusController.kt` — controller contract
- `TvFocusDispatcher.kt` — single `LaunchedEffect` focus dispatcher
- `GuideNavDrawerFocusTargets.kt` — hoisted rail requesters

---

## Migrated screens

### VOD Hub (`VodHubScreen` + `VodHubFocusController`)

**Status: REFACTORED (reference implementation)**

| Concern | Owner |
|---------|--------|
| Zone state | `VodHubFocusUiState.focusZone` |
| D-pad | `VodHubFocusController.handleKey()` (single entry) |
| `requestFocus()` | `VodHubFocusDispatcher` only |
| Nav rail focus | `GuideNavDrawerFocusTargets` + dispatcher |
| Library row focus | `libraryRowFocusRequesters` (screen) + dispatcher |
| Content (wall/grid) | Virtual indices + `rootFocusRequester` host + dispatcher |

**Remaining `FocusRequester`s (VOD hub)**

| Requester | Role |
|-----------|------|
| `navDrawerFocusTargets.profileFocusRequester` | Profile avatar |
| `navDrawerFocusTargets.forIndex(i)` | Rail icons |
| `libraryRowFocusRequesters[0..3]` | Library rows (Home/Movies/Series/Languages) |
| `rootFocusRequester` | Content zone virtual-focus host |
| `browseGridFocusRequester` | Inline search results anchor |
| `browseEmptyStateFocusRequester` | Empty browse grid retry |
| `genrePanelFocusRequester` | Genre sub-panel entry |
| `languageSubmenuFocusRequester` | Language sub-panel entry |
| `inlineSearchFocusRequester` | Search field |
| `movieWatchFocusRequester` | Movie detail overlay |

**Removed (unused / duplicate)**

- `contentFocusRequester`
- `filterPanelFocusRequester`
- `navDrawerFocusRequester` (replaced by `GuideNavDrawerFocusTargets`)
- Wall `firstItemFocusRequester` / `sidebarFocusRequester` params on `NetflixContentWallRow`
- `interceptLeadingEdgeLeft` on browse grid (keys handled only in controller)
- Component-level `LaunchedEffect` focus in `GuideNavDrawer`, `VodLibraryNavPanel`

**Directional transitions (single path each)**

| Transition | Path |
|------------|------|
| Content → Library | `handleKey` → `handleContentKey` → `handleWallContentKey` Left@col0 / `handleBrowseGridKey` Left@leading → `enterLibraryNavFromContent` → `openLibraryNavPanel` → zone `FILTER_PANEL` → dispatcher → `libraryRowFocusRequesters[i]` |
| Library → Content | `handleKey` → `handleLibraryNavPanelKey` Right → `routeLibraryNavRight` → `returnToContentFromLibraryNav` → zone `CONTENT` + restore indices → dispatcher → `rootFocusRequester` |
| Library → Drawer | `handleKey` → `handleLibraryNavPanelKey` Left (or Up@0) → `openNavDrawerFromLibraryNav` → zone `NAV_DRAWER` → dispatcher → `navDrawerFocusTargets.forIndex(Search)` |
| Drawer → Library | `handleKey` → `handleNavDrawerKey` Right → `openLibraryNavPanel` → zone `FILTER_PANEL` → dispatcher → library row |

---

### Live EPG (`HomeEpgScreen` + `HomeEpgGuideController`)

**Status: MIGRATED (canonical VOD pattern)**

| Concern | Owner |
|---------|--------|
| Zone state | `HomeEpgUiState.focusZone` |
| `requestFocus()` (zones) | `HomeEpgFocusDispatcher` only |
| Nav rail focus | `GuideNavDrawerFocusTargets` + dispatcher |
| Channel group rows | `GuideChannelGroupsFocusRegistry` (screen-owned) + dispatcher |
| D-pad | `TvScreenFocusRoot` → `HomeEpgGuideController.handleKey()` |

**Focus zones:** `NAV_DRAWER`, `CHANNEL_GROUPS`, `CONTINUE_WATCHING`, `PREVIEW`, `GRID`

**Tests:** `HomeEpgFocusNavigationTest`

---

### Search overlay (`SearchOverlay`)

**Status: MIGRATED (canonical VOD pattern)**

| Concern | Owner |
|---------|--------|
| Zone state | `SearchFocusUiState` |
| `requestFocus()` | `SearchFocusDispatcher` only |
| D-pad | `TvScreenFocusRoot` → `SearchFocusController.handleKey()` |

**Focus zones:** `FIELD`, `MIC`, `RECENT`, `RESULTS`

**Tests:** `SearchFocusNavigationTest`

---

## Pending migration (inventory)

### Settings (`SettingsScreen`)

- **Controller:** implicit via `SettingsFocusPanel` enum + local handlers
- **Dispatcher:** `LaunchedEffect(focusPanel, listFocusIndex, …)` in screen
- **Zones:** `TOP_BAR`, `LIST`, `DETAIL`
- **Duplicate risk:** `SettingsComponents` dialog `LaunchedEffect`; `SettingsFocusNavigation` pill `requestFocus()`

### Recordings (`RecordingsScreen`)

- **Zones:** `RecFocusZone` (TOP_BAR, LIST, DETAIL)
- **Dispatcher:** screen `LaunchedEffect(focusZone)` — pattern ready for `TvFocusDispatcher` wrapper
- **D-pad:** per-pane `onPreviewKeyEvent`

### Series detail (`SeriesBrowserScreen` / `SeriesDetailPane`)

- **Zones:** `SeriesDetailFocusZone` (HEADER, SEASONS, EPISODES)
- **Dispatcher:** `LaunchedEffect(focusZone, …)` in detail pane
- **D-pad:** detail `onPreviewKeyEvent`

### Player (`PlayerScreen`)

- **Dispatcher:** `LaunchedEffect` for side menu vs player
- **D-pad:** root + side menu `onPreviewKeyEvent`

### Other screens with focus

| Screen | Dispatcher | Controller | Notes |
|--------|------------|------------|-------|
| `SplitViewScreen` | `LaunchedEffect` ×2 | None | Root + pane menu |
| `MultiViewScreen` | `LaunchedEffect(Unit)` | None | Single root |
| `ProfilePickerScreen` | `LaunchedEffect` | None | |
| `ManageProfilesOverlay` | `LaunchedEffect` ×2 | None | Heavy `focusProperties` graph |
| `LoginScreen` | `LaunchedEffect` | None | |
| `GuideGroupsScreen` | `LaunchedEffect` ×2 | None | Own row map |
| `GuideGroupPickerDialog` | `LaunchedEffect` | None | Modal |
| `DirectPlayerScreen` | None | None | Keys only, no requesters |

### Overlays / components (not screens — exempt from one-dispatcher rule)

`MovieDetailOverlay`, `EpisodeDetailOverlay`, `TvTextInputDialog`, `GridModal`, `VodLanguagePreferenceDialog`, `RecordingsComponents` delete dialog, `StorageLocationPicker`, `GoogleSignInBlock`, `VodHubHeroIsland`

Each should eventually use a modal-scoped dispatcher or parent screen dispatcher when open.

---

## Standard zone naming (target)

| Generic | VOD | EPG | Settings | Recordings |
|---------|-----|-----|----------|--------------|
| `NAV_DRAWER` | `NAV_DRAWER` | `NAV_DRAWER` | `TOP_BAR` | `TOP_BAR` |
| `SIDEBAR_PANEL` | `FILTER_PANEL` | `CHANNEL_GROUPS` | `LIST` | `LIST` |
| `SUB_PANEL` | `GENRE_PANEL` / `LANGUAGE_SUBMENU` | — | `DETAIL` | `DETAIL` |
| `CONTENT` | `CONTENT` / `HERO` | `GRID` / `PREVIEW` / `CONTINUE_WATCHING` | — | — |

---

## All `requestFocus()` call sites (post-refactor)

Only **screen dispatchers** and **modal/overlay** components should call `requestFocusSafelyAfterLayout`. Controllers must not.

**Screen dispatchers (canonical)**

- `com.grid.tv.ui.focus.TvFocusDispatcher` → `dispatchFocus()`
- `VodHubFocusDispatcher`
- `HomeEpgFocusDispatcher`
- `SearchFocusDispatcher`

**Still calling `requestFocus` outside dispatchers (migrate)**

- `VodContentFilterPanel` `LaunchedEffect` (unused on VOD hub)
- `SettingsScreen`, `RecordingsScreen`, `SeriesBrowserScreen`, `PlayerScreen`, `SplitViewScreen`, `MultiViewScreen`, `ProfilePickerScreen`, `ManageProfilesOverlay`, `LoginScreen`, `GuideGroupsScreen`
- Various overlays listed above

---

## Verification checklist (VOD Hub)

- [ ] Home tab: Content Left → Library Home highlighted
- [ ] Library Right → content at last wall position
- [ ] Library Left → Search on rail
- [ ] Search Right → Library Home
- [ ] Movies/Series grid: leading-edge Left → Library
- [ ] No focus fights when switching zones quickly
- [ ] Unit: `VodHubFocusNavigationTest` passes

---

## Next steps

1. Complete EPG migration (single `handleKey`, hoist channel-group rows, remove section `onPreviewKeyEvent` duplicates).
2. Wrap `SettingsScreen` / `RecordingsScreen` with `TvFocusDispatcher` + implement `TvFocusController`.
3. Refactor `SearchOverlay` to one dispatcher and one key handler.
4. Add `HomeEpgGuideController : TvFocusController<EpgFocusZone>`.
5. Add lint//detekt rule or test grepping for `requestFocusSafelyAfterLayout` outside `*FocusDispatch*` and overlay package.
