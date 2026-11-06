package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_CUSTOM_DEFAULT_MILLIS
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_CUSTOM_MAX_MILLIS
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionSpeed
import com.android.purebilibili.core.theme.UiPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeSettingsMappingPolicyTest {

    @Test
    fun emptyPreferences_useExpectedRuntimeDefaults() {
        val prefs = mutablePreferencesOf()

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(0, result.displayMode)
        assertFalse(result.isBottomBarFloating)
        assertEquals(0, result.bottomBarLabelMode)
        assertEquals(SettingsManager.TopTabLabelMode.TEXT_ONLY, result.topTabLabelMode)
        assertFalse(result.partitionSideRailOnRight)
        assertFalse(result.hideTopTabs)
        assertEquals(HomeTopRightAction.SETTINGS, result.homeTopRightAction)
        assertEquals(HomeTopLayoutOrder.SEARCH_THEN_TABS, result.homeTopLayoutOrder)
        assertFalse(result.isHeaderBlurEnabled)
        assertEquals(HomeHeaderBlurMode.ALWAYS_OFF, result.headerBlurMode)
        assertEquals(HomeHeaderCollapseMode.BOTH, result.homeHeaderCollapseMode)
        assertEquals(HomeBarHideType.SYNC, result.homeBarHideType)
        assertEquals(
            CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY,
            result.commonListHeaderCollapseMode
        )
        assertTrue(result.isHeaderCollapseEnabled)
        assertTrue(result.showPgcTimeline)
        assertFalse(result.isBottomBarBlurEnabled)
        assertFalse(result.isTopBarLiquidGlassEnabled)
        assertFalse(result.isHomeSearchLiquidGlassEnabled)
        assertFalse(result.isBottomBarLiquidGlassEnabled)
        assertFalse(result.isBottomBarSearchEnabled)
        assertFalse(result.listScopedSearchEnabled)
        assertEquals(BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP, result.bottomBarSearchAutoExpandMode)
        assertEquals(BottomBarSearchLayoutMode.FULL_DOCK, result.bottomBarSearchLayoutMode)
        assertFalse(result.androidNativeLiquidGlassEnabled)
        assertFalse(result.isLiquidGlassEnabled)
        assertEquals(LiquidGlassStyle.SUKISU, result.liquidGlassStyle)
        assertEquals(LiquidGlassMode.BALANCED, result.liquidGlassMode)
        assertEquals(0.52f, result.liquidGlassStrength)
        assertEquals(0.5f, result.liquidGlassProgress)
        assertEquals(LiquidGlassReadabilityMode.STABLE, result.liquidGlassReadabilityMode)
        assertEquals(LiquidGlassAdvancedPreset.BALANCED, result.liquidGlassAdvancedSettings.preset)
        assertEquals(0.62f, result.liquidGlassAdvancedSettings.contentReadability)
        assertEquals(0.56f, result.liquidGlassAdvancedSettings.chromaticAberration)
        assertEquals(0.45f, result.liquidGlassAdvancedSettings.contentDistortion)
        assertEquals(0, result.gridColumnCount)
        assertTrue(result.pinchToChangeGridColumnsEnabled)
        assertEquals(HomeFeedCardWidthPreset.AUTO, result.homeFeedCardWidthPreset)
        assertFalse(result.cardAnimationEnabled)
        assertFalse(result.cardTransitionEnabled)
        assertEquals(VideoSharedTransitionSpeed.STANDARD, result.videoSharedTransitionSpeed)
        assertEquals(
            VIDEO_SHARED_TRANSITION_CUSTOM_DEFAULT_MILLIS,
            result.videoSharedTransitionCustomDurationMillis
        )
        assertFalse(result.smartVisualGuardEnabled)
        assertFalse(result.compactVideoStatsOnCover)
        assertEquals(HomeFeedCardStyle.BILIPAI, result.homeFeedCardStyle)
        assertEquals(HomeDurationStyle.OUTSIDE_COVER, result.homeDurationStyle)
        assertEquals(HomeWallpaperEffectMode.OFF, result.homeWallpaperEffectMode)
        assertEquals(HomeWallpaperEffectScope.HOME_ONLY, result.homeWallpaperEffectScope)
        assertFalse(result.lowQualityHomeCoverInDataSaver)
        assertFalse(result.showHomeUpBadges)
        assertFalse(result.showHomeUpAvatars)
        assertTrue(result.showHomePublishTime)
        assertFalse(result.easterEggEnabled)
        assertFalse(result.crashTrackingConsentShown)
    }

    @Test
    fun savedVisualEffectsRemainOptIn() {
        val result = mapHomeSettingsFromPreferences(
            mutablePreferencesOf(
                booleanPreferencesKey("bottom_bar_floating") to true,
                booleanPreferencesKey("header_blur_enabled") to true,
                booleanPreferencesKey("card_transition_enabled") to true,
                booleanPreferencesKey("android_native_liquid_glass_enabled") to true,
                booleanPreferencesKey("home_hero_carousel_enabled") to true,
            )
        )

        assertTrue(result.isBottomBarFloating)
        assertTrue(result.isHeaderBlurEnabled)
        assertTrue(result.cardTransitionEnabled)
        assertTrue(result.androidNativeLiquidGlassEnabled)
        assertTrue(result.homeHeroCarouselEnabled)
    }

    @Test
    fun populatedPreferences_mapToHomeSettingsCorrectly() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("display_mode") to 1,
            booleanPreferencesKey("bottom_bar_floating") to false,
            intPreferencesKey("bottom_bar_label_mode") to 2,
            intPreferencesKey("top_tab_label_mode") to 1,
            booleanPreferencesKey("partition_side_rail_on_right") to true,
            intPreferencesKey("home_top_right_action") to HomeTopRightAction.INBOX.value,
            intPreferencesKey("home_top_layout_order") to HomeTopLayoutOrder.TABS_THEN_SEARCH.value,
            booleanPreferencesKey("header_blur_enabled") to false,
            booleanPreferencesKey("header_collapse_enabled") to false,
            intPreferencesKey("home_header_collapse_mode") to HomeHeaderCollapseMode.TABS_ONLY.value,
            intPreferencesKey("home_bar_hide_type") to HomeBarHideType.INSTANT.value,
            intPreferencesKey("common_list_header_collapse_mode") to
                CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY.value,
            booleanPreferencesKey("bottom_bar_blur_enabled") to false,
            booleanPreferencesKey("top_bar_liquid_glass_enabled") to true,
            booleanPreferencesKey("home_search_liquid_glass_enabled") to false,
            booleanPreferencesKey("bottom_bar_liquid_glass_enabled") to false,
            booleanPreferencesKey("bottom_bar_search_enabled") to true,
            booleanPreferencesKey("list_scoped_search_enabled") to true,
            intPreferencesKey("bottom_bar_search_auto_expand_mode") to BottomBarSearchAutoExpandMode.DISABLED.value,
            intPreferencesKey("bottom_bar_search_layout_mode") to BottomBarSearchLayoutMode.HOME_AND_SEARCH.value,
            booleanPreferencesKey("android_native_liquid_glass_enabled") to true,
            intPreferencesKey("liquid_glass_style") to LiquidGlassStyle.IOS26.value,
            intPreferencesKey("liquid_glass_advanced_preset") to
                LiquidGlassAdvancedPreset.CUSTOM.value,
            intPreferencesKey("liquid_glass_readability_mode") to
                LiquidGlassReadabilityMode.ADAPTIVE.value,
            floatPreferencesKey("liquid_glass_content_readability") to 0.84f,
            floatPreferencesKey("liquid_glass_chromatic_aberration") to 0.36f,
            floatPreferencesKey("liquid_glass_content_distortion") to 0.71f,
            intPreferencesKey("grid_column_count") to 4,
            intPreferencesKey("home_feed_card_width_preset") to HomeFeedCardWidthPreset.WIDE.value,
            intPreferencesKey("home_feed_card_style") to HomeFeedCardStyle.OFFICIAL.value,
            booleanPreferencesKey("card_animation_enabled") to true,
            booleanPreferencesKey("card_transition_enabled") to false,
            intPreferencesKey("video_shared_transition_speed") to VideoSharedTransitionSpeed.CUSTOM.value,
            intPreferencesKey("video_shared_transition_custom_duration_millis") to 620,
            booleanPreferencesKey("smart_visual_guard_enabled") to false,
            booleanPreferencesKey("compact_video_stats_on_cover") to false,
            booleanPreferencesKey("home_video_duration_badges_visible") to false,
            intPreferencesKey("home_wallpaper_effect_mode") to HomeWallpaperEffectMode.STRONG_BLUR.value,
            intPreferencesKey("home_wallpaper_effect_scope") to HomeWallpaperEffectScope.GLOBAL.value,
            booleanPreferencesKey("low_quality_home_cover_in_data_saver") to true,
            booleanPreferencesKey("home_up_badges_visible") to false,
            booleanPreferencesKey("home_up_avatars_visible") to false,
            booleanPreferencesKey("home_publish_time_visible") to false,
            booleanPreferencesKey("easter_egg_enabled") to true,
            booleanPreferencesKey("crash_tracking_consent_shown") to true
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(1, result.displayMode)
        assertFalse(result.isBottomBarFloating)
        assertEquals(2, result.bottomBarLabelMode)
        assertEquals(1, result.topTabLabelMode)
        assertTrue(result.partitionSideRailOnRight)
        assertEquals(HomeTopRightAction.INBOX, result.homeTopRightAction)
        assertEquals(HomeTopLayoutOrder.TABS_THEN_SEARCH, result.homeTopLayoutOrder)
        assertFalse(result.isHeaderBlurEnabled)
        assertEquals(HomeHeaderBlurMode.ALWAYS_OFF, result.headerBlurMode)
        assertEquals(HomeHeaderCollapseMode.TABS_ONLY, result.homeHeaderCollapseMode)
        assertEquals(HomeBarHideType.INSTANT, result.homeBarHideType)
        assertEquals(
            CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY,
            result.commonListHeaderCollapseMode
        )
        assertTrue(result.isHeaderCollapseEnabled)
        assertFalse(result.isBottomBarBlurEnabled)
        assertTrue(result.isTopBarLiquidGlassEnabled)
        assertFalse(result.isHomeSearchLiquidGlassEnabled)
        assertFalse(result.isBottomBarLiquidGlassEnabled)
        assertTrue(result.isBottomBarSearchEnabled)
        assertTrue(result.listScopedSearchEnabled)
        assertEquals(BottomBarSearchAutoExpandMode.DISABLED, result.bottomBarSearchAutoExpandMode)
        assertEquals(BottomBarSearchLayoutMode.HOME_AND_SEARCH, result.bottomBarSearchLayoutMode)
        assertTrue(result.androidNativeLiquidGlassEnabled)
        assertFalse(result.isLiquidGlassEnabled)
        assertEquals(LiquidGlassStyle.IOS26, result.liquidGlassStyle)
        assertEquals(LiquidGlassMode.CLEAR, result.liquidGlassMode)
        assertEquals(0.42f, result.liquidGlassStrength)
        assertEquals(0.5f, result.liquidGlassProgress)
        assertEquals(LiquidGlassReadabilityMode.ADAPTIVE, result.liquidGlassReadabilityMode)
        assertEquals(LiquidGlassAdvancedPreset.CUSTOM, result.liquidGlassAdvancedSettings.preset)
        assertEquals(0.84f, result.liquidGlassAdvancedSettings.contentReadability)
        assertEquals(0.36f, result.liquidGlassAdvancedSettings.chromaticAberration)
        assertEquals(0.71f, result.liquidGlassAdvancedSettings.contentDistortion)
        assertEquals(4, result.gridColumnCount)
        assertEquals(HomeFeedCardWidthPreset.WIDE, result.homeFeedCardWidthPreset)
        assertTrue(result.cardAnimationEnabled)
        assertFalse(result.cardTransitionEnabled)
        assertEquals(VideoSharedTransitionSpeed.CUSTOM, result.videoSharedTransitionSpeed)
        assertEquals(620, result.videoSharedTransitionCustomDurationMillis)
        assertFalse(result.smartVisualGuardEnabled)
        assertFalse(result.compactVideoStatsOnCover)
        assertEquals(HomeFeedCardStyle.OFFICIAL, result.homeFeedCardStyle)
        assertEquals(HomeDurationStyle.HIDDEN, result.homeDurationStyle)
        assertEquals(HomeWallpaperEffectMode.STRONG_BLUR, result.homeWallpaperEffectMode)
        assertEquals(HomeWallpaperEffectScope.GLOBAL, result.homeWallpaperEffectScope)
        assertTrue(result.lowQualityHomeCoverInDataSaver)
        assertFalse(result.showHomeUpBadges)
        assertFalse(result.showHomeUpAvatars)
        assertFalse(result.showHomePublishTime)
        assertTrue(result.easterEggEnabled)
        assertTrue(result.crashTrackingConsentShown)
    }

    @Test
    fun invalidHomeFeedCardWidthPresetFallsBackToAuto() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("home_feed_card_width_preset") to 99
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(HomeFeedCardWidthPreset.AUTO, result.homeFeedCardWidthPreset)
    }

    @Test
    fun invalidLiquidGlassPresetFallsBackToCanonicalBalancedValues() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("liquid_glass_advanced_preset") to 99,
            floatPreferencesKey("liquid_glass_content_readability") to 2f,
            floatPreferencesKey("liquid_glass_chromatic_aberration") to -1f,
            floatPreferencesKey("liquid_glass_content_distortion") to Float.NaN,
        )

        val result = mapHomeSettingsFromPreferences(prefs).liquidGlassAdvancedSettings

        assertEquals(LiquidGlassAdvancedPreset.BALANCED, result.preset)
        assertEquals(0.62f, result.contentReadability)
        assertEquals(0.56f, result.chromaticAberration)
        assertEquals(0.45f, result.contentDistortion)
    }

    @Test
    fun invalidLiquidGlassReadabilityModeFallsBackToStable() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("liquid_glass_readability_mode") to 99,
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(LiquidGlassReadabilityMode.STABLE, result.liquidGlassReadabilityMode)
    }

    @Test
    fun customLiquidGlassValuesAreClampedIndependently() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("liquid_glass_advanced_preset") to
                LiquidGlassAdvancedPreset.CUSTOM.value,
            floatPreferencesKey("liquid_glass_content_readability") to 2f,
            floatPreferencesKey("liquid_glass_chromatic_aberration") to -1f,
            floatPreferencesKey("liquid_glass_content_distortion") to Float.NaN,
        )

        val result = mapHomeSettingsFromPreferences(prefs).liquidGlassAdvancedSettings

        assertEquals(LiquidGlassAdvancedPreset.CUSTOM, result.preset)
        assertEquals(1f, result.contentReadability)
        assertEquals(0f, result.chromaticAberration)
        assertEquals(0.45f, result.contentDistortion)
    }

    @Test
    fun invalidVideoSharedTransitionSpeedFallsBackToStandard() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("video_shared_transition_speed") to 99
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(VideoSharedTransitionSpeed.STANDARD, result.videoSharedTransitionSpeed)
    }

    @Test
    fun videoSharedTransitionCustomDurationIsClamped() {
        val lowPrefs = mutablePreferencesOf(
            intPreferencesKey("video_shared_transition_custom_duration_millis") to 120
        )
        val highPrefs = mutablePreferencesOf(
            intPreferencesKey("video_shared_transition_custom_duration_millis") to 1200
        )

        assertEquals(
            VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS,
            mapHomeSettingsFromPreferences(lowPrefs).videoSharedTransitionCustomDurationMillis
        )
        assertEquals(
            VIDEO_SHARED_TRANSITION_CUSTOM_MAX_MILLIS,
            mapHomeSettingsFromPreferences(highPrefs).videoSharedTransitionCustomDurationMillis
        )
    }

    @Test
    fun homeSearchLiquidGlassFallsBackToTopDockPreferenceUntilConfigured() {
        val fallbackPrefs = mutablePreferencesOf(
            booleanPreferencesKey("top_bar_liquid_glass_enabled") to true
        )
        val overriddenPrefs = mutablePreferencesOf(
            booleanPreferencesKey("top_bar_liquid_glass_enabled") to true,
            booleanPreferencesKey("home_search_liquid_glass_enabled") to false
        )

        assertTrue(mapHomeSettingsFromPreferences(fallbackPrefs).isHomeSearchLiquidGlassEnabled)
        assertFalse(mapHomeSettingsFromPreferences(overriddenPrefs).isHomeSearchLiquidGlassEnabled)
    }

    @Test
    fun invalidHomeTopRightActionFallsBackToSettings() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("home_top_right_action") to 99
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(HomeTopRightAction.SETTINGS, result.homeTopRightAction)
    }

    @Test
    fun invalidHomeTopLayoutOrderFallsBackToSearchThenTabs() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("home_top_layout_order") to 99
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(HomeTopLayoutOrder.SEARCH_THEN_TABS, result.homeTopLayoutOrder)
    }

    @Test
    fun legacyHeaderCollapseBoolean_mapsToEquivalentCollapseMode() {
        val disabledPrefs = mutablePreferencesOf(
            booleanPreferencesKey("header_collapse_enabled") to false
        )
        val enabledPrefs = mutablePreferencesOf(
            booleanPreferencesKey("header_collapse_enabled") to true
        )

        val disabled = mapHomeSettingsFromPreferences(disabledPrefs)
        val enabled = mapHomeSettingsFromPreferences(enabledPrefs)

        assertEquals(HomeHeaderCollapseMode.OFF, disabled.homeHeaderCollapseMode)
        assertFalse(disabled.isHeaderCollapseEnabled)
        assertEquals(HomeHeaderCollapseMode.BOTH, enabled.homeHeaderCollapseMode)
        assertTrue(enabled.isHeaderCollapseEnabled)
    }

    @Test
    fun invalidHomeHeaderCollapseModeFallsBackToBoth() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("home_header_collapse_mode") to 99
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(HomeHeaderCollapseMode.BOTH, result.homeHeaderCollapseMode)
        assertTrue(result.isHeaderCollapseEnabled)
    }

    @Test
    fun searchOnlyCollapseMode_mapsCorrectlyAndEnablesCollapse() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("home_header_collapse_mode") to HomeHeaderCollapseMode.SEARCH_ONLY.value
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(HomeHeaderCollapseMode.SEARCH_ONLY, result.homeHeaderCollapseMode)
        assertTrue(result.isHeaderCollapseEnabled)
        assertTrue(result.homeHeaderCollapseMode.collapseSearch)
        assertFalse(result.homeHeaderCollapseMode.collapseTabs)
    }

    @Test
    fun topOnlyCollapseMode_restoresSavedSelectionAndCollapsesBothRows() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("home_header_collapse_mode") to HomeHeaderCollapseMode.SHOW_AT_TOP_ONLY.value
        )
        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(HomeHeaderCollapseMode.SHOW_AT_TOP_ONLY, result.homeHeaderCollapseMode)
        assertTrue(result.isHeaderCollapseEnabled)
        assertTrue(result.homeHeaderCollapseMode.collapseSearch)
        assertTrue(result.homeHeaderCollapseMode.collapseTabs)
    }

    @Test
    fun invalidHomeBarHideTypeFallsBackToSync() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("home_bar_hide_type") to 99
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(HomeBarHideType.SYNC, result.homeBarHideType)
    }

    @Test
    fun topBarHideSwitchCollapsesSearchAndTabsUntilBackToTop() {
        assertEquals(
            HomeHeaderCollapseMode.BOTH,
            resolveHomeHeaderCollapseModeForTopBarHide(hideTopBar = true)
        )
        assertEquals(
            HomeHeaderCollapseMode.OFF,
            resolveHomeHeaderCollapseModeForTopBarHide(hideTopBar = false)
        )
        assertEquals(
            HomeHeaderCollapseMode.BOTH,
            resolveHomeHeaderCollapseModeForSearch(
                currentMode = HomeHeaderCollapseMode.TABS_ONLY,
                collapseSearch = true
            )
        )
        assertEquals(
            HomeHeaderCollapseMode.OFF,
            resolveHomeHeaderCollapseModeForSearch(
                currentMode = HomeHeaderCollapseMode.BOTH,
                collapseSearch = false
            )
        )
    }

    @Test
    fun legacyAndroidNativeTopTabLiquidGlassKey_mapsToGlobalOptIn() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("android_native_top_tab_liquid_glass_enabled") to true
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertTrue(result.androidNativeLiquidGlassEnabled)
    }

    @Test
    fun explicitHeaderBlurMode_overridesLegacyBoolean() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("header_blur_enabled") to false,
            intPreferencesKey("home_header_blur_mode") to HomeHeaderBlurMode.ALWAYS_ON.value
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(HomeHeaderBlurMode.ALWAYS_ON, result.headerBlurMode)
        assertTrue(result.isHeaderBlurEnabled)
    }

    @Test
    fun followPresetHeaderBlur_keepsHeaderBlurOnForIosAndMd3() {
        assertTrue(
            resolveHomeHeaderBlurEnabled(
                mode = HomeHeaderBlurMode.FOLLOW_PRESET,
            )
        )
        assertTrue(
            resolveHomeHeaderBlurEnabled(
                mode = HomeHeaderBlurMode.FOLLOW_PRESET,
            )
        )
    }

    @Test
    fun storedLiquidGlassProgress_isUsedBySharedMaterialRecipe() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("liquid_glass_style") to LiquidGlassStyle.SUKISU.value,
            intPreferencesKey("liquid_glass_mode") to LiquidGlassMode.BALANCED.value,
            floatPreferencesKey("liquid_glass_strength") to 0.31f,
            floatPreferencesKey("liquid_glass_material_progress_v2") to 0.82f
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(LiquidGlassStyle.SUKISU, result.liquidGlassStyle)
        assertEquals(LiquidGlassMode.BALANCED, result.liquidGlassMode)
        assertEquals(0.31f, result.liquidGlassStrength)
        assertEquals(0.82f, result.liquidGlassProgress)
    }

    @Test
    fun staleLegacyLiquidGlassProgress_doesNotOverrideNewBaseline() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("liquid_glass_progress") to 0.08f
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(0.5f, result.liquidGlassProgress)
    }

    @Test
    fun legacySharedLiquidGlassToggle_backfillsBottomSwitchOnly() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("liquid_glass_enabled") to false
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertFalse(result.isTopBarLiquidGlassEnabled)
        assertFalse(result.isBottomBarLiquidGlassEnabled)
        assertFalse(result.isLiquidGlassEnabled)
    }

    @Test
    fun legacySharedLiquidGlassToggle_trueStillBackfillsBottomSwitch() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("liquid_glass_enabled") to true
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertFalse(result.isTopBarLiquidGlassEnabled)
        assertTrue(result.isBottomBarLiquidGlassEnabled)
        assertTrue(result.isLiquidGlassEnabled)
    }

    @Test
    fun homeDurationStyle_explicitValueWinsAndInvalidEnumsFallback() {
        val explicit = mapHomeSettingsFromPreferences(
            mutablePreferencesOf(
                booleanPreferencesKey("home_video_duration_badges_visible") to false,
                intPreferencesKey("home_duration_style") to HomeDurationStyle.OVERLAY_TEXT_ONLY.value,
                intPreferencesKey("home_feed_card_style") to 99
            )
        )

        assertEquals(HomeDurationStyle.OVERLAY_TEXT_ONLY, explicit.homeDurationStyle)
        assertEquals(HomeFeedCardStyle.BILIPAI, explicit.homeFeedCardStyle)
    }

    @Test
    fun populatedPreferences_mapHideTopTabs() {
        val prefsTrue = mutablePreferencesOf(
            booleanPreferencesKey("hide_top_tabs") to true
        )
        val prefsFalse = mutablePreferencesOf(
            booleanPreferencesKey("hide_top_tabs") to false
        )

        assertTrue(mapHomeSettingsFromPreferences(prefsTrue).hideTopTabs)
        assertFalse(mapHomeSettingsFromPreferences(prefsFalse).hideTopTabs)
    }
}
