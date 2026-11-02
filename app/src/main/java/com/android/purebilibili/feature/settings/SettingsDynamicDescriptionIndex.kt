package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.store.*
import com.android.purebilibili.feature.screenshot.AppScreenshotCaptureMode
import com.android.purebilibili.feature.screenshot.AppScreenshotGestureMode

// State-dependent descriptions use every supported state so search doesn't depend on a toggle.
private val settingsDynamicDescriptionIndex = mapOf(
    "appearance.theme_role_overrides.enabled" to listOf(
        resolveAdvancedPaletteSubtitle(com.android.purebilibili.core.theme.AppUiStyle.MATERIAL3),
        resolveAdvancedPaletteSubtitle(com.android.purebilibili.core.theme.AppUiStyle.MIUIX),
    ).joinToString("；"),
    "navigation.visibility_mode" to SettingsManager.BottomBarVisibilityMode.entries.joinToString("；") { it.description },
    "notification.enabled" to "应用关闭后定期检查新消息；未登录时不检查。请先开启通知权限；系统通知或通知渠道已关闭",
    "playback.video_codec_preference" to "兼容设备最多，其他编码无法播放时优先尝试；画质与流量更平衡，多数新设备推荐；更节省流量，但需要较新的设备支持",
    "playback.video_second_codec_preference" to "兼容设备最多，其他编码无法播放时优先尝试；画质与流量更平衡，多数新设备推荐；更节省流量，但需要较新的设备支持",
    "playback.data_saver_mode" to SettingsManager.DataSaverMode.entries.joinToString("；") { it.description },
    "playback.subtitle_auto_preference" to "默认关闭字幕；自动开启可用字幕；仅自动启用非 AI 字幕；静音时可自动启用 AI 字幕；只开启中文字幕（含 AI 中文），没有中文字幕时不开启",
    "playback.danmaku_cloud_sync_enabled" to listOf(true, false).joinToString("；") {
        com.android.purebilibili.feature.video.danmaku.resolveDanmakuCloudSyncToggleSubtitle(it)
    },
    "playback.portrait_player_collapse_mode" to PortraitPlayerCollapseMode.entries.joinToString("；") { it.description },
    "playback.auto_rotate_enabled" to "跟随设备方向旋转播放页，并保留平板/展开态折叠屏分栏布局；跟随设备方向自动进入/退出全屏，不受系统旋转锁影响",
    "playback.horizontal_adaptation_enabled" to "横屏时使用大屏布局，适合平板和折叠屏；主要适用于平板和折叠屏",
    "playback.fullscreen_mode" to FullscreenMode.entries.joinToString("；") { it.description } + "；自动横竖屏仅旋转播放页，手动全屏时使用此方向；已开启自动横竖屏，将跟随设备方向自动进退全屏",
    "playback.fullscreen_aspect_ratio" to FullscreenAspectRatio.entries.joinToString("；") { it.description },
    "playback.auto_exit_fullscreen_mode" to AutoExitFullscreenMode.entries.joinToString("；") { it.subtitle },
    "playback.app_screenshot_gesture_mode" to AppScreenshotGestureMode.entries.joinToString("；") { it.description },
    "playback.app_screenshot_capture_mode" to AppScreenshotCaptureMode.entries.joinToString("；") { it.description },
    "playback.bottom_progress_behavior" to BottomProgressBehavior.entries.joinToString("；") { it.description },
    "help.auto_check_update_enabled" to listOf(true, false).joinToString("；") { resolveAutoCheckUpdateSubtitle(it) },
    // 副标题来自字符串资源或运行时插值,静态索引扫描不到,这里按状态手工登记
    "appearance.back_to_top_button_enabled" to "已保存按钮位置，长按可拖动；在长页面显示回顶按钮",
    "appearance.home_publish_time_visible" to "发布时间显示在首页视频卡片上；隐藏发布时间，卡片信息更紧凑",
    "notification.resident_enabled" to "保持应用后台运行以更及时地收到通知；会增加耗电，系统仍可能终止；需要先开启后台消息通知和可用的通知权限",
)

internal fun settingSearchDescription(settingId: String): String = listOfNotNull(
    settingsStaticDescriptionIndex[settingId],
    settingsDynamicDescriptionIndex[settingId],
).joinToString("；")
