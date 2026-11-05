package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.feature.video.player.PlayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioModePlayModePolicyTest {

    @Test
    fun resolveAudioPlayModeLabel_mapsAllModes() {
        assertEquals("顺序播放", resolveAudioPlayModeLabel(PlayMode.SEQUENTIAL))
        assertEquals("随机播放", resolveAudioPlayModeLabel(PlayMode.SHUFFLE))
        assertEquals("单曲循环", resolveAudioPlayModeLabel(PlayMode.REPEAT_ONE))
        assertEquals("列表循环", resolveAudioPlayModeLabel(PlayMode.REPEAT_ALL))
    }

    @Test
    fun shouldUseAudioModeLiquidPlayModeControl_keepsStandardButtonsWithoutLiquidCapability() {
        assertFalse(
            shouldUseAudioModeLiquidPlayModeControl(
                supportsIndependentLiquidGlass = false,
                androidNativeLiquidGlassEnabled = false
            )
        )
    }

    @Test
    fun shouldUseAudioModeLiquidPlayModeControl_respectsSceneRegardlessOfCapability() {
        assertFalse(
            shouldUseAudioModeLiquidPlayModeControl(
                supportsIndependentLiquidGlass = true,
                androidNativeLiquidGlassEnabled = false
            )
        )
        assertTrue(
            shouldUseAudioModeLiquidPlayModeControl(
                supportsIndependentLiquidGlass = false,
                androidNativeLiquidGlassEnabled = true
            )
        )
    }
}
