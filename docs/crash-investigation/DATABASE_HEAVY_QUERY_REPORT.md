# DATABASE_HEAVY_QUERY_REPORT.md

## Queries returning large result sets (evidence from DAO SQL)

### Unbounded or full-table reads

| DAO | Method | SQL pattern | File:Line | Typical rows | Startup caller |
|-----|--------|-------------|-----------|--------------|----------------|
| `EpgLearnedMappingDao` | `all()` | `SELECT * ... ORDER BY learnedAt DESC` **no LIMIT** | `EpgLearnedMappingDao.kt:20` | All mappings | `rebuildEpgLinkResolver` L1385 |
| `EpgSourceChannelDao` | `bySource(source)` | `SELECT * WHERE source = :source` | L11 | All channels per source | Resolver L1379 |
| `EpgSourceChannelDao` | `all()` | `SELECT *` | L26 | All sources | Legacy paths |
| `StreamHealthDao` | `observeAll()` | `SELECT * FROM stream_health` | L27 | All health rows | `channels()` combine L782 |
| `ChannelScanDao` | `all()` | `SELECT * FROM channel_scan` | L18 | All scan rows | Scanner hydrate |
| `StreamFailoverStatsDao` | `observeAll()` | `SELECT *` | L28 | All stats | — |
| `PlaylistDao` | `all()` / `observeAll` | `SELECT * FROM playlists` | L13, L25 | All playlists | `refreshEpgNow` L2069, `SetupGate` |
| `VodCategoryDao` | `all()` | `SELECT * FROM vod_categories` | L21–24 | All categories | `buildCategoryNameLookupUncached` L2835 |
| `SeriesCategoryDao` | `all()` | `SELECT * FROM series_categories` | L21–24 | All categories | Lookup L2838 |
| `ProfileWatchHistoryDao` | `observe` (no limit variant) | `SELECT * WHERE profileId` | L37 | All history | Home flows |
| `ProgramDao` | `distinctChannelEpgIdsForPlaylist` | DISTINCT ids | — | Per-playlist EPG ids | Resolver L1382 |

### Bounded but large at scale

| DAO | Method | LIMIT | File | Startup |
|-----|--------|-------|------|---------|
| `ChannelDao` | `channelsPage` | **200** (`CHANNEL_PAGE_SIZE`) | `IptvRepositoryImpl.kt:226` | `warmLocalUiCache` L2427, `reloadChannels` |
| `ProgramDao` | `loadWindow` | Time window × channel id IN list | `ProgramDao.kt:23+` | `loadWindow` via `programsWindowForChannels` |
| `TitleEnrichmentDao` | `topByPopularity` | **120** | L22 | `UnifiedSearchEngine` rebuild |
| `VodStreamDao` | `countTotal` | scalar | — | `warmLocalUiCache` |
| `SeriesShowDao` | `countTotal` | scalar | — | warm |

### Duplicate queries at startup

| Query | Occurrences | Call chain |
|-------|-------------|------------|
| `channelDao.channelsPage(offset=0, limit=200)` | **2×** | `warmLocalUiCache` L2427 + `HomeEpgViewModel.reloadChannels` ~L967 |
| `profileSettingsDao.get` / `loadSettings` | **2×** | `MainActivity` theme L81 + `bootstrapGuideFromSettings` L505 |
| `vodStreamDao.countTotal` + `seriesShowDao.countTotal` | **3×** | `init` prefs L315, `warmLocalUiCache` L2411, `hydrateVodUiFromRoom` L2330 |
| `playlistDao.observeAll` | **2+ subscribers** | `PlaylistContext` + `HomeEpgViewModel.playlists` |

### N+1 patterns

| Pattern | Location | Detail |
|---------|----------|--------|
| `programsWindowForChannels` per playlist group | `IptvRepositoryImpl.kt` | Groups channels by `playlistId`; resolver built per playlist |
| `channels()` combine | L772–793 | Re-maps all channel rows + all playlists + all stream health on **each emission** |
| Genre hint loops | `buildCategoryNameLookupUncached` L2841–2861 | Two DAO queries + map merge |

---

## `SELECT *` inventory

All DAOs using `SELECT *` are listed in grep output under `app/src/main/java/com/grid/tv/data/db/dao/`. Highest-risk at startup:

1. `epg_learned_mappings` — full table into resolver
2. `epg_source_channels` — per-source full load
3. `stream_health` — full table on every `channels()` emission
4. `programs` — window queries can return **thousands** of rows for wide channel sets

---

## Heavy query: `programsWindowForChannels`

Triggers:
- `HomeEpgViewModel.loadWindow` → `computeLoadWindowOutcome` L1114+
- Post-EPG refresh sample L2238

Loads `ProgramDao.loadWindow(playlistId, chunk, start, end)` in chunks of **400** channel ids (`IptvRepositoryImpl.kt` L1354–1356).

Row count = programmes in **4h window** × matched channels — **UNBOUNDED** by SQL LIMIT.

---

## Recommendations

1. Replace `epgLearnedMappingDao.all()` with scoped/limited query before resolver build.
2. Add `.flowOn(Dispatchers.IO)` to `channels()` combine (L772).
3. Deduplicate `channelsPage(0)` warm vs bootstrap.
4. Cache `loadSettings()` result for startup window.
5. Defer `streamHealthDao.observeAll()` subscription until player/health UI needed.
