@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.playback.session.PlayerHistorySessions
import com.android.purebilibili.feature.video.viewmodel.resolveSponsorBlockSkipTargetPositionMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PlaybackFirstFrameHandoffEmulatorTest {
    @Test
    fun firstReturnToARetainedPlayerKeepsItsRealFrameWithoutReloading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<PlayerParityTestActivity>(
            Intent(context, PlayerParityTestActivity::class.java)
        )
        var state: VideoPlayerState? = null
        var pageScope: CoroutineScope? = null
        val replacements = AtomicInteger()
        val rebuffers = AtomicInteger()
        try {
            scenario.onActivity { activity ->
                pageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                state = VideoPlayerState(activity, activity.player, checkNotNull(pageScope))
                state!!.attachPlayerListeners()
                activity.player.repeatMode = Player.REPEAT_MODE_OFF
                activity.player.setMediaItem(MediaItem.Builder()
                    .setUri("asset:///player_speed_video.mp4").setMediaId("handoff:first").build())
                activity.player.prepare()
                activity.player.play()
            }
            await(scenario) { activity ->
                state!!.hasRenderedFirstFrame.value && activity.player.isPlaying &&
                    activity.renderedFrames.get() > 2 && !activity.player.isLoading
            }
            var beforeReturnFrames = 0L
            scenario.onActivity { activity ->
                activity.player.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        replacements.incrementAndGet()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_BUFFERING) rebuffers.incrementAndGet()
                    }
                })
                // Simulate leaving detail for the playing bar: its scope and diagnostic
                // listeners are gone, while the same player continues rendering.
                state!!.release()
                pageScope!!.cancel()
                beforeReturnFrames = activity.renderedFrames.get()
            }
            await(scenario) { it.renderedFrames.get() > beforeReturnFrames + 2 }
            scenario.onActivity { activity ->
                pageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                state = VideoPlayerState(activity, activity.player, checkNotNull(pageScope))
                state!!.attachPlayerListeners()
                assertSame(activity.player, state!!.player)
                assertTrue("First re-entry lost the retained media's real frame", state!!.hasRenderedFirstFrame.value)
                assertTrue(activity.player.isPlaying)
                assertEquals("Re-entry replaced the media", 0, replacements.get())
                assertEquals("Re-entry interrupted playback", 0, rebuffers.get())
                // A new media on that same player must not inherit its previous frame.
                activity.player.stop()
                activity.player.setMediaItem(MediaItem.Builder()
                    .setUri("asset:///player_speed_video.mp4").setMediaId("handoff:second").build())
            }
            await(scenario) { !state!!.hasRenderedFirstFrame.value }
            scenario.onActivity { activity ->
                assertFalse(state!!.hasRenderedFirstFrame.value)
                activity.player.prepare()
                activity.player.play()
            }
            await(scenario) { state!!.hasRenderedFirstFrame.value && it.player.isPlaying }
        } finally {
            scenario.onActivity { state?.release(); pageScope?.cancel() }
            scenario.close()
        }
    }

    @Test
    fun preparedProgressIsNotHistoryButPlayedPausedMediaSurvivesReclaim() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<PlayerParityTestActivity>(
            Intent(context, PlayerParityTestActivity::class.java)
        )
        try {
            scenario.onActivity { activity ->
                activity.player.stop()
                PlayerHistorySessions.forPlayer(activity.player).bindMedia("BVhistory", 11L)
                activity.player.setMediaItem(MediaItem.fromUri("asset:///player_speed_video.mp4"))
                activity.player.playWhenReady = false
                activity.player.prepare()
            }
            await(scenario) { it.player.playbackState == Player.STATE_READY }
            scenario.onActivity { activity ->
                val player = activity.player
                val history = PlayerHistorySessions.forPlayer(player)
                assertNull(history.captureProgress("BVhistory", 11L, 0L, player.duration, false))
                player.seekTo(player.duration / 3)
                assertNull(history.captureProgress("BVhistory", 11L, player.currentPosition, player.duration, false))
                player.play()
            }
            await(scenario) { it.player.isPlaying }
            scenario.onActivity { activity ->
                val player = activity.player
                player.pause()
                val history = PlayerHistorySessions.forPlayer(player)
                history.bindMedia("BVhistory", 11L, preserveStarted = true)
                assertNotNull(history.captureProgress("BVhistory", 11L, player.currentPosition, player.duration, false))
                // A prepared next part must not inherit the previous part's playback.
                history.bindMedia("BVhistory", 22L)
                player.stop()
                player.setMediaItem(MediaItem.fromUri("asset:///player_speed_video.mp4"))
                player.prepare()
            }
            await(scenario) { it.player.playbackState == Player.STATE_READY }
            scenario.onActivity { activity ->
                val player = activity.player
                assertNull(PlayerHistorySessions.forPlayer(player)
                    .captureProgress("BVhistory", 22L, player.duration / 2, player.duration, false))
                player.play()
            }
            await(scenario) { it.player.isPlaying }
            scenario.onActivity { activity ->
                activity.player.pause()
                val history = PlayerHistorySessions.forPlayer(activity.player)
                history.bindMedia("BVhistory", 22L, preserveStarted = true)
                assertTrue(history.canWriteHistory("BVhistory", 22L))
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun terminalSponsorSeekFinishesExactlyOnceWithoutReturningToTail() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<PlayerParityTestActivity>(
            Intent(context, PlayerParityTestActivity::class.java)
        )
        val endedEvents = AtomicInteger()
        try {
            scenario.onActivity { activity ->
                activity.player.stop()
                activity.player.repeatMode = Player.REPEAT_MODE_OFF
                activity.player.setMediaItem(MediaItem.fromUri("asset:///player_speed_video.mp4"))
                activity.player.prepare()
                activity.player.play()
            }
            await(scenario) { it.player.isPlaying && it.player.duration > 0 }
            scenario.onActivity { activity ->
                val player = activity.player
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) endedEvents.incrementAndGet()
                    }
                })
                player.seekTo(resolveSponsorBlockSkipTargetPositionMs(player.duration + 5_000L, player.duration))
            }
            await(scenario) { it.player.playbackState == Player.STATE_ENDED }
            SystemClock.sleep(600)
            scenario.onActivity { activity ->
                assertEquals(Player.STATE_ENDED, activity.player.playbackState)
                assertEquals(1, endedEvents.get())
                assertTrue(activity.player.currentPosition >= activity.player.duration - 50L)
            }
        } finally {
            scenario.close()
        }
    }

    private fun await(
        scenario: ActivityScenario<PlayerParityTestActivity>,
        predicate: (PlayerParityTestActivity) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        do {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Retained player did not reach the expected rendering state")
    }
}
