package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppThemeConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DanmakuControlCenterMotionPolicyTest {
    @Test
    fun playerMenusFollowGlassWithoutEnablingCenteredMiuixPopups() {
        for (glass in listOf(false, true)) {
            val original = AppThemeConfig(liquidGlassEnabled = glass, nativeMiuixPopupsEnabled = true)
            val menu = playerNativeMenuConfig(original)
            assertEquals(glass, menu.liquidGlassEnabled)
            assertFalse(menu.nativeMiuixPopupsEnabled)
            assertEquals(glass, original.liquidGlassEnabled)
            assertTrue(original.nativeMiuixPopupsEnabled)
        }
    }

    @Test
    fun menuIsolationPreservesUnrelatedApplicationPreferences() {
        val original = AppThemeConfig(
            liquidGlassEnabled = true,
            hapticFeedbackEnabled = false,
            uiEntranceAnimationEnabled = false,
            headerBlurEnabled = false,
            globalTextTapCopyEnabled = true,
        )
        val menu = playerNativeMenuConfig(original)
        assertEquals(original, menu.copy(liquidGlassEnabled = true, nativeMiuixPopupsEnabled = original.nativeMiuixPopupsEnabled))
    }

    @Test
    fun menuMaterialIsIndependentOfPlayerAndPageMaterial() {
        val menusOnly = AppThemeConfig(
            liquidGlassEnabled = false,
            playerGlassEnabled = false,
            popupGlassEnabled = true,
        )
        assertTrue(playerNativeMenuConfig(menusOnly).liquidGlassEnabled)
        val playerOnly = AppThemeConfig(
            liquidGlassEnabled = true,
            playerGlassEnabled = true,
            popupGlassEnabled = false,
        )
        assertFalse(playerNativeMenuConfig(playerOnly).liquidGlassEnabled)
        assertTrue(playerOnly.playerGlassEnabled)
    }

}
