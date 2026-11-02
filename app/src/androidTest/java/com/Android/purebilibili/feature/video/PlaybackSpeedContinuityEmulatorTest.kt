@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video

import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.Flac
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.playback.audio.AUDIO_QUALITY_AUTO
import com.android.purebilibili.feature.video.playback.audio.AUDIO_QUALITY_DOLBY
import com.android.purebilibili.feature.video.playback.audio.AUDIO_QUALITY_HI_RES
import com.android.purebilibili.feature.video.playback.audio.resolveAudioStreamSelection
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PlaybackSpeedContinuityEmulatorTest {
    private lateinit var state: VideoPlayerState
    private lateinit var debugCollection: Job

    @Test
    fun premiumAudioSpeedChangesKeepSourceFirstFrameAndPlayPauseIntent() {
        assertSpeedChangesKeepPlayback(
            requestedAudioQuality = AUDIO_QUALITY_HI_RES,
            expectedAudioQuality = AUDIO_QUALITY_HI_RES,
            expectedMimeType = MimeTypes.AUDIO_FLAC,
        )
    }

    @Test
    fun standardAacSpeedChangesKeepSourceFirstFrameAndPlayPauseIntent() {
        assertSpeedChangesKeepPlayback(
            requestedAudioQuality = 30280,
            expectedAudioQuality = 30280,
            expectedMimeType = MimeTypes.AUDIO_AAC,
        )
    }

    @Test
    fun automaticAudioSpeedChangesKeepSourceFirstFrameAndPlayPauseIntent() {
        assertSpeedChangesKeepPlayback(
            requestedAudioQuality = AUDIO_QUALITY_AUTO,
            expectedAudioQuality = 30280,
            expectedMimeType = MimeTypes.AUDIO_AAC,
        )
    }

    @Test
    fun unavailableHiResKeepsFallbackAacWhenSpeedChanges() {
        assertSpeedChangesKeepPlayback(
            requestedAudioQuality = AUDIO_QUALITY_HI_RES,
            expectedAudioQuality = 30280,
            expectedMimeType = MimeTypes.AUDIO_AAC,
            includeHiRes = false,
        )
    }

    @Test
    fun unavailableDolbyKeepsFallbackAacWhenSpeedChanges() {
        assertSpeedChangesKeepPlayback(
            requestedAudioQuality = AUDIO_QUALITY_DOLBY,
            expectedAudioQuality = 30280,
            expectedMimeType = MimeTypes.AUDIO_AAC,
            includeHiRes = false,
        )
    }

    @Test
    fun highSpeedAudioFallbackStaysOnAacWhenReturningToNormalSpeed() {
        assertSpeedChangesKeepPlayback(
            requestedAudioQuality = AUDIO_QUALITY_HI_RES,
            expectedAudioQuality = 30280,
            expectedMimeType = MimeTypes.AUDIO_AAC,
            initialSpeed = 2f,
        )
    }

    private fun assertSpeedChangesKeepPlayback(
        requestedAudioQuality: Int,
        expectedAudioQuality: Int,
        expectedMimeType: String,
        initialSpeed: Float = 1f,
        includeHiRes: Boolean = true,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val replacements = AtomicInteger()
        val rebuffers = AtomicInteger()
        lateinit var viewModel: VideoPlaybackViewModel
        val videoUrl = "asset:///player_speed_video.mp4"
        val standardAudio = DashAudio(
            id = 30280, baseUrl = "asset:///player_speed_standard.m4a", codecs = "mp4a.40.2"
        )
        val hiResAudio = DashAudio(
            id = AUDIO_QUALITY_HI_RES, baseUrl = "asset:///player_speed_hires.flac", codecs = "fLaC"
        )
        val videos = listOf(DashVideo(id = 64, baseUrl = videoUrl, codecs = "avc1"))
        val dash = Dash(
            video = videos,
            audio = listOf(standardAudio),
            flac = if (includeHiRes) Flac(audio = hiResAudio) else null,
        )
        val selection = resolveAudioStreamSelection(
            dash = dash,
            requestedAudioQuality = requestedAudioQuality,
            playbackSpeed = initialSpeed,
        )
        assertEquals(expectedAudioQuality, selection.selectedPreferenceId)
        val audioUrl = checkNotNull(selection.selected).track.getValidUrl()
        val scenario = ActivityScenario.launch<PlayerParityTestActivity>(
            Intent(context, PlayerParityTestActivity::class.java)
        )
        try {
            scenario.onActivity { activity ->
                val player = activity.player
                viewModel = ViewModelProvider(activity)[VideoPlaybackViewModel::class.java]
                viewModel.attachPlayer(player)
                state = VideoPlayerState(activity, player, activity.lifecycleScope)
                state.attachPlayerListeners()
                debugCollection = activity.lifecycleScope.launch { state.debugInfo.collect {} }
                val factory = DefaultMediaSourceFactory(activity)
                player.setPlaybackSpeed(initialSpeed)
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.setMediaSource(MergingMediaSource(
                    factory.createMediaSource(MediaItem.fromUri(videoUrl)),
                    factory.createMediaSource(MediaItem.fromUri(audioUrl)),
                ))
                viewModel.restoreUiState(VideoPlaybackUiState.Success(
                    info = ViewInfo(bvid = "local-speed-fixture", cid = 1, title = "Speed continuity"),
                    playUrl = videoUrl,
                    audioUrl = audioUrl,
                    currentQuality = 64,
                    videoDurationMs = 30_000,
                    requestedAudioQuality = requestedAudioQuality,
                    selectedAudioQuality = selection.selectedPreferenceId,
                    availableAudioQualities = selection.availableOptions,
                    cachedDashVideos = videos,
                    cachedDashAudios = listOfNotNull(standardAudio, dash.flac?.audio),
                    cachedDash = dash,
                ))
                player.prepare()
                player.play()
            }
            await(scenario) { activity ->
                activity.player.isPlaying && activity.renderedFrames.get() > 2 &&
                    state.debugInfo.value.firstFrame == "rendered" &&
                    !activity.player.isLoading && activity.player.bufferedPosition >= 29_900 &&
                    activity.player.currentTracks.groups.any { group ->
                        group.type == C.TRACK_TYPE_AUDIO && (0 until group.length).any { index ->
                            group.isTrackSelected(index) && group.getTrackFormat(index).sampleMimeType == expectedMimeType
                        }
                    }
            }
            scenario.onActivity { activity ->
                activity.player.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) replacements.incrementAndGet()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_BUFFERING) rebuffers.incrementAndGet()
                    }
                })
            }
            // Include same-side changes as well as crossings of the old 1.5x threshold.
            for (paused in listOf(false, true)) {
                scenario.onActivity { if (paused) it.player.pause() else it.player.play() }
                awaitPlaybackCommands(scenario)
                for (speed in listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 2f, 1.75f, 1.5f, 1.25f, 1f)) {
                    var beforeFrames = 0L
                    var pausedPosition = 0L
                    scenario.onActivity { activity ->
                        beforeFrames = activity.renderedFrames.get()
                        pausedPosition = activity.player.currentPosition
                        assertTrue(viewModel.applyPlaybackSpeedFromUi(speed))
                        assertEquals("Speed change replaced the media source", 0, replacements.get())
                    }
                    awaitPlaybackCommands(scenario)
                    if (!paused) await(scenario) { it.renderedFrames.get() > beforeFrames + 2 }
                    scenario.onActivity { activity ->
                        assertEquals("Speed change discarded the decoded first frame", "rendered", state.debugInfo.value.firstFrame)
                        assertEquals("Speed change flushed the existing buffer", 0, rebuffers.get())
                        assertEquals(speed, activity.player.playbackParameters.speed, 0f)
                        assertEquals(!paused, activity.player.playWhenReady)
                        assertNull(activity.player.playerError)
                        assertEquals(
                            "Speed change switched the selected audio quality",
                            expectedAudioQuality,
                            (viewModel.uiState.value as VideoPlaybackUiState.Success).selectedAudioQuality,
                        )
                        assertTrue("Speed change changed the decoded audio format", activity.player.currentTracks.groups.any { group ->
                            group.type == C.TRACK_TYPE_AUDIO && (0 until group.length).any { index ->
                                group.isTrackSelected(index) && group.getTrackFormat(index).sampleMimeType == expectedMimeType
                            }
                        })
                        if (paused) assertEquals(pausedPosition, activity.player.currentPosition)
                    }
                }
            }
        } finally {
            scenario.onActivity {
                if (::debugCollection.isInitialized) debugCollection.cancel()
                if (::state.isInitialized) state.release()
            }
            scenario.close()
        }
    }

    private fun awaitPlaybackCommands(scenario: ActivityScenario<PlayerParityTestActivity>) {
        // Public player state is updated optimistically. Fence the playback thread before
        // comparing paused positions so pending AudioTrack clock acknowledgements have arrived.
        lateinit var message: PlayerMessage
        scenario.onActivity { message = it.player.createMessage { _, _ -> }.send() }
        assertTrue("Playback thread did not acknowledge commands", message.blockUntilDelivered(10_000))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun await(scenario: ActivityScenario<PlayerParityTestActivity>, predicate: (PlayerParityTestActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        do {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Local player did not reach the expected decoding state")
    }
}
