plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.android.purebilibili.miuixnavigation"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.animation:animation")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    api(libs.androidx.navigationevent.compose)
    implementation("androidx.savedstate:savedstate-compose:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation(libs.miuix.squircle)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
