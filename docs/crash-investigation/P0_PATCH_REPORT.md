# P0 Patch Report — Chromecast Survival Fixes

**Date:** 2026-06-18  
**Scope:** P0-A through P0-D only (no M3U streaming, no other changes)

---

## Files Modified

| File | Lines (approx.) | Change |
|------|-----------------|--------|
| `app/src/main/java/com/grid/tv/player/LowEndDeviceMode.kt` | 49–52, 89 | `isEnabled()` alias; EPG fallback delay **45s → 90s** |
| `app/src/main/java/com/grid/tv/StreamFlowApplication.kt` | 107–113 | Skip `loadVodStreamed(REPOSITORY_INIT)` on low-end |
| `app/src/main/java/com/grid/tv/data/repository/IptvRepositoryImpl.kt` | 126, 283, 1376–1415, 2073, 2315–2322, 2531 | Mutex merge, VOD startup guard, resolver cap |
| `app/src/main/java/com/grid/tv/feature/epg/EpgJobSource.kt` | 4 | Added `GUIDE_OPEN` source |
| `app/src/main/java/com/grid/tv/feature/epg/EpgJobCoordinator.kt` | 35–36, 68–102 | Guide-open EPG trigger; low-end deferred startup |
| `app/src/main/java/com/grid/tv/worker/EpgScheduler.kt` | 22–24 | `scheduleEpgOnGuideOpen()` facade |
| `app/src/main/java/com/grid/tv/ui/viewmodel/HomeEpgViewModel.kt` | 32, 80, 523 | Inject scheduler; trigger EPG on guide bootstrap |

---

## P0-A — Disable Startup VOD Refresh on Low-End

### Before

```
StreamFlowApplication.runDeferredStartup()
  tier3 delay (12s low-end / 5s high-end)
  → loadVodStreamed(REPOSITORY_INIT)
      → hydrateVodUiFromRoom (DB counts)
      → warmLocalUiCache (if needed)
      → scheduleDeferredVodCatalogRefresh → network VOD + series refresh
```

Low-end Chromecast at **~12s** after launch: VOD/series network pipeline starts even if user never opens VOD.

### After

**`StreamFlowApplication.kt` L109–113:**
```kotlin
if (!LowEndDeviceMode.isEnabled()) {
    entryPoint.repository().loadVodStreamed(VodRefreshTrigger.REPOSITORY_INIT)
} else {
    Log.i(TAG, "Skipping startup VOD refresh on low-end device — loads on VOD hub entry")
}
```

**`IptvRepositoryImpl.loadVodStreamed` L2315–2322** (defense in depth):
```kotlin
if (LowEndDeviceMode.isEnabled() && trigger == VodRefreshTrigger.REPOSITORY_INIT) {
    return
}
```

### Unchanged (by design)

- `VOD_HUB_MOUNT` — still loads when user enters VOD hub (`VodHubViewModel` L136)
- `MANUAL_RETRY`, post-import refresh, `BACKGROUND_SYNC` worker
- `warmLocalUiCache()` in tier2 — channel page + **counts only** (no network catalog fetch)

### Expected impact (low-end, cold start)

| Metric | Before | After |
|--------|--------|-------|
| Network VOD/series at startup | Yes (~12s) | **No** |
| TMDB / enrichment prefetch at startup | Via deferred VOD path | **Deferred to VOD hub** |
| Heap spike from streaming parse batches | Possible at ~12s | **Eliminated until VOD entry** |
| Startup tier3 work | VOD + periodic scheduler | **Periodic scheduler only** |

---

## P0-B — Disable Startup EPG Refresh on Low-End (Guide or 90s)

### Before

| Device | EPG worker delay |
|--------|------------------|
| High-end | 5s (`epgStartupDelaySec`) |
| Low-end | 45s |

EPG always scheduled at app launch regardless of whether user opens guide.

### After

| Device | EPG trigger |
|--------|-------------|
| High-end | 5s after launch (unchanged) |
| Low-end | **Guide bootstrap** (`GUIDE_OPEN`, immediate, `REPLACE`) **OR** **90s** fallback (`STARTUP`, `KEEP`) |

**`LowEndDeviceMode.kt` L89:** `epgStartupDelaySec = 90L` (within 60–120s window)

**`EpgJobCoordinator.scheduleEpgOnGuideOpen()`** — low-end only, once per process:
- Enqueues `EpgRefreshWorker` with `source=GUIDE_OPEN`, `initialDelay=0`, `REPLACE`
- Cancels pending 90s startup job if user opens guide first

**`HomeEpgViewModel.bootstrapGuideFromSettings()` L523:**
```kotlin
epgScheduler.scheduleEpgOnGuideOpen()
```

### Expected impact (low-end)

| Metric | Before | After |
|--------|--------|-------|
| EPG at 5–45s if user on home only | Always scheduled | **90s fallback only** |
| EPG when user opens guide | Same global worker | **Immediate on guide open** |
| DB contention during first minute | EPG + VOD overlap possible | **Reduced** — VOD startup removed |

---

## P0-C — `startupHeavyWorkMutex` (Serialize Heavy Work)

### Before

```kotlin
private val vodRefreshMutex = Mutex()
private val epgRefreshMutex = Mutex()
```

EPG refresh and VOD refresh could run **concurrently** → overlapping network, SQLite writes, and heap spikes.

### After

```kotlin
private val startupHeavyWorkMutex = Mutex()
```

**Single mutex guards:**

| Operation | Method | Line |
|-----------|--------|------|
| EPG network refresh | `refreshEpgNow()` | ~2073 |
| VOD + series network refresh | `refreshVodSeriesCatalog()` → `executeVodSeriesNetworkRefresh` | ~2531 |
| EPG link resolver rebuild | `rebuildEpgLinkResolver()` | ~1377 |

### Behavior change

- If `EpgRefreshWorker` runs while VOD hub triggers catalog refresh, the second operation **waits** instead of running in parallel.
- Guide `loadWindow()` → `ensureEpgLinkResolver()` **blocks** until any in-flight EPG/VOD work completes (may add brief guide latency during refresh — acceptable vs LMKD).

### Expected impact

| Metric | Before | After |
|--------|--------|-------|
| Peak concurrent heavy jobs | 2+ (EPG + VOD) | **1** |
| Worst-case heap superposition | EPG parse + VOD parse | **Serialized** |

---

## P0-D — Enforce `MAX_RESOLVER_SOURCE_CHANNELS = 5_000`

### Before (`rebuildEpgLinkResolver`, full method)

```kotlin
private suspend fun rebuildEpgLinkResolver(playlistId: Long): EpgChannelLinkResolver {
    val sourceKey = "xmltv:$playlistId"
    val refs = linkedMapOf<String, XmlTvChannelRef>()
    epgSourceChannelDao.bySource(sourceKey).forEach { source ->   // UNBOUNDED
        refs[source.epgId] = XmlTvChannelRef(source.epgId, source.displayName)
    }
    programDao.distinctChannelEpgIdsForPlaylist(playlistId).forEach { epgId ->  // UNBOUNDED
        refs.putIfAbsent(epgId, XmlTvChannelRef(epgId, epgId))
    }
    val learnedMappings = epgLearnedMappingDao.all().associate { ... }  // still global
    return EpgChannelLinkResolver(xmlTvChannels = refs.values.toList(), ...)
}
```

Constant at L248 was **defined but never referenced**.

### After

```kotlin
val sourceChannels = epgSourceChannelDao.bySource(sourceKey)
if (sourceChannels.size <= MAX_RESOLVER_SOURCE_CHANNELS) {
    sourceChannels.forEach { ... }
} else {
    Log.w(..., "skipping N xmltv source channels — programme ids only")
}
programDao.distinctChannelEpgIdsForPlaylist(playlistId).forEach { epgId ->
    if (refs.size >= MAX_RESOLVER_SOURCE_CHANNELS) return@forEach
    refs.putIfAbsent(epgId, ...)
}
```

Plus log: `resolverChannels=${refs.size} cap=5000`

### Worst-case resolver heap (after cap)

| Component | Max entries | Notes |
|-----------|-------------|-------|
| `refs` map | **5,000** | Hard cap |
| `EpgChannelLinkResolver` internal maps | ~4 × N | N ≤ 5,000; fuzzy disabled when N > 2,500 (existing) |
| `learnedMappings` | Unbounded | **Not changed in this patch** (P1 candidate) |

### Expected impact

For playlists with **10k+ EPG source channels**, resolver heap drops from **unbounded** to **≤5k channel refs** — the audit’s primary 20–100+ MB risk for large XMLTV channel tables.

---

## Combined Expected Reduction (Low-End Chromecast, First 60s)

| Phase | Before | After |
|-------|--------|-------|
| ~5–12s | EPG queued (45s) + VOD network at 12s | **No VOD**; EPG at 90s or guide |
| ~12–45s | VOD parse + DB writes | **Idle** (unless guide opened) |
| Guide open | Resolver rebuild unbounded | **Capped resolver**; EPG refresh if not done |
| Concurrent spikes | EPG ∥ VOD | **Serialized** |

**Process death risk:** Removing simultaneous VOD + EPG + unbounded resolver during the first minute targets the highest-probability LMKD scenario identified in the audit. Exact MB savings require on-device `MEMORY_PRESSURE` capture (`adb logcat -s STARTUP_TRACE MEMORY_PRESSURE EpgFlow VodCatalogPipeline`).

---

## Verification Checklist

```bash
# Cold start on Chromecast / Firestick emulator
adb logcat -s EpgFlow VodCatalogPipeline STARTUP_TRACE MEMORY_PRESSURE

# Expect on low-end:
# - "Skipping startup VOD refresh on low-end device"
# - "EPG low-end startup deferred 90s"
# - On guide: "EPG_JOB_SCHEDULED source=GUIDE_OPEN"
# - On resolver: "resolverChannels=... cap=5000"
# - No concurrent "refreshEpgNow started" + "vod_refresh_start" without mutex wait
```

---

## Not Included (P1 — requested separately)

- M3U stream-to-disk parse (`importM3uChannels` full-String load)
- `epgLearnedMappingDao.all()` scoping
- `VodHubViewModel.enrichmentByKey` cap
