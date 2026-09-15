package com.android.purebilibili.feature.settings

import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.BiliPaiNavRouteTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsNavHierarchyPolicyTest {

    @Test
    fun isSettingsSubtreeRoute_includesSettingsRoutes() {
        assertTrue(isSettingsSubtreeRoute("settings"))
        assertTrue(isSettingsSubtreeRoute("settings_category"))
        assertTrue(isSettingsSubtreeRoute("settings_search"))
        assertTrue(isSettingsSubtreeRoute("appearance_settings"))
        assertTrue(isSettingsSubtreeRoute("home_settings"))
        assertTrue(isSettingsSubtreeRoute("message_notification_settings"))
        assertFalse(isSettingsSubtreeRoute("home"))
    }

    @Test
    fun resolveSettingsNavDepth_mapsHierarchy() {
        assertEquals(0, resolveSettingsNavDepth("settings"))
        assertEquals(1, resolveSettingsNavDepth("settings_category"))
        assertEquals(2, resolveSettingsNavDepth("appearance_settings"))
        assertEquals(2, resolveSettingsNavDepth("home_settings"))
        assertEquals(2, resolveSettingsNavDepth("message_notification_settings"))
        assertEquals(3, resolveSettingsNavDepth("icon_settings"))
        assertEquals(2, resolveSettingsNavDepth("animation_settings"))
    }

    @Test
    fun resolveSettingsNavParentRoute_keepsIndependentCategoriesAtRootDetailDepth() {
        assertEquals("settings_category", resolveSettingsNavParentRoute("animation_settings"))
        assertEquals("settings_category", resolveSettingsNavParentRoute("home_settings"))
        assertEquals("appearance_settings", resolveSettingsNavParentRoute("icon_settings"))
        assertEquals("settings_category", resolveSettingsNavParentRoute("message_notification_settings"))
        assertEquals("settings_category", resolveSettingsNavParentRoute("appearance_settings"))
    }

    @Test
    fun isSettingsNavHierarchyTransition_matchesParentChild() {
        assertTrue(
            isSettingsNavHierarchyTransition(
                parentRoute = "settings",
                childRoute = "settings_category",
            )
        )
        assertTrue(
            isSettingsNavHierarchyTransition(
                parentRoute = "settings_category",
                childRoute = "appearance_settings",
            )
        )
        assertTrue(
            isSettingsNavHierarchyTransition(
                parentRoute = "settings_search",
                childRoute = "appearance_settings",
            )
        )
        assertTrue(
            isSettingsNavHierarchyTransition(
                parentRoute = "settings",
                childRoute = "appearance_settings",
            )
        )
        assertTrue(
            isSettingsNavHierarchyTransition(
                parentRoute = "settings",
                childRoute = "playback_settings",
            )
        )
        assertTrue(
            isSettingsNavHierarchyTransition(
                parentRoute = "settings",
                childRoute = "message_notification_settings",
            )
        )
        assertFalse(
            isSettingsNavHierarchyTransition(
                parentRoute = "appearance_settings",
                childRoute = "animation_settings",
            )
        )
        assertTrue(
            isSettingsNavHierarchyTransition(
                parentRoute = "settings",
                childRoute = "animation_settings",
            )
        )
        assertTrue(
            isSettingsNavHierarchyTransition(
                parentRoute = "profile",
                childRoute = "settings",
            )
        )
        assertTrue(
            isSettingsNavHierarchyTransition(
                parentRoute = "home",
                childRoute = "settings",
            )
        )
        assertTrue(
            isSettingsNavHierarchyTransition(
                parentRoute = "dynamic",
                childRoute = "settings",
            )
        )
        assertFalse(
            isSettingsNavHierarchyTransition(
                parentRoute = "home",
                childRoute = "appearance_settings",
            )
        )
    }

    // forward 决策已迁至 BiliPaiNavEntryProvider（resolveBiliPaiNavEntryForwardRouteTransition，
    // 见 BiliPaiNavEntryProviderPolicyTest 的 SETTINGS_IOS_PUSH_FORWARD 断言）；pop 决策见下。

    @Test
    fun resolveSettingsNavPopTransition_remapsMainHostWhenSettingsTabActive() {
        assertEquals(
            BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
            resolveSettingsNavPopTransition(
                fromKey = BiliPaiNavKey.AppearanceSettings,
                toKey = BiliPaiNavKey.MainHost,
                activeMainHostRoute = "settings",
            )
        )
        assertEquals(
            BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
            resolveSettingsNavPopTransition(
                fromKey = BiliPaiNavKey.AnimationSettings,
                toKey = BiliPaiNavKey.MainHost,
                activeMainHostRoute = "settings",
            )
        )
        assertEquals(
            BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
            resolveSettingsNavPopTransition(
                fromKey = BiliPaiNavKey.Settings,
                toKey = BiliPaiNavKey.MainHost,
                activeMainHostRoute = "profile",
            )
        )
        assertEquals(
            BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
            resolveSettingsNavPopTransition(
                fromKey = BiliPaiNavKey.Settings,
                toKey = BiliPaiNavKey.MainHost,
                activeMainHostRoute = "home",
            )
        )
        assertEquals(
            BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
            resolveSettingsNavPopTransition(
                fromKey = BiliPaiNavKey.Settings,
                toKey = BiliPaiNavKey.MainHost,
                activeMainHostRoute = "dynamic",
            )
        )
        assertNull(
            resolveSettingsNavPopTransition(
                fromKey = BiliPaiNavKey.AppearanceSettings,
                toKey = BiliPaiNavKey.MainHost,
                activeMainHostRoute = "home",
            )
        )
    }

    @Test
    fun resolveSettingsRootCategoryForNavKey_readsCategoryKey() {
        @Suppress("DEPRECATION")
        val category = resolveSettingsRootCategoryForNavKey(
            BiliPaiNavKey.SettingsCategory(SettingsRootCategory.CONTENT_PLAYBACK)
        )
        assertEquals(SettingsRootCategory.PLAYBACK_QUALITY, category)
        assertEquals(
            SettingsRootCategory.PRIVACY_PERMISSION,
            resolveSettingsRootCategoryForRoute("message_notification_settings"),
        )
        assertEquals(
            SettingsRootCategory.PRIVACY_PERMISSION,
            resolveSettingsRootCategoryForNavKey(BiliPaiNavKey.MessageNotificationSettings),
        )
    }

    @Test
    fun singleEntryCategory_directlyTargetsNextLevel() {
        // 「外观与主题」分类页只有一个「外观设置」入口 → 点击分类直接进外观设置页
        assertEquals(
            BiliPaiNavKey.AppearanceSettings,
            resolveSettingsCategoryDirectTargetKey(SettingsRootCategory.APPEARANCE_THEME),
        )
        assertEquals(
            BiliPaiNavKey.AppearanceSettings,
            resolveSettingsCategoryNavKey(SettingsRootCategory.APPEARANCE_THEME),
        )
        // 「插件与扩展」分类页只有一个「插件中心」入口 → 点击分类直接进插件中心
        assertEquals(
            BiliPaiNavKey.PluginsSettings(),
            resolveSettingsCategoryDirectTargetKey(SettingsRootCategory.PLUGINS_EXTENSIONS),
        )
        assertEquals(
            BiliPaiNavKey.PluginsSettings(),
            resolveSettingsCategoryNavKey(SettingsRootCategory.PLUGINS_EXTENSIONS),
        )
    }

    @Test
    fun multiEntryOrInlineCategories_keepCategoryScreen() {
        // 播放/首页/隐私等分类含内联设置或多个入口,保留中间层
        assertNull(resolveSettingsCategoryDirectTargetKey(SettingsRootCategory.PLAYBACK_QUALITY))
        assertNull(resolveSettingsCategoryDirectTargetKey(SettingsRootCategory.HOME_RECOMMENDATION))
        assertNull(resolveSettingsCategoryDirectTargetKey(SettingsRootCategory.NAVIGATION_INTERACTION))
        assertNull(resolveSettingsCategoryDirectTargetKey(SettingsRootCategory.PRIVACY_PERMISSION))
        assertNull(resolveSettingsCategoryDirectTargetKey(SettingsRootCategory.STORAGE_BACKUP))
        assertNull(resolveSettingsCategoryDirectTargetKey(SettingsRootCategory.SYSTEM_ABOUT))
        assertEquals(
            BiliPaiNavKey.SettingsCategory(SettingsRootCategory.PLAYBACK_QUALITY),
            resolveSettingsCategoryNavKey(SettingsRootCategory.PLAYBACK_QUALITY),
        )
    }
}
