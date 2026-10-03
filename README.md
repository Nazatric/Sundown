# Sundown — native Android

Sundown is a native, offline Android music player built with Kotlin, Jetpack Compose, Rust/UniFFI, Media3/ExoPlayer, Room, Preferences DataStore, and Android's Storage Access Framework. It indexes music through user-granted SAF folders and Android MediaStore; playback reads local content URIs. There is no web runtime, WebView, online metadata lookup, or network permission.

## Source layout

- `android/app/` — Kotlin/Compose UI, local library, playback service, and Android integration.
- `android/rust/sundown-core/` — bounded metadata parsing, embedded-art processing, normalized key helpers, and BLAKE3 hashing.
- `android/docs/` — build, source audit, feature coverage, and validation notes.
- `.github/workflows/android.yml` — Rust/Android CI and conditional signed-release APK packaging.

The checked-in tree has no web application, SVG artwork assets, or native iOS project. The existing Android vector drawables remain vector resources.

## Build

Use JDK 17, Android SDK 34, NDK 26.3.11579264, Rust stable with Android targets, and `cargo-ndk`. There is no checked-in Gradle wrapper. From `android/`, run:

```text
gradle :app:testDebugUnitTest :app:assembleDebug
```

The Gradle build compiles the Rust core for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, then generates UniFFI Kotlin bindings before compiling the app. ABI splitting is disabled so the Android release output is a universal APK. Release signing is configured through environment variables/GitHub secrets; signing material is not stored in source control. AAB packaging is not the release target. No IPA target is configured because this repository has no native iOS project.

## Validation

See [`NATIVE_VERIFICATION.md`](NATIVE_VERIFICATION.md) for the current source-audit results and the exact build/device checks that remain unverified or blocked. Do not treat older documentation or CI runs from different commits as validation of the current revision.
