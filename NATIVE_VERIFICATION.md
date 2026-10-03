# Native-only verification

This source tree is the consolidated native Sundown implementation.

## Confirmed statically

- No HTML, CSS, JavaScript/TypeScript, React/JSX, Vite, web manifest, service worker, or web package files remain.
- The Android app does not declare `android.permission.INTERNET`.
- No WebView or Android web-runtime calls are present in `android/app/src/main`.
- AndroidManifest.xml parses successfully.
- GitHub Actions workflow YAML parses successfully.
- Gradle Kotlin DSL and Cargo manifest files are present and internally consistent with the checked source tree.
- Kotlin sources were passed through the Kotlin compiler parser; the local environment lacks the Android/Compose dependency classpath, so full type/build validation cannot be performed here.
- The Rust source tree was structurally inspected; the local environment does not contain the Rust/Cargo toolchain, so a Rust build cannot be performed here.

## Implemented native fixes/features

MediaStore device-music discovery and Android-version-specific audio permission flow; persisted SAF folders and individual file URI access; hybrid Android MediaMetadataRetriever + Rust metadata parsing; real duration persistence; BLAKE3-derived artwork identity; incremental scan/prune behavior; FTS5 library search accelerator with in-memory fallback; bounded scan parallelism; coalesced multi-level artwork loading with compact WebP thumbnails where beneficial; Media3 background playback and MediaSession metadata/artwork; queue support; correct playlist-name ellipsis behavior; Songs A–Z indexing aligned to artist sorting; native BackHandler/predictive-back integration; immersive edge-to-edge presentation and device refresh-rate usage; offline-only runtime with no network permission.

## Not claimed

An on-device APK install, screenshot comparison, 90/120 Hz frame-pacing measurement, memory/battery benchmark, Rust cross-compilation, or release APK signing was not performed in this packaging environment because the required Android SDK/NDK, Gradle runtime/dependencies, and Rust Android toolchain are unavailable here. The included CI workflow is configured to perform those builds in GitHub Actions when the required environment and release signing secrets are available.
