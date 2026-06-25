# VOD Catalog Onboarding — "Building Recommendations" Progress Report

**Date:** 2026-06-18  
**Scope:** Investigate-only (no behavior changes in this report)  
**Primary file:** `app/src/main/java/com/grid/tv/ui/component/VodCatalogOnboarding.kt`

## Executive Summary

The onboarding step **"Building recommendations" is not a separate recommendation-engine job**. It is a **presentation label** shown when:

1. Movie and series ingest phases have **already finished** (`moviesPhaseFinished` && `seriesPhaseFinished`), and  
2. The UI is still waiting for **catalog surface assembly** (wall rows / browse rows / paged items) to meet readiness thresholds.

Progress for that step is **hardcoded to 85%** (`onboardingStepProgressFraction` → `0.85f`). There is **no `RecommendationBuilder`**, **`ImportStep` enum tied to real recommendation work**, or synchronous recommendation pass on the critical path in this codebase.

Users who see the UI **jump straight to "Building recommendations"** are most likely experiencing **Scenario A (staged UI)** combined with **fast backend ingest** — not a 45s recommendation bottleneck.

---

## How Steps Are Resolved

`resolveVodOnboardingStep()` (`VodCatalogOnboarding.kt:129-144`) maps state in strict order:

| Order | Step | Condition |
|-------|------|-----------|
| 1 | Preparing your library | `catalogLoading` && zero movies/series loaded |
| 2 | Scanning playlists | `progress.isLoading` && movies phase not finished && zero movies loaded |
| 3 | Organizing movies | `!moviesPhaseFinished` |
| 4 | Organizing series | movies finished && `!seriesPhaseFinished` |
| 5 | **Building recommendations** | pipeline complete && `effectiveCatalogCount() > 0` && `!isVodCatalogContentReady()` |
| 6 | Finalizing | otherwise |

**Key insight:** Step 5 triggers when ingest is done but **UI content gates** are not met:

```kotlin
// ALL tab
wallRowCount >= 1 && wallItemCount >= 1

// Movies / Series tabs
paged items + categories, browse rows, or phase-complete fallbacks
```

`isVodCatalogContentReady()` reads **Compose-facing counts** (wall rows, browse rows, categories) — not a recommendation job completion flag.

---

## Is Progress Tied to Real Work?

| Step | Progress fraction | Tied to real work? |
|------|-------------------|-------------------|
| Preparing | 0.05 (fixed) | Loosely — initial load flag |
| Scanning | 0.15 (fixed) | Loosely — `isLoading` |
| Organizing movies | 0.2 + `moviesProgressFraction() * 0.25` | **Yes** — `moviesLoaded / moviesTotal` |
| Organizing series | 0.5 + `seriesProgressFraction() * 0.25` | **Yes** — `seriesLoaded / seriesTotal` |
| Building recommendations | **0.85 (fixed)** | **No** — placeholder until content ready |
| Finalizing | 0.95 (fixed) | Dismissal debounce (`READINESS_STABILIZATION_MS = 400ms`) |

Movie/series fractions come from `VodCatalogProgress` (`domain/model/VodCatalogProgress.kt`) fed by repository ingest — real work.

The "Building recommendations" fraction does **not** advance with any background job; it sits at 85% until `isVodCatalogContentReady()` flips true.

---

## Why Earlier Steps Appear Skipped

`VodCatalogOnboardingPanel` uses a **monotonic step index** (`displayedStepIndex`):

```kotlin
LaunchedEffect(currentStep) {
    if (currentStep.ordinal >= displayedStepIndex) {
        displayedStepIndex = currentStep.ordinal
    }
}
```

Effects:

- Steps only move **forward** — never rewind if state regresses.
- If movies/series phases complete quickly (or were cached), the resolver **skips directly** to step 5 on the first frame where content is not yet ready.
- All prior steps render as "reached" (dimmed bullets) even if they were never visibly active.

This matches user reports of immediately landing on "Building recommendations."

---

## Is Recommendation Generation the Bottleneck?

**No dedicated recommendation generation stage exists in the onboarding pipeline.**

`BUILDING_RECOMMENDATIONS` is named for UX; the actual wait is for:

- Wall row assembly (`wallRows` in `VodHubViewModel` content state)
- Browse grid / category hydration
- Optional continue-watching / personalization rows (personal rows are **excluded** from ALL-tab readiness via `isPersonalHistoryWallRow`)

`recommendedForYou` / `trendingNow` in hub content state are populated as part of normal VOD hub assembly — not gated by a separate onboarding worker.

**Likely timing on fast devices:**

| Phase | Typical duration |
|-------|------------------|
| Scan playlists | Sub-second to few seconds (depends on playlist size) |
| Organizing movies | Proportional to `moviesLoaded/moviesTotal` |
| Organizing series | Proportional to `seriesLoaded/seriesTotal` |
| "Building recommendations" | **Wall/browse assembly** — variable, usually seconds unless very large catalog / low-end device |
| Finalizing | 400ms debounce minimum |

If onboarding feels "stuck" on step 5, investigate **wall row build** and **Room/query latency**, not a recommendation engine.

---

## Synchronous / Critical Path?

Onboarding is **presentation-only**:

```kotlin
/** Uses existing VodCatalogProgress and row/category counts — does not trigger loads. */
fun shouldShowVodCatalogOnboarding(...)
```

Nothing in `VodCatalogOnboarding.kt` starts loads or blocks the main thread. Dismissal depends on upstream `VodHubViewModel` / repository work already in flight.

---

## Recommendations Before Changing Code

1. **Rename step 5** to something accurate (e.g. "Preparing your catalog" or "Assembling rows") to avoid false performance signals.
2. **Tie step 5 progress** to a real metric (e.g. `wallItemCount / target`, browse row count) instead of `0.85f`.
3. **Allow step index to reflect actual dwell time** — e.g. animate through intermediate steps with minimum display duration, or remove monotonic skip.
4. **Add timing instrumentation** — log timestamps when each `VodOnboardingStep` becomes active and when `isVodCatalogContentReady` flips true (`adb logcat` tag suggestion: `VOD_ONBOARDING`).
5. **Do not optimize a "recommendation builder"** — none exists; profile wall-row assembly and DB reads instead.

---

## Files Referenced

- `app/src/main/java/com/grid/tv/ui/component/VodCatalogOnboarding.kt` — step resolver, progress fractions, UI panel
- `app/src/main/java/com/grid/tv/domain/model/VodCatalogProgress.kt` — ingest progress model
- `app/src/test/java/com/grid/tv/ui/component/VodCatalogOnboardingTest.kt` — tests for building-recommendations gating
