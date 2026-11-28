import java.util.Properties

/*
 * Parabolic for older Android versions (7.0 to 11 in practice, though it runs
 * anywhere from API 24 up). It is a separate APK with a plain Material 3 UI;
 * the Material 3 Expressive app in ../app is left exactly as it is.
 *
 * Only the UI is written here. The download engine, queue, settings and
 * foreground service are compiled straight from ../app, so a fix there lands in
 * both APKs. Those files reference dev.eymistaken.parabolic.R and MainActivity,
 * which is why this module keeps the same namespace and its own MainActivity.
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val shared = "../app/src/main"

android {
    namespace = "dev.eymistaken.parabolic"
    compileSdk = 37

    defaultConfig {
        // A different id so it can sit next to the main app on the same phone.
        applicationId = "dev.eymistaken.parabolic.legacy"
        minSdk = 24
        targetSdk = 36
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1001002
        versionName = (project.findProperty("versionName") as String?) ?: "1.1.2"
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs(
                "$shared/java/dev/eymistaken/parabolic/core",
                "$shared/java/dev/eymistaken/parabolic/service",
            )
            // Launcher icons, the logo and the notification strings. The main
            // app's Theme.Parabolic comes along unused; this APK has its own.
            res.srcDirs("$shared/res", "src/main/res")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                // Relative to the module, like ../app, so the same line works for both.
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../app/proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    androidResources {
        noCompress += listOf("yt-dlp")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    // The stable Material 3 the BOM picks, not the Expressive release candidate.
    implementation(libs.androidx.compose.material3.stable)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.documentfile)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    implementation(libs.youtubedl.android)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.youtubedl.aria2c)
}
