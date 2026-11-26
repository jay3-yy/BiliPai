package com.android.purebilibili.feature.settings

import android.os.Build
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsDiscoveryPolicyTest {

    @Test
    fun `filterAppearanceRecipesForApi hides liquid glass below Android 13`() {
        val belowApi33 = filterAppearanceRecipesForApi(sdkInt = Build.VERSION_CODES.S_V2)
        assertFalse(belowApi33.contains(AppearanceSceneRecipe.LIQUID_GLASS))
        assertTrue(belowApi33.contains(AppearanceSceneRecipe.NATIVE_MD3))

        val api33Plus = filterAppearanceRecipesForApi(sdkInt = Build.VERSION_CODES.TIRAMISU)
        assertTrue(api33Plus.contains(AppearanceSceneRecipe.LIQUID_GLASS))
    }

    @Test
    fun `resolveNextHiddenGemIndex wraps cleanly around catalog`() {
        assertEquals(1, resolveNextHiddenGemIndex(0, 6))
        assertEquals(0, resolveNextHiddenGemIndex(5, 6))
        assertEquals(0, resolveNextHiddenGemIndex(0, 0))
    }

    @Test
    fun `colloquial intent aliases match appearance and playback settings`() {
        assertTrue(
            resolveSettingsSearchResults("透明底栏").any { it.target == SettingsSearchTarget.INTERFACE_THEME },
            "Colloquial alias 透明底栏 should match interface/theme settings",
        )
        assertTrue(
            resolveSettingsSearchResults("声音忽大忽小").any { it.target == SettingsSearchTarget.PLAYBACK_QUALITY },
            "Colloquial alias 声音忽大忽小 should match playback settings",
        )
        assertTrue(
            resolveSettingsSearchResults("长按锁倍速").any { it.target == SettingsSearchTarget.FULLSCREEN_GESTURE },
            "Colloquial alias 长按锁倍速 should match fullscreen gesture settings",
        )
    }

    @Test
    fun `dynamic category highlight subtitles reflect active customization cleanly`() {
        assertEquals(
            "Miuix · 液态玻璃 · 动态取色 · 纯黑护眼",
            resolveAppearanceCategoryHighlightSubtitle(
                uiStyleLabel = "Miuix",
                liquidGlassEnabled = true,
                dynamicColorEnabled = true,
                amoledDarkEnabled = true,
            ),
        )
        assertEquals(
            "渐弱 20s · 响度均衡 · 自动连播",
            resolvePlaybackCategoryHighlightSubtitle(
                sleepFadeEnabled = true,
                sleepFadeDurationSec = 20,
                loudnessEnabled = true,
                autoPlayEnabled = true,
                fallbackSubtitle = "默认副标题",
            ),
        )
        assertEquals(
            "默认副标题",
            resolvePlaybackCategoryHighlightSubtitle(
                sleepFadeEnabled = false,
                sleepFadeDurationSec = 12,
                loudnessEnabled = false,
                autoPlayEnabled = false,
                fallbackSubtitle = "默认副标题",
            ),
        )
        assertEquals(
            "触感已开（9/11 场景）· 卡片动效",
            resolveAnimationCategoryHighlightSubtitle(
                hapticEnabled = true,
                enabledSceneCount = 9,
                totalSceneCount = 11,
                fallbackSubtitle = "默认",
            ),
        )
    }

    @Test
    fun `collectCustomizedAppearanceAndPlaybackSettings only returns non-default items`() {
        assertTrue(
            collectCustomizedAppearanceSettings(
                uiStyleLabel = "Material 3",
                isDefaultUiStyle = true,
                themeModeLabel = "跟随系统",
                isFollowSystemTheme = true,
                amoledDarkEnabled = false,
                liquidGlassEnabled = true,
                dynamicColorEnabled = true,
                focusViewingHideStatsEnabled = false,
            ).isEmpty()
        )
        val appearanceDiff = collectCustomizedAppearanceSettings(
            uiStyleLabel = "Miuix",
            isDefaultUiStyle = false,
            themeModeLabel = "深色模式",
            isFollowSystemTheme = false,
            amoledDarkEnabled = true,
            liquidGlassEnabled = false,
            dynamicColorEnabled = true,
            focusViewingHideStatsEnabled = true,
        )
        assertEquals(5, appearanceDiff.size)

        val playbackDiff = collectCustomizedPlaybackSettings(
            autoPlayEnabled = true,
            bgPlayEnabled = false,
            loudnessEnabled = true,
            sleepFadeEnabled = true,
            sleepFadeDurationSec = 25,
            longPressSpeedLockEnabled = false,
            twoFingerVerticalSpeedEnabled = true,
        )
        assertEquals(4, playbackDiff.size)
        assertTrue(playbackDiff.any { it.title == "定时渐弱淡出时长" && it.currentValue == "25 秒" })
    }
}
