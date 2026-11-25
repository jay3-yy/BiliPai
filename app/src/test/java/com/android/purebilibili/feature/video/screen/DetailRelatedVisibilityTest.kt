package com.android.purebilibili.feature.video.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRelatedVisibilityTest {
    @Test
    fun `unread preference does not flash recommendations`() {
        val state = resolveDetailRelatedVisibility(null)
        assertEquals(DetailRelatedVisibility.WAITING_FOR_PREFERENCE, state)
        assertFalse(state.isVisible())
    }

    @Test
    fun `disabling the preference hides the whole related block`() {
        val state = resolveDetailRelatedVisibility(false)
        assertEquals(DetailRelatedVisibility.EMPTY, state)
        assertFalse(state.isVisible())
    }

    @Test
    fun `enabling the preference keeps normal recommendation rendering`() {
        val state = resolveDetailRelatedVisibility(true)
        assertEquals(DetailRelatedVisibility.VISIBLE, state)
        assertTrue(state.isVisible())
    }
}
