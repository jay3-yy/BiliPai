package com.android.purebilibili.feature.video.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundPlaybackPolicyTest {

    @Test
    fun inAppMiniPlayerShownOnlyWhenEligible() {
        assertTrue(
            shouldShowInAppMiniPlayerByPolicy(
                mode = SettingsManager.MiniPlayerMode.IN_APP_ONLY,
                isActive = true,
                isNavigatingToVideo = false,
                stopPlaybackOnExit = false
            )
        )
        assertFalse(
            shouldShowInAppMiniPlayerByPolicy(
                mode = SettingsManager.MiniPlayerMode.IN_APP_ONLY,
                isActive = true,
                isNavigatingToVideo = true,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun explicitMiniPlayerEntryOverridesDisabledModeOnlyForAnActiveSession() {
        fun visible(forced: Boolean, active: Boolean = true, navigating: Boolean = false) =
            shouldShowInAppMiniPlayerByPolicy(
                mode = SettingsManager.MiniPlayerMode.OFF,
                isActive = active,
                isNavigatingToVideo = navigating,
                stopPlaybackOnExit = false,
                forcedByUser = forced,
            )
        assertTrue(visible(forced = true))
        assertFalse(visible(forced = false))
        assertFalse(visible(forced = true, active = false))
        assertFalse(visible(forced = true, navigating = true))
    }

    @Test
    fun combinedMiniPlayerModeShowsInAppMiniPlayerAndCanEnterSystemPip() {
        assertTrue(
            shouldShowInAppMiniPlayerByPolicy(
                mode = SettingsManager.MiniPlayerMode.IN_APP_AND_SYSTEM_PIP,
                isActive = true,
                isNavigatingToVideo = false,
                stopPlaybackOnExit = false
            )
        )
        assertTrue(
            shouldEnterPipByPolicy(
                mode = SettingsManager.MiniPlayerMode.IN_APP_AND_SYSTEM_PIP,
                isActive = true,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun stopOnExitDisablesInAppMiniPlayer() {
        assertFalse(
            shouldShowInAppMiniPlayerByPolicy(
                mode = SettingsManager.MiniPlayerMode.IN_APP_ONLY,
                isActive = true,
                isNavigatingToVideo = false,
                stopPlaybackOnExit = true
            )
        )
    }

    @Test
    fun stopOnExitDisablesSystemPip() {
        assertFalse(
            shouldEnterPipByPolicy(
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                isActive = true,
                stopPlaybackOnExit = true
            )
        )
    }

    @Test
    fun systemPipRequiresActivePlayback() {
        assertTrue(
            shouldEnterPipByPolicy(
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                isActive = true,
                stopPlaybackOnExit = false
            )
        )
        assertFalse(
            shouldEnterPipByPolicy(
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                isActive = false,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun stopOnExitDisablesBackgroundAudioEvenInDefaultMode() {
        assertFalse(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.OFF,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = true
            )
        )
    }

    @Test
    fun defaultModeStillSupportsBackgroundAudioWhenOptionOff() {
        assertTrue(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.OFF,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun defaultModeStopsBackgroundAudioWhenLeavingByNavigation() {
        assertFalse(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.OFF,
                isActive = true,
                isLeavingByNavigation = true,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun audioBarKeepsDetailPlaybackAcrossNavigationExit() {
        assertTrue(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = false,
                mode = SettingsManager.MiniPlayerMode.OFF,
                isActive = true,
                isLeavingByNavigation = true,
                stopPlaybackOnExit = false,
                keepForAudioNowPlaying = true,
            )
        )
    }

    @Test
    fun inAppMiniModeCanFallbackToBackgroundAudioWhenAppLeavesForeground() {
        assertTrue(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.IN_APP_ONLY,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = false,
                shouldKeepPlaybackForPipTransition = false
            )
        )
    }

    @Test
    fun systemPipCanFallbackToBackgroundAudioWhenNoPipTransitionWillHappen() {
        assertTrue(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = false,
                shouldKeepPlaybackForPipTransition = false
            )
        )
    }

    @Test
    fun systemPipDoesNotEnableBackgroundAudioDuringPipTransition() {
        assertFalse(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = false,
                shouldKeepPlaybackForPipTransition = true
            )
        )
    }

    @Test
    fun backgroundPlaybackToggleDisablesBackgroundAudioEvenInDefaultMode() {
        assertFalse(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = false,
                mode = SettingsManager.MiniPlayerMode.OFF,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun pipTransitionStateStaysTrueWhileRequestOrActualPipExists() {
        assertTrue(
            shouldKeepPlaybackForPipTransition(
                isSystemPipActive = false,
                hasPendingPlaybackRoutePip = true
            )
        )
        assertTrue(
            shouldKeepPlaybackForPipTransition(
                isSystemPipActive = true,
                hasPendingPlaybackRoutePip = false
            )
        )
        assertFalse(
            shouldKeepPlaybackForPipTransition(
                isSystemPipActive = false,
                hasPendingPlaybackRoutePip = false
            )
        )
    }

    @Test
    fun audioFocusPolicyOnlyHandlesFocusWhenEnabled() {
        assertTrue(resolveHandleAudioFocusByPolicy(audioFocusEnabled = true))
        assertFalse(resolveHandleAudioFocusByPolicy(audioFocusEnabled = false))
    }

    @Test
    fun pausePolicyContinuesForMiniModeOrPipRegardlessOfLeaveHint() {
        assertTrue(
            shouldContinuePlaybackDuringPause(
                isMiniMode = true,
                isPip = false,
                isBackgroundAudio = false,
                wasPlaybackActive = false
            )
        )
        assertTrue(
            shouldContinuePlaybackDuringPause(
                isMiniMode = false,
                isPip = true,
                isBackgroundAudio = false,
                wasPlaybackActive = false
            )
        )
    }

    @Test
    fun pausePolicyKeepsBackgroundAudioWhenSettingAllowsItEvenIfSnapshotLooksInactive() {
        assertTrue(
            shouldContinuePlaybackDuringPause(
                isMiniMode = false,
                isPip = false,
                isBackgroundAudio = true,
                wasPlaybackActive = true
            )
        )
        assertTrue(
            shouldContinuePlaybackDuringPause(
                isMiniMode = false,
                isPip = false,
                isBackgroundAudio = true,
                wasPlaybackActive = false
            )
        )
    }

    @Test
    fun backgroundEntryDisablesVideoTrackForPausedOrAudioOnlySessions() {
        assertTrue(
            shouldDisableVideoTrackOnEnterBackground(
                shouldPauseBuffering = true,
                shouldContinueBackgroundAudio = false
            )
        )
        assertTrue(
            shouldDisableVideoTrackOnEnterBackground(
                shouldPauseBuffering = false,
                shouldContinueBackgroundAudio = true
            )
        )
        assertFalse(
            shouldDisableVideoTrackOnEnterBackground(
                shouldPauseBuffering = false,
                shouldContinueBackgroundAudio = false
            )
        )
    }

    @Test
    fun shortBackgroundLightMode_keepsVideoTrackUntilThreshold() {
        assertFalse(
            shouldApplyHeavyBackgroundVideoOptimization(
                backgroundElapsedMs = 0L,
                shortBackgroundLightModeMs = SHORT_BACKGROUND_LIGHT_MODE_MS
            )
        )
        assertFalse(
            shouldApplyHeavyBackgroundVideoOptimization(
                backgroundElapsedMs = SHORT_BACKGROUND_LIGHT_MODE_MS - 1L,
                shortBackgroundLightModeMs = SHORT_BACKGROUND_LIGHT_MODE_MS
            )
        )
        assertTrue(
            shouldApplyHeavyBackgroundVideoOptimization(
                backgroundElapsedMs = SHORT_BACKGROUND_LIGHT_MODE_MS,
                shortBackgroundLightModeMs = SHORT_BACKGROUND_LIGHT_MODE_MS
            )
        )
    }

    @Test
    fun heavyBackgroundVideoOptimization_requiresDisableIntentAndElapsedThreshold() {
        assertFalse(
            shouldRunHeavyBackgroundVideoOptimization(
                shouldDisableVideoTrack = true,
                stillInBackground = true,
                backgroundElapsedMs = 1_000L
            )
        )
        assertFalse(
            shouldRunHeavyBackgroundVideoOptimization(
                shouldDisableVideoTrack = true,
                stillInBackground = false,
                backgroundElapsedMs = SHORT_BACKGROUND_LIGHT_MODE_MS
            )
        )
        assertFalse(
            shouldRunHeavyBackgroundVideoOptimization(
                shouldDisableVideoTrack = false,
                stillInBackground = true,
                backgroundElapsedMs = SHORT_BACKGROUND_LIGHT_MODE_MS
            )
        )
        assertTrue(
            shouldRunHeavyBackgroundVideoOptimization(
                shouldDisableVideoTrack = true,
                stillInBackground = true,
                backgroundElapsedMs = SHORT_BACKGROUND_LIGHT_MODE_MS
            )
        )
    }

    @Test
    fun memoryPressureTrimLevels_forceHeavyBackgroundVideoOptimization() {
        assertFalse(
            shouldForceHeavyBackgroundVideoOptimizationOnTrimLevel(
                android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            )
        )
        assertFalse(
            shouldForceHeavyBackgroundVideoOptimizationOnTrimLevel(
                android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
            )
        )
        assertTrue(
            shouldForceHeavyBackgroundVideoOptimizationOnTrimLevel(
                android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
            )
        )
        assertTrue(
            shouldForceHeavyBackgroundVideoOptimizationOnTrimLevel(
                android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            )
        )
        assertTrue(
            shouldForceHeavyBackgroundVideoOptimizationOnTrimLevel(
                android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
            )
        )
        assertTrue(
            shouldForceHeavyBackgroundVideoOptimizationOnTrimLevel(
                android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE
            )
        )
        assertTrue(
            shouldForceHeavyBackgroundVideoOptimizationOnTrimLevel(
                android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
            )
        )
    }

    @Test
    fun memoryPressure_canBypassShortBackgroundLightModeWindow() {
        assertTrue(
            shouldRunHeavyBackgroundVideoOptimization(
                shouldDisableVideoTrack = true,
                stillInBackground = true,
                backgroundElapsedMs = 1_000L,
                forceDueToMemoryPressure = true
            )
        )
        assertFalse(
            shouldRunHeavyBackgroundVideoOptimization(
                shouldDisableVideoTrack = true,
                stillInBackground = false,
                backgroundElapsedMs = 1_000L,
                forceDueToMemoryPressure = true
            )
        )
        assertFalse(
            shouldRunHeavyBackgroundVideoOptimization(
                shouldDisableVideoTrack = false,
                stillInBackground = true,
                backgroundElapsedMs = 1_000L,
                forceDueToMemoryPressure = true
            )
        )
    }

    @Test
    fun pendingHeavyOptimization_shouldApplyOnMemoryPressure() {
        assertTrue(
            shouldApplyPendingHeavyBackgroundVideoOptimizationOnMemoryPressure(
                isLowMemoryMode = true,
                stillInBackground = true,
                alreadyAppliedHeavyOptimization = false,
                shouldDisableVideoTrack = true,
                forceDueToMemoryPressure = true
            )
        )
        assertFalse(
            shouldApplyPendingHeavyBackgroundVideoOptimizationOnMemoryPressure(
                isLowMemoryMode = true,
                stillInBackground = true,
                alreadyAppliedHeavyOptimization = true,
                shouldDisableVideoTrack = true,
                forceDueToMemoryPressure = true
            )
        )
        assertFalse(
            shouldApplyPendingHeavyBackgroundVideoOptimizationOnMemoryPressure(
                isLowMemoryMode = false,
                stillInBackground = true,
                alreadyAppliedHeavyOptimization = false,
                shouldDisableVideoTrack = true,
                forceDueToMemoryPressure = true
            )
        )
        assertFalse(
            shouldApplyPendingHeavyBackgroundVideoOptimizationOnMemoryPressure(
                isLowMemoryMode = true,
                stillInBackground = true,
                alreadyAppliedHeavyOptimization = false,
                shouldDisableVideoTrack = true,
                forceDueToMemoryPressure = false
            )
        )
    }

    @Test
    fun idleBackgroundRelease_stopsAndClearsSurfaceWhenNoBackgroundAudio() {
        assertTrue(
            shouldStopIdlePlaybackForBackgroundOptimization(
                shouldContinueBackgroundAudio = false,
                wasPlaybackActive = false
            )
        )
        assertFalse(
            shouldStopIdlePlaybackForBackgroundOptimization(
                shouldContinueBackgroundAudio = true,
                wasPlaybackActive = true
            )
        )
        assertTrue(shouldClearVideoSurfaceOnEnterBackground(shouldDisableVideoTrack = true))
        assertFalse(shouldClearVideoSurfaceOnEnterBackground(shouldDisableVideoTrack = false))
    }

    @Test
    fun pausedSession_withBackgroundAudioSettingOn_stillIdleReleases() {
        assertFalse(
            shouldRetainBackgroundAudioSession(
                shouldContinueBackgroundAudio = true,
                wasPlaybackActive = false
            )
        )
        assertTrue(
            shouldStopIdlePlaybackForBackgroundOptimization(
                shouldContinueBackgroundAudio = true,
                wasPlaybackActive = false
            )
        )
        assertTrue(shouldClearVideoSurfaceOnEnterBackground(shouldDisableVideoTrack = true))
        assertTrue(
            shouldUpgradeHeavyBackgroundOptimizationToIdleRelease(
                isLowMemoryMode = true,
                stillInBackground = true,
                alreadyAppliedHeavyOptimization = true,
                alreadyAppliedIdleRelease = false,
                shouldContinueBackgroundAudio = true,
                wasPlaybackActive = false,
                requestIdlePlaybackRelease = true
            )
        )
        assertFalse(
            shouldUpgradeHeavyBackgroundOptimizationToIdleRelease(
                isLowMemoryMode = true,
                stillInBackground = true,
                alreadyAppliedHeavyOptimization = true,
                alreadyAppliedIdleRelease = false,
                shouldContinueBackgroundAudio = true,
                wasPlaybackActive = true,
                requestIdlePlaybackRelease = true
            )
        )
    }

    @Test
    fun criticalMemoryPressure_canUpgradeAlreadyHeavySessionToIdleRelease() {
        assertTrue(
            shouldUpgradeHeavyBackgroundOptimizationToIdleRelease(
                isLowMemoryMode = true,
                stillInBackground = true,
                alreadyAppliedHeavyOptimization = true,
                alreadyAppliedIdleRelease = false,
                shouldContinueBackgroundAudio = false,
                wasPlaybackActive = false,
                requestIdlePlaybackRelease = true
            )
        )
        assertFalse(
            shouldUpgradeHeavyBackgroundOptimizationToIdleRelease(
                isLowMemoryMode = true,
                stillInBackground = true,
                alreadyAppliedHeavyOptimization = true,
                alreadyAppliedIdleRelease = true,
                shouldContinueBackgroundAudio = false,
                wasPlaybackActive = false,
                requestIdlePlaybackRelease = true
            )
        )
        assertFalse(
            shouldUpgradeHeavyBackgroundOptimizationToIdleRelease(
                isLowMemoryMode = true,
                stillInBackground = true,
                alreadyAppliedHeavyOptimization = true,
                alreadyAppliedIdleRelease = false,
                shouldContinueBackgroundAudio = true,
                wasPlaybackActive = true,
                requestIdlePlaybackRelease = true
            )
        )
        assertFalse(
            shouldUpgradeHeavyBackgroundOptimizationToIdleRelease(
                isLowMemoryMode = true,
                stillInBackground = true,
                alreadyAppliedHeavyOptimization = false,
                alreadyAppliedIdleRelease = false,
                shouldContinueBackgroundAudio = false,
                wasPlaybackActive = false,
                requestIdlePlaybackRelease = true
            )
        )
    }

    @Test
    fun idleStoppedSession_preparesOnForegroundWhenMediaItemsRemain() {
        assertTrue(
            shouldPreparePlaybackOnForegroundResume(
                hasForegroundResumeIntent = true,
                hasMediaItems = true,
                playbackState = Player.STATE_IDLE
            )
        )
        assertFalse(
            shouldPreparePlaybackOnForegroundResume(
                hasForegroundResumeIntent = false,
                hasMediaItems = true,
                playbackState = Player.STATE_IDLE
            )
        )
    }

    @Test
    fun shortBackgroundForegroundResume_skipsTrackRestoreAndFrameSeek() {
        assertFalse(
            shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = false,
                hasMediaItems = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = false,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                hasForegroundResumeIntent = true
            )
        )
    }

    @Test
    fun backgroundEntry_doesNotPauseBufferingWhenBackgroundAudioShouldContinue() {
        assertFalse(
            shouldPauseBufferingOnEnterBackground(
                shouldPauseBuffering = true,
                shouldContinueBackgroundAudio = true
            )
        )
        assertTrue(
            shouldPauseBufferingOnEnterBackground(
                shouldPauseBuffering = true,
                shouldContinueBackgroundAudio = false
            )
        )
    }

    @Test
    fun backgroundTrackSelectionDisablesVideoRendererWhenNeeded() {
        val current = TrackSelectionParameters.Builder().build()

        val result = resolveTrackSelectionParametersForBackground(
            currentTrackSelectionParameters = current,
            shouldDisableVideoTrack = true
        )

        assertTrue(result.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
        assertEquals(0, result.maxVideoWidth)
        assertEquals(0, result.maxVideoHeight)
    }

    @Test
    fun backgroundTrackSelectionStaysUntouchedWhenVideoCanRemainEnabled() {
        val current = TrackSelectionParameters.Builder().build()

        val result = resolveTrackSelectionParametersForBackground(
            currentTrackSelectionParameters = current,
            shouldDisableVideoTrack = false
        )

        assertEquals(current, result)
    }

    @Test
    fun backgroundEntryClearsVideoSurface_whenVideoTrackDisabled() {
        assertTrue(shouldClearVideoSurfaceOnEnterBackground(shouldDisableVideoTrack = true))
        assertFalse(shouldClearVideoSurfaceOnEnterBackground(shouldDisableVideoTrack = false))
    }

    @Test
    fun backgroundEntryTrimsDanmakuCaches_whenVideoPathIsDisabled() {
        assertTrue(
            shouldTrimDanmakuCachesOnEnterBackground(
                shouldDisableVideoTrack = true
            )
        )
        assertFalse(
            shouldTrimDanmakuCachesOnEnterBackground(
                shouldDisableVideoTrack = false
            )
        )
    }

    @Test
    fun foregroundEntryRequestsFrameRefresh_whenVideoTrackWasRestored() {
        assertTrue(
            shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = true,
                hasMediaItems = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = false,
                hasMediaItems = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = true,
                hasMediaItems = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = true,
                hasMediaItems = true,
                playbackState = Player.STATE_IDLE
            )
        )
    }

    @Test
    fun foregroundEntryDoesNotSeek_whenBackgroundAudioTimelineStayedActive() {
        assertFalse(
            shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = true,
                hasMediaItems = true,
                playbackState = Player.STATE_READY,
                retainedBackgroundAudio = true,
            )
        )
    }

    @Test
    fun foregroundEntryResumesPlaybackOnlyWhenPlaybackIntentWasStillActive() {
        assertTrue(
            shouldResumePlaybackOnEnterForeground(
                playWhenReady = true,
                isPlaying = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldResumePlaybackOnEnterForeground(
                playWhenReady = false,
                isPlaying = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldResumePlaybackOnEnterForeground(
                playWhenReady = true,
                isPlaying = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldResumePlaybackOnEnterForeground(
                playWhenReady = true,
                isPlaying = false,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun backgroundEntryDoesNotPauseReadyPlaybackIntent() {
        assertFalse(
            shouldPauseBackgroundBuffering(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun foregroundEntryKicksPlaybackWhenVideoTrackReturnsFromBackgroundAudio() {
        assertTrue(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
        assertTrue(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = true,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
        assertFalse(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = false,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = true,
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = true,
                playWhenReady = true,
                playbackState = Player.STATE_IDLE
            )
        )
    }

    @Test
    fun foregroundEntryKicksPlaybackWhenAndroid16ClearsPlayWhenReadyButResumeIntentExists() {
        assertTrue(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = true,
                playWhenReady = false,
                playbackState = Player.STATE_READY,
                hasForegroundResumeIntent = true
            )
        )
        assertFalse(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = true,
                playWhenReady = false,
                playbackState = Player.STATE_READY,
                hasForegroundResumeIntent = true,
                isLeavingByNavigation = true
            )
        )
    }

    @Test
    fun foregroundEntryPreparesIdlePlayerOnlyWhenResumeIntentExists() {
        assertTrue(
            shouldPreparePlaybackOnForegroundResume(
                hasForegroundResumeIntent = true,
                hasMediaItems = true,
                playbackState = Player.STATE_IDLE
            )
        )
        assertFalse(
            shouldPreparePlaybackOnForegroundResume(
                hasForegroundResumeIntent = false,
                hasMediaItems = true,
                playbackState = Player.STATE_IDLE
            )
        )
        assertFalse(
            shouldPreparePlaybackOnForegroundResume(
                hasForegroundResumeIntent = true,
                hasMediaItems = true,
                playbackState = Player.STATE_IDLE,
                isLeavingByNavigation = true
            )
        )
    }

    @Test
    fun bufferingWithPlayWhenReadyShouldNotBeTreatedAsInactiveInBackground() {
        assertFalse(
            shouldPauseBackgroundBuffering(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun pausedStateShouldStillBeTreatedAsInactiveInBackground() {
        assertTrue(
            shouldPauseBackgroundBuffering(
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun audioNowPlayingRetentionDoesNotOverrideExplicitStop() {
        assertTrue(
            shouldKeepPlaybackForAudioNowPlayingBar(
                sessionActive = true,
                barEnabled = true,
            )
        )
        assertFalse(
            shouldKeepPlaybackForAudioNowPlayingBar(
                sessionActive = false,
                barEnabled = true,
            )
        )
        assertTrue(
            shouldClearPlaybackNotificationOnNavigationExit(
                mode = SettingsManager.MiniPlayerMode.OFF,
                stopPlaybackOnExit = true,
                keepForAudioNowPlaying = true,
            )
        )
        assertTrue(
            shouldClearPlaybackNotificationOnNavigationExit(
                mode = SettingsManager.MiniPlayerMode.OFF,
                stopPlaybackOnExit = true,
                keepForAudioNowPlaying = false,
            )
        )
    }

    @Test
    fun navigationExitShouldClearPlaybackNotificationForOffAndPipButNotInAppModes() {
        assertTrue(
            shouldClearPlaybackNotificationOnNavigationExit(
                mode = SettingsManager.MiniPlayerMode.OFF,
                stopPlaybackOnExit = false
            )
        )
        assertTrue(
            shouldClearPlaybackNotificationOnNavigationExit(
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                stopPlaybackOnExit = false
            )
        )
        assertFalse(
            shouldClearPlaybackNotificationOnNavigationExit(
                mode = SettingsManager.MiniPlayerMode.IN_APP_ONLY,
                stopPlaybackOnExit = false
            )
        )
        assertFalse(
            shouldClearPlaybackNotificationOnNavigationExit(
                mode = SettingsManager.MiniPlayerMode.IN_APP_AND_SYSTEM_PIP,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun navigationLeaveShouldBeIgnoredWhenExpectedBvidDiffersFromCurrent() {
        assertFalse(
            shouldHandleNavigationLeaveForBvid(
                expectedBvid = "BV_OLD",
                currentBvid = "BV_NEW"
            )
        )
    }

    @Test
    fun navigationLeaveShouldProceedWhenBvidMatchesOrMissing() {
        assertTrue(
            shouldHandleNavigationLeaveForBvid(
                expectedBvid = "BV_SAME",
                currentBvid = "BV_SAME"
            )
        )
        assertTrue(
            shouldHandleNavigationLeaveForBvid(
                expectedBvid = null,
                currentBvid = "BV_ANY"
            )
        )
        assertTrue(
            shouldHandleNavigationLeaveForBvid(
                expectedBvid = "BV_ANY",
                currentBvid = null
            )
        )
    }

    @Test
    fun notificationIconShouldFollowSelectedAppIconKey() {
        assertEquals(R.mipmap.ic_launcher_blue_snow_maid_monochrome, resolveNotificationSmallIconRes("icon_blue_snow_maid"))
        assertEquals(R.mipmap.ic_launcher_blue_snow_maid_front_monochrome, resolveNotificationSmallIconRes("icon_blue_snow_maid_front"))
        assertEquals(R.mipmap.ic_launcher_blue_snow_maid_monochrome, resolveNotificationSmallIconRes("icon_telegram_blue"))
        assertEquals(R.mipmap.ic_launcher_blue_snow_maid_monochrome, resolveNotificationSmallIconRes("Dark"))
        assertEquals(R.mipmap.ic_launcher_bilipai_monet_foreground, resolveNotificationSmallIconRes("icon_bilipai"))
        assertEquals(R.mipmap.ic_launcher_bilipai_monet_foreground, resolveNotificationSmallIconRes("icon_bilipai_pink"))
        assertEquals(R.mipmap.ic_launcher_bilipai_monet_foreground, resolveNotificationSmallIconRes("BiliPai White"))
        assertEquals(R.mipmap.ic_launcher_bilipai_monet_foreground, resolveNotificationSmallIconRes("BiliPai Monet"))
        assertEquals(R.drawable.ic_launcher_foreground_flat, resolveNotificationSmallIconRes("icon_3d"))
        assertEquals(R.mipmap.ic_launcher_blue_snow_maid_monochrome, resolveNotificationSmallIconRes("Flat Material"))
        assertEquals(R.mipmap.ic_launcher_blue_snow_maid_monochrome, resolveNotificationSmallIconRes("Neon"))
        assertEquals(R.mipmap.ic_launcher_blue_snow_maid_monochrome, resolveNotificationSmallIconRes("Pink"))
        assertEquals(R.mipmap.ic_launcher_blue_snow_maid_monochrome, resolveNotificationSmallIconRes("unknown_key"))
    }

    @Test
    fun notificationIconShouldAlwaysUseFlatMonochromeResource() {
        // 自适应启动图标在部分系统上会被渲染成纯色圆形，small icon 必须始终走单色资源。
        assertEquals(
            R.mipmap.ic_launcher_bilipai_monet_foreground,
            resolveNotificationIconRes("icon_bilipai")
        )
        assertEquals(
            R.drawable.ic_launcher_foreground_flat,
            resolveNotificationIconRes("icon_3d")
        )
    }

    @Test
    fun playlistNavigationDispatchRequiresCallback() {
        val item = PlaylistItem(
            bvid = "BV1xx",
            title = "sample",
            cover = "",
            owner = "tester"
        )

        var invoked = false
        assertFalse(dispatchPlaylistNavigation(item, callback = null))
        assertFalse(invoked)

        assertTrue(
            dispatchPlaylistNavigation(item) {
                invoked = true
            }
        )
        assertTrue(invoked)
    }

    @Test
    fun playlistNavigationDispatchRejectsBangumiAndEmptyItem() {
        val bangumiItem = PlaylistItem(
            bvid = "BV2yy",
            title = "ep",
            cover = "",
            owner = "tester",
            isBangumi = true
        )
        assertFalse(dispatchPlaylistNavigation(null, callback = {}))
        assertFalse(dispatchPlaylistNavigation(bangumiItem, callback = {}))
    }

    @Test
    fun bangumiNavigationDispatchRequiresValidEpisodeContext() {
        val bangumiItem = PlaylistItem(
            bvid = "",
            title = "第1话",
            cover = "",
            owner = "番剧",
            isBangumi = true,
            seasonId = 100L,
            epId = 200L
        )
        var invoked = false
        assertTrue(
            dispatchBangumiNavigation(bangumiItem) {
                invoked = true
            }
        )
        assertTrue(invoked)

        assertFalse(
            dispatchBangumiNavigation(
                bangumiItem.copy(seasonId = 0L),
                callback = {}
            )
        )
        assertFalse(
            dispatchBangumiNavigation(
                bangumiItem.copy(epId = null),
                callback = {}
            )
        )
        assertFalse(dispatchBangumiNavigation(bangumiItem, callback = null))
        assertFalse(dispatchBangumiNavigation(null, callback = {}))
        assertFalse(dispatchBangumiNavigation(bangumiItem.copy(isBangumi = false), callback = {}))
    }

    @Test
    fun mediaButtonControlTypeShouldMapKeyEvents() {
        assertEquals(
            MediaControlType.PREVIOUS,
            resolveMediaButtonControlType(
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                action = android.view.KeyEvent.ACTION_DOWN
            )
        )
        assertEquals(
            MediaControlType.PLAY_PAUSE,
            resolveMediaButtonControlType(
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = android.view.KeyEvent.ACTION_DOWN
            )
        )
        assertEquals(
            MediaControlType.NEXT,
            resolveMediaButtonControlType(
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                action = android.view.KeyEvent.ACTION_DOWN
            )
        )
        assertNull(
            resolveMediaButtonControlType(
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                action = android.view.KeyEvent.ACTION_UP
            )
        )
    }

    @Test
    fun controlTypeShouldResolveFromIntentExtra() {
        assertEquals(MediaControlType.PREVIOUS, resolveMediaControlType(MiniPlayerManager.ACTION_PREVIOUS))
        assertEquals(MediaControlType.PLAY_PAUSE, resolveMediaControlType(MiniPlayerManager.ACTION_PLAY_PAUSE))
        assertEquals(MediaControlType.NEXT, resolveMediaControlType(MiniPlayerManager.ACTION_NEXT))
        assertNull(resolveMediaControlType(-1))
    }

    @Test
    fun notificationPlaybackStateShouldPreferActualPlayerState() {
        assertTrue(
            resolveNotificationIsPlaying(
                playerIsPlaying = true,
                cachedIsPlaying = false
            )
        )
        assertFalse(
            resolveNotificationIsPlaying(
                playerIsPlaying = false,
                cachedIsPlaying = true
            )
        )
    }

    @Test
    fun notificationPlaybackStateFallsBackToCacheWhenPlayerStateUnknown() {
        assertTrue(
            resolveNotificationIsPlaying(
                playerIsPlaying = null,
                cachedIsPlaying = true
            )
        )
        assertFalse(
            resolveNotificationIsPlaying(
                playerIsPlaying = null,
                cachedIsPlaying = false
            )
        )
    }

    @Test
    fun playbackStateChangeShouldRefreshNotificationOnlyWhenMetadataReadyAndActive() {
        assertTrue(
            shouldRefreshNotificationOnPlaybackStateChange(
                isActive = true,
                title = "测试视频"
            )
        )
        assertFalse(
            shouldRefreshNotificationOnPlaybackStateChange(
                isActive = false,
                title = "测试视频"
            )
        )
        assertFalse(
            shouldRefreshNotificationOnPlaybackStateChange(
                isActive = true,
                title = ""
            )
        )
    }

    @Test
    fun playbackNotificationVisibleOnlyWhilePlaybackIsActuallyActive() {
        assertTrue(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "测试视频",
                isPlaying = true,
                appInBackground = false
            )
        )
        assertFalse(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "测试视频",
                isPlaying = false,
                appInBackground = false
            )
        )
        assertFalse(
            shouldKeepPlaybackNotificationVisible(
                isActive = false,
                title = "测试视频",
                isPlaying = true,
                appInBackground = false
            )
        )
        assertFalse(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "",
                isPlaying = true,
                appInBackground = false
            )
        )
    }

    @Test
    fun appResumeShouldClearStalePlaybackNotificationWhenIdle() {
        assertTrue(
            shouldClearStalePlaybackNotificationOnAppResume(
                isActive = false,
                playerIsPlaying = false
            )
        )
        assertTrue(
            shouldClearStalePlaybackNotificationOnAppResume(
                isActive = true,
                playerIsPlaying = false
            )
        )
        assertFalse(
            shouldClearStalePlaybackNotificationOnAppResume(
                isActive = true,
                playerIsPlaying = true
            )
        )
    }

    @Test
    fun metadataCoverUrlShouldFallbackToCachedValueWhenIncomingCoverEmpty() {
        assertEquals(
            "https://example.com/cached.jpg",
            resolveEffectiveNotificationCoverUrl(
                incomingCoverUrl = "",
                cachedCoverUrl = "https://example.com/cached.jpg"
            )
        )
        assertEquals(
            "https://example.com/new.jpg",
            resolveEffectiveNotificationCoverUrl(
                incomingCoverUrl = "https://example.com/new.jpg",
                cachedCoverUrl = "https://example.com/cached.jpg"
            )
        )
    }

    @Test
    fun notificationArtworkShouldKeepPreviousBitmapWhenIncomingArtworkMissing() {
        assertEquals(
            "cached-artwork",
            resolveEffectiveNotificationArtwork(
                incomingArtwork = null,
                cachedArtwork = "cached-artwork"
            )
        )
        assertEquals(
            "new-artwork",
            resolveEffectiveNotificationArtwork(
                incomingArtwork = "new-artwork",
                cachedArtwork = "cached-artwork"
            )
        )
    }

    @Test
    fun pausedBackgroundSessionShouldKeepPlaybackNotificationVisible() {
        assertTrue(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "Background playback",
                isPlaying = false,
                appInBackground = true
            )
        )
    }

    @Test
    fun bufferingStartupShouldKeepPlaybackNotificationVisible() {
        assertTrue(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "Cold start",
                isPlaying = false,
                appInBackground = false,
                isStartingPlayback = true
            )
        )
        assertFalse(
            shouldKeepPlaybackNotificationVisible(
                isActive = false,
                title = "Cold start",
                isPlaying = false,
                appInBackground = false,
                isStartingPlayback = true
            )
        )
    }

    @Test
    fun onlyBufferingWithPlayWhenReadyCountsAsStartingPlayback() {
        assertTrue(isPlayerStartingPlayback(playWhenReady = true, playbackState = Player.STATE_BUFFERING))
        assertFalse(isPlayerStartingPlayback(playWhenReady = false, playbackState = Player.STATE_BUFFERING))
        assertFalse(isPlayerStartingPlayback(playWhenReady = true, playbackState = Player.STATE_IDLE))
        assertFalse(isPlayerStartingPlayback(playWhenReady = true, playbackState = Player.STATE_ENDED))
    }

    @Test
    fun pausedForegroundSessionShouldNotKeepPlaybackNotificationVisible() {
        assertFalse(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "Foreground playback",
                isPlaying = false,
                appInBackground = false
            )
        )
    }

    @Test
    fun mediaSessionShouldRebindWhenPlaybackPlayerDiffers() {
        val sessionPlayer = Any()
        val playbackPlayer = Any()
        assertTrue(
            shouldRebindMediaSessionPlayer(
                sessionPlayer = sessionPlayer,
                playbackPlayer = playbackPlayer
            )
        )
        assertFalse(
            shouldRebindMediaSessionPlayer(
                sessionPlayer = sessionPlayer,
                playbackPlayer = sessionPlayer
            )
        )
        assertFalse(
            shouldRebindMediaSessionPlayer(
                sessionPlayer = sessionPlayer,
                playbackPlayer = null
            )
        )
    }

    @Test
    fun videoSkipShouldFallbackToDirectBackgroundExecutionWhenCallbackMissing() {
        val item = PlaylistItem(
            bvid = "BV1direct",
            title = "next",
            cover = "",
            owner = "tester"
        )

        assertEquals(
            PlaylistSkipExecutionMode.DIRECT_BACKGROUND,
            resolvePlaylistSkipExecutionMode(
                item = item,
                callbackAvailable = false,
                hasDirectPlaybackContext = true
            )
        )
    }

    @Test
    fun bangumiSkipShouldStillRequireCallbackEvenWhenDirectContextExists() {
        val item = PlaylistItem(
            bvid = "",
            title = "第1话",
            cover = "",
            owner = "番剧",
            isBangumi = true,
            seasonId = 100L,
            epId = 200L
        )

        assertEquals(
            PlaylistSkipExecutionMode.NONE,
            resolvePlaylistSkipExecutionMode(
                item = item,
                callbackAvailable = false,
                hasDirectPlaybackContext = true
            )
        )
        assertEquals(
            PlaylistSkipExecutionMode.CALLBACK,
            resolvePlaylistSkipExecutionMode(
                item = item,
                callbackAvailable = true,
                hasDirectPlaybackContext = true
            )
        )
    }

    @Test
    fun playlistSkipExecutionModeShouldRejectMissingItemOrPlaybackContext() {
        assertEquals(
            PlaylistSkipExecutionMode.NONE,
            resolvePlaylistSkipExecutionMode(
                item = null,
                callbackAvailable = false,
                hasDirectPlaybackContext = true
            )
        )
        assertEquals(
            PlaylistSkipExecutionMode.NONE,
            resolvePlaylistSkipExecutionMode(
                item = PlaylistItem(
                    bvid = "BV1noop",
                    title = "sample",
                    cover = "",
                    owner = "tester"
                ),
                callbackAvailable = false,
                hasDirectPlaybackContext = false
            )
        )
    }

    @Test
    fun externalTransportActionOrderShouldPrioritizeNextBeforePlayPauseAndPrevious() {
        assertEquals(
            listOf(
                MiniPlayerManager.ACTION_NEXT,
                MiniPlayerManager.ACTION_PLAY_PAUSE,
                MiniPlayerManager.ACTION_PREVIOUS
            ),
            resolveExternalTransportActionOrder().toList()
        )
    }
}
