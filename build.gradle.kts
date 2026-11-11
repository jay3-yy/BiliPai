// R8 与 AGP 分开固定，便于更新优化器而保持当前 Studio 支持的 AGP 9.3 系列。
// 历史记录涉及的视频卡片文件类由 app/proguard-rules.pro 暂作合并隔离。

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools:r8:9.5.28")
    }
}

plugins {
    // 1. Android 插件 (版本号要固定)
    // AGP ≥ 9.1 required by Compose BOM 2026.06 / Material3 alpha25 / Lifecycle 2.11 / Nav3 1.2
    id("com.android.application") version "9.3.2" apply false
    id("com.android.library") version "9.3.2" apply false
    id("com.android.test") version "9.3.2" apply false

    id("com.google.devtools.ksp") version "2.3.10" apply false

    // 2. Kotlin 插件（AGP 9+ 内置 Kotlin，无需 org.jetbrains.kotlin.android）
    // Compose 编译器插件
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    // 序列化插件
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false

    // 3. Firebase 相关插件
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}

// Custom policies must also run during each library's partial Lint analysis.
// App-only lintChecks would miss models moved into core-data.
subprojects {
    plugins.withId("com.android.library") {
        dependencies.add("lintChecks", project(":ci-lint"))
    }
}
