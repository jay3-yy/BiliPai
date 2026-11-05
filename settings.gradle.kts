pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// ASM 补丁插件（ComposeDetachedOwnerGuard）以组合构建提供，独立 classpath，不进入 buildSrc。
includeBuild("build-logic")

// Optional local checkout for deterministic development when GitHub Packages credentials are not
// available. Production/default resolution still uses the exact declared package coordinates.
providers.gradleProperty("bili.miuix.source").orNull?.let { sourcePath ->
    includeBuild(sourcePath) {
        dependencySubstitution {
            substitute(module("top.yukonga.miuix.kmp:miuix-core-android"))
                .using(project(":miuix-core"))
            substitute(module("top.yukonga.miuix.kmp:miuix-ui-android"))
                .using(project(":miuix-ui"))
            substitute(module("top.yukonga.miuix.kmp:miuix-shader-android"))
                .using(project(":miuix-shader"))
            substitute(module("top.yukonga.miuix.kmp:miuix-blur-android"))
                .using(project(":miuix-blur"))
            substitute(module("top.yukonga.miuix.kmp:miuix-preference-android"))
                .using(project(":miuix-preference"))
            substitute(module("top.yukonga.miuix.kmp:miuix-squircle-android"))
                .using(project(":miuix-squircle"))
            substitute(module("top.yukonga.miuix.kmp:miuix-icons-android"))
                .using(project(":miuix-icons"))
            substitute(module("top.yukonga.miuix.kmp:miuix-nav-android"))
                .using(project(":miuix-nav"))
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("http://nexus.itgsa.com:5566/repository/sharedViewKit/")
            isAllowInsecureProtocol = true
            content { includeGroup("gsai.sdk") }
        }
        // GitHub Packages (compose-miuix-ui/miuix).
        // Authentication is required even for this public package. Keep credentials outside
        // the repository in ~/.gradle/gradle.properties or environment variables.
        val gprUser = providers.gradleProperty("gpr.user")
            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
        val gprKey = providers.gradleProperty("gpr.key")
            .orElse(providers.environmentVariable("GITHUB_TOKEN"))
        maven {
            name = "GitHubPackagesMiuix"
            url = uri("https://maven.pkg.github.com/compose-miuix-ui/miuix")
            if (gprUser.isPresent && gprKey.isPresent) {
                credentials {
                    username = gprUser.get()
                    password = gprKey.get()
                }
            }
        }
        // Cling Repo
        maven { 
            url = uri("http://4thline.org/m2") 
            isAllowInsecureProtocol = true
        }
    }
}

rootProject.name = "BiliPai"
include(":app")
include(":app-tv")
include(":core-data")
include(":core-player")
include(":baselineprofile")
include(":settings-core")
include(":network-core")
include(":plugin-sdk")
include(":design-system")
include(":design-tokens")
include(":brand-motion")
include(":dolby-ffmpeg-decoder")
include(":danmaku-engine")
include(":miuix-navigation")
include(":ci-lint")

 
