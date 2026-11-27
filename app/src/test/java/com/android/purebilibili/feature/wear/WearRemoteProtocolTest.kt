package com.android.purebilibili.feature.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearRemoteProtocolTest {

    @Test
    fun parsesKnownAndUnknownCommands() {
        assertEquals(WearRemoteCommand.TOGGLE_PLAY, WearRemoteCommand.fromWire("toggle_play"))
        assertEquals(WearRemoteCommand.CYCLE_SPEED, WearRemoteCommand.fromWire(" cycle_speed "))
        assertNull(WearRemoteCommand.fromWire("invalid"))
        assertNull(WearRemoteCommand.fromWire(null))
    }

    @Test
    fun cyclesPlaybackSpeedDeterministically() {
        assertEquals(1.25f, resolveNextWearPlaybackSpeed(1.0f), 0.001f)
        assertEquals(1.5f, resolveNextWearPlaybackSpeed(1.25f), 0.001f)
        assertEquals(2.0f, resolveNextWearPlaybackSpeed(1.5f), 0.001f)
        assertEquals(1.0f, resolveNextWearPlaybackSpeed(2.0f), 0.001f)
        assertEquals(1.0f, resolveNextWearPlaybackSpeed(0.75f), 0.001f)
    }

    @Test
    fun playbackSnapshotRoundTripsThroughJson() {
        val original = WearPlaybackStateSnapshot(
            title = "测试视频",
            author = "UP主",
            playing = true,
            positionMs = 45_000L,
            durationMs = 180_000L,
            speed = 1.5f,
            canPrevious = true,
            canNext = false,
            hasActiveMedia = true,
            locked = false,
        )
        val restored = WearPlaybackStateSnapshot.fromJsonBytes(original.toJsonBytes())
        assertEquals(original, restored)
    }
}
