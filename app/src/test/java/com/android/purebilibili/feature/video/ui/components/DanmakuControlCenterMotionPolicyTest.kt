package com.android.purebilibili.feature.video.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DanmakuControlCenterMotionPolicyTest {
    @Test
    fun fullscreenGlassEndPanelUsesGroupMotion() {
        assertTrue(useDanmakuControlCenterMotion(true, DanmakuSettingsPanelAnchor.End, true, true, false))
    }

    @Test
    fun portraitAndCenteredPresentationsKeepNativeInteraction() {
        assertFalse(useDanmakuControlCenterMotion(false, DanmakuSettingsPanelAnchor.End, true, true, false))
        assertFalse(useDanmakuControlCenterMotion(true, DanmakuSettingsPanelAnchor.Center, true, true, false))
        assertFalse(useDanmakuControlCenterMotion(true, DanmakuSettingsPanelAnchor.Bottom, true, true, false))
    }

    @Test
    fun glassAndMotionPreferencesAreIndependentGates() {
        assertFalse(useDanmakuControlCenterMotion(true, DanmakuSettingsPanelAnchor.End, false, true, false))
        assertFalse(useDanmakuControlCenterMotion(true, DanmakuSettingsPanelAnchor.End, true, false, false))
        assertFalse(useDanmakuControlCenterMotion(true, DanmakuSettingsPanelAnchor.End, true, true, true))
    }
}
