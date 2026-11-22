package com.android.purebilibili.feature.profile

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileAvatarExpansionTest {
    @Test
    fun `pulling a closed avatar uses half distance and returns physical consumption`() {
        val state = ProfileAvatarExpansionState()
        assertEquals(100f, state.dragBy(100f, 200f))
        assertEquals(50f, state.offsetPx)
        assertEquals(0.25f, state.fraction(200f))
        // Only the distance needed to finish closing is consumed; the list gets the rest.
        assertEquals(-100f, state.dragBy(-300f, 200f))
        assertEquals(0f, state.offsetPx)
        assertEquals(0f, state.dragBy(-20f, 200f))
    }

    @Test
    fun `expanded avatar drags at full speed and reset restores closed damping`() = runTest {
        val state = ProfileAvatarExpansionState()
        state.settle(true, 200f, 0f, 1f, motion = false)
        assertTrue(state.isPulledDown)
        assertEquals(-50f, state.dragBy(-50f, 200f))
        assertEquals(150f, state.offsetPx)
        state.reset()
        assertFalse(state.isPulledDown)
        state.dragBy(50f, 200f)
        assertEquals(25f, state.offsetPx)
    }

    @Test
    fun `a window too narrow for expansion consumes no pull and cannot stay expanded`() = runTest {
        val state = ProfileAvatarExpansionState()
        assertEquals(0f, state.dragBy(300f, 0f))
        state.settle(true, 0f, 3000f, 3f, motion = false)
        assertFalse(state.isPulledDown)
        assertEquals(0f, state.fraction(0f))
    }

    @Test
    fun `remeasuring the header preserves its expanded state`() = runTest {
        val state = ProfileAvatarExpansionState()
        state.settle(true, 200f, 0f, 1f, motion = false)
        state.updateExpandedRange(0f)
        assertEquals(0f, state.visualOffsetPx)
        state.updateExpandedRange(240f)
        assertEquals(1f, state.fraction(240f))
        assertEquals(240f, state.visualOffsetPx)
        state.reset()
        state.updateExpandedRange(200f)
        assertEquals(0f, state.visualOffsetPx)
    }

    @Test
    fun `extra pull stretches a fully expanded photo before upward motion closes it`() = runTest {
        val state = ProfileAvatarExpansionState()
        state.settle(true, 200f, 0f, 1f, motion = false)
        assertEquals(100f, state.dragBy(100f, 200f))
        assertEquals(200f, state.offsetPx)
        assertEquals(50f, state.overPullPx)
        assertEquals(250f, state.visualOffsetPx)
        assertEquals(1f, state.fraction(200f))
        // Consume the 100 physical pixels of stretch and 200 pixels of header.
        // The remaining 100 pixels of this drag belong to the list.
        assertEquals(-300f, state.dragBy(-400f, 200f))
        assertEquals(0f, state.visualOffsetPx)
        state.settle(false, 200f, 0f, 1f, motion = false)
        assertEquals(0f, state.overPullPx)
    }

    @Test
    fun `upstream duration uses remaining distance and density scaled velocity caps`() {
        assertEquals(250, profileAvatarExpandDuration(0f, 1f, 0f, 1f))
        assertEquals(125, profileAvatarExpandDuration(0.5f, 1f, 0f, 1f))
        assertEquals(
            profileAvatarExpandDuration(0.33f, 1f, 2000f, 1f),
            profileAvatarExpandDuration(0.33f, 1f, 6000f, 3f),
        )
        assertEquals(
            profileAvatarExpandDuration(0.33f, 1f, -2000f, 1f),
            profileAvatarExpandDuration(0.33f, 1f, -9000f, 1f),
        )
    }
}
