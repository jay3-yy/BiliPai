package com.android.purebilibili.feature.bangumi

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BangumiLiquidGlassStructureTest {

    @Test
    fun `bangumi hub uses adaptive native controls and independent poster ratio`() {
        val screenSource = sourceOf("BangumiScreen.kt")
        val contentSource = sourceOf("BangumiHubContent.kt")
        val detailSource = sourceOf("BangumiDetailScreen.kt")
        val tabRowSource = File(
            "src/main/java/com/android/purebilibili/core/ui/components/AppLiquidAwareTabRow.kt",
        ).readText()
        val floatingDockSource = File(
            "src/main/java/com/android/purebilibili/feature/home/components/FloatingBottomBar.kt",
        ).readText()
        val homeTabSource = sourceOf("HomeBangumiTabPage.kt")

        val reviewSource = sourceOf("BangumiReviewScreen.kt")
        assertTrue(screenSource.contains("BiliPaiImmersiveTopBar("))
        assertTrue(detailSource.contains("ImmersiveAppScaffold as AppScaffold"))
        assertTrue(reviewSource.contains("ImmersiveAppScaffold as AppScaffold"))
        assertTrue(screenSource.contains("AppLiquidAwareTabRow("))
        assertTrue(contentSource.contains("AppLiquidAwareTabRow("))
        assertTrue(contentSource.contains("AdaptivePullToRefreshBox("))
        assertTrue(contentSource.contains("AnimatedContent("))
        assertTrue(contentSource.contains("slideInHorizontally("))
        assertTrue(contentSource.contains("slideOutHorizontally("))
        assertTrue(contentSource.contains("BANGUMI_POSTER_ASPECT_RATIO = 0.75f"))
        assertTrue(contentSource.contains("追番时间表"))
        assertTrue(contentSource.contains("minTabWidth = timelineDayMinWidth"))
        assertTrue(contentSource.contains("112.dp"))
        assertTrue(contentSource.contains("modifier = Modifier.fillMaxWidth()"))
        assertTrue(detailSource.contains("AppStatusBadge("))
        assertTrue(detailSource.contains("emphasized = true"))
        assertTrue(!detailSource.contains("onClick = {},"))
        assertTrue(contentSource.contains("AppThemeAdaptiveTabRow("))
        assertTrue(contentSource.contains("miuixBackdrop = tabBackdrop"))
        assertTrue(screenSource.contains("miuixBackdrop = chromeBackdrop"))
        assertTrue(screenSource.contains(".then(chromeSource?.modifier ?: Modifier)"))
        assertTrue(screenSource.contains("(progressiveBlur || shouldCaptureBangumiHubChrome(state)) && it.isReady"))
        assertTrue(screenSource.contains("shouldCaptureBangumiHubChrome(state)"))
        assertTrue(!screenSource.contains("tabBackdrop = chromeBackdrop"))
        assertTrue(homeTabSource.contains("miuixBackdrop = channelBackdrop"))
        assertTrue(!homeTabSource.contains("tabBackdrop = channelBackdrop"))
        assertTrue(contentSource.contains("modifier = Modifier.width(56.dp)"))
        assertTrue(contentSource.contains("modifier = Modifier.weight(1f)"))
        assertTrue(contentSource.contains("showPgcTimeline"))
        assertTrue(!screenSource.contains("AppNativeTabRow("))
        assertTrue(contentSource.contains("AppLiquidAwareTabRow("))
        assertTrue(tabRowSource.contains("BottomBarLiquidSegmentedControl("))
        assertTrue(tabRowSource.contains("AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp"))
        assertTrue(tabRowSource.contains("AppNativeTabRow("))
        assertTrue(tabRowSource.contains("Modifier.horizontalScroll(scrollState)"))
        assertTrue(tabRowSource.contains("itemWidth = minTabWidth"))
        assertTrue(tabRowSource.contains("dragSelectionEnabled = resolvedDragSelectionEnabled"))
        assertTrue(floatingDockSource.contains("resolveSharedBottomBarCapsuleShape()"))
        assertTrue(!floatingDockSource.contains("remember { CircleShape }"))
        assertTrue(contentSource.contains("miuixBackdrop = tabBackdrop"))
        assertTrue(!contentSource.contains("followBackdrop"))
        assertTrue(!tabRowSource.contains("forceLiquidChrome = true"))
        assertTrue(!contentSource.contains("HomeFeedCardStyle"))
        assertTrue(!contentSource.contains("SettingsManager"))
        assertTrue(!screenSource.contains("TopAppBarScrollBehavior"))
        assertTrue(homeTabSource.contains("AppLiquidAwareTabRow("))
        assertTrue(!homeTabSource.contains("categoryTabsVisible"))
    }

    @Test
    fun `progressive source stays mounted while skeleton loading changes`() {
        val source = sourceOf("BangumiScreen.kt")
        assertTrue(source.contains("if (progressiveBlur || themeConfig.liquidGlassEnabled)"))
        assertTrue(source.contains("(progressiveBlur || shouldCaptureBangumiHubChrome(state)) && it.isReady"))
    }

    @Test
    fun `follow grid scrolls behind both fixed chrome rows`() {
        val screen = sourceOf("BangumiScreen.kt")
        val content = sourceOf("BangumiHubContent.kt")
        assertTrue(screen.contains("showFollowStatusTabs = false"))
        assertTrue(screen.contains("listTopPadding = contentPadding.calculateTopPadding()"))
        assertTrue(screen.indexOf("selectedValue = state.followStatus") < screen.indexOf("BangumiHubContent("))
        assertTrue(content.contains("if (showStatusTabs) Modifier.padding(top = listTopPadding) else Modifier"))
        assertTrue(content.contains("top = if (showStatusTabs) 4.dp else listTopPadding + 4.dp"))
        assertTrue(content.contains("indicatorTopInset = if (showStatusTabs) 0.dp else listTopPadding"))
    }

    private fun sourceOf(path: String): String =
        File("src/main/java/com/android/purebilibili/feature/bangumi/$path").readText()
}
