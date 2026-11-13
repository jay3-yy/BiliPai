package com.android.purebilibili.feature.home.components.miuix

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class DampedDragAnimationTargetTest {

    @Test
    fun `velocity normalization follows the selectable range`() {
        assertEquals(2f, normalizeFloatingDockDragVelocity(8f, 0f..4f))
        assertEquals(-2f, normalizeFloatingDockDragVelocity(-8f, 0f..4f))
        assertEquals(0f, normalizeFloatingDockDragVelocity(0f, 0f..4f))
        assertEquals(8f, normalizeFloatingDockDragVelocity(8f, 0f..1f))
    }

    @Test
    fun `disposing animation owner cancels pending press and release`() = runTest {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val owner = Job(coroutineContext[Job])
            val animation = createAnimation(CoroutineScope(coroutineContext + owner))
            animation.animateToValue(2f)
            repeat(10) { frame ->
                testScheduler.runCurrent()
                clock.sendFrame(frame * 16_000_000L)
            }
            owner.cancel()
            testScheduler.runCurrent()
            assertTrue(owner.isCompleted)
        }
    }

    private fun createAnimation(
        scope: CoroutineScope,
        range: ClosedFloatingPointRange<Float> = 0f..2f,
        useMiuixIndicatorMotion: Boolean = false,
        trackingMode: DampedDragTrackingMode = DampedDragTrackingMode.SPRING,
    ) =
        DampedDragAnimation(
            animationScope = scope,
            initialValue = 0f,
            valueRange = range,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.2f,
            useMiuixIndicatorMotion = useMiuixIndicatorMotion,
            trackingMode = trackingMode,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )

    @Test
    fun `glass indicator follows spring even when native control requests direct tracking`() = runTest {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val animation = createAnimation(
                this,
                useMiuixIndicatorMotion = true,
                trackingMode = DampedDragTrackingMode.DIRECT,
            )
            animation.updateValue(2f)
            assertEquals(2f, animation.targetValue)
            assertEquals(0f, animation.value)
            repeat(4) { frame ->
                testScheduler.runCurrent()
                clock.sendFrame(frame * 16_000_000L)
                testScheduler.runCurrent()
            }
            assertTrue(animation.value > 0f && animation.value < 2f)
            repeat(120) { frame ->
                testScheduler.runCurrent()
                clock.sendFrame((frame + 4) * 16_000_000L)
                testScheduler.runCurrent()
            }
            assertEquals(2f, animation.value, 0.001f)
        }
    }

    @Test
    fun `glass release at rest does not wait for full bloom even for a single item`() = runTest {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val animation = createAnimation(this, 0f..0f, useMiuixIndicatorMotion = true)
            animation.press()
            animation.release()
            var peak = 1f
            repeat(120) { frame ->
                testScheduler.runCurrent()
                clock.sendFrame(frame * 16_000_000L)
                testScheduler.runCurrent()
                peak = maxOf(peak, animation.scaleX)
            }
            assertTrue(peak < 1.2f)
            assertEquals(1f, animation.scaleX, 0.001f)
            assertEquals(1f, animation.scaleY, 0.001f)
            assertEquals(0f, animation.pressProgress, 0.001f)
        }
    }

    @Test
    fun `tap visibly enlarges before settling back`() = runTest {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val animation = createAnimation(this)
            animation.animateToValue(1f)
            var peak = 1f
            repeat(240) { frame ->
                testScheduler.runCurrent()
                clock.sendFrame(frame * 16_000_000L)
                testScheduler.runCurrent()
                peak = maxOf(peak, animation.scaleX)
            }
            assertTrue(peak > 1.05f)
            assertEquals(1f, animation.value, 0.001f)
            assertEquals(1f, animation.scaleX, 0.001f)
            assertEquals(1f, animation.scaleY, 0.001f)
            assertEquals(0f, animation.pressProgress, 0.001f)
        }
    }

    @Test
    fun `rapid reversal and drag release settle without stale scale`() = runTest {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val animation = createAnimation(this)
            animation.animateToValue(2f)
            repeat(300) { frame ->
                if (frame == 5) animation.animateToValue(0f)
                if (frame == 10) {
                    animation.press()
                    animation.updateValue(1.4f)
                }
                if (frame == 15) {
                    animation.animateToValue(1f, animatePress = false)
                    animation.release()
                }
                testScheduler.runCurrent()
                clock.sendFrame(frame * 16_000_000L)
                testScheduler.runCurrent()
            }
            assertEquals(1f, animation.value, 0.001f)
            assertEquals(1f, animation.scaleX, 0.001f)
            assertEquals(1f, animation.scaleY, 0.001f)
            assertEquals(0f, animation.pressProgress, 0.001f)
        }
    }

    @Test
    fun `same target in single item range releases without waiting forever`() = runTest {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val animation = createAnimation(this, 0f..0f)
            animation.animateToValue(0f)
            var peak = 1f
            repeat(240) { frame ->
                testScheduler.runCurrent()
                clock.sendFrame(frame * 16_000_000L)
                testScheduler.runCurrent()
                peak = maxOf(peak, animation.scaleX)
            }
            assertTrue(peak > 1.05f)
            assertEquals(1f, animation.scaleX, 0.001f)
            assertEquals(0f, animation.pressProgress, 0.001f)
        }
    }

    @Test
    fun `requested target changes synchronously for delta accumulation`() {
        val dispatcher = StandardTestDispatcher()
        val animation = DampedDragAnimation(
            animationScope = TestScope(dispatcher),
            initialValue = 1f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )

        animation.updateValue(0f)

        // Do not advance the dispatcher: the next pointer event must already accumulate from the
        // newest requested target even though the spring has not advanced a frame yet.
        assertEquals(0f, animation.targetValue)
    }

    @Test
    fun `repeated glass target does not restart position or deformation`() = runTest {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val reference = createAnimation(this, useMiuixIndicatorMotion = true)
            val repeated = createAnimation(this, useMiuixIndicatorMotion = true)
            reference.updateValue(2f)
            repeated.updateValue(2f)
            repeat(120) { frame ->
                repeated.updateValue(2f)
                testScheduler.runCurrent()
                clock.sendFrame(frame * 16_000_000L)
                testScheduler.runCurrent()
                assertEquals(reference.value, repeated.value, 0.0001f)
                assertEquals(reference.velocity, repeated.velocity, 0.0001f)
            }
            assertEquals(2f, repeated.value, 0.001f)
            assertEquals(0f, repeated.velocity, 0.001f)
        }
    }

    @Test
    fun `pager follow takes over a running glass spring and stops requesting frames`() = runTest {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val animation = createAnimation(this, useMiuixIndicatorMotion = true)
            animation.animateToValue(2f)
            repeat(8) { frame ->
                testScheduler.runCurrent()
                clock.sendFrame(frame * 16_000_000L)
                testScheduler.runCurrent()
            }

            animation.followPagerPosition(0.5f)
            assertEquals(0.5f, animation.value)
            assertEquals(0.5f, animation.targetValue)
            assertEquals(1f, animation.pressProgress, 0.001f)
            assertEquals(1.2f, animation.scaleX, 0.001f)
            assertEquals(1.2f, animation.scaleY, 0.001f)

            animation.followPagerPosition(1f)
            testScheduler.runCurrent()
            repeat(8) { frame ->
                clock.sendFrame((frame + 8) * 16_000_000L)
                testScheduler.runCurrent()
            }
            assertEquals(1f, animation.value)
            assertEquals(0f, animation.pressProgress, 0.001f)
            assertEquals(1f, animation.scaleX, 0.001f)
            assertEquals(1f, animation.scaleY, 0.001f)
            assertTrue(!clock.hasAwaiters)

            animation.updateValue(2f)
            repeat(180) { frame ->
                testScheduler.runCurrent()
                clock.sendFrame((frame + 16) * 16_000_000L)
                testScheduler.runCurrent()
            }
            assertEquals(2f, animation.value, 0.001f)
            assertEquals(0f, animation.velocity, 0.001f)
            assertTrue(!clock.hasAwaiters)
        }
    }

    @Test
    fun `direct tracking takes ownership before dispatcher advances`() {
        val dispatcher = StandardTestDispatcher()
        val animation = DampedDragAnimation(
            animationScope = TestScope(dispatcher),
            initialValue = 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            trackingMode = DampedDragTrackingMode.DIRECT,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )

        animation.updateValue(1f)

        // No scheduler advance: a high-frequency next pointer event must not be able to cancel a
        // still-pending position mutation before it has taken ownership of the Animatable.
        assertEquals(1f, animation.value)
        assertEquals(1f, animation.targetValue)
    }
}
