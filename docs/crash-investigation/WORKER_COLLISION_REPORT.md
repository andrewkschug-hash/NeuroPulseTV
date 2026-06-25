# WORKER_COLLISION_REPORT.md

## Workers inventory

| Worker | File | Schedule | First run (cold start) | Network | Heavy DB |
|--------|------|----------|------------------------|---------|----------|
| `EpgRefreshWorker` (startup) | `EpgJobCoordinator.kt:115–131` | `scheduleStartupEpg` L68–77 | **+5s** high-end / **+45s** low-end (`LowEndDeviceMode.kt:89, 108`) | `refreshEpgNow` XMLTV | `importEpgForPlaylist` bulk insert |
| `EpgRefreshWorker` (periodic) | `EpgJobCoordinator.kt:39–51` | 6h periodic | Not immediate | Same | Same |
| `EpgRefreshWorker` (import) | `EpgJobCoordinator.kt:81–87` | REPLACE, delay 0 | On playlist import | Same | Same |
| `EpgResolverWorker` (periodic) | `EpgJobCoordinator.kt:53–64` | 7d periodic | Not immediate | Resolver network | `EpgResolverEngine` |
| `EpgResolverWorker` (post-refresh) | `EpgJobCoordinator.kt:102–111` | After successful refresh | After EPG import | Yes | Yes |
| `VodCatalogSyncWorker` | `VodCatalogSyncScheduler.kt:18–31` | 12h periodic | Enqueued at tier3 startup, **not immediate** | `get_vod_streams` + `get_series` | Batch inserts |
| `ChannelHealthProbeWorker` | `ChannelHealthScheduler.kt:16–24` | 6h periodic | **Skipped** when `deferChannelHealthProbe` (`LowEndDeviceMode.kt:93`) | HTTP probes | `stream_health` updates |

---

## Startup timeline (code-derived delays)

Constants: `StartupTierPolicy.kt` L12–14, `LowEndDeviceMode.kt` L89/108.

### High-end device

| Time | Event | Thread |
|------|-------|--------|
| 0s | `Application.onCreate`, `MemoryPressureMonitor.start` | Main |
| 0s | `app-deferred-startup` thread starts | BG |
| 0–?s | `AppDatabaseHolder.prewarm` | BG |
| **+0.4s** | `warmCriticalLocalData` → `warmLocalUiCache` | IO |
| **+0.4s** | WorkManager periodic workers enqueued | BG |
| **+0.4s** | `scheduleStartupEpg` queued (**runs +5s**) | WM |
| **+5.0s** | `loadVodStreamed(REPOSITORY_INIT)` scheduled | IO (`tier3` 5s − tier2 0.4s ≈ **+4.6s**) |
| **+5.0s** | **`EpgRefreshWorker` STARTUP may run** | WM |
| **+~5–10s** | VOD deferred refresh (`deferredVodRefreshDelayMs` = tier3 = 5s from `loadVodStreamed`) | IO |
| **+3.17s** (parallel UI) | Splash ends → Home → `HomeEpgViewModel` bootstrap | Main/IO |

### Low-end device (Chromecast-class)

| Time | Event |
|------|-------|
| **+1.2s** | tier2 warm |
| **+12.0s** | tier3 `loadVodStreamed` |
| **+45s** | `EpgRefreshWorker` STARTUP initial delay |
| Channel health probe | **Not scheduled** |

---

## Concurrent work collisions

| Collision | When | Impact |
|-----------|------|--------|
| **EPG worker + VOD refresh** | High-end: ~5–12s after launch | Network + CPU + DB writes + heap spikes (`refreshEpgNow` L2058 + `refreshVodCatalogForPlaylist` L3238) |
| **EPG worker + guide `loadWindow`** | User reaches home before +5s | DB read programmes + EPG bulk write |
| **EPG refresh + resolver rebuild** | Post-refresh L2220–2221 | `epgLinkResolversByPlaylist = null` then lazy rebuild on next guide load |
| **VOD refresh + VOD hub mount** | User opens VOD tab during tier3 | `VodHubViewModel.init` L136 `loadVodStreamed(VOD_HUB_MOUNT)` — mutex in `vodRefreshMutex` |
| **Import EPG + startup EPG** | Playlist import | `scheduleImportEpg` REPLACE supersedes startup (`EpgJobCoordinator.kt:81–87`) |
| **ChannelScanner + EPG** | `EpgRefreshWorker` awaits `channelScanGate` L40–45 | Defers EPG until validation idle |

---

## Mutex / serialization (existing)

| Resource | Mutex | File |
|----------|-------|------|
| VOD refresh | `vodRefreshMutex` | `IptvRepositoryImpl` `refreshVodSeriesCatalog` |
| EPG refresh | `epgRefreshMutex` | `refreshEpgNow` L2058 |
| Playlist import | `playlistImportCoordinator` | Defers VOD L2364–2366 |

**Gap:** No global mutex between **EPG refresh** and **VOD refresh**.

---

## Evidence logs

- `EpgFlow` — `EPG_JOB_SCHEDULED`, worker start/finish (`EpgRefreshWorker.kt:35–68`)
- `VodCatalogPipeline` — VOD worker + ingest (`VodCatalogSyncWorker.kt:27–37`)
- `STARTUP_TRACE` — operation durations + heap (new)
- `MEMORY_PRESSURE` — 5s heap samples (new)
