@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.subtitle.SubtitleCue
import com.android.purebilibili.feature.video.ui.overlay.PortraitSubtitleHost
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real player/production native subtitle host; local media avoids account and network dependencies. */
@RunWith(AndroidJUnit4::class)
class SubtitleSeekEmulatorTest {
    @Test
    fun forwardSeekToSilentGapClearsCaptionAndNextSeekSelectsTargetCue() = withSubtitles { scenario, automation ->
        seek(scenario, 10_500)
        assertCaption(automation, "LATE CUE")
        seek(scenario, 12_000)
        assertNoCaption(automation)
        scenario.onActivity { it.player.setPlaybackSpeed(2f); it.player.play() }
        await(scenario) { it.player.currentPosition >= 14_200 }
        scenario.onActivity { it.player.pause() }
        settle(scenario)
        assertCaption(automation, "NEXT CUE")
        seek(scenario, 1_500)
        assertCaption(automation, "EARLY CUE")
    }

    @Test
    fun backwardSeekDoesNotReviveAnExpiredCaptionFromTheFuture() = withSubtitles { scenario, automation ->
        seek(scenario, 10_500)
        assertCaption(automation, "LATE CUE")
        seek(scenario, 11_100)
        scenario.onActivity { it.player.play() }
        await(scenario) { it.player.currentPosition >= 12_000 }
        scenario.onActivity { it.player.pause() }
        settle(scenario)
        assertNoCaption(automation)
        seek(scenario, 5_000)
        assertNoCaption(automation)
        scenario.onActivity { it.player.play() }
        await(scenario) { it.player.currentPosition >= 6_000 }
        assertNoCaption(automation)
        seek(scenario, 1_500)
        assertCaption(automation, "EARLY CUE")
    }

    private fun withSubtitles(check: (ActivityScenario<PlayerParityTestActivity>, UiAutomation) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val scenario = ActivityScenario.launch<PlayerParityTestActivity>(Intent(context, PlayerParityTestActivity::class.java))
        try {
            scenario.onActivity { activity ->
                activity.player.pause()
                activity.player.repeatMode = Player.REPEAT_MODE_OFF
                activity.player.setMediaItem(MediaItem.fromUri("asset:///player_speed_video.mp4"))
                activity.player.prepare()
                val state = VideoPlaybackUiState.Success(
                    info = ViewInfo(bvid = "subtitle-seek-fixture", cid = 1L, title = "Subtitle seek"),
                    playUrl = "asset:///player_speed_video.mp4",
                    subtitleOwnerBvid = "subtitle-seek-fixture",
                    subtitleOwnerCid = 1L,
                    subtitlePrimaryLanguage = "ai-zh",
                    subtitlePrimaryLikelyAi = true,
                    subtitlePrimaryCues = listOf(
                        SubtitleCue(1_000, 2_000, "EARLY CUE"),
                        SubtitleCue(10_000, 11_000, "LATE CUE"),
                        SubtitleCue(14_000, 15_000, "NEXT CUE"),
                    ),
                )
                activity.setContent {
                    MaterialTheme {
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            Box(Modifier.fillMaxWidth().height(320.dp)) {
                                AndroidView(factory = { PlayerView(it).apply {
                                    player = activity.player
                                    useController = false
                                } }, modifier = Modifier.fillMaxSize())
                                PortraitSubtitleHost(
                                    success = state, player = activity.player,
                                    pageBvid = state.info.bvid, pageCid = state.info.cid,
                                    isCurrentPage = true, controlsVisible = false,
                                    commentExpansionProgress = 0f,
                                    subtitleAutoPreference = SubtitleAutoPreference.ON,
                                    isMuted = false, onSubtitleTrackSelected = {},
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
            await(scenario) { it.player.playbackState == Player.STATE_READY }
            check(scenario, automation)
        } finally {
            automation.takeScreenshot()?.let { frame ->
                File(context.getExternalFilesDir(null), "subtitle-seek-last.png").outputStream().use {
                    frame.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                frame.recycle()
            }
            scenario.close()
        }
    }

    private fun seek(scenario: ActivityScenario<PlayerParityTestActivity>, positionMs: Long) {
        scenario.onActivity { it.player.pause(); it.player.seekTo(positionMs) }
        await(scenario) { it.player.playbackState == Player.STATE_READY && it.player.currentPosition == positionMs }
        settle(scenario)
    }

    private fun settle(scenario: ActivityScenario<PlayerParityTestActivity>) {
        lateinit var message: PlayerMessage
        scenario.onActivity { message = it.player.createMessage { _, _ -> }.send() }
        assertTrue(message.blockUntilDelivered(10_000))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(100, 5_000)
    }

    private fun captionTexts(automation: UiAutomation): Set<String> = buildSet {
        fun visit(node: AccessibilityNodeInfo) {
            val text = node.text?.toString()
            if (node.isVisibleToUser && text != null && text.endsWith(" CUE")) add(text)
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                // Removed Compose nodes may remain in the accessibility client's cache.
                if (child.refresh()) visit(child)
            }
        }
        val root = checkNotNull(automation.windows.firstOrNull {
            it.isActive && it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
        }?.root) { "No active application window for subtitle assertion" }
        check(root.refresh()) { "Application window disappeared during subtitle assertion" }
        visit(root)
    }

    private fun assertCaption(automation: UiAutomation, expected: String) {
        assertEquals(setOf(expected), captionTexts(automation))
    }

    private fun assertNoCaption(automation: UiAutomation) {
        val captions = captionTexts(automation)
        assertFalse("Seek retained stale subtitle: $captions", captions.isNotEmpty())
    }

    private fun await(scenario: ActivityScenario<PlayerParityTestActivity>, condition: (PlayerParityTestActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        do {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(20)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Player did not reach subtitle seek checkpoint")
    }
}
