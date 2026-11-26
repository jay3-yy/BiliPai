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
    OPEN_APPEARANCE_SETTINGS,
    OPEN_HOME_SETTINGS,
    OPEN_SETTINGS,
}

internal data class UpdateHighlight(
    val id: String,
    val introducedVersionCode: Int,
    val title: String,
    val description: String,
    val action: UpdateHighlightAction? = null,
    val badge: String? = null,
    val actionLabel: String? = null,
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
    UpdateHighlightAction.OPEN_APPEARANCE_SETTINGS,
    UpdateHighlightAction.OPEN_HOME_SETTINGS,
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
    UpdateHighlightAction.OPEN_APPEARANCE_SETTINGS,
    UpdateHighlightAction.OPEN_HOME_SETTINGS,
    UpdateHighlightAction.OPEN_SETTINGS,
    null -> false
}
