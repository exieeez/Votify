import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "app.votify.mobile"
    compileSdk = 35

    defaultConfig {
        // Normally app.votify.mobile. A whitelisted package id can be baked in instead for
        // OEM «dynamic islands» that hardcode a supported-app list (OriginOS Dynamic Spot,
        // etc.): built with -PvotifyIslandPackage=<id> and shipped as Votify-island.apk.
        // The app label/icon stay Votify — only the Android package id changes.
        val islandPackage = (project.findProperty("votifyIslandPackage") as String?)?.trim().orEmpty()
        applicationId = islandPackage.ifBlank { "app.votify.mobile" }
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.1.9"

        // Backend base URL. 10.0.2.2 = host machine from the Android emulator.
        // Override for a real device: -PvotifyApiBase=http://192.168.1.10:17217
        val apiBase = (project.findProperty("votifyApiBase") as String?) ?: "http://10.0.2.2:17217"
        buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")

        // Firebase Web Config baked at CI time from the VOTIFY_FIREBASE_CONFIG secret
        // (never committed to the repo). Empty in local builds — the Workshop then
        // accepts a pasted config or pulls it from the server.
        val fbConfig = System.getenv("VOTIFY_FIREBASE_CONFIG")?.trim() ?: ""
        buildConfigField("String", "FIREBASE_CONFIG", "\"" + fbConfig.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
        buildConfigField("String", "BUILD_TIME", "\"" + (System.getenv("BUILD_TIME") ?: Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()) + "\"")
    }

    // Stable signing for every build. The stock debug key is regenerated on each CI runner,
    // which gave every APK a different signature — update-over-install was impossible.
    // Committed PKCS#12: this is a personal sideloaded app, not a Play Store build.
    signingConfigs {
        create("stable") {
            storeFile = file("../keystore/votify-debug.p12")
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor needs java.time/NIO on API < 33
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Media playback (ExoPlayer + background session)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Images
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Animated GIF / WebP backgrounds (ImageDecoder on API 28+, GifDecoder below).
    implementation("io.coil-kt:coil-gif:2.7.0")
    // Google Sign-In via Android Credential Manager.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    // GoogleApiAvailability — upfront GMS presence check on the Google Sign-In button.
    implementation("com.google.android.gms:play-services-base:18.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Local database (favorites, history, playlists)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Settings persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Dominant color extraction for the PC-style tinted player background
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Standalone mode: search / stream YouTube & SoundCloud without any server
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")

    // Required by NewPipeExtractor on minSdk < 33
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
