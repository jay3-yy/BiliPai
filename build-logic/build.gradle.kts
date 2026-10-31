plugins {
    `java-gradle-plugin`
}

// compileOnly 按官方 gradle-recipes 模式：本组合构建的插件类在应用时与 AGP 同一插件类加载器，
// 接口运行期可见。不要改回 buildSrc —— buildSrc 的 compileOnly 会在 daemon 中报
// Unable to load class，implementation 又会把 gradle-api 的 AppPlugin 等类泄漏进主构建
// classpath，触发 "plugin is already on the classpath with an unknown version"。
dependencies {
    compileOnly("com.android.tools.build:gradle-api:${libs.versions.agp.get()}")
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-commons:9.8")
}

gradlePlugin {
    plugins {
        create("composePopupWindowGuard") {
            id = "com.android.bilipai.compose-popup-window-guard"
            implementationClass = "com.android.purebilibili.build.ComposePopupWindowGuardPlugin"
        }
        create("navigationEventDisposalGuard") {
            id = "com.android.bilipai.navigationevent-disposal-guard"
            implementationClass = "com.android.purebilibili.build.NavigationEventDisposalGuardPlugin"
        }
        create("composeDragStartGuard") {
            id = "com.android.bilipai.compose-drag-start-guard"
            implementationClass = "com.android.purebilibili.build.ComposeDragStartGuardPlugin"
        }
        create("composeDetachedOwnerGuard") {
            id = "com.android.bilipai.compose-detached-owner-guard"
            implementationClass = "com.android.purebilibili.build.ComposeDetachedOwnerGuardPlugin"
        }
        create("workManagerNamespaceGuard") {
            id = "com.android.bilipai.workmanager-namespace-guard"
            implementationClass = "com.android.purebilibili.build.WorkManagerNamespaceGuardPlugin"
        }
    }
}
