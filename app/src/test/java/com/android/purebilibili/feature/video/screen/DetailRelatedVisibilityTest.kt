package com.android.purebilibili.feature.video.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailRelatedVisibilityTest {
    @Test
    fun `unread preference does not flash recommendations`() {
        assertEquals(DetailRelatedVisibility.WAITING_FOR_PREFERENCE, resolveDetailRelatedVisibility(null))
    }

    @Test
    fun `disabling the preference hides the whole related block`() {
        assertEquals(DetailRelatedVisibility.EMPTY, resolveDetailRelatedVisibility(false))
    }

    @Test
    fun `enabling the preference keeps normal recommendation rendering`() {
        assertEquals(DetailRelatedVisibility.VISIBLE, resolveDetailRelatedVisibility(true))
    }
}
