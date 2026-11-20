package com.android.purebilibili.feature.video.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.purebilibili.core.store.FullscreenActionCountSettings
import com.android.purebilibili.feature.nearby.NEARBY_HANDOFF_ENABLED
import com.android.purebilibili.feature.nearby.NEARBY_TITLE
import com.android.purebilibili.feature.settings.playbackSettingTitle

internal enum class FullscreenMoreMenuEntry(override val id: String, override val title: String) : PlayerMenuEntry {
    NEARBY_HANDOFF("nearby_handoff", NEARBY_TITLE),
    WATCH_LATER("watch_later", "稍后再看（添加 / 移出）"),
    NOTES("notes", "查看笔记"),
    DOWNLOAD("download", "离线缓存"),
    SAVE_COVER("save_cover", "保存封面"),
    SLEEP_TIMER("sleep_timer", "定时关闭"),
    PLAY_URL("play_url", "播放地址"),
    CHAPTERS("chapters", playbackSettingTitle("playback.chapter_display_mode")),
    CENTER_PLAYBACK("center_playback", playbackSettingTitle("playback.show_center_playback_button")),
    RELOAD("reload", "重载视频"),
    ENHANCEMENT("enhancement", "画质增强"),
    VOLUME("volume", "播放器音量"),
    CDN("cdn", "CDN 设置"),
    TRANSFORM("transform", "画面变换"),
    QUALITY("quality", "选择画质"),
    AUDIO_QUALITY("audio_quality", "选择音质"),
    CODEC("codec", "首选编码格式"),
    PLAYBACK_ORDER("playback_order", "播完后行为"),
    DANMAKU_LIST("danmaku_list", "弹幕列表"),
    DANMAKU_SETTINGS("danmaku_settings", "弹幕设置"),
    SUBTITLE_SETTINGS("subtitle_settings", "字幕设置"),
    LOAD_SUBTITLES("load_subtitles", "加载字幕"),
    SAVE_SUBTITLES("save_subtitles", "保存字幕"),
    PLAYBACK_INFO("playback_info", "播放信息"),
    DIAGNOSTICS("diagnostics", "播放器诊断"),
    REPORT("report", "举报"),
    ACTION_COUNTS("action_counts", FullscreenActionCountSettings.TITLE),
    ADVANCED("advanced", "高级设置"),
    SPEED("speed", "播放倍速"),
}

internal val fullscreenMoreMenuEntries = FullscreenMoreMenuEntry.entries.filter {
    it != FullscreenMoreMenuEntry.NEARBY_HANDOFF || NEARBY_HANDOFF_ENABLED
}

internal const val PLAYER_PLAYBACK_SETTINGS_TITLE = "播放设置"

internal val fullscreenPlaybackSettingsEntries = setOf(
    FullscreenMoreMenuEntry.ENHANCEMENT,
    FullscreenMoreMenuEntry.VOLUME,
    FullscreenMoreMenuEntry.CDN,
    FullscreenMoreMenuEntry.TRANSFORM,
    FullscreenMoreMenuEntry.AUDIO_QUALITY,
    FullscreenMoreMenuEntry.CODEC,
)

internal enum class FullscreenTopShortcut(val entry: FullscreenMoreMenuEntry, val icon: ImageVector) {
    DANMAKU_LIST(FullscreenMoreMenuEntry.DANMAKU_LIST, Icons.Outlined.Comment),
    DOWNLOAD(FullscreenMoreMenuEntry.DOWNLOAD, Icons.Outlined.FolderZip),
    SAVE_COVER(FullscreenMoreMenuEntry.SAVE_COVER, Icons.Outlined.Image),
    PLAYBACK_INFO(FullscreenMoreMenuEntry.PLAYBACK_INFO, Icons.Outlined.Info),
}

internal val fullscreenTopShortcutEntries = FullscreenTopShortcut.entries.map { it.entry }

internal const val PLAYER_QUALITY_PLACEMENT_TITLE = "画质位置"
internal const val PLAYER_SPEED_PLACEMENT_TITLE = "倍速位置"

internal val fullscreenBottomControlOptions = mapOf(
    FullscreenMoreMenuEntry.QUALITY to PlayerBottomControlOption(PLAYER_QUALITY_PLACEMENT_TITLE, showInMenuByDefault = true),
    FullscreenMoreMenuEntry.SPEED to PlayerBottomControlOption(PLAYER_SPEED_PLACEMENT_TITLE, showInMenuByDefault = false),
)

internal enum class PortraitMoreMenuEntry(override val id: String, override val title: String) : PlayerMenuEntry {
    CLEAN_SCREEN("clean_screen", "清屏播放"),
    DETAILS("details", "视频详情"),
    PLAYER_SETTINGS("player_settings", "播放器设置"),
    DANMAKU_TOGGLE("danmaku_toggle", "弹幕开关"),
    SEND_DANMAKU("send_danmaku", "发弹幕"),
    DANMAKU_SETTINGS("danmaku_settings", "弹幕设置"),
    DISLIKE("dislike", "点踩"),
    COIN("coin", "投币"),
    LIKE("like", "点赞"),
    FAVORITE("favorite", "收藏"),
    SHARE("share", "分享"),
    EPISODES("episodes", "选集"),
    PREVIOUS_EPISODE("previous_episode", "上一集"),
    NEXT_EPISODE("next_episode", "下一集"),
    SUBTITLES("subtitles", "字幕"),
    TRANSLATION("translation", "翻译"),
    ASPECT_RATIO("aspect_ratio", "画面比例"),
    CHAPTERS("chapters", "分段信息"),
    HEATMAP("heatmap", "高能进度条"),
    RELATED("related", "相关推荐"),
    LANDSCAPE("landscape", "横屏播放"),
    CAST("cast", "投屏"),
    PIP("pip", "画中画"),
    AUDIO_ONLY("audio_only", "听音频"),
    LOCK("lock", "锁定屏幕"),
    SCREENSHOT("screenshot", "截图"),
    SPONSOR_CONTRIBUTE("sponsor_contribute", "提交片段"),
    SPONSOR_ENABLE("sponsor_enable", "开启空降助手"),
    SPONSOR_SEGMENTS("sponsor_segments", "片段信息"),
    HOME("home", "返回首页"),
}
