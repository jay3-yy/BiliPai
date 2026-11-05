package com.android.purebilibili.feature.home

import androidx.compose.animation.core.snap
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeHeaderScrollStateTest {
    @Test
    fun invalidatedScrollDoesNotRevealTheNextPagesHeader() = runTest {
        val globalOffset = mutableFloatStateOf(0f)
        val state = HomeHeaderScrollState(mutableFloatStateOf(0f), mutableStateOf(false), globalOffset)
        val releaseScroll = CompletableDeferred<Unit>()
        var requestIsCurrent = true
        val request = launch {
            state.withScrollToTopLock(snap(), requestIsCurrent = { requestIsCurrent }) {
                releaseScroll.await()
            }
        }
        runCurrent()
        assertTrue(state.revealLocked)
        assertTrue(state.scrollToTopInProgress)

        // 切页后新页面已经设置自己的折叠状态；旧列表滚动此时才结束。
        requestIsCurrent = false
        state.setOffsetImmediately(-72f)
        state.tabsCollapsed = true
        globalOffset.floatValue = 25f
        releaseScroll.complete(Unit)
        request.join()

        assertEquals(-72f, state.offsetPx)
        assertTrue(state.tabsCollapsed)
        assertEquals(25f, globalOffset.floatValue)
        assertFalse(state.revealLocked)
        assertFalse(state.scrollToTopInProgress)
        assertFalse(state.isSettling)
    }

    @Test
    fun alreadyInvalidRequestDoesNotScrollOrChangeHeader() = runTest {
        val state = HomeHeaderScrollState(
            mutableFloatStateOf(-48f), mutableStateOf(true), mutableFloatStateOf(20f),
        )
        var scrolled = false
        state.withScrollToTopLock(snap(), requestIsCurrent = { false }) { scrolled = true }

        assertFalse(scrolled)
        assertEquals(-48f, state.offsetPx)
        assertTrue(state.tabsCollapsed)
        assertFalse(state.revealLocked)
        assertFalse(state.scrollToTopInProgress)
    }
}
