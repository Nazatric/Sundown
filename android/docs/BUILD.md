# Native Android build

Prerequisites: JDK 17, Android SDK 34, NDK 26.3.11579264, Rust stable with Android targets, and `cargo-ndk`.

From `android/`:

```text
gradle :app:testDebugUnitTest :app:assembleDebug
```

The `app` build automatically compiles the Rust core for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, generates UniFFI Kotlin bindings from the arm64 shared library, and then compiles Kotlin/Compose.

The archive does not contain generated JNI libraries or generated UniFFI source; those are build outputs and are ignored by Git.
