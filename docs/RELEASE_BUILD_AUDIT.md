# Release vs Debug Build Audit

Generated from static analysis of `app/proguard-rules.pro`, dependencies, and VOD/live code paths.

## Summary

Most VOD catalog data uses **Room + org.json** (not Gson/Moshi/Retrofit). Release-only failures on physical Chromecast are **most likely** credential/network/catalog-load issues or **language filter emptying the paging grid**, not poster/Coil stripping.

## Serialization stack

| Technology | In use? | ProGuard coverage |
|------------|---------|-------------------|
| Gson | No | N/A |
| Moshi | No | N/A |
| Retrofit | Dependency only (no interfaces) | Interface method keep present |
| kotlinx.serialization | `MovieDetailsDto`, `GetMovieDetailsRequest` | `@Serializable` keep rule |
| org.json.JSONObject | Xtream VOD/series, TMDB | Plain Kotlin — no reflection |
| Room | All VOD entities | `-keep class com.grid.tv.data.db.**` |

## Classes only referenced through reflection

| Class / area | Risk | Notes |
|--------------|------|-------|
| Hilt `@EntryPoint` interfaces | Medium | `EntryPointAccessors.fromApplication(...)` |
| Room generated `*_Impl` | Low | Broad DB package keep |
| `@HiltWorker` workers | Medium | No explicit worker keep |
| Supabase/Ktor response bodies | Medium | Minimal serialization keeps |
| Coil image pipeline | Low–Medium | Relies on library consumer rules |
| androidx.paging generated sources | Medium | `-dontwarn` only; no explicit PagingSource keep |

## Recommended keep rules (not yet applied)

```
-keep class * extends androidx.paging.PagingSource { *; }
-keep @dagger.hilt.EntryPoint interface * { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keepclassmembers class io.github.jan.supabase.** { *; }
```

## Likely release-only breakpoints (ranked)

1. **Missing Xtream password** — `SecureCredentialStore` is device-bound; playlist metadata can exist without decryptable password → VOD fetch skipped.
2. **Language filter** — `VodLanguagePreferenceStore` can reduce paged `itemCount` to 0 while DB has titles.
3. **Silent VOD pipeline failures** — `runVodPipelineCatching` swallows exceptions; slow Chromecast network → empty catalog.
4. **Series-only freshness** — `isVodCatalogFresh()` true when series count > 0 even if movies failed.
5. **PagingSource R8 edge case** — if paging load fails silently, grid shows 0 items.
6. **BuildConfig secrets** — empty TMDB/Supabase keys affect enrichment/hero, not Room grid.

## Debug checklist (physical Chromecast release)

Filter logcat: `VodCatalog`, `VodCatalogPipeline`

1. `PROVIDER_CONNECTED hasCredentials=true`
2. `VOD_LOAD_COMPLETE movies=N series=M` with N,M > 0
3. `UI_ITEMS_RENDERED count=N` matches repository counts
4. If `CATALOG_EMPTY` — check `reason`, `filtered`, language prefs

## Live / fullscreen (separate from VOD catalog)

VOD uses a **separate ExoPlayer** from live. Live fullscreen black screen after VOD is a **surface ownership race**, not a ProGuard issue.
