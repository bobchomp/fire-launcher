import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Bump both together for each release. The version also goes into the APK file name,
// e.g. fire-launcher-1.1-release.apk.
val appVersionCode = 3
val appVersionName = "1.2"

base {
    archivesName.set("fire-launcher-$appVersionName")
}

android {
    namespace = "com.bobchomp.firelauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bobchomp.firelauncher"
        minSdk = 25 // Fire OS 6 (Android 7.1)
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            // R8 + resource shrinking keep the APK small and fast on older sticks.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed with the local debug key so the release APK can be sideloaded
            // without setting up a keystore. Swap in a real signing config before
            // distributing it anywhere.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
