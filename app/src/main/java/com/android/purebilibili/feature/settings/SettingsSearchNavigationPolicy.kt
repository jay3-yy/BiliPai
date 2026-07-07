package com.android.purebilibili.feature.settings

import com.android.purebilibili.navigation3.BiliPaiNavKey

internal fun resolveSettingsSearchNavigation(result: SettingsSearchResult): BiliPaiNavKey? {
    resolveSettingsSceneDetailFocus(result.target)?.let { detailFocus ->
        return when (detailFocus.target) {
            SettingsSearchTarget.APPEARANCE -> BiliPaiNavKey.AppearanceSettings
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
        SettingsSearchTarget.PLUGINS -> BiliPaiNavKey.PluginsSettings()
        SettingsSearchTarget.SETTINGS_SHARE -> BiliPaiNavKey.SettingsShare
        SettingsSearchTarget.WEBDAV_BACKUP -> BiliPaiNavKey.WebDavBackup
        SettingsSearchTarget.OPEN_SOURCE_LICENSES -> BiliPaiNavKey.OpenSourceLicenses
        SettingsSearchTarget.TIPS -> BiliPaiNavKey.TipsSettings
        else -> null
    }
}
