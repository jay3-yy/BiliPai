package com.android.purebilibili.core.ui.popup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PopupScrollDragStateTest {
    private val bounds = Rect(40f, 100f, 340f, 600f)
    private val input = NestedScrollSource.UserInput

    private fun drag(interactive: Boolean = false, origin: Offset = bounds.center) =
        PopupScrollDragState(8f) {}.apply {
            begin(origin, bounds, interactive, feedbackStarted = !interactive)
        }

    @Test
    fun consumedInnerListScrollingDoesNotStretchEitherKindOfPanel() {
        for (interactive in listOf(false, true)) {
            val state = drag(interactive)
            val consumed = state.onPostScroll(Offset(0f, -60f), Offset.Zero, input)
            state.move(bounds.center + Offset(0f, -60f))
            assertEquals(Offset.Zero, consumed)
            assertEquals(Offset.Zero, state.pull)
            assertFalse(state.canSelect)
        }
    }

    @Test
    fun boundaryPullSurvivesStationaryEventsAndStartsContinuouslyAfterSlop() {
        val state = drag()
        assertEquals(Offset(0f, 7.9f), state.onPostScroll(Offset.Zero, Offset(0f, 7.9f), input))
        assertEquals(Offset.Zero, state.pull)
        state.onPostScroll(Offset.Zero, Offset(0f, 0.2f), input)
        assertEquals(0.1f, state.pull.y, 0.0001f)
        state.onPostScroll(Offset.Zero, Offset(0f, 20f), input)
        val held = state.pull
        state.onPostScroll(Offset.Zero, Offset.Zero, input)
        state.move(bounds.center)
        assertEquals(held, state.pull)
    }

    @Test
    fun crossingPanelEdgeKeepsAccumulatedPullWithoutDoubleCounting() {
        val origin = Offset(bounds.center.x, bounds.bottom - 50f)
        val state = drag(origin = origin)
        state.onPostScroll(Offset.Zero, Offset(0f, 48f), input)
        state.move(Offset(origin.x, bounds.bottom - 0.1f))
        val inside = state.pull
        state.move(Offset(origin.x, bounds.bottom + 0.1f))
        assertEquals(inside, state.pull)
        state.move(Offset(origin.x, bounds.bottom + 60f))
        assertEquals(52f, state.pull.y, 0.001f)
        state.move(Offset(origin.x, bounds.bottom - 0.1f))
        assertEquals(inside, state.pull)
    }

    @Test
    fun reversalReleasesStretchBeforePassingRemainderToList() {
        for (direction in listOf(-1f, 1f)) {
            val state = drag(interactive = true)
            state.onPostScroll(Offset.Zero, Offset(0f, 40f * direction), input)
            assertEquals(32f * direction, state.pull.y, 0.001f)
            assertEquals(Offset(0f, -10f * direction), state.onPreScroll(Offset(0f, -10f * direction), input))
            assertEquals(22f * direction, state.pull.y, 0.001f)
            val released = state.onPreScroll(Offset(0f, -50f * direction), input)
            assertEquals(Offset(0f, -30f * direction), released)
            assertEquals(Offset.Zero, state.pull)
            // 剩下 20px 应交给子列表，本状态不再截走位移。
            assertEquals(Offset.Zero, state.onPreScroll(Offset(0f, -20f * direction), input))
        }
    }

    @Test
    fun horizontalControlsAndEndedGesturesStopPanelConsumption() {
        val state = drag(interactive = true)
        state.move(bounds.center + Offset(30f, 2f))
        assertTrue(state.blocked)
        assertEquals(Offset.Zero, state.onPostScroll(Offset.Zero, Offset(0f, 40f), input))
        assertEquals(Offset.Zero, state.pull)

        state.end()
        state.move(Offset(bounds.right + 100f, bounds.bottom + 100f))
        assertEquals(Offset.Zero, state.onPostScroll(Offset.Zero, Offset(0f, 40f), input))
        assertEquals(Offset.Zero, state.pull)
        assertFalse(state.canSelect)
    }

    @Test
    fun flingsDoNotStartOrChangeFingerDrivenStretch() {
        val state = drag(interactive = true)
        state.onPostScroll(Offset.Zero, Offset(0f, 40f), input)
        val held = state.pull
        assertEquals(Offset.Zero, state.onPostScroll(Offset.Zero, Offset(0f, 90f), NestedScrollSource.SideEffect))
        assertEquals(held, state.pull)
    }

    @Test
    fun releaseConsumesOutwardVelocityButAllowsInwardListFling() = runTest {
        for (direction in listOf(-1f, 1f)) {
            val outward = drag(interactive = true)
            outward.onPostScroll(Offset.Zero, Offset(0f, 40f * direction), input)
            outward.end()
            assertEquals(Offset.Zero, outward.pull)
            assertEquals(
                Velocity(0f, 1200f * direction),
                outward.onPreFling(Velocity(200f, 1200f * direction)),
            )

            val inward = drag(interactive = true)
            inward.onPostScroll(Offset.Zero, Offset(0f, 40f * direction), input)
            inward.end()
            assertEquals(Velocity.Zero, inward.onPreFling(Velocity(200f, -1200f * direction)))
        }
    }

    @Test
    fun remainingListFlingStopsAtPopupWithoutTakingHorizontalVelocity() = runTest {
        val state = drag(interactive = true)
        state.onPostScroll(Offset(0f, -60f), Offset.Zero, input)
        state.end()
        assertEquals(Velocity.Zero, state.onPreFling(Velocity(100f, -1200f)))
        assertEquals(Velocity(0f, -300f), state.onPostFling(Velocity(0f, -900f), Velocity(100f, -300f)))
        assertEquals(Offset.Zero, state.pull)
        assertEquals(Velocity.Zero, state.onPostFling(Velocity.Zero, Velocity(100f, -300f)))
    }

    @Test
    fun flingCompletingBeforeHostReleaseDoesNotLeaveAStaleFling() = runTest {
        val state = drag(interactive = true)
        state.onPostScroll(Offset.Zero, Offset(0f, 40f), input)
        assertEquals(Velocity(0f, 1000f), state.onPreFling(Velocity(0f, 1000f)))
        state.onPostFling(Velocity.Zero, Velocity.Zero)
        state.end()
        assertEquals(Velocity.Zero, state.onPostFling(Velocity.Zero, Velocity(0f, 200f)))
    }

    @Test
    fun cancellationDoesNotClaimTheNextFling() = runTest {
        val state = drag(interactive = true)
        state.onPostScroll(Offset.Zero, Offset(0f, 40f), input)
        state.end(cancelled = true)
        assertEquals(Velocity.Zero, state.onPreFling(Velocity(0f, 1000f)))
        assertEquals(Velocity.Zero, state.onPostFling(Velocity.Zero, Velocity(0f, 200f)))
    }

}
