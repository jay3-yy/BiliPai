plugins {
    id("com.android.application")
    // AGP 9+ provides built-in Kotlin; do not apply org.jetbrains.kotlin.android
    // Baseline profile 插件（AGP 9 兼容线 1.5.0+），提供 :app:generateBaselineProfile 任务
    alias(libs.plugins.androidx.baselineprofile)
    // Compose 编译器插件
    id("org.jetbrains.kotlin.plugin.compose")
    // JSON 序列化插件
    id("org.jetbrains.kotlin.plugin.serialization")
    // Compose 预组合兼容补丁：guard LayoutNodeSubcompositionsState 的 detached-root owner 查找
    id("com.android.bilipai.compose-detached-owner-guard")
    // 弹窗退出后可能仍收到锚点布局回调，不再更新已移除的窗口。
    id("com.android.bilipai.compose-popup-window-guard")
    // Foundation 拖拽兼容补丁：在进入拖拽状态前过滤不受支持的输入类型。
    id("com.android.bilipai.compose-drag-start-guard")
    // 导航分发器被父级级联释放后，容忍子页面重复清理及延后的启用状态更新。
    id("com.android.bilipai.navigationevent-disposal-guard")
    id("com.android.bilipai.workmanager-namespace-guard")
    // Room 数据库编译插件
    id("com.google.devtools.ksp")
    // 🔥 Firebase 相关插件
    // id("com.google.gms.google-services")
    // id("com.google.firebase.crashlytics")
}

// 发布使用已保存的规则；交互采集仅通过显式生成任务执行。
// 采集结果独立写入 generated/baselineProfiles，不覆盖 main/baseline-prof.txt 的热点补充。
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
}

abstract class PrepareKspGeneratedDirTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun prepare() {
        outputDir.get().asFile.mkdirs()
    }
}

abstract class ExportBiliPaiApkTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputDirectory
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val packagedApkDirectory: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.Input
    abstract val outputFileName: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDirectory: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun export() {
        val deliveryName = outputFileName.get().trim()
        // 交付名必须是 BiliPai- 前缀；拒绝 app-release / app-dev 等 AGP 默认名。
        check(
            deliveryName.startsWith("BiliPai-") &&
                deliveryName.endsWith(".apk", ignoreCase = true) &&
                !deliveryName.lowercase().startsWith("app-") &&
                !deliveryName.contains("app-release", ignoreCase = true) &&
                !deliveryName.contains("app-dev", ignoreCase = true)
        ) {
            "Delivery APK must be BiliPai-<version>.apk, not default AGP names like app-release.apk. Got: $deliveryName"
        }

        val sourceApks = packagedApkDirectory.get().asFile
            .walkTopDown()
            .filter { file -> file.isFile && file.extension.equals("apk", ignoreCase = true) }
            .toList()
        check(sourceApks.size == 1) {
            "Expected exactly one packaged APK, found ${sourceApks.size}: ${sourceApks.joinToString()}"
        }

        val destinationDirectory = outputDirectory.get().asFile.apply { mkdirs() }
        destinationDirectory.listFiles()
            ?.filter { file -> file.isFile && file.extension.equals("apk", ignoreCase = true) }
            ?.forEach { staleApk -> staleApk.delete() }
        val target = destinationDirectory.resolve(deliveryName)
        sourceApks.single().copyTo(target = target, overwrite = true)
        logger.lifecycle("BiliPai delivery APK → ${target.absolutePath}")
    }
}

fun String.toBuildConfigStringLiteral(): String {
    val escaped = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
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
    androidResources {
        generateLocaleConfig = true
        // localeFilters is forwarded to aapt2's -c option; use Android resource qualifier syntax.
        localeFilters += listOf("en", "zh-rCN", "zh-rTW")
    }
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.android.purebilibili"
        minSdk = 26
        targetSdk = 37
        // 版本名按发行计划确定；versionCode 每次发布单调 +1
        // 规范：docs/wiki/VERSIONING.md · 更新日志：CHANGELOG.md
        versionCode = 542
        versionName = "0.7.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 指定打包的 CPU 架构（64 位 only）
        ndk {
            // arm64-v8a: modern 64-bit devices
            abiFilters += listOf("arm64-v8a")
        }

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
        // AGP 9 defaults this off; buildTypes use resValue("string", "app_name", ...)
        resValues = true
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
            // 各依赖自带的 maven 元数据不需要打进 APK（pinyin4j 相关曾因此报 duplicate entry）
            excludes += "META-INF/maven/**"
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
        abortOnError = true
        checkDependencies = true
        // CI profiles separate performance contracts from informational style findings.
        val ciChecks = providers.gradleProperty("bili.ciChecks").orNull
        if (ciChecks != null) {
            val styleIssues = setOf(
                "BiliPaiHardcodedColor",
                "BiliPaiHardcodedDimension",
                "BiliPaiHardcodedTypography",
                "BiliPaiHardcodedShape",
                "BiliPaiHardcodedMotion",
                "BiliPaiHardcodedSurface",
            )
            val selectedIssues = when (ciChecks) {
                "performance" -> setOf("BiliPaiFrameBlurAllocation", "BiliPaiModelStability")
                "style" -> styleIssues
                else -> throw GradleException("bili.ciChecks must be performance or style")
            }
            // Never let a platform baseline or style warnings hide a broken checker.
            baseline = null
            val checkerFailures = setOf("LintError", "ObsoleteLintCustomCheck")
            fatal += checkerFailures
            checkOnly += selectedIssues
            checkOnly += checkerFailures
            if (ciChecks == "style") {
                warning += styleIssues
            }
        }
    }
}

// AGP 中间产物改用 BiliPai-<version> 基名（避免 app-release.apk）；
// 交付物再由 export 写成最终 `BiliPai-<version>[.dev].apk`。
val biliApkVersionName: String = android.defaultConfig.versionName ?: "0"
base {
    archivesName.set("BiliPai-$biliApkVersionName")
}
androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name.lowercase()
        if (variantName == "release" || variantName == "dev") {
            val capitalizedVariantName = variant.name.replaceFirstChar { character ->
                character.uppercaseChar()
            }
            val deliveryFileName = when (variantName) {
                "release" -> "BiliPai-$biliApkVersionName.apk"
                else -> "BiliPai-$biliApkVersionName-$variantName.apk"
            }
            // 中间产物直接用规范名：beta 等预发布版本不应带 AGP 默认的 -release 后缀
            // （archivesName 已含完整 versionName）。AGP 9 VariantOutput.outputFileName。
            variant.outputs.forEach { output ->
                output.outputFileName.set(deliveryFileName)
            }
            val exportTask = tasks.register<ExportBiliPaiApkTask>(
                "export${capitalizedVariantName}Apk"
            ) {
                group = "build"
                description =
                    "Exports the $variantName APK as $deliveryFileName (never app-release / app-dev)."
                packagedApkDirectory.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
                outputFileName.set(deliveryFileName)
                outputDirectory.set(layout.buildDirectory.dir("outputs/bilipai/$variantName"))
            }
            // assemble 完成后导出规范名；package 同理。
            listOf(
                "assemble$capitalizedVariantName",
                "package$capitalizedVariantName",
            ).forEach { taskName ->
                tasks.matching { task -> task.name == taskName }.configureEach {
                    finalizedBy(exportTask)
                }
            }
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

// 每个 KSP 任务仅准备自己的目录；其他构建类型的生成文件不参与本任务的输出检查。
val prepareKspGeneratedDirsByTaskName = listOf("debug", "release", "dev", "smooth")
    .flatMap { buildType ->
        listOf("", "UnitTest", "AndroidTest").map { suffix -> "$buildType$suffix" }
    }
    .associate { variantName ->
        val capitalizedVariantName = variantName.replaceFirstChar { it.uppercaseChar() }
        val prepareTask = tasks.register<PrepareKspGeneratedDirTask>(
            "prepare${capitalizedVariantName}KspGeneratedDir"
        ) {
            outputDir.set(layout.buildDirectory.dir("generated/ksp/$variantName"))
        }
        "ksp${capitalizedVariantName}Kotlin" to prepareTask
    }

tasks.matching { task ->
    task.name in prepareKspGeneratedDirsByTaskName
}.configureEach {
    dependsOn(prepareKspGeneratedDirsByTaskName.getValue(name))
}

composeCompiler {
    // 稳定性配置无条件生效。data/model 的属性约束由 BiliPaiModelStability Lint
    // 跨模块检查；List 是只读接口，不等于深度不可变，需要调用方保持所有权约定。
    // 错误的稳定性承诺仍会导致本该发生的重组被跳过。
    // 详细理由见 compose_stability.conf 的文件头。
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf")
    )

    // 🔥 Compose 编译器指标：会显著拖慢编译，所以只在显式要求时打开。
    //   ./gradlew :app:compileSmoothKotlin -Pbili.compose.metrics=true
    // 产出 build/compose_metrics/*-classes.txt 与 build/compose_reports/。
    // 这是**唯一不需要设备就能量化重组行为**的指标——用来回答
    // 「VideoCard 到底 skippable 了没有」这类过去只能靠猜的问题。
    //
    // 之前这段是注释掉的死代码，等于把这个能力关在门外。
    if (providers.gradleProperty("bili.compose.metrics").orNull == "true") {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}

dependencies {
    lintChecks(project(":ci-lint"))
    implementation(project(":brand-motion"))
    val material3Version = "1.5.0-alpha29"
    val material3AdaptiveVersion = "1.3.0"
    val media3Version = "1.11.1"
    val lifecycleVersion = "2.11.0"
    val roomVersion = "2.8.4"
    val hazeVersion = "2.0.1"

    implementation(project(":settings-core"))
    implementation(project(":network-core"))
    implementation(project(":core-data"))
    implementation(project(":core-player"))
    implementation(project(":plugin-sdk"))
    implementation(project(":design-system"))
    implementation(project(":danmaku-engine"))

    // Baseline profile 生成模块：generateBaselineProfile 时在真机/托管设备上跑
    // :baselineprofile 的宏基准并把产物合并进 release/dev 变体。
    baselineProfile(project(":baselineprofile"))

    // --- 1. Compose UI ---
    // Material3 1.5.0-alpha25 is built against Compose 1.12.0-beta01. Use the
    // matching beta BOM so Compose groups are intentionally aligned instead of
    // being upgraded transitively past the stable BOM's 1.11.4 constraints.
    implementation(platform(libs.androidx.compose.bom))
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
    implementation("androidx.compose.material3.adaptive:adaptive:$material3AdaptiveVersion")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:$material3AdaptiveVersion")
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.squircle)
    implementation(libs.miuix.icons)
    implementation(project(":miuix-navigation"))
    // 图标扩展库 (全屏、设置图标等)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    // Compose UI 1.12 requires AndroidClipboard for native text selection/paste.
    // rc14's wrapper only implements Clipboard and crashes in RSS/video note editors (#744).
    implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-cmp-1.12.0-beta03")

    // --- 2. Network (网络请求) ---
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    // Official converter since Retrofit 2.10; binary-compatible with Retrofit 3
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
    // 🔥 Brotli Decompression (for Bilibili Live Danmaku ProtoVer=3)
    implementation("org.brotli:dec:0.1.2")

    // --- 3. Image (图片加载) ---
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-cache-control:3.6.3")
    implementation("io.coil-kt.coil3:coil-gif:3.6.3")  // 🔥 GIF 动图支持
    
    // --- 3.1 Palette (颜色提取 - 动态取色) ---
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("com.materialkolor:material-kolor-material3:6.0.0-beta01")
    implementation("com.github.skydoves:colorpicker-compose:1.1.4")
    
    // --- 3.2 Lottie (动画效果) ---
    implementation("com.airbnb.android:lottie-compose:6.7.1")
    
    // --- 3.3 Haze 2 (毛玻璃：core + blur + materials) ---
    implementation("dev.chrisbanes.haze:haze:$hazeVersion")
    implementation("dev.chrisbanes.haze:haze-blur:$hazeVersion")
    implementation("dev.chrisbanes.haze:haze-blur-materials:$hazeVersion")

    // --- 3.4 骨架屏加载 ---
    // compose-shimmer 已移除：全仓 0 处 import，骨架屏早已改用自研的
    // core/ui/DesignSystem.kt Modifier.shimmer() 与 core/util/ModifierExt.kt
    // shimmerEffect()。VideoDetailSkeletonStructureTest 甚至专门断言不再使用它，
    // 说明迁移是有意为之，只是依赖声明没跟着删。

    // --- 3.5 Compose Cupertino (iOS 风格 UI 组件) ---
    // 提供 iOS 风格的 Switch、Button、Picker、Dialog 等组件
    implementation("io.github.alexzhirkevich:cupertino:0.1.0-alpha04")
    implementation("io.github.alexzhirkevich:cupertino-adaptive:0.1.0-alpha04")
    // 🍎 800+ iOS SF Symbols 风格图标
    implementation("io.github.alexzhirkevich:cupertino-icons-extended:0.1.0-alpha04")
    
    // --- 3.6 Miuix Navigation ---
    // Miuix stack renderer and predictive-back driver.
    
    // --- 3.7 Startup (应用初始化) ---
    implementation("androidx.startup:startup-runtime:1.2.0")

    // Offline article extraction; no WebView or website JavaScript execution.
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("org.jsoup:jsoup:1.23.2")
    
    // --- 4. Player (视频播放器 Media3) ---
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")  // 🔥 HLS 直播流支持
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-extractor:$media3Version")
    implementation("androidx.media3:media3-datasource:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation(project(":dolby-ffmpeg-decoder"))

    // --- 5. Danmaku (弹幕引擎) ---
    // 内部维护的 ByteDance DanmakuRenderEngine 分支；上游已归档。
    // 对 app 只暴露项目自有的引擎中立接口，便于后续替换渲染后端。
    
    // 注：FFmpegKit 已于 2025 年停止维护，改用 ExoPlayer 直接播放分离音视频

    // --- 6. Database (Room 数据库) ---
    implementation("androidx.collection:collection:1.5.0")
    implementation("androidx.paging:paging-common:3.4.2")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // --- 7. DataStore (本地存储 Cookie/设置) ---
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // --- 8. Utils (工具类) ---
    // 二维码生成
    implementation("com.google.zxing:core:3.5.4")
    // BiliPai 设备间二维码传输扫码
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    // Pinyin 拼音转换 (用于模糊搜索)
    // TinyPinyin：内置无声调字典表（常用多音字带上下文消歧），无需初始化、无资源加载。
    // 不用 com.belerweb:pinyin4j:2.5.0——其 jar 内部含重复条目（pom.xml ×2），
    // AGP 9/Kotlin BTAPI 会因 duplicate entry 拒绝编译 classpath。
    implementation("io.github.biezhi:TinyPinyin:2.0.3.RELEASE")
    // Core KTX
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")  // 🔋 ProcessLifecycleOwner 后台检测
    implementation("androidx.metrics:metrics-performance:1.0.0")
    implementation("androidx.window:window:1.5.1")

    // --- 8.1 WorkManager (后台下载任务) ---
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    // --- 8.2 Google Cast (CAF) ---
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
    implementation("androidx.mediarouter:mediarouter:1.8.1")

    // --- 8.3 DLNA Local Proxy (投屏) ---
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
    // Codec and CDN tests require real Android Base64/Uri, not returnDefaultValues stubs.
    testImplementation("org.robolectric:robolectric:4.17")
    // 真实 org.json 实现：android.jar 的 org.json 在单测里是桩（returnDefaultValues 下
    // 静默返回空值），CommandDanmakuPolicy 等生产解析路径需要真实实现才能单测。
    testImplementation("org.json:json:20240303")
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    // JUnit 4 兼容层 (允许 JUnit 5 运行 JUnit 4 测试)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    // AGP 9 使用内建 Kotlin 2.4；JUnit Platform 下必须使用同版本的 JUnit 5
    // 适配层，否则 kotlin.test.Test 不会落到 JVM 测试注解。
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.4.0")
    // MockK for Kotlin mocking
    testImplementation("io.mockk:mockk:1.13.9")
    // Coroutines testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // Turbine for Flow testing
    testImplementation("app.cash.turbine:turbine:1.0.0")
    
    // --- 13. Android Instrumented Tests ---
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.window:window-testing:1.5.1")
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
    // Offline/local builds stay opt-in. The release workflow explicitly supplies this flag.
    val uploadReleaseMapping = providers.gradleProperty("bili.firebase.uploadMapping")
        .map { it.toBoolean() }
        .getOrElse(false)
    android.buildTypes.configureEach {
        val uploadMappingForBuildType = name == "release" && uploadReleaseMapping
        configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
            mappingFileUploadEnabled = uploadMappingForBuildType
        }
    }
}
