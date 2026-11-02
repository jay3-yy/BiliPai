package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class MiniPlayerOverlayPositionPolicyTest {

    @Test
    fun initialOffset_isClampedInsideVisibleBounds() {
        val result = clampMiniPlayerOverlayOffset(
            offsetX = 980f,
            offsetY = 40f,
            screenWidthPx = 1080f,
            screenHeightPx = 2400f,
            miniPlayerWidthPx = 420f,
            miniPlayerHeightPx = 240f,
            outerPaddingPx = 24f,
            topInsetPx = 50f,
            bottomInsetPx = 100f
        )

        assertEquals(636f, result.x, 0.001f)
        assertEquals(74f, result.y, 0.001f)
    }

    @Test
    fun initialOffset_usesCurrentMiniPlayerSizeWhenUserPreviouslyResized() {
        val result = resolveMiniPlayerInitialOverlayOffset(
            cardLeftPx = 760f,
            entryFromLeft = false,
            screenWidthPx = 1080f,
            screenHeightPx = 2400f,
            miniPlayerWidthPx = 640f,
            miniPlayerHeightPx = 360f,
            outerPaddingPx = 24f,
            topInsetPx = 50f,
            bottomInsetPx = 100f
        )

        assertEquals(416f, result.x, 0.001f)
        assertEquals(1916f, result.y, 0.001f)
    }

    @Test
    fun resizeFromRightEdge_keepsOffsetInsideBoundsForNewWidth() {
        val result = resolveMiniPlayerOffsetAfterSizeChanged(
            offsetX = 636f,
            offsetY = 320f,
            screenWidthPx = 1080f,
            screenHeightPx = 2400f,
            miniPlayerWidthPx = 640f,
            miniPlayerHeightPx = 360f,
            outerPaddingPx = 24f,
            topInsetPx = 50f,
            bottomInsetPx = 100f
        )

        assertEquals(416f, result.x, 0.001f)
        assertEquals(320f, result.y, 0.001f)
    }

    @Test
    fun offsetClamp_preservesInBoundsPosition() {
        val result = clampMiniPlayerOverlayOffset(
            offsetX = 120f,
            offsetY = 320f,
            screenWidthPx = 1080f,
            screenHeightPx = 2400f,
            miniPlayerWidthPx = 420f,
            miniPlayerHeightPx = 240f,
            outerPaddingPx = 24f,
            topInsetPx = 50f,
            bottomInsetPx = 100f
        )

        assertEquals(120f, result.x, 0.001f)
        assertEquals(320f, result.y, 0.001f)
    }

    @Test
    fun dockedBottomOffset_keepsMiniPlayerAboveHomeBottomBarArea() {
        assertEquals(
            2030f,
            resolveMiniPlayerDockedBottomOffsetY(
                screenHeightPx = 2400f,
                miniPlayerHeightPx = 240f,
                outerPaddingPx = 24f,
                bottomInsetPx = 106f
            ),
            0.001f
        )
    }

    @Test
    fun resizeBounds_keepMiniPlayerVisibleAndAllowUsefulScaling() {
        val bounds = resolveMiniPlayerResizeBounds(
            defaultWidthDp = 220,
            defaultHeightDp = 130,
            screenWidthDp = 393,
            screenHeightDp = 852,
            outerPaddingDp = 12,
            topInsetDp = 50,
            bottomInsetDp = 100
        )

        assertEquals(168f, bounds.minWidthDp, 0.001f)
        assertEquals(369f, bounds.maxWidthDp, 0.001f)
    }

    @Test
    fun resizeBounds_fitActualVideoAspectInSmallWindows() {
        for (aspect in listOf(9f / 16f, 1f, 16f / 9f, 2.39f)) {
            for ((width, height) in listOf(393 to 852, 240 to 400, 320 to 260)) {
                val bounds = resolveMiniPlayerResizeBounds(220, 130, width, height, 12, 50, 100, aspect)
                assertTrue(bounds.minWidthDp <= bounds.maxWidthDp)
                for (requestedWidth in listOf(bounds.minWidthDp, bounds.maxWidthDp)) {
                    val availableWidth = (width - 24).toFloat()
                    val availableHeight = (height - 24 - 150).toFloat()
                    val dimensions = resolveAdaptiveMiniPlayerDimensions(aspect, requestedWidth, 130f,
                        maxWidthDp = availableWidth, maxHeightDp = availableHeight)
                    assertTrue(dimensions.widthDp <= availableWidth + 0.01f)
                    assertTrue(dimensions.heightDp <= availableHeight + 0.01f)
                    assertEquals(dimensions.aspectRatio, dimensions.widthDp / dimensions.heightDp, 0.001f)
                }
            }
        }
    }


    @Test
    fun edgeStash_requiresOutwardPullAndCancelsOnReturn() {
        val left = MiniPlayerEdgeStashSide.LEFT
        val right = MiniPlayerEdgeStashSide.RIGHT
        assertEquals(null, resolveMiniPlayerEdgeStashSide(12f, 12f, 180f, 28f))
        assertEquals(null, resolveMiniPlayerEdgeStashSide(180f, 12f, 180f, 28f))
        assertEquals(null, resolveMiniPlayerEdgeStashSide(-10f, 12f, 180f, 28f))
        assertEquals(left, resolveMiniPlayerEdgeStashSide(-16f, 12f, 180f, 28f))
        assertEquals(right, resolveMiniPlayerEdgeStashSide(208f, 12f, 180f, 28f))
        // Retain an armed side through small threshold noise; dragging inward cancels it.
        assertEquals(left, resolveMiniPlayerEdgeStashSide(-8f, 12f, 180f, 28f, left))
        assertEquals(null, resolveMiniPlayerEdgeStashSide(0f, 12f, 180f, 28f, left))
        assertEquals(right, resolveMiniPlayerEdgeStashSide(200f, 12f, 180f, 28f, right))
        assertEquals(null, resolveMiniPlayerEdgeStashSide(190f, 12f, 180f, 28f, right))
        assertEquals(null, resolveMiniPlayerEdgeStashSide(Float.NaN, 12f, 180f, 28f))
    }

}
