# Channel Groups ANR Fix Report

**Date:** 2026-06-18  
**ANR:** `Input dispatching timed out` — KeyEvent blocked >5002ms on main thread  
**Root cause (pre-fix):** `GuideCategoryProcessor.organizeGroups()` O(N²) on Compose main thread, invoked twice per recomposition, triggered up to 4× per metadata burst.

---

## P0-G — Why `organizeGroups` was called twice

| Call site | File | Lines | Purpose |
|-----------|------|-------|---------|
| `buildGuideGroupCategories()` | `HomeEpgScreen.kt` | 241-246 | `guideGroupCategories` for filter menu + controller |
| `GuideCategoryProcessor.organizeGroups()` | `HomeEpgScreen.kt` | 248-258 | `organizedGuideGroups` for `GuideGroupsScreen` |

`buildGuideGroupCategories()` (`GuideGroupTree.kt` L44-53) **delegates to the same** `organizeGroups()` and returns only `.flatCategories`.

**No semantic difference** — pure duplicate CPU. One `organizedGuideGroups` StateFlow now feeds both `GuideGroupsScreen` and `guideGroupCategories = organized.flatCategories`.

---

## Changes implemented

### P0-F — O(N) dedup (`GuideCategoryProcessor.kt`)

**Before:** `dedupedKeys.keys.firstOrNull { ... }` per group → **O(N²)**  
**After:** `HashMap<String, String>` keyed by `name.lowercase()` → **O(N)**

### P0-E — Off main thread (`HomeEpgViewModel.kt`)

- `organizedGuideGroups: StateFlow<OrganizedGuideGroups>` computed via `withContext(Dispatchers.Default)`
- Compose only `collectAsStateWithLifecycle()` — no `remember { organizeGroups() }`

### P0-H — Coalesced metadata (`GuideGroupMetadata.kt`, `IptvRepositoryImpl.kt`)

- New `observeGroupMetadata()` — single `observeGroupChannelCounts` emission → `groups` + `counts`
- `groupMetadata` StateFlow debounced once
- `channelGroups` / `groupChannelCounts` derived via `.map` (same upstream, no double organize)

### P0-G — Single organize per update

- Removed both `remember` blocks from `HomeEpgScreen.kt`
- One `flatMapLatest` in ViewModel per `(metadata, hideAdult)` change

### P1 — Lazy `FocusRequester` (`GuideGroupsScreen.kt`)

**Before:** `List(visibleRows.size) { FocusRequester() }` — all rows upfront  
**After:** `mutableStateMapOf<Int, FocusRequester>()` + `getOrPut` on focus/navigation/compose

### Instrumentation (`GroupsTrace.kt`)

```bash
adb logcat -s GROUPS_TRACE
```

Logs:
- `operation=organizeGroups` — `rawGroupCount`, `flatCategoryCount`, `durationMs`, `thread`
- `operation=visibleRows` — `visibleRowCount`, `focusRequesterCount`, `thread`

---

## Files modified

| File | Change |
|------|--------|
| `feature/guide/GuideCategoryProcessor.kt` | O(N) dedup; `OrganizedGuideGroups.EMPTY` |
| `feature/guide/GuideGroupMetadata.kt` | **New** — coalesced metadata type |
| `feature/guide/GroupsTrace.kt` | **New** — `GROUPS_TRACE` logging |
| `domain/repository/IptvRepository.kt` | `observeGroupMetadata()` |
| `data/repository/IptvRepositoryImpl.kt` | Single-query metadata; `groups()`/`counts()` delegate |
| `ui/viewmodel/HomeEpgViewModel.kt` | `groupMetadata`, `organizedGuideGroups` on `Default` |
| `ui/screen/HomeEpgScreen.kt` | Remove duplicate `remember` organize |
| `ui/screen/GuideGroupsScreen.kt` | Lazy `FocusRequester` map |

---

## Complexity reduction

| Phase | Before | After |
|-------|--------|-------|
| Dedup | O(N²) | **O(N)** |
| organize per metadata burst | Up to **4×** (2 flows × 2 remember) | **1×** |
| organize thread | **Main** (Compose) | **Default** |
| FocusRequester alloc | **N** at compose | **~visible + navigated** |

---

## Duration estimates (N = raw provider groups)

| N | Before (main thread) | After (`Default` + O(N)) |
|---|----------------------|---------------------------|
| 1,000 | ~400ms–2s × 2–4 calls | **~20–80ms** × 1 |
| 5,000 | **5–15s+** (ANR) | **~50–200ms** |
| 10,000 | **20s+** (ANR) | **~100–400ms** |

*Estimates from algorithm analysis; validate on device with `GROUPS_TRACE`.*

### Before (typical ANR sequence)

```
GROUP BY channels (IO, ~200–800ms)
  → channelGroups emit → organizeGroups ×2 on Main (~3–8s)
  → groupChannelCounts emit → organizeGroups ×2 on Main (~3–8s)
  → KeyEvent timeout at 5002ms
```

### After (expected)

```
GROUP BY channels (IO, once)
  → groupMetadata emit (debounced)
  → organizeGroups ×1 on Default (~50–200ms for N=5k)
  → Compose collects prebuilt StateFlow (~1ms)
  → FocusRequester: allocate on demand only
```

---

## Verification

```bash
adb logcat -v time -s GROUPS_TRACE Choreographer
```

1. Open guide → wait for metadata (4–10s after bootstrap)
2. Open **Channel Groups**
3. Expect `organizeGroups` on thread `DefaultDispatcher-worker-*`, `durationMs` < 500 for typical catalogs
4. Expand a large category — `focusRequesterCount` should stay **< 50** while scrolling, not equal `visibleRowCount` until navigated

---

## Not in scope (future)

- Move `buildVisibleGuideGroupRows` off main when expanded category has 5k+ children
- `shareIn` on `observeGroupMetadata()` for Settings screen dual subscription
- SQL index on `(playlistId, groupName)` if GROUP BY remains slow on IO
