package com.android.purebilibili.core.ui.popup

import androidx.compose.ui.geometry.Rect
import com.android.purebilibili.core.store.home.GlassMotionStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElasticPopupMotionTest {
    private val card = Rect(60f, 320f, 360f, 740f)
    private val anchor = Rect(300f, 240f, 360f, 300f)

    @Test
    fun changingPreferenceDoesNotChangeAnOpenMenusReturnStyle() {
        val controller = AppGlassChoicePopupController({ _, _, _ -> }, {})
        controller.preferredMotionStyle = GlassMotionStyle.ELASTIC
        controller.show(request(), {})
        val state = controller.morph
        state.card = card
        state.anchor = anchor
        state.advanceTo(state.openDuration)
        controller.preferredMotionStyle = GlassMotionStyle.STANDARD
        controller.requestDismiss()
        assertEquals(GlassMotionStyle.ELASTIC, state.motionStyle)
        controller.finish()
        controller.show(request(), {})
        assertEquals(GlassMotionStyle.STANDARD, state.motionStyle)
    }

    private fun request() = com.android.purebilibili.core.ui.components.AppSingleChoicePopupRequest(
        title = "菜单", options = emptyList(), selectedValue = null,
        anchorBoundsInWindow = anchor, centered = false,
    )

    @Test
    fun openingAndReturnHaveFiniteEndpointsAcrossWindowSizesAndDensities() {
        for (bounds in listOf(card, Rect(8f, 80f, 180f, 360f), Rect(300f, 200f, 740f, 800f))) {
            for (density in listOf(1f, 3f)) {
                fun scaled(rect: Rect) = Rect(rect.left * density, rect.top * density,
                    rect.right * density, rect.bottom * density)
                val state = PopupMorphState()
                state.beginOpen(true, GlassMotionStyle.ELASTIC)
                state.card = scaled(bounds)
                state.anchor = scaled(anchor)
                var start = ElasticPopupMotion.frame(state, 0f, 24f * density, density)
                for (step in 0..130) {
                    val frame = ElasticPopupMotion.frame(state, step * 0.005f, 24f * density, density)
                    assertTrue(frame.rect.width > 0f && frame.rect.height > 0f)
                    assertTrue(frame.radius in 0f..frame.rect.minDimension / 2f)
                    assertTrue(frame.contentAlpha in 0f..1f)
                    assertTrue(frame.sourceAlpha in 0f..1f)
                    assertTrue(frame.lensProgress!! in 0f..1f)
                    start = frame
                }
                assertEquals(state.card, start.rect)
                assertEquals(1f, start.contentAlpha)
                state.advanceTo(state.openDuration)
                state.beginClose(start, start)
                val end = ElasticPopupMotion.frame(state, state.closeDuration, 24f * density, density)
                assertEquals(state.anchor, end.sourceRect)
                assertEquals(1f, end.sourceAlpha)
                assertEquals(0f, end.surfaceAlpha)
                assertEquals(0f, end.contentAlpha)
            }
        }
    }

    @Test
    fun interruptedClosingAndReopeningPreserveTheCurrentFrameAndVelocity() {
        val state = PopupMorphState()
        state.beginOpen(true, GlassMotionStyle.ELASTIC)
        state.card = card
        state.anchor = anchor
        state.advanceTo(0.08f)
        val start = ElasticPopupMotion.frame(state, 0.08f, 24f, 1f)
        val previous = ElasticPopupMotion.frame(state, 0.08f - state.sampleSeconds, 24f, 1f)
        state.beginClose(start, previous)
        assertEquals(start, ElasticPopupMotion.frame(state, 0f, 24f, 1f))
        val turning = ElasticPopupMotion.frame(state, 0.04f, 24f, 1f)
        val beforeTurn = ElasticPopupMotion.frame(state, 0.04f - state.sampleSeconds, 24f, 1f)
        state.advanceTo(0.04f)
        state.reopen(turning, beforeTurn)
        assertEquals(turning, ElasticPopupMotion.frame(state, 0f, 24f, 1f))
        val resumed = ElasticPopupMotion.frame(state, 0.0001f, 24f, 1f)
        val expectedVelocity = (turning.rect.center.x - beforeTurn.rect.center.x) / state.sampleSeconds
        val resumedVelocity = (resumed.rect.center.x - turning.rect.center.x) / 0.0001f
        assertEquals(expectedVelocity, resumedVelocity, 3f)
        assertEquals(card, ElasticPopupMotion.frame(state, state.openDuration, 24f, 1f).rect)
    }
}
