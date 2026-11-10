package com.android.purebilibili.feature.video.ui.section

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.feature.video.ui.components.GesturePercentTransitionDirection
import com.android.purebilibili.feature.video.ui.components.resolveGesturePercentTransitionDirection
import com.android.purebilibili.feature.video.ui.components.shouldTriggerGesturePercentHaptic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoGestureFeedbackPolicyTest {

    @Test
    fun `resolveOrientationSwitchHintText returns landscape copy`() {
        assertEquals("已切换到横屏", resolveOrientationSwitchHintText(isFullscreen = true))
    }

    @Test
    fun `resolveOrientationSwitchHintText returns portrait copy`() {
        assertEquals("已切换到竖屏", resolveOrientationSwitchHintText(isFullscreen = false))
    }

    @Test
    fun `resolveGestureIndicatorLabel returns brightness label`() {
        assertEquals("亮度", resolveGestureIndicatorLabel(VideoGestureMode.Brightness))
    }

    @Test
    fun `resolveGestureIndicatorLabel returns volume label`() {
        assertEquals("音量", resolveGestureIndicatorLabel(VideoGestureMode.Volume))
    }

    @Test
    fun `resolveGestureIndicatorLabel returns empty for non level mode`() {
        assertEquals("", resolveGestureIndicatorLabel(VideoGestureMode.Seek))
    }

    @Test
    fun `resolveGestureLevelIconStyle maps three distinct themes`() {
        assertEquals(
            GestureLevelIconStyle.Md3,
            resolveGestureLevelIconStyle(AppTopTabPresentation.MATERIAL_UNDERLINE)
        )
        assertEquals(
            GestureLevelIconStyle.Ios,
            resolveGestureLevelIconStyle(AppTopTabPresentation.MOVING_CAPSULE)
        )
        assertEquals(
            GestureLevelIconStyle.Miuix,
            resolveGestureLevelIconStyle(AppTopTabPresentation.TONAL_CAPSULE)
        )
    }

    @Test
    fun `resolveGestureDisplayIcon maps brightness level for md3`() {
        assertEquals(
            Icons.Filled.BrightnessLow,
            resolveGestureDisplayIcon(
                mode = VideoGestureMode.Brightness,
                percent = 0.2f,
                fallbackIcon = null,
                iconStyle = GestureLevelIconStyle.Md3
            )
        )
        assertEquals(
            Icons.Filled.BrightnessMedium,
            resolveGestureDisplayIcon(
                mode = VideoGestureMode.Brightness,
                percent = 0.52f,
                fallbackIcon = null,
                iconStyle = GestureLevelIconStyle.Md3
            )
        )
        assertEquals(
            Icons.Filled.BrightnessHigh,
            resolveGestureDisplayIcon(
                mode = VideoGestureMode.Brightness,
                percent = 0.92f,
                fallbackIcon = null,
                iconStyle = GestureLevelIconStyle.Md3
            )
        )
    }

    @Test
    fun `resolveGestureDisplayIcon maps volume level for md3`() {
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeOff,
            resolveGestureDisplayIcon(
                mode = VideoGestureMode.Volume,
                percent = 0f,
                fallbackIcon = null,
                iconStyle = GestureLevelIconStyle.Md3
            )
        )
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeMute,
            resolveGestureDisplayIcon(
                mode = VideoGestureMode.Volume,
                percent = 0.2f,
                fallbackIcon = null,
                iconStyle = GestureLevelIconStyle.Md3
            )
        )
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeDown,
            resolveGestureDisplayIcon(
                mode = VideoGestureMode.Volume,
                percent = 0.5f,
                fallbackIcon = null,
                iconStyle = GestureLevelIconStyle.Md3
            )
        )
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeUp,
            resolveGestureDisplayIcon(
                mode = VideoGestureMode.Volume,
                percent = 0.9f,
                fallbackIcon = null,
                iconStyle = GestureLevelIconStyle.Md3
            )
        )
    }

    @Test
    fun `resolveGestureDisplayIcon maps miuix volume and brightness ladders`() {
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeOff,
            resolveVolumeGestureIcon(
                percent = 0f,
                iconStyle = GestureLevelIconStyle.Miuix
            )
        )
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeUp,
            resolveVolumeGestureIcon(
                percent = 0.9f,
                iconStyle = GestureLevelIconStyle.Miuix
            )
        )
        assertEquals(
            Icons.Filled.Brightness4,
            resolveBrightnessGestureIcon(
                percent = 0.1f,
                iconStyle = GestureLevelIconStyle.Miuix
            )
        )
        assertEquals(
            Icons.Filled.Brightness7,
            resolveBrightnessGestureIcon(
                percent = 0.9f,
                iconStyle = GestureLevelIconStyle.Miuix
            )
        )
    }

    @Test
    fun `resolveGestureDisplayIcon falls back for unsupported mode`() {
        assertEquals(
            Icons.Filled.BrightnessHigh,
            resolveGestureDisplayIcon(
                mode = VideoGestureMode.Seek,
                percent = 0.5f,
                fallbackIcon = null,
                iconStyle = GestureLevelIconStyle.Md3
            )
        )
    }

    @Test
    fun `shouldTriggerFullscreenBySwipe follows default direction`() {
        assertEquals(
            true,
            shouldTriggerFullscreenBySwipe(
                isFullscreen = false,
                reverseGesture = false,
                totalDragDistanceY = -80f,
                thresholdPx = 50f
            )
        )
        assertEquals(
            true,
            shouldTriggerFullscreenBySwipe(
                isFullscreen = true,
                reverseGesture = false,
                totalDragDistanceY = 80f,
                thresholdPx = 50f
            )
        )
    }

    @Test
    fun `shouldTriggerFullscreenBySwipe supports reverse direction`() {
        assertEquals(
            true,
            shouldTriggerFullscreenBySwipe(
                isFullscreen = false,
                reverseGesture = true,
                totalDragDistanceY = 80f,
                thresholdPx = 50f
            )
        )
        assertEquals(
            true,
            shouldTriggerFullscreenBySwipe(
                isFullscreen = true,
                reverseGesture = true,
                totalDragDistanceY = -80f,
                thresholdPx = 50f
            )
        )
    }

    @Test
    fun `vertical inline swipe dispatches directly to portrait fullscreen`() {
        assertEquals(
            true,
            shouldEnterPortraitFullscreenFromSwipe(
                isFullscreen = false,
                isVerticalVideo = true,
            )
        )
        assertEquals(
            false,
            shouldEnterPortraitFullscreenFromSwipe(
                isFullscreen = false,
                isVerticalVideo = false,
            )
        )
        assertEquals(
            false,
            shouldEnterPortraitFullscreenFromSwipe(
                isFullscreen = true,
                isVerticalVideo = true,
            )
        )
    }

    @Test
    fun `portrait entry requires clear vertical direction and respects reverse gesture`() {
        fun enters(dx: Float, dy: Float, reversed: Boolean = false, fullscreen: Boolean = false, vertical: Boolean = true) =
            shouldTriggerPortraitFullscreenDuringDrag(fullscreen, vertical, reversed, dx, dy, 2.5f)

        assertEquals(true, enters(0f, -3f))
        assertEquals(false, enters(1f, -3f))
        assertEquals(false, enters(0f, -2.5f))
        assertEquals(false, enters(0f, 3f))
        assertEquals(true, enters(0f, 3f, reversed = true))
        assertEquals(false, enters(0f, -3f, fullscreen = true))
        assertEquals(false, enters(0f, -3f, vertical = false))
    }

    @Test
    fun `resolveVerticalGestureMode uses portrait swipe setting for upward entry`() {
        assertEquals(
            VideoGestureMode.SwipeToFullscreen,
            resolveVerticalGestureMode(
                isFullscreen = false,
                isSwipeUp = true,
                startX = 500f,
                leftZoneEnd = 300f,
                rightZoneStart = 700f,
                portraitSwipeToFullscreenEnabled = true,
                centerSwipeToFullscreenEnabled = false
            )
        )
    }

    @Test
    fun `resolveVerticalGestureMode keeps edge volume brightness ahead of portrait fullscreen`() {
        assertEquals(
            VideoGestureMode.Brightness,
            resolveVerticalGestureMode(
                isFullscreen = false,
                isSwipeUp = true,
                startX = 120f,
                leftZoneEnd = 300f,
                rightZoneStart = 700f,
                portraitSwipeToFullscreenEnabled = true,
                centerSwipeToFullscreenEnabled = false
            )
        )
        assertEquals(
            VideoGestureMode.Volume,
            resolveVerticalGestureMode(
                isFullscreen = false,
                isSwipeUp = true,
                startX = 920f,
                leftZoneEnd = 300f,
                rightZoneStart = 700f,
                portraitSwipeToFullscreenEnabled = true,
                centerSwipeToFullscreenEnabled = false
            )
        )
    }

    @Test
    fun `resolveVerticalGestureMode uses center setting in fullscreen independently`() {
        assertEquals(
            VideoGestureMode.SwipeToFullscreen,
            resolveVerticalGestureMode(
                isFullscreen = true,
                isSwipeUp = false,
                startX = 500f,
                leftZoneEnd = 300f,
                rightZoneStart = 700f,
                portraitSwipeToFullscreenEnabled = false,
                centerSwipeToFullscreenEnabled = true
            )
        )
    }

    @Test
    fun `resolveVerticalGestureMode returns none when center swipe disabled`() {
        assertEquals(
            VideoGestureMode.None,
            resolveVerticalGestureMode(
                isFullscreen = true,
                isSwipeUp = false,
                startX = 500f,
                leftZoneEnd = 300f,
                rightZoneStart = 700f,
                portraitSwipeToFullscreenEnabled = false,
                centerSwipeToFullscreenEnabled = false
            )
        )
    }

    @Test
    fun `resolveVerticalGestureMode disables brightness volume when setting off`() {
        assertEquals(
            VideoGestureMode.None,
            resolveVerticalGestureMode(
                isFullscreen = true,
                isSwipeUp = false,
                startX = 120f,
                leftZoneEnd = 300f,
                rightZoneStart = 700f,
                portraitSwipeToFullscreenEnabled = false,
                centerSwipeToFullscreenEnabled = false,
                slideVolumeBrightnessEnabled = false
            )
        )
        assertEquals(
            VideoGestureMode.None,
            resolveVerticalGestureMode(
                isFullscreen = true,
                isSwipeUp = false,
                startX = 920f,
                leftZoneEnd = 300f,
                rightZoneStart = 700f,
                portraitSwipeToFullscreenEnabled = false,
                centerSwipeToFullscreenEnabled = false,
                slideVolumeBrightnessEnabled = false
            )
        )
    }

    @Test
    fun `shouldShowDanmakuLayers respects pip no danmaku setting`() {
        assertEquals(
            true,
            shouldShowDanmakuLayers(
                isInPipMode = true,
                danmakuEnabled = true,
                isPortraitFullscreen = false,
                pipNoDanmakuEnabled = false,
                hostLifecycleStarted = true
            )
        )
        assertEquals(
            false,
            shouldShowDanmakuLayers(
                isInPipMode = true,
                danmakuEnabled = true,
                isPortraitFullscreen = false,
                pipNoDanmakuEnabled = true,
                hostLifecycleStarted = true
            )
        )
        assertEquals(
            false,
            shouldShowDanmakuLayers(
                isInPipMode = false,
                danmakuEnabled = false,
                isPortraitFullscreen = false,
                pipNoDanmakuEnabled = false,
                hostLifecycleStarted = true
            )
        )
    }

    @Test
    fun `resolveGesturePercentTransitionDirection follows value delta`() {
        assertEquals(
            GesturePercentTransitionDirection.Increase,
            resolveGesturePercentTransitionDirection(previousPercent = 40, currentPercent = 41)
        )
        assertEquals(
            GesturePercentTransitionDirection.Decrease,
            resolveGesturePercentTransitionDirection(previousPercent = 41, currentPercent = 40)
        )
        assertEquals(
            GesturePercentTransitionDirection.None,
            resolveGesturePercentTransitionDirection(previousPercent = 40, currentPercent = 40)
        )
    }

    @Test
    fun `shouldTriggerGesturePercentHaptic only ticks crossed steps and endpoints`() {
        assertFalse(shouldTriggerGesturePercentHaptic(previousPercent = 41, currentPercent = 42))
        assertTrue(shouldTriggerGesturePercentHaptic(previousPercent = 44, currentPercent = 45))
        assertTrue(shouldTriggerGesturePercentHaptic(previousPercent = 46, currentPercent = 45))
        assertTrue(shouldTriggerGesturePercentHaptic(previousPercent = 98, currentPercent = 100))
        assertTrue(shouldTriggerGesturePercentHaptic(previousPercent = 2, currentPercent = 0))
    }

    @Test
    fun `resolveGestureLevelOverlayVisualPolicy returns warm accent for brightness`() {
        val policy = resolveGestureLevelOverlayVisualPolicy(
            mode = VideoGestureMode.Brightness,
            percent = 0.3f
        )

        assertEquals(Color(0xFFFFD54F), policy.accentColor)
        // Scrim must stay opaque enough for white text over bright video frames.
        assertTrue(policy.containerAlpha >= 0.70f)
    }

    @Test
    fun `resolveGestureLevelOverlayVisualPolicy returns cool accent for volume`() {
        val policy = resolveGestureLevelOverlayVisualPolicy(
            mode = VideoGestureMode.Volume,
            percent = 0.8f
        )

        assertEquals(Color(0xFF80DEEA), policy.accentColor)
        assertTrue(policy.glowAlpha > 0.3f)
        assertTrue(policy.containerAlpha >= 0.70f)
    }

    @Test
    fun `resolveGestureRenderProgress clamps to safe range`() {
        assertEquals(0f, resolveGestureRenderProgress(-0.3f))
        assertEquals(0.6f, resolveGestureRenderProgress(0.6f))
        assertEquals(1f, resolveGestureRenderProgress(1.4f))
    }
}
