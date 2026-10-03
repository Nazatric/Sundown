# Sundown — native Android

Sundown is a native Android music player built from Kotlin + Jetpack Compose + Rust, with Media3/ExoPlayer playback, Room persistence, Preferences DataStore, Android Storage Access Framework, and UniFFI for the Rust core.

This repository contains the Android implementation only. The former web application and web build files are intentionally absent. The Android runtime does not use WebView, HTML, CSS, JavaScript, a service worker, or online metadata.

## Native merge basis

The project uses the stronger native implementations from the two supplied source trees rather than blindly combining them. The current tree also folds in the native-only fixes for real MediaStore discovery/permissions, reliable duration extraction, FTS5 search acceleration, BLAKE3 identity keys, smarter artwork caching, A-Z navigation, and playlist label layout. The Rust/UniFFI build pipeline, metadata/artwork handling, incremental library scan, MediaSession lifecycle, artwork cache, duration handling, state architecture, and native navigation come from the stronger implementation. The additive native features retained from the other tree are the queue UI, and backup rules.

## Native tree

- `android/app/` — Kotlin/Compose Android application.
- `android/rust/sundown-core/` — Rust metadata/artwork/index core exposed with UniFFI.
- `android/docs/` — native build, parity, audit, and feature documentation, including the implemented feature/technology status.
- `.github/workflows/android.yml` — Rust tests and reproducible Android CI.

## Build

Use Android Studio with JDK 17, Android SDK 34, NDK 26.3.11579264, Rust stable with Android targets, and `cargo-ndk`. From `android/`, run:

```text
gradle :app:testDebugUnitTest :app:assembleDebug
```

The Gradle build compiles the Rust core for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, then generates Kotlin UniFFI bindings from the built native library before Kotlin compilation. A universal APK is configured; ABI splitting is disabled.

Release signing is optional and uses environment variables only; signing material is never stored in source control. The Android release target is a single signed universal APK. AAB is not a release target. The iOS release target is a signed IPA when a native iOS target is present.

## Verification status

Static source/resource validation can be performed in this archive. A complete Android/device build was not run inside the packaging environment because the Android SDK/NDK, Gradle distribution, Rust Android targets, and adb are not installed here. CI is the authoritative build environment.
