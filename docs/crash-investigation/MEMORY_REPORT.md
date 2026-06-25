# MEMORY_REPORT.md

Evidence-based memory allocation audit. Line numbers refer to `app/src/main/java` unless noted.

## Methodology

- Rankings use **code-proven caps** where constants exist.
- Where no cap exists, size is marked **UNBOUNDED** (not estimated).
- JVM heap figures use `Runtime.getRuntime()` (see `PlaybackDiagnostics.memorySnapshot()` L131–139).

---

## Top 50 Largest Potential Memory Consumers (ranked)

| Rank | Object / Cache | File | Method | Line | Type | Cap in Code | Lifetime | Release |
|------|----------------|------|--------|------|------|-------------|----------|---------|
| 1 | Xtream `get_live_streams` response body | `RemoteTextFetcher.kt` | `fetchDetailed` → `executeFetch` | L99–122 | `String` | **UNBOUNDED** | Import only | GC after parse |
| 2 | M3U playlist download | `IptvRepositoryImpl.kt` | `importM3uChannels` | L1711 | `String` via `fetch()` | **UNBOUNDED** | Import | GC |
| 3 | EPG XMLTV download (disk-spooled path) | `RemoteTextFetcher.kt` | `fetchEpgXmlTv` | L234+ | Temp file + parse buffers | Disk-bounded | EPG worker | File delete post-parse |
| 4 | VOD catalog temp file | `RemoteTextFetcher.kt` | `fetchCatalogToTempFile` | L149+ | `File` on disk | `rawBytes` logged | Per refresh | `deleteCatalogCacheFile` L3381 |
| 5 | `EpgChannelLinkResolver` per playlist | `IptvRepositoryImpl.kt` | `rebuildEpgLinkResolver` | L1376–1397 | Multiple `Map` + `List` | **UNBOUNDED** (cap L247 unused) | Singleton until `notifyEpgLinksUpdated` L1431 | `epgLinkResolversByPlaylist = null` |
| 6 | `epgLearnedMappingDao.all()` | `EpgLearnedMappingDao.kt` | `all()` | L20 | `List<Entity>` → `Map` | **UNBOUNDED** | Resolver lifetime | Resolver cache clear |
| 7 | `epgSourceChannelDao.bySource` rows | `EpgSourceChannelDao.kt` | `bySource` | L11 | `List<Entity>` | **UNBOUNDED** | Resolver | Same as #5 |
| 8 | `programDao.distinctChannelEpgIdsForPlaylist` | `ProgramDao.kt` | query | — | `List<String>` | **UNBOUNDED** | Resolver build | Same |
| 9 | Room `streamflow.db` page cache | `AppDatabaseHolder.kt` | `get` / `prewarm` | L25–27 | SQLite pages | OS-managed | Process | DB close |
| 10 | Coil memory cache | `StreamFlowApplication.kt` | `onCreate` | L54–59 | Bitmap pool | **16 MB** low-end L83 / **48 MB** high-end L102 | Process | `trimImageCaches` L75–78 |
| 11 | Coil disk cache | `StreamFlowApplication.kt` | `onCreate` | L61–65 | Disk | **20 MB** / **50 MB** | Process | LRU |
| 12 | Live `ExoPlayer` instance | `LivePlayerManager.kt` | `getOrCreatePlayer` | L311+ | ExoPlayer + buffers | 1 primary | Until `destroyPlayerInstance` L856+ | `releasePlayerResources` L437 |
| 13 | `MultiPanePlaybackPool.players` | `MultiPanePlaybackPool.kt` | `getOrCreatePlayer` | L32–53 | `Map<Int, ExoPlayer>` | **4 slots** (comment L18); **2 panes** low-end `LowEndDeviceMode` L73 | Session | `releasePane` / orchestrator teardown |
| 14 | VOD `DirectPlayerViewModel` player | `DirectPlayerViewModel.kt` | `createPlayer` | L99 | ExoPlayer | 1 per screen | Screen dispose L430 | `releasePlayer` L156 |
| 15 | `HomeEpgViewModel._channels` retention | `HomeEpgViewModel.kt` | `trimRetainedChannels` | L1013+ | `List<Channel>` | **≤800** (4×`CHANNEL_PAGE_SIZE` 200) | ViewModel | `onCleared` |
| 16 | `HomeEpgViewModel._epgPrograms` | `HomeEpgViewModel.kt` | `loadWindow` / merge | L1068+ | `List<Program>` | Window-scoped; grows with channel pages | ViewModel | Trim on channel drop L946 |
| 17 | `EpgBlockCache` | `EpgBlockCache.kt` | `put` | L24–26 | `BoundedMemoryCache<String, List<Program>>` | **6 blocks / 4 MB** L36–38 | Repository | `epgCache.clear` on refresh |
| 18 | `seriesSeasonsCache` | `IptvRepositoryImpl.kt` | field | L433–438 | `BoundedMemoryCache` | **24 entries / 8 MB** | Repository | LRU eviction |
| 19 | `viewportEpgLastFetch` / `viewportEpgFailureUntil` | `IptvRepositoryImpl.kt` | L258–271 | `BoundedMemoryCache` | **512 entries** each | Repository | LRU |
| 20 | VOD ingest batch | `XtreamCatalogStreamParser.kt` | `parseVodCatalogStream` | L39–59 | `List<VodItem>` per batch | **100 items** `DEFAULT_BATCH_SIZE` L16 | Per batch callback | Discarded after `insertAll` |
| 21 | Series ingest batch | `XtreamCatalogStreamParser.kt` | `parseSeriesCatalogStream` | L62–77 | `List<SeriesShow>` | **100 items** | Per batch | Discarded after insert |
| 22 | `VodHubViewModel.enrichmentByKey` | `VodHubViewModel.kt` | prefetch paths | L252+ | `Map` full copy on update | **UNBOUNDED** | ViewModel | `onCleared` |
| 23 | `TitleEnrichmentRepository.sessionCache` | `TitleEnrichmentRepository.kt` | L31–37 | `BoundedMemoryCache` | **400 entries / 2 MB** | Singleton | LRU |
| 24 | `UnifiedSearchIndex` | `UnifiedSearchEngine.kt` | `rebuildIndex` | L43+ | In-memory index | **UNBOUNDED** | Singleton | Rebuild |
| 25 | `ProgrammeIndex` cache | `HomeEpgViewModel.kt` | `buildWithCache` | L426–430 | Index object | Tied to channel+program count | ViewModel | Rebuilt on debounce |
| 26 | `cachedCategoryLookup` | `IptvRepositoryImpl.kt` | L2814–2822 | `Map<String,String>` | **UNBOUNDED** | Until revision bump | `invalidateCategoryLookupCache` L2783 |
| 27 | `buildCategoryNameLookupUncached` tables | `IptvRepositoryImpl.kt` | L2832–2866 | Multiple maps + genre hints | **UNBOUNDED** | Per call | GC |
| 28 | `streamHealthDao.observeAll()` in `channels()` | `IptvRepositoryImpl.kt` | `channels` | L782 | Flow → `associateBy` | **UNBOUNDED** rows | Per emission | Flow cancel |
| 29 | `playlistDao.observeAll()` (multiple subscribers) | `PlaylistContext`, `HomeEpgViewModel`, `channels()` | various | `List<PlaylistEntity>` | **UNBOUNDED** | Flow | Cancel |
| 30 | `ChannelScanner` probe state | `ChannelScanner.kt` | init L115+ | Maps + batches | Batched IO | Singleton | — |
| 31 | `ChannelScanStatusCache` | `ChannelScanStatusCache.kt` | — | Bounded via registry | Registry-defined | Singleton | — |
| 32 | `HostFailureTracker` | `HostFailureTracker.kt` | — | `BoundedMemoryCache` | Registry caps | Singleton | — |
| 33 | Xtream auth + categories raw | `IptvRepositoryImpl.kt` | `importXtreamCatalog` | L1792–1797 | `String` | **UNBOUNDED** | Import | GC |
| 34 | `get_vod_categories` body | `IptvRepositoryImpl.kt` | `refreshVodCategoriesForPlaylist` | L3415 | `String` in `fetchDetailed` | **UNBOUNDED** | Per refresh | GC |
| 35 | `epgLinkResolversByPlaylist` map | `IptvRepositoryImpl.kt` | L274, L1393–1395 | `MutableMap<Long, Resolver>` | One resolver per playlist | Until EPG notify | `= null` |
| 36 | `EpgUiSnapshot` build | `HomeEpgViewModel.kt` | combine L401–409 | Snapshot object | Up to retained channels | Per emission | Replaced |
| 37 | `featuredCarousel` + hero enrichment | `VodHubViewModel.kt` | init L161–167 | Lists + TMDB entities | Prefetch **20–40** low-end/high-end L143 | Hub session | VM clear |
| 38 | `recommendationSample` | `VodHubViewModel.kt` + `StartupTierPolicy.kt` | L33 | `List` sample | **150 / 500** items | Hub | Replaced |
| 39 | `PlayerFactory` listener maps | `PlayerFactory.kt` | L131–150 | `ConcurrentHashMap` | Per active player | Player lifetime | `releasePlayer` L134 |
| 40 | `DecoderPressureTracker.sessions` | `DecoderPressureTracker.kt` | L30–33 | `ConcurrentHashMap` | Per registered player | Process | `unregisterPlayer` |
| 41 | ExoPlayer disk cache dirs | `LivePlayerManager.kt` | `clearStreamCache` | L856+ | Files | Configured in media source | Tune teardown | Explicit clear |
| 42 | `vodCacheByPlaylist` (if used) | Repository field | — | List cache | Check usage | — | — |
| 43 | `ContinueWatching` flow rows | `ContinueWatchingDao.kt` | observe | Flow | DB-sized | Flow | — |
| 44 | `profileWatchHistoryDao.observeVodPositions` | `HomeEpgViewModel` wiring | L276–277 | Flow map | All VOD positions profile | Home mount | — |
| 45 | `RecordingViewModel` recording flows | `RecordingViewModel.kt` | L71+ | Flow | All recordings | Home route | — |
| 46 | `TitleEnrichmentDao.topByPopularity(120)` | `UnifiedSearchEngine.kt` | rebuild | L181 | 120 rows | Search index build | Rebuild |
| 47 | `warmLocalUiCache` channel page | `IptvRepositoryImpl.kt` | L2427–2437 | 200 `ChannelEntity` | **200** | Warm only | GC |
| 48 | `importXtreamCatalog` `liveChannels` list | `IptvRepositoryImpl.kt` | L1803–1807 | `List<ChannelEntity>` before chunk insert | Full parse in memory | Import | GC after insert |
| 49 | Gson `JsonReader` buffers during VOD stream parse | `XtreamCatalogStreamParser.kt` | `parseCatalogStream` | L80+ | Parser stack | **≤1 object + batch 100** | Per batch | — |
| 50 | Splash / Compose composition trees | UI layer | — | Compose nodes | Transient | Per frame | Recomposition |

---

## Confirmed UNBOUNDED allocations (LMKD-critical)

1. **`rebuildEpgLinkResolver`** — `MAX_RESOLVER_SOURCE_CHANNELS = 5_000` at `IptvRepositoryImpl.kt:247` is **never referenced** in `rebuildEpgLinkResolver` (L1376–1397).
2. **`RemoteTextFetcher.fetch()`** — entire HTTP body as `String` (L58, L99–122). Used for M3U (L1711) and Xtream live import (L1792–1797).
3. **`epgLearnedMappingDao.all()`** — no LIMIT (L20 `EpgLearnedMappingDao.kt`); loaded on every resolver rebuild (L1385–1387).
4. **`VodHubViewModel.enrichmentByKey`** — map copy growth without cap.

---

## Instrumentation added

- `StartupTrace` — tag `STARTUP_TRACE`, logs heap at each traced operation.
- `MemoryPressureMonitor` — tag `MEMORY_PRESSURE`, samples every **5s** from `StreamFlowApplication.onCreate`.

Filter logcat:
```
adb logcat -s STARTUP_TRACE MEMORY_PRESSURE PlaybackDiag VodCatalogPipeline EpgFlow
```
