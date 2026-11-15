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
    fun foregroundArrivalDoesNotCancelStartedBackgroundLoad() = runTest {
        val priority = ImageLoadPriority()
        val finish = CompletableDeferred<String>()
        var attempts = 0
        val preload = async {
            priority.background(BackgroundLane.NEAR) {
                attempts++
                finish.await()
            }
        }
        runCurrent()
        val visible = launch { priority.foreground { awaitCancellation() } }
        runCurrent()
        assertTrue(preload.isActive)
        finish.complete("cover")
        assertEquals("cover", preload.await())
        assertEquals(1, attempts)
        visible.cancelAndJoin()
    }

    @Test
    fun shortForegroundBurstRestartsContinuousQuietWindow() = runTest {
        val priority = ImageLoadPriority()
        var started = false
        val preload = launch { priority.background(BackgroundLane.FAR) { started = true } }
        runCurrent()
        advanceTimeBy(100)
        // 开始和结束之间不让出线程，验证 StateFlow 合并通知后仍能识别短请求。
        priority.foreground { Unit }
        runCurrent()
        advanceTimeBy(219)
        runCurrent()
        assertFalse(started)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(started)
        preload.join()
    }

    @Test
    fun localWorkAlsoRequiresContinuousQuiet() = runTest {
        val priority = ImageLoadPriority()
        var started = false
        val work = launch { priority.backgroundLocalWork(90) { started = true } }
        runCurrent()
        advanceTimeBy(50)
        priority.foreground { Unit }
        runCurrent()
        advanceTimeBy(89)
        runCurrent()
        assertFalse(started)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(started)
        work.join()
    }

    @Test
    fun allForegroundRequestsMustFinishAndFailuresReleaseCount() = runTest {
        val priority = ImageLoadPriority()
        val first = launch { priority.foreground { awaitCancellation() } }
        val second = launch { priority.foreground { awaitCancellation() } }
        runCurrent()
        var started = false
        val preload = launch { priority.background(BackgroundLane.NEAR) { started = true } }
        first.cancelAndJoin()
        runCurrent()
        assertFalse(started)
        second.cancelAndJoin()
        preload.join()
        assertTrue(started)
        try {
            priority.foreground<Unit> { error("Image decode failed") }
        } catch (_: IllegalStateException) {
            // 失败与取消都必须释放计数。
        }
        assertEquals("cover", priority.background(BackgroundLane.NEAR) { "cover" })
    }

    @Test
    fun nearLaneIsSerialAndPageCancellationReleasesRunningAndQueuedWork() = runTest {
        val priority = ImageLoadPriority()
        var active = 0
        var queuedStarted = false
        val page = launch {
            launch {
                priority.background(BackgroundLane.NEAR) {
                    active++
                    try { awaitCancellation() } finally { active-- }
                }
            }
            launch { priority.background(BackgroundLane.NEAR) { queuedStarted = true } }
        }
        runCurrent()
        assertEquals(1, active)
        assertFalse(queuedStarted)
        page.cancelAndJoin()
        assertEquals(0, active)
        assertFalse(queuedStarted)
        assertEquals("new page", priority.background(BackgroundLane.NEAR) { "new page" })
    }
}
