package com.android.purebilibili.feature.home

import androidx.compose.runtime.mutableStateOf
import com.android.purebilibili.feature.home.policy.HomeFeedScrollAnchor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeReturnStateTest {
    private fun anchor(index: Int) = HomeFeedScrollAnchor(
        category = HomeCategory.RECOMMEND,
        popularSubCategory = PopularSubCategory.COMPREHENSIVE,
        firstVisibleItemIndex = index,
        firstVisibleItemScrollOffset = 24,
        headerOffsetPx = -64f,
    )

    @Test
    fun repeatedReturnsWithoutDetailNotificationRestoreCardClicks() {
        val state = HomeReturnState(mutableStateOf(null))
        repeat(4) { index ->
            state.beginVideoNavigation(anchor(index))
            assertTrue(state.videoNavigationInProgress)
            state.onHomeVisibilityChanged(false)
            assertTrue(state.videoNavigationInProgress)
            // 其他播放页面返回时没有调用 finishVideoReturn。
            state.onHomeVisibilityChanged(true)
            assertTrue(state.contentInteractionRestored)
        }
    }

    @Test
    fun stillVisibleHomeDoesNotReleaseTheClickGuardBeforeNavigationLeaves() {
        val state = HomeReturnState(mutableStateOf(null))
        state.beginVideoNavigation(anchor(1))
        state.onHomeVisibilityChanged(true)
        assertTrue(state.videoNavigationInProgress)
        state.onHomeVisibilityChanged(false)
        state.onHomeVisibilityChanged(true)
        state.beginVideoNavigation(anchor(2))
        state.onHomeVisibilityChanged(true)
        assertTrue(state.videoNavigationInProgress)
    }

    @Test
    fun hiddenPageDoesNotReplaceTheClickTimeAnchorAndOldRestoreCannotClearTheNextClick() = runTest {
        val state = HomeReturnState(mutableStateOf(null))
        val clickedAnchor = anchor(7)
        state.beginVideoNavigation(clickedAnchor)
        state.freezeAnchorIfMissing(anchor(12))
        assertSame(clickedAnchor, state.pendingAnchor)
        assertTrue(state.finishVideoReturn(0) { })

        // 同一位置再次点击也属于新的恢复请求，不能按数据相等消费旧请求。
        val nextAnchor = clickedAnchor.copy()
        state.beginVideoNavigation(nextAnchor)
        state.consumeAnchor(clickedAnchor)
        assertSame(nextAnchor, state.pendingAnchor)
        state.consumeAnchor(nextAnchor)
        assertNull(state.pendingAnchor)
    }

    @Test
    fun aNewVideoDuringTheReturnWindowSuppressesOnlyTheOldCompletion() = runTest {
        val state = HomeReturnState(mutableStateOf(null))
        var completions = 0
        state.beginVideoNavigation(anchor(3))
        val oldReturn = async { state.finishVideoReturn(400) { completions++ } }
        runCurrent()
        assertTrue(state.contentInteractionRestored)
        assertFalse(state.videoNavigationInProgress)

        advanceTimeBy(100)
        state.beginVideoNavigation(anchor(9))
        assertFalse(state.contentInteractionRestored)
        assertFalse(oldReturn.await())
        assertEquals(0, completions)
        assertTrue(state.videoNavigationInProgress)

        val nextReturn = async { state.finishVideoReturn(400) { completions++ } }
        runCurrent()
        assertTrue(state.contentInteractionRestored)
        assertTrue(nextReturn.await())
        assertEquals(1, completions)
    }
}
