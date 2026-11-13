package com.android.purebilibili.core.ui.popup

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.android.purebilibili.core.store.home.GlassMotionStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PopupMorphFrameTest {
    private val card = Rect(60f, 320f, 360f, 740f)
    private val anchor = Rect(300f, 240f, 360f, 300f)

    private fun state(style: GlassMotionStyle) = PopupMorphState().apply {
        beginOpen(true, style)
        this.card = this@PopupMorphFrameTest.card
        this.anchor = this@PopupMorphFrameTest.anchor
    }

    @Test
    fun renderersShareAFrameUntilTimeOrGeometryChanges() {
        for (style in GlassMotionStyle.entries) {
            val state = state(style)
            state.advanceTo(0.1f)
            val first = state.frame(24f, 1f)
            assertSame(first, state.frame(24f, 1f))
            state.advanceTo(0.2f)
            assertNotEquals(first.rect, state.frame(24f, 1f).rect)
            state.advanceTo(state.openDuration)
            assertEquals(card, state.frame(24f, 1f).rect)
            state.card = card.translate(Offset(20f, 10f))
            assertEquals(state.card, state.frame(24f, 1f).rect)
            assertEquals(36f, state.frame(36f, 2f).radius)
            state.safeBounds = Rect(0f, 0f, 200f, 400f)
            val fitted = state.frame(36f, 2f)
            assertTrue(fitted.rect.right <= 200f && fitted.rect.bottom <= 400f)
        }
    }

    @Test
    fun predictiveBackAndSourceDetachmentUpdateWithoutAdvancingTheClock() {
        for (style in GlassMotionStyle.entries) {
            val state = state(style)
            state.advanceTo(0.2f)
            val resting = state.frame(24f, 1f)
            state.predictiveBack.beginGesture()
            state.predictiveBack.seek(0.5f)
            val preview = state.frame(24f, 1f)
            assertNotEquals(resting.rect, preview.rect)
            state.detachSource()
            assertNotEquals(preview, state.frame(24f, 1f))
            state.predictiveBack.reset()
            assertEquals(0f, state.frame(24f, 1f).sourceAlpha)
        }
    }

    @Test
    fun closingReopeningAndNewSessionDoNotReuseThePreviousTransition() {
        for (style in GlassMotionStyle.entries) {
            val state = state(style)
            state.advanceTo(0.1f)
            val opening = state.frame(24f, 1f)
            state.beginClose(opening, opening)
            val closing = state.frame(24f, 1f)
            state.reopen(closing, closing)
            assertEquals(closing, state.frame(24f, 1f))
            state.beginOpen(false, style)
            state.card = card
            state.advanceTo(state.openDuration)
            assertEquals(card, state.frame(24f, 1f).rect)
            assertEquals(null, state.frame(24f, 1f).sourceRect)
        }
    }

    @Test
    fun pressAndDragUpdateTheSharedFrameWhileOpeningTimeStaysFixed() = runTest {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val state = state(GlassMotionStyle.STANDARD)
            state.advanceTo(state.openDuration)
            val resting = state.frame(24f, 1f)
            state.elastic.isTracking = true
            state.elastic.retarget(this, true, Offset(20f, 10f), reduced = false)
            repeat(30) { frame ->
                testScheduler.runCurrent()
                clock.sendFrame(frame * 16_000_000L)
                testScheduler.runCurrent()
            }
            assertNotEquals(resting.rect, state.frame(24f, 1f).rect)
            state.elastic.stop()
            state.elastic.snapToRest()
            assertEquals(resting, state.frame(24f, 1f))
        }
    }
}
