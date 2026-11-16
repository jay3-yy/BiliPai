package com.android.purebilibili.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeMotionAndFeedPerformanceStructureTest {
    @Test
    fun bottomTabSwitch_usesSharedPagerSelection() {
        val source = sourceFile("navigation/MainBottomPagerState.kt")
        val sharedMotionSource = sourceFile("navigation/PagerSelectionMotion.kt")

        assertTrue(source.contains("animatePagerSelection(pagerState, safeTargetIndex)"))
        assertTrue(sharedMotionSource.contains("PagerState.tabIndicatorPosition"))
        assertFalse(sharedMotionSource.contains("scrollBy(value - consumedPx)"))
        assertTrue(sharedMotionSource.contains("easing = EaseInOut"))
        assertTrue(sharedMotionSource.contains("pagerState.animateScrollToPage(target, animationSpec = spec)"))
        assertFalse(sharedMotionSource.contains("pagerState.animateScrollBy("))
        assertTrue(!sharedMotionSource.contains("dispatchRawDelta"))
    }

    @Test
    fun homeFeed_compactStatsFollowTheAppearanceSettingWithoutInfoBadgeHaze() {
        val source = sourceFile("feature/home/HomeScreen.kt")

        assertTrue(source.contains("compactStatsOnCover = homeSettings.compactVideoStatsOnCover"))
        assertTrue(source.contains("showInfoGlassBadges = false"))
    }

    @Test
    fun homeFeed_mountsVideoSharedBoundsWhenTransitionIsEnabled() {
        val source = sourceFile("feature/home/HomeScreen.kt")

        assertTrue(
            source.contains(
                "val cardTransitionEnabled = homePerformanceConfig.cardTransitionEnabled && !systemReduceMotion",
            ),
        )
    }

    @Test
    fun homeFeed_doesNotMountDissolveLayoutTrackingUntilRequested() {
        val source = sourceFile("feature/home/HomeCategoryPage.kt")

        assertTrue(source.contains("MaybeDissolvableVideoCard("))
        assertTrue(source.contains("preserveContentLayerWhenIdle = cardTransitionEnabled"))
        assertFalse(Regex("(?m)^\\s*DissolvableVideoCard\\(").containsMatchIn(source))
    }

    @Test
    fun videoMotionSwitch_doesNotChangeGlobalNavigationTiming() {
        val source = sourceFile("navigation/AppNavigation.kt")

        assertTrue(source.contains("remember(isTabletLayout, cardTransitionEnabled)"))
        assertTrue(source.contains("cardTransitionEnabled = cardTransitionEnabled"))
        assertTrue(source.contains("val shouldApplyBackground = cardTransitionEnabled &&"))
    }

    private fun sourceFile(relativePath: String): String {
        return listOf(
            File("app/src/main/java/com/android/purebilibili/$relativePath"),
            File("src/main/java/com/android/purebilibili/$relativePath"),
        ).first { it.exists() }.readText()
    }
}
