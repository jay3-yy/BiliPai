package com.android.purebilibili.core.util

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HingeLayoutPolicyTest {

    @Test
    fun `book posture splits content around a vertical separating hinge`() {
        val hinge = AppHingeFeature(
            orientation = AppHingeOrientation.Vertical,
            bounds = IntRect(490, 0, 510, 800),
            isSeparating = true,
            isOccluding = false,
            isFlat = false,
        )
        val regions = resolveHingeSafeContentRegions(1000, 800, listOf(hinge))
        assertEquals(2, regions.size)
        assertEquals(IntRect(0, 0, 490, 800), regions.first())
        assertEquals(IntRect(510, 0, 1000, 800), regions.last())
        assertEquals(
            AppHingeSafePane.Start,
            resolvePreferredHingeSafePane(AppFoldPosture.Book, AppHingeSafeContentPurpose.Media),
        )
        assertEquals(
            AppHingeSafePane.End,
            resolvePreferredHingeSafePane(AppFoldPosture.Book, AppHingeSafeContentPurpose.Dialog),
        )
    }

    @Test
    fun `tabletop posture keeps media above the hinge and controls below`() {
        val hinge = AppHingeFeature(
            orientation = AppHingeOrientation.Horizontal,
            bounds = IntRect(0, 390, 1000, 410),
            isSeparating = true,
            isOccluding = false,
            isFlat = false,
        )
        val regions = resolveHingeSafeContentRegions(1000, 800, listOf(hinge))
        assertEquals(2, regions.size)
        assertEquals(
            IntRect(0, 0, 1000, 390),
            resolveHingeSafeRegion(regions, AppHingeSafePane.Top),
        )
        assertEquals(
            IntRect(0, 410, 1000, 800),
            resolveHingeSafeRegion(regions, AppHingeSafePane.Bottom),
        )
        assertEquals(
            AppHingeSafePane.Top,
            resolvePreferredHingeSafePane(AppFoldPosture.Tabletop, AppHingeSafeContentPurpose.Media),
        )
        assertEquals(
            AppHingeSafePane.Bottom,
            resolvePreferredHingeSafePane(AppFoldPosture.Tabletop, AppHingeSafeContentPurpose.Controls),
        )
    }

    @Test
    fun `fully occluding hinge is excluded from safe regions`() {
        val hinge = AppHingeFeature(
            orientation = AppHingeOrientation.Vertical,
            bounds = IntRect(480, 0, 520, 800),
            isSeparating = true,
            isOccluding = true,
            isFlat = false,
        )
        val regions = resolveHingeSafeContentRegions(1000, 800, listOf(hinge))
        assertTrue(regions.none { region -> region.left < 520 && region.right > 480 })
        assertEquals(
            listOf(IntRect(480, 0, 520, 800)),
            resolveOccludingHingeBounds(listOf(hinge), 1000, 800),
        )
    }

    @Test
    fun `multiple vertical hinges produce three safe panes for trifold-style windows`() {
        val hinges = listOf(
            AppHingeFeature(
                orientation = AppHingeOrientation.Vertical,
                bounds = IntRect(330, 0, 350, 800),
                isSeparating = true,
                isOccluding = true,
                isFlat = true,
            ),
            AppHingeFeature(
                orientation = AppHingeOrientation.Vertical,
                bounds = IntRect(650, 0, 670, 800),
                isSeparating = true,
                isOccluding = true,
                isFlat = true,
            ),
        )
        val regions = resolveHingeSafeContentRegions(1000, 800, hinges)
        assertEquals(3, regions.size)
        assertEquals(IntRect(0, 0, 330, 800), regions[0])
        assertEquals(IntRect(350, 0, 650, 800), regions[1])
        assertEquals(IntRect(670, 0, 1000, 800), regions[2])
        val primary = selectPrimaryHingeFeature(hinges)
        assertEquals(hinges.first().bounds, primary?.bounds)
    }

    @Test
    fun `flat windows without a hinge keep a single full-window region`() {
        val regions = resolveHingeSafeContentRegions(1000, 800, emptyList())
        assertEquals(listOf(IntRect(0, 0, 1000, 800)), regions)
        assertFalse(
            AppFoldingFeatureInfo().hasObstructingHinge
        )
    }

    @Test
    fun `flat non separating flexible crease leaves the full viewport available`() {
        val hinge = AppHingeFeature(AppHingeOrientation.Vertical, IntRect(500, 0, 500, 800), false, false, true)
        assertEquals(listOf(IntRect(0, 0, 1000, 800)), resolveHingeSafeContentRegions(1000, 800, listOf(hinge), clearancePx = 16))
    }

    @Test
    fun `flat physical hinge still splits a short window`() {
        val hinge = AppHingeFeature(AppHingeOrientation.Vertical, IntRect(400, 0, 416, 300), true, true, true)
        val info = AppWindowAdaptiveInfo(
            windowSizeClass = WindowSizeClass(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Compact,
                800.dp, 300.dp),
            foldingFeature = AppFoldingFeatureInfo(posture = AppFoldPosture.Flat, hinges = listOf(hinge)),
        )
        assertTrue(info.shouldAvoidHinge)
        assertEquals(listOf(IntRect(0, 0, 400, 300), IntRect(416, 0, 800, 300)),
            resolveHingeSafeContentRegions(800, 300, listOf(hinge)))
    }

    @Test
    fun `container wholly inside an occluding hinge has no usable region`() {
        val hinge = AppHingeFeature(AppHingeOrientation.Vertical, IntRect(0, 0, 1000, 800), true, true, true)
        assertEquals(emptyList(), resolveHingeSafeContentRegions(1000, 800, listOf(hinge)))
    }

    @Test
    fun `a hinge outside the local viewport does not consume its width`() {
        val hinge = AppHingeFeature(AppHingeOrientation.Vertical, IntRect(500, 0, 516, 800), true, true, true)
        assertEquals(listOf(IntRect(0, 0, 400, 800)), resolveHingeSafeContentRegions(
            400, 800, listOf(hinge), androidx.compose.ui.unit.IntOffset(600, 0), 16))
    }

    @Test
    fun `IME cropped tabletop host keeps only the visible upper safe region`() {
        val hinge = AppHingeFeature(AppHingeOrientation.Horizontal, IntRect(0, 390, 800, 410), true, false, false)
        assertEquals(listOf(IntRect(0, 0, 800, 374)), resolveHingeSafeContentRegions(800, 400, listOf(hinge), clearancePx = 16))
    }

}
