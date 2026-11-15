import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing key passwords live in local.properties (not in the repository).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.lumen.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lumen.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // One project-local key signs both build types, so debug and release
    // builds can be installed over each other without losing settings.
    // The key and its passwords stay outside the repository: put
    // lumen.storePassword and lumen.keyPassword in local.properties and the
    // key in signing/lumen.keystore. Without them the default debug key is used.
    val lumenKey = rootProject.file("signing/lumen.keystore")
    val hasLumenKey = lumenKey.exists() && localProps.getProperty("lumen.storePassword") != null

    signingConfigs {
        if (hasLumenKey) {
            create("lumen") {
                storeFile = lumenKey
                storePassword = localProps.getProperty("lumen.storePassword")
                keyAlias = localProps.getProperty("lumen.keyAlias", "lumen")
                keyPassword = localProps.getProperty("lumen.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasLumenKey) signingConfig = signingConfigs.getByName("lumen")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasLumenKey) signingConfigs.getByName("lumen") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
