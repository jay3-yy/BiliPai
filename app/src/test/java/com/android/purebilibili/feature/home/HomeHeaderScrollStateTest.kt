package com.android.purebilibili.feature.home

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
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
            state.withScrollToTopLock(requestIsCurrent = { requestIsCurrent }) {
                releaseScroll.await()
                true
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
        state.withScrollToTopLock(requestIsCurrent = { false }) {
            scrolled = true
            true
        }

        assertFalse(scrolled)
        assertEquals(-48f, state.offsetPx)
        assertTrue(state.tabsCollapsed)
        assertFalse(state.revealLocked)
        assertFalse(state.scrollToTopInProgress)
    }

    @Test
    fun headerWaitsForLeadingCardAndFollowsItsRemainingScroll() = runTest {
        val state = HomeHeaderScrollState(
            mutableFloatStateOf(-60f), mutableStateOf(true), mutableFloatStateOf(20f),
        )
        val remainingScrollPx = mutableStateOf<Int?>(null)
        val finished = CompletableDeferred<Unit>()
        val request = launch {
            state.withScrollToTopLock(requestIsCurrent = { true }) {
                state.followReturnToTop(remainingScrollPx = { remainingScrollPx.value }) {
                    finished.await()
                    true
                }
            }
        }
        runCurrent()
        assertEquals(-60f, state.offsetPx)
        assertEquals(null, state.returnTabRevealFraction)

        remainingScrollPx.value = 120
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(-60f, state.offsetPx)
        assertEquals(0f, state.returnTabRevealFraction)

        remainingScrollPx.value = 60
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(-30f, state.offsetPx)
        assertEquals(0.5f, state.returnTabRevealFraction)

        remainingScrollPx.value = 0
        finished.complete(Unit)
        request.join()
        assertEquals(0f, state.offsetPx)
        assertFalse(state.tabsCollapsed)
        assertEquals(null, state.returnTabRevealFraction)
        assertFalse(state.revealLocked)
    }
}
