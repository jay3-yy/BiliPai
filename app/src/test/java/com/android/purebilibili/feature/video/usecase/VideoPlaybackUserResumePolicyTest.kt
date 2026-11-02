package com.android.purebilibili.feature.video.usecase

import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

class VideoPlaybackUserResumePolicyTest {

    @Test
    fun `playPlayerFromUserAction resumes paused ready playback without compatibility seek`() {
        val player = mockk<Player>(relaxed = true)
        every { player.playbackState } returns Player.STATE_READY
        every { player.mediaItemCount } returns 1
        every { player.isPlaying } returns false
        every { player.playWhenReady } returns false

        playPlayerFromUserAction(player)

        verify(exactly = 0) { player.seekTo(any()) }
        verify(exactly = 1) { player.play() }
    }

    @Test
    fun `playPlayerFromUserAction prepares idle player before first double tap resume`() {
        val player = mockk<Player>(relaxed = true)
        every { player.playbackState } returns Player.STATE_IDLE
        every { player.mediaItemCount } returns 1
        every { player.isPlaying } returns false
        every { player.playWhenReady } returns false

        playPlayerFromUserAction(player)

        verify(exactly = 1) { player.prepare() }
        verify(exactly = 1) { player.playWhenReady = true }
        verify(exactly = 1) { player.play() }
    }

    @Test
    fun `togglePlayerPlaybackFromUserAction restarts ended playback from beginning`() {
        val player = mockk<Player>(relaxed = true)
        every { player.playbackState } returns Player.STATE_ENDED
        every { player.mediaItemCount } returns 1
        every { player.currentPosition } returns 216_000L
        every { player.isPlaying } returns false
        every { player.playWhenReady } returns false

        togglePlayerPlaybackFromUserAction(player)

        verify(exactly = 1) { player.seekTo(0L) }
        verify(exactly = 1) { player.play() }
    }

    @Test
    fun `togglePlayerPlaybackFromUserAction resumes immediately after pause intent while isPlaying is stale`() {
        val player = mockk<Player>(relaxed = true)
        every { player.playbackState } returns Player.STATE_READY
        every { player.mediaItemCount } returns 1
        every { player.isPlaying } returns true
        every { player.playWhenReady } returns false

        togglePlayerPlaybackFromUserAction(player)

        verify(exactly = 0) { player.pause() }
        verify(exactly = 1) { player.playWhenReady = true }
        verify(exactly = 1) { player.play() }
    }

    @Test
    fun `togglePlayerPlaybackFromUserAction kicks silent ready playback instead of pausing first`() {
        val player = mockk<Player>(relaxed = true)
        every { player.playbackState } returns Player.STATE_READY
        every { player.mediaItemCount } returns 1
        every { player.isPlaying } returns false
        every { player.playWhenReady } returns true

        togglePlayerPlaybackFromUserAction(player)

        verify(exactly = 0) { player.pause() }
        verify(exactly = 1) { player.play() }
    }


    @Test
    fun `applyPlaybackIntentAfterSourceChange replays source swaps when autoplay should continue`() {
        val player = mockk<Player>(relaxed = true)

        applyPlaybackIntentAfterSourceChange(
            player = player,
            playWhenReady = true
        )

        verify(exactly = 1) { player.playWhenReady = true }
        verify(exactly = 1) { player.play() }
    }

    @Test
    fun `applyPlaybackIntentAfterSourceChange keeps paused transitions paused`() {
        val player = mockk<Player>(relaxed = true)

        applyPlaybackIntentAfterSourceChange(
            player = player,
            playWhenReady = false
        )

        verify(exactly = 1) { player.playWhenReady = false }
        verify(exactly = 0) { player.play() }
    }
}
