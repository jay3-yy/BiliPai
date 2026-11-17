package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.performance.PERFORMANCE_HISTORY_SETTING_ID
import com.android.purebilibili.core.performance.PERFORMANCE_HISTORY_TITLE
import com.android.purebilibili.navigation3.BiliPaiNavEntryContentRole
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.BiliPaiNavRouteTransition
import com.android.purebilibili.navigation3.legacyRouteToBiliPaiNavKey
import com.android.purebilibili.navigation3.resolveBiliPaiNavEntryContentRole
import com.android.purebilibili.navigation3.toLegacyRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceHistoryNavigationTest {
    @Test
    fun historySearchOpensDedicatedRestorablePage() {
        val result = SettingsSearchResult(
            target = SettingsSearchTarget.DIAGNOSTICS,
            title = PERFORMANCE_HISTORY_TITLE,
            subtitle = "",
            section = "帮助与关于",
            settingId = PERFORMANCE_HISTORY_SETTING_ID,
            page = SettingsRootCategory.SYSTEM_ABOUT,
        )
        val destination = resolveSettingsSearchNavigation(result)
        assertEquals(BiliPaiNavKey.PerformanceHistory, destination)
        assertEquals(destination, legacyRouteToBiliPaiNavKey(BiliPaiNavKey.PerformanceHistory.toLegacyRoute()))
        assertEquals(BiliPaiNavEntryContentRole.PERFORMANCE_HISTORY, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.PerformanceHistory))
    }

    @Test
    fun historyKeepsSettingsCategoryAndReturnTransitions() {
        val route = BiliPaiNavKey.PerformanceHistory.routeBase
        assertTrue(isSettingsSubtreeRoute(route))
        assertEquals(SettingsRootCategory.SYSTEM_ABOUT, resolveSettingsRootCategoryForRoute(route))
        assertEquals(2, resolveSettingsNavDepth(route))
        listOf("settings", "settings_category", "settings_search").forEach { parent ->
            assertTrue(isSettingsNavHierarchyTransition(parent, route))
            assertEquals(BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP, resolveSettingsNavPopTransition(route, parent))
        }
    }
}
