// 根目录 build.gradle.kts
buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    // 1. Android 插件 (版本号要固定)
    id("com.android.application") version "8.13.1" apply false
    id("com.android.library") version "8.13.1" apply false

    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false

    // 2. Kotlin 全家桶 (全部统一到 2.0.0)
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    // Compose 编译器插件 (Kotlin 2.1)
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    // 序列化插件
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    
    // 3. Firebase 相关插件
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}