# Sundown

Sundown is a native Android music player engineered for a high-fidelity, offline-first experience. It combines a skeuomorphic visual identity inspired by classic digital music players with modern high-refresh-rate performance.

## Core Features

- **Pixel-Perfect Skeuomorphism**: A dimensional, glossy UI with stacked album artwork and convincing depth.
- **High-Performance Rendering**: Engineered for smooth 90/120Hz scrolling and responsive animations.
- **Offline-First & Native**: Built with Kotlin and Jetpack Compose. No WebViews, no streaming, no cloud dependencies.
- **Local Library**: Full support for Artists, Albums, Songs, Genres, and Playlists indexed directly from device storage.
- **Advanced Media Support**: Powered by Media3 and ExoPlayer for robust playback and seamless system integration.
- **Privacy by Design**: Requires no Internet permission. Your library stays on your device.

## Technology Stack

- **Language**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose
- **Media Engine**: Android Media3 (ExoPlayer)
- **Local Storage**: MediaStore + Custom high-performance indexing
- **Image Pipeline**: Custom optimized artwork extraction and caching

## Build & Development

To build the project, open the `android` directory in Android Studio. Ensure you have the latest stable Android Gradle Plugin and Kotlin compiler.

For a release build:
1. Configure your signing credentials in `android/app/build.gradle.kts` (or provide them via environment variables).
2. Run `./gradlew assembleRelease` to generate a universal signed APK.

## License

Copyright © 2026 Nazatric. All rights reserved.
