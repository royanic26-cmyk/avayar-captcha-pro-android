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
    versionCode = 5
    versionName = "1.2.0"
  }

  signingConfigs {
    create("release") {
      storeFile = file("avayar-permanent-release.jks")
      storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "AvayarSecure2026!"
      keyAlias = System.getenv("KEY_ALIAS") ?: "avayar_official"
      keyPassword = System.getenv("KEY_PASSWORD") ?: "AvayarSecure2026!"
      enableV1Signing = true
      enableV2Signing = true
      enableV3Signing = true
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      isShrinkResources = false
      signingConfig = signingConfigs.getByName("release")
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }
}
