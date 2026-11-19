package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenPlayerOverlayPollingPolicyTest {

    @Test
    fun foregroundResumeRebindsFullscreenSurface_onlyWhenViewAndPlayerExist() {
        assertTrue(
            shouldRebindFullscreenSurfaceOnResume(
                hasPlayerView = true,
                hasPlayer = true
            )
        )
        assertFalse(
            shouldRebindFullscreenSurfaceOnResume(
                hasPlayerView = false,
                hasPlayer = true
            )
        )
        assertFalse(
            shouldRebindFullscreenSurfaceOnResume(
                hasPlayerView = true,
                hasPlayer = false
            )
        )
    }

    @Test
    fun pausedPlayback_keepsFullscreenControlsVisible() {
        assertFalse(
            shouldAutoHideFullscreenControls(
                showControls = true,
                gestureMode = FullscreenGestureMode.None,
                isPlaying = false
            )
        )
    }

    @Test
    fun activePlayback_stillAutoHidesFullscreenControlsWithoutGesture() {
        assertTrue(
            shouldAutoHideFullscreenControls(
                showControls = true,
                gestureMode = FullscreenGestureMode.None,
                isPlaying = true
            )
        )
    }
}
