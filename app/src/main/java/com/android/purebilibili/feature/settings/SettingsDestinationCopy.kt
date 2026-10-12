package com.android.purebilibili.feature.settings

/**
 * Single source of truth for settings destination copy (title + summary).
 *
 * Category detail entries, group rows, and the settings search index all render
 * destination names from here so the same destination never drifts into
 * different titles across entry paths.
 */
data class SettingsDestinationCopy(
    val title: String,
    val summary: String,
)

internal fun settingsDestinationCopy(target: SettingsSearchTarget): SettingsDestinationCopy = when (target) {
    // 场景级分组（分类页聚合入口）
    SettingsSearchTarget.INTERFACE_THEME -> SettingsDestinationCopy(
        title = "界面与主题",
        summary = "界面风格、颜色、字体和启动画面",
    )
    SettingsSearchTarget.NAVIGATION -> SettingsDestinationCopy(
        title = "导航与标签",
        summary = "调整底栏、分类标签和侧边栏",
    )
    SettingsSearchTarget.PLAYBACK_QUALITY -> SettingsDestinationCopy(
        title = "播放与画质",
        summary = "画质、倍速、字幕和连续播放",
    )
    SettingsSearchTarget.DATA_BACKUP -> SettingsDestinationCopy(
        title = "存储与备份",
        summary = "保存位置、缓存、设置分享和云备份",
    )
    SettingsSearchTarget.PRIVACY_PERMISSION -> SettingsDestinationCopy(
        title = "隐私与权限",
        summary = "历史记录、系统权限和屏蔽名单",
    )
    SettingsSearchTarget.DIAGNOSTICS -> SettingsDestinationCopy(
        title = "问题排查",
        summary = "记录和导出信息，帮助排查问题",
    )
    SettingsSearchTarget.ABOUT_SUPPORT -> SettingsDestinationCopy(
        title = "关于与支持",
        summary = "版本更新、使用帮助和官方渠道",
    )

    // 目的地页面与行入口
    SettingsSearchTarget.APPEARANCE -> SettingsDestinationCopy(
        title = "外观设置",
        summary = "选择界面风格、颜色、字体、显示大小和启动画面",
    )
    SettingsSearchTarget.PLAYBACK -> SettingsDestinationCopy(
        title = "播放设置",
        summary = "调整解码、清晰度、倍速、小窗和全屏操作",
    )
    SettingsSearchTarget.BOTTOM_BAR -> SettingsDestinationCopy(
        title = "导航设置",
        summary = "选择底栏和顶部入口，并调整图标、文字和顺序",
    )
    SettingsSearchTarget.ANIMATION -> SettingsDestinationCopy(
        title = "动画与触感",
        summary = "页面动画、振动反馈和玻璃效果",
    )
    SettingsSearchTarget.FULLSCREEN_GESTURE -> SettingsDestinationCopy(
        title = "全屏与手势",
        summary = "设置自动横屏、亮度音量手势和全屏返回方式",
    )
    SettingsSearchTarget.INTERACTION_COMMENT -> SettingsDestinationCopy(
        title = "互动与评论",
        summary = "调整评论显示、点赞操作、视频简介和内容入口",
    )
    SettingsSearchTarget.HOME_FEED -> SettingsDestinationCopy(
        title = "首页与推荐",
        summary = "卡片布局、首页壁纸、推荐内容和动态",
    )
    SettingsSearchTarget.PERMISSION -> SettingsDestinationCopy(
        title = "权限管理",
        summary = "查看每项系统权限的用途和当前授权状态",
    )
    SettingsSearchTarget.MESSAGE_NOTIFICATION -> SettingsDestinationCopy(
        title = "消息通知",
        summary = "后台消息、关注更新与开播提醒",
    )
    SettingsSearchTarget.BLOCKED_LIST -> SettingsDestinationCopy(
        title = "黑名单管理",
        summary = "管理已屏蔽的 UP 主",
    )
    SettingsSearchTarget.SETTINGS_SHARE -> SettingsDestinationCopy(
        title = "设置分享",
        summary = "分享设置，或从文件导入",
    )
    SettingsSearchTarget.WEBDAV_BACKUP -> SettingsDestinationCopy(
        title = "WebDAV 云备份",
        summary = "备份与恢复设置和插件",
    )
    SettingsSearchTarget.DOWNLOAD_PATH -> SettingsDestinationCopy(
        title = "下载位置",
        summary = "选择视频等下载内容的保存目录",
    )
    SettingsSearchTarget.IMAGE_SAVE_PATH -> SettingsDestinationCopy(
        title = "图片保存位置",
        summary = "选择动态图片、头像和评论图片的保存目录",
    )
    SettingsSearchTarget.CLEAR_CACHE -> SettingsDestinationCopy(
        title = "清除缓存",
        summary = "清理应用缓存并设置自动清理周期与容量上限",
    )
    SettingsSearchTarget.PLUGINS -> SettingsDestinationCopy(
        title = "插件中心",
        summary = "安装、启用和管理扩展功能",
    )
    SettingsSearchTarget.EXPORT_LOGS -> SettingsDestinationCopy(
        title = "导出日志",
        summary = "导出排查信息，隐私信息会先隐藏",
    )
    SettingsSearchTarget.OPEN_SOURCE_LICENSES -> SettingsDestinationCopy(
        title = "开源许可证",
        summary = "查看应用使用的开源项目及其许可协议",
    )
    SettingsSearchTarget.OPEN_SOURCE_HOME -> SettingsDestinationCopy(
        title = "开源主页",
        summary = "GitHub",
    )
    SettingsSearchTarget.CHECK_UPDATE -> SettingsDestinationCopy(
        title = "检查更新",
        summary = "立即检查是否有可用的新版本",
    )
    SettingsSearchTarget.VIEW_RELEASE_NOTES -> SettingsDestinationCopy(
        title = "查看更新日志",
        summary = "查看当前版本和最近版本的功能变化",
    )
    SettingsSearchTarget.REPLAY_ONBOARDING -> SettingsDestinationCopy(
        title = "重看使用须知",
        summary = "开源约定与官方渠道",
    )
    SettingsSearchTarget.TIPS -> SettingsDestinationCopy(
        title = "使用技巧",
        summary = "了解不容易发现的快捷操作和进阶功能",
    )
    SettingsSearchTarget.OPEN_LINKS -> SettingsDestinationCopy(
        title = "默认打开链接",
        summary = "用 BiliPai 打开 B 站链接",
    )
    SettingsSearchTarget.DONATE -> SettingsDestinationCopy(
        title = "打赏作者",
        summary = "支持项目后续持续开发和维护",
    )
    SettingsSearchTarget.TELEGRAM -> SettingsDestinationCopy(
        title = "Telegram 频道 / 交流群",
        summary = "@bilipai666 · @bilipai888",
    )
    SettingsSearchTarget.TWITTER -> SettingsDestinationCopy(
        title = "Twitter / X",
        summary = "@YangY_0x00",
    )
    SettingsSearchTarget.DISCLAIMER -> SettingsDestinationCopy(
        title = "发布渠道声明",
        summary = "GitHub · Telegram 频道与群组",
    )
}
