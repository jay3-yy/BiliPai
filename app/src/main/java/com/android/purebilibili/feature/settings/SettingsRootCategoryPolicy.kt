package com.android.purebilibili.feature.settings

import kotlinx.serialization.Serializable

@Serializable
enum class SettingsRootCategory(
    val title: String,
    val subtitle: String,
    val searchTarget: SettingsSearchTarget
) {
    APPEARANCE_INTERACTION(
        title = "外观与交互",
        subtitle = "主题、动效、导航、全屏与手势",
        searchTarget = SettingsSearchTarget.INTERFACE_THEME
    ),
    CONTENT_PLAYBACK(
        title = "内容与播放",
        subtitle = "首页展示、推荐流、播放画质、评论互动",
        searchTarget = SettingsSearchTarget.HOME_FEED
    ),
    PRIVACY_STORAGE(
        title = "隐私与存储",
        subtitle = "设置分享、WebDAV、下载缓存、权限与黑名单",
        searchTarget = SettingsSearchTarget.DATA_BACKUP
    ),
    SYSTEM_ABOUT(
        title = "系统与关于",
        subtitle = "插件、诊断、版本、更新、社群与支持",
        searchTarget = SettingsSearchTarget.DIAGNOSTICS
    )
}

internal fun resolveSettingsRootCategoryOrder(): List<SettingsRootCategory> = listOf(
    SettingsRootCategory.APPEARANCE_INTERACTION,
    SettingsRootCategory.CONTENT_PLAYBACK,
    SettingsRootCategory.PRIVACY_STORAGE,
    SettingsRootCategory.SYSTEM_ABOUT
)

internal fun resolveTabletSettingsRootCategoryOrder(): List<SettingsRootCategory> =
    resolveSettingsRootCategoryOrder()

internal fun resolveSettingsRootCategoryForSearchTarget(
    target: SettingsSearchTarget
): SettingsRootCategory? = when (target) {
    SettingsSearchTarget.INTERFACE_THEME,
    SettingsSearchTarget.APPEARANCE,
    SettingsSearchTarget.ANIMATION,
    SettingsSearchTarget.NAVIGATION,
    SettingsSearchTarget.BOTTOM_BAR,
    SettingsSearchTarget.FULLSCREEN_GESTURE -> SettingsRootCategory.APPEARANCE_INTERACTION

    SettingsSearchTarget.HOME_FEED,
    SettingsSearchTarget.PLAYBACK_QUALITY,
    SettingsSearchTarget.PLAYBACK,
    SettingsSearchTarget.INTERACTION_COMMENT -> SettingsRootCategory.CONTENT_PLAYBACK

    SettingsSearchTarget.DATA_BACKUP,
    SettingsSearchTarget.SETTINGS_SHARE,
    SettingsSearchTarget.WEBDAV_BACKUP,
    SettingsSearchTarget.DOWNLOAD_PATH,
    SettingsSearchTarget.IMAGE_SAVE_PATH,
    SettingsSearchTarget.CLEAR_CACHE,
    SettingsSearchTarget.PRIVACY_PERMISSION,
    SettingsSearchTarget.PERMISSION,
    SettingsSearchTarget.BLOCKED_LIST -> SettingsRootCategory.PRIVACY_STORAGE

    SettingsSearchTarget.DIAGNOSTICS,
    SettingsSearchTarget.PLUGINS,
    SettingsSearchTarget.EXPORT_LOGS,
    SettingsSearchTarget.ABOUT_SUPPORT,
    SettingsSearchTarget.OPEN_SOURCE_LICENSES,
    SettingsSearchTarget.OPEN_SOURCE_HOME,
    SettingsSearchTarget.CHECK_UPDATE,
    SettingsSearchTarget.VIEW_RELEASE_NOTES,
    SettingsSearchTarget.REPLAY_ONBOARDING,
    SettingsSearchTarget.TIPS,
    SettingsSearchTarget.OPEN_LINKS,
    SettingsSearchTarget.DISCLAIMER,
    SettingsSearchTarget.TELEGRAM,
    SettingsSearchTarget.TWITTER,
    SettingsSearchTarget.DONATE -> SettingsRootCategory.SYSTEM_ABOUT
}

internal fun isSceneSettingsSearchTarget(target: SettingsSearchTarget): Boolean = target in setOf(
    SettingsSearchTarget.INTERFACE_THEME,
    SettingsSearchTarget.HOME_FEED,
    SettingsSearchTarget.PLAYBACK_QUALITY,
    SettingsSearchTarget.NAVIGATION,
    SettingsSearchTarget.DATA_BACKUP,
    SettingsSearchTarget.DIAGNOSTICS
)
