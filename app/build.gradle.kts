plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.royanic26.captchaaccessible"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.royanic26.captchaaccessible"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-test"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
