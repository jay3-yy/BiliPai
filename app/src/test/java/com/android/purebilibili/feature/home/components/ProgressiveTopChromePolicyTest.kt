package com.android.purebilibili.feature.home.components

import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressiveTopChromePolicyTest {
    @Test
    fun opaqueBackgroundProtectsChromeWithoutAnActiveBlur() {
        assertTrue(shouldUseOpaqueTopChromeBackground(false, false))
        assertFalse(shouldUseOpaqueTopChromeBackground(true, false))
        assertFalse(shouldUseOpaqueTopChromeBackground(false, true))
        assertFalse(shouldUseOpaqueTopChromeBackground(true, true))
        assertFalse(shouldUseOpaqueTopChromeBackground(false, false, liquidGlassActive = true))

        val source = loadSource("feature/home/components/ProgressiveTopChrome.kt")
        assertTrue(source.contains("MaterialTheme.colorScheme.background.copy(alpha = 1f)"))
        assertTrue(source.contains(".then(modifier)"))
    }

    @Test
    fun sharedProgressiveBlurUsesTheSoftTopEdgePreset() {
        assertEquals(10f, BILIPAI_PROGRESSIVE_TOP_BLUR_RADIUS_DP)
        assertEquals(0f, BILIPAI_PROGRESSIVE_TOP_BLUR_START_FRACTION)
        assertEquals(1.25f, BILIPAI_PROGRESSIVE_TOP_BLUR_FALLOFF_CURVE)
        assertEquals(0f, BILIPAI_PROGRESSIVE_TOP_BLUR_DEFAULT_GRADIENT.startFraction)
        assertEquals(1.25f, BILIPAI_PROGRESSIVE_TOP_BLUR_DEFAULT_GRADIENT.curve)
        val source = loadSource("feature/home/components/ProgressiveTopChrome.kt")
        assertTrue(source.contains("gradient = ProgressiveBlur.Top"))
        assertTrue(source.contains("bottomStart = 28.dp"))
        assertTrue(source.contains("bottomEnd = 28.dp"))
    }

    @Test
    fun progressiveBlurRequiresEnabledBackdropAndAndroid13() {
        assertTrue(shouldUseBiliPaiProgressiveTopBlur(true, true, sdkInt = 33))
        assertFalse(shouldUseBiliPaiProgressiveTopBlur(false, true, sdkInt = 33))
        assertFalse(shouldUseBiliPaiProgressiveTopBlur(true, false, sdkInt = 33))
        assertFalse(shouldUseBiliPaiProgressiveTopBlur(true, true, sdkInt = 32))
    }

    @Test
    fun progressiveBlurDoesNotNeedSamplingWithoutAValidRadius() {
        for (radius in listOf(0f, -1f, 0.001f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse(
                shouldUseBiliPaiProgressiveTopBlur(true, true, sdkInt = 33, blurRadiusDp = radius),
                "Invalid radius must not request a backdrop: $radius",
            )
        }
        assertTrue(shouldUseBiliPaiProgressiveTopBlur(true, true, sdkInt = 33, blurRadiusDp = 0.01f))
        assertTrue(shouldUseBiliPaiProgressiveTopBlur(true, true, sdkInt = 33, blurRadiusDp = 40f))
    }

    @Test
    fun progressiveBlurExtendsBelowTheTopDock() {
        assertEquals(20.dp, resolveProgressiveTopBlurBottomExtension(true, 0f))
        assertEquals(41.dp, resolveProgressiveTopBlurBottomExtension(true, 0.75f))
        assertEquals(48.dp, resolveProgressiveTopBlurBottomExtension(true, 1f))
        assertEquals(0.dp, resolveProgressiveTopBlurBottomExtension(false, 1f))
    }

    @Test
    fun homeDynamicAndCommonListReuseTheSharedProgressiveTopBlur() {
        val homeHeader = loadSource("feature/home/components/HomeHeader.kt")
        val dynamicTopBar = loadSource("feature/dynamic/components/DynamicTopBar.kt")
        val commonList = loadSource("feature/list/CommonListScreen.kt")

        assertTrue(homeHeader.contains("Modifier.biliPaiProgressiveTopBlur("))
        assertTrue(homeHeader.contains("useProgressiveTopBlur = isProgressiveBlurRequested"))
        assertTrue(dynamicTopBar.contains("BiliPaiImmersiveTopBar("))
        assertTrue(dynamicTopBar.contains("enabled = isProgressiveBlurActive"))
        assertTrue(dynamicTopBar.contains("fadeEnabled = isProgressiveFadeActive"))
        assertTrue(commonList.contains("BiliPaiImmersiveTopBar("))
        assertTrue(commonList.contains("enabled = isProgressiveTopBlurEnabled && !isHeaderBlurEnabled"))
        val bangumiHub = loadSource("feature/bangumi/BangumiScreen.kt")
        val bangumiDetail = loadSource("feature/bangumi/BangumiDetailScreen.kt")
        val bangumiReview = loadSource("feature/bangumi/BangumiReviewScreen.kt")
        assertTrue(bangumiHub.contains("ImmersiveAppScaffold as AppScaffold"))
        assertTrue(bangumiHub.contains("listTopPadding = listTopPadding"))
        assertTrue(bangumiHub.contains("onGloballyPositioned"))
        assertTrue(bangumiDetail.contains("ImmersiveAppScaffold as AppScaffold"))
        assertTrue(bangumiReview.contains("ImmersiveAppScaffold as AppScaffold"))
        val profile = loadSource("feature/profile/ProfileScreen.kt")
        val favoriteCategory = loadSource("feature/list/FavoriteCategoryScreen.kt")
        assertTrue(profile.contains("BiliPaiImmersiveTopBar("))
        assertTrue(profile.contains("rememberProfileProgressiveTopChrome()"))
        assertTrue(commonList.contains("captureScrollableContent = progressiveHeaderRequested"))
        assertTrue(favoriteCategory.contains("topPadding = stickyChromeReserve"))
        val space = loadSource("feature/space/SpaceScreen.kt")
        val settingsTablet = loadSource("feature/settings/screen/SettingsTabletShell.kt")
        assertTrue(space.contains("BiliPaiImmersiveTopBar("))
        assertTrue(space.contains("spaceChromeSource?.modifier"))
        assertTrue(space.contains("globalWallpaperAwareBackground(MaterialTheme.colorScheme.surface)"))
        assertTrue(space.contains("top = chromeTopInset"))
        assertFalse(space.contains("onPinnedChromeHeightChanged"))
        assertFalse(space.contains("val tabPinned = gridState.firstVisibleItemIndex > 0"))
        assertTrue(profile.contains("captureBackground()"))
        assertTrue(profile.contains("profileProgressiveBackdrop(progressiveTopChrome)"))
        assertTrue(profile.contains("globalWallpaperAwareBackground(colorScheme.surface)"))
        assertTrue(bangumiHub.contains("BiliPaiImmersiveTopBar("))
        assertTrue(bangumiHub.contains(".then(chromeSource?.modifier ?: Modifier)"))
        assertTrue(bangumiHub.contains("globalWallpaperAwareBackground(MaterialTheme.colorScheme.background)"))
        assertTrue(bangumiHub.contains("showFollowStatusTabs = false"))
        assertTrue(settingsTablet.contains("BiliPaiImmersiveTopBar("))
    }

    @Test
    fun independentVideoActivityProvidesGlobalFadeAndCardEffectSettings() {
        val videoActivity = loadSource("feature/video/VideoActivity.kt")

        assertTrue(videoActivity.contains("getProgressiveTopBlurEnabled(this@VideoActivity)"))
        assertTrue(videoActivity.contains("getProgressiveTopFadeEnabled(this@VideoActivity)"))
        assertTrue(videoActivity.contains("progressiveTopFadeEnabled = progressiveTopFadeEnabled"))
        assertTrue(videoActivity.contains("getHomeCardDynamicTintEnabled(this@VideoActivity)"))
        assertTrue(videoActivity.contains("LocalHomeCardDynamicTintEnabled provides"))
    }

    @Test
    fun progressiveBlurContainersIncludeTheStatusBarBand() {
        val homeHeader = loadSource("feature/home/components/HomeHeader.kt")
        val dynamicTopBar = loadSource("feature/dynamic/components/DynamicTopBar.kt")
        val commonList = loadSource("feature/list/CommonListScreen.kt")

        assertTrue(homeHeader.contains("floatingTabBackdropOverlap + progressiveBlurBottomExtension"))
        assertTrue(homeHeader.contains("topTabInnerOwnsFloatingDockShell && !isHeaderBlurEnabled"))
        assertTrue(homeHeader.contains("floatingDockBlurEnabled = isHeaderBlurEnabled"))
        assertTrue(homeHeader.contains("floatingDockContainerVisible = !isHeaderBlurEnabled"))
        assertTrue(dynamicTopBar.contains("Spacer(modifier = Modifier.height(statusBarHeight))"))
        assertTrue(commonList.contains(".then(topBarBackgroundModifier)"))
    }

    @Test
    fun includedTabRowDoesNotLeaveProgressiveBlurExtensionBelowDock() {
        assertFalse(
            shouldExtendProgressiveTopBlurBelowTabs(
                progressiveBlurEnabled = true,
                tabRowIncludedInBlur = true,
            )
        )
        assertTrue(
            shouldExtendProgressiveTopBlurBelowTabs(
                progressiveBlurEnabled = true,
                tabRowIncludedInBlur = false,
            )
        )
    }

    @Test
    fun immersiveLayerExtendsItsDrawingWithoutIncreasingHeaderLayoutHeight() {
        val source = loadSource("feature/home/components/ProgressiveTopChrome.kt")
        assertTrue(source.contains(".matchParentSize()"))
        assertTrue(source.contains("minHeight = constraints.minHeight + extension"))
        assertTrue(source.contains("LocalImmersiveTopChromeActive provides"))
        assertTrue(source.contains("(active || headerBlurActive)"))
    }

    @Test
    fun searchAndWatchLaterCaptureScrollableContentInsteadOfAnEmptyHeader() {
        val search = loadSource("feature/search/SearchScreen.kt")
        val watchLater = loadSource("feature/watchlater/WatchLaterScreen.kt")
        assertTrue(search.contains("if (!state.showResults)"))
        assertTrue(search.contains("searchTopChromeGlass(inputShape, chromeSpec.inputHeightDp)"))
        assertTrue(search.contains("if (immersiveSearchChrome) Color.Transparent else searchTopBarHeaderColor"))
        assertTrue(watchLater.contains("watchLaterChromeSource?.modifier"))
        assertTrue(watchLater.contains("globalWallpaperAwareBackground(AppSurfaceTokens.groupedListContainer())"))
        assertFalse(watchLater.contains(".matchParentSize()"))
        val immersiveScaffold = loadSource("core/ui/ImmersiveAppScaffold.kt")
        assertTrue(immersiveScaffold.contains(".globalWallpaperAwareBackground(containerColor)"))
        val topicDetail = loadSource("feature/search/TopicDetailScreen.kt")
        val topicBackdropIndex = topicDetail.indexOf("Modifier.layerBackdrop(topicBackdrop)")
        val topicFillIndex = topicDetail.indexOf(".globalWallpaperAwareBackground()")
        assertTrue(topicBackdropIndex >= 0)
        assertTrue(topicFillIndex > topicBackdropIndex)
    }

    private fun loadSource(relativePath: String): String {
        return listOf(
            File("app/src/main/java/com/android/purebilibili/$relativePath"),
            File("src/main/java/com/android/purebilibili/$relativePath"),
        ).first { it.exists() }.readText()
    }
}
