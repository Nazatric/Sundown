import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sundown.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sundown.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
    }

    // Ship one universal APK containing all supported Rust ABIs.
    splits {
        abi {
            isEnable = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!System.getenv("SUNDOWN_STORE_FILE").isNullOrBlank() && file(System.getenv("SUNDOWN_STORE_FILE")).exists()) {
                signingConfig = signingConfigs.create("releaseEnv") {
                    storeFile = file(System.getenv("SUNDOWN_STORE_FILE"))
                    storePassword = System.getenv("SUNDOWN_STORE_PASSWORD")
                    keyAlias = System.getenv("SUNDOWN_KEY_ALIAS")
                    keyPassword = System.getenv("SUNDOWN_KEY_PASSWORD")
                }
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Native core produced by: cargo ndk -o app/src/main/jniLibs build --release
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    // Material3 is present only for Scaffold/ripple plumbing. Every visible
    // control in this app is custom-drawn to match the web original.
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Required at runtime by UniFFI-generated Kotlin bindings.
    implementation(libs.jna) { artifact { type = "aar" } }

    testImplementation(libs.junit)
}


// Compile the Rust core for every supported device/emulator ABI and generate the
// matching UniFFI Kotlin bindings before Android sources are compiled. The APK
// is therefore never allowed to silently ship the Kotlin-only fallback.
val rustCrate = rootProject.layout.projectDirectory.dir("rust/sundown-core")
val jniLibs = layout.projectDirectory.dir("src/main/jniLibs")
val generatedBindings = layout.buildDirectory.dir("generated/uniffi/main")
val nativeAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

android.sourceSets.getByName("main").java.srcDir(generatedBindings.get().asFile)

val buildSundownRust by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds sundown-core for Android ABIs with cargo-ndk."
    workingDir(rustCrate.asFile)
    inputs.files(fileTree(rustCrate) {
        include("Cargo.toml", "Cargo.lock", "uniffi.toml", "uniffi-bindgen.rs", "src/**")
    })
    outputs.files(nativeAbis.map { jniLibs.file("$it/libsundown_core.so") })
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "-o", jniLibs.asFile.absolutePath,
        "build", "--release",
    )
}

val generateSundownBindings by tasks.registering(Exec::class) {
    group = "build"
    description = "Generates Kotlin UniFFI bindings from the Android Rust library."
    dependsOn(buildSundownRust)
    workingDir(rustCrate.asFile)
    val library = jniLibs.file("arm64-v8a/libsundown_core.so")
    inputs.file(library)
    inputs.file(rustCrate.file("Cargo.toml"))
    inputs.files(fileTree(rustCrate) { include("Cargo.lock") })
    inputs.file(rustCrate.file("uniffi.toml"))
    inputs.file(rustCrate.file("uniffi-bindgen.rs"))
    outputs.dir(generatedBindings)
    doFirst { generatedBindings.get().asFile.mkdirs() }
    commandLine(
        "cargo", "run",
        "--features", "bindgen-cli",
        "--bin", "uniffi-bindgen",
        "--",
        "generate",
        "--library", library.asFile.absolutePath,
        "--language", "kotlin",
        "--out-dir", generatedBindings.get().asFile.absolutePath,
    )
    doLast {
        val sources = generatedBindings.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        if (sources.isEmpty()) throw GradleException("UniFFI generated no Kotlin sources.")
        sources.forEach { logger.lifecycle("Generated UniFFI Kotlin: ${it.relativeTo(rootProject.projectDir)}") }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateSundownBindings)
}

tasks.configureEach {
    if (name.startsWith("compile") && name.endsWith("Kotlin")) {
        dependsOn(generateSundownBindings)
    }
}
