package com.android.purebilibili.feature.video.screen

import android.content.pm.ActivityInfo
import com.android.purebilibili.core.util.PlayerRotationSensitivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerRotationSensitivityTest {
    private fun orientation(degrees: Int, landscape: Boolean, preset: PlayerRotationSensitivity, hold: Boolean = false): Int? {
        val t = preset.thresholds
        return resolvePhoneAutoRotateRequestedOrientation(
            degrees, landscape,
            landscapeHoldEnabled = hold,
            portraitSnapDegrees = t.portraitSnap,
            landscapeEnterMinDegrees = t.landscapeEnterMin,
            landscapeEnterMaxDegrees = t.landscapeEnterMax,
            landscapeKeepMinDegrees = t.landscapeKeepMin,
            landscapeKeepMaxDegrees = t.landscapeKeepMax,
        )
    }

    @Test
    fun `entry and retention thresholds are mirrored for both landscape sides`() {
        PlayerRotationSensitivity.entries.forEach { preset ->
            val t = preset.thresholds
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, orientation(t.landscapeEnterMin, false, preset))
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, orientation(t.landscapeEnterMin + 180, false, preset))
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, orientation(t.landscapeKeepMax, true, preset))
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, orientation(t.landscapeKeepMax + 180, true, preset))
            assertNull(orientation(t.landscapeEnterMin - 1, false, preset))
            assertNull(orientation(t.landscapeEnterMin + 179, false, preset))
        }
    }

    @Test
    fun `sensitive enters earlier while hold and upside down avoid unintended portrait exits`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, orientation(55, false, PlayerRotationSensitivity.SENSITIVE))
        assertNull(orientation(55, false, PlayerRotationSensitivity.STEADY))
        PlayerRotationSensitivity.entries.forEach { preset ->
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, orientation(0, true, preset))
            assertNull(orientation(0, true, preset, hold = true))
            assertNull(orientation(180, true, preset))
            assertNull(orientation(-1, true, preset))
        }
        assertEquals(PlayerRotationSensitivity.STANDARD, PlayerRotationSensitivity.fromValue(999))
    }

    @Test
    fun `recent direction changes retain the existing settling protection`() {
        assertNull(resolvePhoneAutoRotateTargetToApply(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, 1_000L, 1_100L))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, resolvePhoneAutoRotateTargetToApply(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, 1_000L, 1_600L))
    }
}
