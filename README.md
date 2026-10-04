# Sundown

A native, offline Android music player with a pixel-perfect skeuomorphic UI.

## Features

- **Glossy layered album artwork** — five-layer card stacks with specular highlights, dimensional shadows, and curved gloss
- **Smooth 90/120 Hz rendering** — stable Compose keys, remembered allocations, 4-thread parallel artwork decode, controlled recomposition
- **Multi-level artwork caching** — LRU memory cache, persistent disk cache (WebP), near-visible prefetching, 80ms fade-in for cache misses
- **Fast FTS5 search** — SQLite full-text indexed across title, artist, album, genre
- **Incremental MediaStore indexing** — generation-based change tracking without full rescans
- **Robust playback** — Media3/ExoPlayer, MediaSession, system notification, background playback
- **Modern navigation** — PredictiveBackHandler, gesture-back, reliable sheet transitions
- **Edge-to-edge fullscreen** — status bar hidden, proper inset handling
- **Rust-powered core** — BLAKE3 hashing, metadata parsing, artwork processing via UniFFI
- **Completely offline** — no Internet permission, no cloud, no streaming, no telemetry

## Architecture

| Layer | Technology |
|---|---|
| UI | Kotlin · Jetpack Compose |
| Media | Media3 · ExoPlayer · MediaSession |
| Storage | Room · SQLite FTS5 · Preferences DataStore |
| Native Core | Rust · UniFFI · BLAKE3 |
| File Access | Storage Access Framework · MediaStore |

## Source Layout

```
android/app/       Kotlin/Compose UI, library, playback, Android integration
android/rust/      sundown-core: metadata, artwork, indexing, BLAKE3
android/docs/      Build notes, audit, feature coverage
.github/workflows/ CI: Rust tests, Android tests, debug + release APK
```

## Build

Requirements: JDK 17, Android SDK 34, NDK 26.3, Rust stable with Android targets, `cargo-ndk`.

```bash
cd android
gradle :app:assembleDebug        # debug APK
gradle :app:assembleRelease      # release APK (requires signing config)
```

Release signing uses environment variables: `SUNDOWN_STORE_FILE`, `SUNDOWN_STORE_PASSWORD`, `SUNDOWN_KEY_ALIAS`, `SUNDOWN_KEY_PASSWORD`. CI uses equivalent GitHub secrets.

## Release

See [Releases](https://github.com/Nazatric/Sundown/releases) for the latest universal APK.

## License

Copyright © 2026 Nazatric. All rights reserved.
