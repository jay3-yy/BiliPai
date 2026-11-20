package com.android.purebilibili.navigation3.predictiveback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCardBackCompletionPolicyTest {
    @Test
    fun `floating card lands after rotating away from its furthest position`() {
        assertTrue(VideoCardBackCompletionPolicy.shouldCommit(0.50f, 0.95f, -3f))
    }

    @Test
    fun `explicit expansion back toward detail cancels even with neutral release`() {
        assertFalse(VideoCardBackCompletionPolicy.shouldCommit(0.30f, 0.95f, 0f))
    }

    @Test
    fun `strong reverse motion before floating mode retains original behavior`() {
        assertFalse(VideoCardBackCompletionPolicy.shouldCommit(0.40f, 0.45f, -2f))
    }

    @Test
    fun `neutral vertical lift respects platform completion before floating mode`() {
        assertTrue(VideoCardBackCompletionPolicy.shouldCommit(0.20f, 0.20f, 0f))
    }

    @Test
    fun `invalid samples cannot cause an unintended pop`() {
        assertFalse(VideoCardBackCompletionPolicy.shouldCommit(Float.NaN, 0.95f, 0f))
        assertFalse(VideoCardBackCompletionPolicy.shouldCommit(0.50f, 0.95f, Float.NaN))
    }
}
