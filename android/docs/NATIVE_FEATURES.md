# Native feature status

Sundown is Android-native: Kotlin + Jetpack Compose owns the UI and Android integration; Rust/UniFFI owns CPU-heavy metadata/artwork/index operations; Media3/ExoPlayer owns playback and MediaSession owns background controls. No web runtime is included.

Implemented in this build:

- MediaStore device music discovery with Android 13+ `READ_MEDIA_AUDIO` and Android 12-and-earlier `READ_EXTERNAL_STORAGE` permission handling.
- SAF folder access with persisted grants and safe pruning only after a complete provider walk and successful parsing pass.
- Hybrid metadata extraction: `MediaMetadataRetriever` supplies real duration and broad container compatibility; the Rust parser fills tag/art gaps.
- Real duration persistence and `mm:ss` / `h:mm:ss` formatting.
- BLAKE3-based stable library/artwork keys with SHA-256 only as the explicit native-unavailable fallback.
- Optional SQLite FTS5 search accelerator with runtime feature detection and in-memory search fallback.
- Incremental scans keyed by stable identity, file size, modification time, and artwork-cache presence.
- Multi-level artwork caching, coalesced decode jobs, bounded preload, and compact WebP row-art cache on API 30+ when it actually saves space.
- Queue management and MediaSession notifications.
- Native Android back navigation and predictive-back callback support.
- Fullscreen status-bar hiding while preserving native gesture navigation.
- High-refresh display left under Android/device control rather than forcing 60 Hz.
- No `INTERNET` permission and no online metadata/cloud dependency.

Deliberately not forced into the APK:

- Zstandard is not used for the live Room/FTS database because the database engine already provides page-level storage and queries; adding another compression layer would add CPU/native footprint without an established measured benefit.
- AVIF is not used for generated caches because the Android public bitmap encoder does not provide a stable cross-version AVIF encoding API in the supported min-SDK range; the cache therefore chooses JPEG or WebP based on actual size/compatibility.
- Lofty is not added as a parallel parser because SAF/MediaStore URIs are not guaranteed to expose filesystem paths; the mature Android extractor plus bounded Rust parser avoids copying entire files or introducing a redundant parser dependency.
