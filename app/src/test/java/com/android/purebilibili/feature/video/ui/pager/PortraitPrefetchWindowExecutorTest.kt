package com.android.purebilibili.feature.video.ui.pager

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortraitPrefetchWindowExecutorTest {

    @Test
    fun `updateTargets cancels stale jobs keeps in-flight targets and cancelAll cleans up`() = runTest {
        val executor = PortraitPrefetchWindowExecutor<String> { it }
        val gate = CompletableDeferred<Unit>()
        val launches = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        executor.updateTargets(this, listOf("BV1#1", "BV2#2")) { key ->
            launches += key
            launch {
                try {
                    gate.await()
                } finally {
                    if (!gate.isCompleted) cancelled += key
                }
            }
        }
        assertEquals(setOf("BV1#1", "BV2#2"), executor.activeKeys)
        assertEquals(listOf("BV1#1", "BV2#2"), launches)

        // Slide window forward: BV1 leaves, BV2 stays (not re-launched), BV3 enters.
        executor.updateTargets(this, listOf("BV2#2", "BV3#3")) { key ->
            launches += key
            launch {
                try {
                    gate.await()
                } finally {
                    if (!gate.isCompleted) cancelled += key
                }
            }
        }
        testScheduler.runCurrent()

        assertEquals(setOf("BV2#2", "BV3#3"), executor.activeKeys)
        assertEquals(listOf("BV1#1", "BV2#2", "BV3#3"), launches)
        assertTrue("BV1#1" in cancelled)
        assertFalse("BV2#2" in cancelled)

        executor.cancelAll()
        testScheduler.runCurrent()
        assertTrue(executor.activeKeys.isEmpty())
        assertTrue("BV2#2" in cancelled)
        assertTrue("BV3#3" in cancelled)
    }
}
