plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.securitycamera"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.securitycamera"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
