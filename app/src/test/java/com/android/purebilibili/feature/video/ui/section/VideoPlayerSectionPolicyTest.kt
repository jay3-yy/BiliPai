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
    fun playerControls_areHiddenWhenEnteringVideo() {
        assertFalse(INITIAL_PLAYER_CONTROLS_VISIBLE)
        assertTrue(INITIAL_PLAYER_CHROME_AUTO_HIDE_HANDLED)
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
                statusBarHeightPx = 96
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
    fun forcedReturnTakeover_disablesInlinePlayerRetentionBindingAndVisibility() {
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
        assertFalse(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = true,
                isInPipMode = false,
                forceCoverDuringReturnAnimation = true
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
    fun playerSurfaceRebind_onlyWhenForegroundVideoSurfaceCanRender() {
        assertTrue(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = false,
                videoWidth = 1920,
                videoHeight = 1080
            )
        )
    }

    @Test
    fun playerSurfaceRebind_skipsOnlyWhenPipOrPlayerViewMissing() {
        assertFalse(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = true,
                videoWidth = 1920,
                videoHeight = 1080
            )
        )
        assertTrue(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = false,
                videoWidth = 0,
                videoHeight = 1080
            )
        )
        assertFalse(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = false,
                isInPipMode = false,
                videoWidth = 1920,
                videoHeight = 1080
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
                isInPipMode = false,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertTrue(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = false,
                isInPipMode = true,
                forceCoverDuringReturnAnimation = false
            )
        )
    }

    @Test
    fun inlinePlayerBinding_detachesSurface_whenBackgroundedOrPortraitFullscreenOwnsPlayback() {
        assertFalse(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = false,
                isInPipMode = false,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertFalse(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = true,
                hostLifecycleStarted = true,
                isInPipMode = false,
                forceCoverDuringReturnAnimation = false
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
                smallestScreenWidthDp = 411
            )
        )
    }

    @Test
    fun playbackReadyAutoFullscreen_allowsTabletsBecauseSettingIsExplicit() {
        assertTrue(
            shouldAllowPlaybackStateAutoFullscreen(
                smallestScreenWidthDp = 600
            )
        )
    }

    @Test
    fun playbackStateAutoFullscreen_triggersWhenAttachedAfterPlaybackAlreadyStarted() {
        assertTrue(
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
        assertEquals(10, visual.bottomVisualOffsetDp)
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
    fun longPressSpeedLockHint_dismissActionEndsCurrentLongPressSpeed() {
        val source = loadVideoPlayerSectionSource()
        val dismissAction = source
            .substringAfter("TextButton(\n                            onClick = {\n                                showLongPressSpeedLockHint = false")
            .substringBefore("Text(\"不再提示\")")

        assertTrue(dismissAction.contains("finishLongPressSpeedGesture(gestureEnded = true)"))
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
        val volumeGestureBlock = source
            .substringAfter("VideoGestureMode.Volume -> {")
            .substringBefore("VideoGestureMode.Seek ->")
            .substringBefore("else ->")

        assertTrue(dragStartBlock.contains("startVolumeStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)"))
        assertTrue(volumeGestureBlock.contains("resolveSystemStreamVolumeFromGesture("))
        assertTrue(volumeGestureBlock.contains("screenHeightPx = context.resources.displayMetrics.heightPixels.toFloat()"))
        assertFalse(volumeGestureBlock.contains("screenHeightPx = size.height.toFloat()"))
        assertTrue(volumeGestureBlock.contains("audioManager.setStreamVolume("))
        assertTrue(volumeGestureBlock.contains("AudioManager.STREAM_MUSIC"))
        assertFalse(volumeGestureBlock.contains("playerState.player.volume ="))
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
        return sourceFile.readText()
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
    fun tapAfterLongPressSpeedRelease_doesNotToggleControls() {
        assertFalse(
            shouldToggleControlsForVideoTap(
                longPressSpeedEndedAtMs = 1_000L,
                nowMs = 1_120L
            )
        )
        assertTrue(
            shouldToggleControlsForVideoTap(
                longPressSpeedEndedAtMs = 1_000L,
                nowMs = 1_700L
            )
        )
        assertTrue(
            shouldToggleControlsForVideoTap(
                longPressSpeedEndedAtMs = 0L,
                nowMs = 1_120L
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
    fun viewportTransformGesture_disabledDuringPlaybackEvenWhenUnlocked() {
        assertFalse(
            shouldEnableViewportTransformGesture(
                isScreenLocked = false
            )
        )
        assertFalse(
            shouldEnableViewportTransformGesture(
                isScreenLocked = true
            )
        )
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
