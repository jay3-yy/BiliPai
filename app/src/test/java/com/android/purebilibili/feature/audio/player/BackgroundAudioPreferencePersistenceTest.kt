package com.android.purebilibili.feature.audio.player

import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.player.PlaylistItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackgroundAudioPreferencePersistenceTest {
    private val running = BackgroundAudioSnapshot(
        queue = listOf(PlaylistItem(bvid = "a", title = "A", cover = "", owner = "")),
        index = 0, positionMs = 12345L, playing = true, speed = 1.25f, accountMid = 7L,
        playMode = PlayMode.REPEAT_ONE, shuffle = true,
        savedPlayMode = PlayMode.SEQUENTIAL, savedShuffle = false,
        backgroundPlaybackOverride = true, backgroundOverrideBvid = "a",
    )

    @Test
    fun `disk checkpoint keeps progress but excludes temporary order and background choice`() {
        val disk = running.forPersistence()
        assertEquals(PlayMode.SEQUENTIAL, disk.playMode)
        assertEquals(false, disk.shuffle)
        assertNull(disk.backgroundPlaybackOverride)
        assertNull(disk.backgroundOverrideBvid)
        assertEquals(running.queue, disk.queue)
        assertEquals(running.index, disk.index)
        assertEquals(running.positionMs, disk.positionMs)
        assertEquals(running.speed, disk.speed)
        assertEquals(PlayMode.REPEAT_ONE, running.playMode)
        assertEquals(true, running.backgroundPlaybackOverride)
        val restored = backgroundAudioJson.decodeFromString(BackgroundAudioSnapshot.serializer(),
            backgroundAudioJson.encodeToString(BackgroundAudioSnapshot.serializer(), disk))
        assertEquals(disk, restored)
    }

    @Test
    fun `live handoff transfers the original sleep schedule and disk checkpoints drop it`() {
        val timers = listOf(
            PlaybackSleepTimer.Countdown(minutes = 30, deadlineMs = 1_900_000L),
            PlaybackSleepTimer.AfterCurrent(bvid = "a", cid = 42L),
        )
        timers.forEach { timer ->
            val live = running.copy(sleepTimer = timer)
            val serialized = backgroundAudioJson.encodeToString(BackgroundAudioSnapshot.serializer(), live)
            val received = backgroundAudioJson.decodeFromString(BackgroundAudioSnapshot.serializer(), serialized)

            assertEquals(timer, received.sleepTimer)
            assertNull(received.forPersistence().sleepTimer)
        }
    }

    @Test
    fun `legacy checkpoints retain the original order when no saved baseline was supplied`() {
        val legacy = running.copy(savedPlayMode = null, savedShuffle = null, backgroundPlaybackOverride = null, backgroundOverrideBvid = null)
        assertEquals(legacy, legacy.forPersistence())
    }
}
