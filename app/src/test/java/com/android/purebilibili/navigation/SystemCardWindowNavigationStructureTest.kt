package com.android.purebilibili.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** 导航卡片窗口承接约束：窗口内不再堆叠同类窗口，底栏选中项绑定窗口内容身份。 */
class SystemCardWindowNavigationStructureTest {

    @Test
    fun navigationCardWindowHostCommitsInAppBeforeCapturingSystemSession() {
        val body = appNavigationSource()
            .substringAfter("fun navigateToSystemCardWindowOrPushImpl(")
            .substringBefore("val latestOpenSystemCard")

        val gateToCapture = body
            .substringAfter("com.android.purebilibili.navigation.NavigationCardActivity")
            .substringBefore("captureVideoCardNavigationSession(")
        assertTrue(gateToCapture.contains("!resolvedKey.isPlaybackWindowKey()"))
        assertTrue(gateToCapture.contains("return commitInApp()"))
    }

    @Test
    fun bottomPagerInitialPageConsultsWindowEntryIdentity() {
        val source = appNavigationSource()

        assertTrue(source.contains("initialPage = resolveBottomBarInitialPage("))
        assertTrue(source.contains("windowInitialRoute = windowInitialKey?.toLegacyRoute(),"))
    }

    private fun appNavigationSource(): File {
        val roots = listOf(
            File("src/main/java/com/android/purebilibili"),
            File("app/src/main/java/com/android/purebilibili"),
        )
        return roots.map { File(it, "navigation/AppNavigation.kt") }.firstOrNull { it.exists() }
            ?: error("找不到 navigation/AppNavigation.kt")
    }
}
