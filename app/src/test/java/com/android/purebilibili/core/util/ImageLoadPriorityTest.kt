package com.android.purebilibili.core.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
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
class ImageLoadPriorityTest {
    @Test
    fun visibleRequestsInterruptPrefetchAndItResumesAfterTheyFinish() = runTest {
        val priority = ImageLoadPriority()
        var attempts = 0
        var cancellations = 0
        val finishPreload = CompletableDeferred<Unit>()
        val preload = async {
            priority.prefetch {
                attempts++
                try {
                    finishPreload.await()
                    "cover"
                } finally {
                    if (!finishPreload.isCompleted) cancellations++
                }
            }
        }
        advanceTimeBy(360)
        runCurrent()
        assertEquals(1, attempts)
        val visible = launch { priority.foreground { awaitCancellation() } }
        runCurrent()
        assertEquals(1, cancellations)
        advanceTimeBy(1000)
        assertEquals(1, attempts)
        visible.cancelAndJoin()
        advanceTimeBy(359)
        runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, attempts)
        finishPreload.complete(Unit)
        assertEquals("cover", preload.await())
    }

    @Test
    fun multipleVisibleRequestsMustAllFinishAndErrorsReleaseTheirBudget() = runTest {
        val priority = ImageLoadPriority()
        val first = launch { priority.foreground { awaitCancellation() } }
        val second = launch { priority.foreground { awaitCancellation() } }
        runCurrent()
        var started = false
        val preload = async { priority.prefetch { started = true } }
        first.cancelAndJoin()
        advanceTimeBy(1000)
        runCurrent()
        assertFalse(started)
        second.cancelAndJoin()
        try {
            priority.foreground<Unit> { error("Image decode failed") }
        } catch (_: IllegalStateException) {
            // The failure must release the foreground count as cancellation does.
        }
        advanceTimeBy(360)
        runCurrent()
        preload.await()
        assertTrue(started)
    }

    @Test
    fun preloadsAreSerialAndLeavingPageCancelsQueuedAndRunningWork() = runTest {
        val priority = ImageLoadPriority()
        var active = 0
        var queuedStarted = false
        val running = launch {
            priority.prefetch {
                active++
                try { awaitCancellation() } finally { active-- }
            }
        }
        val queued = launch { priority.prefetch { queuedStarted = true } }
        advanceTimeBy(360)
        runCurrent()
        assertEquals(1, active)
        assertFalse(queuedStarted)
        queued.cancelAndJoin()
        running.cancelAndJoin()
        advanceTimeBy(1000)
        assertEquals(0, active)
        assertFalse(queuedStarted)
        assertEquals("new page", priority.prefetch { "new page" })
    }
}
