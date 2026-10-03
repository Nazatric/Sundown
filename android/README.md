# Sundown — native Android project

This is the canonical Android implementation: Kotlin and Jetpack Compose own the UI and Android integration; Room/DataStore own local persistence; Media3/ExoPlayer owns playback; Rust/UniFFI supplies bounded metadata/artwork processing and BLAKE3 helpers. The app is offline-only and contains no web runtime.

## Native capabilities

- Real MediaStore discovery with version-specific audio permissions and change observation.
- Persisted SAF folder access and individually selected local audio files.
- Incremental Room indexing, full-text substring-search acceleration where Android SQLite supports FTS5, and a Kotlin fallback.
- Hybrid Android/Rust metadata extraction, bounded cover previews and MediaSession artwork.
- Background MediaSession playback, queue/state restoration, and local-audio wake mode.

## Build requirements

JDK 17, Android SDK 34, Android NDK 26.3.11579264, Rust stable with `aarch64-linux-android`, `armv7-linux-androideabi`, and `x86_64-linux-android` targets, plus `cargo-ndk`. No Gradle wrapper is checked in.

The Gradle build compiles `sundown-core` for all supported ABIs and generates Kotlin UniFFI bindings from the arm64 library before Android compilation. ABI splits are disabled for a universal APK. Release signing is conditional on the configured signing environment; no signing material is checked in.

## Validation

The current checkout has not been Android/Rust built or device-tested in this environment. See [`docs/BUILD.md`](docs/BUILD.md) and the repository's [`NATIVE_VERIFICATION.md`](../NATIVE_VERIFICATION.md) for requirements and current status.
