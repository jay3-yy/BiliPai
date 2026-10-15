package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppListItemStyle
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.AppFontSizePreset
import com.android.purebilibili.core.theme.AppUiScalePreset
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.feature.screenshot.AppScreenshotCaptureMode
import com.android.purebilibili.feature.screenshot.AppScreenshotGestureMode
import com.android.purebilibili.feature.settings.AppLanguage
import com.android.purebilibili.feature.settings.AppThemeMode
import com.android.purebilibili.feature.settings.DarkThemeStyle
import com.android.purebilibili.feature.settings.Md3ColorSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppThemeSettingsMappingPolicyTest {

    @Test
    fun retoneSourceSurvivesPreferenceMappingAndPreservesManualColor() {
        val result = mapAppThemeSettingsFromPreferences(mutablePreferencesOf(
            stringPreferencesKey("md3_color_source") to Md3ColorSource.WALLPAPER_RETONED.name,
            booleanPreferencesKey("dynamic_color") to false,
            stringPreferencesKey("md3_custom_color_hex") to "#FF5722",
        ))
        assertEquals(Md3ColorSource.WALLPAPER_RETONED, result.md3ColorSource)
        assertEquals("#FF5722", result.md3CustomColorHex)
    }

    @Test
    fun scheduledThemeSettingsPersistWithMinutePrecision() {
        val settings = mapAppThemeSettingsFromPreferences(mutablePreferencesOf(
            booleanPreferencesKey("dark_theme_schedule_enabled") to true,
            intPreferencesKey("dark_theme_schedule_start_minute") to 22 * 60 + 15,
            intPreferencesKey("dark_theme_schedule_end_minute") to 6 * 60 + 45,
        ))
        assertEquals(com.android.purebilibili.feature.settings.DarkThemeSchedule(true, 1335, 405), settings.darkThemeSchedule)
    }

    @Test
    fun emptyPreferences_useStartupThemeDefaults() {
        val result = mapAppThemeSettingsFromPreferences(mutablePreferencesOf())

        assertEquals(AppUiStyle.MATERIAL3, result.uiStyle)
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, result.themeMode)
        assertEquals(DarkThemeStyle.DEFAULT, result.darkThemeStyle)
        assertEquals(AppLanguage.FOLLOW_SYSTEM, result.appLanguage)
        assertEquals(Md3ColorSource.FOLLOW_WALLPAPER, result.md3ColorSource)
        assertEquals("#007AFF", result.md3CustomColorHex)
        assertFalse(result.themeRoleOverrides.enabled)
        assertEquals("#FFFDF8", result.themeRoleOverrides.light.backgroundHex)
        assertEquals("#121212", result.themeRoleOverrides.dark.backgroundHex)
        assertEquals(0, result.themeColorIndex)
        assertEquals(AppFontSizePreset.DEFAULT, result.appFontSizePreset)
        assertEquals("", result.appFontFileName)
        assertEquals(AppUiScalePreset.STANDARD, result.appUiScalePreset)
        assertEquals(0, result.appDpiOverridePercent)
        assertFalse(result.appGestureScreenshotEnabled)
        assertEquals(
            AppScreenshotGestureMode.TOP_RIGHT_TWO_FINGER_LONG_PRESS,
            result.appScreenshotGestureMode
        )
        assertEquals(AppScreenshotCaptureMode.FULL_WINDOW, result.appScreenshotCaptureMode)
        assertEquals(AppIconStyle.AUTO, result.appIconStyle)
        assertEquals(AppListItemStyle.AUTO, result.appListItemStyle)
    }

    @Test
    fun preferences_mapAllStartupThemeFieldsFromOneSnapshot() {
        val result = mapAppThemeSettingsFromPreferences(
            mutablePreferencesOf(
                intPreferencesKey("ui_preset") to UiPreset.IOS.value,
                intPreferencesKey("android_native_variant_v1") to AndroidNativeVariant.MIUIX.value,
                intPreferencesKey("theme_mode_v2") to AppThemeMode.DARK.value,
                intPreferencesKey("dark_theme_style_v1") to DarkThemeStyle.AMOLED.value,
                intPreferencesKey("app_language_v1") to AppLanguage.ENGLISH.value,
                stringPreferencesKey("md3_color_source") to Md3ColorSource.CUSTOM.name,
                stringPreferencesKey("md3_custom_color_hex") to "#ff00ff",
                booleanPreferencesKey("theme_role_overrides_enabled") to true,
                stringPreferencesKey("theme_light_background") to "#f0f1f2",
                stringPreferencesKey("theme_light_primary_text") to "#111111",
                stringPreferencesKey("theme_dark_control_accent") to "#abcdef",
                intPreferencesKey("theme_color_index") to 4,
                intPreferencesKey("app_font_size_preset") to AppFontSizePreset.LARGE.value,
                stringPreferencesKey("app_font_file_name") to "demo.ttf",
                intPreferencesKey("app_ui_scale_preset") to AppUiScalePreset.LARGE.value,
                intPreferencesKey("app_dpi_override_percent") to 200,
                booleanPreferencesKey("app_gesture_screenshot_enabled") to true,
                intPreferencesKey("app_screenshot_gesture_mode") to
                    AppScreenshotGestureMode.THREE_FINGER_SWIPE_DOWN.value,
                intPreferencesKey("app_screenshot_capture_mode") to
                    AppScreenshotCaptureMode.SELECT_REGION.value,
                stringPreferencesKey("app_icon_style") to AppIconStyle.THEME_CONTAINER.name,
                stringPreferencesKey("app_list_item_style") to AppListItemStyle.NATIVE.name
            )
        )

        // 历史 iOS 值单向迁移为默认主题 MIUIX（运行时不再产生 iOS）。
        assertEquals(AppUiStyle.MIUIX, result.uiStyle)
        assertEquals(AppThemeMode.DARK, result.themeMode)
        assertEquals(DarkThemeStyle.AMOLED, result.darkThemeStyle)
        assertEquals(AppLanguage.ENGLISH, result.appLanguage)
        assertEquals(Md3ColorSource.CUSTOM, result.md3ColorSource)
        assertEquals("#FF00FF", result.md3CustomColorHex)
        assertEquals(true, result.themeRoleOverrides.enabled)
        assertEquals("#F0F1F2", result.themeRoleOverrides.light.backgroundHex)
        assertEquals("#111111", result.themeRoleOverrides.light.primaryTextHex)
        assertEquals("#ABCDEF", result.themeRoleOverrides.dark.controlAccentHex)
        assertEquals(4, result.themeColorIndex)
        assertEquals(AppFontSizePreset.LARGE, result.appFontSizePreset)
        assertEquals("demo.ttf", result.appFontFileName)
        assertEquals(AppUiScalePreset.LARGE, result.appUiScalePreset)
        assertEquals(115, result.appDpiOverridePercent)
        assertEquals(AppIconStyle.THEME_CONTAINER, result.appIconStyle)
        assertEquals(AppListItemStyle.NATIVE, result.appListItemStyle)
        assertEquals(true, result.appGestureScreenshotEnabled)
        assertEquals(AppScreenshotGestureMode.THREE_FINGER_SWIPE_DOWN, result.appScreenshotGestureMode)
        assertEquals(AppScreenshotCaptureMode.SELECT_REGION, result.appScreenshotCaptureMode)
    }
}
