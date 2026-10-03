# Native feature coverage and validation status

## Feature parity matrix

| Web feature | Native source mapping | Native source status (not runtime-verified) |
|---|---|---|
| App launcher icon | Adaptive icons retain the prior `sundown_launcher_foreground`; requested uploaded image is not accessible in this checkout | exact requested icon not integrated; awaiting source image |
| 5 library tabs | `LibraryScreen` + `SegmentedControl` | source present; unverified |
| Album grid (2/3/3/4 cols) | `LazyVerticalGrid` with the CSS breakpoints | source present; unverified |
| Album stack artwork | `AlbumStack` — 5 layers, exact rotations/tones | source present; unverified |
| Artist / genre drill-in | `Route.Filtered` + VM filters | source present; unverified |
| Songs list (virtualised) | `LazyColumn` (windowing is built in) | source present; unverified |
| Songs sticky header + Shuffle | `SongsHeader`, `shuffleAll()` | source present; unverified |
| A–Z jump rail | `AlphabetIndex` + `jumpTarget` | source present; unverified |
| Search (debounced) | `SearchPill` + VM `query` flow | source present; unverified |
| Album sheet + favourite | `AlbumSheetContent` | source present; unverified |
| Playlist CRUD | `PlaylistSheetContent`, `NewPlaylistContent`, `ChooserSheetContent` | source present; unverified |
| Sources + Settings | `SourcesSheetContent` (5 start-screen choices, 4 toggles; asset-kit entry deliberately omitted) | source present; unverified |
| Android media notifications | Media3 session notifications use Android's [API 33+ exemption](https://developer.android.com/about/versions/13/changes/notification-permission#exemptions); no `POST_NOTIFICATIONS` request or Notification Listener access | source present; device behavior unverified |
| Mini player | `MiniPlayer` | source present; unverified |
| Now Playing sheet | `NowPlayingContent` | source present; unverified |
| Transport / seek / volume / mute | `TransportRow`, `ProgressSlider`, `VolumePill` | source present; unverified |
| Shuffle / repeat / queue | ExoPlayer shuffle order + repeat modes | source present; unverified |
| Media Session | Media3 `MediaSessionService` (lock screen, BT, Auto) | source present; unverified |
| Folder access + persistence | SAF tree + persistable permission | source present; unverified |
| Individual file fallback | `OpenMultipleDocuments` | source present; unverified |
| Incremental rescan | Kotlin size+mtime diff in `LibraryRepository`; Rust `diff_scan` is not wired | source present; unverified |
| Progressive scan UI | batch flush per 24 files | source present; unverified |
| Metadata parsing | Rust `metadata.rs` (ID3/MP4/FLAC/OGG/WAV) | source present; unverified |
| Artwork previews | Rust `artwork.rs` + Kotlin fallback: up to 1024/160 px, JPEG q92, no upscaling, bounded decode dimensions | CI tests pass; device image quality unverified |
| Artwork reuse / preloading | Per-key in-flight decode coalescing, bounded LRU, nearby-item preload, serialized source-image conversion | source present; runtime performance unmeasured |
| Duration labels | Shared `formatDuration` for rows, sheets and timelines | unit tests pass in CI |
| High-refresh displays | No app refresh-rate cap; timeline animates between Media3 snapshots | source present; device pacing unverified |
| Status strip | Branded strip and decorative battery; fake clock removed | source present; visual parity unverified |
| Library persistence | Room | source present; unverified |
| Prefs/queue/position persistence | DataStore | source present; unverified |
| Keep screen on | `FLAG_KEEP_SCREEN_ON` | source present; unverified |
| Toasts / empty / loading / error | `NoticeToast`, `EmptyState`, `Spinner`, `ScanBar` | source present; unverified |
| Reduced motion | Gallery arrival snaps when Android animator animations are disabled; remaining animation coverage still needs a device audit | partial source support; unverified |
| Back-stack behaviour | Navigation Compose destinations | source present; unverified |

## Navigation check

```
Library ──▶ Playlist ──▶ NowPlaying
   ◀── back      ◀── back
Library ──▶ Album ──▶ Chooser ──▶ NewPlaylist
```
Tabs are top-level and never push, so back from a tab exits the app. This is
intentional and was an explicit earlier bug report against the web build.

## Validation status — what is and isn't verified

GitHub Actions [run 37116604969](https://github.com/Nazatric/The-Player/actions/runs/37116604969) on code commit `4174326` passed `cargo test`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; the workflow uploaded a debug APK artifact. This verifies CI compilation, Rust tests, Android unit tests, and debug packaging for that source commit, not device behavior or release signing. The local environment has no Android SDK, Gradle executable/wrapper, Kotlin compiler, Rust toolchain, NDK or emulator; no install, playback/SAF device test, screenshot diff, or performance profile has occurred. Rows marked source-present describe code only; they are not runtime guarantees.

### Narrow source facts verified by inspection (not behavior tests)

1. No WebView references in the shipped app source; the term appears in documentation, not UI code.
2. No HTML/CSS/JS renders the UI — every pixel comes from Compose.
3. No bundled website — nothing under `android/` ships web assets.
4. The source `AndroidManifest.xml` does not declare `INTERNET`; the merged manifest was not separately inspected.
5. A feature inventory exists; the table above explicitly distinguishes source presence from verified behavior.
6. Native navigation uses a real `NavHostController` back stack.
7. Token values match the stylesheet (`PIXEL_SPEC.md`).
8. Rust contains parsing/artwork code and exported index helpers; Kotlin currently groups/filters/scans in the repository. Rust and Android unit tests passed in CI on `ed024c1`.

### Static checks run here (source-only)

- Python comparison of native asset-kit declarations against `src/lib/assetKit.ts` / `public/assets-kit`: **23 icons, 21 colors, 6 gradients, both SVG masters matched, and the maskable PNG copy was byte-identical**.
- Python XML parsing: **8 Android resource/manifest XML files were well-formed**.

These source-only checks do not themselves compile Kotlin/Rust or exercise the app; the separate CI run above verifies compilation, tests, UniFFI generation, and debug packaging.

### NOT verified (requires device/release validation)

9. Installing and exercising the debug APK on an emulator or physical device.
10. Pixel diffing against the web app at matched dimensions.
11. Playback, Media Session, notification presentation, SAF and scanning on real hardware.
12. Performance with 1 000+ tracks and frame-pacing on high-refresh displays.
13. A signed stable release APK and release notes.

The CI debug build and unit tests pass, but this is not yet a release or a
runtime-verified deliverable. Device behavior, visual fidelity, performance,
and release signing remain open.

## Recommended Phase 6 procedure (for when a toolchain is available)

1. Provide JDK 17, Android SDK/NDK, Rust targets, `cargo-ndk`, and UniFFI 0.28.
   There is no checked-in `gradlew`; open `android/` in Android Studio or install
   a compatible Gradle distribution and create a wrapper, then run
   `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
2. Run the APK on a 1080×2400 device (393×873 dp) — the reference viewport.
3. Capture the web app in Chrome DevTools at 393×873 and screenshot the native
   app on the same screens.
4. Diff the pairs (ImageMagick `compare -metric AE`, or Android's
   `screenshot-tests-for-android`) and record deltas per screen.
5. Prioritise: toolbar height, cover size, row height, caption baselines,
   player cluster positions — these drive the overall composition.
6. Re-run until the diffs are confined to antialiasing and font rasterisation,
   which cannot be eliminated between Skia-in-browser and Skia-in-Compose.
