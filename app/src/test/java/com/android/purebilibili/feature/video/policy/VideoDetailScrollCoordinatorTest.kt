package com.android.purebilibili.feature.video.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoDetailScrollCoordinatorTest {

    @Test
    fun preScroll_doesNothingWhenInlineCollapseDisabled() {
        val update = reduceVideoDetailPreScroll(
            currentOffsetPx = -40f,
            deltaPx = -30f,
            minOffsetPx = -320f,
            inlinePortraitScrollEnabled = false,
            isPortraitFullscreen = false
        )

        assertNull(update)
    }

    @Test
    fun preScroll_consumesUpwardSwipeToCollapsePlayer() {
        val update = reduceVideoDetailPreScroll(
            currentOffsetPx = -40f,
            deltaPx = -30f,
            minOffsetPx = -320f,
            inlinePortraitScrollEnabled = true,
            isPortraitFullscreen = false
        )

        assertEquals(-70f, update?.nextOffsetPx)
        assertEquals(-30f, update?.consumedDeltaPx)
    }

    @Test
    fun dragHandleScroll_consumesUpwardSwipeWithoutInlineCollapseSetting() {
        val update = reduceVideoDetailDragHandleScroll(
            currentOffsetPx = 0f,
            deltaPx = -42f,
            minOffsetPx = -320f,
            isPortraitFullscreen = false
        )

        assertEquals(-42f, update?.nextOffsetPx)
        assertEquals(-42f, update?.consumedDeltaPx)
    }

    @Test
    fun postScroll_consumesDownwardSwipeToRestorePlayer() {
        val update = reduceVideoDetailPostScroll(
            currentOffsetPx = -80f,
            deltaPx = 25f,
            minOffsetPx = -320f,
            inlinePortraitScrollEnabled = true,
            isPortraitFullscreen = false
        )

        assertEquals(-55f, update?.nextOffsetPx)
        assertEquals(25f, update?.consumedDeltaPx)
    }

    @Test
    fun postScroll_doesNothingInPortraitFullscreen() {
        val update = reduceVideoDetailPostScroll(
            currentOffsetPx = -80f,
            deltaPx = 25f,
            minOffsetPx = -320f,
            inlinePortraitScrollEnabled = true,
            isPortraitFullscreen = true
        )

        assertNull(update)
    }

    @Test
    fun collapseProgress_clampsIntoZeroToOneRange() {
        assertEquals(
            0f,
            resolveVideoDetailCollapseProgress(
                playerHeightOffsetPx = 40f,
                collapseRangePx = 320f,
                isPortraitFullscreen = false
            )
        )
        assertEquals(
            0.5f,
            resolveVideoDetailCollapseProgress(
                playerHeightOffsetPx = -160f,
                collapseRangePx = 320f,
                isPortraitFullscreen = false
            )
        )
        assertEquals(
            0f,
            resolveVideoDetailCollapseProgress(
                playerHeightOffsetPx = -160f,
                collapseRangePx = 320f,
                isPortraitFullscreen = true
            )
        )
    }
}
