# Worst Case VOD Memory Model

Evidence from `refreshVodCatalogForPlaylist`, `refreshSeriesCatalogForPlaylist`, `loadVodStreamed`.

---

## Pipeline phases

### `loadVodStreamed(trigger)` — `IptvRepositoryImpl.kt:2310–2327`

| Step | Thread | Action |
|------|--------|--------|
| 1 | `vodRepositoryScope` + IO | `hydrateVodUiFromRoom` — count queries only L2330–2341 |
| 2 | IO | `warmLocalUiCache` if needed |
| 3 | IO | `scheduleDeferredVodCatalogRefresh` — delay from `StartupTierPolicy.deferredVodRefreshDelayMs` L2360 |

No network in `loadVodStreamed` itself — network is deferred.

---

### `refreshVodCatalogForPlaylist` — L3217–3401

#### Before parsing

| Item | Detail | File:Line |
|------|--------|-----------|
| Network | `fetchCatalogToTempFile(vodUrl, "vod_pl${playlist.id}")` | L3239–3242 |
| Storage | Temp file on device cache dir (via `RemoteTextFetcher`) | L149+ |
| `rawBytes` | Logged at L3246 — **on-disk size = download size** |
| Memory | `headPreview` only (small substring), not full body | L3250–3257 |
| Guard | `PlaybackDiagnostics.logMemory("before_vod_stream_parse")` | L3284 |

#### During parsing

| Item | Detail | File:Line |
|------|--------|-----------|
| Parser | `XtreamCatalogStreamParser.parseVodCatalogStream` | L3292–3332 |
| Batch size | **100** `DEFAULT_BATCH_SIZE` | `XtreamCatalogStreamParser.kt:16` |
| Per-batch objects | `List<VodItem>` max 100 → `toEntity` → `vodStreamDao.insertAll` | L3320 |
| Peak JVM objects | ≤100 `VodItem` + `VodStreamEntity` batch + Gson `JsonReader` stack | Streaming L11–13 comment |
| Skip guard | `isSystemLowOnMemory()` at 85% skips entire refresh L2459–2461 | Before network |

#### After parsing

| Item | Detail | File:Line |
|------|--------|-----------|
| Temp file | `deleteCatalogCacheFile(catalogFile)` in `finally` L3380–3381 | |
| DB | `deleteStaleByPlaylist` sync generation L3366 | |
| Retained caches | `cachedCategoryLookup` may rebuild via `refreshVodCategoriesForPlaylist` | L3368 |
| Memory log | `after_vod_stream_parse` L3338 | |
| UI | `bumpVodCatalogRevision`, count publish | L3367 |

---

### `refreshSeriesCatalogForPlaylist` — L3455+

Same pattern:
- `fetchCatalogToTempFile` L3476
- `parseSeriesCatalogStream` batch **100** L3519
- `seriesShowDao.insertAll` per batch L3544
- Temp file deleted in `finally`
- `before_series_stream_parse` / `after_series_stream_parse` L3509, L3562

---

## Catalog scale answers (heap vs disk)

Assumptions from code only:
- Parse holds **≤100 items** in heap at once.
- Full catalog JSON size = `fetchResult.rawBytes` on disk.

| Catalog size | Fits in JVM heap during parse? | Fits on device? | Code basis |
|--------------|-------------------------------|-----------------|------------|
| **100k movies** | **Yes** (batch 100) | Requires disk ≥ JSON size + SQLite growth | Streaming parser |
| **200k movies** | **Yes** (batch 100) | Disk/SQLite bound | Same |
| **500k movies** | **Yes** (batch 100) | **Disk/SQLite** likely fails before heap | Same |

**Heap is NOT the limiting factor for Xtream VOD refresh** — disk space, SQLite size, and **concurrent EPG/VOD workers** are.

---

## Contrast: paths that DO NOT stream

| Path | Full memory load | File:Line |
|------|------------------|-----------|
| M3U import | Entire playlist `String` | `importM3uChannels` L1711 |
| Xtream live import | `liveRaw` full `String` | L1795–1797 |
| `get_vod_categories` | `fetchDetailed().body` | L3415–3420 |

---

## Instrumentation

`StartupTrace` wraps:
- `refreshVodCatalogForPlaylist playlistId=...`
- `refreshSeriesCatalogForPlaylist playlistId=...`

Pair with `PlaybackDiagnostics.logMemory` and `MEMORY_PRESSURE` samples during refresh.
