package com.android.purebilibili.core.store

import com.android.purebilibili.core.ui.AppLiquidGlassScene
import com.android.purebilibili.core.ui.AppLiquidGlassSettings
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassScenePolicyTest {
    @Test fun dockOnlyKeepsOtherSurfacesNativeAndMasterRetainsChoice() {
        var scenes = AppLiquidGlassSettings()
        AppLiquidGlassScene.entries.filter { it != AppLiquidGlassScene.BOTTOM_DOCK }.forEach {
            scenes = scenes.withEnabled(it, false)
        }
        val enabled = HomeSettings(androidNativeLiquidGlassEnabled = true, liquidGlassScenes = scenes)
        assertTrue(enabled.bottomDockGlassEnabled)
        assertFalse(enabled.homeTopGlassEnabled)
        assertFalse(enabled.homeSearchGlassEnabled)
        assertFalse(enabled.commentBarGlassEnabled)
        assertFalse(enabled.otherGlassEnabled)
        val disabled = enabled.copy(androidNativeLiquidGlassEnabled = false)
        assertTrue(AppLiquidGlassScene.entries.none(disabled::isGlassSceneEnabled))
        assertEquals(enabled, disabled.copy(androidNativeLiquidGlassEnabled = true))
    }

    @Test fun existingUsersRetainAllSurfacesAndSearchIsIndependentOfTopBar() {
        val existing = HomeSettings(androidNativeLiquidGlassEnabled = true)
        assertTrue(AppLiquidGlassScene.entries.all(existing::isGlassSceneEnabled))
        val searchOnly = existing.copy(liquidGlassScenes = existing.liquidGlassScenes
            .withEnabled(AppLiquidGlassScene.HOME_TOP_BAR, false))
        assertFalse(searchOnly.homeTopGlassEnabled)
        assertTrue(searchOnly.homeSearchGlassEnabled)
        assertTrue(searchOnly.commentBarGlassEnabled)
    }

    @Test fun legacyOtherComponentsOffIsInheritedUntilEachSplitSceneIsChanged() {
        val legacy = AppLiquidGlassSettings(disabledMask = AppLiquidGlassScene.OTHER_COMPONENTS.bit)
        assertFalse(legacy.isEnabled(AppLiquidGlassScene.PLAYER_CONTROLS))
        assertFalse(legacy.isEnabled(AppLiquidGlassScene.MENUS_DIALOGS))
        assertFalse(legacy.isEnabled(AppLiquidGlassScene.NOW_PLAYING))
        val updated = legacy.withEnabled(AppLiquidGlassScene.PLAYER_CONTROLS, true)
        val restored = AppLiquidGlassSettings(updated.disabledMask)
        assertTrue(restored.isEnabled(AppLiquidGlassScene.PLAYER_CONTROLS))
        assertFalse(restored.isEnabled(AppLiquidGlassScene.MENUS_DIALOGS))
        assertFalse(restored.isEnabled(AppLiquidGlassScene.OTHER_COMPONENTS))
    }

    @Test fun playerMenusAndNowPlayingCanBeSelectedIndependently() {
        val preferences = AppLiquidGlassSettings()
            .withEnabled(AppLiquidGlassScene.OTHER_COMPONENTS, false)
            .withEnabled(AppLiquidGlassScene.PLAYER_CONTROLS, false)
            .withEnabled(AppLiquidGlassScene.NOW_PLAYING, false)
        val home = HomeSettings(androidNativeLiquidGlassEnabled = true, liquidGlassScenes = preferences)
        assertTrue(home.popupGlassEnabled)
        assertFalse(home.playerGlassEnabled)
        assertFalse(home.nowPlayingGlassEnabled)
        assertFalse(home.otherGlassEnabled)
        assertFalse(home.copy(androidNativeLiquidGlassEnabled = false).popupGlassEnabled)
    }
}
