plugins {
    id("com.android.application")
}

android {
    namespace = "de.polenoffline.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.polenoffline.app"
        minSdk = 26          // Android 8+
        targetSdk = 34
        // Wird von GitHub bei jedem Bau hochgezählt (für Updates ohne Deinstallation)
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()
        versionCode = 100 + run
        versionName = "1.0.$run"
        // Nur Handy-Prozessoren einpacken (macht die App deutlich kleiner)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Fester Schlüssel, damit Updates sich über die alte Version installieren lassen
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Texterkennung für Preisschilder – Modell ist in der App enthalten, funktioniert offline
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // FileProvider für Kamerafotos
    implementation("androidx.core:core:1.13.1")
    // Kotlin-Bibliotheken auf eine Version bringen (verhindert doppelte Klassen)
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
}
