package com.android.purebilibili.feature.video.ui.overlay

import androidx.media3.common.Player
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerProgressObservationTest {
    private val player = mockk<Player>()
    private val listener = slot<Player.Listener>()
    private var position = 1_000L
    private var buffered = 10_000L
    private var playing = false
    private var playbackState = Player.STATE_READY
    private val events = mockk<Player.Events>()

    init {
        every { player.addListener(capture(listener)) } just Runs
        every { player.removeListener(any()) } just Runs
        every { player.currentPosition } answers { position }
        every { player.bufferedPosition } answers { buffered }
        every { player.duration } returns 60_000L
        every { player.isPlaying } answers { playing }
        every { player.playbackState } answers { playbackState }
    }

    @Test
    fun pausedPlayback_waitsWithoutPolling_andRefreshesSeekBufferAndEndEvents() = runTest {
        val snapshots = mutableListOf<PlayerProgress>()
        val job = launch {
            observeVideoPlayerProgress(player, 60_000L, 200L) { snapshots += it }
        }
        runCurrent()
        assertEquals(1, snapshots.size)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(1, snapshots.size)

        position = 25_000L
        buffered = 40_000L
        listener.captured.onEvents(player, events)
        runCurrent()
        assertEquals(PlayerProgress(25_000L, 60_000L, 40_000L), snapshots.last())

        playbackState = Player.STATE_ENDED
        listener.captured.onEvents(player, events)
        runCurrent()
        assertEquals(PlayerProgress(60_000L, 60_000L, 60_000L), snapshots.last())
        job.cancelAndJoin()
    }

    @Test
    fun playbackEvents_stopAndResumePeriodicSampling_withoutWaitingForOldInterval() = runTest {
        playing = true
        val snapshots = mutableListOf<PlayerProgress>()
        val job = launch {
            observeVideoPlayerProgress(player, 60_000L, 200L) { snapshots += it }
        }
        runCurrent()
        position = 1_200L
        advanceTimeBy(200L)
        runCurrent()
        assertEquals(1_200L, snapshots.last().current)
        assertEquals(2, snapshots.size)

        playing = false
        listener.captured.onEvents(player, events)
        runCurrent()
        val pausedCount = snapshots.size
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(pausedCount, snapshots.size)

        playing = true
        position = 2_000L
        listener.captured.onEvents(player, events)
        runCurrent()
        assertEquals(2_000L, snapshots.last().current)
        advanceTimeBy(200L)
        runCurrent()
        assertEquals(pausedCount + 2, snapshots.size)
        job.cancelAndJoin()
    }

    @Test
    fun cancellingVisibleOwner_removesListener_andNewOwnerTakesFreshSnapshot() = runTest {
        playing = true
        val snapshots = mutableListOf<PlayerProgress>()
        val firstJob = launch {
            observeVideoPlayerProgress(player, 60_000L, 200L) { snapshots += it }
        }
        runCurrent()
        val oldListener = listener.captured
        firstJob.cancelAndJoin()
        verify(exactly = 1) { player.removeListener(oldListener) }
        position = 8_000L
        oldListener.onEvents(player, events)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(1, snapshots.size)

        val secondJob = launch {
            observeVideoPlayerProgress(player, 60_000L, 200L) { snapshots += it }
        }
        runCurrent()
        assertEquals(8_000L, snapshots.last().current)
        assertEquals(2, snapshots.size)
        secondJob.cancelAndJoin()
        verify(exactly = 2) { player.removeListener(any()) }
    }

    @Test
    fun pendingSeek_canKeepSamplingUntilOwnerClearsRecoveryState() = runTest {
        val snapshots = mutableListOf<PlayerProgress>()
        val job = launch {
            observeVideoPlayerProgress(player, 60_000L, 200L, pollWhilePaused = true) {
                snapshots += it
            }
        }
        runCurrent()
        position = 12_000L
        advanceTimeBy(200L)
        runCurrent()
        assertEquals(12_000L, snapshots.last().current)
        job.cancelAndJoin()
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(2, snapshots.size)
    }
}
