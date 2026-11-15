package com.android.purebilibili.navigation

import com.android.purebilibili.feature.home.components.BottomNavItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BottomPagerPreloadSchedulerTest {
    private val tabs = listOf(BottomNavItem.HOME, BottomNavItem.DYNAMIC, BottomNavItem.STORY, BottomNavItem.PROFILE)

    @Test
    fun inputArrivingBeforeIdleCommitPreventsMountWithoutWaitingForEffectCancellation() = runTest {
        var allowed = true
        val idleRequested = CompletableDeferred<Unit>()
        val idle = CompletableDeferred<Unit>()
        val additions = mutableListOf<BottomNavItem>()
        val job = launch {
            preloadBottomPagerPages(
                visibleItems = tabs,
                preloadedItems = { setOf(BottomNavItem.HOME) },
                awaitFrame = {},
                awaitIdle = { idleRequested.complete(Unit); idle.await() },
                isPreloadAllowed = { allowed },
                onPreload = { additions += it },
            )
        }
        idleRequested.await()
        assertTrue(additions.isEmpty())
        allowed = false
        idle.complete(Unit)
        job.join()
        assertTrue(additions.isEmpty())
    }

    @Test
    fun cancellationWhileWaitingForIdleDoesNotMountOrDropRetainedPages() = runTest {
        val retained = setOf(BottomNavItem.HOME)
        var loaded = retained
        val idleRequested = CompletableDeferred<Unit>()
        val idle = CompletableDeferred<Unit>()
        val job = launch {
            preloadBottomPagerPages(
                visibleItems = tabs,
                preloadedItems = { loaded },
                awaitFrame = {},
                awaitIdle = { idleRequested.complete(Unit); idle.await() },
                onPreload = { loaded = loaded + it },
            )
        }
        idleRequested.await()
        job.cancelAndJoin()
        idle.complete(Unit)
        runCurrent()
        assertEquals(retained, loaded)
    }

    @Test
    fun quietWindowThenOnePagePerIntervalWithoutStoryPlayer() = runTest {
        var loaded = setOf(BottomNavItem.HOME)
        val additions = mutableListOf<BottomNavItem>()
        val job = launch {
            preloadBottomPagerPages(tabs, { loaded }, {}) {
                additions += it
                loaded = loaded + it
            }
        }
        runCurrent()
        advanceTimeBy(BOTTOM_PAGER_PRELOAD_IDLE_MILLIS - 1)
        runCurrent()
        assertTrue(additions.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(BottomNavItem.DYNAMIC), additions)
        advanceTimeBy(BOTTOM_PAGER_PRELOAD_INTERVAL_MILLIS)
        runCurrent()
        assertEquals(listOf(BottomNavItem.DYNAMIC, BottomNavItem.PROFILE), additions)
        job.join()
        assertFalse(BottomNavItem.STORY in loaded)
    }

    @Test
    fun interruptedPreloadKeepsExistingPagesAndResumesAfterAnotherQuietWindow() = runTest {
        var loaded = setOf(BottomNavItem.HOME)
        val additions = mutableListOf<BottomNavItem>()
        suspend fun preload() = preloadBottomPagerPages(tabs, { loaded }, {}) {
            additions += it
            loaded = loaded + it
        }
        val first = launch { preload() }
        advanceTimeBy(BOTTOM_PAGER_PRELOAD_IDLE_MILLIS)
        runCurrent()
        first.cancelAndJoin()
        advanceTimeBy(2_000)
        assertEquals(listOf(BottomNavItem.DYNAMIC), additions)

        val resumed = launch { preload() }
        runCurrent()
        advanceTimeBy(BOTTOM_PAGER_PRELOAD_IDLE_MILLIS - 1)
        runCurrent()
        assertEquals(listOf(BottomNavItem.DYNAMIC), additions)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(BottomNavItem.DYNAMIC, BottomNavItem.PROFILE), additions)
        resumed.join()
    }

    @Test
    fun cancellationWhileWaitingForFrameDoesNotCreateAnotherPage() = runTest {
        val frameRequested = CompletableDeferred<Unit>()
        val frame = CompletableDeferred<Unit>()
        val additions = mutableListOf<BottomNavItem>()
        val job = launch {
            preloadBottomPagerPages(tabs, { setOf(BottomNavItem.HOME) }, {
                frameRequested.complete(Unit)
                frame.await()
            }) { additions += it }
        }
        frameRequested.await()
        job.cancelAndJoin()
        frame.complete(Unit)
        runCurrent()
        assertTrue(additions.isEmpty())
    }

    @Test
    fun selectingAnUnwarmedPageBypassesPreloadWaitAndRetainedPagesStayComposed() {
        assertTrue(shouldComposeBottomPagerPage(BottomNavItem.PROFILE, 3, 0, 3, true, 0, false))
        assertTrue(shouldComposeBottomPagerPage(BottomNavItem.DYNAMIC, 1, 0, 0, false, 0, true))
        assertFalse(shouldComposeBottomPagerPage(BottomNavItem.PROFILE, 3, 0, 0, false, 0, false))
        assertEquals(BottomNavItem.PROFILE, nextBottomPagerPreloadItem(
            listOf(BottomNavItem.PROFILE, BottomNavItem.HOME, BottomNavItem.DYNAMIC),
            setOf(BottomNavItem.HOME, BottomNavItem.DYNAMIC),
        ))
    }

    @Test
    fun coveredMainHostAndCardOrPagerMotionDisableBackgroundWork() {
        assertTrue(shouldAllowBottomPagerBackgroundWork(true, true, false))
        assertFalse(shouldAllowBottomPagerBackgroundWork(false, true, false))
        assertFalse(shouldAllowBottomPagerBackgroundWork(true, false, false))
        assertFalse(shouldAllowBottomPagerBackgroundWork(true, true, true))
        // 空间/搜索返回时栈顶已是首页，但退出动画尚未结束。
        assertFalse(shouldAllowBottomPagerBackgroundWork(true, true, false, isNavigationSettled = false))
    }
}
