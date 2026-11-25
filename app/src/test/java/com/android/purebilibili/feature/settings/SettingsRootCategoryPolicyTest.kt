package com.android.purebilibili.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsRootCategoryPolicyTest {

    @Test
    fun `mobile and tablet settings share the same root categories`() {
        val expected = listOf(
            SettingsRootCategory.APPEARANCE_THEME,
            SettingsRootCategory.ANIMATION_HAPTICS,
            SettingsRootCategory.PLAYBACK_QUALITY,
            SettingsRootCategory.HOME_RECOMMENDATION,
            SettingsRootCategory.NAVIGATION_INTERACTION,
            SettingsRootCategory.PRIVACY_PERMISSION,
            SettingsRootCategory.STORAGE_BACKUP,
            SettingsRootCategory.PLUGINS_EXTENSIONS,
            SettingsRootCategory.SYSTEM_ABOUT,
        )

        assertEquals(expected, resolveSettingsRootCategoryOrder())
        assertEquals(expected, resolveTabletSettingsRootCategoryOrder())
    }

    @Test
    fun `root categories expose the agreed user facing titles`() {
        assertEquals(
            listOf(
                "界面外观",
                "动效与反馈",
                "播放设置",
                "首页与动态",
                "导航布局",
                "隐私与权限",
                "下载与备份",
                "插件中心",
                "手柄控制",
                "帮助与关于",
            ),
            resolveSettingsRootCategoryOrder().map { it.title },
        )
    }

    @Test
    fun `search targets map back to their direct category`() {
        assertEquals(
            SettingsRootCategory.HOME_RECOMMENDATION,
            resolveSettingsRootCategoryForSearchTarget(SettingsSearchTarget.HOME_FEED),
        )
        assertEquals(
            SettingsRootCategory.PLAYBACK_QUALITY,
            resolveSettingsRootCategoryForSearchTarget(SettingsSearchTarget.FULLSCREEN_GESTURE),
        )
        assertEquals(
            SettingsRootCategory.PLAYBACK_QUALITY,
            resolveSettingsRootCategoryForSearchTarget(SettingsSearchTarget.INTERACTION_COMMENT),
        )
        assertEquals(
            SettingsRootCategory.ANIMATION_HAPTICS,
            resolveSettingsRootCategoryForSearchTarget(SettingsSearchTarget.ANIMATION),
        )
        assertEquals(
            SettingsRootCategory.STORAGE_BACKUP,
            resolveSettingsRootCategoryForSearchTarget(SettingsSearchTarget.WEBDAV_BACKUP),
        )
        assertEquals(
            SettingsRootCategory.PLUGINS_EXTENSIONS,
            resolveSettingsRootCategoryForSearchTarget(SettingsSearchTarget.PLUGINS),
        )
        assertEquals(
            SettingsRootCategory.PLUGINS_EXTENSIONS,
            resolveSettingsRootCategoryForSearchTarget(SettingsSearchTarget.GAMEPAD_CONTROL),
        )
        assertEquals(
            SettingsRootCategory.SYSTEM_ABOUT,
            resolveSettingsRootCategoryForSearchTarget(SettingsSearchTarget.TELEGRAM),
        )
        assertEquals(
            SettingsRootCategory.SYSTEM_ABOUT,
            resolveSettingsRootCategoryForSearchTarget(SettingsSearchTarget.TIPS),
        )
        assertEquals(
            SettingsRootCategory.SYSTEM_ABOUT,
            resolveSettingsRootCategoryForSearchTarget(SettingsSearchTarget.EXPORT_LOGS),
        )
        assertFalse(SettingsRootCategory.PLUGINS_EXTENSIONS.title.contains("Beta", ignoreCase = true))
        assertFalse(SettingsRootCategory.PLUGINS_EXTENSIONS.subtitle.contains("Beta", ignoreCase = true))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy serialized categories canonicalize to visible entries`() {
        assertEquals(
            SettingsRootCategory.APPEARANCE_THEME,
            canonicalSettingsRootCategory(SettingsRootCategory.APPEARANCE_INTERACTION),
        )
        assertEquals(
            SettingsRootCategory.PLAYBACK_QUALITY,
            canonicalSettingsRootCategory(SettingsRootCategory.CONTENT_PLAYBACK),
        )
        assertEquals(
            SettingsRootCategory.PRIVACY_PERMISSION,
            canonicalSettingsRootCategory(SettingsRootCategory.PRIVACY_STORAGE),
        )
    }
}
