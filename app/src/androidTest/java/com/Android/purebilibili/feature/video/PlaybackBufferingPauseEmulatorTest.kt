@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.feature.video.screen.handleAudioModePlayPause
import com.android.purebilibili.feature.video.usecase.applyPlaybackButtonUserAction
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

/** Real decoder starvation, not a mocked BUFFERING flag or a network-dependent timeout. */
@RunWith(AndroidJUnit4::class)
class PlaybackBufferingPauseEmulatorTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun initialBufferingCanBePausedAndDoesNotAutoplayWhenDataArrives() {
        assertBufferingCanBePaused(rebuffer = false)
    }

    @Test
    fun rebufferingCanBePausedAndDoesNotAutoplayWhenDataArrives() {
        assertBufferingCanBePaused(rebuffer = true)
    }

    @Test
    fun nonVisualControlsCancelBufferingIntent() {
        assertBufferingCanBePaused(false) {
            applyPlaybackButtonUserAction(it, isShowingPauseIcon = false)
            assertFalse(it.playWhenReady)
            applyPlaybackButtonUserAction(it, isShowingPauseIcon = true)
            assertTrue(it.playWhenReady)
            applyPlaybackButtonUserAction(it, isShowingPauseIcon = false)
        }
        assertBufferingCanBePaused(false, ::togglePlayerPlaybackFromUserAction)
        assertBufferingCanBePaused(false) { it.handleAudioModePlayPause() }
    }

    private fun assertBufferingCanBePaused(rebuffer: Boolean, pauseAction: ((Player) -> Unit)? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val materials = if (pauseAction == null) listOf(false, true) else listOf(false)
        for (glass in materials) {
            for (fullscreen in materials) {
                val gate = ReadGate(initiallyBlocked = !rebuffer)
                val intent = Intent(context, PlayerParityTestActivity::class.java)
                    .putExtra("glass", glass).putExtra("fullscreen", fullscreen)
                val scenario = ActivityScenario.launch<PlayerParityTestActivity>(intent)
                try {
                    scenario.onActivity { activity ->
                        activity.richCapabilities = false
                        val videoFactory = DefaultMediaSourceFactory(context)
                        val upstream = DefaultDataSource.Factory(context)
                        val audioFactory = DefaultMediaSourceFactory(DataSource.Factory {
                            val source = upstream.createDataSource()
                            object : DataSource by source {
                                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                                    source.read(buffer, offset, gate.permit(length))
                            }
                        })
                        activity.player.repeatMode = Player.REPEAT_MODE_OFF
                        activity.player.setMediaSource(MergingMediaSource(
                            videoFactory.createMediaSource(MediaItem.fromUri("asset:///player_speed_video.mp4")),
                            audioFactory.createMediaSource(MediaItem.fromUri("asset:///player_speed_hires.flac")),
                        ))
                        activity.player.prepare()
                        activity.player.play()
                    }
                    if (rebuffer) {
                        await(scenario) {
                            it.player.currentMediaItem?.localConfiguration?.uri?.toString() == "asset:///player_speed_video.mp4" &&
                                it.player.isPlaying && it.player.currentPosition > 100
                        }
                        gate.block()
                    }
                    await(scenario) { gate.blocked && it.player.playbackState == Player.STATE_BUFFERING }
                    scenario.onActivity {
                        assertTrue(it.player.playWhenReady)
                        assertFalse(it.player.isPlaying)
                    }
                    rule.onNodeWithTag("player_play_pause").assertIsDisplayed()
                    if (pauseAction == null) {
                        rule.onNodeWithContentDescription("暂停播放", useUnmergedTree = true).performClick()
                    } else {
                        scenario.onActivity { pauseAction(it.player) }
                    }
                    await(scenario) { !it.player.playWhenReady }
                    rule.onNodeWithContentDescription("开始播放", useUnmergedTree = true).assertIsDisplayed()

                    gate.release()
                    await(scenario) { it.player.playbackState == Player.STATE_READY }
                    fence(scenario)
                    var pausedPosition = 0L
                    scenario.onActivity {
                        assertFalse("Data arrival must not undo pause", it.player.playWhenReady)
                        assertFalse(it.player.isPlaying)
                        pausedPosition = it.player.currentPosition
                    }
                    SystemClock.sleep(250)
                    scenario.onActivity { assertEquals(pausedPosition, it.player.currentPosition) }
                    rule.onNodeWithContentDescription("开始播放", useUnmergedTree = true).performClick()
                    await(scenario) { it.player.isPlaying && it.player.currentPosition > pausedPosition + 100 }
                } finally {
                    gate.release()
                    scenario.close()
                }
            }
        }
    }

    private fun await(
        scenario: ActivityScenario<PlayerParityTestActivity>,
        condition: (PlayerParityTestActivity) -> Boolean,
    ) {
        try {
            rule.waitUntil(20_000) {
                var satisfied = false
                scenario.onActivity { satisfied = condition(it) }
                satisfied
            }
        } catch (error: Exception) {
            var snapshot = ""
            scenario.onActivity {
                snapshot = "state=${it.player.playbackState}, pwr=${it.player.playWhenReady}, " +
                    "playing=${it.player.isPlaying}, pos=${it.player.currentPosition}, " +
                    "buffered=${it.player.bufferedPosition}, error=${it.player.playerError}"
            }
            throw AssertionError(snapshot, error)
        }
    }

    private fun fence(scenario: ActivityScenario<PlayerParityTestActivity>) {
        lateinit var message: PlayerMessage
        scenario.onActivity { message = it.player.createMessage { _, _ -> }.send() }
        message.blockUntilDelivered(10_000)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private class ReadGate(initiallyBlocked: Boolean) {
        private val released = CountDownLatch(1)
        @Volatile private var blocking = initiallyBlocked
        @Volatile var blocked = false
            private set

        fun block() { blocking = true }

        fun permit(length: Int): Int {
            if (length == 0) return 0
            if (blocking) {
                blocked = true
                released.await()
            } else if (released.count > 0) {
                // Keep the local fixture from loading to EOF before the first decoded frames.
                SystemClock.sleep(4)
            }
            return if (released.count == 0L) length else minOf(length, 4096)
        }

        fun release() = released.countDown()
    }
}
