package com.android.purebilibili.core.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class VerticalPriorityPagerGestureTest {

    @Test
    fun `movement below system touch slop stays undecided`() {
        assertEquals(
            PagerGestureDirection.UNDECIDED,
            resolveVerticalPriorityPagerGestureDirection(
                totalX = 5f,
                totalY = 5f,
                touchSlop = 10f,
            ),
        )
    }

    @Test
    fun `mostly vertical drag locks vertical`() {
        assertEquals(
            PagerGestureDirection.VERTICAL,
            resolveVerticalPriorityPagerGestureDirection(
                totalX = 8f,
                totalY = 20f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `early vertical drift does not lock vertical before a horizontal swipe shows intent`() {
        // 用户场景：手指落下先向下漂几像素，随后横向滑动。竖向漂移超出系统
        // slop 但仍在意图宽限内时必须保持 UNDECIDED，给横向锁定留机会。
        assertEquals(
            PagerGestureDirection.UNDECIDED,
            resolveVerticalPriorityPagerGestureDirection(
                totalX = 3f,
                totalY = 9f,
                touchSlop = 8f,
            ),
        )
        // 漂移继续累积直到超出宽限后，仍归属竖向（保持既有偏竖语义）。
        assertEquals(
            PagerGestureDirection.VERTICAL,
            resolveVerticalPriorityPagerGestureDirection(
                totalX = 4f,
                totalY = 14f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `ambiguous diagonal drag prefers vertical content`() {
        assertEquals(
            PagerGestureDirection.VERTICAL,
            resolveVerticalPriorityPagerGestureDirection(
                totalX = 18f,
                totalY = 15f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `slightly horizontal diagonal gets a short intent grace distance`() {
        assertEquals(
            PagerGestureDirection.UNDECIDED,
            resolveVerticalPriorityPagerGestureDirection(
                totalX = 9f,
                totalY = 7f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `clearly horizontal drag locks pager at system touch slop`() {
        assertEquals(
            PagerGestureDirection.HORIZONTAL,
            resolveVerticalPriorityPagerGestureDirection(
                totalX = 9f,
                totalY = 2f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `home pager waits through a short horizontal arc before taking the gesture`() {
        assertEquals(
            PagerGestureDirection.UNDECIDED,
            resolveVerticalPriorityPagerGestureDirection(
                totalX = 11f,
                totalY = 2f,
                touchSlop = 8f,
                horizontalLockSlopMultiplier = HOME_PAGER_HORIZONTAL_LOCK_SLOP_MULTIPLIER,
            ),
        )
        assertEquals(
            PagerGestureDirection.HORIZONTAL,
            resolveVerticalPriorityPagerGestureDirection(
                totalX = 13f,
                totalY = 3f,
                touchSlop = 8f,
                horizontalLockSlopMultiplier = HOME_PAGER_HORIZONTAL_LOCK_SLOP_MULTIPLIER,
            ),
        )
    }

    @Test
    fun `initial pager delta consumes one touch slop without reversing direction`() {
        assertEquals(
            1f,
            resolvePagerInitialHorizontalDelta(totalX = 9f, touchSlop = 8f),
        )
        assertEquals(
            -1f,
            resolvePagerInitialHorizontalDelta(totalX = -9f, touchSlop = 8f),
        )
        assertEquals(
            0f,
            resolvePagerInitialHorizontalDelta(totalX = 7f, touchSlop = 8f),
        )
    }

    @Test
    fun `slow drag changes page after responsive positional threshold`() {
        assertEquals(
            3,
            resolvePagerReleaseTargetPage(
                startPage = 2,
                pageCount = 5,
                pageSizePx = 400f,
                scrollDeltaPx = 81f,
                scrollVelocityPxPerSecond = 100f,
                minimumFlingVelocityPxPerSecond = 900f,
            ),
        )
        assertEquals(
            2,
            resolvePagerReleaseTargetPage(
                startPage = 2,
                pageCount = 5,
                pageSizePx = 400f,
                scrollDeltaPx = 79f,
                scrollVelocityPxPerSecond = 100f,
                minimumFlingVelocityPxPerSecond = 900f,
            ),
        )
    }

    @Test
    fun `wide tablet page uses capped positional threshold`() {
        assertEquals(
            3,
            resolvePagerReleaseTargetPage(
                startPage = 2,
                pageCount = 5,
                pageSizePx = 2_000f,
                scrollDeltaPx = 97f,
                scrollVelocityPxPerSecond = 100f,
                maximumPositionThresholdPx = 96f,
                minimumFlingVelocityPxPerSecond = 900f,
            ),
        )
        assertEquals(
            2,
            resolvePagerReleaseTargetPage(
                startPage = 2,
                pageCount = 5,
                pageSizePx = 2_000f,
                scrollDeltaPx = 95f,
                scrollVelocityPxPerSecond = 100f,
                maximumPositionThresholdPx = 96f,
                minimumFlingVelocityPxPerSecond = 900f,
            ),
        )
    }

    @Test
    fun `short fast fling changes page in fling direction`() {
        assertEquals(
            1,
            resolvePagerReleaseTargetPage(
                startPage = 2,
                pageCount = 5,
                pageSizePx = 400f,
                scrollDeltaPx = -20f,
                scrollVelocityPxPerSecond = -901f,
                minimumFlingVelocityPxPerSecond = 900f,
            ),
        )
    }

    @Test
    fun `opposite release velocity returns to start page instead of jumping backwards`() {
        assertEquals(
            2,
            resolvePagerReleaseTargetPage(
                startPage = 2,
                pageCount = 5,
                pageSizePx = 400f,
                scrollDeltaPx = 120f,
                scrollVelocityPxPerSecond = -1_000f,
                minimumFlingVelocityPxPerSecond = 900f,
            ),
        )
        assertEquals(
            2,
            resolvePagerReleaseTargetPage(
                startPage = 2,
                pageCount = 5,
                pageSizePx = 400f,
                scrollDeltaPx = -120f,
                scrollVelocityPxPerSecond = 1_000f,
                minimumFlingVelocityPxPerSecond = 900f,
            ),
        )
    }

    @Test
    fun `release target stays inside pager bounds`() {
        assertEquals(
            0,
            resolvePagerReleaseTargetPage(
                startPage = 0,
                pageCount = 5,
                pageSizePx = 400f,
                scrollDeltaPx = -200f,
                scrollVelocityPxPerSecond = -1_000f,
                minimumFlingVelocityPxPerSecond = 900f,
            ),
        )
    }
}
