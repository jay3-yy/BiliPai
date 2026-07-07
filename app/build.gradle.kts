plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Compose 编译器插件
    id("org.jetbrains.kotlin.plugin.compose")
    // JSON 序列化插件
    id("org.jetbrains.kotlin.plugin.serialization")
    // Room 数据库编译插件
    id("com.google.devtools.ksp")
    // 🔥 Firebase 相关插件
    // id("com.google.gms.google-services")
    // id("com.google.firebase.crashlytics")
}

abstract class PrepareKspGeneratedDirsTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.OutputDirectories
    abstract val outputDirs: org.gradle.api.file.ConfigurableFileCollection

    @org.gradle.api.tasks.TaskAction
    fun prepare() {
        outputDirs.files.forEach { it.mkdirs() }
    }
}

fun String.toBuildConfigStringLiteral(): String {
    val escaped = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

configurations.all {
    exclude(group = "androidx.navigationevent", module = "navigationevent-compose")
}

val debugVerboseLogsEnabled = providers.gradleProperty("bili.debug.verboseLogs")
    .map(String::toBoolean)
    .orElse(false)
    .get()
val debugVerboseRuntimeLogPersistenceEnabled = providers.gradleProperty("bili.debug.persistVerboseLogs")
    .map(String::toBoolean)
    .orElse(false)
    .get()
val debugLeakCanaryEnabled = providers.gradleProperty("bili.debug.leakCanary")
    .map(String::toBoolean)
    .orElse(false)
    .get()
val debugUiToolingRuntimeEnabled = providers.gradleProperty("bili.debug.uiTooling")
    .map(String::toBoolean)
    .orElse(false)
    .get()
val buildCommitSha = providers.gradleProperty("bili.build.commitSha")
    .orElse("local")
    .get()
val buildGitRef = providers.gradleProperty("bili.build.gitRef")
    .orElse("")
    .get()
val buildWorkflowRunId = providers.gradleProperty("bili.build.workflowRunId")
    .orElse("")
    .get()
val buildWorkflowRunUrl = providers.gradleProperty("bili.build.workflowRunUrl")
    .orElse("")
    .get()
val buildReleaseTag = providers.gradleProperty("bili.build.releaseTag")
    .orElse("")
    .get()

android {
    namespace = "com.android.purebilibili"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    // 投屏服务进程开关：
    // 默认独立 :cast 进程（通过 CastBridgeService 做跨进程 IPC）
    // 如需快速回滚，可传 -PcastServiceProcess=com.android.purebilibili
    val castServiceProcess =
        (project.findProperty("castServiceProcess") as String?) ?: ":cast"

    defaultConfig {
        applicationId = "com.android.purebilibili"
        minSdk = 26
        targetSdk = 35  // 保持35以避免Android 16的新运行时行为
        // 🔥🔥 [版本号] 发布新版前记得更新！格式：versionCode +1, versionName 递增
        // 更新日志：CHANGELOG.md
        versionCode = 248
        versionName = "9.8.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 👇👇👇 指定打包的 CPU 架构（64 位 only）👇👇👇
        ndk {
            // arm64-v8a: modern 64-bit devices
            abiFilters += listOf("arm64-v8a")
        }

        manifestPlaceholders["castServiceProcess"] = castServiceProcess
        buildConfigField("String", "BUILD_COMMIT_SHA", buildCommitSha.toBuildConfigStringLiteral())
        buildConfigField("String", "BUILD_GIT_REF", buildGitRef.toBuildConfigStringLiteral())
        buildConfigField("String", "BUILD_WORKFLOW_RUN_ID", buildWorkflowRunId.toBuildConfigStringLiteral())
        buildConfigField("String", "BUILD_WORKFLOW_RUN_URL", buildWorkflowRunUrl.toBuildConfigStringLiteral())
        buildConfigField("String", "BUILD_RELEASE_TAG", buildReleaseTag.toBuildConfigStringLiteral())
    }
    
    // 🔥 Keep a single APK artifact while packaging arm64-v8a only
    splits {
        abi {
            isEnable = false
        }
    }

    buildTypes {
        release {
            // Disable PNG crunching to avoid AAPT errors
            isCrunchPngs = false
            buildConfigField("boolean", "ALLOW_HARDCODED_DNS_FALLBACK", "false")
            buildConfigField("boolean", "ENABLE_VERBOSE_DEBUG_LOGS", "false")
            buildConfigField("boolean", "ENABLE_VERBOSE_RUNTIME_LOG_PERSISTENCE", "false")
            // 🔥 启用 R8 代码压缩
            isMinifyEnabled = true
            // 🔥 启用资源压缩 (移除未使用的资源)
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug 构建保持快速编译
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "BiliPai Debug")
            buildConfigField("boolean", "ALLOW_HARDCODED_DNS_FALLBACK", "true")
            buildConfigField("boolean", "ENABLE_VERBOSE_DEBUG_LOGS", debugVerboseLogsEnabled.toString())
            buildConfigField(
                "boolean",
                "ENABLE_VERBOSE_RUNTIME_LOG_PERSISTENCE",
                debugVerboseRuntimeLogPersistenceEnabled.toString()
            )
            isMinifyEnabled = false
            isShrinkResources = false
        }
        create("dev") {
            // Dev 保持“接近发布”的验证语义，不用于日常本地快速迭代。
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "BiliPai Dev")
            buildConfigField("boolean", "ALLOW_HARDCODED_DNS_FALLBACK", "true")
            buildConfigField("boolean", "ENABLE_VERBOSE_DEBUG_LOGS", "false")
            buildConfigField("boolean", "ENABLE_VERBOSE_RUNTIME_LOG_PERSISTENCE", "false")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        create("smooth") {
            // Smooth 用于本地快速验证正式版运行语义：保留非 debug 行为，但跳过 R8/资源压缩。
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-smooth"
            resValue("string", "app_name", "BiliPai Smooth")
            buildConfigField("boolean", "ALLOW_HARDCODED_DNS_FALLBACK", "true")
            buildConfigField("boolean", "ENABLE_VERBOSE_DEBUG_LOGS", "false")
            buildConfigField("boolean", "ENABLE_VERBOSE_RUNTIME_LOG_PERSISTENCE", "false")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("dev", "release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // 🔥 排除不必要的文件以减小体积
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/kotlin/**"
            excludes += "DebugProbesKt.bin"
            // 📺 Cling DLNA 库冲突文件
            excludes += "META-INF/beans.xml"
        }
    }
    
    // 🚀 启用 JUnit 5
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        // 🔥 允许 Android 类在单元测试中返回默认值而非抛出异常
        unitTests.isReturnDefaultValues = true
    }

    lint {
        baseline = file("lint-baseline.xml")
        textReport = true
        abortOnError = true
    }
    
    // 🔥 自定义 APK 输出文件名
    applicationVariants.configureEach {
        val variant = this
        outputs.configureEach {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output.outputFileName = "BiliPai-${variant.name}-${variant.versionName}.apk"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

ksp {
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

val prepareKspGeneratedVariants = listOf(
    "debug",
    "debugUnitTest",
    "release",
    "releaseUnitTest",
    "dev",
    "devUnitTest"
)

val prepareKspGeneratedDirs by tasks.registering(PrepareKspGeneratedDirsTask::class) {
    outputDirs.from(
        prepareKspGeneratedVariants.map { variantName ->
            layout.buildDirectory.dir("generated/ksp/$variantName")
        }
    )
}

tasks.matching { task ->
    task.name.startsWith("ksp") && task.name.endsWith("Kotlin")
}.configureEach {
    dependsOn(prepareKspGeneratedDirs)
}

// 🔥 Compose 编译器性能指标 (仅在需要分析时启用，会拖慢编译速度)
// composeCompiler {
//     reportsDestination = layout.buildDirectory.dir("compose_reports")
//     metricsDestination = layout.buildDirectory.dir("compose_metrics")
// }

dependencies {
    val miuixVersion = "0.9.2"
    val material3Version = "1.5.0-alpha18"
    val media3Version = "1.10.0"
    val lifecycleVersion = "2.10.0"
    val roomVersion = "2.8.4"

    implementation(project(":settings-core"))
    implementation(project(":network-core"))
    implementation(project(":plugin-sdk"))

    // --- 1. Compose UI ---
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))  // 🔥 更新到最新版本
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")  // 🚀 For AppCompatDelegate night mode
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime-tracing")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:$material3Version")
    implementation("androidx.compose.material3:material3-window-size-class:$material3Version") // [新增] 窗口大小类
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuixVersion")
    // 图标扩展库 (全屏、设置图标等)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc14")

    // --- 2. Network (网络请求) ---
    implementation("com.squareup.retrofit2:retrofit:2.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
    // 🔥 Brotli Decompression (for Bilibili Live Danmaku ProtoVer=3)
    implementation("org.brotli:dec:0.1.2")

    // --- 3. Image (图片加载) ---
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")  // 🔥 GIF 动图支持
    
    // --- 3.1 Palette (颜色提取 - 动态取色) ---
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("com.materialkolor:material-kolor:4.1.1")
    implementation("com.github.skydoves:colorpicker-compose:1.1.4")
    
    // --- 3.2 Lottie (动画效果) ---
    implementation("com.airbnb.android:lottie-compose:6.7.1")
    
    // --- 3.3 Haze (毛玻璃效果) ---
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    implementation("dev.chrisbanes.haze:haze-materials:1.7.2")
    
    // --- 3.4 Shimmer (骨架屏加载) ---
    implementation("com.valentinilk.shimmer:compose-shimmer:1.4.0")
    
    // --- 3.5 Compose Cupertino (iOS 风格 UI 组件) ---
    // 提供 iOS 风格的 Switch、Button、Picker、Dialog 等组件
    implementation("io.github.alexzhirkevich:cupertino:0.1.0-alpha04")
    implementation("io.github.alexzhirkevich:cupertino-adaptive:0.1.0-alpha04")
    // 🍎 800+ iOS SF Symbols 风格图标
    implementation("io.github.alexzhirkevich:cupertino-icons-extended:0.1.0-alpha04")
    
    // --- 3.6 Navigation3 (Compose 自有返回栈与预测性返回迁移层) ---
    implementation("androidx.navigation3:navigation3-runtime:1.1.3")
    // navigation3-ui 使用 Miuix fork：在 androidx 同包名下提供 NavDisplayTransitionEffects
    // (blockInputDuringTransition / enableCornerClip / dimAmount)，与 InstallerX 对齐，
    // 在转场期间屏蔽触摸拦截以消除预测性返回手势冲突。runtime/event 仍用 androidx 1.1.3/1.1.2。
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:$miuixVersion") {
        exclude(group = "androidx.navigationevent", module = "navigationevent-compose")
        exclude(group = "org.jetbrains.androidx.navigationevent", module = "navigationevent-compose")
    }
    // 预测式返回：使用本地 vendored 版 androidx.navigationevent.compose（位于
    // app/src/main/java/androidx/navigationevent/compose/），以便在 onBackCompleted 里
    // 把 transitionState 的提交延迟到用户回调内执行，保证 scale/aosp 退出动画能在
    // InProgress 状态下读取到最新手势数据。下面排除上游同 group 的 compose 产物，避免与
    // 本地源码冲突。
    implementation("androidx.navigationevent:navigationevent:1.1.2")
    
    // --- 3.7 Startup (应用初始化) ---
    implementation("androidx.startup:startup-runtime:1.2.0")
    
    // --- 3.8 Backdrop (液态玻璃效果) ---
    // 提供透镜折射、玻璃高光、连续圆角等 iOS/visionOS 风格视觉效果
    implementation("io.github.kyant0:backdrop:2.0.0-alpha03")


    // --- 4. Player (视频播放器 Media3) ---
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")  // 🔥 HLS 直播流支持
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-datasource:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")

    // --- 5. Danmaku (弹幕引擎) ---
    // 🔥 使用 ByteDance DanmakuRenderEngine - 轻量级高性能弹幕渲染引擎
    implementation("com.github.bytedance:DanmakuRenderEngine:v0.1.0")
    
    // 注：FFmpegKit 已于 2025 年停止维护，改用 ExoPlayer 直接播放分离音视频

    // --- 6. Database (Room 数据库) ---
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // --- 7. DataStore (本地存储 Cookie/设置) ---
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // --- 8. Utils (工具类) ---
    // 二维码生成
    implementation("com.google.zxing:core:3.5.4")
    // Pinyin 拼音转换 (用于模糊搜索)
    implementation("com.belerweb:pinyin4j:2.5.0")
    // Core KTX
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")  // 🔋 ProcessLifecycleOwner 后台检测
    implementation("androidx.metrics:metrics-performance:1.0.0")
    implementation("androidx.window:window")

    // --- 8.1 WorkManager (后台下载任务) ---
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    // --- 8.2 Google Cast (CAF) ---
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
    implementation("androidx.mediarouter:mediarouter:1.8.1")

    // --- 8.3 DLNA & Local Proxy (投屏) ---
    // DLNA Casting (Cling)
    implementation("org.fourthline.cling:cling-core:2.1.2")
    implementation("org.fourthline.cling:cling-support:2.1.2")
    // Jetty (Cling 传输层依赖)
    implementation("org.eclipse.jetty:jetty-server:8.1.22.v20160922")
    implementation("org.eclipse.jetty:jetty-servlet:8.1.22.v20160922")
    implementation("org.eclipse.jetty:jetty-client:8.1.22.v20160922")
    implementation("javax.servlet:javax.servlet-api:3.1.0")
    
    // NanoHTTPD (Lightweight local proxy server)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    
    // --- 9. SplashScreen (启动屏支持) ---
    implementation("androidx.core:core-splashscreen:1.2.0")
    
    // --- 10. ProfileInstaller (启动优化) ---
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    
    // --- 11. Firebase (崩溃追踪和分析) ---
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    // --- 11. Debug (调试工具) ---
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    if (debugUiToolingRuntimeEnabled) {
        debugImplementation("androidx.compose.ui:ui-tooling")
    }
    if (debugLeakCanaryEnabled) {
        // 🔥 LeakCanary - 内存泄漏检测 (按需启用)
        debugImplementation("com.squareup.leakcanary:leakcanary-android:2.13")
    }
    
    // --- 12. Testing (测试框架) ---
    // JUnit 4 (兼容旧测试)
    testImplementation("junit:junit:4.13.2")
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    // JUnit 4 兼容层 (允许 JUnit 5 运行 JUnit 4 测试)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
    // Kotlin Test (提供 assertEquals, assertTrue 等断言)
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
    // MockK for Kotlin mocking
    testImplementation("io.mockk:mockk:1.13.9")
    // Coroutines testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    // Turbine for Flow testing
    testImplementation("app.cash.turbine:turbine:1.0.0")
    
    // --- 13. Android Instrumented Tests ---
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.03.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.register("assembleFast") {
    group = "build"
    description = "Assembles the fast local development variant (debug)."
    dependsOn("assembleDebug")
}

tasks.register("installFast") {
    group = "install"
    description = "Installs the fast local development variant (debug) on a connected device."
    dependsOn("installDebug")
}

tasks.register("assembleFastRelease") {
    group = "build"
    description = "Assembles the fast local release-like variant (smooth, no R8/resource shrink)."
    dependsOn("assembleSmooth")
}

tasks.register("installFastRelease") {
    group = "install"
    description = "Installs the fast local release-like variant (smooth, no R8/resource shrink)."
    dependsOn("installSmooth")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    tasks.matching { task ->
        task.name.startsWith("uploadCrashlyticsMappingFile")
    }.configureEach {
        // 本地构建不上传 mapping，避免 release/dev 在离线环境失败。
        enabled = false
    }
}
