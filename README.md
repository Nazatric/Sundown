# Sundown

A native, offline Android music player built with Kotlin, Jetpack Compose, Rust/UniFFI, Media3/ExoPlayer, Room, and Android's Storage Access Framework.

## Features

- **Pixel-perfect skeuomorphic UI** — glossy layered album artwork stacks, dimensional shadows, polished metal controls
- **Offline-first** — no Internet permission, no cloud, no streaming, no telemetry
- **High-refresh-rate performance** — engineered for smooth 90/120 Hz scrolling and animations
- **Full local library** — Artists, Albums, Songs, Genres, Playlists with A–Z index navigation
- **Fast search** — SQLite FTS5 indexed search across title, artist, album, genre
- **Robust playback** — Media3/ExoPlayer with MediaSession, system notification, background playback
- **Incremental indexing** — MediaStore generation tracking, SAF folder scanning, handles adds/deletes/changes efficiently
- **Multi-level artwork caching** — memory cache, disk cache, background decoding, near-visible prefetching
- **Rust-powered core** — BLAKE3 hashing, metadata parsing, artwork processing via UniFFI

## Architecture

| Layer | Technology |
|---|---|
| UI | Kotlin · Jetpack Compose |
| Media | Media3 · ExoPlayer · MediaSession |
| Storage | Room · SQLite FTS5 · Preferences DataStore |
| Native Core | Rust · UniFFI · BLAKE3 |
| File Access | Storage Access Framework · MediaStore |

## Source Layout

- `android/app/` — Kotlin/Compose UI, local library, playback service, Android integration
- `android/rust/sundown-core/` — metadata parsing, artwork processing, indexing, BLAKE3 hashing
- `android/docs/` — build notes, source audit, feature coverage, validation
- `.github/workflows/android.yml` — CI: Rust tests, Android tests, debug + release APK builds

## Build

Requirements: JDK 17, Android SDK 34, NDK 26.3.11579264, Rust stable with Android targets, `cargo-ndk`.

```bash
cd android
gradle :app:testDebugUnitTest :app:assembleDebug
```

The Gradle build compiles the Rust core for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, generates UniFFI Kotlin bindings, then builds the universal APK (ABI splitting disabled).

### Release Build

Release signing uses environment variables: `SUNDOWN_STORE_FILE`, `SUNDOWN_STORE_PASSWORD`, `SUNDOWN_KEY_ALIAS`, `SUNDOWN_KEY_PASSWORD`. CI uses equivalent GitHub secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

```bash
SUNDOWN_STORE_FILE=release.keystore \
SUNDOWN_STORE_PASSWORD=... \
SUNDOWN_KEY_ALIAS=... \
SUNDOWN_KEY_PASSWORD=... \
gradle :app:assembleRelease
```

## Release

See [Releases](https://github.com/Nazatric/Sundown/releases) for the latest universal APK.

## License

Copyright © 2026 Nazatric. All rights reserved.
