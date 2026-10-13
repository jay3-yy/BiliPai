package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DanmakuSettingsMappingPolicyTest {

    private val portraitScope = DanmakuSettingsScope.PORTRAIT
    private val landscapeScope = DanmakuSettingsScope.LANDSCAPE

    @Test
    fun arbitraryDisplayAreaValues_snapToNearestSupportedOption() {
        assertEquals(0.125f, normalizeDanmakuDisplayArea(0.125f))
        assertEquals(0.375f, normalizeDanmakuDisplayArea(0.33f))
        assertEquals(0.625f, normalizeDanmakuDisplayArea(0.6f))
        assertEquals(0.625f, normalizeDanmakuDisplayArea(0.63f))
        assertEquals(0.875f, normalizeDanmakuDisplayArea(0.875f))
        assertEquals(1.0f, normalizeDanmakuDisplayArea(0.99f))
    }

    @Test
    fun populatedPreferences_normalizeFullscreenPanelWidthToThird() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("danmaku_enabled") to false,
            floatPreferencesKey("danmaku_opacity") to 0.3f,
            floatPreferencesKey("danmaku_font_scale") to 1.3f,
            floatPreferencesKey("danmaku_speed") to 1.6f,
            floatPreferencesKey("danmaku_area") to 0.75f,
            intPreferencesKey("danmaku_font_weight") to 8,
            floatPreferencesKey("danmaku_stroke_width") to 4.2f,
            floatPreferencesKey("danmaku_line_height") to 2.1f,
            floatPreferencesKey("danmaku_scroll_duration_seconds") to 12.5f,
            floatPreferencesKey("danmaku_static_duration_seconds") to 9.5f,
            booleanPreferencesKey("danmaku_scroll_fixed_velocity") to true,
            booleanPreferencesKey("danmaku_static_to_scroll") to true,
            booleanPreferencesKey("danmaku_massive_mode") to true,
            booleanPreferencesKey("danmaku_merge_duplicates") to false,
            intPreferencesKey("danmaku_duplicate_merge_window_ms") to 1200,
            intPreferencesKey("danmaku_duplicate_merge_count_threshold") to 4,
            booleanPreferencesKey("danmaku_allow_scroll") to false,
            booleanPreferencesKey("danmaku_allow_top") to false,
            booleanPreferencesKey("danmaku_allow_bottom") to false,
            booleanPreferencesKey("danmaku_allow_colorful") to false,
            booleanPreferencesKey("danmaku_allow_special") to false,
            booleanPreferencesKey("danmaku_block_attention_commands") to true,
            booleanPreferencesKey("danmaku_smart_occlusion") to true,
            intPreferencesKey("danmaku_fullscreen_panel_width_mode") to DanmakuPanelWidthMode.HALF.value,
            stringPreferencesKey("danmaku_block_rules") to "剧透\n广告\n  \n测试"
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertFalse(result.enabled)
        assertEquals(0.3f, result.opacity)
        assertEquals(1.3f, result.fontScale)
        assertEquals(1.6f, result.speed)
        assertEquals(0.75f, result.displayArea)
        assertFalse(result.mergeDuplicates)
        assertFalse(result.allowScroll)
        assertFalse(result.allowTop)
        assertFalse(result.allowBottom)
        assertFalse(result.allowColorful)
        assertFalse(result.allowSpecial)
        assertTrue(result.hideInteractiveCommands)
        assertTrue(result.blockAttentionCommands)
        assertTrue(result.smartOcclusion)
        assertEquals(DanmakuPanelWidthMode.THIRD, result.fullscreenPanelWidthMode)
        assertEquals("剧透\n广告\n  \n测试", result.blockRulesRaw)
        assertEquals(listOf("剧透", "广告", "测试"), result.blockRules)
        assertEquals(8, propertyValue<Int>(result, "fontWeight"))
        assertEquals(4.2f, propertyValue<Float>(result, "strokeWidth"))
        assertEquals(2.1f, propertyValue<Float>(result, "lineHeight"))
        assertEquals(12.5f, propertyValue<Float>(result, "scrollDurationSeconds"))
        assertEquals(9.5f, propertyValue<Float>(result, "staticDurationSeconds"))
        assertEquals(1200, propertyValue<Int>(result, "duplicateMergeWindowMs"))
        assertEquals(4, propertyValue<Int>(result, "duplicateMergeCountThreshold"))
        assertTrue(propertyValue<Boolean>(result, "scrollFixedVelocity"))
        assertTrue(propertyValue<Boolean>(result, "staticDanmakuToScroll"))
        assertTrue(propertyValue<Boolean>(result, "massiveMode"))
    }

    @Test
    fun persistedFontScale_supportsThirtyPercentMinimum() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("danmaku_font_scale") to 0.2f
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertEquals(0.3f, result.fontScale)
    }

    @Test
    fun persistedMergeWindow_supportsLongWindowsAndCapsTheUpperBoundary() {
        val longWindow = mapDanmakuSettingsFromPreferences(
            mutablePreferencesOf(intPreferencesKey("danmaku_duplicate_merge_window_ms") to 9_000),
            portraitScope
        )
        val oversizedWindow = mapDanmakuSettingsFromPreferences(
            mutablePreferencesOf(intPreferencesKey("danmaku_duplicate_merge_window_ms") to 20_000),
            portraitScope
        )

        assertEquals(9_000, longWindow.duplicateMergeWindowMs)
        assertEquals(15_000, oversizedWindow.duplicateMergeWindowMs)
    }

    @Test
    fun legacySubsecondMergeWindow_isRaisedToOneSecond() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("danmaku_duplicate_merge_window_ms") to 100
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertEquals(1_000, result.duplicateMergeWindowMs)
    }

    @Test
    fun persistedDisplayArea_isNormalizedBackToDiscreteOption() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("danmaku_area") to 0.33f
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertEquals(0.25f, result.displayArea)
    }

    @Test
    fun invalidFullscreenPanelWidthMode_fallsBackToThirdWidth() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("danmaku_fullscreen_panel_width_mode") to 99
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertEquals(DanmakuPanelWidthMode.THIRD, result.fullscreenPanelWidthMode)
    }

    @Test
    fun portraitDisplayAreaMode_readsScopedPreferenceAndFallsBackToVideoViewport() {
        val screenTopPrefs = mutablePreferencesOf(
            intPreferencesKey("danmaku_portrait_display_area_mode") to
                PortraitDanmakuDisplayAreaMode.SCREEN_TOP.value
        )
        val invalidPrefs = mutablePreferencesOf(
            intPreferencesKey("danmaku_portrait_display_area_mode") to 99
        )

        assertEquals(
            PortraitDanmakuDisplayAreaMode.SCREEN_TOP,
            mapDanmakuSettingsFromPreferences(screenTopPrefs, portraitScope).portraitDisplayAreaMode
        )
        assertEquals(
            PortraitDanmakuDisplayAreaMode.VIDEO_VIEWPORT,
            mapDanmakuSettingsFromPreferences(invalidPrefs, portraitScope).portraitDisplayAreaMode
        )
        assertEquals(
            PortraitDanmakuDisplayAreaMode.VIDEO_VIEWPORT,
            mapDanmakuSettingsFromPreferences(screenTopPrefs, landscapeScope).portraitDisplayAreaMode
        )
    }

    @Test
    fun portraitScope_fallsBackToLegacyPreferences_whenScopedKeysAreMissing() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("danmaku_enabled") to false,
            floatPreferencesKey("danmaku_opacity") to 0.42f,
            floatPreferencesKey("danmaku_font_scale") to 1.4f,
            floatPreferencesKey("danmaku_speed") to 1.6f,
            floatPreferencesKey("danmaku_area") to 0.75f,
            booleanPreferencesKey("danmaku_merge_duplicates") to false,
            booleanPreferencesKey("danmaku_allow_scroll") to false,
            booleanPreferencesKey("danmaku_allow_top") to false,
            booleanPreferencesKey("danmaku_allow_bottom") to false,
            booleanPreferencesKey("danmaku_allow_colorful") to false,
            booleanPreferencesKey("danmaku_allow_special") to false,
            booleanPreferencesKey("danmaku_smart_occlusion") to true,
            stringPreferencesKey("danmaku_block_rules") to "剧透\n测试"
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertFalse(result.enabled)
        assertEquals(0.42f, result.opacity)
        assertEquals(1.4f, result.fontScale)
        assertEquals(1.6f, result.speed)
        assertEquals(0.75f, result.displayArea)
        assertFalse(result.mergeDuplicates)
        assertFalse(result.allowScroll)
        assertFalse(result.allowTop)
        assertFalse(result.allowBottom)
        assertFalse(result.allowColorful)
        assertFalse(result.allowSpecial)
        assertTrue(result.smartOcclusion)
        assertEquals(listOf("剧透", "测试"), result.blockRules)
    }

    @Test
    fun landscapeScope_fallsBackToLegacyPreferences_whenScopedKeysAreMissing() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("danmaku_opacity") to 0.61f,
            floatPreferencesKey("danmaku_font_scale") to 1.8f,
            floatPreferencesKey("danmaku_speed") to 0.8f,
            floatPreferencesKey("danmaku_area") to 1.0f
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, landscapeScope)

        assertEquals(0.61f, result.opacity)
        assertEquals(1.8f, result.fontScale)
        assertEquals(0.8f, result.speed)
        assertEquals(1.0f, result.displayArea)
    }

    @Test
    fun sharedPreferences_useLandscapeValueForBothScopes() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("danmaku_portrait_enabled") to true,
            booleanPreferencesKey("danmaku_landscape_enabled") to false,
            floatPreferencesKey("danmaku_portrait_font_scale") to 0.9f,
            floatPreferencesKey("danmaku_landscape_font_scale") to 1.6f,
            floatPreferencesKey("danmaku_portrait_line_height") to 1.6f,
            floatPreferencesKey("danmaku_landscape_line_height") to 1.1f,
            floatPreferencesKey("danmaku_portrait_area") to 0.25f,
            floatPreferencesKey("danmaku_landscape_area") to 0.75f,
            floatPreferencesKey("danmaku_opacity") to 0.4f,
            floatPreferencesKey("danmaku_portrait_opacity") to 0.55f,
            floatPreferencesKey("danmaku_landscape_opacity") to 0.72f,
            floatPreferencesKey("danmaku_portrait_stroke_width") to 1.2f,
            floatPreferencesKey("danmaku_landscape_stroke_width") to 3.8f,
            stringPreferencesKey("danmaku_portrait_block_rules") to "竖屏",
            stringPreferencesKey("danmaku_landscape_block_rules") to "横屏"
        )

        val portrait = mapDanmakuSettingsFromPreferences(prefs, portraitScope)
        val landscape = mapDanmakuSettingsFromPreferences(prefs, landscapeScope)

        assertFalse(portrait.enabled)
        assertFalse(landscape.enabled)
        assertEquals(1.6f, portrait.fontScale)
        assertEquals(1.6f, landscape.fontScale)
        assertEquals(1.1f, portrait.lineHeight)
        assertEquals(1.1f, landscape.lineHeight)
        assertEquals(0.75f, portrait.displayArea)
        assertEquals(0.75f, landscape.displayArea)
        assertEquals(0.55f, portrait.opacity)
        assertEquals(0.72f, landscape.opacity)
        assertEquals(1.2f, propertyValue<Float>(portrait, "strokeWidth"))
        assertEquals(3.8f, propertyValue<Float>(landscape, "strokeWidth"))
        assertEquals(listOf("竖屏"), portrait.blockRules)
        assertEquals(listOf("横屏"), landscape.blockRules)
    }

    @Test
    fun sharedPreferences_fallBackToLegacyPortraitValues_whenLandscapeValueMissing() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("danmaku_portrait_enabled") to false,
            floatPreferencesKey("danmaku_portrait_font_scale") to 1.4f,
            floatPreferencesKey("danmaku_portrait_line_height") to 1.3f,
            floatPreferencesKey("danmaku_portrait_area") to 0.75f
        )

        val portrait = mapDanmakuSettingsFromPreferences(prefs, portraitScope)
        val landscape = mapDanmakuSettingsFromPreferences(prefs, landscapeScope)

        assertFalse(portrait.enabled)
        assertFalse(landscape.enabled)
        assertEquals(1.4f, portrait.fontScale)
        assertEquals(1.4f, landscape.fontScale)
        assertEquals(1.3f, portrait.lineHeight)
        assertEquals(1.3f, landscape.lineHeight)
        assertEquals(0.75f, portrait.displayArea)
        assertEquals(0.75f, landscape.displayArea)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> propertyValue(instance: Any, name: String): T {
        val upperName = name.replaceFirstChar { it.uppercase() }
        val getter = instance.javaClass.methods.firstOrNull {
            it.name == "get$upperName" || it.name == "is$upperName"
        }
        assertNotNull(getter, "Expected property `$name` to exist on ${instance.javaClass.simpleName}")
        return getter.invoke(instance) as T
    }
}
