package com.android.purebilibili.feature.home

import com.android.purebilibili.core.util.BackgroundLane
import com.android.purebilibili.core.util.ImageLoadPriority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeCoverPreloadSchedulerTest {
    @Test
    fun nearLaneProgressesWhileFarLaneWaitsAndLoads() = runTest {
        val windows = MutableStateFlow(HomeCoverPreloadWindow(far = 3..3))
        val started = mutableListOf<Pair<BackgroundLane, Int>>()
        val page = launch {
            consumeHomeCoverPreloadWindows(windows, ImageLoadPriority()) { lane, index, _ ->
                started += lane to index
                if (lane == BackgroundLane.FAR) awaitCancellation()
            }
        }
        runCurrent()
        windows.value = HomeCoverPreloadWindow(near = 1..1, far = 3..3)
        runCurrent()
        assertEquals(listOf(BackgroundLane.NEAR to 1), started)
        advanceTimeBy(220)
        runCurrent()
        assertTrue((BackgroundLane.FAR to 3) in started)
        windows.value = HomeCoverPreloadWindow(near = 5..5)
        runCurrent()
        assertTrue((BackgroundLane.NEAR to 5) in started)
        page.cancelAndJoin()
    }

    @Test
    fun cachedCoversDoNotConsumeFarQuietWindows() = runTest {
        val windows = MutableStateFlow(HomeCoverPreloadWindow(far = 3..4))
        val started = mutableListOf<Int>()
        val page = launch {
            consumeHomeCoverPreloadWindows(
                windowFlow = windows,
                priority = ImageLoadPriority(),
                needsPreload = { it != 3 },
            ) { _, index, _ -> started += index }
        }
        advanceTimeBy(220)
        runCurrent()
        assertEquals(listOf(4), started)
        page.cancelAndJoin()
    }

    @Test
    fun scrollingDiscardsFarRequestStillWaitingForQuiet() = runTest {
        val windows = MutableStateFlow(HomeCoverPreloadWindow(far = 3..5))
        val started = mutableListOf<Int>()
        val page = launch {
            consumeHomeCoverPreloadWindows(windows, ImageLoadPriority()) { _, index, _ -> started += index }
        }
        runCurrent()
        advanceTimeBy(100)
        windows.value = HomeCoverPreloadWindow(near = 8..8)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(8), started)
        page.cancelAndJoin()
    }

    @Test
    fun windowChangeFinishesActiveRequestThenSkipsOldTail() = runTest {
        val windows = MutableStateFlow(HomeCoverPreloadWindow(near = 1..2))
        val finishFirst = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        var firstFinished = false
        val page = launch {
            consumeHomeCoverPreloadWindows(windows, ImageLoadPriority()) { _, index, _ ->
                started += index
                if (index == 1) {
                    finishFirst.await()
                    firstFinished = true
                }
            }
        }
        runCurrent()
        windows.value = HomeCoverPreloadWindow(near = 10..11)
        runCurrent()
        assertEquals(listOf(1), started)
        assertFalse(firstFinished)
        finishFirst.complete(Unit)
        runCurrent()
        assertTrue(firstFinished)
        assertEquals(listOf(1, 10, 11), started)
        page.cancelAndJoin()
    }

    @Test
    fun leavingPageCancelsBothLanesAndDoesNotStartQueuedRequests() = runTest {
        val windows = MutableStateFlow(HomeCoverPreloadWindow(near = 1..2, far = 3..4))
        val started = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val page = launch {
            consumeHomeCoverPreloadWindows(windows, ImageLoadPriority()) { _, index, _ ->
                started += index
                try { awaitCancellation() } finally { cancelled += index }
            }
        }
        advanceTimeBy(220)
        runCurrent()
        assertEquals(setOf(1, 3), started.toSet())
        page.cancelAndJoin()
        windows.value = HomeCoverPreloadWindow(near = 8..8)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(setOf(1, 3), cancelled.toSet())
        assertEquals(2, started.size)
    }
}
