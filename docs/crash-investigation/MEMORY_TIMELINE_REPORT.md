# MEMORY_TIMELINE_REPORT.md

## Purpose

Correlate JVM heap samples with startup phases and background work to detect **growth**, **spikes**, and **leaks** before LMKD.

## Instrumentation

### `MemoryPressureMonitor`

| Property | Value |
|----------|-------|
| File | `feature/startup/MemoryPressureMonitor.kt` |
| Started | `StreamFlowApplication.onCreate` L34 |
| Interval | **5_000 ms** (`INTERVAL_MS`) |
| Tag | `MEMORY_PRESSURE` |
| Fields logged | `usedMb`, `freeMb`, `maxMb`, `percentUsed`, `deltaMb`, `trend` |

Trend flags:
- `SPIKE` — `deltaMb >= 32` since last sample
- `GROWTH` — `deltaMb >= 8`
- `STABLE` — otherwise

### `StartupTrace`

| Property | Value |
|----------|-------|
| File | `feature/startup/StartupTrace.kt` |
| Tag | `STARTUP_TRACE` |
| Traced operations | See table below |

## Traced operations (implementation map)

| Operation | File:Line |
|-----------|-----------|
| `Application.onCreate` | `StreamFlowApplication.kt:35` |
| `AppDatabaseHolder.prewarm` | `StreamFlowApplication.kt:87–89` |
| `MainActivity.onCreate` | `MainActivity.kt:73` |
| `warmLocalUiCache` | `IptvRepositoryImpl.kt:2390` |
| `rebuildEpgLinkResolver` | `IptvRepositoryImpl.kt:1377` |
| `refreshEpgNow` | `IptvRepositoryImpl.kt:2059` |
| `refreshVodCatalogForPlaylist` | `IptvRepositoryImpl.kt:3238` |
| `refreshSeriesCatalogForPlaylist` | `IptvRepositoryImpl.kt:3471` |
| `HomeEpgViewModel.init` (marker) | `HomeEpgViewModel.kt:399` |
| `HomeEpgViewModel.reloadChannels` | `HomeEpgViewModel.kt:927` |
| `HomeEpgViewModel.loadWindow` | `HomeEpgViewModel.kt:1071` |

## Log format

```
[STARTUP_TRACE]
timestamp=<epoch ms>
thread=<name>
operation=<name>
durationMs=<ms>
heapUsed=<bytes>
heapFree=<bytes>
heapMax=<bytes>
heapUsedMb=<mb>
heapMaxMb=<mb>
heapPct=<percent>

[MEMORY_PRESSURE]
timestamp=<epoch ms>
usedMb=<mb>
freeMb=<mb>
maxMb=<mb>
percentUsed=<percent>
deltaMb=<mb since last>
trend=STABLE|GROWTH|SPIKE
```

## Existing complementary logs

| Tag | Content | File |
|-----|---------|------|
| `PlaybackDiag` | `MEMORY stage=...` | `PlaybackDiagnostics.logMemory` L39–45 |
| `PlaybackDiag` | `DEVICE_PROFILE` at launch | L21–36 |
| `StartupProfiler` | Stage markers +elapsed ms | `StartupProfiler.kt:17–21` |
| `VodCatalogPipeline` | Ingest batches | `VodCatalogIngestLogger` |
| `EpgFlow` | EPG worker lifecycle | `EpgRefreshWorker.kt` |

## How to capture on Chromecast

```bash
adb logcat -c
adb logcat -s STARTUP_TRACE MEMORY_PRESSURE MEMORY_PRESSURE StartupProfiler PlaybackDiag EpgFlow VodCatalogPipeline > crash_session.log
```

Reproduce: cold start → wait 60s → open guide → scroll → open VOD → return → wait for workers.

## Analysis checklist

1. Plot `usedMb` vs `timestamp` from `MEMORY_PRESSURE`.
2. Mark `STARTUP_TRACE` events on same timeline.
3. Correlate `SPIKE`/`GROWTH` with:
   - `refreshEpgNow` completion
   - `refreshVodCatalogForPlaylist` / `refreshSeriesCatalogForPlaylist`
   - `rebuildEpgLinkResolver`
   - `before_vod_stream_parse` / `after_vod_stream_parse` (`PlaybackDiagnostics`)
4. If heap rises monotonically without plateau → leak suspect (`enrichmentByKey`, resolver cache, Flow subscribers).
5. If spike then drop → transient allocation (acceptable if sub-max).

## Note on JVM vs native heap

`Runtime.getRuntime()` does **not** include MediaCodec/ExoPlayer native allocations. Decoder pressure requires `DecoderPressureTracker` logs and `dumpsys meminfo` on device.
