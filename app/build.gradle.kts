plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    signingConfigs {
        create("lumen") {
            storeFile = rootProject.file("signing/lumen.keystore")
            storePassword = "lumen-launcher"
            keyAlias = "lumen"
            keyPassword = "lumen-launcher"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lumen")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("lumen")
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
