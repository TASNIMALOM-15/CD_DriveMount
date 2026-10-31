plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.drivemount.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drivemount.app"
        minSdk = 26
        targetSdk = 35
        // প্রতিটি CI build-এর versionCode বাড়ে (GitHub run number), যাতে নতুন APK পুরোনোর ওপর update হয়
        versionCode = maxOf(14, (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 100)
        versionName = "0.11.0"

        // rclone engine: 32-bit (armeabi-v7a) ও 64-bit (arm64-v8a) দুই ধরনের ARM ফোনের জন্য
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // সব build একই চাবিতে সই হয়। আগে প্রতিবার CI নতুন debug চাবি বানাত, তাই update না হয়ে
    // "App not installed" আসত ও আনইনস্টল করতে হতো। এই ফাইল হারালে বা বদলালে আবার আনইনস্টল লাগবে।
    signingConfigs {
        create("dm") {
            storeFile = file("dm-signing.keystore")
            storePassword = "drivemount"
            keyAlias = "drivemount"
            keyPassword = "drivemount"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dm")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dm")
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
}

dependencies {
    // GitHub Actions-এ gomobile দিয়ে তৈরি rclone engine
    implementation(files("libs/rclone.aar"))

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.biometric:biometric:1.1.0")
    // biometric পুরোনো fragment (1.2.x) টানে, যার সঙ্গে permission-অনুরোধ ক্র্যাশ করে ("lower 16 bits for requestCode")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // Terabox-এর নিজস্ব HLS স্ট্রিম (তাৎক্ষণিক চালু)
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    // PlayerView: ছবির আকার (fit/fill/zoom), সাবটাইটেল দেখানো
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ছবির থাম্বনেল
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ডাটাবেস (অ্যাকাউন্ট তালিকা)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
