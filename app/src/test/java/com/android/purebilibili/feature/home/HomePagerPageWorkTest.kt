package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomePagerPageWorkTest {
    @Test
    fun swipeExposesBothPagesButDefersActiveWorkUntilSettled() {
        for (current in listOf(true, false)) {
            val swiping = work(current = current, scrolling = true)
            assertTrue(swiping.collectUpdates)
            assertFalse(swiping.runActiveWork)
        }
        assertTrue(work().runActiveWork)
        assertFalse(work(current = false).runActiveWork)
    }

    @Test
    fun preloadedCoveredAndUnfocusedPagesKeepTheirContentWithoutActiveWork() {
        for (hidden in listOf(work(visible = false), work(unobscured = false), work(visualActive = false))) {
            assertFalse(hidden.collectUpdates)
            assertFalse(hidden.runActiveWork)
        }
        // 数据可供锚点恢复，恢复未完成前不能触发预取或加载更多。
        val restoring = work(workAllowed = false)
        assertTrue(restoring.collectUpdates)
        assertFalse(restoring.runActiveWork)
    }

    private fun work(
        visible: Boolean = true,
        current: Boolean = true,
        scrolling: Boolean = false,
        visualActive: Boolean = true,
        unobscured: Boolean = true,
        workAllowed: Boolean = true,
    ) = resolveHomePagerPageWork(
        visible = visible,
        currentPage = current,
        pagerScrolling = scrolling,
        updatesAllowed = true,
        workAllowed = workAllowed,
        visualWorkActive = visualActive,
        unobscured = unobscured,
    )
}
