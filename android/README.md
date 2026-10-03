# Sundown — native Android project

This directory is the canonical Android implementation. It is Kotlin + Jetpack Compose + Rust with Media3/ExoPlayer, Room, Preferences DataStore, SAF, and generated UniFFI bindings.

The old web application is not part of this repository. There is no WebView or web runtime in the Android app.

## Native capabilities retained

The merged native tree keeps the stronger library/indexing, metadata, artwork, playback, state, navigation, and Rust/UniFFI implementations, plus the requested native queue management.

## Build requirements

JDK 17, Android SDK 34, Android NDK 26.3.11579264, Rust stable with `aarch64-linux-android`, `armv7-linux-androideabi`, and `x86_64-linux-android` targets, plus `cargo-ndk`.

The Gradle build compiles `sundown-core` for all supported ABIs and generates Kotlin UniFFI bindings from the arm64 library before Android compilation. This prevents the app from silently depending on the old fallback-only Rust wrapper. The release build is a single universal APK; AAB is not produced.

## Validation

The packaging environment does not contain the Android SDK/NDK or a configured Gradle/Rust Android toolchain, so a device install and screenshot/performance comparison were not performed while creating this archive.
