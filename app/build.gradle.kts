plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Compose 编译器插件
    id("org.jetbrains.kotlin.plugin.compose")
    // JSON 序列化插件
    id("org.jetbrains.kotlin.plugin.serialization")
    // Room 数据库编译插件
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.android.purebilibili"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.android.purebilibili"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 👇👇👇 核心修复：指定打包的 CPU 架构 👇👇👇
        // 解决 INSTALL_FAILED_NO_MATCHING_ABIS 错误
        ndk {
            // arm64-v8a: 现代真机 (Pixel 7/8/9 等纯64位手机)
            // armeabi-v7a: 老旧真机
            // x86_64: 电脑模拟器
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- 1. Compose UI ---
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 图标扩展库 (全屏、设置图标等)
    implementation("androidx.compose.material:material-icons-extended")

    // --- 2. Network (网络请求) ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // --- 3. Image (图片加载) ---
    implementation("io.coil-kt:coil-compose:2.6.0")

    // --- 4. Player (视频播放器 Media3) ---
    implementation("androidx.media3:media3-exoplayer:1.3.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.0")
    implementation("androidx.media3:media3-ui:1.3.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.0")

    // --- 5. Danmaku (弹幕引擎) ---
    implementation("com.github.bilibili:DanmakuFlameMaster:0.9.25")

    // --- 6. Database (Room 数据库) ---
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // --- 7. DataStore (本地存储 Cookie/设置) ---
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // --- 8. Utils (工具类) ---
    // 二维码生成
    implementation("com.google.zxing:core:3.5.3")
    // Core KTX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- 9. Debug (调试工具) ---
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}