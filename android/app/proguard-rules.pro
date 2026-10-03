# UniFFI bindings talk to the Rust core through JNA reflection.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class uniffi.** { *; }

# Room entities are reflected over by the generated DAOs.
-keep class com.sundown.player.data.db.** { *; }

# Media3 session callbacks.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
