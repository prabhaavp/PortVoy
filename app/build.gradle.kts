plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.prabhaav.portvoy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.prabhaav.portvoy"
        minSdk = 25
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Enables code shrinking, obfuscation, and optimization
            isMinifyEnabled = true
            isShrinkResources = true

            // Includes the ProGuard configuration files for code shrinking
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
}

dependencies {

    // Better UI
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.browser:browser:1.8.0")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
