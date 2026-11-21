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
