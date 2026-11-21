plugins {
    id("com.android.library")
    // AGP 9+ built-in Kotlin
}

android {
    namespace = "com.android.purebilibili.network.core"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${libs.versions.kotlin.get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
