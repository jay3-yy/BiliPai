package com.android.purebilibili.feature.settings

import com.android.purebilibili.navigation3.BiliPaiNavKey

internal fun resolveSettingsSearchNavigation(result: SettingsSearchResult): BiliPaiNavKey? {
    if (result.settingId == PROCESS_MEMORY_SETTING_ID) return BiliPaiNavKey.ProcessMemory
    if (result.settingId == com.android.purebilibili.core.performance.PERFORMANCE_HISTORY_SETTING_ID) {
        return BiliPaiNavKey.PerformanceHistory
    }
    // 场景内具体条目已指定分类时，不再被通用入口映射到其他子页。
    if (isSceneSettingsSearchTarget(result.target)) {
        result.page?.let { return resolveSettingsCategoryNavKey(it) }
    }
    resolveSettingsSceneDetailFocus(result.target)?.let { detailFocus ->
        return when (detailFocus.target) {
            SettingsSearchTarget.APPEARANCE -> BiliPaiNavKey.AppearanceSettings
            SettingsSearchTarget.HOME_FEED -> BiliPaiNavKey.HomeSettings
            SettingsSearchTarget.ANIMATION -> BiliPaiNavKey.AnimationSettings
            SettingsSearchTarget.PLAYBACK -> BiliPaiNavKey.PlaybackSettings
            SettingsSearchTarget.BOTTOM_BAR -> BiliPaiNavKey.BottomBarSettings
            else -> null
        }
    }
    if (isSceneSettingsSearchTarget(result.target)) {
        return null
    }
    return when (result.target) {
        SettingsSearchTarget.APPEARANCE -> BiliPaiNavKey.AppearanceSettings
        SettingsSearchTarget.ANIMATION -> BiliPaiNavKey.AnimationSettings
        SettingsSearchTarget.PLAYBACK -> BiliPaiNavKey.PlaybackSettings
        SettingsSearchTarget.BOTTOM_BAR -> BiliPaiNavKey.BottomBarSettings
        SettingsSearchTarget.PERMISSION -> BiliPaiNavKey.PermissionSettings
        SettingsSearchTarget.SPACE_PRIVACY -> BiliPaiNavKey.SpacePrivacySettings
        SettingsSearchTarget.LOGIN_ACTIVITY -> BiliPaiNavKey.LoginActivitySettings
        SettingsSearchTarget.MESSAGE_NOTIFICATION -> BiliPaiNavKey.MessageNotificationSettings
        SettingsSearchTarget.PLUGINS -> BiliPaiNavKey.PluginsSettings()
        SettingsSearchTarget.SETTINGS_SHARE -> BiliPaiNavKey.SettingsShare
        SettingsSearchTarget.WEBDAV_BACKUP -> BiliPaiNavKey.WebDavBackup
        SettingsSearchTarget.OPEN_SOURCE_LICENSES -> BiliPaiNavKey.OpenSourceLicenses
        SettingsSearchTarget.TIPS -> BiliPaiNavKey.TipsSettings
        SettingsSearchTarget.KEYBOARD_SHORTCUTS -> BiliPaiNavKey.KeyboardShortcutsSettings
        // Leaf entries such as GitHub, update check and Telegram are actions hosted by their
        // root category. They used to resolve to null, so tapping a valid search result did
        // nothing. Open the owning category; the normal settings callbacks remain authoritative.
        else -> resolveSettingsRootCategoryForSearchTarget(result.target)
            ?.let { category -> BiliPaiNavKey.SettingsCategory(category) }
    }
}
