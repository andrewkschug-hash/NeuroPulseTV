# CHROMECAST_SURVIVAL_PLAN.md

Target: **≤2GB RAM**, `LowEndDeviceMode.active == true` (`LowEndDeviceMode.kt:66–94`).

Existing low-end toggles are listed first; **gaps** require new work.

---

## Already implemented (baseline)

| Feature | Low-end value | File:Line |
|---------|---------------|-----------|
| tier2 delay | 1200 ms | `StartupTierPolicy.kt:12` |
| tier3 delay | 12000 ms | `StartupTierPolicy.kt:14` |
| EPG startup worker delay | **45 s** | `LowEndDeviceMode.kt:89` |
| Guide bootstrap delay | 600 ms | `StartupTierPolicy.kt:17` |
| EPG hydrate delay | 800 ms | `StartupTierPolicy.kt:19` |
| Coil memory | **16 MB** | `LowEndDeviceMode.kt:83` |
| Coil disk | **20 MB** | L84 |
| Coil RGB565 | enabled | `StreamFlowApplication.kt:68` |
| Max multi-pane | **2** | `LowEndDeviceMode.kt:73` |
| Channel health worker | **disabled** | `deferChannelHealthProbe` L93 |
| VOD paging initial | 1× page | `StartupTierPolicy.kt:30–31` |
| Recommendation sample | **150** | `StartupTierPolicy.kt:33` |
| VOD refresh skip | at **85%** heap | `IptvRepositoryImpl.kt:2561–2565` |
| EPG fuzzy match off | N > **2500** | `EpgChannelLinkResolver.kt:107` |

---

## Proposed survival mode additions

### P0 — Must ship for LMKD

| Change | Action | Memory / startup | Risk |
|--------|--------|------------------|------|
| **Disable startup EPG on low-end** | Skip `scheduleStartupEpg()` when `LowEndDeviceMode.active` | Defers largest XMLTV spike **45s+**; eliminates overlap with tier3 VOD | Stale EPG until manual refresh |
| **Disable tier3 `loadVodStreamed` on cold start** | Gate `StreamFlowApplication.kt:109` on `!active` or user opened VOD | Removes **12s** VOD network+DB on cold start | Stale VOD until hub opened |
| **Enforce `MAX_RESOLVER_SOURCE_CHANNELS`** | Truncate `refs` in `rebuildEpgLinkResolver` L1378–1384 | Caps resolver maps at **5000** channels | Some channels lose fuzzy/name match |
| **M3U stream-to-disk** | Replace `fetch()` with temp-file parser in `importM3uChannels` L1711 | Eliminates full-file `String` OOM | Medium implementation |

### P1 — High value

| Change | Action | Benefit | Risk |
|--------|--------|---------|------|
| Lazy search index | Defer `UnifiedSearchEngine.init` rebuild until search focused | −DB + CPU at home mount | First search slower |
| Lazy home flows | Defer `liveSportsNow`, `moviesStartingSoon`, `vodProgress` subscriptions | Fewer DB observers | Features empty until loaded |
| Skip splash for returning users | Short-circuit `SplashScreen` when playlist exists | **−3170 ms** perceived | UX change |
| `channels().flowOn(IO)` | `IptvRepositoryImpl.kt:772` | ANR/jank reduction | Low |
| Cap enrichment map | LRU 400 in `VodHubViewModel` | Bounded hub memory | Evicted posters |
| Serialize background work | Mutex: EPG refresh vs VOD refresh | No dual spike | Longer total refresh time |

### P2 — Tuning

| Change | Action |
|--------|--------|
| Reduce `CHANNEL_PAGE_SIZE` on low-end | 200 → 100 |
| Reduce EPG window channel priority | `PRIORITY_EPG_CHANNEL_COUNT` lower |
| Reduce `EpgBlockCache` | 6 → 3 blocks on low-end |
| `decodeOnlyMultiPaneAudio` | Already true L92 — enforce single video decoder |

---

## Disable matrix (survival mode)

| Subsystem | Normal low-end | Survival |
|-----------|----------------|----------|
| Startup EPG worker | Delay 45s | **OFF** |
| Tier3 VOD refresh | 12s after launch | **OFF** until VOD tab |
| Channel health probe | OFF | OFF |
| Unified search index build | On catalog revision | **On first search** |
| TMDB prefetch | 20 items | **0** until hero visible |
| Coil crossfade | 0 | 0 |
| Multi-pane | max 2 | **max 1** (optional) |
| Preview player | 500ms debounce | **OFF** until user focuses channel |

---

## Expected outcomes (qualitative)

| Metric | Mechanism |
|--------|-----------|
| **Memory** | Removing concurrent EPG+VOD at T+12s eliminates largest dual spike; resolver cap bounds worst-case heap |
| **Startup** | Skip splash + defer VOD/EPG → guide interactive sooner |
| **ANR** | IO dispatcher on `channels()` + SetupGate DB on IO |
| **Crash/LMKD** | M3U streaming + resolver cap address proven UNBOUNDED paths |

Device A/B required to quantify MB and seconds — use `MEMORY_PRESSURE` + `STARTUP_TRACE` logs.

---

## Implementation hook

Gate survival behaviors on existing profile:

```kotlin
LowEndDeviceMode.current().active // already used
// Proposed: LowEndDeviceMode.current().survivalModeActive
```

Single flag in `LowEndDeviceMode.Profile` avoids scattering device checks.

---

## VOD catalog scale on survival mode

| Catalog size | Parse heap (batch 100) | Disk for temp JSON | Survival verdict |
|--------------|------------------------|--------------------|------------------|
| 100k movies | Bounded by batch | Provider-dependent | **Feasible** if refresh deferred off startup |
| 200k movies | Same | 2× disk | **Feasible** with disk space; refresh on user action |
| 500k movies | Same | 5× disk | **SQLite + disk** limit before heap; paginated UI required |

Parse path: `XtreamCatalogStreamParser.kt` — does not allocate full catalog `List` in memory.
