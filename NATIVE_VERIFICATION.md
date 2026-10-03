# Native source audit and verification status

Status recorded 2026-10-04. The repository is Android-native and offline-only. This audit covers the current working tree; source inspection is not a substitute for compilation, device testing, signing, or a release artifact.

## Requested categories

| Category | Status | Evidence / limitation |
|---|---|---|
| Native-only and offline runtime | PASS (source) | Tracked-file inventory found no web runtime, HTML/CSS/JS assets, SVG artwork assets, or iOS project. Android app manifest declares no `INTERNET` permission. |
| UI/UX preservation | PASS (diff scope) | No screen, component, theme, typography, color, spacing, or navigation-layout files were changed. Screenshot/rendering parity remains UNVERIFIED. |
| MediaStore discovery and API permissions | PASS (source) | Read-permission branch, MediaStore scan, path/generation fingerprints, and debounced change observer reviewed; device/provider behavior is UNVERIFIED. |
| SAF discovery and persisted grants | PASS (source) | Tree grants, individual URI ingestion, source fingerprints, and guarded deletion flow reviewed; provider behavior is UNVERIFIED. |
| Incremental scans and metadata | PASS (source) | Changed rows are parsed in bounded batches off the UI thread; Kotlin/Room and Rust parser changes reviewed. Build and device behavior are UNVERIFIED. |
| Playback, queue, state restoration and notification artwork | PASS (source) | Media3 service/controller, checkpoint updates (including empty queues), artwork metadata path, and local wake mode reviewed; background/device behavior is UNVERIFIED. |
| Artwork bounds, invalidation and caches | PASS (source) | Content-derived IDs, bounded previews, memory/disk cache limits and intentional-eviction markers reviewed; image quality and memory behavior are UNVERIFIED. |
| Room, FTS5 and search | PASS (source) | Room 1→2 migration and incremental FTS5/fallback implementation reviewed. Host SQLite 3.40.1 FTS5 create/insert/update/delete/substring probe PASS; Android SQLite availability and large-library performance are UNVERIFIED. |
| Release APK configuration | PASS (source) | ABI splits are disabled and optional environment/secret-based universal-APK signing is configured. CI assembled/uploaded the debug APK; signed release packaging was skipped because signing secrets are not configured. |
| iOS / IPA | BLOCKED / NOT APPLICABLE | No native iOS project exists, so no IPA was built or claimed. |
| Android/Rust compilation and unit tests | PASS (CI) / BLOCKED locally | GitHub Actions run [37152109273](https://github.com/Nazatric/Sundown/actions/runs/37152109273) for implementation commit `0466d274ce18f5851d332fcf6ae1a98b2d384ad5` passed Rust unit tests, Android unit tests, and debug APK assembly. This sandbox still lacks JDK, Gradle, Android SDK/NDK, Rust/Cargo/cargo-ndk, Kotlin compiler and adb. |
| Device, screenshot and performance validation | UNVERIFIED | No emulator or physical Android device is available here. |

## Static checks completed

- `git diff --check`: PASS.
- Python `xml.etree.ElementTree` parsing of all 8 Android XML files: PASS.
- Android manifest permission inspection: PASS — no declared `android.permission.INTERNET`.
- Tracked/source-tree inventory: PASS — no web source/runtime extensions, iOS source directory, SVG artwork assets, or raster artwork assets were found in the audited checkout.
- Host SQLite 3.40.1 FTS5 trigram probe: PASS for external-content triggers, substring phrase matching, updates, and deletes. This does **not** validate FTS5 on Android.

## Not performed / not claimed

No local Kotlin or Rust compilation/test run, emulator/device install, playback/SAF exercise, screenshot comparison, performance/memory benchmark, signed release APK, or IPA build was performed in this sandbox. The linked CI run verifies the implementation commit's Rust tests, Android unit tests and debug APK assembly; it does not verify device behavior, visual parity, or release signing.

## Follow-up device and release checks

On a physical device or emulator, exercise API 32 and API 33+ permissions, SAF folder grants, MediaStore changes, selected-file URI grants, metadata/artwork, MediaSession background playback, queue/process restoration, back/predictive back and system insets. Compare screenshots at the documented viewport and profile a large local library. Build a signed universal APK only when valid signing material is configured; do not build an IPA without a native iOS project.
