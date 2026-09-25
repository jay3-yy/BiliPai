package com.android.purebilibili.feature.video.ui.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TwoFingerSpeedGesturePolicyTest {
    private val supportedSpeeds = listOf(
        0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.3f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f
    )

    @Test
    fun `enabling vertical mode disables horizontal mode`() {
        val current = TwoFingerSpeedToggleState(
            verticalEnabled = false,
            horizontalEnabled = true
        )

        assertEquals(
            TwoFingerSpeedToggleState(
                verticalEnabled = true,
                horizontalEnabled = false
            ),
            applyVerticalTwoFingerSpeedToggle(current, enabled = true)
        )
    }

    @Test
    fun `enabling horizontal mode disables vertical mode`() {
        val current = TwoFingerSpeedToggleState(
            verticalEnabled = true,
            horizontalEnabled = false
        )

        assertEquals(
            TwoFingerSpeedToggleState(
                verticalEnabled = false,
                horizontalEnabled = true
            ),
            applyHorizontalTwoFingerSpeedToggle(current, enabled = true)
        )
    }

    @Test
    fun `disabling one mode does not force enable the other`() {
        assertEquals(
            TwoFingerSpeedToggleState(
                verticalEnabled = false,
                horizontalEnabled = false
            ),
            applyVerticalTwoFingerSpeedToggle(
                current = TwoFingerSpeedToggleState(
                    verticalEnabled = true,
                    horizontalEnabled = false
                ),
                enabled = false
            )
        )
        assertEquals(
            TwoFingerSpeedToggleState(
                verticalEnabled = false,
                horizontalEnabled = false
            ),
            applyHorizontalTwoFingerSpeedToggle(
                current = TwoFingerSpeedToggleState(
                    verticalEnabled = false,
                    horizontalEnabled = true
                ),
                enabled = false
            )
        )
    }

    @Test
    fun `gesture stays off when both toggles are disabled`() {
        assertEquals(
            TwoFingerSpeedGestureMode.Off,
            resolveTwoFingerSpeedGestureMode(
                verticalEnabled = false,
                horizontalEnabled = false
            )
        )
    }

    @Test
    fun `vertical mode only locks when y movement is dominant beyond threshold`() {
        assertEquals(
            LockedTwoFingerSpeedAxis.Vertical,
            resolveLockedTwoFingerSpeedAxis(
                mode = TwoFingerSpeedGestureMode.Vertical,
                totalDragX = 28f,
                totalDragY = -120f,
                thresholdPx = 64f
            )
        )
        assertNull(
            resolveLockedTwoFingerSpeedAxis(
                mode = TwoFingerSpeedGestureMode.Vertical,
                totalDragX = 140f,
                totalDragY = -120f,
                thresholdPx = 64f
            )
        )
        assertNull(
            resolveLockedTwoFingerSpeedAxis(
                mode = TwoFingerSpeedGestureMode.Vertical,
                totalDragX = 10f,
                totalDragY = -40f,
                thresholdPx = 64f
            )
        )
    }

    @Test
    fun `horizontal mode only locks when x movement is dominant beyond threshold`() {
        assertEquals(
            LockedTwoFingerSpeedAxis.Horizontal,
            resolveLockedTwoFingerSpeedAxis(
                mode = TwoFingerSpeedGestureMode.Horizontal,
                totalDragX = 132f,
                totalDragY = 30f,
                thresholdPx = 64f
            )
        )
        assertNull(
            resolveLockedTwoFingerSpeedAxis(
                mode = TwoFingerSpeedGestureMode.Horizontal,
                totalDragX = 132f,
                totalDragY = 136f,
                thresholdPx = 64f
            )
        )
    }

    @Test
    fun `speed snaps upward from nearest supported bucket`() {
        assertEquals(
            1.3f,
            resolveTwoFingerGesturePlaybackSpeed(
                startSpeed = 1.25f,
                mode = TwoFingerSpeedGestureMode.Vertical,
                totalDragX = 0f,
                totalDragY = -120f,
                containerWidthPx = 1000f,
                containerHeightPx = 1000f,
                supportedSpeeds = supportedSpeeds
            )
        )
    }

    @Test
    fun `speed snaps downward and clamps to supported minimum`() {
        assertEquals(
            0.25f,
            resolveTwoFingerGesturePlaybackSpeed(
                startSpeed = 0.5f,
                mode = TwoFingerSpeedGestureMode.Horizontal,
                totalDragX = -600f,
                totalDragY = 0f,
                containerWidthPx = 900f,
                containerHeightPx = 1200f,
                supportedSpeeds = supportedSpeeds
            )
        )
    }

    @Test
    fun `speed change ignores gesture when feature mode is off`() {
        assertEquals(
            1.0f,
            resolveTwoFingerGesturePlaybackSpeed(
                startSpeed = 1.0f,
                mode = TwoFingerSpeedGestureMode.Off,
                totalDragX = 500f,
                totalDragY = -500f,
                containerWidthPx = 900f,
                containerHeightPx = 1200f,
                supportedSpeeds = supportedSpeeds
            )
        )
    }
}
