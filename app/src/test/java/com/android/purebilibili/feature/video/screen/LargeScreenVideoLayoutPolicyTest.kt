package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.store.TabletSecondaryDefaultTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LargeScreenVideoLayoutPolicyTest {

    @Test
    fun landscapeLayoutHidesIntroRelatedAndHonorsConfiguredDefaultTab() {
        assertFalse(resolveShowRelatedInIntro(LargeScreenVideoLayoutMode.Landscape))
        assertTrue(resolveShowRelatedInIntro(LargeScreenVideoLayoutMode.AlmostSquare))
        assertTrue(resolveIncludeRelatedTabInSecondary(LargeScreenVideoLayoutMode.Landscape))
        assertFalse(resolveIncludeRelatedTabInSecondary(LargeScreenVideoLayoutMode.AlmostSquare))
        assertTrue(
            resolveRelatedTabFirstInSecondary(
                LargeScreenVideoLayoutMode.Landscape,
                TabletSecondaryDefaultTab.RELATED,
            ),
        )
        assertFalse(
            resolveRelatedTabFirstInSecondary(
                LargeScreenVideoLayoutMode.Landscape,
                TabletSecondaryDefaultTab.COMMENTS,
            ),
        )
        assertFalse(
            resolveRelatedTabFirstInSecondary(
                LargeScreenVideoLayoutMode.AlmostSquare,
                TabletSecondaryDefaultTab.RELATED,
            ),
        )
        val source = java.io.File(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/LargeScreenVideoLayout.kt"
        ).takeIf { it.exists() } ?: java.io.File(
            "src/main/java/com/android/purebilibili/feature/video/screen/LargeScreenVideoLayout.kt"
        )
        val text = source.readText()
        assertTrue(text.contains("includeRelatedTab = resolveIncludeRelatedTabInSecondary(metrics.mode)"))
        assertTrue(text.contains("defaultTab = secondaryDefaultTab"))
        assertTrue(text.contains("includeOwnerUploadsTab = true"))
        assertTrue(text.contains("showRelatedVideos = showRelatedInIntro"))
        assertTrue(text.contains("LargeScreenVideoLayoutMode.AlmostSquare"))
        assertFalse(text.contains("fixedTab = TabletSecondaryTab.COLLECTION"))
    }

    @Test
    fun collectionGetsOwnColumnOnlyWhenEachPaneIsAtLeast280dp() {
        assertFalse(
            shouldUseDedicatedCollectionColumn(
                availableWidthDp = 800f,
                hasCollection = true,
            )
        )
        assertTrue(
            shouldUseDedicatedCollectionColumn(
                availableWidthDp = 960f,
                hasCollection = true,
            )
        )
        assertFalse(
            shouldUseDedicatedCollectionColumn(
                availableWidthDp = 1280f,
                hasCollection = false,
            )
        )
    }

    @Test
    fun landscapeTabletUsesLeftPlayerAndClampedSidePane() {
        val metrics = resolveLargeScreenVideoMetrics(
            windowWidthDp = 1280f,
            windowHeightDp = 800f,
            isVerticalVideo = false,
        )
        assertEquals(LargeScreenVideoLayoutMode.Landscape, metrics.mode)
        assertTrue(metrics.introBelowPlayer)
        assertTrue(metrics.sidePaneWidthDp in 280f..425f)
        assertEquals(1280f - metrics.sidePaneWidthDp, metrics.playerWidthDp, 0.5f)
        assertEquals(metrics.playerWidthDp / (16f / 9f), metrics.playerHeightDp, 1f)
    }

    @Test
    fun verticalVideoInLandscapeUsesThreePanes() {
        val metrics = resolveLargeScreenVideoMetrics(
            windowWidthDp = 1280f,
            windowHeightDp = 800f,
            isVerticalVideo = true,
            enableVerticalExpand = true,
        )
        assertEquals(LargeScreenVideoLayoutMode.VerticalThreePane, metrics.mode)
        assertFalse(metrics.introBelowPlayer)
        assertEquals(800f / (16f / 9f), metrics.playerWidthDp, 1f)
        assertEquals((1280f - metrics.playerWidthDp) / 2f, metrics.sidePaneWidthDp, 1f)
    }

    @Test
    fun compactPortraitStaysOnPhoneLayout() {
        assertFalse(
            shouldUseLargeScreenVideoLayout(
                windowWidthDp = 393f,
                windowHeightDp = 851f,
                horizontalAdaptationEnabled = true,
            )
        )
        val metrics = resolveLargeScreenVideoMetrics(
            windowWidthDp = 393f,
            windowHeightDp = 851f,
            isVerticalVideo = false,
        )
        assertEquals(LargeScreenVideoLayoutMode.Phone, metrics.mode)
    }

    @Test
    fun disabledHorizontalAdaptationNeverEntersLargeScreenLayout() {
        assertFalse(
            shouldUseLargeScreenVideoLayout(
                windowWidthDp = 1280f,
                windowHeightDp = 800f,
                horizontalAdaptationEnabled = false,
            )
        )
    }

    @Test
    fun verticalVideoDefaultsToLandscapeWithoutExpand() {
        val metrics = resolveLargeScreenVideoMetrics(
            windowWidthDp = 1280f,
            windowHeightDp = 800f,
            isVerticalVideo = true,
        )
        assertEquals(LargeScreenVideoLayoutMode.Landscape, metrics.mode)
    }

    @Test
    fun landscapeSidePaneStaysBetween280And425() {
        val width = resolveLargeScreenLandscapePlayerWidthDp(1280f, 800f)
        val side = 1280f - width
        assertTrue(side in 280f..425f)
    }

    @Test
    fun foldableCoverLandscape_staysOnPhoneLayout() {
        assertFalse(
            shouldUseLargeScreenVideoLayout(
                windowWidthDp = 672f,
                windowHeightDp = 460f,
                horizontalAdaptationEnabled = true,
                isFoldableCoverWindow = true,
            )
        )
    }

    @Test
    fun puraXMaxCoverPortrait_reservesScrollableDetailViewport() {
        // Pura X Max cover display: 1848 x 1264 px. Its landscape-natural cover window is about
        // 672 x 460dp even when the device is held in its normal portrait posture.
        assertEquals(
            230f,
            resolvePhoneInlineVideoViewportHeightDp(
                windowWidthDp = 672f,
                windowHeightDp = 460f,
                isFoldableCoverWindow = true,
            ),
            0.1f,
        )
    }

    @Test
    fun regularPhone_keepsFullWidthSixteenByNinePlayer() {
        assertEquals(
            393f * 9f / 16f,
            resolvePhoneInlineVideoViewportHeightDp(
                windowWidthDp = 393f,
                windowHeightDp = 851f,
                isFoldableCoverWindow = false,
            ),
            0.1f,
        )
    }

    @Test
    fun puraXMaxInnerPortrait_entersAlmostSquareLayout() {
        // Pura X Max inner display: 1828 x 2584 px, aspect ratio ~ 0.7074
        // With density ~ 2.75, width ~ 665dp, height ~ 940dp
        assertTrue(
            shouldUseLargeScreenVideoLayout(
                windowWidthDp = 665f,
                windowHeightDp = 940f,
                horizontalAdaptationEnabled = true,
                isFoldableCoverWindow = false,
            )
        )
        val metrics = resolveLargeScreenVideoMetrics(
            windowWidthDp = 665f,
            windowHeightDp = 940f,
            isVerticalVideo = false,
        )
        assertEquals(LargeScreenVideoLayoutMode.AlmostSquare, metrics.mode)
        assertEquals(940f * 0.4f, metrics.playerHeightDp, 1f)
    }
}
