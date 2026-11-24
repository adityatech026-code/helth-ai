plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.swasthsaathi.voice"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.swasthsaathi.voice"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // URL of the Node backend (emulator -> host machine). Override per build type.
        buildConfigField("String", "BACKEND_URL", "\"http://10.0.2.2:3000\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // The knowledge base lives in /shared so backend, Android and iOS use ONE copy.
    sourceSets["main"].assets.srcDirs("src/main/assets", "../../shared/knowledge")

    buildTypes {
        release { isMinifyEnabled = true }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Offline speech-to-text (VOSK). JNA is required by VOSK on Android.
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
