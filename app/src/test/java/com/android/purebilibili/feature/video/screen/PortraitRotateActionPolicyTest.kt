package com.android.purebilibili.feature.video.screen

import android.content.pm.ActivityInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PortraitRotateActionPolicyTest {

    @Test
    fun portraitRotateAction_forcesLandscapeWhenPhoneUsesOrientationDrivenFullscreen() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolvePortraitRotateTargetOrientation(
                isOrientationDrivenFullscreen = true,
            )
        )
    }

    @Test
    fun portraitRotateAction_returnsNullWhenOrientationIsNotDriver() {
        assertNull(
            resolvePortraitRotateTargetOrientation(
                isOrientationDrivenFullscreen = false,
            )
        )
    }

    @Test
    fun explicitPortraitRotateActionRemainsAvailableAfterPortraitEntry() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            resolvePhoneVideoRequestedOrientation(
                autoRotateEnabled = true,
                fullscreenMode = com.android.purebilibili.core.store.FullscreenMode.AUTO,
                isCompactDevice = true,
                isOrientationDrivenFullscreen = true,
                isFullscreenMode = false,
                manualPortraitHoldActive = true,
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolvePortraitRotateTargetOrientation(isOrientationDrivenFullscreen = true)
        )
    }
}
