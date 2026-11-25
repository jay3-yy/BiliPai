package com.android.purebilibili.core.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackProgressManagerTest {
    @Test
    fun `progress entries are isolated across accounts and restored when switching back`() {
        var activeMid = 1001L
        val manager = PlaybackProgressManager(accountMidProvider = { activeMid })

        manager.savePosition(bvid = "BV17x411w7KC", cid = 55L, positionMs = 42_000L, durationMs = 300_000L)
        assertEquals(42_000L, manager.getCachedPosition("BV17x411w7KC", 55L))
        assertEquals(42_000L, manager.getCachedPosition("BV17x411w7KC"))

        activeMid = 2002L
        assertEquals(0L, manager.getCachedPosition("BV17x411w7KC", 55L))
        assertEquals(0L, manager.getCachedPosition("BV17x411w7KC"))
        assertFalse(manager.hasPosition("BV17x411w7KC", 55L))

        manager.savePosition(bvid = "BV17x411w7KC", cid = 55L, positionMs = 95_000L, durationMs = 300_000L)
        assertEquals(95_000L, manager.getCachedPosition("BV17x411w7KC", 55L))

        activeMid = 0L
        assertEquals(0L, manager.getCachedPosition("BV17x411w7KC", 55L))

        activeMid = 1001L
        assertEquals(42_000L, manager.getCachedPosition("BV17x411w7KC", 55L))
        assertTrue(manager.hasPosition("BV17x411w7KC", 55L))
    }

    @Test
    fun `legacy unprefixed entries only fall back for guest and never leak into logged-in accounts`() {
        var activeMid = 0L
        val manager = PlaybackProgressManager(accountMidProvider = { activeMid })
        manager.seedLegacyEntryForTest("BV1legacy#88", 30_000L)
        manager.seedLegacyEntryForTest("BV1legacy", 30_000L)

        assertEquals(30_000L, manager.getCachedPosition("BV1legacy", 88L))

        activeMid = 1001L
        assertEquals(0L, manager.getCachedPosition("BV1legacy", 88L))
        assertEquals(0L, manager.getCachedPosition("BV1legacy"))

        activeMid = 0L
        manager.clearPosition("BV1legacy", 88L)
        assertEquals(0L, manager.getCachedPosition("BV1legacy", 88L))
        assertEquals(0L, manager.getCachedPosition("BV1legacy"))
    }
}
