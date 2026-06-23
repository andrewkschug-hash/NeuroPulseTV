# VOD Catalog Chromecast Diagnostic Guide

Use after opening VOD on physical Chromecast release build:

```bash
adb logcat -s VodCatalog VodCatalogPipeline
```

## Decision tree

| Log pattern | Stage | Likely cause |
|-------------|-------|--------------|
| `PROVIDER_CONNECTED hasCredentials=false` | A | Missing Xtream password (secure + DB both empty) |
| `CATALOG_STAGE_FAILURE stage=credentials` | A | Username/password/server URL missing |
| `MOVIES_RECEIVED count=0` after load | B | Network fetch or parse failed — see `VodCatalogPipeline` |
| `MOVIES_RECEIVED count=N` (N>0) + `UI_ITEMS_RENDERED count=0` | C or D | Paging/UI or language filter |
| `CATALOG_STAGE_FAILURE stage=filter` | D | Language filter active, all titles filtered |
| `CATALOG_STAGE_FAILURE stage=ui` + DB>0 | C | PagingSource or Compose grid not rendering |

## Report template

```
Provider state:     PROVIDER_CONNECTED hasCredentials=? securePassword=? dbPassword=?
Movie count (DB):   MOVIES_RECEIVED count=?
Series count (DB):  SERIES_RECEIVED count=?
Browse rows:        UI_ITEMS_RENDERED VodHubMovieBrowseRows count=?
Paging items:       UI_ITEMS_RENDERED VodHubMoviesPaging count=?
Language filter:    preferredVodLanguages=? (empty = no filter)
Failure stage:      CATALOG_STAGE_FAILURE stage=? reason=?
```

## Emulator vs Chromecast comparison

Run same log capture on emulator debug and Chromecast release. Diff the report template rows.
