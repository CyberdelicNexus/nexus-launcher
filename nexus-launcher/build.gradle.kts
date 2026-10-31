plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.meta.spatial.plugin)
}

val releaseKeystorePath = providers.environmentVariable("NEXUS_LAUNCHER_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("NEXUS_LAUNCHER_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("NEXUS_LAUNCHER_KEY_ALIAS").orNull
val releaseSigningReady =
  listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias).all { !it.isNullOrBlank() }

android {
  namespace = "nexus.cyberdelic.kiosk"
  compileSdk = 34
  // Unity's bundled SDK ships these; AGP's default would download 35.0.0.
  buildToolsVersion = "36.0.0"

  defaultConfig {
    applicationId = "nexus.cyberdelic.kiosk"
    minSdk = 34
    targetSdk = 34
    versionCode = providers.gradleProperty("nexusVersionCode").orElse("1").get().toInt()
    versionName = providers.gradleProperty("nexusVersionName").orElse("0.1.0").get()
    ndk { abiFilters += "arm64-v8a" }
  }

  signingConfigs {
    if (releaseSigningReady) {
      create("release") {
        storeFile = file(releaseKeystorePath!!)
        storePassword = releaseKeystorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeystorePassword
      }
    }
  }

  buildTypes {
    getByName("debug") {
      // One signer everywhere, so Fleet installs and Meta alpha builds can update each other.
      if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
    }
    getByName("release") {
      isDebuggable = false
      isMinifyEnabled = false
      if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  // KeyEvent's key codes are plain constants; the stubbed class is enough for the unit tests.
  testOptions { unitTests.isReturnDefaultValues = true }

  lint {
    abortOnError = true
    checkReleaseBuilds = false
  }
}

kotlin { jvmToolchain(17) }

dependencies {
  implementation(libs.meta.spatial.sdk.base)
  implementation(libs.meta.spatial.sdk.toolkit)
  implementation(libs.meta.spatial.sdk.vr)
  testImplementation(libs.junit)
  testImplementation(libs.json)
}

spatial { allowUsageDataCollection.set(false) }
