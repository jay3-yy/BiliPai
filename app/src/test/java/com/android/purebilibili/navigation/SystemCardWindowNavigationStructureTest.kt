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

    @Test
    fun homeTopActionPrefersInAppBottomPagerOverSystemCardWindowExceptAvatarCardTransition() {
        val body = appNavigationSource()
            .substringAfter("fun openHomeTopActionImpl(")
            .substringBefore("val latestOpenHomeTopAction")

        val gateToLaunch = body.substringBefore("navigateToSystemCardWindowOrPushImpl(")
        assertTrue(gateToLaunch.contains("control != HomeHeaderControl.AVATAR"))
        assertTrue(gateToLaunch.contains("shouldPreferInAppBottomPagerOverSystemCardWindow("))
        assertTrue(gateToLaunch.contains("openInApp()"))
    }

    @Test
    fun pushNavigation3TargetPrefersInAppBottomPagerOverSystemCardWindowWhenTabIsHosted() {
        val body = appNavigationSource()
            .substringAfter("fun pushNavigation3TargetImpl(")
            .substringBefore("val latestPushNavigation3Target")

        val gateToCardSource = body.substringBefore("latestNavigateWithRecordedCardSource")
        assertTrue(gateToCardSource.contains("shouldPreferInAppBottomPagerOverSystemCardWindow("))
    }

    @Test
    fun childWindowDisablesBottomPagerSwitchingAndHidesBottomBar() {
        val source = appNavigationSource()
        val requestPagerBody = source
            .substringAfter("fun requestBottomPagerPageForRoute(")
            .substringBefore("fun pushNavigation3TargetImpl(")
        assertTrue(requestPagerBody.contains("if (isChildWindow) return false"))

        val bottomBarMountGateBlock = source
            .substringAfter("val bottomBarMountGate = shouldShowBottomBarForNavigation(")
            .substringBefore("val sideBarRouteGate")
        val sideBarRouteGateBlock = source
            .substringAfter("val sideBarRouteGate = shouldShowBottomBarForNavigation(")
            .substringBefore("val sideBarMountGate")
        val showBottomBarBlock = source
            .substringAfter("val showBottomBar = shouldShowBottomBarForNavigation(")
            .substringBefore("val finalBottomBarVisible")

        assertTrue(bottomBarMountGateBlock.contains("isChildWindow = isChildWindow"))
        assertTrue(sideBarRouteGateBlock.contains("isChildWindow = isChildWindow"))
        assertTrue(showBottomBarBlock.contains("isChildWindow = isChildWindow"))
    }

    private fun appNavigationSource(): String {
        val roots = listOf(
            File("src/main/java/com/android/purebilibili"),
            File("app/src/main/java/com/android/purebilibili"),
        )
        return roots.map { File(it, "navigation/AppNavigation.kt") }.firstOrNull { it.exists() }?.readText()
            ?: error("找不到 navigation/AppNavigation.kt")
    }
}
