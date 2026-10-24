package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationSettingsMappingPolicyTest {

    @Test
    fun editorReceivesSavedOrderAndHiddenItemsAlongsideRuntimeProjection() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("bottom_bar_order") to "PROFILE,HOME,FAVORITE,DYNAMIC",
            stringPreferencesKey("bottom_bar_visible_tabs") to "PROFILE,HOME",
            intPreferencesKey("bottom_bar_visibility_mode") to SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE.value,
            booleanPreferencesKey("bottom_bar_scroll_compact_enabled") to true,
            booleanPreferencesKey("bottom_bar_floating") to true,
        )
        val settings = mapAppNavigationSettingsFromPreferences(prefs)

        assertEquals(listOf("PROFILE", "HOME", "FAVORITE", "DYNAMIC"), settings.bottomBarOrder)
        assertEquals(setOf("PROFILE", "HOME"), settings.bottomBarVisibleTabs)
        assertEquals(listOf("PROFILE", "HOME"), settings.orderedVisibleTabIds)
        assertEquals(SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE, settings.savedBottomBarVisibilityMode)
        assertEquals(SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE, settings.bottomBarVisibilityMode)
    }

    @Test
    fun editorPreservesExplicitEmptyVisibilityInsteadOfSubstitutingDefaults() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("bottom_bar_visible_tabs") to "")
        assertEquals(emptySet(), mapAppNavigationSettingsFromPreferences(prefs).bottomBarVisibleTabs)
    }

    @Test
    fun indicatorWobbleDefaultsEnabledAndKeepsExplicitChoice() {
        val key = booleanPreferencesKey("liquid_indicator_wobble_enabled")
        assertTrue(AppNavigationSettings().liquidIndicatorWobbleEnabled)
        assertTrue(mapAppNavigationSettingsFromPreferences(mutablePreferencesOf()).liquidIndicatorWobbleEnabled)
        for (enabled in listOf(false, true)) {
            val prefs = mutablePreferencesOf(key to enabled)
            assertEquals(enabled, mapAppNavigationSettingsFromPreferences(prefs).liquidIndicatorWobbleEnabled)
        }
    }

    @Test
    fun scrollCompactDefaultsEnabledAndPreservesExplicitOptOut() {
        val compact = booleanPreferencesKey("bottom_bar_scroll_compact_enabled")
        val visibility = intPreferencesKey("bottom_bar_visibility_mode")
        val prefs = mutablePreferencesOf(
            visibility to SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE.value,
        )
        assertTrue(HomeSettings().bottomBarScrollCompactEnabled)
        assertTrue(mapHomeSettingsFromPreferences(prefs).bottomBarScrollCompactEnabled)
        assertEquals(SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE,
            mapAppNavigationSettingsFromPreferences(prefs).bottomBarVisibilityMode)
        prefs[compact] = false
        assertFalse(mapHomeSettingsFromPreferences(prefs).bottomBarScrollCompactEnabled)
        assertEquals(SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE,
            mapAppNavigationSettingsFromPreferences(prefs).bottomBarVisibilityMode)
    }

    @Test
    fun explicitVisibilityModeWinsForEveryDockCompactAndFloatingCombination() {
        val mode = intPreferencesKey("bottom_bar_visibility_mode")
        val compact = booleanPreferencesKey("bottom_bar_scroll_compact_enabled")
        val floating = booleanPreferencesKey("bottom_bar_floating")
        for (visibility in SettingsManager.BottomBarVisibilityMode.entries) {
            for (compactEnabled in listOf(false, true)) {
                for (floatingEnabled in listOf(false, true)) {
                    val prefs = mutablePreferencesOf(
                        mode to visibility.value, compact to compactEnabled, floating to floatingEnabled,
                    )
                    val mapped = mapAppNavigationSettingsFromPreferences(prefs)
                    assertEquals(visibility, mapped.bottomBarVisibilityMode)
                    assertEquals(visibility, mapped.savedBottomBarVisibilityMode)
                    assertEquals(visibility.value, prefs[mode])
                    assertEquals(compactEnabled, mapHomeSettingsFromPreferences(prefs).bottomBarScrollCompactEnabled)
                }
            }
        }
    }

    @Test
    fun emptyPreferences_useExpectedNavigationDefaults() {
        val prefs = mutablePreferencesOf()

        val result = mapAppNavigationSettingsFromPreferences(prefs)

        assertEquals(
            SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE,
            result.bottomBarVisibilityMode
        )
        assertEquals(
            listOf("HOME", "DYNAMIC", "HISTORY", "LISTEN_VIDEO", "PROFILE"),
            result.orderedVisibleTabIds
        )
        assertEquals(emptyMap(), result.bottomBarItemColors)
        assertEquals(emptyMap(), result.bottomBarItemLabels)
        assertFalse(result.tabletUseSidebar)
        assertTrue(result.sidebarAccountSwitcherEnabled)
        assertTrue(result.predictiveBackEnabled)
        assertEquals("miuix", result.predictiveBackAnimationStyle)
        assertEquals("always_right", result.predictiveBackExitDirection)
        assertTrue(result.miuixTransitionBlurEnabled)
        assertEquals(100, result.miuixPredictiveBackMaxProgressPercent)
        assertTrue(result.videoSharedReturnGestureFollowEnabled)
        assertTrue(result.videoSharedReturnGestureTranslationEnabled)
    }

    @Test
    fun emptyPreferences_onTablet_defaultSidebarEnabled() {
        val prefs = mutablePreferencesOf()

        val result = mapAppNavigationSettingsFromPreferences(
            preferences = prefs,
            defaultTabletUseSidebar = true
        )

        assertTrue(result.tabletUseSidebar)
        assertTrue(defaultTabletUseSidebar(isTabletDevice = true))
        assertFalse(defaultTabletUseSidebar(isTabletDevice = false))
    }

    @Test
    fun gestureRotationAndTranslationPreferencesAreIndependent() {
        for (poseEnabled in listOf(false, true)) {
            for (translationEnabled in listOf(false, true)) {
                val result = mapAppNavigationSettingsFromPreferences(mutablePreferencesOf(
                    booleanPreferencesKey("video_shared_return_gesture_follow_enabled") to poseEnabled,
                    booleanPreferencesKey("video_shared_return_gesture_translation_enabled") to translationEnabled,
                ))
                assertEquals(poseEnabled, result.videoSharedReturnGestureFollowEnabled)
                assertEquals(translationEnabled, result.videoSharedReturnGestureTranslationEnabled)
            }
        }
    }

    @Test
    fun populatedPreferences_mapToNavigationSettingsCorrectly() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("bottom_bar_order") to "PROFILE,HOME,DYNAMIC,HISTORY",
            stringPreferencesKey("bottom_bar_visible_tabs") to "HOME,PROFILE,HISTORY",
            stringPreferencesKey("bottom_bar_item_colors") to "HOME:2,PROFILE:4,INVALID:x,NO_COLON",
            stringPreferencesKey("bottom_bar_item_labels") to
                "home=%E9%A6%96%E9%A1%B5%2C%E6%96%B0,PROFILE=%E8%B4%A6%E5%8F%B7",
            booleanPreferencesKey("tablet_use_sidebar") to true,
            booleanPreferencesKey("sidebar_account_switcher_enabled") to false,
            booleanPreferencesKey("miuix_transition_blur_enabled") to false,
            intPreferencesKey("miuix_predictive_back_max_progress_percent") to 120,
            booleanPreferencesKey("video_shared_return_gesture_follow_enabled") to false,
        )

        val result = mapAppNavigationSettingsFromPreferences(prefs)

        assertEquals(listOf("PROFILE", "HOME", "HISTORY"), result.orderedVisibleTabIds)
        assertEquals(mapOf("HOME" to 2, "PROFILE" to 4, "INVALID" to 0), result.bottomBarItemColors)
        assertEquals(mapOf("HOME" to "首页,新", "PROFILE" to "账号"), result.bottomBarItemLabels)
        assertTrue(result.tabletUseSidebar)
        assertFalse(result.sidebarAccountSwitcherEnabled)
        assertFalse(result.miuixTransitionBlurEnabled)
        assertEquals(100, result.miuixPredictiveBackMaxProgressPercent)
        assertFalse(result.videoSharedReturnGestureFollowEnabled)
    }

    @Test
    fun visibleBottomTabs_surviveWhenSavedOrderIsMissing() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("bottom_bar_order") to "",
            stringPreferencesKey("bottom_bar_visible_tabs") to "HOME,DYNAMIC,HISTORY,PROFILE"
        )

        val result = mapAppNavigationSettingsFromPreferences(prefs)

        assertEquals(listOf("HOME", "DYNAMIC", "HISTORY", "PROFILE"), result.orderedVisibleTabIds)
    }

    @Test
    fun listenVideoMigration_insertsBeforeProfileWhenCapacityRemains() {
        val result = resolveListenVideoBottomTabMigration(
            order = listOf("HOME", "DYNAMIC", "HISTORY", "PROFILE"),
            visible = setOf("HOME", "DYNAMIC", "HISTORY", "PROFILE"),
            migrationComplete = false
        )

        assertEquals(
            listOf("HOME", "DYNAMIC", "HISTORY", "LISTEN_VIDEO", "PROFILE"),
            result.order
        )
        assertEquals(
            setOf("HOME", "DYNAMIC", "HISTORY", "LISTEN_VIDEO", "PROFILE"),
            result.visible
        )
        assertTrue(result.markComplete)
    }

    @Test
    fun listenVideoMigration_preservesExistingFiveItemCustomization() {
        val original = listOf("HOME", "STORY", "FAVORITE", "LIVE", "PROFILE")

        val result = resolveListenVideoBottomTabMigration(
            order = original,
            visible = original.toSet(),
            migrationComplete = false
        )

        assertEquals(original, result.order)
        assertEquals(original.toSet(), result.visible)
        assertTrue(result.markComplete)
    }

    @Test
    fun listenVideoMigration_doesNotReinsertAfterUserHidesIt() {
        val original = listOf("HOME", "DYNAMIC", "HISTORY", "PROFILE", "LISTEN_VIDEO")

        val result = resolveListenVideoBottomTabMigration(
            order = original,
            visible = setOf("HOME", "DYNAMIC", "HISTORY", "PROFILE"),
            migrationComplete = true
        )

        assertEquals(original, result.order)
        assertEquals(setOf("HOME", "DYNAMIC", "HISTORY", "PROFILE"), result.visible)
        assertFalse(result.markComplete)
    }
}
