package com.android.purebilibili.core.ui.popup

import androidx.compose.runtime.MonotonicFrameClock
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
class AppGlassPopupBackStateTest {
    private val card = Rect(40f, 300f, 340f, 700f)
    private val anchor = Rect(280f, 220f, 340f, 280f)
    private val resting = AppGlassPopupFrame(card, 24f, 1f, 1f, 0f)

    @Test
    fun fullGestureProgressCollapsesIntoTheSourceInsteadOfOnlyShrinkingInPlace() {
        val state = AppGlassPopupBackState()
        state.beginGesture()
        state.seek(0.5f)
        val preview = popupPredictiveBackFrame(resting, state.progress, 1f, anchor, true)
        assertEquals(card.center + (anchor.center - card.center) * 0.5f, preview.rect.center)
        assertTrue(preview.rect.width < card.width)
        assertTrue(preview.sourceAlpha > 0f)

        state.seek(1f)
        val collapsed = popupPredictiveBackFrame(resting, state.progress, 1f, anchor, true)
        assertEquals(anchor.center, collapsed.rect.center)
        assertEquals(anchor, collapsed.sourceRect)
        assertEquals(1f, collapsed.sourceAlpha)
        assertEquals(0f, collapsed.surfaceAlpha)
        assertEquals(0f, collapsed.contentAlpha)
    }

    @Test
    fun cancelledGestureRestoresAndAnotherGestureTakesOverFromTheVisibleProgress() = runTest {
        val state = AppGlassPopupBackState()
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(testScheduler.currentTime * 1_000_000L)
            }
        }
        withContext(clock) {
            state.beginGesture()
            state.seek(0.8f)
            state.restore(this, reduced = false)
            advanceTimeBy(80)
            runCurrent()
            val recovering = state.progress
            assertTrue(recovering > 0f && recovering < 0.8f)

            state.beginGesture()
            state.seek(0f)
            assertEquals(recovering, state.progress)
            state.seek(0.5f)
            val takenOver = recovering + (1f - recovering) * 0.5f
            assertEquals(takenOver, state.progress)
            advanceUntilIdle()
            assertEquals(takenOver, state.progress)

            state.restore(this, reduced = false)
            advanceUntilIdle()
            assertEquals(0f, state.progress)
            assertEquals(resting, popupPredictiveBackFrame(resting, state.progress, 1f, anchor, true))
        }
    }

    @Test
    fun reducedMotionCancellationImmediatelyRestoresThePopup() = runTest {
        val state = AppGlassPopupBackState()
        state.beginGesture()
        state.seek(0.7f)
        state.restore(this, reduced = true)
        assertEquals(0f, state.progress)
        assertEquals(resting, popupPredictiveBackFrame(resting, state.progress, 1f, anchor, true))
    }

    @Test
    fun unanchoredGestureFadesAtItsOwnCenterAndClampsSystemProgress() {
        val state = AppGlassPopupBackState()
        state.beginGesture()
        state.seek(2f)
        val frame = popupPredictiveBackFrame(resting, state.progress, 1f, Rect.Zero, false)
        assertEquals(1f, state.progress)
        assertEquals(card.center, frame.rect.center)
        assertEquals(0f, frame.contentAlpha)
        assertEquals(0f, frame.surfaceAlpha)
    }
}
