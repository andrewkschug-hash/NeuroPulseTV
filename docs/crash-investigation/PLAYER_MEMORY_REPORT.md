# PLAYER_MEMORY_REPORT.md

## ExoPlayer creation sites

| # | Location | File:Line | Owner | Release |
|---|----------|-----------|-------|---------|
| 1 | `PlayerFactory.create` | `PlayerFactory.kt:117–128` | Caller-supplied `decoderOwner` | `PlayerFactory.releasePlayer` L134–138 |
| 2 | `LivePlayerManager.getOrCreatePlayer` | `LivePlayerManager.kt:311+` | `@Singleton` LivePlayerManager | `destroyPlayerInstance` L856+ |
| 3 | `MultiPanePlaybackPool.getOrCreatePlayer` | `MultiPanePlaybackPool.kt:38–53` | Pool slot `paneIndex` | `releasePane` L197+ |
| 4 | `MultiPanePlaybackPool.adoptExistingPlayer` | `MultiPanePlaybackPool.kt:59–72` | Reuses live player as pane 0 | `releasePane` |
| 5 | `DirectPlayerViewModel.createPlayer` | `DirectPlayerViewModel.kt:99+` | VOD screen VM | `releasePlayer` L156, screen dispose `DirectPlayerScreen.kt:430` |
| 6 | `SplitViewViewModel.playerForPane` | `SplitViewViewModel.kt:150` | Delegates to pool | Pool release |
| 7 | `MultiViewManager.getOrCreatePlayer` | `MultiViewManager.kt:21` | Delegates to pool | Pool release |
| 8 | `PreviewPlayerManager` | `PreviewPlayerManager.kt:22` | **Facade** → `LivePlayerManager` MINI | Same as #2 |

**Note:** No `SimpleExoPlayer` direct usage — Media3 `ExoPlayer.Builder` only (`PlayerFactory.kt:117`).

---

## Maximum simultaneous ExoPlayers

| Scenario | Players | Evidence |
|----------|---------|----------|
| Cold start | **0** | No player until tune/preview |
| Home / Guide shell | **0–1** | Preview may call `PreviewPlayerManager` → `LivePlayerManager` MINI |
| Guide preview playing | **1** | Single `LivePlayerManager.player` L80 |
| Fullscreen live (`PlayerScreen`) | **1** | Same instance, mode FULLSCREEN |
| Split View / MultiView | **≤ maxPaneCount** | `MultiPanePlaybackPool.kt:18` "max 4 pane slots"; `LowEndDeviceMode.kt:73–75` → **2** on ≤2GB |
| Live handoff to pane 0 + extra panes | **≤ maxPaneCount** | `adoptExistingPlayer` does not create duplicate for pane 0 |
| VOD `DirectPlayerScreen` | **+1** | Separate VM player; orchestrator may release live first |
| Picture-in-Picture | **1** active | Android PiP — same player instance typically |
| Background (app backgrounded) | **0** after timeout | `AppPlayerLifecycleCoordinator.kt:70` → `releasePlayerResources` |

**Theoretical maximum (high-end profile):** 4 (pool) + 1 (VOD if not orchestrated) = **5** — orchestrator normally prevents overlap.

**Low-end (Chromecast profile):** `maxPaneCount = 2` (`LowEndDeviceMode.kt:73`), `decodeOnlyMultiPaneAudio = true` L92.

---

## Buffer / decoder memory (configured, not measured)

| Setting | Low-end | High-end | Source |
|---------|---------|----------|--------|
| `maxBufferMsCap` | 60_000 ms | 300_000 ms | `LowEndDeviceMode.kt:86, 105` |
| Max video (track selector) | 1280×720 @ 2.5 Mbps | 1920×1080 @ 8 Mbps | `PlayerFactory.kt:91–96` |
| Back buffer (live stability) | 15_000 ms | 30_000 ms | `PlayerFactory.kt:58–59` |

---

## Decoder tracking

`DecoderPressureTracker.kt` L25–76 — tracks concurrent players, surfaces, active video decoders. Logs pressure levels WARN/CRITICAL when limits exceeded (`DeviceDecoderLimits`).

---

## Release paths

| Path | Trigger | File:Line |
|------|---------|-----------|
| Background timeout | App background | `AppPlayerLifecycleCoordinator.kt:70` |
| Orchestrator teardown | Exclusive playback | `PlaybackOrchestrator.kt:189, 266` |
| Pool pane release | User exits split | `MultiPanePlaybackPool.kt:197–218` |
| Live destroy | Settings rebuild / stop | `LivePlayerManager.kt:856+` |
| VOD screen leave | Compose dispose | `DirectPlayerScreen.kt:427–430` |

---

## LMKD relevance

ExoPlayer + MediaCodec typically dominates **native heap** (not fully visible in `Runtime.getRuntime()`). `DecoderPressureTracker` + `PlaybackDiagnostics.logMemory` are required to correlate Java heap with decoder pressure.

**Risk:** Multiple panes on 2GB devices with `maxPaneCount=2` still allows **2 decoders + 1 preview** if orchestration fails.
