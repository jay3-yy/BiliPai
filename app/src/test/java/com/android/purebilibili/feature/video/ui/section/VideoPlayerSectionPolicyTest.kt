package com.android.purebilibili.feature.video.ui.section

import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import com.android.purebilibili.feature.video.playback.session.PlaybackSeekSessionState
import com.android.purebilibili.feature.video.playback.session.startPlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.session.syncPlaybackSeekSession
import com.android.purebilibili.feature.video.playback.session.updatePlaybackSeekInteraction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoPlayerSectionPolicyTest {

    @Test
    fun inlineAmbientCapture_stopsWhenPortraitFullscreenOwnsPlayback() {
        assertTrue(
            shouldCaptureInlineStatusBarAmbientFrame(
                contentTopInsetPx = 48f,
                isFullscreen = false,
                isPortraitFullscreen = false,
                isInPipMode = false,
                hostLifecycleStarted = true,
                statusBarHazeEnabled = true,
            )
        )
        assertFalse(
            shouldCaptureInlineStatusBarAmbientFrame(
                contentTopInsetPx = 48f,
                isFullscreen = false,
                isPortraitFullscreen = true,
                isInPipMode = false,
                hostLifecycleStarted = true,
                statusBarHazeEnabled = true,
            )
        )
        assertFalse(
            shouldCaptureInlineStatusBarAmbientFrame(
                contentTopInsetPx = 0f,
                isFullscreen = false,
                isPortraitFullscreen = false,
                isInPipMode = false,
                hostLifecycleStarted = true,
                statusBarHazeEnabled = true,
            )
        )
    }

    @Test
    fun inlinePlayerGestures_disabledWhilePortraitFullscreenOwnsTouch() {
        assertTrue(shouldEnableInlinePlayerGestures(isPortraitFullscreen = false))
        assertFalse(shouldEnableInlinePlayerGestures(isPortraitFullscreen = true))
    }

    @Test
    fun inlineDanmakuHostEffects_stoppedDuringPortraitFullscreen() {
        assertTrue(
            shouldRunVideoPlayerDanmakuHostEffects(
                danmakuHostActive = true,
                hostLifecycleStarted = true,
                isPortraitFullscreen = false,
            )
        )
        assertFalse(
            shouldRunVideoPlayerDanmakuHostEffects(
                danmakuHostActive = true,
                hostLifecycleStarted = true,
                isPortraitFullscreen = true,
            )
        )
    }

    @Test
    fun videoPlayerSection_gatesGesturesAndAmbientOnPortraitFullscreen() {
        val source = loadVideoPlayerSectionSource()

        assertTrue(source.contains("shouldEnableInlinePlayerGestures(isPortraitFullscreen)"))
        assertTrue(source.contains("shouldCaptureInlineStatusBarAmbientFrame("))
        assertTrue(source.contains("isPortraitFullscreen = isPortraitFullscreen"))
    }

    @Test
    fun portraitDanmakuOverlay_configuresPassiveTouchSoComposeReceivesGestures() {
        val source = java.io.File(
            "src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt"
        ).readText()

        val overlayBlock = source.substringAfter("private fun PortraitDanmakuOverlay(")
            .substringBefore("internal fun resolvePortraitPagerRepeatMode")
        assertTrue(overlayBlock.contains("configureAsPassiveDanmakuOverlay()"))
        assertTrue(source.contains("import com.android.purebilibili.feature.video.danmaku.configureAsPassiveDanmakuOverlay"))
    }

    @Test
    fun progressPolling_stopsWhenPlayerChromeAndSeekInteractionsAreIdle() {
        assertFalse(
            shouldPollVideoPlayerProgress(
                controlsVisible = false,
                gestureVisible = false,
                isSliderMoving = false,
                hasPendingSeek = false
            )
        )
        assertTrue(
            shouldPollVideoPlayerProgress(
                controlsVisible = true,
                gestureVisible = false,
                isSliderMoving = false,
                hasPendingSeek = false
            )
        )
        assertTrue(
            shouldPollVideoPlayerProgress(
                controlsVisible = false,
                gestureVisible = false,
                isSliderMoving = true,
                hasPendingSeek = false
            )
        )
        assertTrue(
            shouldPollVideoPlayerProgress(
                controlsVisible = false,
                gestureVisible = false,
                isSliderMoving = false,
                hasPendingSeek = true
            )
        )
    }

    @Test
    fun subtitlePolling_isOwnedByIsolatedOverlayHost() {
        val source = loadVideoPlayerSectionSource()
        val subtitleHost = source
            .substringAfter("private fun BoxScope.VideoSubtitleOverlayHost(")
            .substringBefore("fun VideoPlayerSection(")
        val playerSection = source.substringAfter("fun VideoPlayerSection(")

        assertTrue(subtitleHost.contains("val subtitlePositionMs by produceState("))
        assertFalse(playerSection.contains("val subtitlePositionMs by produceState("))
        assertFalse(playerSection.contains("DanmakuView check:"))
        assertFalse(playerSection.contains("Conditions met, creating DanmakuView"))
    }

    @Test
    fun publicEntryPoint_usesSmallStateAndActionContracts() {
        val source = loadVideoPlayerSectionSource()
        val publicEntry = source
            .substringAfter("fun VideoPlayerSection(")
            .substringBefore(") {")

        assertTrue(source.contains("internal fun VideoPlayerSection("))
        assertTrue(publicEntry.contains("state: VideoPlayerSectionState"))
        assertTrue(publicEntry.contains("actions: VideoPlayerSectionActions"))
        assertFalse(publicEntry.contains("playerState: VideoPlayerState"))
        assertFalse(publicEntry.contains("onToggleFullscreen: () -> Unit"))
        assertTrue(source.contains("private fun VideoPlayerSectionContent("))
    }

    @Test
    fun autoFullscreen_snapshotDoesNotReenterAfterFullscreenPlayerIsRecreated() {
        assertFalse(
            shouldToggleAutoFullscreenForCurrentPlaybackSnapshot(
                autoEnterFullscreenEnabled = true,
                autoExitFullscreenEnabled = false,
                allowPlaybackStateAutoFullscreen = true,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                hasAutoEnteredFullscreen = false,
                isFullscreen = false,
            )
        )
    }

    @Test
    fun coverCorner_sharedReturnKeepsFrozenSourceCardCorner() {
        assertEquals(
            18,
            resolveVideoPlayerCoverCornerDp(
                sourceCornerDp = 18,
                playerCornerDp = 12,
                preserveSourceCardCornerDuringSharedReturn = true,
            )
        )
    }

    @Test
    fun coverCorner_normalPlayerKeepsPlayerCorner() {
        assertEquals(
            12,
            resolveVideoPlayerCoverCornerDp(
                sourceCornerDp = 18,
                playerCornerDp = 12,
                preserveSourceCardCornerDuringSharedReturn = false,
            )
        )
    }

    @Test
    fun playerControls_areHiddenWhenEnteringVideo() {
        assertFalse(INITIAL_PLAYER_CONTROLS_VISIBLE)
        assertTrue(INITIAL_PLAYER_CHROME_AUTO_HIDE_HANDLED)
    }

    @Test
    fun playerInteractionIdentity_tracksActualMediaAcrossAutoAdvance() {
        assertEquals(
            "BV-next_202",
            resolvePlayerInteractionIdentity(
                routeBvid = "BV-route",
                playbackBvid = "BV-next",
                playbackCid = 202L
            )
        )
        assertEquals(
            "BV-route_303",
            resolvePlayerInteractionIdentity(
                routeBvid = "BV-route",
                playbackBvid = "",
                playbackCid = 303L
            )
        )
    }

    @Test
    fun dragStart_ignoresBottomControlZone() {
        assertTrue(
            shouldIgnoreVideoPlayerDragStart(
                offsetY = 940f,
                containerHeightPx = 1_000f,
                topGestureExclusionPx = 48f,
                bottomGestureExclusionPx = 120f
            )
        )
    }

    @Test
    fun dragStart_allowsCenterZoneAboveBottomControls() {
        assertFalse(
            shouldIgnoreVideoPlayerDragStart(
                offsetY = 700f,
                containerHeightPx = 1_000f,
                topGestureExclusionPx = 48f,
                bottomGestureExclusionPx = 120f
            )
        )
    }

    @Test
    fun fullscreenDragStart_ignoresHorizontalSystemGestureEdges() {
        assertTrue(shouldIgnoreVideoPlayerHorizontalEdgeDragStart(20f, 1_000f, true, 48f))
        assertTrue(shouldIgnoreVideoPlayerHorizontalEdgeDragStart(980f, 1_000f, true, 48f))
        assertFalse(shouldIgnoreVideoPlayerHorizontalEdgeDragStart(500f, 1_000f, true, 48f))
        assertFalse(shouldIgnoreVideoPlayerHorizontalEdgeDragStart(20f, 1_000f, false, 48f))
    }

    @Test
    fun bottomGestureExclusion_includesExpandedProgressPreviewContainer() {
        assertEquals(
            186,
            resolveVideoPlayerBottomGestureExclusionHeightDp(
                controlBarBottomPaddingDp = 14,
                progressSpacingDp = 10,
                progressContainerHeightDp = 120,
                controlRowHeightDp = 30,
                extraBufferDp = 12
            )
        )
    }

    @Test
    fun bottomGestureExclusion_allowsLandscapeCenterDragWhenProgressPreviewIsHidden() {
        val visibleControlExclusionPx = resolveVideoPlayerBottomGestureExclusionHeightDp(
            controlBarBottomPaddingDp = 14,
            progressSpacingDp = 10,
            progressContainerHeightDp = 40,
            controlRowHeightDp = 40,
            extraBufferDp = 12
        ).toFloat()

        assertFalse(
            shouldIgnoreVideoPlayerDragStart(
                offsetY = 210f,
                containerHeightPx = 360f,
                topGestureExclusionPx = 48f,
                bottomGestureExclusionPx = visibleControlExclusionPx
            )
        )
    }

    @Test
    fun bottomGestureExclusion_usesVisibleProgressHeightInVideoPlayerSection() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/ui/section/VideoPlayerSection.kt")
            .readText()

        assertTrue(
            source.contains("progressContainerHeightDp = videoProgressBarLayoutPolicy.baseHeightWithChapterDp"),
            "Background drag exclusion should use the visible idle progress bar height; the expanded preview height is only visible while the progress bar itself is scrubbing."
        )
    }

    @Test
    fun dragStart_ignoresProgressPreviewZoneAboveBottomControls() {
        assertTrue(
            shouldIgnoreVideoPlayerDragStart(
                offsetY = 830f,
                containerHeightPx = 1_000f,
                topGestureExclusionPx = 48f,
                bottomGestureExclusionPx = 186f
            )
        )
    }

    @Test
    fun inlineGestureExclusions_keepHalfOfShortPlayerInteractive() {
        val exclusions = resolveVideoPlayerGestureVerticalExclusions(
            containerHeightPx = 120f,
            isFullscreen = false,
            controlsVisible = true,
            requestedBottomControlsExclusionPx = 100f,
            inlineTopExclusionPx = 24f,
            inlineBottomExclusionPx = 48f,
            fullscreenEdgeExclusionPx = 48f
        )

        assertEquals(20f, exclusions.topPx, 0.001f)
        assertEquals(40f, exclusions.bottomPx, 0.001f)
    }

    @Test
    fun gestureSeekDuration_usesFallbackWhenPlayerDurationIsUnset() {
        assertEquals(
            120_000L,
            resolveGestureSeekableDurationMs(
                playbackDurationMs = 0L,
                fallbackDurationMs = 120_000L
            )
        )
    }

    @Test
    fun relativeSeekTarget_clampsBackwardAtZero() {
        assertEquals(
            0L,
            resolveRelativeSeekTargetPosition(
                currentPositionMs = 3_000L,
                deltaMs = -10_000L,
                durationMs = 120_000L
            )
        )
    }

    @Test
    fun relativeSeekTarget_clampsForwardAtKnownDuration() {
        assertEquals(
            120_000L,
            resolveRelativeSeekTargetPosition(
                currentPositionMs = 118_000L,
                deltaMs = 10_000L,
                durationMs = 120_000L
            )
        )
    }

    @Test
    fun relativeSeekTarget_keepsOpenEndedStreamsUnclamped() {
        assertEquals(
            65_000L,
            resolveRelativeSeekTargetPosition(
                currentPositionMs = 55_000L,
                deltaMs = 10_000L,
                durationMs = 0L
            )
        )
    }

    @Test
    fun systemStreamVolumeFromGesture_increasesOnUpwardDrag() {
        assertEquals(
            9,
            resolveSystemStreamVolumeFromGesture(
                startVolumeStep = 5,
                maxVolumeStep = 15,
                totalDragDistanceY = -300f,
                screenHeightPx = 1_200f,
                gestureSensitivity = 1.0f
            )
        )
    }

    @Test
    fun systemStreamVolumeFromGesture_decreasesOnDownwardDrag() {
        assertEquals(
            2,
            resolveSystemStreamVolumeFromGesture(
                startVolumeStep = 5,
                maxVolumeStep = 15,
                totalDragDistanceY = 240f,
                screenHeightPx = 1_200f,
                gestureSensitivity = 1.0f
            )
        )
    }

    @Test
    fun systemStreamVolumeFromGesture_clampsToSystemRange() {
        assertEquals(
            15,
            resolveSystemStreamVolumeFromGesture(
                startVolumeStep = 14,
                maxVolumeStep = 15,
                totalDragDistanceY = -1_200f,
                screenHeightPx = 1_200f,
                gestureSensitivity = 2.0f
            )
        )
        assertEquals(
            0,
            resolveSystemStreamVolumeFromGesture(
                startVolumeStep = 1,
                maxVolumeStep = 15,
                totalDragDistanceY = 1_200f,
                screenHeightPx = 1_200f,
                gestureSensitivity = 2.0f
            )
        )
    }

    @Test
    fun systemStreamVolumeFromGesture_returnsZeroForInvalidSystemRange() {
        assertEquals(
            0,
            resolveSystemStreamVolumeFromGesture(
                startVolumeStep = 5,
                maxVolumeStep = 0,
                totalDragDistanceY = -300f,
                screenHeightPx = 1_200f,
                gestureSensitivity = 1.0f
            )
        )
    }

    @Test
    fun pinchExitFullscreen_onlyTriggersForInwardPinchWithoutConflictingGestures() {
        assertTrue(
            shouldTriggerPinchExitFullscreen(
                isFullscreen = true,
                isScreenLocked = false,
                twoFingerSpeedAxisLocked = false,
                currentViewportScale = 1.0f,
                cumulativeZoom = 0.78f,
                minExitZoom = 0.82f
            )
        )
        assertFalse(
            shouldTriggerPinchExitFullscreen(
                isFullscreen = true,
                isScreenLocked = false,
                twoFingerSpeedAxisLocked = true,
                currentViewportScale = 1.0f,
                cumulativeZoom = 0.78f,
                minExitZoom = 0.82f
            )
        )
        assertFalse(
            shouldTriggerPinchExitFullscreen(
                isFullscreen = true,
                isScreenLocked = false,
                twoFingerSpeedAxisLocked = false,
                currentViewportScale = 1.4f,
                cumulativeZoom = 0.78f,
                minExitZoom = 0.82f
            )
        )
    }

    @Test
    fun keepScreenAwake_onlyWhilePlaybackIsActiveOrStarting() {
        assertTrue(
            shouldKeepVideoPlaybackAwake(
                playWhenReady = true,
                isPlaying = true,
                playbackState = Player.STATE_READY
            )
        )
        assertTrue(
            shouldKeepVideoPlaybackAwake(
                playWhenReady = true,
                isPlaying = false,
                playbackState = Player.STATE_BUFFERING
            )
        )
        assertTrue(
            shouldKeepVideoPlaybackAwake(
                playWhenReady = true,
                isPlaying = false,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun keepScreenAwake_allowsSleepBeforeStartPausedAndEnded() {
        assertFalse(
            shouldKeepVideoPlaybackAwake(
                playWhenReady = false,
                isPlaying = false,
                playbackState = Player.STATE_IDLE
            )
        )
        assertFalse(
            shouldKeepVideoPlaybackAwake(
                playWhenReady = false,
                isPlaying = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldKeepVideoPlaybackAwake(
                playWhenReady = true,
                isPlaying = false,
                playbackState = Player.STATE_ENDED
            )
        )
    }

    @Test
    fun danmakuLayerTopOffset_keepsInlinePortraitDanmakuAnchoredToViewportTop() {
        assertEquals(
            0,
            resolveDanmakuLayerTopOffsetPx(
                isFullscreen = false,
                statusBarHeightPx = 96
            )
        )
        assertEquals(
            0,
            resolveDanmakuLayerTopOffsetPx(
                isFullscreen = true,
                statusBarHeightPx = 96,
                useScreenTopSurface = false
            )
        )
        assertEquals(
            96,
            resolveDanmakuLayerTopOffsetPx(
                isFullscreen = true,
                statusBarHeightPx = 96,
                useScreenTopSurface = true
            )
        )
    }

    @Test
    fun screenTopDanmakuSurface_onlyAppliesOutsideLandscapeFullscreen() {
        assertTrue(
            shouldUseScreenTopDanmakuSurface(
                portraitDisplayAreaMode =
                    com.android.purebilibili.core.store.PortraitDanmakuDisplayAreaMode.SCREEN_TOP,
                isLandscapeFullscreen = false
            )
        )
        assertFalse(
            shouldUseScreenTopDanmakuSurface(
                portraitDisplayAreaMode =
                    com.android.purebilibili.core.store.PortraitDanmakuDisplayAreaMode.SCREEN_TOP,
                isLandscapeFullscreen = true
            )
        )
        assertFalse(
            shouldUseScreenTopDanmakuSurface(
                portraitDisplayAreaMode =
                    com.android.purebilibili.core.store.PortraitDanmakuDisplayAreaMode.VIDEO_VIEWPORT,
                isLandscapeFullscreen = false
            )
        )
    }

    @Test
    fun danmakuSettingsScope_keepsPortraitProfileDuringPortraitFullscreenTransition() {
        assertEquals(
            com.android.purebilibili.core.store.DanmakuSettingsScope.LANDSCAPE,
            resolveVideoPlayerDanmakuSettingsScope(
                isFullscreen = true,
                isPortraitFullscreen = false
            )
        )
        assertEquals(
            com.android.purebilibili.core.store.DanmakuSettingsScope.PORTRAIT,
            resolveVideoPlayerDanmakuSettingsScope(
                isFullscreen = true,
                isPortraitFullscreen = true
            )
        )
        assertEquals(
            com.android.purebilibili.core.store.DanmakuSettingsScope.PORTRAIT,
            resolveVideoPlayerDanmakuSettingsScope(
                isFullscreen = false,
                isPortraitFullscreen = false
            )
        )
    }

    @Test
    fun inlinePlayerTakeover_disablesKeepingLastFrame_whenPortraitFullscreenOwnsPlayback() {
        assertFalse(
            shouldKeepInlinePlayerContentOnReset(
                isPortraitFullscreen = true,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertTrue(
            shouldKeepInlinePlayerContentOnReset(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = false
            )
        )
    }

    @Test
    fun inlinePlayerTakeover_hidesInlinePlayerView_whenPortraitFullscreenOwnsPlayback() {
        assertFalse(
            shouldShowInlinePlayerView(
                isPortraitFullscreen = true,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertTrue(
            shouldShowInlinePlayerView(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = false
            )
        )
    }

    @Test
    fun forcedReturnTakeover_hidesInlinePlayerButKeepsItsSurfaceBound() {
        assertFalse(
            shouldKeepInlinePlayerContentOnReset(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = true
            )
        )
        assertFalse(
            shouldShowInlinePlayerView(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = true
            )
        )
        assertTrue(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = true,
                isInPipMode = false
            )
        )
    }

    @Test
    fun danmakuLayers_hidden_whenHostLifecycleStopped() {
        assertFalse(
            shouldShowDanmakuLayers(
                isInPipMode = false,
                danmakuEnabled = true,
                isPortraitFullscreen = false,
                pipNoDanmakuEnabled = false,
                hostLifecycleStarted = false
            )
        )
        assertTrue(
            shouldShowDanmakuLayers(
                isInPipMode = false,
                danmakuEnabled = true,
                isPortraitFullscreen = false,
                pipNoDanmakuEnabled = false,
                hostLifecycleStarted = true
            )
        )
    }

    @Test
    fun livePlayerSharedElement_enabledOnlyWhenAllGuardsPass() {
        assertTrue(
            shouldEnableLivePlayerSharedElement(
                transitionEnabled = true,
                allowLivePlayerSharedElement = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun livePlayerSharedElement_disabledWhenPredictiveBackRequiresStability() {
        assertFalse(
            shouldEnableLivePlayerSharedElement(
                transitionEnabled = true,
                allowLivePlayerSharedElement = false,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun livePlayerSharedElement_disabledWhenTransitionSwitchOff() {
        assertFalse(
            shouldEnableLivePlayerSharedElement(
                transitionEnabled = false,
                allowLivePlayerSharedElement = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun livePlayerSharedElement_disabledWhenReturnCoverTakesOverSharedBounds() {
        assertFalse(
            shouldEnableLivePlayerSharedElement(
                transitionEnabled = true,
                allowLivePlayerSharedElement = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true,
                forceCoverDuringReturnAnimation = true
            )
        )
    }

    @Test
    fun livePlayerSharedElement_disabledUnderHdrSurfaceOutput() {
        assertFalse(
            shouldEnableLivePlayerSharedElement(
                transitionEnabled = true,
                allowLivePlayerSharedElement = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true,
                requiresHdrSurfaceOutput = true
            )
        )
    }

    @Test
    fun playerSurfaceRebind_onlyWhenForegroundVideoSurfaceNeedsRecovery() {
        assertTrue(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = false,
                videoWidth = 0,
                videoHeight = 0,
                needsSurfaceRecovery = true
            )
        )
        assertFalse(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = false,
                videoWidth = 1920,
                videoHeight = 1080,
                needsSurfaceRecovery = false
            )
        )
        assertTrue(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = false,
                videoWidth = 1920,
                videoHeight = 1080,
                needsSurfaceRecovery = true
            )
        )
    }

    @Test
    fun playerSurfaceRebind_skipsOnlyWhenPipOrPlayerViewMissingOrSurfaceHealthy() {
        assertFalse(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = true,
                videoWidth = 1920,
                videoHeight = 1080,
                needsSurfaceRecovery = true
            )
        )
        assertTrue(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = false,
                videoWidth = 0,
                videoHeight = 1080,
                needsSurfaceRecovery = false
            )
        )
        assertFalse(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = false,
                isInPipMode = false,
                videoWidth = 1920,
                videoHeight = 1080,
                needsSurfaceRecovery = true
            )
        )
    }

    @Test
    fun foregroundSurfaceRecovery_skipsRetryWhenSurfaceAlreadyHealthy() {
        assertFalse(
            shouldStartForegroundSurfaceRecovery(
                hasPlayerView = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = false,
                needsSurfaceRecovery = false,
                videoWidth = 1920,
                videoHeight = 1080
            )
        )
        assertTrue(
            shouldStartForegroundSurfaceRecovery(
                hasPlayerView = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = false,
                needsSurfaceRecovery = true,
                videoWidth = 0,
                videoHeight = 0
            )
        )
    }

    @Test
    fun foregroundSurfaceRecovery_runsOnlyForInlineForegroundPlayerView() {
        assertTrue(
            shouldStartForegroundSurfaceRecovery(
                hasPlayerView = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = false
            )
        )
        assertFalse(
            shouldStartForegroundSurfaceRecovery(
                hasPlayerView = false,
                shouldBindInlinePlayerView = true,
                isInPipMode = false
            )
        )
        assertFalse(
            shouldStartForegroundSurfaceRecovery(
                hasPlayerView = true,
                shouldBindInlinePlayerView = false,
                isInPipMode = false
            )
        )
        assertFalse(
            shouldStartForegroundSurfaceRecovery(
                hasPlayerView = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = true
            )
        )
    }

    @Test
    fun foregroundSurfaceRecovery_kicksPlaybackOnlyWhenRenderChainLooksStuck() {
        assertTrue(
            shouldKickPlaybackAfterSurfaceRecovery(
                playWhenReady = true,
                isPlaying = false,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
        assertTrue(
            shouldKickPlaybackAfterSurfaceRecovery(
                playWhenReady = true,
                isPlaying = false,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING
            )
        )
        assertFalse(
            shouldKickPlaybackAfterSurfaceRecovery(
                playWhenReady = false,
                isPlaying = false,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
        assertFalse(
            shouldKickPlaybackAfterSurfaceRecovery(
                playWhenReady = true,
                isPlaying = true,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
        assertFalse(
            shouldKickPlaybackAfterSurfaceRecovery(
                playWhenReady = true,
                isPlaying = false,
                playbackState = androidx.media3.common.Player.STATE_READY,
                hasPlaybackResumeIntent = false
            )
        )
    }

    @Test
    fun foregroundRecoveryWatchdog_logsOnlyWhenNoFrameReturns() {
        assertTrue(
            shouldLogForegroundSurfaceRecoveryTimeout(
                hasRenderedFirstFrameSinceRecovery = false,
                playWhenReady = true,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
        assertTrue(
            shouldLogForegroundSurfaceRecoveryTimeout(
                hasRenderedFirstFrameSinceRecovery = false,
                playWhenReady = true,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING
            )
        )
        assertFalse(
            shouldLogForegroundSurfaceRecoveryTimeout(
                hasRenderedFirstFrameSinceRecovery = true,
                playWhenReady = true,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
        assertFalse(
            shouldLogForegroundSurfaceRecoveryTimeout(
                hasRenderedFirstFrameSinceRecovery = false,
                playWhenReady = false,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
    }

    @Test
    fun playbackStallLogging_requiresMeaningfulDelayDuringActivePlayback() {
        assertTrue(
            shouldLogPlaybackStall(
                bufferingDurationMs = 900L,
                playWhenReady = true,
                currentPositionMs = 12_000L
            )
        )
        assertFalse(
            shouldLogPlaybackStall(
                bufferingDurationMs = 300L,
                playWhenReady = true,
                currentPositionMs = 12_000L
            )
        )
        assertFalse(
            shouldLogPlaybackStall(
                bufferingDurationMs = 900L,
                playWhenReady = false,
                currentPositionMs = 12_000L
            )
        )
        assertFalse(
            shouldLogPlaybackStall(
                bufferingDurationMs = 900L,
                playWhenReady = true,
                currentPositionMs = 0L
            )
        )
    }

    @Test
    fun inlinePlayerBinding_keepsSurfaceAttached_onlyWhenForegroundOrPip() {
        assertTrue(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = true,
                isInPipMode = false
            )
        )
        assertTrue(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = false,
                isInPipMode = true
            )
        )
    }

    @Test
    fun inlinePlayerBinding_detachesSurface_whenBackgroundedOrPortraitFullscreenOwnsPlayback() {
        assertFalse(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = false,
                isInPipMode = false
            )
        )
        assertFalse(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = true,
                hostLifecycleStarted = true,
                isInPipMode = false
            )
        )
    }

    @Test
    fun inlinePlayerBinding_attachesBackgroundTarget_forLiveBackPreview() {
        assertTrue(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = false,
                isInPipMode = false,
                liveBackPreview = true
            )
        )
        assertFalse(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = true,
                hostLifecycleStarted = false,
                isInPipMode = false,
                liveBackPreview = true
            )
        )
    }

    @Test
    fun predictiveBackCancelRecovery_runsOnlyForTheCurrentBoundInlinePlayer() {
        assertTrue(
            shouldRecoverInlinePlayerAfterPredictiveBackCancel(
                recoveryGeneration = 1,
                hasPlayerView = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = false
            )
        )
        assertFalse(
            shouldRecoverInlinePlayerAfterPredictiveBackCancel(
                recoveryGeneration = 1,
                hasPlayerView = true,
                shouldBindInlinePlayerView = false,
                isInPipMode = false
            )
        )
    }

    @Test
    fun danmakuReload_runsOnlyWhenForegroundHostCanActuallyLoad() {
        assertTrue(
            shouldLoadDanmakuForForegroundHost(
                hostLifecycleStarted = true,
                shouldLoadImmediately = true
            )
        )
        assertFalse(
            shouldLoadDanmakuForForegroundHost(
                hostLifecycleStarted = false,
                shouldLoadImmediately = true
            )
        )
        assertFalse(
            shouldLoadDanmakuForForegroundHost(
                hostLifecycleStarted = true,
                shouldLoadImmediately = false
            )
        )
    }

    @Test
    fun longPressSpeed_keepsHiResAtConfiguredSpeed() {
        val effective = resolveEffectiveLongPressSpeed(
            requestedSpeed = 3.0f,
            currentAudioQuality = 30251
        )

        assertEquals(3.0f, effective)
    }

    @Test
    fun longPressSpeed_honorsConfiguredSpeedForHiResAudio() {
        val effective = resolveEffectiveLongPressSpeed(
            requestedSpeed = 2.5f,
            currentAudioQuality = 30251
        )

        assertEquals(2.5f, effective)
    }

    @Test
    fun longPressSpeed_keepsStandardAudioAtRequestedHighSpeed() {
        val effective = resolveEffectiveLongPressSpeed(
            requestedSpeed = 3.0f,
            currentAudioQuality = 30280
        )

        assertTrue(effective == 3.0f)
    }

    @Test
    fun longPressSpeed_keepsRequestedLowSpeed() {
        val effective = resolveEffectiveLongPressSpeed(
            requestedSpeed = 1.25f,
            currentAudioQuality = 30280
        )

        assertTrue(effective == 1.25f)
    }

    @Test
    fun longPressPlaybackParameters_forceNaturalPitch() {
        val parameters = resolveLongPressPlaybackParameters(
            requestedSpeed = 1.25f,
            currentAudioQuality = 30280
        )

        assertEquals(1.25f, parameters.speed)
        assertEquals(1.0f, parameters.pitch)
    }

    @Test
    fun longPressPlaybackParameters_keepHiResConfiguredSpeedWithNaturalPitch() {
        val parameters = resolveLongPressPlaybackParameters(
            requestedSpeed = 3.0f,
            currentAudioQuality = 30251
        )

        assertEquals(PlaybackParameters(3.0f, 1.0f), parameters)
    }

    @Test
    fun explicitPlaybackParameters_keepHiResSpeedWithNaturalPitch() {
        val parameters = resolveSpeedSafePlaybackParameters(
            requestedSpeed = 2.0f,
            currentAudioQuality = 30251
        )

        assertEquals(PlaybackParameters(2.0f, 1.0f), parameters)
    }

    @Test
    fun longPressPlaybackParameters_keepStandardAudioRequestedHighSpeed() {
        val parameters = resolveLongPressPlaybackParameters(
            requestedSpeed = 3.0f,
            currentAudioQuality = 30280
        )

        assertEquals(PlaybackParameters(3.0f, 1.0f), parameters)
    }

    @Test
    fun longPressStart_capturesCurrentPlaybackParameters_whenNoLockIsActive() {
        val decision = resolveLongPressSpeedStartDecision(
            currentPlaybackParameters = PlaybackParameters(1.25f, 1.0f),
            previousOriginalPlaybackParameters = PlaybackParameters.DEFAULT,
            longPressSpeedLocked = false,
            requestedSpeed = 2.0f,
            currentAudioQuality = 30280
        )

        assertEquals(PlaybackParameters(1.25f, 1.0f), decision.originalPlaybackParameters)
        assertEquals(PlaybackParameters(2.0f, 1.0f), decision.targetPlaybackParameters)
        assertFalse(decision.clearExistingLock)
    }

    @Test
    fun longPressStart_preservesPreLockOriginalPlaybackParameters_whenLockAlreadyActive() {
        val decision = resolveLongPressSpeedStartDecision(
            currentPlaybackParameters = PlaybackParameters(2.0f, 1.0f),
            previousOriginalPlaybackParameters = PlaybackParameters(1.0f, 1.0f),
            longPressSpeedLocked = true,
            requestedSpeed = 2.0f,
            currentAudioQuality = 30280
        )

        assertEquals(PlaybackParameters(1.0f, 1.0f), decision.originalPlaybackParameters)
        assertEquals(PlaybackParameters(2.0f, 1.0f), decision.targetPlaybackParameters)
        assertFalse(decision.clearExistingLock)
    }

    @Test
    fun longPressCompatHint_showsOnlyWhenRuntimeSpeedIsReducedFirstTime() {
        assertTrue(
            shouldShowHiResLongPressCompatHint(
                requestedSpeed = 3.0f,
                effectiveSpeed = 1.5f,
                hasShownHint = false
            )
        )
    }

    @Test
    fun longPressCompatHint_staysSilentAfterFirstReminderOrWithoutRuntimeReduction() {
        assertFalse(
            shouldShowHiResLongPressCompatHint(
                requestedSpeed = 3.0f,
                effectiveSpeed = 1.5f,
                hasShownHint = true
            )
        )
        assertFalse(
            shouldShowHiResLongPressCompatHint(
                requestedSpeed = 1.5f,
                effectiveSpeed = 1.5f,
                hasShownHint = false
            )
        )
        assertFalse(
            shouldShowHiResLongPressCompatHint(
                requestedSpeed = 3.0f,
                effectiveSpeed = 3.0f,
                hasShownHint = false
            )
        )
        assertFalse(
            shouldShowHiResLongPressCompatHint(
                requestedSpeed = 1.5004f,
                effectiveSpeed = 1.5f,
                hasShownHint = false
            )
        )
    }

    @Test
    fun playbackReadyAutoFullscreen_enabledForPhonesInOrientationDrivenMode() {
        assertTrue(
            shouldAllowPlaybackStateAutoFullscreen(
                hasValidWindow = true
            )
        )
    }

    @Test
    fun playbackReadyAutoFullscreen_allowsTabletsBecauseSettingIsExplicit() {
        assertTrue(
            shouldAllowPlaybackStateAutoFullscreen(
                hasValidWindow = true
            )
        )
    }

    @Test
    fun playbackStateAutoFullscreen_triggersWhenAttachedAfterPlaybackAlreadyStarted() {
        // 快照路径刻意恒 false：退出全屏后的重组会把「重新组合」误认成「开始播放」，
        // 自动全屏只能由 Player 的实际状态事件补发（见 shouldToggleAutoFullscreenForPlaybackEvent）。
        assertFalse(
            shouldToggleAutoFullscreenForCurrentPlaybackSnapshot(
                autoEnterFullscreenEnabled = true,
                autoExitFullscreenEnabled = true,
                allowPlaybackStateAutoFullscreen = true,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                hasAutoEnteredFullscreen = false,
                isFullscreen = false
            )
        )

        // 事件路径在挂载后采样时补发自动全屏（播放已开始、无 playWhenReady 跳变）
        assertTrue(
            shouldToggleAutoFullscreenForPlaybackEvent(
                autoEnterFullscreenEnabled = true,
                autoExitFullscreenEnabled = true,
                allowPlaybackStateAutoFullscreen = true,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                hasAutoEnteredFullscreen = false,
                isFullscreen = false,
                previousPlayWhenReady = true,
            )
        )
    }

    @Test
    fun playbackSnapshot_doesNotAutoExitFullscreenFromStaleEndedStateDuringContinuousPlayback() {
        assertFalse(
            shouldToggleAutoFullscreenForCurrentPlaybackSnapshot(
                autoEnterFullscreenEnabled = false,
                autoExitFullscreenEnabled = true,
                allowPlaybackStateAutoFullscreen = true,
                playbackState = Player.STATE_ENDED,
                playWhenReady = true,
                hasAutoEnteredFullscreen = true,
                isFullscreen = true,
                willContinueToNextItem = false,
            )
        )
    }

    @Test
    fun playbackStateAutoFullscreen_triggersWhenReadyStartsPlayingInline() {
        assertTrue(
            shouldToggleAutoFullscreenForPlaybackEvent(
                autoEnterFullscreenEnabled = true,
                autoExitFullscreenEnabled = true,
                allowPlaybackStateAutoFullscreen = true,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                hasAutoEnteredFullscreen = false,
                isFullscreen = false
            )
        )
    }

    @Test
    fun autoExitFullscreen_allPartsKeepsFullscreenWhenNextPartWillPlay() {
        assertFalse(
            shouldAutoExitFullscreenOnPlaybackEnded(
                mode = com.android.purebilibili.core.store.AutoExitFullscreenMode.ALL_PARTS,
                isFullscreen = true,
                playbackState = Player.STATE_ENDED,
                willContinueToNextItem = true,
            )
        )
        assertTrue(
            shouldAutoExitFullscreenOnPlaybackEnded(
                mode = com.android.purebilibili.core.store.AutoExitFullscreenMode.ALL_PARTS,
                isFullscreen = true,
                playbackState = Player.STATE_ENDED,
                willContinueToNextItem = false,
            )
        )
        assertTrue(
            shouldAutoExitFullscreenOnPlaybackEnded(
                mode = com.android.purebilibili.core.store.AutoExitFullscreenMode.CURRENT_PART,
                isFullscreen = true,
                playbackState = Player.STATE_ENDED,
                willContinueToNextItem = true,
            )
        )
    }

    @Test
    fun willContinueAfterCurrentItem_detectsMultiPartAndSeason() {
        assertTrue(
            resolveWillContinuePlaybackAfterCurrentItem(
                pageCount = 3,
                currentPageIndex = 0,
                hasUgcSeasonNext = false,
                hasPlaylistNext = false,
                completionAdvancesToNext = true,
            )
        )
        assertFalse(
            resolveWillContinuePlaybackAfterCurrentItem(
                pageCount = 3,
                currentPageIndex = 2,
                hasUgcSeasonNext = false,
                hasPlaylistNext = false,
                completionAdvancesToNext = true,
            )
        )
        assertTrue(
            resolveWillContinuePlaybackAfterCurrentItem(
                pageCount = 1,
                currentPageIndex = 0,
                hasUgcSeasonNext = true,
                hasPlaylistNext = false,
                completionAdvancesToNext = true,
            )
        )
    }

    @Test
    fun playbackStateAutoFullscreen_triggersWhenPlayWhenReadyTurnsTrueAfterReady() {
        assertTrue(
            shouldToggleAutoFullscreenForPlaybackEvent(
                autoEnterFullscreenEnabled = true,
                autoExitFullscreenEnabled = true,
                allowPlaybackStateAutoFullscreen = true,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                hasAutoEnteredFullscreen = false,
                isFullscreen = false,
                previousPlayWhenReady = false
            )
        )
    }

    @Test
    fun playbackStateAutoFullscreen_ignoresRepeatedPlayWhenReadyTrueEventsAfterEntering() {
        assertFalse(
            shouldToggleAutoFullscreenForPlaybackEvent(
                autoEnterFullscreenEnabled = true,
                autoExitFullscreenEnabled = true,
                allowPlaybackStateAutoFullscreen = true,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                hasAutoEnteredFullscreen = true,
                isFullscreen = false,
                previousPlayWhenReady = false
            )
        )
    }

    @Test
    fun autoplayChromeAutoHide_triggersOnceAfterFirstFrameWhilePlaying() {
        assertTrue(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = true,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                isSeekScrubbing = false
            )
        )
    }

    @Test
    fun autoplayChromeAutoHide_staysOffWhileSeekScrubbing() {
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = true,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                isSeekScrubbing = true
            )
        )
    }

    @Test
    fun autoplayChromeAutoHide_staysOffForPausedForcedOrAlreadyHandledStates() {
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = false,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = true,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                isSeekScrubbing = false
            )
        )
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = true,
                isPlaying = true,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                isSeekScrubbing = false
            )
        )
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = false,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                isSeekScrubbing = false
            )
        )
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = true,
                isFirstFrameRendered = false,
                forceCoverDuringReturnAnimation = false,
                isSeekScrubbing = false
            )
        )
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = true,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = true,
                isSeekScrubbing = false
            )
        )
    }

    @Test
    fun longPressSpeedLock_triggersOnlyInTopOrBottomTargetZone() {
        assertTrue(
            shouldLockLongPressSpeedInTargetZone(
                longPressSpeedLockEnabled = true,
                isLongPressing = true,
                alreadyLocked = false,
                currentPointerY = 72f,
                containerHeightPx = 1_000f,
                lockZoneHeightPx = 120f
            )
        )
        assertTrue(
            shouldLockLongPressSpeedInTargetZone(
                longPressSpeedLockEnabled = true,
                isLongPressing = true,
                alreadyLocked = false,
                currentPointerY = 928f,
                containerHeightPx = 1_000f,
                lockZoneHeightPx = 120f
            )
        )
        assertFalse(
            shouldLockLongPressSpeedInTargetZone(
                longPressSpeedLockEnabled = false,
                isLongPressing = true,
                alreadyLocked = false,
                currentPointerY = 72f,
                containerHeightPx = 1_000f,
                lockZoneHeightPx = 120f
            )
        )
        assertFalse(
            shouldLockLongPressSpeedInTargetZone(
                isLongPressing = true,
                alreadyLocked = false,
                currentPointerY = 260f,
                containerHeightPx = 1_000f,
                lockZoneHeightPx = 120f
            )
        )
        assertFalse(
            shouldLockLongPressSpeedInTargetZone(
                isLongPressing = true,
                alreadyLocked = true,
                currentPointerY = 80f,
                containerHeightPx = 1_000f,
                lockZoneHeightPx = 120f
            )
        )
        assertFalse(
            shouldLockLongPressSpeedInTargetZone(
                isLongPressing = false,
                alreadyLocked = false,
                currentPointerY = 80f,
                containerHeightPx = 1_000f,
                lockZoneHeightPx = 120f
            )
        )
        assertFalse(
            shouldLockLongPressSpeedInTargetZone(
                isLongPressing = true,
                alreadyLocked = false,
                currentPointerY = 80f,
                containerHeightPx = 0f,
                lockZoneHeightPx = 120f
            )
        )
    }

    @Test
    fun longPressSpeedLock_requiresConfiguredDragDistanceBeforeLocking() {
        assertFalse(
            shouldLockLongPressSpeedInTargetZone(
                isLongPressing = true,
                alreadyLocked = false,
                currentPointerY = 72f,
                containerHeightPx = 1_000f,
                lockZoneHeightPx = 120f,
                accumulatedDragYPx = 20f,
                minDragDistancePx = 56f
            )
        )
        assertTrue(
            shouldLockLongPressSpeedInTargetZone(
                isLongPressing = true,
                alreadyLocked = false,
                currentPointerY = 72f,
                containerHeightPx = 1_000f,
                lockZoneHeightPx = 120f,
                accumulatedDragYPx = -80f,
                minDragDistancePx = 56f
            )
        )
    }

    @Test
    fun longPressSpeedLock_usesLowerSensitivityOutsideFullscreen() {
        val fullscreen = resolveLongPressSpeedLockSensitivityPolicy(isFullscreen = true)
        val inline = resolveLongPressSpeedLockSensitivityPolicy(isFullscreen = false)

        assertEquals(LONG_PRESS_SPEED_LOCK_ZONE_HEIGHT_DP, fullscreen.lockZoneHeightDp)
        assertTrue(fullscreen.minDragDistanceDp > 0)
        assertTrue(inline.lockZoneHeightDp < fullscreen.lockZoneHeightDp)
        assertTrue(inline.minDragDistanceDp > fullscreen.minDragDistanceDp)
    }

    @Test
    fun longPressSpeedLock_visualSeparatesMarkerFromPlaybackProgress() {
        val visual = resolveLongPressSpeedLockZoneVisualPolicy()

        assertEquals(0f, visual.zoneFillAlpha)
        assertEquals(0f, visual.borderAlpha)
        assertTrue(visual.edgeGradientAlpha > 0f)
        assertTrue(visual.centerMarkerAlpha > visual.edgeGradientAlpha)
        assertEquals(4, visual.centerMarkerHeightDp)
        assertEquals(0.22f, visual.centerMarkerWidthFraction)
        assertEquals(0, visual.bottomVisualOffsetDp)
    }

    @Test
    fun subtitleBottomOffset_tracksChromeAndSafeInsetsAcrossScreenShapes() {
        val density = 3f
        val hidden = resolveSubtitleBottomOffsetPx(
            isFullscreen = true,
            controlsVisible = false,
            navigationInsetPx = 0,
            bottomControlsHeightPx = 180,
            density = density
        )
        val visible = resolveSubtitleBottomOffsetPx(
            isFullscreen = true,
            controlsVisible = true,
            navigationInsetPx = 24,
            bottomControlsHeightPx = 132,
            density = density
        )

        assertEquals(72, hidden)
        assertEquals(180, visible)
        assertEquals(
            96,
            resolveSubtitleBottomOffsetPx(
                isFullscreen = false,
                controlsVisible = false,
                navigationInsetPx = 0,
                bottomControlsHeightPx = 0,
                density = density
            )
        )
    }

    @Test
    fun subtitleBottomOffset_staysFixedWhenPositionIsLocked() {
        val hidden = resolveSubtitleBottomOffsetPx(
            isFullscreen = true,
            controlsVisible = false,
            positionLocked = true,
            navigationInsetPx = 12,
            bottomControlsHeightPx = 80,
            density = 2f
        )
        val visible = resolveSubtitleBottomOffsetPx(
            isFullscreen = true,
            controlsVisible = true,
            positionLocked = true,
            navigationInsetPx = 12,
            bottomControlsHeightPx = 80,
            density = 2f
        )

        assertEquals(hidden, visible)
    }

    @Test
    fun longPressSpeedFeedback_usesLightweightTextAndNoDefaultLockInstruction() {
        val source = loadVideoPlayerSectionSource()

        assertTrue(source.contains("\"倍速播放中 ${'$'}{effectiveLongPressSpeed}x\""))
        assertFalse(source.contains("拖至上下区域锁定"))
        assertFalse(source.contains("rememberInfiniteTransition(label = \"fast_forward\")"))
    }

    @Test
    fun longPressSpeedLockHint_usesNonModalPromptActions() {
        val source = loadVideoPlayerSectionSource()

        assertTrue(source.contains("需要长按锁定倍速吗？"))
        assertTrue(source.contains("开启锁定"))
        assertTrue(source.contains("不再提示"))
        assertFalse(source.contains("AlertDialog"))
    }

    @Test
    fun longPressSpeedLockHint_waitsUntilAccelerationEnds() {
        assertFalse(
            shouldShowLongPressSpeedLockHint(
                hintRequested = true,
                isLongPressing = true,
                isInPipMode = false,
            )
        )
        assertTrue(
            shouldShowLongPressSpeedLockHint(
                hintRequested = true,
                isLongPressing = false,
                isInPipMode = false,
            )
        )
    }

    @Test
    fun longPressSpeedLockHint_dismissActionEndsCurrentLongPressSpeed() {
        val source = loadVideoPlayerSectionSource()
        val dismissAction = source
            .substringAfter("TextButton(\n                            onClick = {\n                                showLongPressSpeedLockHint = false")
            .substringBefore("Text(\"不再提示\")")

        assertTrue(dismissAction.contains("finishLongPressSpeedGesture(gestureEnded = true)"))
    }

    @Test
    fun longPressSpeedFeedback_canBeDismissedWithoutChangingGestureState() {
        assertTrue(
            shouldShowLongPressSpeedFeedback(
                isLongPressing = true,
                isPlaybackSurfaceActive = true,
                hintDismissed = false,
                hintHidden = false,
            )
        )
        assertFalse(
            shouldShowLongPressSpeedFeedback(
                isLongPressing = true,
                isPlaybackSurfaceActive = true,
                hintDismissed = true,
                hintHidden = false,
            )
        )
        assertFalse(
            shouldShowLongPressSpeedFeedback(
                isLongPressing = true,
                isPlaybackSurfaceActive = false,
                hintDismissed = false,
                hintHidden = false,
            )
        )
        assertFalse(
            shouldShowLongPressSpeedFeedback(
                isLongPressing = true,
                isPlaybackSurfaceActive = true,
                hintDismissed = false,
                hintHidden = true,
            )
        )
    }

    @Test
    fun longPressSpeedLockHint_promptActionsMarkLocalHintAsShownImmediately() {
        val source = loadVideoPlayerSectionSource()
        val hintPrompt = source
            .substringAfter("text = \"需要长按锁定倍速吗？\"")
            .substringBefore("Text(\"不再提示\")")
        val dismissAction = source
            .substringAfter("TextButton(\n                            onClick = {\n                                showLongPressSpeedLockHint = false")
            .substringBefore("Text(\"不再提示\")")

        assertTrue(hintPrompt.contains("hasShownLongPressSpeedLockHintLocally = true"))
        assertTrue(dismissAction.contains("hasShownLongPressSpeedLockHintLocally = true"))
    }

    @Test
    fun longPressSpeedDrag_usesSingleLongPressDragGestureDetector() {
        val source = loadVideoPlayerSectionSource()

        assertTrue(
            source.contains("detectDragGesturesAfterLongPress("),
            "Long-press speed drag must be handled by the same gesture detector that owns the long press."
        )
        assertFalse(
            source.contains("onLongPress ="),
            "detectTapGestures long-press handling consumes post-long-press moves before drag locking can see them."
        )
    }

    @Test
    fun volumeGesture_usesSystemMusicStreamInsteadOfPlayerVolume() {
        val source = loadVideoPlayerSectionSource()
        val dragStartBlock = source
            .substringAfter("onDragStart = { offset ->")
            .substringBefore("onDragEnd = {")
        val systemVolumeGestureBlock = source
            .substringAfter("resolveSystemStreamVolumeFromGesture(")
            .substringBefore("audioManager.setStreamVolume(")
            .let { "resolveSystemStreamVolumeFromGesture($it" }

        assertTrue(dragStartBlock.contains("startVolumeStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)"))
        assertTrue(source.contains("resolveSystemStreamVolumeFromGesture("))
        assertTrue(systemVolumeGestureBlock.contains("screenHeightPx = context.resources.displayMetrics.heightPixels.toFloat()"))
        assertFalse(systemVolumeGestureBlock.contains("screenHeightPx = size.height.toFloat()"))
        assertTrue(source.contains("audioManager.setStreamVolume("))
        assertTrue(source.contains("AudioManager.STREAM_MUSIC"))
        assertFalse(source.contains("playerState.player.volume ="))
        assertFalse(source.contains("SettingsManager.setPreferredPlayerVolume("))
    }

    @Test
    fun longPressExclusiveDrag_onlyConsumesBeforeSpeedIsLocked() {
        assertTrue(
            shouldConsumeExclusiveLongPressSpeedDrag(
                isLongPressing = true,
                longPressSpeedLocked = false
            )
        )
        assertFalse(
            shouldConsumeExclusiveLongPressSpeedDrag(
                isLongPressing = true,
                longPressSpeedLocked = true
            )
        )
        assertFalse(
            shouldConsumeExclusiveLongPressSpeedDrag(
                isLongPressing = false,
                longPressSpeedLocked = false
            )
        )
    }

    @Test
    fun lockedLongPressSpeedUnlock_requiresRightSideHoldAndDownwardDrag() {
        assertTrue(
            shouldUnlockLockedLongPressSpeedFromRightDownDrag(
                longPressSpeedLocked = true,
                isLongPressing = true,
                startX = 720f,
                startY = 300f,
                currentY = 380f,
                containerWidthPx = 1_000f,
                holdDurationMs = 1_100L,
                minDownDragPx = 56f
            )
        )
        assertFalse(
            shouldUnlockLockedLongPressSpeedFromRightDownDrag(
                longPressSpeedLocked = true,
                isLongPressing = true,
                startX = 420f,
                startY = 300f,
                currentY = 380f,
                containerWidthPx = 1_000f,
                holdDurationMs = 1_100L,
                minDownDragPx = 56f
            )
        )
        assertFalse(
            shouldUnlockLockedLongPressSpeedFromRightDownDrag(
                longPressSpeedLocked = true,
                isLongPressing = true,
                startX = 720f,
                startY = 300f,
                currentY = 340f,
                containerWidthPx = 1_000f,
                holdDurationMs = 1_100L,
                minDownDragPx = 56f
            )
        )
        assertFalse(
            shouldUnlockLockedLongPressSpeedFromRightDownDrag(
                longPressSpeedLocked = true,
                isLongPressing = true,
                startX = 720f,
                startY = 300f,
                currentY = 380f,
                containerWidthPx = 1_000f,
                holdDurationMs = 900L,
                minDownDragPx = 56f
            )
        )
    }

    private fun loadVideoPlayerSectionSource(): String {
        val sourceFile = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/video/ui/section/VideoPlayerSection.kt"),
            File("src/main/java/com/android/purebilibili/feature/video/ui/section/VideoPlayerSection.kt")
        ).first { it.exists() }
        // git autocrlf=true 在 Windows 检出时转为 CRLF，多行断言统一按 LF 归一化以匹配 CI。
        return sourceFile.readText().replace("\r\n", "\n")
    }

    @Test
    fun playerReplacement_restartsVideoOutputRouterBindingEffect() {
        val outputBindingBlock = loadVideoPlayerSectionSource()
            .substringAfter("val shouldBindDirectPlayerView")
            .substringBefore("// 进度手势相关状态")

        assertTrue(
            outputBindingBlock.contains("LaunchedEffect(\n        videoOutputRouter,"),
            "Replacing the player creates a new VideoOutputRouter, so the binding effect must " +
                "restart even when the existing PlayerView and output mode are unchanged."
        )
        assertTrue(outputBindingBlock.contains("videoOutputRouter.update("))
    }

    @Test
    fun directPlayback_bindsPlayerViewSynchronouslyLikeKnownGoodVersion() {
        val playerViewBlock = loadVideoPlayerSectionSource()
            .substringAfter("// 1. PlayerView (底层)")
            .substringBefore("if (shouldUseAnime4kPipeline)")

        assertTrue(
            playerViewBlock.contains(
                "player = if (shouldBindDirectPlayerView) playerState.player else null"
            )
        )
        assertTrue(
            playerViewBlock.contains(
                "playerView.player = if (shouldBindDirectPlayerView) playerState.player else null"
            )
        )
    }

    @Test
    fun videoOutputRouter_doesNotOwnSteadyStateDirectPlayerBinding() {
        val routerSource = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/video/ui/section/VideoOutputRouter.kt"),
            File("src/main/java/com/android/purebilibili/feature/video/ui/section/VideoOutputRouter.kt")
        ).first { it.exists() }.readText()
        val releaseBlock = routerSource.substringAfter("fun release()")
            .substringBefore("private fun applyRoute()")
        val directRouteBlock = routerSource.substringAfter("val wasUsingAnime4K")

        assertFalse(releaseBlock.contains(".player = null"))
        assertFalse(directRouteBlock.contains("view.player = player"))
        assertTrue(directRouteBlock.contains("if (wasUsingAnime4K && shouldBindDirectPlayerView)"))
    }

    @Test
    fun playerReplacement_doesNotRestartActivityLifecycleRecovery() {
        val lifecycleBlock = loadVideoPlayerSectionSource()
            .substringAfter("// Activity 生命周期监听必须只跟随 LifecycleOwner")
            .substringBefore("// --- [优化] 视频封面逻辑 ---")

        assertTrue(lifecycleBlock.contains("val lifecyclePlayer by rememberUpdatedState(playerState.player)"))
        assertTrue(lifecycleBlock.contains("DisposableEffect(lifecycleOwner)"))
        assertFalse(lifecycleBlock.contains("DisposableEffect(lifecycleOwner, playerState.player)"))
        assertTrue(lifecycleBlock.contains("if (!hasObservedHostPause)"))
        assertTrue(lifecycleBlock.contains("Lifecycle.Event.ON_PAUSE"))
    }

    @Test
    fun lockedLongPressSpeed_reappliesWhenPlaybackSpeedUnexpectedlyResets() {
        assertTrue(
            shouldReapplyLockedLongPressSpeed(
                longPressSpeedLocked = true,
                isLongPressing = false,
                observedPlaybackSpeed = 1.0f,
                lockedLongPressSpeed = 2.0f
            )
        )
        assertFalse(
            shouldReapplyLockedLongPressSpeed(
                longPressSpeedLocked = true,
                isLongPressing = true,
                observedPlaybackSpeed = 1.0f,
                lockedLongPressSpeed = 2.0f
            )
        )
        assertFalse(
            shouldReapplyLockedLongPressSpeed(
                longPressSpeedLocked = false,
                isLongPressing = false,
                observedPlaybackSpeed = 1.0f,
                lockedLongPressSpeed = 2.0f
            )
        )
    }

    @Test
    fun explicitPlaybackSpeedChange_clearsLockedLongPressSpeedBeforeApplyingUserSpeed() {
        assertTrue(
            shouldClearLockedLongPressSpeedForExplicitSpeedChange(
                longPressSpeedLocked = true,
                isLongPressing = false
            )
        )
        assertFalse(
            shouldClearLockedLongPressSpeedForExplicitSpeedChange(
                longPressSpeedLocked = true,
                isLongPressing = true
            )
        )
        assertFalse(
            shouldClearLockedLongPressSpeedForExplicitSpeedChange(
                longPressSpeedLocked = false,
                isLongPressing = false
            )
        )
    }

    @Test
    fun longPressRelease_restoresOriginalSpeedOnlyWhenNotLocked() {
        assertTrue(
            shouldRestorePlaybackParametersAfterLongPressRelease(
                wasLongPressing = true,
                longPressSpeedLocked = false,
                gestureEnded = true
            )
        )
        assertFalse(
            shouldRestorePlaybackParametersAfterLongPressRelease(
                wasLongPressing = true,
                longPressSpeedLocked = true,
                gestureEnded = true
            )
        )
        assertFalse(
            shouldRestorePlaybackParametersAfterLongPressRelease(
                wasLongPressing = false,
                longPressSpeedLocked = false,
                gestureEnded = true
            )
        )
        assertFalse(
            shouldRestorePlaybackParametersAfterLongPressRelease(
                wasLongPressing = true,
                longPressSpeedLocked = false,
                gestureEnded = false
            )
        )
    }

    @Test
    fun longPressSpeedGesture_disabledWhenScreenLockedOrVideoScaled() {
        assertFalse(
            shouldEnableLongPressSpeedGesture(
                isScreenLocked = true,
                scale = 1f,
                isMultiTouchActive = false
            )
        )
        assertFalse(
            shouldEnableLongPressSpeedGesture(
                isScreenLocked = false,
                scale = 1.2f,
                isMultiTouchActive = false
            )
        )
        assertFalse(
            shouldEnableLongPressSpeedGesture(
                isScreenLocked = false,
                scale = 1f,
                isMultiTouchActive = true
            )
        )
        assertTrue(
            shouldEnableLongPressSpeedGesture(
                isScreenLocked = false,
                scale = 1f,
                isMultiTouchActive = false
            )
        )
    }

    @Test
    fun viewportTransformGesture_supportsFullscreenAndVerticalVideoOnlyWhenUnlocked() {
        assertTrue(
            shouldEnableViewportTransformGesture(
                isScreenLocked = false,
                isFullscreen = true,
                isPortraitFullscreen = false,
                isVerticalVideo = false,
            )
        )
        assertTrue(
            shouldEnableViewportTransformGesture(false, false, true, false)
        )
        assertTrue(
            shouldEnableViewportTransformGesture(false, false, false, true)
        )
        assertFalse(
            shouldEnableViewportTransformGesture(
                isScreenLocked = true,
                isFullscreen = true,
                isPortraitFullscreen = false,
                isVerticalVideo = true,
            )
        )
        assertFalse(shouldEnableViewportTransformGesture(false, false, false, false))
    }

    @Test
    fun progressOverride_bypassesFrozenSeekSessionDuringLongPressSpeed() {
        val frozenSeekSession = updatePlaybackSeekInteraction(
            state = startPlaybackSeekInteraction(
                state = syncPlaybackSeekSession(
                    state = PlaybackSeekSessionState(),
                    playbackPositionMs = 8_000L
                ),
                positionMs = 8_000L
            ),
            positionMs = 12_000L
        )

        assertNull(
            resolveProgressDisplayOverridePositionMs(
                seekSession = frozenSeekSession,
                pendingPlaybackTransitionPositionMs = null,
                isLongPressing = true,
                longPressSpeedLocked = false
            )
        )
        assertNull(
            resolveProgressDisplayOverridePositionMs(
                seekSession = frozenSeekSession,
                pendingPlaybackTransitionPositionMs = null,
                isLongPressing = false,
                longPressSpeedLocked = true
            )
        )
        assertEquals(
            12_000L,
            resolveProgressDisplayOverridePositionMs(
                seekSession = frozenSeekSession,
                pendingPlaybackTransitionPositionMs = null,
                isLongPressing = false,
                longPressSpeedLocked = false
            )
        )
    }

    @Test
    fun progressOverride_prefersPendingTransitionWhenLongPressSpeedBypassesSeekSession() {
        assertEquals(
            18_000L,
            resolveProgressDisplayOverridePositionMs(
                seekSession = PlaybackSeekSessionState(sliderPositionMs = 12_000L, isSliderMoving = true),
                pendingPlaybackTransitionPositionMs = 18_000L,
                isLongPressing = true,
                longPressSpeedLocked = false
            )
        )
    }

    @Test
    fun gestureSeekStart_usesLivePlaybackPositionWhenSeekSessionIsIdle() {
        assertEquals(
            26_000L,
            resolveGestureSeekStartPositionMs(
                seekSession = PlaybackSeekSessionState(
                    playbackPositionMs = 8_000L,
                    sliderPositionMs = 8_000L
                ),
                playbackPositionMs = 26_000L
            )
        )
    }

    @Test
    fun gestureSeekStart_usesSliderPositionWhileSeekInteractionIsActive() {
        assertEquals(
            12_000L,
            resolveGestureSeekStartPositionMs(
                seekSession = PlaybackSeekSessionState(
                    playbackPositionMs = 8_000L,
                    sliderPositionMs = 12_000L,
                    isSliderMoving = true
                ),
                playbackPositionMs = 26_000L
            )
        )
    }

    @Test
    fun longPressSpeedStart_resetsSeekSessionToCurrentPlaybackPosition() {
        val source = loadVideoPlayerSectionSource()
        val startBlock = source
            .substringAfter("fun startLongPressSpeedGesture(startOffset: Offset? = null) {")
            .substringBefore("fun unlockLockedLongPressSpeedFromGesture()")

        assertTrue(startBlock.contains("resetPlaybackSeekSessionForActivePlayback("))
        assertTrue(startBlock.contains("gestureMode = VideoGestureMode.None"))
    }

    @Test
    fun longPressSpeed_usesDirectPlayerParametersWithoutRefreshingThePlaybackSource() {
        val source = loadVideoPlayerSectionSource()
        val startBlock = source
            .substringAfter("fun startLongPressSpeedGesture(startOffset: Offset? = null) {")
            .substringBefore("fun unlockLockedLongPressSpeedFromGesture()")
        val finishBlock = source
            .substringAfter("fun finishLongPressSpeedGesture(gestureEnded: Boolean) {")
            .substringBefore("// 换集/换片后收口侧栏与手势中间态")

        assertTrue(startBlock.contains("applyLongPressPlaybackParameters"))
        assertTrue(finishBlock.contains("applyLongPressPlaybackParameters"))
        assertFalse(startBlock.contains("onPlaybackSpeedChange("))
        assertFalse(finishBlock.contains("onPlaybackSpeedChange("))
    }

    @Test
    fun seekDragCancel_restoresDanmakuAfterScrubCancel() {
        val source = loadVideoPlayerSectionSource()
        val cancelBlock = source
            .substringAfter("onSeekDragCancel = {")
            .substringBefore("isSeekScrubbing =")

        assertTrue(cancelBlock.contains("cancelPlaybackSeekInteraction"))
        assertTrue(cancelBlock.contains("danmakuManager.cancelSeekScrub()"))
    }

    fun longPressSpeedDragStart_ignoresRegularDragWhileLongPressSpeedIsActive() {
        val source = loadVideoPlayerSectionSource()
        val dragStartBlock = source
            .substringAfter("onDragStart = { offset ->")
            .substringBefore("onDragEnd = {")

        assertTrue(dragStartBlock.contains("if (isLongPressing || longPressSpeedLocked)"))
    }

    @Test
    fun returnCoverPresentation_usesSharedBoundsDuringForcedReturn() {
        val spec = resolveVideoPlayerEntryPresentationSpec(
            shouldKeepCoverForManualStart = false,
            forceCoverDuringReturnAnimation = true,
            isVerticalVideo = false,
            targetMode = com.android.purebilibili.core.ui.transition.VideoSharedTransitionTargetMode.InlinePlayer
        )

        assertTrue(spec.coverUsesSharedBounds)
        assertFalse(spec.fillCoverViewport)
        assertFalse(spec.showManualStartPlayButton)
    }

    @Test
    fun returnCoverPresentation_usesSharedBoundsForManualStart() {
        val spec = resolveVideoPlayerEntryPresentationSpec(
            shouldKeepCoverForManualStart = true,
            forceCoverDuringReturnAnimation = false,
            isVerticalVideo = false,
            targetMode = com.android.purebilibili.core.ui.transition.VideoSharedTransitionTargetMode.InlinePlayer
        )

        assertTrue(spec.coverUsesSharedBounds)
        assertTrue(spec.fillCoverViewport)
        assertEquals(VideoPlayerCoverContentScaleMode.Crop, spec.coverContentScaleMode)
        assertTrue(spec.showManualStartPlayButton)
    }

    @Test
    fun returnCoverPresentation_doesNotUseSharedBoundsForNormalPlayback() {
        val spec = resolveVideoPlayerEntryPresentationSpec(
            shouldKeepCoverForManualStart = false,
            forceCoverDuringReturnAnimation = false,
            isVerticalVideo = false,
            targetMode = com.android.purebilibili.core.ui.transition.VideoSharedTransitionTargetMode.InlinePlayer
        )

        assertFalse(spec.coverUsesSharedBounds)
        assertFalse(spec.showManualStartPlayButton)
    }
}
