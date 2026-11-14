plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.drivemount.plugin.gphotos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drivemount.plugin.gphotos"
        minSdk = 26
        targetSdk = 35
        versionCode = maxOf(1, (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 100)
        versionName = "0.2.1"
    }

    // DriveMount-এর সঙ্গে একই চাবিতে সই: প্লাগইন শুধু একই চাবির অ্যাপের ডাকে সাড়া দেয়
    signingConfigs {
        create("dm") {
            storeFile = file("../app/dm-signing.keystore")
            storePassword = "drivemount"
            keyAlias = "drivemount"
            keyPassword = "drivemount"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("dm") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dm")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
