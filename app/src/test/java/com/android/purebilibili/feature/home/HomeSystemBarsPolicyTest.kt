package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeSystemBarsPolicyTest {

    @Test
    fun systemBars_areOnlyOwnedWhileHomeIsTopLevel() {
        assertTrue(shouldApplyHomeSystemBars(isTopLevelActive = true))
        assertFalse(shouldApplyHomeSystemBars(isTopLevelActive = false))
    }

    @Test
    fun topSkinArtworkControlsStatusBarIconContrast() {
        assertFalse(resolveHomeStatusBarDarkIcons(true, "dark", true, true))
        assertTrue(resolveHomeStatusBarDarkIcons(true, "light", false, false))
        assertTrue(resolveHomeStatusBarDarkIcons(true, null, true, false))
        assertFalse(resolveHomeStatusBarDarkIcons(false, "light", true, false))
        // 真实像素采样与深色前景兜底优先修正错误/缺失的 color_mode
        assertTrue(
            resolveHomeStatusBarDarkIcons(
                hasTopSkinArtwork = true,
                skinColorMode = "dark",
                topSkinTintIsLight = false,
                defaultBackgroundIsLight = false,
                sampledTopArtworkLuminance = 0.84f,
            )
        )
        assertTrue(
            resolveHomeStatusBarDarkIcons(
                hasTopSkinArtwork = true,
                skinColorMode = null,
                topSkinTintIsLight = false,
                defaultBackgroundIsLight = false,
                skinForegroundLuminance = 0.02f,
            )
        )
        // 用户手动覆盖策略高于自动像素采样
        assertFalse(
            resolveHomeStatusBarDarkIcons(
                hasTopSkinArtwork = true,
                skinColorMode = "light",
                topSkinTintIsLight = true,
                defaultBackgroundIsLight = true,
                sampledTopArtworkLuminance = 0.90f,
                tonePolicy = com.android.purebilibili.core.plugin.skin.UiSkinTonePolicy.FORCE_DARK_BG,
            )
        )
        assertTrue(
            resolveHomeStatusBarDarkIcons(
                hasTopSkinArtwork = true,
                skinColorMode = "dark",
                topSkinTintIsLight = false,
                defaultBackgroundIsLight = false,
                sampledTopArtworkLuminance = 0.12f,
                tonePolicy = com.android.purebilibili.core.plugin.skin.UiSkinTonePolicy.FORCE_LIGHT_BG,
            )
        )
    }
}
