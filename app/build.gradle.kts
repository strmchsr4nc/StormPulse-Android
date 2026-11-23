plugins {
    id("com.android.application")
}

android {
    namespace = "com.carolinasvrwx.stormpulse"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.carolinasvrwx.stormpulse"
        minSdk = 24
        targetSdk = 37
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
    lint {
        abortOnError = true
        warningsAsErrors = true
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
