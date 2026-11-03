package com.android.purebilibili.feature.settings

internal fun settingItemPath(item: PlaybackSettingItem): String {
    val owner = when (item.page) {
        SettingsRootCategory.VIDEO_DECODER,
        SettingsRootCategory.COMMENTS_CONTENT,
        SettingsRootCategory.FULLSCREEN_GESTURE -> SettingsRootCategory.PLAYBACK_QUALITY.title
        SettingsRootCategory.PLAYER_DIAGNOSTICS -> SettingsRootCategory.SYSTEM_ABOUT.title
        else -> item.page.title
    }
    val page = when {
        item.page == SettingsRootCategory.PLAYER_DIAGNOSTICS -> "问题排查"
        item.target == SettingsSearchTarget.ANIMATION -> "动画与触感"
        item.target == SettingsSearchTarget.PLAYBACK -> "播放设置"
        else -> null
    }
    val section = when (item.sectionKey) {
        SettingsSearchFocusIds.PLAYBACK_DECODER -> "视频解码"
        SettingsSearchFocusIds.PLAYBACK_SPEED -> "播放速度"
        SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER -> "小窗与后台"
        SettingsSearchFocusIds.PLAYBACK_NETWORK -> "网络与画质"
        SettingsSearchFocusIds.PLAYBACK_DATA_SAVER -> "省流量"
        SettingsSearchFocusIds.PLAYBACK_DEBUG -> "播放器"
        SettingsSearchFocusIds.PLAYBACK_INTERACTION -> "字幕与连播"
        SettingsSearchFocusIds.PLAYBACK_COMMENTS -> "互动与评论"
        SettingsSearchFocusIds.PLAYBACK_FULLSCREEN -> "全屏与手势"
        "appearance_display_mode" -> "界面与效果"
        "appearance_theme_color" -> "主题与色彩"
        "appearance_text_size" -> "文字与大小"
        "appearance_splash_icon" -> "开屏与图标"
        "appearance_language" -> "语言"
        "home_list" -> "首页与列表"
        "home_wallpaper" -> "首页壁纸"
        "home_recommendation" -> "内容与推荐流"
        "home_browsing" -> "浏览交互与手势"
        "home_options" -> "推荐与动态"
        "animation_page" -> "动画与振动"
        "animation_card" -> "过渡动画"
        "animation_glass" -> "液态玻璃与磨砂"
        "navigation_behavior" -> "导航行为"
        "navigation_top_tabs" -> "顶部标签"
        "navigation_tablet" -> "平板导航"
        "diagnostics_options" -> "应用日志"
        "help_options" -> if (item.settingId == "help.easter_egg_enabled") "使用与反馈" else "更新"
        else -> null
    }
    return listOfNotNull("设置", owner, page, section).joinToString(" / ")
}
