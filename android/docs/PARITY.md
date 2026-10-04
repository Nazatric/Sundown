# Native source mapping and verification status

The earlier design inventory is retained as a reference. The repository itself is Android-native; no web runtime or iOS project is included. “Source present” is not a claim of device behavior or pixel parity.

## Feature mapping

| Capability | Native implementation | Current source status |
|---|---|---|
| Library tabs, album grid, artist/genre drill-in, songs and playlists | Compose screens, `LibraryViewModel`, navigation destinations | Present; runtime/layout unverified |
| Search | Debounced Compose state, incremental SQLite FTS5 trigram accelerator, exact Kotlin substring fallback | Present; Android SQLite behavior unverified |
| Album/playlist sheets, favorites and queue UI | Existing Compose sheet components and `PlayerController` | Present; runtime unverified |
| A–Z navigation and sorting | Existing `AlphabetIndex`, Kotlin/Rust normalized sort keys | Present; device behavior unverified |
| SAF folder and one-off file sources | Persisted SAF tree grants and `ACTION_OPEN_DOCUMENT` URI access | Present; provider/device behavior unverified |
| MediaStore device library | API-specific audio permission, per-volume MediaStore version/generation checkpoints, ID-based deletion snapshot and debounced content observer | Present; permission/provider behavior unverified |
| Incremental scan and Room storage | Kotlin source fingerprints, changed-row parse pool, batched Room writes/deletes and guarded prune | Present; large-library performance unverified |
| Metadata and duration | `MediaMetadataRetriever` plus bounded Rust ID3/MP4/FLAC/OGG/WAV parsing and persisted duration | Present; codec/tag coverage unverified |
| Artwork preview and caching | Up to 1024/160 px previews, content-derived IDs, memory LRU, bounded disk cache, decode coalescing and preload | Present; image quality/memory behavior unverified |
| Playback and MediaSession | Media3/ExoPlayer service, background controls, local wake mode and artwork metadata | Present; device/background behavior unverified |
| Queue and playback restoration | DataStore checkpoints and Room metadata reattachment, including explicit empty-queue state | Present; process-death/device behavior unverified |
| Back/predictive back and fullscreen | Navigation Compose, Android back callbacks, existing edge-to-edge shell | Present; gesture/inset behavior unverified |
| Offline/native-only runtime | No `INTERNET` permission, WebView or web runtime in app source | Verified by source inspection |
| Universal Android APK | ABI splitting disabled; conditional release signing configured | Build configuration present; no signed artifact verified |
| iOS IPA | No native iOS project/source exists | Not applicable; no IPA target |

## Back-stack reference

```text
Library ──▶ Playlist ──▶ NowPlaying
   ◀── back      ◀── back
Library ──▶ Album ──▶ Chooser ──▶ NewPlaylist
```

Tabs remain top-level rather than adding history entries. This describes source configuration only; Android back and predictive-back behavior still needs device verification.

## Validation status for this revision

| Category | Status | Evidence / limitation |
|---|---|---|
| Repository/native-only/offline audit | PASS (source) | Tracked source inventory found no web runtime, network permission, SVG artwork assets, or iOS project. |
| Reference UI fidelity and interaction geometry | UNVERIFIED (screenshots/device) | Existing Compose screens and components were adjusted for the requested compact “Playlists” label, A–Z interaction, artwork presentation/cache, slider preview and Sources close behavior. No reference screenshot is present, so pixel parity and device interaction remain unverified; this was not a redesign. |
| MediaStore/SAF/permissions | PASS (source) | Implementations and permission branches inspected; device/provider behavior is UNVERIFIED. |
| Incremental indexing, metadata, artwork, queue and state fixes | PASS (source review + CI) / UNVERIFIED (runtime) | The source changes passed run 37181294576 at commit `0898a24a1eda31350bd0603f1ece6dd06649bcb7`; Android runtime/provider behavior remains UNVERIFIED. |
| Room migration / FTS5 | PASS (source) | Migration and fallback paths inspected; Android SQLite/FTS availability and large-library performance are UNVERIFIED. |
| XML/resource and diff checks | PASS | `NATIVE_VERIFICATION.md` records XML parsing, manifest, source-inventory and diff checks; these do not replace a build. |
| Android/Rust build and unit-test execution | PASS (CI) / UNVERIFIED (device) / BLOCKED locally | GitHub Actions run [37181294576](https://github.com/Nazatric/Sundown/actions/runs/37181294576) passed Rust tests, Android unit tests, debug APK assembly, unsigned release-variant assembly and ABI verification on code commit `0898a24a1eda31350bd0603f1ece6dd06649bcb7`. This report-only follow-up does not alter application sources; Java, Gradle, Cargo and Android SDK tools are unavailable locally. |
| Device install, playback, notification, SAF, screenshots and performance | UNVERIFIED | No emulator or physical Android device is available here. |
| Signed universal APK | BLOCKED | CI assembled/uploaded the debug APK. Signed release packaging was skipped because signing secrets are not configured; no signed artifact is claimed. |
| IPA | BLOCKED / NOT APPLICABLE | No genuine native iOS project is present. |

CI evidence is commit-scoped. Older implementation run IDs remain in historical notes such as `PIXEL_SPEC.md` and `android/README.md`; they are not evidence for newer application sources. The latest application-source CI evidence recorded here is run 37181294576 at `0898a24`; this documentation-only follow-up is checked separately in PR #1.

## Local reproduction and device validation still needed

1. Run `gradle :app:testDebugUnitTest :app:assembleDebug` from `android/` with the documented JDK, SDK/NDK and Rust targets.
2. Install the debug APK and exercise API 32 and API 33+ permissions, SAF folder grants, MediaStore changes, selected-file URI grants, metadata, queue restore, background playback and artwork notifications.
3. Compare screenshots against the repository's design reference at the documented viewport; do not use a missing web runtime as a test dependency.
4. Profile a large local library and verify disk/memory artwork eviction on supported Android SQLite versions.
5. Produce a signed universal APK only when the release signing material is configured; do not create an IPA without a native iOS project.
