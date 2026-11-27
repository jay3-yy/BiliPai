plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.android.bilipai.wear"
    compileSdk { version = release(37) { minorApiLevel = 0 } }

    defaultConfig {
        applicationId = "com.android.purebilibili"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-wear"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }

    val releaseStoreFile = providers.environmentVariable("BILI_RELEASE_STORE_FILE").orNull
    val releaseStorePassword = providers.environmentVariable("BILI_RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("BILI_RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("BILI_RELEASE_KEY_PASSWORD").orNull
    if (listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }) {
        signingConfigs.create("officialRelease") {
            storeFile = file(requireNotNull(releaseStoreFile))
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("officialRelease")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            // 与 :app 保持一致的包名后缀与默认调试签名，满足 Wear Data Layer 校验要求。
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
