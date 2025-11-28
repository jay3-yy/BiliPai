// 根目录 build.gradle.kts
buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    // 1. Android 插件 (版本号要固定)
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false

    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false

    // 2. Kotlin 全家桶 (全部统一到 2.0.0)
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    // Compose 编译器插件 (Kotlin 2.0 新增)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    // 序列化插件
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false
}