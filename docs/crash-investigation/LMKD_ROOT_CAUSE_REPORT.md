# LMKD_ROOT_CAUSE_REPORT.md

Android Low Memory Killer Daemon (LMKD) terminates processes when **system** memory is exhausted. On 2GB TV devices, Java heap spikes + native decoder memory + large transient allocations are the primary suspects.

**This report ranks causes by code evidence, not post-mortem logs** (device captures required to confirm).

---

## Ranked root causes

| Rank | Cause | Probability | Impact | Confidence | Evidence |
|------|-------|-------------|--------|------------|----------|
| 1 | **Concurrent EPG bulk import + VOD catalog refresh** | High on high-end timing; Medium on low-end (45s EPG delay) | Process kill during dual network+DB spike | **High** (schedule proven) | `StartupTierPolicy` tier3 5s/12s + `epgStartupDelaySec` 5s/45s; no cross-mutex (`WORKER_COLLISION_REPORT.md`) |
| 2 | **M3U / Xtream live import — full body as `String`** | High on import path | OOM → kill | **High** | `importM3uChannels` L1711 `fetch()`; `importXtreamCatalog` L1792–1797; `RemoteTextFetcher` L99–122 |
| 3 | **`rebuildEpgLinkResolver` unbounded maps** | High on large EPG | Java heap OOM / system pressure | **High** | L1376–1397; unused cap L247; 4× map copies in `EpgChannelLinkResolver` |
| 4 | **`epgLearnedMappingDao.all()` global load** | Medium–High | Adds L rows to every resolver | **High** | `EpgLearnedMappingDao.kt:20`; `IptvRepositoryImpl.kt:1385–1387` |
| 5 | **ExoPlayer + preview + multi-pane decoders** | Medium | Native OOM (LMKD) | **Medium** | `MultiPanePlaybackPool` up to 4; low-end cap 2; `LivePlayerManager` separate instance |
| 6 | **Coil logo storm on guide grid** | Medium | Heap + native bitmap | **Medium** | Grid compose + 16MB coil cap low-end; unbounded concurrent decode requests |
| 7 | **`VodHubViewModel.enrichmentByKey` unbounded** | Medium (VOD path) | Heap growth | **High** (code) | Map copy on each prefetch |
| 8 | **EPG XMLTV parse + bulk Room insert** | Medium | CPU + I/O pressure, memory during parse buffers | **Medium** | `refreshEpgNow` L2058+; `importEpgForPlaylist` |
| 9 | **`channels()` Flow remap on Main** | Medium (jank/ANR, indirect) | ANR → watchdog kill | **Medium** | `IptvRepositoryImpl.kt:772–793` no `flowOn(IO)` |
| 10 | **Splash 3170ms + heavy Hilt graph on Main** | Low for LMKD; High for slow start | Perceived freeze | **High** | `SplashScreen.kt:52–67`; `@HiltAndroidApp` |

---

## Evidence: VOD path does NOT load full catalog into heap

| Stage | Mechanism | File:Line |
|-------|-----------|-----------|
| Download | Stream to temp file | `fetchCatalogToTempFile` L149 |
| Parse | Gson streaming, batch **100** | `XtreamCatalogStreamParser` L16, L39–59 |
| Persist | Batch `insertAll` then discard batch | `IptvRepositoryImpl` L3320 |
| Low memory guard | Skip refresh at **85%** heap | `isSystemLowOnMemory` L2561–2565 |
| Memory log | Before/after parse | `PlaybackDiagnostics.logMemory` L3284, L3338 |

**Conclusion:** 100k–500k VOD **parse** can fit JVM if disk space sufficient; **LMKD risk during parse is lower than EPG/resolver/import paths** unless disk full or batch size changed.

---

## Evidence: M3U path loads entire playlist into heap

```kotlin
// IptvRepositoryImpl.kt:1711
val content = remoteTextFetcher.fetch(normalizedUrl)
```

`fetch` → `fetchDetailed` → full `String` body (`RemoteTextFetcher.kt:58, 99–122`).

`m3uParser.parseAsFlow` streams **after** full download — **large M3U cannot fit 2GB devices**.

---

## Worker / memory correlation (high-end cold start)

```
T+0s    Application, Hilt, Coil config
T+0.4s  warmLocalUiCache (DB)
T+4.6s  loadVodStreamed → schedules VOD network refresh
T+5s    EpgRefreshWorker STARTUP → refreshEpgNow (XMLTV download + parse + insert)
T+5–?s  refreshVodCatalogForPlaylist (disk file + batch parse) — OVERLAP WINDOW
T+?s    rebuildEpgLinkResolver on next guide EPG load
```

---

## What is NOT a likely LMKD primary cause

| Item | Reason |
|------|--------|
| VOD streaming parse alone | Batched, disk-backed |
| `ChannelHealthProbeWorker` on low-end | Skipped (`deferChannelHealthProbe`) |
| Periodic 12h VOD sync at launch | Enqueue only, no immediate run |
| Series category sidebar dedup | UI-only, negligible heap |

---

## Required device validation

To **confirm** LMKD (vs crash/OOM exception):

```bash
adb logcat -b events | grep -i "kill\|lmkd\|lowmemory"
adb shell dumpsys activity processes | grep -A5 grid.tv
```

Pair with `crash_session.log` capturing `MEMORY_PRESSURE` + `STARTUP_TRACE`.

---

## Fix priority for LMKD reduction

1. Enforce resolver channel cap (L247).
2. Stream M3U to disk parser (mirror VOD `fetchCatalogToTempFile`).
3. Serialize EPG startup worker vs VOD tier3 refresh (global `BackgroundWorkMutex`).
4. Scope/limit `epgLearnedMappingDao.all()`.
5. Cap `enrichmentByKey`.
6. Enable survival mode on low-end (see `CHROMECAST_SURVIVAL_PLAN.md`).
