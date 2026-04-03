package com.android.purebilibili.feature.video.ui.overlay

import androidx.media3.common.Player
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.android.purebilibili.core.store.BottomProgressBehavior
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

class VideoPlayerOverlayPolicyTest {

    @Test
    fun sponsorProgressBarMarkers_areClampedIntoTrackBounds() {
        val markers = resolveSponsorProgressBarMarkers(
            durationMs = 100_000L,
            markers = listOf(
                SponsorProgressMarker(
                    segmentId = "segment",
                    category = SponsorCategory.SPONSOR,
                    startTimeMs = -5_000L,
                    endTimeMs = 130_000L
                )
            )
        )

        assertEquals(1, markers.size)
        assertEquals(0f, markers.single().startFraction)
        assertEquals(1f, markers.single().endFraction)
    }

    @Test
    fun sponsorProgressBarMarkers_areEmptyWhenDurationIsInvalid() {
        val markers = resolveSponsorProgressBarMarkers(
            durationMs = 0L,
            markers = listOf(
                SponsorProgressMarker(
                    segmentId = "segment",
                    category = SponsorCategory.SPONSOR,
                    startTimeMs = 10_000L,
                    endTimeMs = 20_000L
                )
            )
        )

        assertTrue(markers.isEmpty())
    }

    @Test
    fun sponsorProgressBarMarkers_useSecondaryStyleForNonSponsorSegments() {
        val markers = resolveSponsorProgressBarMarkers(
            durationMs = 100_000L,
            markers = listOf(
                SponsorProgressMarker(
                    segmentId = "segment",
                    category = SponsorCategory.INTRO,
                    startTimeMs = 10_000L,
                    endTimeMs = 20_000L
                )
            )
        )

        assertEquals(1, markers.size)
        assertEquals(SponsorCategory.INTRO, markers.single().category)
        assertTrue(markers.single().color.alpha < 1f)
    }

    @Test
    fun inlineOverlayProgressPolling_stopsWhenHostLifecycleStops() {
        assertTrue(
            shouldPollInlineVideoOverlayProgress(
                playerExists = true,
                hostLifecycleStarted = true
            )
        )
        assertFalse(
            shouldPollInlineVideoOverlayProgress(
                playerExists = true,
                hostLifecycleStarted = false
            )
        )
        assertFalse(
            shouldPollInlineVideoOverlayProgress(
                playerExists = false,
                hostLifecycleStarted = true
            )
        )
    }

    @Test
    fun episodeEntryShownWhenRelatedVideosExist() {
        assertTrue(
            shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 1,
                hasSeasonEpisodes = false,
                pagesCount = 1
            )
        )
    }

    @Test
    fun episodeEntryShownWhenSeasonEpisodesExist() {
        assertTrue(
            shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 0,
                hasSeasonEpisodes = true,
                pagesCount = 1
            )
        )
    }

    @Test
    fun episodeEntryShownWhenPagesExist() {
        assertTrue(
            shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 0,
                hasSeasonEpisodes = false,
                pagesCount = 3
            )
        )
    }

    @Test
    fun episodeEntryHiddenWhenNoEpisodeData() {
        assertFalse(
            shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 0,
                hasSeasonEpisodes = false,
                pagesCount = 1
            )
        )
    }

    @Test
    fun nextEpisodeTargetPrefersPageNext() {
        val target = resolveNextEpisodeTarget(
            pagesCount = 3,
            currentPageIndex = 0,
            seasonEpisodeBvids = listOf("BV1", "BV2"),
            currentBvid = "BV1",
            relatedBvids = listOf("BV3")
        )
        assertTrue(target?.nextPageIndex == 1)
    }

    @Test
    fun nextEpisodeTargetFallsBackToSeasonThenRelated() {
        val seasonTarget = resolveNextEpisodeTarget(
            pagesCount = 1,
            currentPageIndex = 0,
            seasonEpisodeBvids = listOf("BV1", "BV2"),
            currentBvid = "BV1",
            relatedBvids = listOf("BV3")
        )
        assertTrue(seasonTarget?.nextBvid == "BV2")

        val relatedTarget = resolveNextEpisodeTarget(
            pagesCount = 1,
            currentPageIndex = 0,
            seasonEpisodeBvids = listOf("BV1"),
            currentBvid = "BV1",
            relatedBvids = listOf("BV1", "BV3")
        )
        assertTrue(relatedTarget?.nextBvid == "BV3")
    }

    @Test
    fun nextEpisodeTargetReturnsNullWhenNoCandidate() {
        assertTrue(
            resolveNextEpisodeTarget(
                pagesCount = 1,
                currentPageIndex = 0,
                seasonEpisodeBvids = emptyList(),
                currentBvid = "BV1",
                relatedBvids = emptyList()
            )
                == null
        )
    }

    @Test
    fun drawerVisibleShouldConsumeBackgroundGestures() {
        assertTrue(
            shouldConsumeBackgroundGesturesForEndDrawer(
                endDrawerVisible = true
            )
        )
    }

    @Test
    fun drawerHiddenShouldNotConsumeBackgroundGestures() {
        assertFalse(
            shouldConsumeBackgroundGesturesForEndDrawer(
                endDrawerVisible = false
            )
        )
    }

    @Test
    fun centerPlayButtonHiddenWhenScrubbingOrBuffering() {
        assertFalse(
            shouldShowCenterPlayButton(
                isVisible = true,
                isPlaying = false,
                isQualitySwitching = false,
                isFullscreen = true,
                isBuffering = true,
                isScrubbing = false,
                isSeekTransitionPending = false
            )
        )
        assertFalse(
            shouldShowCenterPlayButton(
                isVisible = true,
                isPlaying = false,
                isQualitySwitching = false,
                isFullscreen = true,
                isBuffering = false,
                isScrubbing = true,
                isSeekTransitionPending = false
            )
        )
        assertTrue(
            shouldShowCenterPlayButton(
                isVisible = true,
                isPlaying = false,
                isQualitySwitching = false,
                isFullscreen = true,
                isBuffering = false,
                isScrubbing = false,
                isSeekTransitionPending = false
            )
        )
    }

    @Test
    fun centerPlayButtonHiddenDuringSeekResumeTransition() {
        assertFalse(
            shouldShowCenterPlayButton(
                isVisible = true,
                isPlaying = false,
                isQualitySwitching = false,
                isFullscreen = true,
                isBuffering = false,
                isScrubbing = false,
                isSeekTransitionPending = true
            )
        )
    }

    @Test
    fun playbackButtonState_staysActiveWhilePlayerIsBufferingForResume() {
        assertTrue(
            resolveOverlayPlaybackButtonPlayingState(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun playbackButtonState_staysInactiveForPausedReadyPlayer() {
        assertFalse(
            resolveOverlayPlaybackButtonPlayingState(
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun bufferingIndicatorCanShowDuringScrubbingEvenWhenControlsVisible() {
        assertTrue(
            shouldShowBufferingIndicator(
                isBuffering = true,
                isQualitySwitching = false,
                isVisible = true,
                isScrubbing = true
            )
        )
        assertFalse(
            shouldShowBufferingIndicator(
                isBuffering = true,
                isQualitySwitching = false,
                isVisible = true,
                isScrubbing = false
            )
        )
    }

    @Test
    fun playbackDebugRows_includeAllReadableStatsAndSkipEmptyValues() {
        val rows = resolvePlaybackDebugRows(
            PlaybackDebugInfo(
                resolution = "1920 x 1080",
                videoBitrate = "8.4 Mbps",
                audioBitrate = "192 kbps",
                videoCodec = "HEVC",
                audioCodec = "AAC LC",
                frameRate = "60 fps",
                videoDecoder = "c2.qti.hevc.decoder",
                audioDecoder = "",
                playbackState = "READY",
                playWhenReady = "true",
                isPlaying = "true",
                firstFrame = "rendered",
                droppedFrames = "12",
                bandwidthEstimate = "8.6 Mbps",
                lastVideoEvent = "first frame rendered",
                lastAudioEvent = "audio sink recovered"
            )
        )

        assertEquals(
            listOf(
                DebugStatRow("Resolution", "1920 x 1080"),
                DebugStatRow("Video bitrate", "8.4 Mbps"),
                DebugStatRow("Audio bitrate", "192 kbps"),
                DebugStatRow("Video codec", "HEVC"),
                DebugStatRow("Audio codec", "AAC LC"),
                DebugStatRow("Frame rate", "60 fps"),
                DebugStatRow("Video decoder", "c2.qti.hevc.decoder"),
                DebugStatRow("Playback state", "READY"),
                DebugStatRow("Play when ready", "true"),
                DebugStatRow("Is playing", "true"),
                DebugStatRow("First frame", "rendered"),
                DebugStatRow("Dropped frames", "12"),
                DebugStatRow("Bandwidth", "8.6 Mbps"),
                DebugStatRow("Last video event", "first frame rendered"),
                DebugStatRow("Last audio event", "audio sink recovered")
            ),
            rows
        )
    }

    @Test
    fun appendPlaybackDiagnosticEvent_keepsNewestEntriesWithinLimit() {
        val result = (1..4).fold(emptyList<String>()) { events, index ->
            appendPlaybackDiagnosticEvent(
                current = events,
                event = "event-$index",
                maxEntries = 3
            )
        }

        assertEquals(
            listOf("event-2", "event-3", "event-4"),
            result
        )
    }

    @Test
    fun buildPlaybackDiagnosticReport_formatsStructuredUserShareText() {
        val report = buildPlaybackDiagnosticReport(
            title = "Test video",
            bvid = "BV1xx411c7mD",
            cid = 123456L,
            currentPositionMs = 125_000L,
            bufferedPositionMs = 188_000L,
            debugInfo = PlaybackDebugInfo(
                resolution = "1920 x 1080",
                videoCodec = "HEVC",
                audioCodec = "AAC",
                playbackState = "READY",
                playWhenReady = "true",
                isPlaying = "false",
                firstFrame = "rendered",
                droppedFrames = "12",
                bandwidthEstimate = "8.6 Mbps",
                lastVideoEvent = "first frame rendered",
                lastAudioEvent = "audio sink recovered"
            ),
            recentEvents = listOf(
                "pause requested at 01:40",
                "resume requested at 02:05"
            ),
            generatedAtMillis = 1_711_701_234_000L
        )

        assertTrue(report.contains("BiliPai Player Diagnostics"))
        assertTrue(report.contains("Title: Test video"))
        assertTrue(report.contains("BVID: BV1xx411c7mD"))
        assertTrue(report.contains("CID: 123456"))
        assertTrue(report.contains("Position: 02:05"))
        assertTrue(report.contains("Buffered: 03:08"))
        assertTrue(report.contains("Playback state: READY"))
        assertTrue(report.contains("Bandwidth: 8.6 Mbps"))
        assertTrue(report.contains("Recent events:"))
        assertTrue(report.contains("- pause requested at 01:40"))
        assertTrue(report.contains("- resume requested at 02:05"))
    }

    @Test
    fun resolvePlaybackIssueSignal_flagsLongBufferingAsStutter() {
        val signal = resolvePlaybackIssueSignal(
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            firstFrameRendered = true,
            bufferingDurationMs = 12_000L,
            waitingFirstFrameDurationMs = 0L
        )

        assertNotNull(signal)
        assertEquals(PlaybackIssueType.STUTTER, signal.type)
        assertTrue(signal.title.contains("卡顿"))
    }

    @Test
    fun resolvePlaybackIssueSignal_flagsReadyWithoutFirstFrameAsBlackScreen() {
        val signal = resolvePlaybackIssueSignal(
            playbackState = Player.STATE_READY,
            playWhenReady = true,
            firstFrameRendered = false,
            bufferingDurationMs = 0L,
            waitingFirstFrameDurationMs = 6_000L
        )

        assertNotNull(signal)
        assertEquals(PlaybackIssueType.BLACK_SCREEN, signal.type)
        assertTrue(signal.title.contains("黑屏"))
    }

    @Test
    fun resolvePlaybackIssueSignal_ignoresShortWaitsAndRecoveredFirstFrame() {
        assertNull(
            resolvePlaybackIssueSignal(
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                firstFrameRendered = true,
                bufferingDurationMs = 3_000L,
                waitingFirstFrameDurationMs = 0L
            )
        )
        assertNull(
            resolvePlaybackIssueSignal(
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                firstFrameRendered = true,
                bufferingDurationMs = 0L,
                waitingFirstFrameDurationMs = 10_000L
            )
        )
    }

    @Test
    fun resolvePlaybackActionNoResponseSignal_flagsPlayActionTimeout() {
        val signal = resolvePlaybackActionNoResponseSignal(
            actionType = PlaybackUserActionType.PLAY,
            actionAgeMs = 2_200L,
            hasPlayerResponded = false
        )

        assertNotNull(signal)
        assertEquals(PlaybackIssueType.NO_RESPONSE, signal.type)
        assertTrue(signal.title.contains("无响应"))
    }

    @Test
    fun resolvePlaybackActionNoResponseSignal_ignoresFastOrRecoveredActions() {
        assertNull(
            resolvePlaybackActionNoResponseSignal(
                actionType = PlaybackUserActionType.PLAY,
                actionAgeMs = 1_000L,
                hasPlayerResponded = false
            )
        )
        assertNull(
            resolvePlaybackActionNoResponseSignal(
                actionType = PlaybackUserActionType.PAUSE,
                actionAgeMs = 5_000L,
                hasPlayerResponded = true
            )
        )
    }

    @Test
    fun resolvePlaybackDiagnosticEvents_skipsCollectionWhenLoggingDisabled() {
        assertEquals(
            listOf("00:10 | first frame rendered"),
            resolvePlaybackDiagnosticEvents(
                current = listOf("00:10 | first frame rendered"),
                event = "00:15 | buffering started",
                diagnosticsEnabled = false
            )
        )

        assertEquals(
            listOf("00:10 | first frame rendered", "00:15 | buffering started"),
            resolvePlaybackDiagnosticEvents(
                current = listOf("00:10 | first frame rendered"),
                event = "00:15 | buffering started",
                diagnosticsEnabled = true
            )
        )
    }

    @Test
    fun shouldMonitorPlaybackIssues_requiresLoggingEnabledAndActiveSignals() {
        assertFalse(
            shouldMonitorPlaybackIssues(
                diagnosticsEnabled = false,
                bufferingStartedAtMs = 10L,
                waitingFirstFrameStartedAtMs = 0L,
                hasPendingUserAction = false
            )
        )
        assertFalse(
            shouldMonitorPlaybackIssues(
                diagnosticsEnabled = true,
                bufferingStartedAtMs = 0L,
                waitingFirstFrameStartedAtMs = 0L,
                hasPendingUserAction = false
            )
        )
        assertTrue(
            shouldMonitorPlaybackIssues(
                diagnosticsEnabled = true,
                bufferingStartedAtMs = 10L,
                waitingFirstFrameStartedAtMs = 0L,
                hasPendingUserAction = false
            )
        )
        assertTrue(
            shouldMonitorPlaybackIssues(
                diagnosticsEnabled = true,
                bufferingStartedAtMs = 0L,
                waitingFirstFrameStartedAtMs = 0L,
                hasPendingUserAction = true
            )
        )
    }

    @Test
    fun centerPlaybackButtonStyle_keepsReadableWhiteGlyphAcrossThemes() {
        val darkStyle = resolveCenterPlaybackButtonStyle(isDarkTheme = true)
        val lightStyle = resolveCenterPlaybackButtonStyle(isDarkTheme = false)

        assertTrue(darkStyle.containerColor.alpha > lightStyle.containerColor.alpha)
        assertTrue(darkStyle.innerColor != lightStyle.innerColor)
        assertEquals(Color.White, darkStyle.iconTint)
        assertEquals(Color.White, lightStyle.iconTint)
    }

    @Test
    fun playbackGlyphOpticalOffset_onlyShiftsPlayIcon() {
        assertEquals(0f, resolvePlaybackGlyphHorizontalBias(isPlaying = true))
        assertTrue(resolvePlaybackGlyphHorizontalBias(isPlaying = false) > 0f)
    }

    @Test
    fun pageSelectorSheetOuterBottomPadding_isZeroInFullscreen() {
        assertEquals(0, resolvePageSelectorSheetOuterBottomPaddingDp(isFullscreen = true))
        assertEquals(8, resolvePageSelectorSheetOuterBottomPaddingDp(isFullscreen = false))
    }

    @Test
    fun persistentBottomProgressBarRespectsAlwaysShowBehavior() {
        assertTrue(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = false,
                behavior = BottomProgressBehavior.ALWAYS_SHOW
            )
        )
        assertFalse(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = true,
                isFullscreen = true,
                behavior = BottomProgressBehavior.ALWAYS_SHOW
            )
        )
    }

    @Test
    fun persistentBottomProgressBarRespectsAlwaysHideBehavior() {
        assertFalse(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = true,
                behavior = BottomProgressBehavior.ALWAYS_HIDE
            )
        )
    }

    @Test
    fun persistentBottomProgressBarRespectsOnlyShowFullscreenBehavior() {
        assertTrue(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = true,
                behavior = BottomProgressBehavior.ONLY_SHOW_FULLSCREEN
            )
        )
        assertFalse(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = false,
                behavior = BottomProgressBehavior.ONLY_SHOW_FULLSCREEN
            )
        )
    }

    @Test
    fun persistentBottomProgressBarRespectsOnlyHideFullscreenBehavior() {
        assertTrue(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = false,
                behavior = BottomProgressBehavior.ONLY_HIDE_FULLSCREEN
            )
        )
        assertFalse(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = true,
                behavior = BottomProgressBehavior.ONLY_HIDE_FULLSCREEN
            )
        )
    }

    @Test
    fun resolveDisplayedOnlineCountRespectsSetting() {
        assertEquals(
            "123人正在看",
            resolveDisplayedOnlineCount(
                onlineCount = "123人正在看",
                showOnlineCount = true
            )
        )
        assertEquals(
            "",
            resolveDisplayedOnlineCount(
                onlineCount = "123人正在看",
                showOnlineCount = false
            )
        )
    }

    @Test
    fun displayedProgressPrefersPreviewWhileSeeking() {
        val resolved = resolveDisplayedPlayerProgress(
            progress = PlayerProgress(
                current = 12_000L,
                duration = 60_000L,
                buffered = 20_000L
            ),
            previewPositionMs = 34_000L,
            previewActive = true
        )

        assertEquals(34_000L, resolved.current)
        assertEquals(60_000L, resolved.duration)
        assertEquals(20_000L, resolved.buffered)
    }

    @Test
    fun displayedProgressClampsPreviewWithinDuration() {
        val resolved = resolveDisplayedPlayerProgress(
            progress = PlayerProgress(
                current = 12_000L,
                duration = 60_000L,
                buffered = 20_000L
            ),
            previewPositionMs = 90_000L,
            previewActive = true
        )

        assertEquals(60_000L, resolved.current)
    }

    @Test
    fun displayedProgressFallsBackToPlayerPositionWhenPreviewInactive() {
        val resolved = resolveDisplayedPlayerProgress(
            progress = PlayerProgress(
                current = 12_000L,
                duration = 60_000L,
                buffered = 20_000L
            ),
            previewPositionMs = 34_000L,
            previewActive = false
        )

        assertEquals(12_000L, resolved.current)
    }
}
