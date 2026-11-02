package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.store.player.PlayerSettingsStore

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
    fun endedPlayback_ignoresStaleRecoveryAndQualitySwitchFlags() {
        assertNull(resolveCenterLoadingUiState(
            isBuffering = true,
            isQualitySwitching = true,
            isSeekTransitionPending = true,
            playWhenReady = true,
            isPlaying = false,
            bandwidthEstimate = "",
            playbackState = androidx.media3.common.Player.STATE_ENDED,
        ))
        assertFalse(resolveOverlayPlaybackButtonPlayingState(
            isPlaying = false,
            playWhenReady = true,
            playbackState = androidx.media3.common.Player.STATE_ENDED,
            hasPendingSeekResume = true,
        ))
    }

    @Test
    fun progressSnapshot_fillsLastTwoSecondsOnlyAfterPlaybackEnded() {
        val ended = resolvePlayerProgressSnapshot(
            58_000L, 60_000L, 59_000L, androidx.media3.common.Player.STATE_ENDED,
        )
        assertEquals(60_000L, ended.current)
        assertEquals(60_000L, ended.buffered)
        for (state in listOf(
            androidx.media3.common.Player.STATE_READY,
            androidx.media3.common.Player.STATE_BUFFERING,
        )) {
            assertEquals(58_000L, resolvePlayerProgressSnapshot(
                58_000L, 60_000L, 59_000L, state,
            ).current)
        }
        assertEquals(58_000L, resolvePlayerProgressSnapshot(
            58_000L, 0L, 59_000L, androidx.media3.common.Player.STATE_ENDED,
        ).current)
    }

    @Test
    fun `long press speed keeps shared progress snapshot at high frequency`() {
        assertEquals(
            100L,
            resolveInlineVideoOverlayProgressPollingIntervalMs(
                controlsVisible = false,
                isPlaying = true,
                highFrequencyProgressActive = true
            )
        )
        assertEquals(
            1000L,
            resolveInlineVideoOverlayProgressPollingIntervalMs(
                controlsVisible = false,
                isPlaying = true,
                highFrequencyProgressActive = false
            )
        )
    }

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
    fun episodeEntryHiddenWhenOnlyRelatedVideosExist() {
        assertFalse(
            shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 1,
                hasSeasonEpisodes = false,
                pagesCount = 1,
                hasExternalPlaylist = false
            )
        )
    }

    @Test
    fun episodeEntryShownWhenSingleItemExternalPlaylistExists() {
        assertTrue(
            shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 0,
                hasSeasonEpisodes = false,
                pagesCount = 1,
                hasExternalPlaylist = true
            )
        )
        assertTrue(
            com.android.purebilibili.feature.video.ui.overlay.glass.shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 0,
                hasSeasonEpisodes = false,
                pagesCount = 1,
                hasExternalPlaylist = true
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
    fun episodeNavigationExhaustsPartsBeforeCrossingCollectionBoundaryWithExactIdentity() {
        val first = PiliEpisodeNavigationEntry("current", 11, pageIndex = 0)
        val second = PiliEpisodeNavigationEntry("current", 12, pageIndex = 1)
        val nextVideo = PiliEpisodeNavigationEntry("next", 71, pageIndex = 3)
        val parts = listOf(first, second)
        val collection = listOf(first, nextVideo)
        assertEquals(second, navigateEpisode(first, parts, collection))
        assertEquals(nextVideo, navigateEpisode(second, parts, collection))
        assertEquals(first, navigateEpisode(second, parts, collection, forward = false))
    }

    @Test
    fun episodeNavigationReversesPartsWithoutReversingCollection() {
        val previous = PiliEpisodeNavigationEntry("previous", 1)
        val first = PiliEpisodeNavigationEntry("current", 11, pageIndex = 0)
        val middle = PiliEpisodeNavigationEntry("current", 12, pageIndex = 1)
        val last = PiliEpisodeNavigationEntry("current", 13, pageIndex = 2)
        val next = PiliEpisodeNavigationEntry("next", 21)
        val parts = listOf(first, middle, last)
        val collection = listOf(previous, first, next)
        assertEquals(first, navigateEpisode(middle, parts, collection, reversePages = true))
        assertEquals(next, navigateEpisode(first, parts, collection, reversePages = true))
        assertEquals(last, navigateEpisode(middle, parts, collection, forward = false, reversePages = true))
    }

    @Test
    fun episodeNavigationReversesCollectionWithoutReversingParts() {
        val previous = PiliEpisodeNavigationEntry("previous", 1)
        val first = PiliEpisodeNavigationEntry("current", 11, pageIndex = 0)
        val last = PiliEpisodeNavigationEntry("current", 12, pageIndex = 1)
        val next = PiliEpisodeNavigationEntry("next", 21)
        val parts = listOf(first, last)
        val collection = listOf(previous, first, next)
        assertEquals(last, navigateEpisode(first, parts, collection, reverseOuter = true))
        assertEquals(previous, navigateEpisode(last, parts, collection, reverseOuter = true))
        assertEquals(next, navigateEpisode(first, parts, collection, forward = false, reverseOuter = true))
    }

    @Test
    fun episodeNavigationLoopsPartsOnlyWhenNoOuterQueueExists() {
        val first = PiliEpisodeNavigationEntry("current", 11, pageIndex = 0)
        val last = PiliEpisodeNavigationEntry("current", 12, pageIndex = 1)
        val parts = listOf(first, last)
        assertEquals(first, navigateEpisode(last, parts, loop = true))
        assertEquals(last, navigateEpisode(first, parts, forward = false, loop = true))
        assertNull(navigateEpisode(last, parts))
        val next = PiliEpisodeNavigationEntry("next", 21)
        assertEquals(next, navigateEpisode(last, parts, listOf(first, next), loop = true))
        assertNull(navigateEpisode(first, listOf(first), loop = true))
    }

    @Test
    fun episodeNavigationLoopsOuterQueueInBothDirectionsWithoutLosingCid() {
        val first = PiliEpisodeNavigationEntry("first", 11)
        val last = PiliEpisodeNavigationEntry("last", 98, pageIndex = 4)
        val queue = listOf(first, last)
        assertEquals(first, navigateEpisode(last, outer = queue, loop = true))
        assertEquals(last, navigateEpisode(first, outer = queue, forward = false, loop = true))
        assertNull(navigateEpisode(last, outer = queue))
    }

    @Test
    fun episodeRecommendationsRequireForwardAutomaticContinuationAndSkipCurrentVideo() {
        val current = PiliEpisodeNavigationEntry("current", 11)
        val sameVideoOtherPart = PiliEpisodeNavigationEntry("current", 12)
        val related = PiliEpisodeNavigationEntry("related", 84)
        val recommendations = listOf(sameVideoOtherPart, related)
        assertNull(navigateEpisode(current, related = recommendations))
        assertNull(navigateEpisode(current, related = recommendations, forward = false, autoContinue = true))
        assertEquals(related, navigateEpisode(current, related = recommendations, autoContinue = true))
    }

    @Test
    fun episodeNavigationDoesNotGuessAnIndexForUnknownPlaybackIdentity() {
        val first = PiliEpisodeNavigationEntry("first", 11, pageIndex = 0)
        val second = PiliEpisodeNavigationEntry("second", 21, pageIndex = 1)
        val unknown = PiliEpisodeNavigationEntry("unknown", 999)
        for (forward in listOf(false, true)) {
            assertNull(navigateEpisode(unknown, listOf(first, second), listOf(first, second), forward = forward))
            assertNull(navigateEpisode(unknown, listOf(first, second), listOf(first, second), forward = forward, loop = true))
        }
        assertNull(navigateEpisode(first.copy(cid = 999), pages = listOf(first)))
    }

    @Test
    fun externalQueueTraversesPartsBeforeNextVideoRegardlessOfCollectionSort() {
        val previous = PiliEpisodeNavigationEntry("previous", 7)
        val first = PiliEpisodeNavigationEntry("A", 11, pageIndex = 0)
        val middle = PiliEpisodeNavigationEntry("A", 12, pageIndex = 1)
        val last = PiliEpisodeNavigationEntry("A", 13, pageIndex = 2)
        val queued = PiliEpisodeNavigationEntry("B", 29)
        val seasonSuccessor = PiliEpisodeNavigationEntry("C", 31)
        val parts = listOf(first, middle, last)
        val queue = listOf(previous, first.copy(pageIndex = null), queued)
        val season = listOf(previous, first, seasonSuccessor)
        for (reverseOuter in listOf(false, true)) {
            for (reversePages in listOf(false, true)) {
                val orderedParts = if (reversePages) parts.asReversed() else parts
                fun next(current: PiliEpisodeNavigationEntry, forward: Boolean = true) = navigateEpisode(
                    current, pages = parts, outer = season, externalPlaylist = queue,
                    related = listOf(PiliEpisodeNavigationEntry("related", 41)),
                    forward = forward, reversePages = reversePages, reverseOuter = reverseOuter,
                    autoContinue = true,
                )
                assertEquals(orderedParts[1], next(orderedParts[0]))
                assertEquals(orderedParts[2], next(orderedParts[1]))
                assertEquals(queued, next(orderedParts[2]))
                assertEquals(orderedParts[1], next(orderedParts[2], forward = false))
                assertEquals(orderedParts[0], next(orderedParts[1], forward = false))
                assertEquals(previous, next(orderedParts[0], forward = false))
            }
        }
    }

    @Test
    fun externalQueueBoundariesStopOnlyAfterPartsAndNeverEscapeToSeasonOrRelated() {
        val first = PiliEpisodeNavigationEntry("A", 11, pageIndex = 0)
        val second = PiliEpisodeNavigationEntry("A", 12, pageIndex = 1)
        val penultimate = PiliEpisodeNavigationEntry("B", 21, pageIndex = 0)
        val last = PiliEpisodeNavigationEntry("B", 22, pageIndex = 1)
        val queue = listOf(first.copy(pageIndex = null), penultimate.copy(pageIndex = null))
        for ((parts, boundary, forward) in listOf(
            Triple(listOf(first, second), first, false),
            Triple(listOf(penultimate, last), last, true),
        )) {
            assertNull(
                navigateEpisode(
                    boundary, pages = parts, externalPlaylist = queue,
                    outer = listOf(PiliEpisodeNavigationEntry("before", 1), boundary, PiliEpisodeNavigationEntry("after", 41)),
                    related = listOf(PiliEpisodeNavigationEntry("C", 31)),
                    forward = forward, autoContinue = true,
                )
            )
        }
        assertEquals(second, navigateEpisode(first, pages = listOf(first, second), externalPlaylist = queue))
        assertEquals(penultimate, navigateEpisode(last, pages = listOf(penultimate, last), externalPlaylist = queue, forward = false))
    }

    @Test
    fun externalLoopFinishesPartsBeforeWrappingQueueAndPreservesTargetCid() {
        val first = PiliEpisodeNavigationEntry("A", 11)
        val beforeLast = PiliEpisodeNavigationEntry("B", 28, pageIndex = 0)
        val last = PiliEpisodeNavigationEntry("B", 29, pageIndex = 1)
        val parts = listOf(beforeLast, last)
        val queue = listOf(first, beforeLast.copy(pageIndex = null))
        assertEquals(last, navigateEpisode(beforeLast, pages = parts, externalPlaylist = queue, loop = true))
        assertEquals(
            first,
            navigateEpisode(
                last, pages = parts, externalPlaylist = queue, loop = true,
                related = listOf(PiliEpisodeNavigationEntry("C", 31)), autoContinue = true,
            )
        )
        assertEquals(beforeLast, navigateEpisode(last, pages = parts, externalPlaylist = queue, forward = false, loop = true))
        assertEquals(queue.last(), navigateEpisode(first, externalPlaylist = queue, forward = false, loop = true))
        assertEquals(
            beforeLast,
            navigateEpisode(last, pages = parts, externalPlaylist = queue, reversePages = true, loop = true)
        )
        assertEquals(
            first,
            navigateEpisode(beforeLast, pages = parts, externalPlaylist = queue, reversePages = true, loop = true)
        )
    }

    @Test
    fun emptyOrSingleExternalQueueStillTraversesPartsButNeverFallsBackToSeason() {
        val first = PiliEpisodeNavigationEntry("A", 11, pageIndex = 0)
        val last = PiliEpisodeNavigationEntry("A", 12, pageIndex = 1)
        val parts = listOf(first, last)
        val season = listOf(PiliEpisodeNavigationEntry("before", 1), first, PiliEpisodeNavigationEntry("after", 41))
        for (queue in listOf(emptyList(), listOf(first.copy(pageIndex = null)))) {
            for (loop in listOf(false, true)) {
                assertEquals(last, navigateEpisode(first, pages = parts, externalPlaylist = queue, loop = loop))
                assertEquals(first, navigateEpisode(last, pages = parts, externalPlaylist = queue, forward = false, loop = loop))
                assertNull(
                    navigateEpisode(
                        first, pages = parts, externalPlaylist = queue, outer = season,
                        forward = false, loop = loop, autoContinue = true,
                    )
                )
            }
            assertNull(
                navigateEpisode(
                    last, pages = parts, externalPlaylist = queue, outer = season,
                    related = listOf(PiliEpisodeNavigationEntry("C", 31)), autoContinue = true,
                )
            )
        }
        assertNull(navigateEpisode(last, pages = parts, externalPlaylist = emptyList(), loop = true, outer = season, autoContinue = true))
        assertEquals(
            first.copy(pageIndex = null),
            navigateEpisode(last, pages = parts, externalPlaylist = listOf(first.copy(pageIndex = null)), loop = true),
        )
    }

    private fun navigateEpisode(
        current: PiliEpisodeNavigationEntry,
        pages: List<PiliEpisodeNavigationEntry> = emptyList(),
        outer: List<PiliEpisodeNavigationEntry> = emptyList(),
        related: List<PiliEpisodeNavigationEntry> = emptyList(),
        externalPlaylist: List<PiliEpisodeNavigationEntry>? = null,
        forward: Boolean = true,
        reversePages: Boolean = false,
        reverseOuter: Boolean = false,
        loop: Boolean = false,
        autoContinue: Boolean = false,
    ): PiliEpisodeNavigationEntry? = resolvePiliEpisodeNavigation(
        pages = pages, outer = outer, related = related,
        externalPlaylist = externalPlaylist,
        currentBvid = current.bvid, currentCid = current.cid,
        forward = forward, reversePages = reversePages, reverseOuter = reverseOuter,
        loop = loop, autoContinue = autoContinue,
    )

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
    fun bufferingIndicatorShowsDuringPlaybackStartup() {
        assertTrue(
            shouldShowBufferingIndicator(
                isBuffering = true,
                isQualitySwitching = false,
                playWhenReady = true
            )
        )
        assertFalse(
            shouldShowBufferingIndicator(
                isBuffering = true,
                isQualitySwitching = false,
                playWhenReady = false
            )
        )
        assertFalse(
            shouldShowBufferingIndicator(
                isBuffering = true,
                isQualitySwitching = true,
                playWhenReady = true
            )
        )
    }

    @Test
    fun bufferingIndicatorHidesUntilFirstFrameIsRendered() {
        // 进场时 playWhenReady=true 且 STATE_BUFFERING，但首帧未到：
        // 此时必须由封面承担加载表达，不能再叠转圈。
        assertFalse(
            shouldShowBufferingIndicator(
                isBuffering = true,
                isQualitySwitching = false,
                playWhenReady = true,
                hasRenderedFirstFrame = false,
            )
        )
        assertTrue(
            shouldShowBufferingIndicator(
                isBuffering = true,
                isQualitySwitching = false,
                playWhenReady = true,
                hasRenderedFirstFrame = true,
            )
        )
    }

    @Test
    fun bufferingIndicatorHidesDuringTransitionOverlaySuppression() {
        assertFalse(
            shouldShowBufferingIndicator(
                isBuffering = true,
                isQualitySwitching = false,
                playWhenReady = true,
                hasRenderedFirstFrame = true,
                suppressTransientOverlays = true,
            )
        )
    }

    @Test
    fun centerLoadingUiState_prefersQualitySwitchReasonAndShowsBandwidth() {
        val state = resolveCenterLoadingUiState(
            isBuffering = true,
            isQualitySwitching = true,
            isSeekTransitionPending = true,
            playWhenReady = true,
            isPlaying = false,
            bandwidthEstimate = "1.8 Mbps"
        )

        assertNotNull(state)
        assertEquals(CenterLoadingReason.QUALITY_SWITCH, state.reason)
        assertEquals("1.8 Mbps", state.primaryText)
        assertEquals("正在切换清晰度...", state.secondaryText)
    }

    @Test
    fun centerLoadingUiState_usesSeekReasonDuringSeekBuffering() {
        val state = resolveCenterLoadingUiState(
            isBuffering = true,
            isQualitySwitching = false,
            isSeekTransitionPending = true,
            playWhenReady = true,
            isPlaying = false,
            bandwidthEstimate = "856 kbps"
        )

        assertNotNull(state)
        assertEquals(CenterLoadingReason.SEEK_BUFFERING, state.reason)
        assertEquals("856 kbps", state.primaryText)
        assertEquals("正在定位新进度...", state.secondaryText)
    }

    @Test
    fun centerLoadingUiState_returnsNullForPlainBuffering() {
        assertNull(
            resolveCenterLoadingUiState(
                isBuffering = true,
                isQualitySwitching = false,
                isSeekTransitionPending = false,
                playWhenReady = true,
                isPlaying = false,
                bandwidthEstimate = "2.4 Mbps"
            )
        )
    }

    @Test
    fun centerLoadingUiState_fallsBackToGenericMessageWhenBandwidthMissing() {
        val state = resolveCenterLoadingUiState(
            isBuffering = true,
            isQualitySwitching = false,
            isSeekTransitionPending = true,
            playWhenReady = true,
            isPlaying = false,
            bandwidthEstimate = "  "
        )

        assertNotNull(state)
        assertEquals("正在缓冲...", state.primaryText)
        assertEquals("正在定位新进度...", state.secondaryText)
    }

    @Test
    fun centerLoadingUiState_showsRecoveringPlaybackWhenSeekPendingWithoutBuffering() {
        val state = resolveCenterLoadingUiState(
            isBuffering = false,
            isQualitySwitching = false,
            isSeekTransitionPending = true,
            playWhenReady = true,
            isPlaying = false,
            bandwidthEstimate = ""
        )

        assertNotNull(state)
        assertEquals(CenterLoadingReason.SEEK_BUFFERING, state.reason)
        assertEquals("正在恢复播放...", state.primaryText)
        assertEquals("正在等待视频重新跟上进度...", state.secondaryText)
    }

    @Test
    fun centerLoadingVisualState_usesThemePrimaryForSpinnerAndHighlight() {
        val state = resolveCenterLoadingVisualState(
            themePrimary = Color(0xFF7CB342)
        )

        assertEquals(Color(0xFF7CB342), state.indicatorColor)
        assertEquals(Color(0xFF7CB342), state.primaryTextColor)
        assertTrue(state.secondaryTextColor.alpha < 1f)
    }

    @Test
    fun fullscreenLockButtonVisualState_matchesCurrentLockState() {
        val locked = resolveFullscreenLockButtonVisualState(isScreenLocked = true)
        assertEquals(FullscreenLockButtonIcon.LOCKED, locked.icon)
        assertEquals("已锁定", locked.contentDescription)
        assertTrue(locked.highlighted)

        val unlocked = resolveFullscreenLockButtonVisualState(isScreenLocked = false)
        assertEquals(FullscreenLockButtonIcon.UNLOCKED, unlocked.icon)
        assertEquals("未锁定", unlocked.contentDescription)
        assertFalse(unlocked.highlighted)
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
                playerViewport = "2400 x 1080",
                cdnHost = "upos.example.com",
                cdnIndex = "2/3",
                networkType = "WiFi",
                forwardBuffer = "0 ms",
                lastLoadError = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: timeout",
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
                DebugStatRow("Player viewport", "2400 x 1080"),
                DebugStatRow("CDN host", "upos.example.com"),
                DebugStatRow("CDN index", "2/3"),
                DebugStatRow("Network type", "WiFi"),
                DebugStatRow("Forward buffer", "0 ms"),
                DebugStatRow("Last load error", "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: timeout"),
                DebugStatRow("Last video event", "first frame rendered"),
                DebugStatRow("Last audio event", "audio sink recovered")
            ),
            rows
        )
    }

    @Test
    fun playbackInsight_usesOnlyAvailableMeasuredValuesAndGroupsDetails() {
        val insight = resolvePlaybackInsightPresentation(
            PlaybackDebugInfo(
                resolution = "3840 x 2160",
                videoCodec = "HEVC",
                frameRate = "59.94 fps",
                videoDecoder = "c2.qti.hevc.decoder",
                playbackState = "READY",
                droppedFrames = "0"
            )
        )

        assertEquals("3840 x 2160 · HEVC · 59.94 fps", insight.summary)
        assertEquals(PlaybackInsightLevel.LIVE, insight.level)
        assertEquals("实时数据", insight.statusText)
        assertEquals(
            listOf("视频编码", "帧率", "视频解码器", "掉帧"),
            insight.sections.getValue(PlaybackInsightSection.VIDEO).map { it.label }
        )
        assertFalse(insight.summary.contains("HDR"))
    }

    @Test
    fun playbackInsight_reportsObservedDroppedFramesWithoutInventingNetworkDiagnosis() {
        val insight = resolvePlaybackInsightPresentation(
            PlaybackDebugInfo(
                videoBitrate = "8.4 Mbps",
                bandwidthEstimate = "2.1 Mbps",
                droppedFrames = "7"
            )
        )

        assertEquals(PlaybackInsightLevel.ATTENTION, insight.level)
        assertEquals("已记录 7 个掉帧", insight.statusText)
        assertFalse(insight.statusText.contains("带宽"))
        assertFalse(insight.statusText.contains("网络"))
    }

    @Test
    fun playbackInsight_marksMissingDataAsUnavailable() {
        val insight = resolvePlaybackInsightPresentation(PlaybackDebugInfo())

        assertEquals(PlaybackInsightLevel.UNAVAILABLE, insight.level)
        assertEquals("等待播放器数据", insight.summary)
        assertTrue(insight.sections.isEmpty())
    }

    @Test
    fun playbackInsightHud_respectsModeControlsAttentionAndLock() {
        val smartMode = PlayerSettingsStore.PlayerInsightMode.SMART
        val alwaysMode = PlayerSettingsStore.PlayerInsightMode.ALWAYS

        assertFalse(shouldShowPlaybackInsightHud(smartMode, true, false, false, PlaybackInsightLevel.LIVE))
        assertTrue(shouldShowPlaybackInsightHud(smartMode, true, true, false, PlaybackInsightLevel.LIVE))
        assertTrue(shouldShowPlaybackInsightHud(smartMode, true, false, false, PlaybackInsightLevel.ATTENTION))
        assertTrue(shouldShowPlaybackInsightHud(alwaysMode, true, false, false, PlaybackInsightLevel.LIVE))
        assertFalse(shouldShowPlaybackInsightHud(alwaysMode, true, true, true, PlaybackInsightLevel.ATTENTION))
        assertFalse(shouldShowPlaybackInsightHud(alwaysMode, false, true, false, PlaybackInsightLevel.LIVE))
    }

    @Test
    fun playbackInsightPanel_isCompactInLandscapeAndBoundedInPortrait() {
        assertEquals(
            PlaybackInsightPanelLayoutPolicy(widthDp = 374, maxHeightDp = 360, edgePaddingDp = 16),
            resolvePlaybackInsightPanelLayoutPolicy(screenWidthDp = 891, screenHeightDp = 411)
        )
        assertEquals(
            PlaybackInsightPanelLayoutPolicy(widthDp = 361, maxHeightDp = 520, edgePaddingDp = 16),
            resolvePlaybackInsightPanelLayoutPolicy(screenWidthDp = 393, screenHeightDp = 852)
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
                playerViewport = "2400 x 1080",
                cdnHost = "upos.example.com",
                cdnIndex = "2/3",
                networkType = "WiFi",
                forwardBuffer = "0 ms",
                lastLoadError = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: timeout",
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
        assertTrue(report.contains("Player viewport: 2400 x 1080"))
        assertTrue(report.contains("CDN host: upos.example.com"))
        assertTrue(report.contains("CDN index: 2/3"))
        assertTrue(report.contains("Network type: WiFi"))
        assertTrue(report.contains("Forward buffer: 0 ms"))
        assertTrue(report.contains("Last load error: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: timeout"))
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

    @Test
    fun inlineControlsAutoHide_staysOffWhileSeekScrubbing() {
        assertFalse(
            shouldAutoHideInlineControlsAfterDelay(
                controlsVisible = true,
                isPlaying = true,
                isSeekScrubbing = true
            )
        )
    }

    @Test
    fun inlineControlsAutoHide_allowsPlayingIdleControlsToHide() {
        assertTrue(
            shouldAutoHideInlineControlsAfterDelay(
                controlsVisible = true,
                isPlaying = true,
                isSeekScrubbing = false
            )
        )
    }

    @Test
    fun inlineControlsAutoHide_staysOffWhileFloatingPanelVisible() {
        assertFalse(
            shouldAutoHideInlineControlsAfterDelay(
                controlsVisible = true,
                isPlaying = true,
                isSeekScrubbing = false,
                floatingPanelVisible = true
            )
        )
    }

    @Test
    fun hiddenInlineControls_cancelActiveSeekScrub() {
        assertTrue(
            shouldCancelSeekScrubWhenControlsHidden(
                controlsVisible = false,
                isSeekScrubbing = true
            )
        )
        assertFalse(
            shouldCancelSeekScrubWhenControlsHidden(
                controlsVisible = true,
                isSeekScrubbing = true
            )
        )
    }

}
