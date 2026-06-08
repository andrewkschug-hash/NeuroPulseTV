# NeuroPulseTV

Android TV IPTV player built with Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, Room, Retrofit, WorkManager, MVVM, and Hilt.

## Features

- Add playlist using M3U URL
- Add playlist using local file picker
- Parse and persist channels (group, logo, epg-id)
- Channel browser with group filtering and search
- Full-screen player with D-pad friendly overlay controls
- EPG screen with time x channel style rows
- Favorites list
- Settings actions for EPG refresh and background schedule
- Background EPG refresh via WorkManager

## Tech Stack

- UI: Jetpack Compose + androidx.tv (tv-foundation, tv-material)
- Playback: Media3 ExoPlayer (HLS, MPEG-TS/progressive TS, RTMP datasource)
- Persistence: Room
- Networking: Retrofit + OkHttp
- Background jobs: WorkManager
- Architecture: MVVM + Hilt dependency injection

## Project Structure

- app/src/main/java/com/neuropulse/tv
  - data
    - db (entities, daos, database)
    - network (Retrofit API + M3U/XMLTV parsers)
    - repository (TvRepositoryImpl)
  - domain
    - model
    - repository
  - presentation
    - navigation
    - screen
    - theme
    - viewmodel
  - worker

## Build

1. Open `NeuroPulseTV` in Android Studio (or VS Code with Android toolchain).
2. Ensure Android SDK 35 is installed.
3. Sync Gradle.
4. Run on Android TV emulator/device (API 26+).

## Notes

- Network security config currently allows cleartext streams for IPTV compatibility.
- On first launch, seed sample channels are created if no playlist exists.
- Local file picker imports plain M3U content.
