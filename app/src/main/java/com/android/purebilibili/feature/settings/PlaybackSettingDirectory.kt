package com.android.purebilibili.feature.settings

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal data class PlaybackSettingItem(
    val settingId: String,
    val title: String,
    val page: SettingsRootCategory,
    val sectionKey: String,
    val observeValue: ((Context) -> Flow<String>)?,
    val aliases: List<String> = emptyList(),
    val target: SettingsSearchTarget = SettingsSearchTarget.PLAYBACK,
    val readState: ((SettingsUiState) -> String)? = null,
    val openCategory: Boolean = false,
)

internal val settingsItemDirectory: List<PlaybackSettingItem>
    get() = playbackSettingDirectory + additionalSettingsDirectory

internal fun settingItemTitle(settingId: String): String =
    settingsItemDirectory.first { it.settingId == settingId }.title

internal val playbackSettingDirectory = listOf(
    PlaybackSettingItem("playback.native_miuix_player_popups", "使用小米风格弹窗", SettingsRootCategory.VIDEO_DECODER, SettingsSearchFocusIds.PLAYBACK_DECODER, { context -> com.android.purebilibili.core.store.player.PlayerSettingsStore .getNativeMiuixPlayerPopups(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.hw_decode", "启用硬件解码", SettingsRootCategory.VIDEO_DECODER, SettingsSearchFocusIds.PLAYBACK_DECODER, null),
    PlaybackSettingItem("playback.remember_last_playback_speed", "记住上次倍速", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_SPEED, { context -> com.android.purebilibili.core.store.SettingsManager .getRememberLastPlaybackSpeed(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.long_press_speed_hint_hidden", "隐藏长按倍速提示", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_SPEED, { context -> com.android.purebilibili.core.store.SettingsManager .getLongPressSpeedHintHidden(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.stop_playback_on_exit", "离开播放页后停止", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getStopPlaybackOnExit(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.background_playback_enabled", "后台播放", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getBackgroundPlaybackEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.audio_focus_enabled", "播放时暂停其他应用的声音", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getAudioFocusEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.audio_now_playing_bar_enabled", "底部视频入口", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getAudioNowPlayingBarEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.audio_now_playing_bar_opens_audio_mode", "点底部视频入口听视频", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getAudioNowPlayingBarOpensAudioMode(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.audio_now_playing_bar_immersive_enabled", "听视频时自动隐藏标题", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getAudioNowPlayingBarImmersiveEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.loudness_normalization_enabled", "响度均衡", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getLoudnessNormalizationEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.audio_spectrum_enabled", "音乐氛围", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getAudioSpectrumEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.startup_auto_play_enabled", "重新打开后继续播放", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getStartupAutoPlayEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.pip_no_danmaku_enabled", "小窗中隐藏弹幕", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getPipNoDanmakuEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.audio_mode_auto_pip_enabled", "听视频离开时自动进入画中画", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER, { context -> com.android.purebilibili.core.store.SettingsManager .getAudioModeAutoPipEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.player_diagnostic_logging_enabled", "记录播放问题", SettingsRootCategory.PLAYER_DIAGNOSTICS, SettingsSearchFocusIds.PLAYBACK_DEBUG, { context -> com.android.purebilibili.core.store.SettingsManager .getPlayerDiagnosticLoggingEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.dash_segment_requests_enabled", "分段加载兼容模式（实验性）", SettingsRootCategory.PLAYER_DIAGNOSTICS, SettingsSearchFocusIds.PLAYBACK_DEBUG, { context -> com.android.purebilibili.core.store.SettingsManager .getDashSegmentRequestsEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.quality_switch_failure_dialog_enabled", "画质切换失败时提示", SettingsRootCategory.PLAYER_DIAGNOSTICS, SettingsSearchFocusIds.PLAYBACK_DEBUG, { context -> com.android.purebilibili.core.store.SettingsManager .getQualitySwitchFailureDialogEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.quality_switch_failure_dialog_once_enabled", "只提醒一次", SettingsRootCategory.PLAYER_DIAGNOSTICS, SettingsSearchFocusIds.PLAYBACK_DEBUG, { context -> com.android.purebilibili.core.store.SettingsManager .getQualitySwitchFailureDialogOnceEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.directed_traffic_enabled", "B站定向流量支持", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_NETWORK, { context -> com.android.purebilibili.core.store.SettingsManager .getBiliDirectedTrafficEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.auto_highest_quality_enabled", "自动最高画质", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_NETWORK, { context -> com.android.purebilibili.core.store.SettingsManager .getAutoHighestQuality(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.home_settings.low_quality_home_cover_in_data_saver", "省流量时降低首页封面清晰度", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_DATA_SAVER, { context -> com.android.purebilibili.core.store.SettingsManager.getHomeSettings(context).map { if (it.lowQualityHomeCoverInDataSaver) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.click_to_play_enabled", "进入视频自动播放", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_INTERACTION, { context -> com.android.purebilibili.core.store.SettingsManager .getClickToPlay(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.resume_playback_prompt_enabled", "继续播放提示", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_INTERACTION, { context -> com.android.purebilibili.core.store.SettingsManager .getResumePlaybackPromptEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.space_played_video_locate_prompt_enabled", "在 UP 主页定位刚看过的视频", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_INTERACTION, { context -> com.android.purebilibili.core.store.SettingsManager .getSpacePlayedVideoLocatePromptEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.next_watch_hint_enabled", "接下来观看卡片", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_INTERACTION, { context -> com.android.purebilibili.core.store.SettingsManager.getNextWatchHintEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.auto_play_enabled", "自动播放下一个", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_INTERACTION, { context -> com.android.purebilibili.core.store.SettingsManager .getAutoPlay(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.external_playlist_auto_continue_enabled", "列表/收藏夹连续播放", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_INTERACTION, { context -> com.android.purebilibili.core.store.SettingsManager .getExternalPlaylistAutoContinue(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.hide_interactive_command_danmaku", "隐藏视频内互动提示", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getDanmakuHideInteractiveCommands(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.danmaku_cloud_sync_enabled", "同步弹幕设置到账号", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getDanmakuCloudSyncEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.video_info_default_expanded", "默认展开视频简介", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getVideoInfoDefaultExpanded(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.video_argue_msg_shown", "显示 UP 主视频声明", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getVideoArgueMsgShown(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.detailed_comment_time_enabled", "完整评论时间", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getDetailedCommentTimeEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.show_video_detail_comment_count", "视频详情显示评论数", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getShowVideoDetailCommentCount(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.video_ai_summary_entry_enabled", "显示 AI 总结入口", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getVideoAiSummaryEntryEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.video_note_enabled", "显示视频笔记", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getVideoNoteEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.video_note_default_collapsed", "默认折叠视频笔记", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getVideoNoteDefaultCollapsed(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.double_tap_like", "双击点赞", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, null),
    PlaybackSettingItem("playback.favorite_quick_save_default_folder", "点按收藏进默认收藏夹", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.FavoriteInteractionSettingsStore .getQuickSaveDefaultFolder(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.auto_skip_op_ed", "自动跳过片头片尾", SettingsRootCategory.PLAYBACK_QUALITY, SettingsSearchFocusIds.PLAYBACK_INTERACTION, null),
    PlaybackSettingItem("playback.sub_reply_loaded_count_enabled", "显示已加载回复数", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getSubReplyLoadedCountEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.comment_fraud_detection_enabled", "评论发送检测", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getCommentFraudDetectionEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.comment_avatar_push_enabled", "评论头像推挤", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager.getCommentAvatarPushEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.comment_member_decorations_enabled", "评论区个性装扮", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getCommentMemberDecorationsEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.image_preview_long_press_save_enabled", "图片长按操作", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getImagePreviewLongPressSaveEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.image_preview3d_page_enabled", "图片立体翻页", SettingsRootCategory.COMMENTS_CONTENT, SettingsSearchFocusIds.PLAYBACK_COMMENTS, { context -> com.android.purebilibili.core.store.SettingsManager .getImagePreview3dPageEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.video_detail_chrome_scroll_hide_enabled", "浏览评论时收起顶栏", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getVideoDetailChromeScrollHideEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.pause_on_player_collapse_enabled", "缩小后自动暂停", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getPauseOnPlayerCollapseEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.portrait_swipe_to_fullscreen_enabled", "竖屏上滑进入全屏", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getPortraitSwipeToFullscreenEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.direct_portrait_story_entry", "点竖屏视频直接全屏", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getAutoPortraitFullscreen(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.portrait_only_vertical_recommendations", "刷视频时只推荐竖屏（测试）", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getPortraitOnlyVerticalRecommendations(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.launch_to_portrait_feed_on_startup", "打开应用直接刷视频", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getLaunchToPortraitFeedOnStartup(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.center_swipe_to_fullscreen_enabled", "中部滑动切换全屏", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getCenterSwipeToFullscreenEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.slide_volume_brightness_enabled", "滑动调亮度和音量", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getSlideVolumeBrightnessEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.set_system_brightness_enabled", "调节系统亮度", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getSetSystemBrightnessEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.fullscreen_swipe_seek_enabled", "横屏精细调进度", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getFullscreenSwipeSeekEnabled(context).map { if (it) "开启" else "关闭" } }, aliases = listOf("横屏滑动调进度")),
    PlaybackSettingItem(
        "playback.swipe_seek_use_relative_duration",
        "按视频时长比例调进度",
        SettingsRootCategory.FULLSCREEN_GESTURE,
        SettingsSearchFocusIds.PLAYBACK_FULLSCREEN,
        { context -> com.android.purebilibili.core.store.SettingsManager.getSwipeSeekUseRelativeDuration(context).map { if (it) "开启" else "关闭" } },
        aliases = listOf("相对时长", "相对进度", "固定秒数", "快进快退", "横向拖动", "滑动进度模式", "视频长度", "精准调节")
    ),
    PlaybackSettingItem(
        "playback.swipe_seek_relative_percent",
        "滑动调进度时长比例",
        SettingsRootCategory.FULLSCREEN_GESTURE,
        SettingsSearchFocusIds.PLAYBACK_FULLSCREEN,
        { context -> com.android.purebilibili.core.store.SettingsManager.getSwipeSeekRelativePercent(context).map { "$it%" } },
        aliases = listOf("进度百分比", "快进比例", "快退比例", "滑动范围", "总时长比例", "25%", "50%", "90%", "100%")
    ),
    PlaybackSettingItem("playback.auto_rotate_enabled", "自动横竖屏切换", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getAutoRotateEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.horizontal_adaptation_enabled", "横屏适配", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getHorizontalAdaptationEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.player_control_visibility.show_cast_button", "显示投屏按钮", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager.getPlayerControlVisibilitySettings(context).map { if (it.showCastButton) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.player_control_visibility.show_follow_button", "显示关注按钮", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager.getPlayerControlVisibilitySettings(context).map { if (it.showFollowButton) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.player_control_visibility.compact_player_chrome", "精简播放按钮", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager.getPlayerControlVisibilitySettings(context).map { if (it.compactPlayerChrome) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.fullscreen_gesture_reverse", "全屏手势反向", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getFullscreenGestureReverse(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.video_ambient_settings.enabled", "动态环境光", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager.getVideoAmbientSettings(context).map { if (it.enabled) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.immersive_video_page_status_bar", "状态栏背景模糊", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getHideVideoPageStatusBar(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.portrait_letterbox_ambient_haze", "用模糊画面填充黑边", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getPortraitLetterboxAmbientHaze(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.auto_enter_fullscreen", "自动进入全屏", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getAutoEnterFullscreen(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.auto_exit_fullscreen_mode", "自动退出全屏", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getAutoExitFullscreenMode(context).map { if (it != com.android.purebilibili.core.store.AutoExitFullscreenMode.OFF) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.show_fullscreen_lock_button", "全屏显示锁定按钮", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getShowFullscreenLockButton(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.show_fullscreen_screenshot_button", "全屏显示截图按钮", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getShowFullscreenScreenshotButton(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.app_gesture_screenshot_enabled", "应用内截图", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getAppGestureScreenshotEnabled(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.show_fullscreen_battery_level", "全屏显示电量", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getShowFullscreenBatteryLevel(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.show_fullscreen_time", "全屏显示时间", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getShowFullscreenTime(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.show_fullscreen_action_items", "全屏显示互动按钮", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getShowFullscreenActionItems(context).map { if (it) "开启" else "关闭" } }),
    PlaybackSettingItem("playback.progress_peak_danmaku_enabled", "进度条显示弹幕热度", SettingsRootCategory.FULLSCREEN_GESTURE, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, { context -> com.android.purebilibili.core.store.SettingsManager .getProgressPeakDanmakuEnabled(context).map { if (it) "开启" else "关闭" } }),
)

internal fun playbackSettingTitle(settingId: String): String =
    playbackSettingDirectory.first { it.settingId == settingId }.title

internal fun playbackSettingStateValue(settingId: String, state: SettingsUiState): String = when (settingId) {
    "playback.hw_decode" -> if (state.hwDecode) "开启" else "关闭"
    "playback.double_tap_like" -> if (state.doubleTapLike) "开启" else "关闭"
    "playback.auto_skip_op_ed" -> if (state.autoSkipOpEd) "开启" else "关闭"
    else -> ""
}
