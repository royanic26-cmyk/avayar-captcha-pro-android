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
        versionCode = 3
        versionName = "1.0.0"
    }

      signingConfigs {
    create("release") {
      storeFile = file("my-release-key.jks")
      storePassword = "avayar1234"
      keyAlias = "avayar"
      keyPassword = "avayar1234"
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
