# EPG_RESOLVER_RISK_REPORT.md

## Entry point

| Item | Value |
|------|-------|
| File | `IptvRepositoryImpl.kt` |
| Method | `rebuildEpgLinkResolver(playlistId: Long)` |
| Lines | 1376–1397 |
| Caller | `ensureEpgLinkResolver` L1370–1373 → `programsWindowForChannels` |
| Thread | `Dispatchers.IO` (via repository suspend callers) |
| Cache | `epgLinkResolversByPlaylist: MutableMap<Long, EpgChannelLinkResolver>?` L274 |

## Declared but unused cap

```kotlin
// IptvRepositoryImpl.kt:247
private const val MAX_RESOLVER_SOURCE_CHANNELS = 5_000
```

**Grep confirms:** constant appears only at definition line — **not enforced** in `rebuildEpgLinkResolver`.

---

## Collection-by-collection breakdown

### Step 1: `refs` — `linkedMapOf<String, XmlTvChannelRef>()`

| Property | Value |
|----------|-------|
| Type | `LinkedHashMap<String, XmlTvChannelRef>` |
| Population | `epgSourceChannelDao.bySource("xmltv:$playlistId")` L1379–1381 |
| Second pass | `programDao.distinctChannelEpgIdsForPlaylist(playlistId)` L1382–1384 via `putIfAbsent` |
| Element type | `XmlTvChannelRef(id, displayName)` — two `String` fields (`EpgChannelLinkResolver.kt` L3–6) |
| Max elements | **UNBOUNDED** — all source rows + all distinct programme channel ids for playlist |
| Copies | One `XmlTvChannelRef` per unique epgId key |
| Lifetime | Copied into `EpgChannelLinkResolver` then stored in `epgLinkResolversByPlaylist` until L1431 / L2220 |

### Step 2: `learnedMappings`

| Property | Value |
|----------|-------|
| Type | `Map<String, String>` via `.associate` L1385–1387 |
| Query | `epgLearnedMappingDao.all()` — **no LIMIT** (`EpgLearnedMappingDao.kt` L20) |
| Scope | **GLOBAL** — not filtered by `playlistId` |
| Max elements | Row count in `epg_learned_mappings` table — **UNBOUNDED** |
| Lifetime | Held inside each per-playlist resolver instance |

### Step 3: `EpgChannelLinkResolver` constructor allocations

File: `EpgChannelLinkResolver.kt` L41–63

| Collection | Type | Count | Notes |
|------------|------|-------|-------|
| `channels` | `List<XmlTvChannelRef>` | `xmlTvChannels.distinctBy { it.id }` L42 | N refs |
| `byExactIdLower` | `Map<String, XmlTvChannelRef>` | `associateBy { id.lowercase() }` L49 | N entries |
| `byNormalizedId` | `Map<String, XmlTvChannelRef>` | `buildMap` L50–55 | ≤ N entries |
| `byNormalizedName` | `Map<String, XmlTvChannelRef>` | `buildMap` L56–61 | ≤ N entries |
| `learnedEpgIdByNormalizedName` | `Map<String, String>` | L62 | M learned rows |
| `fuzzyCandidateDisplayNames` | `List<String>?` | L44–47 | **Only if N ≤ 2_500** (`FUZZY_MATCH_MAX_CHANNELS` L107) |

**Copy multiplier:** For N channel refs, resolver holds approximately **4×N map entries** plus lists.

### Step 4: `epgLinkResolversByPlaylist` cache

| Property | Value |
|----------|-------|
| Type | `MutableMap<Long, EpgChannelLinkResolver>` L1393–1395 |
| Growth | +1 resolver per distinct `playlistId` that loads EPG window |
| Shrink | `epgLinkResolversByPlaylist = null` on `notifyEpgLinksUpdated` L1431 and post-`refreshEpgNow` L2220 |
| Survives refresh | **Yes** — until explicit null; rebuild creates new resolver objects (old eligible for GC) |

---

## Loops and copies

| Loop | File:Line | Operation |
|------|-----------|-----------|
| Source channel insert | L1379–1381 | `forEach { refs[epgId] = XmlTvChannelRef(...) }` |
| Programme id insert | L1382–1384 | `forEach { refs.putIfAbsent(epgId, ...) }` |
| Learned mapping associate | L1385–1387 | Full table scan → new Map |
| Resolver init maps | `EpgChannelLinkResolver.kt` L49–61 | 3× iterate `channels` list |
| Fuzzy list build | L44–45 | `channels.map { displayName }` if N ≤ 2500 |

---

## Worst-case sizing (formulaic, not device-specific)

Let:
- **S** = `epgSourceChannelDao.bySource("xmltv:playlistId").size`
- **P** = `programDao.distinctChannelEpgIdsForPlaylist(playlistId).size`
- **L** = `epgLearnedMappingDao.all().size`
- **N** = `|refs|` ≤ S + P (deduped by epgId key)

Resolver memory scales as **O(N + L)** with constant factor ≥4 for maps inside `EpgChannelLinkResolver`.

**Fuzzy matching:** Disabled when N > **2_500** (L43–47, L107).

**Multi-playlist:** Each playlist id adds another full resolver (including global **L** learned mappings duplicated per resolver).

---

## Unbounded / never-shrinking findings

| Finding | Evidence | Severity |
|---------|----------|----------|
| No channel cap on resolver build | L247 unused | **P0** |
| Global learned mappings in every resolver | L1385–1387, DAO `all()` L20 | **P0** |
| Per-playlist resolver map grows with playlist count | L1393–1395 | **P1** |
| Old resolvers only cleared on EPG notify/refresh | L1431, L2220 | **P1** |
| `programsWindowForChannels` triggers resolver per playlist | Caller chain | **P1** |

---

## Recommended fixes (code locations)

1. Enforce `refs` size ≤ `MAX_RESOLVER_SOURCE_CHANNELS` before constructing resolver (`IptvRepositoryImpl.kt:1378–1384`).
2. Replace `epgLearnedMappingDao.all()` with playlist-scoped query or `LIMIT` + lazy load (`L1385–1387`).
3. Share one learned-mapping map across resolvers (singleton, invalidated on mapping DAO change).
4. Log `refs.size`, `learnedMappings.size`, and heap in `StartupTrace` (instrumented L1376).
