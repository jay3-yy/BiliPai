package com.android.purebilibili.feature.video.player

import com.android.purebilibili.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackServicePolicyTest {

    @Test
    fun `notification media control should promote foreground before handling playback`() {
        assertTrue(
            shouldRefreshPlaybackServiceForeground(
                action = PlaybackService.ACTION_MEDIA_CONTROL,
                isForegroundStarted = true
            )
        )
    }

    @Test
    fun `foreground service should refresh promotion even when already started`() {
        assertTrue(
            shouldRefreshPlaybackServiceForeground(
                action = PlaybackService.ACTION_START_FOREGROUND,
                isForegroundStarted = true
            )
        )
    }

    @Test
    fun `stop command should not stop service when newer start exists`() {
        assertFalse(
            shouldStopPlaybackServiceForStartId(
                stopActionStartId = 4,
                latestHandledStartId = 5
            )
        )
    }

    @Test
    fun `fallback foreground notification required when primary notification is null`() {
        assertTrue(shouldStartForegroundWithFallback(primaryNotification = null))
    }

    @Test
    fun `fallback foreground notification not required when primary notification exists`() {
        val placeholder = Any()
        assertFalse(shouldStartForegroundWithFallback(primaryNotification = placeholder))
    }

    @Test
    fun `fallback notification icon should follow selected app icon`() {
        assertEquals(
            R.mipmap.ic_launcher_3d,
            resolvePlaybackServiceFallbackIconRes("icon_3d")
        )
        assertEquals(
            R.mipmap.ic_launcher_blue_snow_maid_monochrome,
            resolvePlaybackServiceFallbackIconRes("unknown")
        )
    }
}
