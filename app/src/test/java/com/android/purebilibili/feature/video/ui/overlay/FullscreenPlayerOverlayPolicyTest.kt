package com.android.purebilibili.feature.video.ui.overlay

import android.content.pm.ActivityInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenPlayerOverlayPolicyTest {

    @Test
    fun fullscreenDragGestures_enabledWhenControlsHidden() {
        assertTrue(
            shouldStartFullscreenDragGesture(
                gesturesEnabled = true,
                showControls = false,
                startY = 540f,
                screenHeight = 1080f,
                statusBarExclusionZonePx = 40f,
                visibleTopControlsHeightPx = 96f,
                visibleBottomControlsHeightPx = 120f
            )
        )
    }

    @Test
    fun fullscreenDragGestures_protectBottomSystemGestureEdgeWhenControlsHidden() {
        assertFalse(
            shouldStartFullscreenDragGesture(
                gesturesEnabled = true,
                showControls = false,
                startY = 1030f,
                screenHeight = 1080f,
                statusBarExclusionZonePx = 48f,
                visibleTopControlsHeightPx = 96f,
                visibleBottomControlsHeightPx = 120f,
                bottomEdgeExclusionZonePx = 56f,
            )
        )
    }

    @Test
    fun fullscreenDragGestures_protectVisibleTopControls() {
        assertFalse(
            shouldStartFullscreenDragGesture(
                gesturesEnabled = true,
                showControls = true,
                startY = 72f,
                screenHeight = 1080f,
                statusBarExclusionZonePx = 40f,
                visibleTopControlsHeightPx = 96f,
                visibleBottomControlsHeightPx = 120f
            )
        )
    }

    @Test
    fun fullscreenCenterSeekGesture_staysEnabledWhenControlsVisible() {
        assertTrue(
            shouldStartFullscreenDragGesture(
                gesturesEnabled = true,
                showControls = true,
                startY = 540f,
                screenHeight = 1080f,
                statusBarExclusionZonePx = 40f,
                visibleTopControlsHeightPx = 96f,
                visibleBottomControlsHeightPx = 120f
            )
        )
    }

    @Test
    fun fullscreenBottomGestureExclusion_matchesVisibleBottomControlsHeight() {
        assertEquals(90, resolveFullscreenVisibleBottomControlsGestureExclusionHeightDp())
    }

    @Test
    fun fullscreenLandscapeSeekGesture_allowsLowerMiddleAboveVisibleControls() {
        assertTrue(
            shouldStartFullscreenDragGesture(
                gesturesEnabled = true,
                showControls = true,
                startY = 260f,
                screenHeight = 360f,
                statusBarExclusionZonePx = 40f,
                visibleTopControlsHeightPx = 96f,
                visibleBottomControlsHeightPx = resolveFullscreenVisibleBottomControlsGestureExclusionHeightDp().toFloat()
            )
        )
    }

    @Test
    fun fullscreenPendingGestureSeekPosition_holdsUntilPlayerReportsTarget() {
        assertEquals(
            25_000L,
            resolveFullscreenPendingGestureSeekPosition(
                currentPositionMs = 10_000L,
                pendingSeekPositionMs = 25_000L
            )
        )
        assertEquals(
            null,
            resolveFullscreenPendingGestureSeekPosition(
                currentPositionMs = 24_700L,
                pendingSeekPositionMs = 25_000L
            )
        )
    }

    @Test
    fun fullscreenOverlayExitOrientation_restoresOriginalRequestForPhoneAndTablet() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            resolveFullscreenOverlayExitRequestedOrientation(
                originalRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolveFullscreenOverlayExitRequestedOrientation(
                originalRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            resolveFullscreenOverlayExitRequestedOrientation(
                originalRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            )
        )
    }

    @Test
    fun fullscreenPlaybackButton_usesThemeTintedNativeIconWithoutSurfaceShadow() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/ui/overlay/FullscreenPlayerOverlay.kt")
            .readText()
        val playbackButtonIndex = source.indexOf("applyPlaybackButtonUserAction(")
        assertTrue(playbackButtonIndex >= 0)
        val iconButtonIndex = source.lastIndexOf("IconButton(", playbackButtonIndex)
        assertTrue(iconButtonIndex >= 0)
        val buttonSource = source.substring(
            iconButtonIndex,
            playbackButtonIndex + 760
        )

        assertTrue(buttonSource.contains("IconButton("))
        assertTrue(buttonSource.contains("Icons.Filled.Pause"))
        assertTrue(buttonSource.contains("Icons.Filled.PlayArrow"))
        assertTrue(buttonSource.contains("tint = AppContentColors.accent()"))
        assertFalse(buttonSource.contains("Surface("))
    }

    @Test
    fun fullscreenDragGestures_protectVisibleBottomControls() {
        assertFalse(
            shouldStartFullscreenDragGesture(
                gesturesEnabled = true,
                showControls = true,
                startY = 1000f,
                screenHeight = 1080f,
                statusBarExclusionZonePx = 40f,
                visibleTopControlsHeightPx = 96f,
                visibleBottomControlsHeightPx = 120f
            )
        )
    }

    @Test
    fun fullscreenDragGestures_disabledWhenDialogsAlreadyBlockGestures() {
        assertFalse(
            shouldStartFullscreenDragGesture(
                gesturesEnabled = false,
                showControls = false,
                startY = 540f,
                screenHeight = 1080f,
                statusBarExclusionZonePx = 40f,
                visibleTopControlsHeightPx = 96f,
                visibleBottomControlsHeightPx = 120f
            )
        )
    }
}
