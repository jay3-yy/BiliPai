package com.android.purebilibili.feature.home.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LiquidGlassReadabilitySamplingTest {
    @Test
    fun `idle window does not request periodic copies`() = runTest {
        val draws = Channel<Unit>(Channel.CONFLATED)
        var samples = 0
        val job = backgroundScope.launch {
            sampleLiquidGlassReadabilityOnDraw(draws, { testScheduler.currentTime }) { samples++ }
        }
        draws.trySend(Unit)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(350)
        testScheduler.runCurrent()
        assertEquals(1, samples)

        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()
        assertEquals(1, samples)
        job.cancel()
    }

    @Test
    fun `draw bursts merge into one current sample without replaying old frames`() = runTest {
        val draws = Channel<Unit>(Channel.CONFLATED)
        val samples = mutableListOf<Long>()
        val job = backgroundScope.launch {
            sampleLiquidGlassReadabilityOnDraw(draws, { testScheduler.currentTime }) {
                samples += testScheduler.currentTime
            }
        }
        draws.trySend(Unit)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(350)
        testScheduler.runCurrent()

        repeat(12) {
            draws.trySend(Unit)
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(100)
        }
        assertEquals(listOf(350L), samples)
        testScheduler.advanceTimeBy(300)
        testScheduler.runCurrent()
        assertEquals(listOf(350L, 1850L), samples)
        testScheduler.advanceTimeBy(10_000)
        testScheduler.runCurrent()
        assertEquals(2, samples.size)
        job.cancel()
    }

    @Test
    fun `hiding cancels a queued copy and restoring samples the current window`() = runTest {
        var samples = 0
        val oldDraws = Channel<Unit>(Channel.CONFLATED)
        val hidden = backgroundScope.launch {
            sampleLiquidGlassReadabilityOnDraw(oldDraws, { testScheduler.currentTime }) { samples++ }
        }
        oldDraws.trySend(Unit)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(200)
        hidden.cancel()
        testScheduler.runCurrent()
        oldDraws.trySend(Unit)
        testScheduler.advanceTimeBy(10_000)
        testScheduler.runCurrent()
        assertEquals(0, samples)

        val currentDraws = Channel<Unit>(Channel.CONFLATED)
        val restored = backgroundScope.launch {
            sampleLiquidGlassReadabilityOnDraw(currentDraws, { testScheduler.currentTime }) { samples++ }
        }
        currentDraws.trySend(Unit)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(350)
        testScheduler.runCurrent()
        assertEquals(1, samples)
        restored.cancel()
    }
}
