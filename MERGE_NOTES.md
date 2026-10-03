# Native architecture notes

This repository contains the Android implementation only. The tracked tree has no web runtime or native iOS project. These notes describe the current architecture; they are not a build or runtime-verification record.

- Kotlin/Compose owns screens, navigation, MediaStore/SAF access, Room-backed library indexing, and playback state.
- Media3/ExoPlayer and `MediaSessionService` own foreground/background playback.
- Rust/UniFFI supplies bounded metadata parsing, embedded-art previews, normalized keys, and BLAKE3 hashing; Kotlin retains Android/platform fallbacks.
- Release configuration targets one universal APK with optional environment/secret-based signing. No AAB or IPA artifact is claimed.

See `NATIVE_VERIFICATION.md` for the current audit, static checks, and build/device limitations.
