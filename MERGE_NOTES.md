# Native merge notes

Two supplied Sundown source archives were compared. Only native Android/Rust content was retained.

## Selection

- Native app architecture, library scanning, SAF handling, artwork cache, playback lifecycle, state separation, metadata fallback, duration handling, Rust/UniFFI build pipeline, and Rust metadata/artwork hardening: selected from the stronger implementation.
- Native queue management and backup rules: retained where they improved the requested native app.
- Old fallback-only `nativecore/SundownCore.kt` and the redundant `MetadataExtractor.kt`: not copied because the selected Rust/UniFFI + platform fallback path already covers their supported responsibilities with a reproducible native build.
- Web React/Vite source, web manifest, service worker, public web assets, and web build configuration: removed completely.

No APK was claimed as device-verified during packaging; the archive was source-integrated and statically checked instead.
