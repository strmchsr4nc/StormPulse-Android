plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.carolinasvrwx.stormpulse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.carolinasvrwx.stormpulse"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Declare the Kotlin runtime explicitly for Android D8/desugaring and transitive Kotlin libraries.
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    implementation("androidx.activity:activity-ktx:1.10.1")
    // Android Auto projected-display support; phone UI remains the existing WebView.
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")
    // OpenStreetMap renderer for the separate native navigation screen.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // Backport java.time APIs for the minSdk 24 devices supported by StormPulse.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
