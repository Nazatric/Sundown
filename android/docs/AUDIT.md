# Native design audit — source mapping

This document is a reference inventory of the existing screens and behaviors. The original web design is not shipped in this native-only repository, and this inventory is not a certification of visual parity.

The current working source has not been Android/Rust built or device-tested in this environment. Older CI notes from other commits do not verify this revision. Mappings below describe intended native counterparts only; see `PARITY.md` and the root `NATIVE_VERIFICATION.md` for the current status.

## 1. Screens / destinations

| # | Web construct | Native destination | Back behaviour |
|---|---|---|---|
| 1 | Library (single page, 5 tabs) | `Route.Library` (start) | Exits app |
| 2 | Artists tab | Library state `tab=Artists` | Top-level — no back entry (deliberate, see §9) |
| 3 | Albums tab | Library state `tab=Albums` | Top-level |
| 4 | Songs tab | Library state `tab=Songs` | Top-level |
| 5 | Genres tab | Library state `tab=Genres` | Top-level |
| 6 | Playlists tab | Library state `tab=Playlists` | Top-level |
| 7 | Artist drill-in (`artistFilter`) | `Route.Filtered(artist=…)` | → Library |
| 8 | Genre drill-in (`genre`) | `Route.Filtered(genre=…)` | → Library |
| 9 | Album sheet | `Route.Album(key)` | → caller |
| 10 | Playlist sheet | `Route.Playlist(id)` | → caller |
| 11 | New-playlist sheet | `Route.NewPlaylist(seedIds)` | → caller |
| 12 | Add-to-playlist chooser | `Route.Chooser(trackId)` | → caller |
| 13 | Sources + Settings sheet | `Route.Sources` | → caller |
| 14 | Now Playing sheet | `Route.NowPlaying` | → caller |

Intended back-stack chain (must be confirmed on-device): Library → Playlist →
Now Playing → back → Playlist → back → Library → back → app exits.

## 2. Persistent chrome (always present)

- Branded strip: app name `Sundown` and a decorative battery glyph. Android's real status bar stays hidden; no fake clock is drawn.
- Toolbar: `Sources` button, 5-way segmented control, search pill.
- Scan bar (conditional, while indexing).
- Filter toolbar (conditional, when a filter/search is active).
- Library surface with bottom fade.
- A–Z index rail (Albums/Artists/Songs, toggleable in settings).
- Mini player (always docked at the bottom).

## 3. Every interactive element

Toolbar: Sources (opens sheet) · 5 tabs (switch view; native keyboard-arrow parity is not verified) · search input (native debounce currently 160 ms) · search clear.

Grid: album tile (→ Album sheet) · artist tile (→ artist filter) · genre tile
(→ genre filter) · playlist tile (→ Playlist sheet) · New Playlist tile.

Songs: sticky header (count + **Shuffle** button) · row tap (play / toggle
pause when it is the current track) · row `+` (→ chooser).

A–Z rail: 27 keys (A–Z, `#`) → jump-to-first-match, toast when no match.

Mini player: artwork (→ Now Playing) · meta (→ Now Playing) · prev · play/pause
· next · progress scrub · (≥860 dp also: repeat, shuffle, volume, mute).

Now Playing: album stack · open-album button · scrub · prev/play/next · repeat ·
shuffle · volume · mute · close.

Album sheet: back · close · Play Album / Pause · favourite star · track rows ·
track `+`.

Playlist sheet: back · close · Play · rows · row `+` · Delete Playlist (confirm).

New Playlist: Cancel · name field · song search · per-song checkboxes · Create.

Chooser: per-playlist add (disabled when already present) · New Playlist · close.

Sources: choose SAF folder · restore access · rescan · disconnect · add individual
files · 5 start-screen choices · 4 library/playback toggles · clear artwork cache ·
erase library data (confirm).

The original design included an asset-kit screen, but the native
Sources sheet intentionally has no asset-kit option or destination.

Notice toast: auto-dismiss 4.5 s + manual dismiss.

## 4. State

`tracks`, `booted`, `tab`, `query`/`search`, `genre`, `artistFilter`,
`favoritesOnly`, `favorites[]`, `playlists[]`, `defaultTab`, `autoRescan`,
`showIndex`, `highArt`, `keepAwake`, `folderName`, `permission`
(granted/prompt/none), `scanning` (phase/done/total/current), sheet-open flags,
`notice`, and player snapshot (track, playing, loading, elapsed, duration,
volume, muted, shuffle, repeat, queue, hasSource).

## 5. Data & storage

| Web | Native |
|---|---|
| IndexedDB `tracks` store | Room table `tracks` |
| IndexedDB `art` store (340 px + 96 px JPEG blobs) | App-private artwork files plus Room IDs (up to 1024 px + 160 px; small WebP when smaller on supported Android versions) |
| IndexedDB `prefs` | Preferences DataStore |
| IndexedDB `handles` (directory handle) | Persisted SAF tree URI permission |
| Object URLs | `content://` URIs + `ParcelFileDescriptor` |

## 6. Background / async work

- Recursive folder walk (web: `FileSystemDirectoryHandle.values()`).
- Incremental Kotlin diff by source identity, document URI/path, size, modification time, and (on API 30+) MediaStore generation → added / updated / removed. Metadata/search work is performed off the UI thread; Kotlin owns scanning and indexing.
- Metadata parse pool (web: 2–4 Blob workers) → Rust + Kotlin coroutine
  dispatcher (`Dispatchers.IO.limitedParallelism`, clamped to 2–4).
- Artwork center-crop to up to 1024 px and 160 px JPEG q92 previews; small source art is not upscaled. Rust decoding has source-size limits, and cover conversions are serialized during scans to bound transient heap use.
- Progressive emission — rows appear while the scan continues.
- Coalesced DB writes (web: ~900 ms batches).
- Single-flight scan lock.
- Artwork decoding is coalesced per preview key and prefetched for visible plus nearby gallery/list items.
- Grouping and sort indexes rebuild only on Room track-list changes; search filtering runs on `Dispatchers.Default`.
- Player time updates are isolated from library row state, while the progress thumb interpolates between 250 ms service snapshots at the display's native vsync rate.
- Scan-progress emissions are coalesced to avoid one full UI update per parsed file.

## 7. Permissions

| Web | Native |
|---|---|
| File System Access prompt | `ACTION_OPEN_DOCUMENT_TREE` + persistable read permission |
| File input fallback | `ACTION_OPEN_DOCUMENT` (multi-select) |
| Screen Wake Lock API | `FLAG_KEEP_SCREEN_ON` |
| Media Session API | Media3 `MediaSessionService`; Android exempts media-session notifications from the API 33+ notification runtime-permission gate, so no `POST_NOTIFICATIONS` prompt is needed |
| — | `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; no Notification Listener access |

## 8. Error / empty / loading states

- No library yet → empty state with “Choose Music Folder”.
- Search/filter with no match → “Nothing matches” + Show Everything.
- Genres empty, Playlists empty-on-search.
- Nothing playing (Now Playing sheet variant).
- Unreadable/moved file → toast, playback stops cleanly.
- Unsupported codec → toast.
- Permission refused / revoked → “Restore Folder Access”.
- Loading: scan bar and transport/loading states; per-row spinner parity is not verified.

## 9. Deliberate behaviour preserved

Tab switches do **not** push history. This was an explicit bug fix requested
earlier (back was cycling Songs → Albums → Playlists instead of leaving).
Android top-level-destination guidance matches: back from a top-level tab
exits. Drill-ins and sheets *do* push, newest-closes-first.

## 10. Audio

Original file stream is played untouched — no transcode, resample, or
normalisation. Web used `<audio>` + object URL; native uses ExoPlayer with the
raw `content://` URI and the default (bit-perfect-passthrough-capable)
AudioSink. Gapless-ish behaviour via ExoPlayer's playlist API. The Activity leaves
`preferredRefreshRate` at 0 (no app-level cap); Compose remains vsync-driven so
Android can use the display's available high-refresh mode.
