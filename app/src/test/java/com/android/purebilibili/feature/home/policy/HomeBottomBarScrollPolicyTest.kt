package com.android.purebilibili.feature.home.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeBottomBarScrollPolicyTest {

    @Test
    fun topOfList_alwaysShowsBottomBar() {
        val update = reduceHomeBottomBarListScroll(
            previousState = HomeBottomBarScrollState(firstVisibleItem = 4, scrollOffset = 320),
            firstVisibleItem = 0,
            scrollOffset = 80,
            isVideoNavigating = false
        )

        assertEquals(BottomBarVisibilityIntent.SHOW, update.visibilityIntent)
        assertEquals(HomeBottomBarScrollState(firstVisibleItem = 0, scrollOffset = 80), update.state)
    }

    @Test
    fun scrollingDownToNextItem_hidesBottomBar() {
        val update = reduceHomeBottomBarListScroll(
            previousState = HomeBottomBarScrollState(firstVisibleItem = 2, scrollOffset = 120),
            firstVisibleItem = 3,
            scrollOffset = 20,
            isVideoNavigating = false
        )

        assertEquals(BottomBarVisibilityIntent.HIDE, update.visibilityIntent)
    }

    @Test
    fun scrollingUpToPreviousItem_showsBottomBar() {
        val update = reduceHomeBottomBarListScroll(
            previousState = HomeBottomBarScrollState(firstVisibleItem = 3, scrollOffset = 20),
            firstVisibleItem = 2,
            scrollOffset = 240,
            isVideoNavigating = false
        )

        assertEquals(BottomBarVisibilityIntent.SHOW, update.visibilityIntent)
    }

    @Test
    fun smallOffsetDelta_doesNotToggleBottomBar() {
        val update = reduceHomeBottomBarListScroll(
            previousState = HomeBottomBarScrollState(firstVisibleItem = 2, scrollOffset = 300),
            firstVisibleItem = 2,
            scrollOffset = 420,
            isVideoNavigating = false,
            offsetHysteresisPx = 200
        )

        assertNull(update.visibilityIntent)
    }

    @Test
    fun largePositiveOffsetDelta_hidesBottomBar() {
        val update = reduceHomeBottomBarListScroll(
            previousState = HomeBottomBarScrollState(firstVisibleItem = 2, scrollOffset = 300),
            firstVisibleItem = 2,
            scrollOffset = 520,
            isVideoNavigating = false,
            offsetHysteresisPx = 200
        )

        assertEquals(BottomBarVisibilityIntent.HIDE, update.visibilityIntent)
    }

    @Test
    fun videoNavigation_suppressesVisibilityChangesButKeepsTrackingStateFresh() {
        val update = reduceHomeBottomBarListScroll(
            previousState = HomeBottomBarScrollState(firstVisibleItem = 2, scrollOffset = 120),
            firstVisibleItem = 4,
            scrollOffset = 60,
            isVideoNavigating = true
        )

        assertNull(update.visibilityIntent)
        assertEquals(HomeBottomBarScrollState(firstVisibleItem = 4, scrollOffset = 60), update.state)
    }

    @Test
    fun videoNavigation_afterContentInteractionRestore_usesScrollDirectionNormally() {
        val update = reduceHomeBottomBarListScroll(
            previousState = HomeBottomBarScrollState(firstVisibleItem = 2, scrollOffset = 120),
            firstVisibleItem = 4,
            scrollOffset = 60,
            isVideoNavigating = true,
            contentInteractionRestored = true
        )

        assertEquals(BottomBarVisibilityIntent.HIDE, update.visibilityIntent)
        assertEquals(HomeBottomBarScrollState(firstVisibleItem = 4, scrollOffset = 60), update.state)
    }

    @Test
    fun chromeScrollOffset_usesCurrentOffsetAtTopAndSaturatesPastFirstItem() {
        assertEquals(
            0f,
            resolveBottomBarChromeScrollOffset(firstVisibleItem = 0, scrollOffset = 0)
        )
        assertEquals(
            72f,
            resolveBottomBarChromeScrollOffset(firstVisibleItem = 0, scrollOffset = 72)
        )
        assertEquals(
            1000f,
            resolveBottomBarChromeScrollOffset(firstVisibleItem = 1, scrollOffset = 0)
        )
    }

    @Test
    fun scrollSample_suppressesSubStepDriftWithinOneItem() {
        val emitted = HomeBottomBarScrollState(firstVisibleItem = 2, scrollOffset = 300)
        assertFalse(
            shouldEmitBottomBarScrollSample(
                previousEmittedItem = emitted.firstVisibleItem,
                previousEmittedOffset = emitted.scrollOffset,
                firstVisibleItem = emitted.firstVisibleItem,
                scrollOffset = emitted.scrollOffset + 39
            )
        )
        assertTrue(
            shouldEmitBottomBarScrollSample(
                previousEmittedItem = emitted.firstVisibleItem,
                previousEmittedOffset = emitted.scrollOffset,
                firstVisibleItem = emitted.firstVisibleItem,
                scrollOffset = emitted.scrollOffset + 40
            )
        )
    }

    @Test
    fun scrollSample_emitsOnItemChangeAndAbsoluteTopRegion() {
        assertTrue(
            shouldEmitBottomBarScrollSample(
                previousEmittedItem = 2,
                previousEmittedOffset = 0,
                firstVisibleItem = 3,
                scrollOffset = 0
            )
        )
        // 回到顶部显隐区是绝对判定，即使位移未跨过步长也要逐帧上报，
        // 否则停在阈值下方时 SHOW 永远不会结算。
        assertTrue(
            shouldEmitBottomBarScrollSample(
                previousEmittedItem = 0,
                previousEmittedOffset = 99,
                firstVisibleItem = 0,
                scrollOffset = 90
            )
        )
        assertFalse(
            shouldEmitBottomBarScrollSample(
                previousEmittedItem = 0,
                previousEmittedOffset = 99,
                firstVisibleItem = 0,
                scrollOffset = 120
            )
        )
    }
}
