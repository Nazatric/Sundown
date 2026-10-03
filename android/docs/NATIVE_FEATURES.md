# Native feature status

Sundown is Android-native and offline-only. Kotlin/Compose owns the UI and Android integration; Kotlin owns MediaStore/SAF discovery, Room persistence, grouping, search, and playback state; Rust/UniFFI supplies bounded metadata/artwork processing and BLAKE3 hashing/key helpers; Media3/ExoPlayer owns playback and the background MediaSession. No WebView, web runtime, or network permission is included.

## Implemented in source

- MediaStore device-music discovery with Android 13+ `READ_MEDIA_AUDIO` and Android 12-and-earlier `READ_EXTERNAL_STORAGE` permissions, relative-path fingerprints, API 30+ generation fingerprints, and debounced content-change observation.
- SAF folder access with persisted grants, path/document-URI/size/mtime fingerprints, and pruning only after a complete provider walk and successful parsing pass.
- Incremental one-off file ingestion that avoids reparsing unchanged imports.
- Hybrid metadata extraction: `MediaMetadataRetriever` supplies duration and broad container compatibility; the bounded Rust parser fills tag/art gaps, including ID3v2.2 text and `PIC` frames.
- Real duration persistence and shared `mm:ss` / `h:mm:ss` formatting.
- Content-derived artwork IDs (BLAKE3 of embedded bytes; SHA-256 fallback when the native library is unavailable), so changed cover bytes do not reuse album-name cache entries.
- Optional SQLite FTS5 trigram substring-search acceleration. Changed track documents are synchronized incrementally off the UI thread; short/non-ASCII queries and unsupported SQLite builds use the exact Kotlin substring fallback.
- Bounded scan parallelism and batched Room writes/deletes.
- Artwork previews bounded to 1024 px and 160 px, a bounded memory LRU, a 128 MiB disk cache, coalesced decode jobs, nearby-item preloading, and compact WebP row-art where it saves space. Intentional disk-LRU evictions are marked so scans do not repeatedly reparse the same covers; explicit cache clearing removes those markers.
- Media3 background playback, MediaSession artwork, wake mode for local audio, and persisted queue/position including explicit empty-queue checkpoints; queue edits made during startup are applied only after live-session versus saved-queue reconciliation.
- Native Android back navigation and predictive-back integration; fullscreen/system-inset behavior remains in the existing Compose shell.
- High-refresh display behavior is left under Android/device control rather than forcing 60 Hz.
- No `INTERNET` permission and no online metadata/cloud dependency.

## Deliberately not forced into the APK

- Zstandard is not used for Room/FTS because no measured benefit justifies an additional compression layer.
- AVIF is not used for generated caches; JPEG/WebP remain the supported output formats in the current Android compatibility range.
- A second path-dependent tag parser is not added: user-granted SAF/MediaStore URIs are not guaranteed to expose filesystem paths, and the existing Android extractor plus bounded Rust parser cover the current local-file flow.
- No IPA is produced: the repository has no native iOS project.

These statements describe source implementation only. Build, device, visual, performance, and release-signing status is tracked separately in `NATIVE_VERIFICATION.md`.
