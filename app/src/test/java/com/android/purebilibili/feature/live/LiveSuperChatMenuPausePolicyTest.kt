package com.android.purebilibili.feature.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LiveSuperChatMenuPausePolicyTest {
    @Test
    fun `last menu release compensates the entire pause once`() {
        val policy = LiveSuperChatMenuPausePolicy()
        policy.open(42L, 1_000L, 100L)
        policy.open(42L, 2_000L, 101L)
        assertTrue(policy.isPaused(42L))
        assertEquals(100L, policy.countdownTime(42L, 102L))
        assertEquals(null, policy.close(42L, 3_000L))
        assertTrue(policy.isPaused(42L))
        assertEquals(100L, policy.countdownTime(42L, 104L))
        assertEquals(5L, policy.close(42L, 5_500L))
        assertFalse(policy.isPaused(42L))
        assertEquals(104L, policy.countdownTime(42L, 104L))
        assertEquals(null, policy.close(42L, 6_000L))
    }

    @Test
    fun `room changes clear holds and independent cards keep separate pauses`() {
        val policy = LiveSuperChatMenuPausePolicy()
        policy.open(1L, 1_000L, 100L)
        policy.open(2L, 2_000L, 101L)
        assertEquals(1L, policy.close(2L, 2_100L))
        assertTrue(policy.isPaused(1L))
        policy.clear()
        assertFalse(policy.isPaused(1L))
        assertEquals(null, policy.close(1L, 4_000L))
        policy.open(0L, 1_000L, 100L)
        assertFalse(policy.isPaused(0L))
    }
}
