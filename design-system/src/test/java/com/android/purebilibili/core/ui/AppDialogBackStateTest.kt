package com.android.purebilibili.core.ui

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDialogBackStateTest {
    @Test
    fun newGestureInterruptsRecoveryWithoutJumpingOrAcceptingOldFrames() = runBlocking {
        val frames = Channel<Long>(Channel.UNLIMITED)
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.receive())
        }
        val state = AppDialogBackState()
        state.beginGesture()
        state.seek(1f)
        val preview = state.progress
        val recovery = launch(clock, start = CoroutineStart.UNDISPATCHED) { state.settle(0f, false) }
        state.settling = recovery
        frames.send(0L)
        yield()
        frames.send(70_000_000L)
        yield()
        val interrupted = state.progress
        assertTrue(interrupted > 0f && interrupted < preview)

        state.beginGesture()
        state.seek(0f)
        assertEquals(interrupted, state.progress)
        frames.send(180_000_000L)
        yield()
        assertEquals(interrupted, state.progress)
        assertTrue(recovery.isCancelled)
        state.seek(0.5f)
        assertTrue(state.progress > interrupted)
        recovery.join()
    }

    @Test
    fun resetCancelsRecoveryAndLeavesTheNextPresentationAtRest() {
        val state = AppDialogBackState()
        state.beginGesture()
        state.seek(0.8f)
        val recovery = kotlinx.coroutines.Job()
        state.settling = recovery
        state.reset()
        assertTrue(recovery.isCancelled)
        state.beginGesture()
        state.seek(0f)
        assertEquals(0f, state.progress)
    }
}
