package com.android.purebilibili.feature.settings

import kotlinx.serialization.Serializable

@Serializable
enum class SettingsRootCategory(
    val title: String,
    val subtitle: String,
    val searchTarget: SettingsSearchTarget,
) {
    APPEARANCE_THEME(
        title = "外观与动画",
        subtitle = "主题、文字大小、图标、动画与振动",
        searchTarget = SettingsSearchTarget.INTERFACE_THEME,
    ),
    PLAYBACK_QUALITY(
        title = "播放设置",
        subtitle = "画质、倍速、字幕、连播、小窗和后台播放",
        searchTarget = SettingsSearchTarget.PLAYBACK_QUALITY,
    ),
    HOME_RECOMMENDATION(
        title = "首页与动态",
        subtitle = "视频卡片、推荐内容和动态页面",
        searchTarget = SettingsSearchTarget.HOME_FEED,
    ),
    NAVIGATION_INTERACTION(
        title = "导航布局",
        subtitle = "底栏、首页分类栏、搜索分类和平板侧边栏",
        searchTarget = SettingsSearchTarget.NAVIGATION,
    ),
    PRIVACY_PERMISSION(
        title = "隐私与权限",
        subtitle = "历史记录、身份验证、系统权限和屏蔽名单",
        searchTarget = SettingsSearchTarget.PRIVACY_PERMISSION,
    ),
    STORAGE_BACKUP(
        title = "下载与备份",
        subtitle = "保存位置、缓存、设置分享和 WebDAV 备份",
        searchTarget = SettingsSearchTarget.DATA_BACKUP,
    ),
    PLUGINS_EXTENSIONS(
        title = "插件中心",
        subtitle = "插件、装扮和扩展功能",
        searchTarget = SettingsSearchTarget.PLUGINS,
    ),
    SYSTEM_ABOUT(
        title = "帮助与关于",
        subtitle = "使用帮助、问题排查、更新和应用信息",
        searchTarget = SettingsSearchTarget.ABOUT_SUPPORT,
    ),

    FULLSCREEN_GESTURE("全屏与手势", "方向、滑动、播放按钮和截图", SettingsSearchTarget.FULLSCREEN_GESTURE),
    COMMENTS_CONTENT("评论与内容", "评论、点赞收藏、简介、笔记和图片浏览", SettingsSearchTarget.INTERACTION_COMMENT),
    MESSAGE_NOTIFICATION("消息通知", "私信、互动、关注更新和开播提醒", SettingsSearchTarget.MESSAGE_NOTIFICATION),
    VIDEO_DECODER("视频解码", "硬件解码与编码格式", SettingsSearchTarget.PLAYBACK),
    PLAYER_DIAGNOSTICS("问题排查", "应用日志、播放信息与兼容选项", SettingsSearchTarget.DIAGNOSTICS),
    GLASS_ADVANCED("玻璃高级调节", "模糊、对比度、彩光与形变", SettingsSearchTarget.ANIMATION),

    @Deprecated("仅用于恢复旧版本保存的设置导航状态")
    APPEARANCE_INTERACTION(
        title = "外观与主题",
        subtitle = "界面风格、主题、字体与图标",
        searchTarget = SettingsSearchTarget.INTERFACE_THEME,
    ),

    @Deprecated("仅用于恢复旧版本保存的设置导航状态")
    CONTENT_PLAYBACK(
        title = "播放与画质",
        subtitle = "解码、清晰度、字幕与播放行为",
        searchTarget = SettingsSearchTarget.PLAYBACK_QUALITY,
    ),

    @Deprecated("仅用于恢复旧版本保存的设置导航状态")
    PRIVACY_STORAGE(
        title = "隐私与权限",
        subtitle = "隐私模式、系统权限与黑名单",
        searchTarget = SettingsSearchTarget.PRIVACY_PERMISSION,
    ),
}

internal fun canonicalSettingsRootCategory(category: SettingsRootCategory): SettingsRootCategory =
    when (category) {
        SettingsRootCategory.APPEARANCE_INTERACTION -> SettingsRootCategory.APPEARANCE_THEME
        SettingsRootCategory.CONTENT_PLAYBACK -> SettingsRootCategory.PLAYBACK_QUALITY
        SettingsRootCategory.PRIVACY_STORAGE -> SettingsRootCategory.PRIVACY_PERMISSION
        SettingsRootCategory.FULLSCREEN_GESTURE,
        SettingsRootCategory.COMMENTS_CONTENT,
        SettingsRootCategory.VIDEO_DECODER -> SettingsRootCategory.PLAYBACK_QUALITY
        SettingsRootCategory.MESSAGE_NOTIFICATION -> SettingsRootCategory.PRIVACY_PERMISSION
        SettingsRootCategory.PLAYER_DIAGNOSTICS -> SettingsRootCategory.SYSTEM_ABOUT
        SettingsRootCategory.GLASS_ADVANCED -> SettingsRootCategory.APPEARANCE_THEME
        else -> category
    }

internal fun resolveSettingsRootCategoryOrder(): List<SettingsRootCategory> = listOf(
    SettingsRootCategory.APPEARANCE_THEME,
    SettingsRootCategory.PLAYBACK_QUALITY,
    SettingsRootCategory.HOME_RECOMMENDATION,
    SettingsRootCategory.NAVIGATION_INTERACTION,
    SettingsRootCategory.PRIVACY_PERMISSION,
    SettingsRootCategory.STORAGE_BACKUP,
    SettingsRootCategory.PLUGINS_EXTENSIONS,
    SettingsRootCategory.SYSTEM_ABOUT,
)

internal fun resolveTabletSettingsRootCategoryOrder(): List<SettingsRootCategory> =
    resolveSettingsRootCategoryOrder()

internal fun resolveSettingsRootCategoryForSearchTarget(
    target: SettingsSearchTarget,
): SettingsRootCategory? = when (target) {
    SettingsSearchTarget.INTERFACE_THEME,
    SettingsSearchTarget.APPEARANCE -> SettingsRootCategory.APPEARANCE_THEME

    SettingsSearchTarget.PLAYBACK_QUALITY,
    SettingsSearchTarget.PLAYBACK,
    SettingsSearchTarget.FULLSCREEN_GESTURE,
    SettingsSearchTarget.INTERACTION_COMMENT -> SettingsRootCategory.PLAYBACK_QUALITY

    SettingsSearchTarget.HOME_FEED -> SettingsRootCategory.HOME_RECOMMENDATION

    SettingsSearchTarget.NAVIGATION,
    SettingsSearchTarget.BOTTOM_BAR -> SettingsRootCategory.NAVIGATION_INTERACTION

    SettingsSearchTarget.PRIVACY_PERMISSION,
    SettingsSearchTarget.PERMISSION,
    SettingsSearchTarget.SPACE_PRIVACY,
    SettingsSearchTarget.LOGIN_ACTIVITY,
    SettingsSearchTarget.BLOCKED_LIST,
    SettingsSearchTarget.MESSAGE_NOTIFICATION -> SettingsRootCategory.PRIVACY_PERMISSION

    // 动画与触感页的导航父级是外观设置,搜索归属保持一致
    SettingsSearchTarget.ANIMATION -> SettingsRootCategory.APPEARANCE_THEME

    SettingsSearchTarget.DATA_BACKUP,
    SettingsSearchTarget.SETTINGS_SHARE,
    SettingsSearchTarget.WEBDAV_BACKUP,
    SettingsSearchTarget.DOWNLOAD_PATH,
    SettingsSearchTarget.IMAGE_SAVE_PATH,
    SettingsSearchTarget.CLEAR_CACHE -> SettingsRootCategory.STORAGE_BACKUP

    SettingsSearchTarget.PLUGINS -> SettingsRootCategory.PLUGINS_EXTENSIONS
    SettingsSearchTarget.NEARBY_HANDOFF -> SettingsRootCategory.PLAYBACK_QUALITY

    SettingsSearchTarget.DIAGNOSTICS,
    SettingsSearchTarget.EXPORT_LOGS,
    SettingsSearchTarget.ABOUT_SUPPORT,
    SettingsSearchTarget.OPEN_SOURCE_LICENSES,
    SettingsSearchTarget.OPEN_SOURCE_HOME,
    SettingsSearchTarget.CHECK_UPDATE,
    SettingsSearchTarget.VIEW_RELEASE_NOTES,
    SettingsSearchTarget.REPLAY_ONBOARDING,
    SettingsSearchTarget.DISCLAIMER,
    SettingsSearchTarget.TELEGRAM,
    SettingsSearchTarget.TWITTER,
    SettingsSearchTarget.DONATE,
    SettingsSearchTarget.TIPS,
    SettingsSearchTarget.OPEN_LINKS -> SettingsRootCategory.SYSTEM_ABOUT
}

internal fun isSceneSettingsSearchTarget(target: SettingsSearchTarget): Boolean = target in setOf(
    SettingsSearchTarget.INTERFACE_THEME,
    SettingsSearchTarget.PLAYBACK_QUALITY,
    SettingsSearchTarget.HOME_FEED,
    SettingsSearchTarget.NAVIGATION,
    SettingsSearchTarget.PRIVACY_PERMISSION,
    SettingsSearchTarget.DATA_BACKUP,
    SettingsSearchTarget.DIAGNOSTICS,
)
