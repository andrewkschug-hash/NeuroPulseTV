# VOD / Chromecast Diagnostic Guide

Use after opening VOD on physical Chromecast or Android TV:

```bash
adb logcat -s VodCatalog VodCatalogPipeline PlaybackDiag JsonParseMetrics TuneLatency LiveFullscreen DecoderPressure
```

## Decision tree

| Log pattern | Area | Likely cause |
|-------------|------|--------------|
| `PROVIDER_CONNECTED hasCredentials=false` | Network/credentials | Missing Xtream password (secure + DB both empty) |
| `CATALOG_STAGE_FAILURE stage=credentials` | Network/credentials | Username/password/server URL missing |
| `VOD_NETWORK_FAILURE type=SSLHandshakeException` | Network/TLS | Chromecast TLS or cert issue |
| `VOD_NETWORK_FAILURE type=UnknownHostException` | Network/DNS | DNS failure on device |
| `MEMORY heapPct=85+` during refresh | Memory/OOM | Full catalog JSON held in RAM — skip refresh or OOM |
| `MEMORY stage=after_vod_parse heapPct=90+` | Memory/OOM | Parse peak — largest Chromecast hang/crash risk |
| `JSON parse on MAIN thread` | Main thread | Blocking UI — ANR risk |
| `PLAYBACK_ERROR codecCapability=true` | ExoPlayer/codec | H264/H265/AAC HW decode mismatch or bitrate too high |
| `PLAYBACK_ERROR code=DECODER_INIT_FAILED` | ExoPlayer/codec | MediaCodec init failure on device |
| `MOVIES_RECEIVED count=0` after load | Catalog pipeline | Network fetch or parse failed |
| `MOVIES_RECEIVED count=N` (N>0) + `UI_ITEMS_RENDERED count=0` | Paging/UI | Language filter or PagingSource not rendering |
| `CATALOG_STAGE_FAILURE stage=filter` | UI filter | Language filter active, all titles filtered |
| `Input dispatching timed out` | ANR | Main-thread stall >5s — check JsonParseMetrics + heap during VOD open |

## Emulator vs Chromecast comparison

Compare `DEVICE_PROFILE` and `CODEC_DECODER` lines from both devices:

```
adb logcat -d -s PlaybackDiag | grep -E "DEVICE_PROFILE|CODEC_DECODER|MEMORY|VOD_NETWORK"
```

Key diffs:
- `emulator=true` vs `emulator=false`
- `ramMb` / `memoryClass` / `lowEnd=true`
- `CODEC_DECODER mime=video/hevc supported=false` on Chromecast → HEVC streams will fail
- `heapPct` during `before_vod_parse` / `after_vod_parse`

## Report template

```
Device:             DEVICE_PROFILE model=? ramMb=? lowEnd=? emulator=?
Codecs:             video/avc=? video/hevc=? audio/mp4a-latm=?
Provider state:     PROVIDER_CONNECTED hasCredentials=? securePassword=? dbPassword=?
VOD fetch:          VOD_NETWORK_FETCH http=? bytes=? elapsedMs=?
Movie count (DB):   MOVIES_RECEIVED count=?
Series count (DB):  SERIES_RECEIVED count=?
Memory at parse:    MEMORY stage=after_vod_parse heapPct=?
Browse rows:        UI_ITEMS_RENDERED VodHubMovieBrowseRows count=?
Paging items:       UI_ITEMS_RENDERED VodHubMoviesPaging count=?
Playback error:     PLAYBACK_ERROR owner=? codecCapability=? code=?
Failure stage:      CATALOG_STAGE_FAILURE stage=? reason=?
```
