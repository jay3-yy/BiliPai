package com.android.purebilibili.feature.settings.highlights

import com.android.purebilibili.core.store.AppThemeSettings
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.materialkolor.PaletteStyle

internal const val MAX_RELEASE_HIGHLIGHTS = 9

internal enum class UpdateHighlightAction {
    PREVIEW_CMF,
    OPEN_DONATE,
    OPEN_PLUGINS_SETTINGS,
    OPEN_DOWNLOAD_LIST,
    OPEN_ICON_SETTINGS,
    OPEN_LISTEN_VIDEO,
    OPEN_PERSONAL_RECAP,
    OPEN_WATCH_LATER,
    OPEN_CUSTOM_PUSH_SETTINGS,
    OPEN_PLAYBACK_SETTINGS,
    OPEN_DECODER_SETTINGS,
    OPEN_APPEARANCE_SETTINGS,
    OPEN_IMAGE_DISPLAY_SETTINGS,
    OPEN_HOME_SETTINGS,
    OPEN_STORAGE_SETTINGS,
    OPEN_SETTINGS,
}

internal enum class UpdateHighlightIcon {
    SPEED,
    CONTENT_CUT,
    CAST,
    WIDGETS,
    AUTO_FIX_HIGH,
    CLEANING_SERVICES,
    PALETTE,
    FAVORITE,
    DOWNLOAD,
    HEADPHONES,
    HISTORY,
    WATCH_LATER,
    NOTIFICATIONS,
    PLAY_CIRCLE,
    HOME,
    TUNE,
    AUTO_AWESOME,
}

internal data class UpdateHighlight(
    val id: String,
    val introducedVersionCode: Int,
    val title: String,
    val description: String,
    val action: UpdateHighlightAction? = null,
    val badge: String? = null,
    val actionLabel: String? = null,
    val icon: UpdateHighlightIcon? = null,
)

/**
 * Release highlights catalog.
 *
 * Each release adds entries tagged with its `introducedVersionCode` (matching `app/build.gradle.kts`).
 * `latestReleaseHighlights(versionCode)` always resolves only the highest `introducedVersionCode <= versionCode`,
 * so every new version automatically replaces the previous release's preview cards.
 */
internal val updateHighlightCatalog = listOf(
    UpdateHighlight(
        id = "cmf_spec_2026",
        introducedVersionCode = 487,
        title = "试试新的 CMF 配色",
        description = "新增 SPEC_2026，让主题色、容器和背景有新的搭配。先看看深浅色效果，再决定是否使用。",
        action = UpdateHighlightAction.PREVIEW_CMF,
        badge = "主题配色",
        actionLabel = "预览配色",
    ),
    UpdateHighlight(
        id = "donate_qr_refresh_617",
        introducedVersionCode = 617,
        title = "支持与赞赏体验升级",
        description = "感谢您的支持！",
        action = UpdateHighlightAction.OPEN_DONATE,
        badge = "赞赏支持",
        actionLabel = "打开赞赏",
    ),
    UpdateHighlight(
        id = "cdn_parallel_chunk_617",
        introducedVersionCode = 617,
        title = "海外播放多节点并行分块加速",
        description = "新增多 CDN 镜像池并行分块下载与自适应并发调度，弱网与跨区高码率播放更稳。",
        action = UpdateHighlightAction.OPEN_PLUGINS_SETTINGS,
        badge = "播放加速",
        actionLabel = "插件中心",
    ),
    UpdateHighlight(
        id = "offline_cache_refresh_617",
        introducedVersionCode = 617,
        title = "离线缓存弹幕更新与分 P 合一",
        description = "支持刷新已缓存视频弹幕、合并展示多分 P 视频，卡片右下角可一键回到原视频。",
        action = UpdateHighlightAction.OPEN_DOWNLOAD_LIST,
        badge = "离线缓存",
        actionLabel = "离线缓存",
    ),
    UpdateHighlight(
        id = "custom_icon_zero_diff_617",
        introducedVersionCode = 617,
        title = "自定义图标配色与桌面零色差",
        description = "升级感知色差匹配，支持零色差色板吸附、双色互换以及桌面零色差快捷方式。",
        action = UpdateHighlightAction.OPEN_ICON_SETTINGS,
        badge = "图标定制",
        actionLabel = "图标设置",
    ),
    UpdateHighlight(
        id = "listen_ambient_winddown_617",
        introducedVersionCode = 617,
        title = "听视频白噪音与睡眠定时渐弱",
        description = "新增环境声伴听、唱片转盘动效与睡眠定时音量渐弱，夜间助眠收听更柔和。",
        action = UpdateHighlightAction.OPEN_LISTEN_VIDEO,
        badge = "听视频",
        actionLabel = "听视频",
    ),
    UpdateHighlight(
        id = "personal_recap_keepsake_617",
        introducedVersionCode = 617,
        title = "个人回顾、完结纪念与历史记忆卡",
        description = "新增观影回顾与分享卡片、番剧/合集完结纪念收藏，以及历史页记忆卡与情绪微件。",
        action = UpdateHighlightAction.OPEN_PERSONAL_RECAP,
        badge = "回忆纪念",
        actionLabel = "个人回顾",
    ),
    UpdateHighlight(
        id = "mealtime_gacha_watchlater_617",
        introducedVersionCode = 617,
        title = "饭点扭蛋与今日推荐口味偏好",
        description = "不知道看什么时可一键抽取下饭视频，并支持自定义今日推荐的口味偏好。",
        action = UpdateHighlightAction.OPEN_WATCH_LATER,
        badge = "稍后再看",
        actionLabel = "稍后再看",
    ),
    UpdateHighlight(
        id = "custom_push_nearby_617",
        introducedVersionCode = 617,
        title = "自定义推送与跨设备附近接力",
        description = "支持扫码配置自定义推送、跨设备附近接力续播与设置备份同步。",
        action = UpdateHighlightAction.OPEN_CUSTOM_PUSH_SETTINGS,
        badge = "推送接力",
        actionLabel = "推送设置",
    ),
    UpdateHighlight(
        id = "appearance_scene_recipes_617",
        introducedVersionCode = 617,
        title = "外观场景配方与全局交互打磨",
        description = "新增外观场景配方一键切换，优化分 P / 合集平滑排序、直播全屏手势与开屏首帧衔接。",
        action = UpdateHighlightAction.OPEN_APPEARANCE_SETTINGS,
        badge = "外观体验",
        actionLabel = "外观设置",
    ),
    UpdateHighlight(
        id = "donate_qr_refresh_619",
        introducedVersionCode = 619,
        title = "支持与赞赏体验升级",
        description = "感谢您的支持！",
        action = UpdateHighlightAction.OPEN_DONATE,
        badge = "赞赏支持",
        actionLabel = "打开赞赏",
    ),
    UpdateHighlight(
        id = "mini_player_polish_619",
        introducedVersionCode = 619,
        title = "全局小窗交互升级与全屏切换修复",
        description = "小窗控件平滑淡入淡出、居中高对比播放键、时间/直播角标与贴边横向拉出；修复首次切小窗不播、展开全屏尺寸异常及退出全屏不恢复小窗。",
        action = UpdateHighlightAction.OPEN_PLAYBACK_SETTINGS,
        badge = "小窗播放",
        actionLabel = "播放设置",
    ),
    UpdateHighlight(
        id = "home_refresh_count_619",
        introducedVersionCode = 619,
        title = "回归首页刷新条数与双指捏合优化",
        description = "首页设置回归单次推荐刷新条数调节（10–30 条），同步优化首页列表、播放器与图片双指捏合缩放阻尼回弹手感。",
        action = UpdateHighlightAction.OPEN_HOME_SETTINGS,
        badge = "首页体验",
        actionLabel = "首页设置",
    ),
    UpdateHighlight(
        id = "danmaku_comment_reply_619",
        introducedVersionCode = 619,
        title = "横屏弹幕点击对齐与评论隐藏搜索词",
        description = "修复横屏全屏弹幕点击位置向下偏移问题；评论区新增隐藏搜索词跳转链接开关，并同步优化动态评论气泡图标。",
        action = UpdateHighlightAction.OPEN_PLAYBACK_SETTINGS,
        badge = "弹幕评论",
        actionLabel = "播放设置",
    ),
    UpdateHighlight(
        id = "profile_partition_glass_619",
        introducedVersionCode = 619,
        title = "个人主页与分区侧栏液态玻璃打磨",
        description = "个人主页顶部返回与更多按钮接入 Miuix Backdrop 液态玻璃圆钮，收藏夹卡片跟随主题强调色；优化分区侧边栏纵向胶囊采样对齐。",
        action = UpdateHighlightAction.OPEN_APPEARANCE_SETTINGS,
        badge = "液态玻璃",
        actionLabel = "外观设置",
    ),
    UpdateHighlight(
        id = "nearby_glass_surface_619",
        introducedVersionCode = 619,
        title = "附近接力液态玻璃视觉升级",
        description = "附近设备面板与收发阶段浮层全面接入液态玻璃卡片与胶囊表面，兼顾 MD3 与 Miuix 原生一致性。",
        action = UpdateHighlightAction.OPEN_CUSTOM_PUSH_SETTINGS,
        badge = "附近接力",
        actionLabel = "推送设置",
    ),
    UpdateHighlight(
        id = "story_widget_fixes_619",
        introducedVersionCode = 619,
        title = "竖屏流与桌面播放微件稳定性修复",
        description = "完善竖屏流画中画及退出跳详情衔接，修复桌面播放微件在 BroadcastReceiver 环境下绑定媒体服务异常的问题。",
        action = UpdateHighlightAction.OPEN_PLAYBACK_SETTINGS,
        badge = "稳定性",
        actionLabel = "播放设置",
    ),
    UpdateHighlight(
        id = "decoder_weak_network_622",
        introducedVersionCode = 622,
        title = "软硬解提速与弱网一键降级",
        description = "优化硬解识别与软解择优排序；视频、番剧、直播与竖屏卡顿或软解过载时，支持一键切硬解、降画质或切线路。",
        action = UpdateHighlightAction.OPEN_DECODER_SETTINGS,
        badge = "流畅播放",
        actionLabel = "解码设置",
        icon = UpdateHighlightIcon.SPEED,
    ),
    UpdateHighlight(
        id = "video_tools_ocr_clip_622",
        introducedVersionCode = 622,
        title = "画面识字、批注与片段剪辑",
        description = "暂停看片时在播放器「更多」菜单即可识别画面文字、涂鸦批注或截取精彩片段保存到本地。",
        action = null,
        badge = "创作工具",
        icon = UpdateHighlightIcon.CONTENT_CUT,
    ),
    UpdateHighlight(
        id = "cast_output_switcher_622",
        introducedVersionCode = 622,
        title = "投屏与输出设备切换升级",
        description = "接入系统媒体输出切换器并升级投屏控制，在手机、音箱与电视之间流转播放更顺手。",
        action = UpdateHighlightAction.OPEN_PLUGINS_SETTINGS,
        badge = "投屏流转",
        actionLabel = "插件中心",
        icon = UpdateHighlightIcon.CAST,
    ),
    UpdateHighlight(
        id = "widgets_tiles_wear_622",
        introducedVersionCode = 622,
        title = "追番微件、快捷磁贴与手表协同",
        description = "可在系统桌面微件、下拉快捷磁贴或 Wear OS 手表端添加稍后再看、追番提醒与远程播放控制。",
        action = null,
        badge = "桌面穿戴",
        icon = UpdateHighlightIcon.WIDGETS,
    ),
    UpdateHighlight(
        id = "hdr_multitask_haptics_622",
        introducedVersionCode = 622,
        title = "Ultra HDR 预览、多窗口拖放与触感升级",
        description = "支持动态图 Ultra HDR 呈现、大屏多窗口与跨应用拖放，以及更细腻的交互触感反馈。",
        action = UpdateHighlightAction.OPEN_IMAGE_DISPLAY_SETTINGS,
        badge = "视觉交互",
        actionLabel = "图片显示",
        icon = UpdateHighlightIcon.AUTO_FIX_HIGH,
    ),
    UpdateHighlight(
        id = "cache_clean_polish_622",
        introducedVersionCode = 622,
        title = "全新缓存清理动效与细节打磨",
        description = "重绘缓存清理环形进度与动效，发现音乐新增封面显示，并优化图片清晰度策略与更新弹窗体验。",
        action = UpdateHighlightAction.OPEN_STORAGE_SETTINGS,
        badge = "存储体验",
        actionLabel = "清理缓存",
        icon = UpdateHighlightIcon.CLEANING_SERVICES,
    ),
)

internal data class UpdateHighlightHistory(
    val lastVersionCode: Int = 0,
    val pendingIds: Set<String> = emptySet(),
    val handledIds: Set<String> = emptySet(),
)

/** A first install establishes a baseline. Legacy upgraded installs can receive this first release.
 * A downgrade never lowers the high-water mark or erases previous decisions. */
internal fun prepareUpdateHighlights(
    history: UpdateHighlightHistory,
    currentVersionCode: Int,
    isExistingInstall: Boolean,
    catalog: List<UpdateHighlight> = updateHighlightCatalog,
): UpdateHighlightHistory {
    if (currentVersionCode <= history.lastVersionCode) return history
    val previous = history.lastVersionCode
    val candidates = catalog.filter { it.introducedVersionCode <= currentVersionCode &&
        (previous == 0 || it.introducedVersionCode > previous) }.map { it.id }.toSet()
    if (previous == 0 && !isExistingInstall) return UpdateHighlightHistory(
        lastVersionCode = currentVersionCode, handledIds = history.handledIds + candidates,
    )
    return history.copy(
        lastVersionCode = currentVersionCode,
        pendingIds = (history.pendingIds + candidates) - history.handledIds,
    )
}

internal fun latestReleaseHighlights(versionCode: Int, catalog: List<UpdateHighlight> = updateHighlightCatalog): List<UpdateHighlight> {
    val available = catalog.filter { it.introducedVersionCode <= versionCode }
    val latest = available.maxOfOrNull { it.introducedVersionCode } ?: return emptyList()
    return available.filter { it.introducedVersionCode == latest }.take(MAX_RELEASE_HIGHLIGHTS)
}

/** Resolves only the latest release's pending highlights so each version update cleanly replaces older previews. */
internal fun resolveAutomaticReleaseHighlights(
    versionCode: Int,
    pendingIds: Set<String>,
    settings: AppThemeSettings,
    catalog: List<UpdateHighlight> = updateHighlightCatalog,
): List<UpdateHighlight> = latestReleaseHighlights(versionCode, catalog).filter {
    it.id in pendingIds &&
        canPreviewUpdateHighlight(it, settings) &&
        !isUpdateHighlightAlreadyEnabled(it, settings)
}

/** 去掉 `-debug` / `-dev` / `-smooth` 等构建变体后缀，返回面向用户的纯净展示版本号。 */
internal fun formatDisplayVersionName(versionName: String): String {
    val trimmed = versionName.trim()
    return trimmed.substringBefore('-').ifBlank { trimmed }
}

internal fun isHomeRouteForAutomaticHighlights(
    isMainHostTop: Boolean,
    isHomeBottomTab: Boolean,
    currentRoute: String?,
    homeRoute: String,
): Boolean = (isMainHostTop && isHomeBottomTab) || currentRoute == homeRoute

internal fun canPreviewUpdateHighlight(highlight: UpdateHighlight, settings: AppThemeSettings): Boolean = when (highlight.action) {
    UpdateHighlightAction.PREVIEW_CMF -> settings.uiStyle == AppUiStyle.MATERIAL3 && settings.md3ColorSource == Md3ColorSource.CUSTOM
    UpdateHighlightAction.OPEN_DONATE,
    UpdateHighlightAction.OPEN_PLUGINS_SETTINGS,
    UpdateHighlightAction.OPEN_DOWNLOAD_LIST,
    UpdateHighlightAction.OPEN_ICON_SETTINGS,
    UpdateHighlightAction.OPEN_LISTEN_VIDEO,
    UpdateHighlightAction.OPEN_PERSONAL_RECAP,
    UpdateHighlightAction.OPEN_WATCH_LATER,
    UpdateHighlightAction.OPEN_CUSTOM_PUSH_SETTINGS,
    UpdateHighlightAction.OPEN_PLAYBACK_SETTINGS,
    UpdateHighlightAction.OPEN_DECODER_SETTINGS,
    UpdateHighlightAction.OPEN_APPEARANCE_SETTINGS,
    UpdateHighlightAction.OPEN_IMAGE_DISPLAY_SETTINGS,
    UpdateHighlightAction.OPEN_HOME_SETTINGS,
    UpdateHighlightAction.OPEN_STORAGE_SETTINGS,
    UpdateHighlightAction.OPEN_SETTINGS,
    null -> true
}

internal fun isUpdateHighlightAlreadyEnabled(highlight: UpdateHighlight, settings: AppThemeSettings): Boolean = when (highlight.action) {
    UpdateHighlightAction.PREVIEW_CMF -> canPreviewUpdateHighlight(highlight, settings) &&
        !settings.seedPaletteStyle.enabled && settings.colorStyle is PaletteStyle.Cmf
    UpdateHighlightAction.OPEN_DONATE,
    UpdateHighlightAction.OPEN_PLUGINS_SETTINGS,
    UpdateHighlightAction.OPEN_DOWNLOAD_LIST,
    UpdateHighlightAction.OPEN_ICON_SETTINGS,
    UpdateHighlightAction.OPEN_LISTEN_VIDEO,
    UpdateHighlightAction.OPEN_PERSONAL_RECAP,
    UpdateHighlightAction.OPEN_WATCH_LATER,
    UpdateHighlightAction.OPEN_CUSTOM_PUSH_SETTINGS,
    UpdateHighlightAction.OPEN_PLAYBACK_SETTINGS,
    UpdateHighlightAction.OPEN_DECODER_SETTINGS,
    UpdateHighlightAction.OPEN_APPEARANCE_SETTINGS,
    UpdateHighlightAction.OPEN_IMAGE_DISPLAY_SETTINGS,
    UpdateHighlightAction.OPEN_HOME_SETTINGS,
    UpdateHighlightAction.OPEN_STORAGE_SETTINGS,
    UpdateHighlightAction.OPEN_SETTINGS,
    null -> false
}
