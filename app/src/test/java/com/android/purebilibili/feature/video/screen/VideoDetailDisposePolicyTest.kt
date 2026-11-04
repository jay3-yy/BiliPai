package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailDisposePolicyTest {

    @Test
    fun videoSwitchFlagUsesLocalStateAsFallbackWhenManagerFlagIsResetEarly() {
        assertTrue(
            resolveIsNavigatingToVideoDuringDispose(
                localNavigatingToVideo = true,
                managerNavigatingToVideo = false
            )
        )
    }

    @Test
    fun videoSwitchFlagUsesManagerStateWhenAvailable() {
        assertTrue(
            resolveIsNavigatingToVideoDuringDispose(
                localNavigatingToVideo = false,
                managerNavigatingToVideo = true
            )
        )
    }

    @Test
    fun disposeHandledAsNavigationExitWhenNoKeepAliveConditionMatches() {
        assertTrue(
            shouldHandleVideoDetailDisposeAsNavigationExit(
                isNavigatingToAudioMode = false,
                isNavigatingToMiniMode = false,
                isMiniModeActive = false,
                isChangingConfigurations = false,
                isNavigatingToVideo = false
            )
        )
    }

    @Test
    fun disposeSkippedWhenNavigatingToAudioMode() {
        assertFalse(
            shouldHandleVideoDetailDisposeAsNavigationExit(
                isNavigatingToAudioMode = true,
                isNavigatingToMiniMode = false,
                isMiniModeActive = false,
                isChangingConfigurations = false,
                isNavigatingToVideo = false
            )
        )
    }

    @Test
    fun disposeSkippedWhenNavigatingToMiniMode() {
        assertFalse(
            shouldHandleVideoDetailDisposeAsNavigationExit(
                isNavigatingToAudioMode = false,
                isNavigatingToMiniMode = true,
                isMiniModeActive = false,
                isChangingConfigurations = false,
                isNavigatingToVideo = false
            )
        )
    }

    @Test
    fun disposeSkippedWhenMiniModeWasActivatedByNavigationLayer() {
        assertFalse(
            shouldHandleVideoDetailDisposeAsNavigationExit(
                isNavigatingToAudioMode = false,
                isNavigatingToMiniMode = false,
                isMiniModeActive = true,
                isChangingConfigurations = false,
                isNavigatingToVideo = false
            )
        )
    }

    @Test
    fun disposeSkippedWhenConfigurationChanges() {
        assertFalse(
            shouldHandleVideoDetailDisposeAsNavigationExit(
                isNavigatingToAudioMode = false,
                isNavigatingToMiniMode = false,
                isMiniModeActive = false,
                isChangingConfigurations = true,
                isNavigatingToVideo = false
            )
        )
    }

    @Test
    fun disposeSkippedWhenNavigatingToAnotherVideo() {
        assertFalse(
            shouldHandleVideoDetailDisposeAsNavigationExit(
                isNavigatingToAudioMode = false,
                isNavigatingToMiniMode = false,
                isMiniModeActive = false,
                isChangingConfigurations = false,
                isNavigatingToVideo = true
            )
        )
    }

    @Test
    fun miniPlayerHandoffKeepsPlaybackButReleasesTheDetailsOrientation() {
        // Explicit small-window and navigation-triggered mini playback share this contract.
        for ((navigatingToMini, miniActive) in listOf(true to false, false to true, true to true)) {
            assertFalse(
                shouldHandleVideoDetailDisposeAsNavigationExit(
                    isNavigatingToAudioMode = false,
                    isNavigatingToMiniMode = navigatingToMini,
                    isMiniModeActive = miniActive,
                    isChangingConfigurations = false,
                    isNavigatingToVideo = false,
                )
            )
            assertTrue(
                shouldRestoreVideoDetailOrientationOnDispose(
                    isNavigatingToAudioMode = false,
                    isChangingConfigurations = false,
                    isNavigatingToVideo = false,
                )
            )
        }
    }

    @Test
    fun orientationRestorationDoesNotOverrideAudioConfigurationOrNewDetailOwners() {
        for ((audio, changingConfig, nextVideo) in listOf(
            Triple(true, false, false),
            Triple(false, true, false),
            Triple(false, false, true),
        )) {
            assertFalse(
                shouldRestoreVideoDetailOrientationOnDispose(
                    isNavigatingToAudioMode = audio,
                    isChangingConfigurations = changingConfig,
                    isNavigatingToVideo = nextVideo,
                )
            )
        }
        assertTrue(
            shouldRestoreVideoDetailOrientationOnDispose(
                isNavigatingToAudioMode = false,
                isChangingConfigurations = false,
                isNavigatingToVideo = false,
            )
        )
    }

    @Test
    fun returningState_isMarkedOnlyWhenDisposeShouldBeHandledAsNavigationExit() {
        assertTrue(
            shouldMarkReturningStateOnVideoDetailDispose(
                shouldHandleAsNavigationExit = true
            )
        )
        assertFalse(
            shouldMarkReturningStateOnVideoDetailDispose(
                shouldHandleAsNavigationExit = false
            )
        )
    }

    @Test
    fun enteringVideoDetail_clearsStaleReturningStateWhenPresent() {
        assertTrue(
            shouldClearStaleReturningStateOnVideoDetailEnter(
                isReturningFromDetail = true
            )
        )
        assertFalse(
            shouldClearStaleReturningStateOnVideoDetailEnter(
                isReturningFromDetail = false
            )
        )
    }
}
