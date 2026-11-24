package com.android.purebilibili.feature.audio.screen

import com.android.purebilibili.feature.video.ui.components.PlayerMenuEntry

internal enum class MusicMoreMenuEntry(
    override val id: String,
    override val title: String,
    val layoutPreference: MusicPlayerLayoutPreference? = null,
) : PlayerMenuEntry {
    COVER_STYLE("cover_style", "切换封面"),
    COVER_MOTION("cover_motion", "封面律动"),
    LYRICS_STYLE("lyrics_style", "歌词界面"),
    THEME_COLOR("theme_color", "背景主题色"),
    TITLE_COLLAPSE("title_collapse", "折叠标题"),
    LAYOUT_AUTO("layout_auto", resolveMusicPlayerLayoutPreferenceLabel(MusicPlayerLayoutPreference.AUTO), MusicPlayerLayoutPreference.AUTO),
    LAYOUT_SPLIT("layout_split", resolveMusicPlayerLayoutPreferenceLabel(MusicPlayerLayoutPreference.SPLIT), MusicPlayerLayoutPreference.SPLIT),
    LAYOUT_TABLETOP("layout_tabletop", resolveMusicPlayerLayoutPreferenceLabel(MusicPlayerLayoutPreference.TABLETOP), MusicPlayerLayoutPreference.TABLETOP),
    AUDIO_QUALITY("audio_quality", "选择音质"),
    QUEUE("queue", "3D 唱片架 / 播放队列"),
    IMPORT_PLAYLIST("import_playlist", "导入歌单"),
    VIDEO_MODE("video_mode", "返回视频"),
    COLLECTION("collection", "选集 / 合集"),
    SPEED("speed", "播放倍速"),
    AB_LOOP("ab_loop", "A-B 区间循环"),
    HISTORY("history", "最近播放"),
    SLEEP_TIMER("sleep_timer", "定时关闭"),
    FAVORITE("favorite", "收藏"),
    DOWNLOAD("download", "缓存音频"),
    SHARE("share", "分享"),
    PIP("pip", "画中画"),
    ORIENTATION("orientation", "切换横竖屏"),
    LYRICS_SEARCH("lyrics_search", "搜索歌词"),
    SUBTITLE_SETTINGS("subtitle_settings", "字幕 / 歌词设置"),
}
