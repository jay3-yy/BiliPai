package com.android.purebilibili.core.ui.components

import coil3.decode.DataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeImageRevealStateTest {
    @Test
    fun cachedAndLocalResultsAreImmediatelyVisible() {
        for (source in listOf(DataSource.MEMORY_CACHE, DataSource.MEMORY, DataSource.DISK)) {
            val state = HomeImageRevealState()
            state.onSuccess(source, motionAllowed = true)
            assertFalse(state.pending)
            assertEquals(1f, state.alpha)
        }
    }

    @Test
    fun hidingAnImageFinishesItsRevealAndReturningDoesNotReplayIt() = runTest {
        val state = HomeImageRevealState()
        state.onSuccess(DataSource.NETWORK, motionAllowed = true)
        assertTrue(state.pending)
        assertEquals(0f, state.alpha)
        state.finish(motionAllowed = false)
        assertFalse(state.pending)
        assertEquals(1f, state.alpha)
        state.onSuccess(DataSource.NETWORK, motionAllowed = true)
        assertFalse(state.pending)
    }

    @Test
    fun networkResultWithoutMotionPermissionDoesNotWaitForVisibility() {
        val state = HomeImageRevealState()
        state.onSuccess(DataSource.NETWORK, motionAllowed = false)
        assertFalse(state.pending)
        assertEquals(1f, state.alpha)
        state.onSuccess(DataSource.NETWORK, motionAllowed = true)
        assertFalse(state.pending)
    }
}
