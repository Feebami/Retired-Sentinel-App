plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.feebami.retiredsentinel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.feebami.retiredsentinel"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }

    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.security.crypto)

    // CameraX
    implementation(libs.bundles.camerax)

    // LiteRT (Correct for Feb 2026 / AI Edge)
    implementation(libs.litert)
    implementation(libs.litert.support)

    // ML Kit
    implementation(libs.mlkit.face.detection)

    // JCodec
    implementation(libs.jcodec)
    implementation(libs.jcodec.android)

    // OkHttp for Telegram
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
