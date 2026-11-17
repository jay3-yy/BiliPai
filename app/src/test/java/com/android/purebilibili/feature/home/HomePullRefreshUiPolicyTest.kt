package com.android.purebilibili.feature.home

import com.android.purebilibili.core.ui.AppPullRefreshIndicatorStyle
import com.android.purebilibili.core.ui.AppPullRefreshMotionStyle
import com.android.purebilibili.core.ui.PresetPrimitiveRenderer
import com.android.purebilibili.core.ui.resolveAppPullRefreshProfile
import com.android.purebilibili.core.ui.resolvePullRefreshThresholdDp
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePullRefreshUiPolicyTest {

    @Test
    fun `material md3 preset uses native refresh motion style`() {
        assertEquals(
            AppPullRefreshMotionStyle.PLATFORM,
            resolveAppPullRefreshProfile(PresetPrimitiveRenderer.MATERIAL3).motionStyle
        )
    }

    @Test
    fun `miuix variant keeps material pull motion for previous md3 behavior`() {
        assertEquals(
            AppPullRefreshMotionStyle.PLATFORM,
            resolveAppPullRefreshProfile(PresetPrimitiveRenderer.MIUIX_BRIDGED).motionStyle
        )
    }

    @Test
    fun `pull refresh indicator style routes native material and miuix separately`() {
        assertEquals(
            AppPullRefreshIndicatorStyle.MATERIAL_DEFAULT,
            resolveAppPullRefreshProfile(PresetPrimitiveRenderer.MATERIAL3).indicatorStyle
        )
        assertEquals(
            AppPullRefreshIndicatorStyle.MIUIX_NATIVE,
            resolveAppPullRefreshProfile(PresetPrimitiveRenderer.MIUIX_BRIDGED).indicatorStyle
        )
    }

    @Test
    fun `home screen routes miuix native refresh through adaptive container`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")

        assertTrue(source.contains("AdaptivePullToRefreshBox("))
        assertTrue(source.contains("AppPullRefreshIndicatorStyle.MIUIX_NATIVE -> Unit"))
        // Overlay chrome height via indicatorTopInset (not scaffold default 0).
        assertTrue(source.contains("indicatorTopInset = homeRefreshIndicatorTopInset"))
        assertTrue(source.contains("homeRefreshIndicatorTopInset = listTopPadding"))
    }

    @Test
    fun `resolvePullRefreshThresholdDp returns comfortable trigger distance`() {
        assertEquals(44f, resolvePullRefreshThresholdDp(), 0.001f)
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        val sourceFile = listOf(
            File(path),
            File(normalizedPath)
        ).firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText()
    }

    @Test
    fun `comfortable pull refresh threshold reduces required finger travel from material default`() {
        val requiredFingerTravelDp = resolveRequiredPullDistanceDp(
            thresholdDp = resolvePullRefreshThresholdDp(),
            dragMultiplier = 0.5f
        )

        assertEquals(88f, requiredFingerTravelDp, 0.001f)
        assertTrue(requiredFingerTravelDp < 160f)
    }

    @Test
    fun `shouldResetToTopOnRefreshStart returns false when already at top`() {
        assertFalse(shouldResetToTopOnRefreshStart(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0))
    }

    @Test
    fun `shouldResetToTopOnRefreshStart returns true when list is scrolled`() {
        assertTrue(shouldResetToTopOnRefreshStart(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0))
        assertTrue(shouldResetToTopOnRefreshStart(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 12))
    }

    @Test
    fun `shouldResetToTopAfterIncrementalRefresh returns false for non-recommend category`() {
        assertFalse(
            shouldResetToTopAfterIncrementalRefresh(
                currentCategory = HomeCategory.POPULAR,
                newItemsCount = 3,
                isRefreshing = false,
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 0
            )
        )
    }

    @Test
    fun `shouldResetToTopAfterIncrementalRefresh returns true for follow even at top`() {
        assertTrue(
            shouldResetToTopAfterIncrementalRefresh(
                currentCategory = HomeCategory.FOLLOW,
                newItemsCount = 2,
                isRefreshing = false,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0
            )
        )
    }

    @Test
    fun `shouldResetToTopAfterIncrementalRefresh returns false while refreshing`() {
        assertFalse(
            shouldResetToTopAfterIncrementalRefresh(
                currentCategory = HomeCategory.RECOMMEND,
                newItemsCount = 3,
                isRefreshing = true,
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 0
            )
        )
    }

    @Test
    fun `shouldResetToTopAfterIncrementalRefresh returns false when no new items`() {
        assertFalse(
            shouldResetToTopAfterIncrementalRefresh(
                currentCategory = HomeCategory.RECOMMEND,
                newItemsCount = 0,
                isRefreshing = false,
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 0
            )
        )
    }

    @Test
    fun `shouldResetToTopAfterIncrementalRefresh returns false when already at top`() {
        assertFalse(
            shouldResetToTopAfterIncrementalRefresh(
                currentCategory = HomeCategory.RECOMMEND,
                newItemsCount = 3,
                isRefreshing = false,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0
            )
        )
    }

    @Test
    fun `shouldResetToTopAfterIncrementalRefresh returns true when recommend has new items and list is scrolled`() {
        assertTrue(
            shouldResetToTopAfterIncrementalRefresh(
                currentCategory = HomeCategory.RECOMMEND,
                newItemsCount = 3,
                isRefreshing = false,
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 0
            )
        )
    }

    @Test
    fun `resolvePullRefreshHintText shows pull text while indicator animates back`() {
        assertEquals(
            "下拉刷新...",
            resolvePullRefreshHintText(
                progress = 1.15f,
                isRefreshing = false,
                isStateAnimating = true
            )
        )
    }

    @Test
    fun `resolvePullRefreshHintText shows release text only when actively over threshold`() {
        assertEquals(
            "松手刷新",
            resolvePullRefreshHintText(
                progress = 1.15f,
                isRefreshing = false,
                isStateAnimating = false
            )
        )
    }

    @Test
    fun `resolvePullIndicatorTranslationY keeps minimum gap from cards`() {
        val translationY = resolvePullIndicatorTranslationY(
            dragOffsetPx = 40f,
            indicatorHeightPx = 40f,
            minGapPx = 8f,
            isRefreshing = false
        )
        assertEquals(-8f, translationY, 0.001f)
    }

    @Test
    fun `resolvePullIndicatorTranslationY pins indicator when refreshing`() {
        val translationY = resolvePullIndicatorTranslationY(
            dragOffsetPx = 0f,
            indicatorHeightPx = 40f,
            minGapPx = 8f,
            isRefreshing = true
        )
        assertEquals(0f, translationY, 0.001f)
    }

    @Test
    fun `resolvePullContentOffsetFraction clears extra gap once refreshing is active`() {
        assertEquals(
            0f,
            resolvePullContentOffsetFraction(
                distanceFraction = 0f,
                isRefreshing = true,
                motionStyle = AppPullRefreshMotionStyle.CUPERTINO
            ),
            0.001f
        )
    }

    @Test
    fun `resolvePullContentOffsetFraction returns zero when idle and no pull`() {
        assertEquals(
            0f,
            resolvePullContentOffsetFraction(
                distanceFraction = 0f,
                isRefreshing = false,
                motionStyle = AppPullRefreshMotionStyle.CUPERTINO
            ),
            0.001f
        )
    }

    @Test
    fun `resolvePullContentOffsetFraction lets md3 content follow finger during pull`() {
        assertEquals(
            0.6f,
            resolvePullContentOffsetFraction(
                distanceFraction = 1.2f,
                isRefreshing = false,
                motionStyle = AppPullRefreshMotionStyle.PLATFORM
            ),
            0.001f
        )
    }

    @Test
    fun `md3 screenshot pull indicator uses ios content motion curve`() {
        val lightPull = resolvePullContentOffsetFraction(
            distanceFraction = 0.4f,
            isRefreshing = false,
            motionStyle = AppPullRefreshMotionStyle.PLATFORM,
            indicatorStyle = AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE
        )
        val heavyPull = resolvePullContentOffsetFraction(
            distanceFraction = 1.2f,
            isRefreshing = false,
            motionStyle = AppPullRefreshMotionStyle.PLATFORM,
            indicatorStyle = AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE
        )

        assertTrue(lightPull > 0f)
        assertTrue(heavyPull > lightPull)
        assertEquals(0.351f, lightPull, 0.001f)
        assertEquals(0.805f, heavyPull, 0.001f)
        assertTrue(heavyPull < 1.12f)
    }

    @Test
    fun `md3 screenshot pull offset applies progressive drag resistance`() {
        assertEquals(0f, resolveMd3ScreenshotPullOffsetFraction(0f), 0.001f)
        assertEquals(0.741f, resolveMd3ScreenshotPullOffsetFraction(1f), 0.001f)
        assertTrue(
            resolveMd3ScreenshotPullOffsetFraction(0.5f) <
                resolveMd3ScreenshotPullOffsetFraction(1f)
        )
    }

    @Test
    fun `md3 screenshot pull offset reserves room for indicator at refresh threshold`() {
        val indicatorHeight = resolveMd3ScreenshotRefreshIndicatorHeightDp(
            progress = 1f,
            isRefreshing = false
        )
        val indicatorTotalHeight = resolveMd3ScreenshotRefreshIndicatorTotalHeightDp(
            indicatorHeightDp = indicatorHeight,
            hasHintText = true
        )
        val contentOffset = resolvePullContentMaxOffsetDp(AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE) *
            resolvePullContentOffsetFraction(
                distanceFraction = 1f,
                isRefreshing = false,
                motionStyle = AppPullRefreshMotionStyle.PLATFORM,
                indicatorStyle = AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE
            )

        assertTrue(contentOffset >= indicatorTotalHeight + 8f)
    }

    @Test
    fun `miuix material indicator uses ios content motion curve`() {
        assertEquals(
            0.3f,
            resolvePullContentOffsetFraction(
                distanceFraction = 0.6f,
                isRefreshing = false,
                motionStyle = AppPullRefreshMotionStyle.PLATFORM,
                indicatorStyle = AppPullRefreshIndicatorStyle.MATERIAL_DEFAULT
            ),
            0.001f
        )
        assertEquals(
            0.6f,
            resolvePullContentOffsetFraction(
                distanceFraction = 1.2f,
                isRefreshing = false,
                motionStyle = AppPullRefreshMotionStyle.PLATFORM,
                indicatorStyle = AppPullRefreshIndicatorStyle.MATERIAL_DEFAULT
            ),
            0.001f
        )
    }

    @Test
    fun `md3 screenshot pull content max offset reserves indicator distance`() {
        assertEquals(
            172f,
            resolvePullContentMaxOffsetDp(AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE),
            0.001f
        )
    }

    @Test
    fun `md3 screenshot pull indicator releases cards back to neutral when refreshing`() {
        assertEquals(
            0f,
            resolveStablePullContentOffsetFraction(
                distanceFraction = 1.15f,
                isRefreshing = true,
                isStateAnimating = false,
                motionStyle = AppPullRefreshMotionStyle.PLATFORM,
                indicatorStyle = AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE
            ),
            0.001f
        )
    }

    @Test
    fun `stable md3 screenshot pull offset follows finger back toward top`() {
        assertEquals(
            0.426f,
            resolveStablePullContentOffsetFraction(
                distanceFraction = 0.5f,
                isRefreshing = false,
                isStateAnimating = false,
                motionStyle = AppPullRefreshMotionStyle.PLATFORM,
                indicatorStyle = AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE
            ),
            0.001f
        )
    }

    @Test
    fun `md3 screenshot pull indicator stretches with pull distance`() {
        val initialHeight = resolveMd3ScreenshotRefreshIndicatorHeightDp(
            progress = 0f,
            isRefreshing = false
        )
        val releaseHeight = resolveMd3ScreenshotRefreshIndicatorHeightDp(
            progress = 1f,
            isRefreshing = false
        )

        assertEquals(44f, initialHeight, 0.001f)
        assertTrue(releaseHeight > initialHeight)
        assertEquals(70f, releaseHeight, 0.001f)
        assertEquals(42f, resolveMd3ScreenshotRefreshIndicatorHeightDp(progress = 1f, isRefreshing = true), 0.001f)
    }

    @Test
    fun `md3 screenshot pull indicator total height includes hint row`() {
        assertEquals(
            132f,
            resolveMd3ScreenshotRefreshIndicatorTotalHeightDp(
                indicatorHeightDp = 86f,
                hasHintText = true
            ),
            0.001f
        )
        assertEquals(
            102f,
            resolveMd3ScreenshotRefreshIndicatorTotalHeightDp(
                indicatorHeightDp = 86f,
                hasHintText = false
            ),
            0.001f
        )
    }

    @Test
    fun `md3 screenshot pull indicator stays centered between tabs and pushed cards`() {
        assertEquals(
            10f,
            resolveMd3ScreenshotRefreshIndicatorTranslationY(
                dragOffsetPx = 140f,
                indicatorTotalHeightPx = 120f,
                minGapPx = 8f
            ),
            0.001f
        )
        assertEquals(
            0f,
            resolveMd3ScreenshotRefreshIndicatorTranslationY(
                dragOffsetPx = 80f,
                indicatorTotalHeightPx = 120f,
                minGapPx = 8f
            ),
            0.001f
        )
    }

    @Test
    fun `stable pull content offset follows finger upward`() {
        assertEquals(
            0.3f,
            resolveStablePullContentOffsetFraction(
                distanceFraction = 0.6f,
                isRefreshing = false,
                isStateAnimating = false,
                motionStyle = AppPullRefreshMotionStyle.CUPERTINO
            ),
            0.001f
        )
    }

    @Test
    fun `stable pull content offset grows with finger while pulling down`() {
        assertEquals(
            0.6f,
            resolveStablePullContentOffsetFraction(
                distanceFraction = 1.2f,
                isRefreshing = false,
                isStateAnimating = false,
                motionStyle = AppPullRefreshMotionStyle.CUPERTINO
            ),
            0.001f
        )
    }

    @Test
    fun `stable pull content offset resets after pull returns to idle`() {
        assertEquals(
            0f,
            resolveStablePullContentOffsetFraction(
                distanceFraction = 0f,
                isRefreshing = false,
                isStateAnimating = false,
                motionStyle = AppPullRefreshMotionStyle.CUPERTINO
            ),
            0.001f
        )
    }

    @Test
    fun `stable pull content offset clears while refresh is active`() {
        assertEquals(
            0f,
            resolveStablePullContentOffsetFraction(
                distanceFraction = 1.2f,
                isRefreshing = true,
                isStateAnimating = false,
                motionStyle = AppPullRefreshMotionStyle.CUPERTINO
            ),
            0.001f
        )
    }

    @Test
    fun `pull offset snaps to finger only during active drag`() {
        assertTrue(
            shouldSnapPullOffsetToFinger(
                distanceFraction = 0.6f,
                isRefreshing = false,
                isStateAnimating = false
            )
        )
        assertFalse(
            shouldSnapPullOffsetToFinger(
                distanceFraction = 0.6f,
                isRefreshing = false,
                isStateAnimating = true
            )
        )
        assertFalse(
            shouldSnapPullOffsetToFinger(
                distanceFraction = 0.6f,
                isRefreshing = true,
                isStateAnimating = false
            )
        )
    }

    @Test
    fun `md3 screenshot pull offset stays finger driven while material state animates`() {
        assertTrue(
            shouldSnapPullOffsetToFinger(
                distanceFraction = 0.6f,
                isRefreshing = false,
                isStateAnimating = true,
                indicatorStyle = AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE
            )
        )
    }

    @Test
    fun `stable pull content offset lets md3 content follow finger`() {
        assertEquals(
            0.6f,
            resolveStablePullContentOffsetFraction(
                distanceFraction = 1.2f,
                isRefreshing = false,
                isStateAnimating = false,
                motionStyle = AppPullRefreshMotionStyle.PLATFORM
            ),
            0.001f
        )
    }
}
