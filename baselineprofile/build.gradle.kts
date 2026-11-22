plugins {
    id("com.android.test")
    // Baseline profile 生成端：本模块宏基准的产物作为 profile 发布给 :app
    alias(libs.plugins.androidx.baselineprofile)
    // AGP 9+ built-in Kotlin
}

android {
    namespace = "com.android.purebilibili.baselineprofile"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW_BATTERY"
        // Enable Perfetto composition tracing in macrobenchmark runs when needed.
        testInstrumentationRunnerArguments["androidx.benchmark.fullTracing.enable"] = "true"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        managedDevices {
            allDevices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api31") {
                    device = "Pixel 6"
                    apiLevel = 31
                    systemImageSource = "aosp"
                }
                // Android 13 起可采集 RuntimeShader 折射与玻璃控件分支。
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api33") {
                    device = "Pixel 6"
                    apiLevel = 33
                    systemImageSource = "aosp"
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

// AGP 9 testedApks configurations omit TargetJvmEnvironment, causing KMP miuix transitive
// modules (miuix-core / miuix-material) to fall back to desktop variants.
configurations.matching { it.name.endsWith("TestedApks") }.configureEach {
    attributes.attribute(
        org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        objects.named(org.gradle.api.attributes.java.TargetJvmEnvironment.ANDROID),
    )
}

dependencies {
    // 与 androidx.baselineprofile 插件 1.5.0 对齐（1.4.1 与 AGP 9 不兼容线配套）
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.tracing:tracing-perfetto:1.0.0")
    implementation("androidx.tracing:tracing-perfetto-binary:1.0.0")
}
