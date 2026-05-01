package com.android.purebilibili.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchPolicyTest {

    @Test
    fun blankQuery_returnsEmptyList() {
        val results = resolveSettingsSearchResults("   ")

        assertTrue(results.isEmpty())
    }

    @Test
    fun queryByChineseKeyword_hitsExpectedSetting() {
        val results = resolveSettingsSearchResults("缓存")

        assertTrue(results.any { it.target == SettingsSearchTarget.CLEAR_CACHE })
    }

    @Test
    fun queryByEnglishAlias_isCaseInsensitive() {
        val results = resolveSettingsSearchResults("gItHuB")

        assertTrue(results.any { it.target == SettingsSearchTarget.OPEN_SOURCE_HOME })
    }

    @Test
    fun prefixMatch_ranksBeforeGenericContains() {
        val results = resolveSettingsSearchResults("检查")

        assertEquals(SettingsSearchTarget.CHECK_UPDATE, results.firstOrNull()?.target)
    }

    @Test
    fun limit_isRespected() {
        val results = resolveSettingsSearchResults("设", maxResults = 3)

        assertEquals(3, results.size)
    }

    @Test
    fun queryByShareKeyword_hitsSettingsShareEntry() {
        val results = resolveSettingsSearchResults("导入")

        assertTrue(results.any { it.target == SettingsSearchTarget.SETTINGS_SHARE })
    }

    @Test
    fun queryByGlassKeyword_hitsAppearanceEntry() {
        val results = resolveSettingsSearchResults("玻璃")

        assertTrue(results.any { it.target == SettingsSearchTarget.APPEARANCE })
    }

    @Test
    fun queryByUpBadgeKeyword_hitsAppearanceEntry() {
        val results = resolveSettingsSearchResults("UP主标识")

        assertTrue(results.any { it.target == SettingsSearchTarget.APPEARANCE })
    }

    @Test
    fun queryByMd3Alias_hitsAppearanceEntry() {
        val results = resolveSettingsSearchResults("md3")

        assertTrue(results.any { it.target == SettingsSearchTarget.APPEARANCE })
    }

    @Test
    fun queryByAndroidNativeLiquidGlass_focusesAppearanceThemeSection() {
        val result = resolveSettingsSearchResults("安卓原生液态玻璃").firstOrNull()

        assertEquals(SettingsSearchTarget.APPEARANCE, result?.target)
        assertEquals(SettingsSearchFocusIds.APPEARANCE_THEME, result?.focusId)
    }

    @Test
    fun queryByPinyin_hitsChineseAlias() {
        val results = resolveSettingsSearchResults("waiguan")

        assertTrue(results.any { it.target == SettingsSearchTarget.APPEARANCE })
    }

    @Test
    fun queryByPredictiveBack_hitsAppearanceEntry() {
        val results = resolveSettingsSearchResults("预测性返回")

        assertTrue(results.any { it.target == SettingsSearchTarget.APPEARANCE })
    }

    @Test
    fun queryByPictureInPicture_hitsPlaybackEntry() {
        val results = resolveSettingsSearchResults("画中画")

        assertTrue(results.any { it.target == SettingsSearchTarget.PLAYBACK })
    }

    @Test
    fun queryByAutoRotate_hitsPlaybackEntry() {
        val results = resolveSettingsSearchResults("自动横竖屏")

        assertTrue(results.any { it.target == SettingsSearchTarget.PLAYBACK })
    }

    @Test
    fun queryByAutoCheckUpdate_hitsCheckUpdateEntry() {
        val results = resolveSettingsSearchResults("自动检查更新")

        assertTrue(results.any { it.target == SettingsSearchTarget.CHECK_UPDATE })
    }

    @Test
    fun queryByBottomBar_surfacesTopTabDiscoverabilityInSubtitle() {
        val result = resolveSettingsSearchResults("底栏").firstOrNull {
            it.target == SettingsSearchTarget.BOTTOM_BAR && it.title == "导航设置"
        }

        assertEquals("底栏、顶部标签、平板侧边栏", result?.subtitle)
    }

    @Test
    fun queryByAutoCollapse_hitsTopTabManagementEntry() {
        val result = resolveSettingsSearchResults("自动收缩").firstOrNull {
            it.target == SettingsSearchTarget.BOTTOM_BAR && it.title == "顶部标签管理"
        }

        assertEquals("显示/隐藏、排序、自动收缩", result?.subtitle)
        assertEquals("导航设置", result?.section)
    }

    @Test
    fun queryByDefaultHomeTabKeywords_hitsTopTabManagementEntry() {
        listOf("默认首页", "默认热门", "启动首页").forEach { query ->
            val result = resolveSettingsSearchResults(query).firstOrNull {
                it.target == SettingsSearchTarget.BOTTOM_BAR &&
                    it.focusId == SettingsSearchFocusIds.BOTTOM_BAR_TOP_TABS
            }

            assertEquals("顶部标签管理", result?.title)
        }
    }

    @Test
    fun queryBySidebarNavigation_hitsNavigationSettingsEntry() {
        val result = resolveSettingsSearchResults("侧边导航栏").firstOrNull {
            it.target == SettingsSearchTarget.BOTTOM_BAR &&
                it.focusId == SettingsSearchFocusIds.BOTTOM_BAR_TABLET
        }

        assertEquals("平板侧边导航栏", result?.title)
        assertEquals("导航设置", result?.section)
    }
}
