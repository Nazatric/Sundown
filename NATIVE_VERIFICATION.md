# Native audit and verification status

Audit date: 2026-10-04. This report separates source inspection, CI build/test evidence, screenshot comparison, device validation, performance profiling and release signing. A green CI run is not visual, device, performance, notification-runtime or signing proof.

## Required audit matrix

| Category | Status | Evidence / limitation |
|---|---|---|
| Native Android architecture and offline-only boundary | PASS (source) | Kotlin/Compose, Rust/UniFFI, Room, SAF/MediaStore and Media3 are native integrations. The Android manifest has no `INTERNET` permission; no web runtime/WebView or native iOS project is present. |
| Reference visual fidelity | UNVERIFIED | No reference screenshots, recordings or raster design assets are present in the checkout. `android/docs/PIXEL_SPEC.md` is a measurable target, not a screenshot-validated result. The requested sleeve sheen and compact tab adjustment are source-level implementations only; no pixel-parity claim is made. |
| Custom controls, mini-player proportions, fullscreen/insets and responsive UI | UNVERIFIED (runtime) | Existing custom Compose controls and layout dimensions were audited; compact segmented-control padding was adjusted without shrinking the whole toolbar. There is no emulator/device screenshot or interaction recording to confirm geometry or touch behavior. |
| A–Z taps, drag scrubbing, jump targets and current-letter state | PASS (CI unit tests) / UNVERIFIED (device) | The rail maps drag positions to letters, scrolls directly while dragging, animates taps, highlights the first visible letter, and avoids drag-time no-match notices. The pure key-mapping tests passed in run 37181294576 at `0898a24`; device gesture behavior is not checked. |
| Sources close, sheets, modal/back and predictive-back priority | PASS (source structure) / UNVERIFIED (device) | The Sources toolbar remains outside its scrollable content; navigation destinations and explicit filter/dialog back handlers are present. System back, predictive-back and repeated close taps need device validation. |
| First-launch local-audio permission and indexing | PASS (source) / UNVERIFIED (device) | A one-time startup request uses `READ_MEDIA_AUDIO` on API 33+ and `READ_EXTERNAL_STORAGE` on API 26–32. Actual permission is re-read from Android on resume and before scans; a granted request queues a MediaStore scan after any active scan. Denial leaves a truthful state and SAF remains available. Runtime grant/deny/revoke flows are not device-tested. No image/video permission is requested. |
| Media3 session, notification and lock-screen controls | PASS (source) / UNVERIFIED (device) | `MediaSessionService`, ExoPlayer, media-playback foreground-service declarations, session activity and metadata artwork are present. Android's media-session notification exemption is used; no unnecessary `POST_NOTIFICATIONS` or Notification Listener permission is added. Actual notification/lock-screen behavior is unverified. |
| Playback, queue and persisted empty-queue checkpoint | PASS (source) / UNVERIFIED (device) | Queue removal/clear persists the actual empty queue and null current ID; service checkpoints also preserve an observed empty queue. Startup queue edits are reconciled against a live session. Process-death and background runtime behavior remain untested. |
| Library scanning, Room, metadata and search | PASS (source) / UNVERIFIED (device) | Incremental fingerprints, per-volume MediaStore version/generation checkpoints, ID-based deletion detection, a row-level provider-version fingerprint (Room migration v2→v3) to reparse after provider rebuilds, bounded parse workers, batched Room writes/deletes, off-main-thread indexing and optional FTS5 with a Kotlin fallback were reviewed. Android SQLite/provider behavior and large-library performance are not verified here. |
| Artwork identity, resize, preload and cache bounds | PASS (source) / UNVERIFIED (device) | Artwork IDs are content-derived; Rust creates at-most-1024 px and 160 px previews; memory LRU and 128 MiB disk bounds, preload, a two-decode concurrency limit and a short placeholder crossfade are present. Visual quality, memory pressure and eviction/rebuild behavior need device profiling. |
| High-refresh/frame pacing | UNVERIFIED | No 90/120 Hz smoothness claim is made. `preferredRefreshRate` remains 0 and no unsupported mode is hardcoded; no frame profiler or high-refresh device is available. |
| Rust tests, Android unit tests, debug build and release-variant assembly | PASS (CI) / UNVERIFIED (device) | GitHub Actions run [37181294576](https://github.com/Nazatric/Sundown/actions/runs/37181294576), code commit `0898a24a1eda31350bd0603f1ece6dd06649bcb7`: Rust unit tests, Android unit tests, debug APK assembly, unsigned release-variant assembly and universal native-ABI verification passed. No local Java/Gradle/Cargo/Android toolchain is installed; the follow-up change to this report is documentation-only. |
| Android workflow syntax/execution | PASS (CI) | Run [37181294576](https://github.com/Nazatric/Sundown/actions/runs/37181294576) parsed and executed the workflow at code commit `0898a24`; secret detection and unsigned validation ran, and signed-build/upload was skipped because signing inputs were missing. |
| Universal release variant and ABI composition | PASS (unsigned CI validation only) | The release APK assembled and its ZIP contains `lib/arm64-v8a/libsundown_core.so`, `lib/armeabi-v7a/libsundown_core.so` and `lib/x86_64/libsundown_core.so`. The unsigned release APK is validation evidence only and was not uploaded or presented as a deliverable. |
| Signed universal release APK | BLOCKED | No signed release APK was built or uploaded. CI reported missing GitHub Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Configure those secrets before requesting a signed universal APK; debug and unsigned APKs are not substitutes. |
| iOS / IPA | BLOCKED / NOT APPLICABLE | There is no native iOS implementation or project, so no IPA is produced or claimed. |

## Static checks completed in this audit

- `git diff --check`: PASS.
- Python XML parsing: all 8 Android XML files are well-formed.
- Manifest check: no `INTERNET` or `POST_NOTIFICATIONS`; local audio permissions are declared.
- Repository inventory: no reference screenshots/recordings or raster/video artwork assets were found.
- The host FTS5 probe documented in the previous audit is not proof of Android SQLite FTS5 support.

## Evidence still required

1. On API 32 and API 33+ devices, test permission grant/deny/revoke, MediaStore indexing after grant, SAF access, and the Sources retry action.
2. Exercise MediaSession notifications, lock-screen controls, queue restoration, empty-queue persistence, back/predictive-back, X close, rotation and insets.
3. Compare reference screenshots at the documented viewport. The current checkout contains no screenshots or recordings to compare against.
4. Profile a large library and high-refresh device before making frame-pacing, memory or 90/120 Hz smoothness claims.
5. Build, sign, verify and upload the universal release APK only when valid signing inputs are configured. Run 37181294576 validated the unsigned release variant and ABI contents only; no signed APK was built or uploaded. Signing is blocked by the four missing Actions secrets listed above.
