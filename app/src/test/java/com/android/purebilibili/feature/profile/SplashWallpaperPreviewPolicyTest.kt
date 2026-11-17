package com.android.purebilibili.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class SplashWallpaperPreviewPolicyTest {
    @Test
    fun currentPhonePreviewMatchesTallSplashViewport() {
        assertEquals(
            1280f / 2772f,
            resolveSplashWallpaperPreviewAspectRatio(false, false, 1280, 2772),
            0.0001f,
        )
    }

    @Test
    fun currentTabletPreviewTracksPortraitLandscapeAndNarrowWindow() {
        listOf(1600 to 2560, 2560 to 1600, 1200 to 2560).forEach { (width, height) ->
            assertEquals(
                width.toFloat() / height,
                resolveSplashWallpaperPreviewAspectRatio(true, true, width, height),
                0.0001f,
            )
        }
    }

    @Test
    fun otherDeviceKeepsIllustrativeRatio() {
        assertEquals(1.6f, resolveSplashWallpaperPreviewAspectRatio(true, false, 1280, 2772), 0.0001f)
        assertEquals(0.5f, resolveSplashWallpaperPreviewAspectRatio(false, true, 2560, 1600), 0.0001f)
    }

    @Test
    fun unmeasuredWindowUsesIllustrativeRatio() {
        assertEquals(0.5f, resolveSplashWallpaperPreviewAspectRatio(false, false, 0, 0), 0.0001f)
        assertEquals(1.6f, resolveSplashWallpaperPreviewAspectRatio(true, true, 0, 0), 0.0001f)
    }
}
