import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI sets GITHUB_RUN_NUMBER so every build installs as an update over the last one.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// The file is called quiKey-debug.apk, not app-debug.apk.
base {
    archivesName.set("quiKey")
}

android {
    namespace = "io.github.selementpecher.shortcutkeyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "designatedfreetime.quiKey"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.1.$buildNumber"
    }

    signingConfigs {
        // A fixed key checked into the repo, so a new build can be installed over an old one
        // without losing saved shortcuts. Only for the sideloaded test builds: the Play Store release uses the "upload" key below.
        getByName("debug") {
            storeFile = rootProject.file("keystore/shared-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // The Play Store upload key. It is never in the repo: CI (or the owner's computer) passes it in.
    // Without these set, the release build is simply not signed (and Play would refuse it).
    val uploadKeystore = System.getenv("QUIKEY_KEYSTORE_FILE")
    if (uploadKeystore != null) {
        signingConfigs.create("upload") {
            storeFile = file(uploadKeystore)
            storePassword = System.getenv("QUIKEY_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("QUIKEY_KEY_ALIAS")
            keyPassword = System.getenv("QUIKEY_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (uploadKeystore != null) signingConfig = signingConfigs.getByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.robolectric:robolectric:4.16")
}
