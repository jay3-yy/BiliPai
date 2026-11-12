package com.android.purebilibili.core.ui.popup

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class PopupElasticReleaseTest {
    @Test
    fun quickDragReleaseKeepsTheCurrentFrameAndDoesNotCompleteAnotherPressLift() = runTest {
        val motion = PopupElasticMotion()
        val base = AppGlassPopupFrame(Rect(0f, 0f, 240f, 360f), 24f, 1f, 1f, 0f)
        val releaseWidths = mutableListOf<Float>()
        var released = false
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                val result = onFrame(testScheduler.currentTime * 1_000_000L)
                Snapshot.sendApplyNotifications()
                if (released) releaseWidths += motion.frame(base, 1f).rect.width
                return result
            }
        }
        withContext(clock) {
            motion.isTracking = true
            motion.retarget(this, pressed = true, pull = Offset(0f, 30f), reduced = false)
            runCurrent()
            advanceTimeBy(48)
            runCurrent()
            val atRelease = motion.frame(base, 1f)
            assertTrue(atRelease.rect.center.y > base.rect.center.y)
            motion.isTracking = false
            released = true
            motion.retarget(this, pressed = false, pull = Offset.Zero, reduced = false)
            runCurrent()
            assertEquals(atRelease, motion.frame(base, 1f))
            advanceUntilIdle()

            val fullPress = popupElasticFrame(base, 1f, Offset.Zero, 1f)
            assertTrue(releaseWidths.isNotEmpty())
            assertTrue(releaseWidths.max() < fullPress.rect.width)
            assertEquals(base, motion.frame(base, 1f))
        }
    }
}
