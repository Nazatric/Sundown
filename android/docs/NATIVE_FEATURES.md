# Native feature status

Sundown is Android-native and offline-only. Kotlin/Compose owns the UI and Android integration; Kotlin owns MediaStore/SAF discovery, Room persistence, grouping, search, and playback state; Rust/UniFFI supplies bounded metadata/artwork processing and BLAKE3 hashing/key helpers; Media3/ExoPlayer owns playback and the background MediaSession. No WebView, web runtime, or network permission is included.

## Implemented in source

- One-time first-launch local-audio permission request (`READ_MEDIA_AUDIO` on Android 13+, `READ_EXTERNAL_STORAGE` on Android 12 and earlier), live PackageManager re-checks on resume, and a queued scan immediately after grant. Permission denial is represented honestly; SAF remains independent.
- SAF folder access with persisted grants, path/document-URI/size/mtime fingerprints, and pruning only after a complete provider walk and successful parsing pass.
- MediaStore updates query changed rows with per-volume version/generation checkpoints where supported, force a full metadata scan after provider-version changes, observe each discovered external volume, and retain an ID-only current snapshot to verify deletions.
- Incremental one-off file ingestion that avoids reparsing unchanged imports.
- Hybrid metadata extraction: `MediaMetadataRetriever` supplies duration and broad container compatibility; the bounded Rust parser fills tag/art gaps, including ID3v2.2 text and `PIC` frames.
- Real duration persistence and shared `mm:ss` / `h:mm:ss` formatting.
- Content-derived artwork IDs (BLAKE3 of embedded bytes; SHA-256 fallback when the native library is unavailable), so changed cover bytes do not reuse album-name cache entries.
- Optional SQLite FTS5 trigram substring-search acceleration. Changed track documents are synchronized incrementally off the UI thread; short/non-ASCII queries and unsupported SQLite builds use the exact Kotlin substring fallback.
- Bounded scan parallelism and batched Room writes/deletes.
- Artwork previews bounded to 1024 px and 160 px, a bounded memory LRU, a 128 MiB disk cache, coalesced decoding limited to two concurrent bitmap jobs, nearby-item preloading, and a short placeholder crossfade. Content-derived art IDs prevent album-name cache collisions; explicit cache clearing invalidates eviction markers.
- Media3 `MediaSessionService` background playback and platform media notification/lock-screen controls, session artwork, wake mode for local audio, and persisted queue/position including explicit empty-queue checkpoints; queue edits made during startup are applied only after live-session versus saved-queue reconciliation. Media-session notifications are exempt from the Android 13+ notification runtime-permission gate, so no unrelated `POST_NOTIFICATIONS` prompt is added.
- The compact segmented tabs preserve the full “Playlists” label by reducing only cell padding. A–Z navigation supports taps and vertical scrubbing, highlights the visible letter, ignores empty letters during drags, and uses ASCII `#` fallback consistently with Rust sorting.
- Native Android back navigation and predictive-back integration; Sources close remains in the fixed toolbar outside the scrollable content. Fullscreen/system-inset behavior remains in the existing Compose shell and is not device-verified.
- High-refresh display behavior is left under Android/device control rather than forcing 60 Hz; smoothness or 90/120 Hz frame pacing is not claimed without profiler/device evidence.
- No `INTERNET` permission and no online metadata/cloud dependency.

## Deliberately not forced into the APK

- Zstandard is not used for Room/FTS because no measured benefit justifies an additional compression layer.
- AVIF is not used for generated caches; JPEG/WebP remain the supported output formats in the current Android compatibility range.
- A second path-dependent tag parser is not added: user-granted SAF/MediaStore URIs are not guaranteed to expose filesystem paths, and the existing Android extractor plus bounded Rust parser cover the current local-file flow.
- No IPA is produced: the repository has no native iOS project.

These statements describe source implementation only. Build, device, visual, performance, and release-signing status is tracked separately in `NATIVE_VERIFICATION.md`.
