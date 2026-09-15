package com.android.purebilibili.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

internal enum class SettingsIconRole {
    INTERFACE_THEME,
    HOME_FEED,
    NAVIGATION,
    PLAYBACK_QUALITY,
    FULLSCREEN_GESTURE,
    COPY_TEXT,
    INTERACTION_COMMENT,
    DATA_BACKUP,
    PRIVACY_PERMISSION,
    DIAGNOSTICS,
    ABOUT_SUPPORT,
    APPEARANCE,
    ANIMATION,
    PLAYBACK,
    BOTTOM_BAR,
    PERMISSION,
    MESSAGE_NOTIFICATION,
    BLOCKED_LIST,
    SETTINGS_SHARE,
    WEBDAV_BACKUP,
    DOWNLOAD_PATH,
    IMAGE_SAVE_PATH,
    CLEAR_CACHE,
    PLUGINS,
    EXPORT_LOGS,
    OPEN_SOURCE_LICENSES,
    OPEN_SOURCE_HOME,
    CHECK_UPDATE,
    VIEW_RELEASE_NOTES,
    REPLAY_ONBOARDING,
    TIPS,
    OPEN_LINKS,
    DONATE,
    DISCLAIMER,
    RELEASE_CHANNEL,
    CRASH_TRACKING,
    ANALYTICS,
    FEED_API,
    REFRESH_COUNT,
    DYNAMIC_PREVIEW_TEXT,
    DYNAMIC_TAB_VISIBILITY,
    EASTER_EGG,
    AUTO_CHECK_UPDATE,
    BUILD_SOURCE,
    BUILD_FINGERPRINT,
    BUILD_VERIFICATION,
    ANDROID_LIQUID_GLASS,
    DYNAMIC_COLOR,
    THEME_COLOR_PICKER,
    COLOR_STYLE,
    COLOR_SPEC,
    APP_LANGUAGE,
    FONT_FILE,
    SPLASH_WALLPAPER,
    RANDOM_WALLPAPER,
    DISPLAY_STYLE,
    HOME_COVER_GLASS,
    VIDEO_DURATION_BADGES,
    HOME_INFO_GLASS,
    HOME_WALLPAPER,
    WALLPAPER_EFFECT,
    HOME_UP_BADGES,
    HOME_UP_AVATAR,
    FULL_VIDEO_CARD_CONTENT,
    ONLINE_COUNT,
    GRID_COLUMNS,
    HOME_CARD_WIDTH,
    CARD_ENTRANCE_ANIMATION,
    CARD_TRANSITION_ANIMATION,
    LIVE_SURFACE_TRANSITION,
    PREDICTIVE_BACK,
    MIUIX_TRANSITION_BLUR,
    TOP_DOCK_GLASS,
    HOME_SEARCH_GLASS,
    BOTTOM_BAR_GLASS,
    TOP_BAR_BLUR,
    HEADER_COLLAPSE,
    BOTTOM_BAR_BLUR,
    FLOATING_BOTTOM_BAR,
    HARDWARE_DECODER,
    PLAYBACK_SPEED,
    NATIVE_MIUIX_DIALOG,
    LONG_PRESS_SPEED_HINT,
    RESUME_PLAYBACK_PROMPT,
    STOP_ON_EXIT,
    BACKGROUND_PLAYBACK,
    PLAYLIST_AUTO_CONTINUE,
    AUDIO_FOCUS,
    SLIDE_VOLUME_BRIGHTNESS,
    PIP_DANMAKU,
    DANMAKU_CLOUD_SYNC,
    AUDIO_MODE_PIP,
    PLAYER_DIAGNOSTICS,
    QUALITY_WARNING,
    SUBTITLE,
    COMMENT_DECORATION,
    AI_SUMMARY,
    VIDEO_NOTE,
    LIKE_INTERACTION,
    VIDEO_DESCRIPTION,
    FULLSCREEN_ORIENTATION,
    HORIZONTAL_ADAPTATION,
    FULLSCREEN_GESTURE_REVERSE,
    IMMERSIVE_STATUS_BAR,
    AUTO_ENTER_FULLSCREEN,
    AUTO_EXIT_FULLSCREEN,
    FULLSCREEN_LOCK,
    FULLSCREEN_SCREENSHOT,
    CLEAN_SCREENSHOT,
    BATTERY_STATUS,
    TIME_STATUS,
    PLAYER_ACTIONS,
    PRIVACY_CONTENT_AUTHENTICATION,
    PLAYER_STATS,
    PLAYER_DIAGNOSTIC_LOGS,
    QUALITY_WARNING_ONCE,
    DIRECTED_TRAFFIC,
    AUTO_HIGHEST_QUALITY,
    AUTO_PLAY_ON_OPEN,
    STARTUP_PORTRAIT_FEED,
    HOME_HERO_AUTOPLAY,
    AUTO_PLAY_NEXT,
    VIDEO_NOTE_COLLAPSE,
    INTERACTIVE_COMMANDS,
    PORTRAIT_SWIPE_FULLSCREEN,
    CENTER_SWIPE_FULLSCREEN,
    SYSTEM_BRIGHTNESS,
    APP_ICON,
    HOME_CARD_STATS_COMPACT,
    HOME_HERO_CAROUSEL,
    HOME_ONLINE_COUNT,
    PORTRAIT_STORY_ENTRY,
    DISPLAY_SCALE,
    UI_ENTRANCE_ANIMATION,
    FULLSCREEN_SWIPE_BACK,
    FOLLOW_BUTTON,
    PRIVACY_HISTORY,
    CUSTOM_MD3_COLOR,
    THEME_LIGHT_BACKGROUND,
    THEME_LIGHT_PRIMARY_TEXT,
    THEME_LIGHT_SECONDARY_TEXT,
    THEME_LIGHT_CONTROL,
    THEME_DARK_BACKGROUND,
    THEME_DARK_PRIMARY_TEXT,
    THEME_DARK_SECONDARY_TEXT,
    THEME_DARK_CONTROL,
    DEVELOPER_CRASH_TRACKING,
    DEVELOPER_ANALYTICS,
    APP_VERSION,
    BOTTOM_BAR_GLASS_PREVIEW,
    ADVANCED_COLOR,
    CAST_BUTTON,
    PROGRESS_PEAK_DANMAKU,
    IMAGE_3D_PAGE,
    SPLASH_ICON_ANIMATION,
    NAV_ICON_CROSS_SCALE,
    SUB_REPLY_LOADED_COUNT,
    COMMENT_VISIBILITY_CHECK,
    PORTRAIT_AMBIENT_HAZE,
    BACK_TO_TOP,
    HOME_HEADER_COLLAPSE,
    PGC_TIMELINE,
    RELATED_VIDEO_TRANSITION,
    RETURN_GESTURE_POSE,
    AUTO_SKIP_OP_ED,
    BLUR_INTENSITY,
    HAPTIC_FEEDBACK,
    REMEMBER_PLAYBACK_SPEED,
    SPACE_PLAYED_VIDEO_LOCATE,
    IMAGE_LONG_PRESS_ACTION,
    PLAYER_COLLAPSE_PAUSE,
    BOTTOM_BAR_SEARCH,
    DATA_SAVER_COVER_QUALITY,
    SEGMENT_LOADING_COMPATIBILITY
}

@Composable
internal fun rememberSettingsSemanticIcon(
    role: SettingsIconRole,
): ImageVector {
    // The component skin owns the colored container and control styling. The glyph itself
    // always comes from the role-specific local vector so missing MIUIX icons are never
    // substituted with unrelated phone/settings/contact symbols.
    return ImageVector.vectorResource(resolveSettingsMaterialSymbolResource(role))
}

/**
 * 设置语义与组件皮肤解耦：所有外观都使用一一对应的本地 VectorDrawable，Miuix 仅负责
 * 外层容器、着色和控件形态，避免用无关 glyph 填补其图标库缺项。
 */
internal fun resolveSettingsMaterialSymbolResource(role: SettingsIconRole): Int = when (role) {
    SettingsIconRole.INTERFACE_THEME -> R.drawable.ms_color_lens_24
    SettingsIconRole.HOME_FEED -> R.drawable.ms_home_24
    SettingsIconRole.NAVIGATION -> R.drawable.ms_dashboard_24
    SettingsIconRole.PLAYBACK_QUALITY -> R.drawable.ms_high_quality_24
    SettingsIconRole.FULLSCREEN_GESTURE -> R.drawable.ms_touch_app_24
    SettingsIconRole.COPY_TEXT -> R.drawable.ms_content_copy_24
    SettingsIconRole.INTERACTION_COMMENT -> R.drawable.ms_chat_bubble_outline_24
    SettingsIconRole.DATA_BACKUP -> R.drawable.ms_backup_24
    SettingsIconRole.PRIVACY_PERMISSION -> R.drawable.ms_lock_24
    SettingsIconRole.DIAGNOSTICS -> R.drawable.ms_terminal_24
    SettingsIconRole.ABOUT_SUPPORT -> R.drawable.ms_info_24
    SettingsIconRole.APPEARANCE -> R.drawable.ms_palette_24
    SettingsIconRole.ANIMATION -> R.drawable.ms_animation_24
    SettingsIconRole.PLAYBACK -> R.drawable.ms_play_circle_24
    SettingsIconRole.BOTTOM_BAR -> R.drawable.ms_widgets_24
    SettingsIconRole.PERMISSION -> R.drawable.ms_security_24
    SettingsIconRole.MESSAGE_NOTIFICATION -> R.drawable.ms_notifications_24
    SettingsIconRole.BLOCKED_LIST -> R.drawable.ms_block_24
    SettingsIconRole.SETTINGS_SHARE -> R.drawable.ms_share_24
    SettingsIconRole.WEBDAV_BACKUP -> R.drawable.ms_cloud_upload_24
    SettingsIconRole.DOWNLOAD_PATH -> R.drawable.ms_folder_24
    SettingsIconRole.IMAGE_SAVE_PATH -> R.drawable.ms_photo_24
    SettingsIconRole.CLEAR_CACHE -> R.drawable.ms_delete_outline_24
    SettingsIconRole.PLUGINS -> R.drawable.ms_extension_24
    SettingsIconRole.EXPORT_LOGS -> R.drawable.ms_article_24
    SettingsIconRole.OPEN_SOURCE_LICENSES -> R.drawable.ms_gavel_24
    SettingsIconRole.OPEN_SOURCE_HOME -> R.drawable.ms_open_in_new_24
    SettingsIconRole.CHECK_UPDATE -> R.drawable.ms_system_update_24
    SettingsIconRole.VIEW_RELEASE_NOTES -> R.drawable.ms_newspaper_24
    SettingsIconRole.REPLAY_ONBOARDING -> R.drawable.ms_replay_24
    SettingsIconRole.TIPS -> R.drawable.ms_lightbulb_24
    SettingsIconRole.OPEN_LINKS -> R.drawable.ms_link_24
    SettingsIconRole.DONATE -> R.drawable.ms_card_giftcard_24
    SettingsIconRole.DISCLAIMER -> R.drawable.ms_warning_amber_24
    SettingsIconRole.RELEASE_CHANNEL -> R.drawable.ms_rocket_24
    SettingsIconRole.CRASH_TRACKING -> R.drawable.ms_bug_report_24
    SettingsIconRole.ANALYTICS -> R.drawable.ms_analytics_24
    SettingsIconRole.FEED_API -> R.drawable.ms_rss_feed_24
    SettingsIconRole.REFRESH_COUNT -> R.drawable.ms_refresh_24
    SettingsIconRole.DYNAMIC_PREVIEW_TEXT -> R.drawable.ms_text_snippet_24
    SettingsIconRole.DYNAMIC_TAB_VISIBILITY -> R.drawable.ms_visibility_24
    SettingsIconRole.EASTER_EGG -> R.drawable.ms_auto_awesome_24
    SettingsIconRole.AUTO_CHECK_UPDATE -> R.drawable.ms_update_24
    SettingsIconRole.BUILD_SOURCE -> R.drawable.ms_tag_24
    SettingsIconRole.BUILD_FINGERPRINT -> R.drawable.ms_fingerprint_24
    SettingsIconRole.BUILD_VERIFICATION -> R.drawable.ms_verified_user_24
    SettingsIconRole.ANDROID_LIQUID_GLASS -> R.drawable.ms_water_drop_24
    SettingsIconRole.DYNAMIC_COLOR -> R.drawable.ms_format_color_text_24
    SettingsIconRole.THEME_COLOR_PICKER -> R.drawable.ms_colorize_24
    SettingsIconRole.COLOR_STYLE -> R.drawable.ms_brush_24
    SettingsIconRole.COLOR_SPEC -> R.drawable.ms_auto_fix_high_24
    SettingsIconRole.APP_LANGUAGE -> R.drawable.ms_language_24
    SettingsIconRole.FONT_FILE -> R.drawable.ms_font_download_24
    SettingsIconRole.SPLASH_WALLPAPER -> R.drawable.ms_wallpaper_24
    SettingsIconRole.RANDOM_WALLPAPER -> R.drawable.ms_shuffle_24
    SettingsIconRole.DISPLAY_STYLE -> R.drawable.ms_view_carousel_24
    SettingsIconRole.HOME_COVER_GLASS -> R.drawable.ms_opacity_24
    SettingsIconRole.VIDEO_DURATION_BADGES -> R.drawable.ms_timer_24
    SettingsIconRole.HOME_INFO_GLASS -> R.drawable.ms_badge_24
    SettingsIconRole.HOME_WALLPAPER -> R.drawable.ms_image_24
    SettingsIconRole.WALLPAPER_EFFECT -> R.drawable.ms_blur_on_24
    SettingsIconRole.HOME_UP_BADGES -> R.drawable.ms_workspace_premium_24
    SettingsIconRole.HOME_UP_AVATAR -> R.drawable.ms_account_circle_24
    SettingsIconRole.FULL_VIDEO_CARD_CONTENT -> R.drawable.ms_notes_24
    SettingsIconRole.ONLINE_COUNT -> R.drawable.ms_online_prediction_24
    SettingsIconRole.GRID_COLUMNS -> R.drawable.ms_grid_view_24
    SettingsIconRole.HOME_CARD_WIDTH -> R.drawable.ms_width_normal_24
    SettingsIconRole.CARD_ENTRANCE_ANIMATION -> R.drawable.ms_auto_awesome_motion_24
    SettingsIconRole.CARD_TRANSITION_ANIMATION -> R.drawable.ms_sync_alt_24
    SettingsIconRole.LIVE_SURFACE_TRANSITION -> R.drawable.ms_movie_24
    SettingsIconRole.PREDICTIVE_BACK -> R.drawable.ms_arrow_back_24
    SettingsIconRole.MIUIX_TRANSITION_BLUR -> R.drawable.ms_gradient_24
    SettingsIconRole.TOP_DOCK_GLASS -> R.drawable.ms_layers_24
    SettingsIconRole.HOME_SEARCH_GLASS -> R.drawable.ms_manage_search_24
    SettingsIconRole.BOTTOM_BAR_GLASS -> R.drawable.ms_blur_circular_24
    SettingsIconRole.TOP_BAR_BLUR -> R.drawable.ms_view_headline_24
    SettingsIconRole.HEADER_COLLAPSE -> R.drawable.ms_keyboard_arrow_up_24
    SettingsIconRole.BOTTOM_BAR_BLUR -> R.drawable.ms_blur_linear_24
    SettingsIconRole.FLOATING_BOTTOM_BAR -> R.drawable.ms_view_agenda_24
    SettingsIconRole.HARDWARE_DECODER -> R.drawable.ms_memory_24
    SettingsIconRole.PLAYBACK_SPEED -> R.drawable.ms_speed_24
    SettingsIconRole.NATIVE_MIUIX_DIALOG -> R.drawable.ms_chat_bubble_outline_24
    SettingsIconRole.LONG_PRESS_SPEED_HINT -> R.drawable.ms_visibility_off_24
    SettingsIconRole.RESUME_PLAYBACK_PROMPT -> R.drawable.ms_restore_24
    SettingsIconRole.STOP_ON_EXIT -> R.drawable.ms_stop_circle_24
    SettingsIconRole.BACKGROUND_PLAYBACK -> R.drawable.ms_music_note_24
    SettingsIconRole.PLAYLIST_AUTO_CONTINUE -> R.drawable.ms_queue_play_next_24
    SettingsIconRole.AUDIO_FOCUS -> R.drawable.ms_headphones_24
    SettingsIconRole.SLIDE_VOLUME_BRIGHTNESS -> R.drawable.ms_swap_vert_24
    SettingsIconRole.PIP_DANMAKU -> R.drawable.ms_textsms_24
    SettingsIconRole.DANMAKU_CLOUD_SYNC -> R.drawable.ms_cloud_sync_24
    SettingsIconRole.AUDIO_MODE_PIP -> R.drawable.ms_picture_in_picture_24
    SettingsIconRole.PLAYER_DIAGNOSTICS -> R.drawable.ms_query_stats_24
    SettingsIconRole.QUALITY_WARNING -> R.drawable.ms_report_problem_24
    SettingsIconRole.SUBTITLE -> R.drawable.ms_subtitles_24
    SettingsIconRole.COMMENT_DECORATION -> R.drawable.ms_mode_comment_24
    SettingsIconRole.AI_SUMMARY -> R.drawable.ms_smart_toy_24
    SettingsIconRole.VIDEO_NOTE -> R.drawable.ms_edit_note_24
    SettingsIconRole.LIKE_INTERACTION -> R.drawable.ms_thumb_up_off_alt_24
    SettingsIconRole.VIDEO_DESCRIPTION -> R.drawable.ms_subject_24
    SettingsIconRole.FULLSCREEN_ORIENTATION -> R.drawable.ms_screen_rotation_24
    SettingsIconRole.HORIZONTAL_ADAPTATION -> R.drawable.ms_aspect_ratio_24
    SettingsIconRole.FULLSCREEN_GESTURE_REVERSE -> R.drawable.ms_swipe_vertical_24
    SettingsIconRole.IMMERSIVE_STATUS_BAR -> R.drawable.ms_fullscreen_24
    SettingsIconRole.AUTO_ENTER_FULLSCREEN -> R.drawable.ms_open_in_full_24
    SettingsIconRole.AUTO_EXIT_FULLSCREEN -> R.drawable.ms_fullscreen_exit_24
    SettingsIconRole.FULLSCREEN_LOCK -> R.drawable.ms_screen_lock_rotation_24
    SettingsIconRole.FULLSCREEN_SCREENSHOT -> R.drawable.ms_screenshot_24
    SettingsIconRole.CLEAN_SCREENSHOT -> R.drawable.ms_screenshot_monitor_24
    SettingsIconRole.BATTERY_STATUS -> R.drawable.ms_battery_full_24
    SettingsIconRole.TIME_STATUS -> R.drawable.ms_access_time_24
    SettingsIconRole.PLAYER_ACTIONS -> R.drawable.ms_more_horiz_24
    SettingsIconRole.PRIVACY_CONTENT_AUTHENTICATION -> R.drawable.ms_verified_24
    SettingsIconRole.PLAYER_STATS -> R.drawable.ms_insert_chart_outlined_24
    SettingsIconRole.PLAYER_DIAGNOSTIC_LOGS -> R.drawable.ms_report_gmailerrorred_24
    SettingsIconRole.QUALITY_WARNING_ONCE -> R.drawable.ms_notification_important_24
    SettingsIconRole.DIRECTED_TRAFFIC -> R.drawable.ms_network_locked_24
    SettingsIconRole.AUTO_HIGHEST_QUALITY -> R.drawable.ms_settings_suggest_24
    SettingsIconRole.AUTO_PLAY_ON_OPEN -> R.drawable.ms_play_arrow_24
    SettingsIconRole.STARTUP_PORTRAIT_FEED -> R.drawable.ms_vertical_align_top_24
    SettingsIconRole.HOME_HERO_AUTOPLAY -> R.drawable.ms_smart_display_24
    SettingsIconRole.AUTO_PLAY_NEXT -> R.drawable.ms_playlist_play_24
    SettingsIconRole.VIDEO_NOTE_COLLAPSE -> R.drawable.ms_short_text_24
    SettingsIconRole.INTERACTIVE_COMMANDS -> R.drawable.ms_comments_disabled_24
    SettingsIconRole.PORTRAIT_SWIPE_FULLSCREEN -> R.drawable.ms_swipe_up_24
    SettingsIconRole.CENTER_SWIPE_FULLSCREEN -> R.drawable.ms_swipe_24
    SettingsIconRole.SYSTEM_BRIGHTNESS -> R.drawable.ms_brightness_medium_24
    SettingsIconRole.APP_ICON -> R.drawable.ms_apps_24
    SettingsIconRole.HOME_CARD_STATS_COMPACT -> R.drawable.ms_stacked_bar_chart_24
    SettingsIconRole.HOME_HERO_CAROUSEL -> R.drawable.ms_view_day_24
    SettingsIconRole.HOME_ONLINE_COUNT -> R.drawable.ms_groups_24
    SettingsIconRole.PORTRAIT_STORY_ENTRY -> R.drawable.ms_stay_current_portrait_24
    SettingsIconRole.DISPLAY_SCALE -> R.drawable.ms_zoom_out_map_24
    SettingsIconRole.UI_ENTRANCE_ANIMATION -> R.drawable.ms_motion_photos_on_24
    SettingsIconRole.FULLSCREEN_SWIPE_BACK -> R.drawable.ms_swipe_right_24
    SettingsIconRole.FOLLOW_BUTTON -> R.drawable.ms_person_add_24
    SettingsIconRole.PRIVACY_HISTORY -> R.drawable.ms_history_toggle_off_24
    SettingsIconRole.CUSTOM_MD3_COLOR -> R.drawable.ms_format_paint_24
    SettingsIconRole.THEME_LIGHT_BACKGROUND -> R.drawable.ms_light_mode_24
    SettingsIconRole.THEME_LIGHT_PRIMARY_TEXT -> R.drawable.ms_text_fields_24
    SettingsIconRole.THEME_LIGHT_SECONDARY_TEXT -> R.drawable.ms_format_size_24
    SettingsIconRole.THEME_LIGHT_CONTROL -> R.drawable.ms_tune_24
    SettingsIconRole.THEME_DARK_BACKGROUND -> R.drawable.ms_dark_mode_24
    SettingsIconRole.THEME_DARK_PRIMARY_TEXT -> R.drawable.ms_text_format_24
    SettingsIconRole.THEME_DARK_SECONDARY_TEXT -> R.drawable.ms_subtitles_off_24
    SettingsIconRole.THEME_DARK_CONTROL -> R.drawable.ms_control_point_24
    SettingsIconRole.DEVELOPER_CRASH_TRACKING -> R.drawable.ms_health_and_safety_24
    SettingsIconRole.DEVELOPER_ANALYTICS -> R.drawable.ms_data_usage_24
    SettingsIconRole.APP_VERSION -> R.drawable.ms_new_releases_24
    SettingsIconRole.BOTTOM_BAR_GLASS_PREVIEW -> R.drawable.ms_lens_blur_24
    SettingsIconRole.ADVANCED_COLOR -> R.drawable.ms_invert_colors_24
    SettingsIconRole.CAST_BUTTON -> R.drawable.ms_cast_24
    SettingsIconRole.PROGRESS_PEAK_DANMAKU -> R.drawable.ms_graphic_eq_24
    SettingsIconRole.IMAGE_3D_PAGE -> R.drawable.ms_3d_rotation_24
    SettingsIconRole.SPLASH_ICON_ANIMATION -> R.drawable.ms_filter_frames_24
    SettingsIconRole.NAV_ICON_CROSS_SCALE -> R.drawable.ms_compare_arrows_24
    SettingsIconRole.SUB_REPLY_LOADED_COUNT -> R.drawable.ms_numbers_24
    SettingsIconRole.COMMENT_VISIBILITY_CHECK -> R.drawable.ms_fact_check_24
    SettingsIconRole.PORTRAIT_AMBIENT_HAZE -> R.drawable.ms_filter_hdr_24
    SettingsIconRole.BACK_TO_TOP -> R.drawable.ms_keyboard_double_arrow_up_24
    SettingsIconRole.HOME_HEADER_COLLAPSE -> R.drawable.ms_compress_24
    SettingsIconRole.PGC_TIMELINE -> R.drawable.ms_calendar_month_24
    SettingsIconRole.RELATED_VIDEO_TRANSITION -> R.drawable.ms_video_library_24
    SettingsIconRole.RETURN_GESTURE_POSE -> R.drawable.ms_rotate90_degrees_ccw_24
    SettingsIconRole.AUTO_SKIP_OP_ED -> R.drawable.ms_skip_next_24
    SettingsIconRole.BLUR_INTENSITY -> R.drawable.ms_flare_24
    SettingsIconRole.HAPTIC_FEEDBACK -> R.drawable.ms_touch_app_fill_24
    SettingsIconRole.REMEMBER_PLAYBACK_SPEED -> R.drawable.ms_history_fill_24
    SettingsIconRole.SPACE_PLAYED_VIDEO_LOCATE -> R.drawable.ms_search_24
    SettingsIconRole.IMAGE_LONG_PRESS_ACTION -> R.drawable.ms_photo_library_24
    SettingsIconRole.PLAYER_COLLAPSE_PAUSE -> R.drawable.ms_pause_24
    SettingsIconRole.BOTTOM_BAR_SEARCH -> R.drawable.ms_search_fill_24
    SettingsIconRole.DATA_SAVER_COVER_QUALITY -> R.drawable.ms_wifi_24
    SettingsIconRole.SEGMENT_LOADING_COMPATIBILITY -> R.drawable.ms_cloud_download_24
}

@Composable
internal fun rememberThemeAwareSettingsIcon(
    materialSymbolResource: Int,
    miuixIcon: ImageVector,
): ImageVector = when (rememberAppSemanticVisualPolicy().effectiveIconFamily) {
    AppSemanticIconFamily.MATERIAL -> ImageVector.vectorResource(materialSymbolResource)
    AppSemanticIconFamily.MIUIX -> miuixIcon
}

/** Material 设置界面的统一 VectorDrawable → ImageVector 入口。 */
@Composable
internal fun rememberMaterialSymbol(
    materialSymbolResource: Int,
): ImageVector = ImageVector.vectorResource(materialSymbolResource)

internal fun resolveSettingsSearchTargetIconRole(
    target: SettingsSearchTarget
): SettingsIconRole = when (target) {
    SettingsSearchTarget.INTERFACE_THEME -> SettingsIconRole.INTERFACE_THEME
    SettingsSearchTarget.HOME_FEED -> SettingsIconRole.HOME_FEED
    SettingsSearchTarget.NAVIGATION -> SettingsIconRole.NAVIGATION
    SettingsSearchTarget.PLAYBACK_QUALITY -> SettingsIconRole.PLAYBACK_QUALITY
    SettingsSearchTarget.FULLSCREEN_GESTURE -> SettingsIconRole.FULLSCREEN_GESTURE
    SettingsSearchTarget.INTERACTION_COMMENT -> SettingsIconRole.INTERACTION_COMMENT
    SettingsSearchTarget.DATA_BACKUP -> SettingsIconRole.DATA_BACKUP
    SettingsSearchTarget.PRIVACY_PERMISSION -> SettingsIconRole.PRIVACY_PERMISSION
    SettingsSearchTarget.DIAGNOSTICS -> SettingsIconRole.DIAGNOSTICS
    SettingsSearchTarget.ABOUT_SUPPORT -> SettingsIconRole.ABOUT_SUPPORT
    SettingsSearchTarget.APPEARANCE -> SettingsIconRole.APPEARANCE
    SettingsSearchTarget.ANIMATION -> SettingsIconRole.ANIMATION
    SettingsSearchTarget.PLAYBACK -> SettingsIconRole.PLAYBACK
    SettingsSearchTarget.BOTTOM_BAR -> SettingsIconRole.BOTTOM_BAR
    SettingsSearchTarget.PERMISSION -> SettingsIconRole.PERMISSION
    SettingsSearchTarget.MESSAGE_NOTIFICATION -> SettingsIconRole.MESSAGE_NOTIFICATION
    SettingsSearchTarget.BLOCKED_LIST -> SettingsIconRole.BLOCKED_LIST
    SettingsSearchTarget.SETTINGS_SHARE -> SettingsIconRole.SETTINGS_SHARE
    SettingsSearchTarget.WEBDAV_BACKUP -> SettingsIconRole.WEBDAV_BACKUP
    SettingsSearchTarget.DOWNLOAD_PATH -> SettingsIconRole.DOWNLOAD_PATH
    SettingsSearchTarget.IMAGE_SAVE_PATH -> SettingsIconRole.IMAGE_SAVE_PATH
    SettingsSearchTarget.CLEAR_CACHE -> SettingsIconRole.CLEAR_CACHE
    SettingsSearchTarget.PLUGINS -> SettingsIconRole.PLUGINS
    SettingsSearchTarget.EXPORT_LOGS -> SettingsIconRole.EXPORT_LOGS
    SettingsSearchTarget.OPEN_SOURCE_LICENSES -> SettingsIconRole.OPEN_SOURCE_LICENSES
    SettingsSearchTarget.OPEN_SOURCE_HOME -> SettingsIconRole.OPEN_SOURCE_HOME
    SettingsSearchTarget.CHECK_UPDATE -> SettingsIconRole.CHECK_UPDATE
    SettingsSearchTarget.VIEW_RELEASE_NOTES -> SettingsIconRole.VIEW_RELEASE_NOTES
    SettingsSearchTarget.REPLAY_ONBOARDING -> SettingsIconRole.REPLAY_ONBOARDING
    SettingsSearchTarget.TIPS -> SettingsIconRole.TIPS
    SettingsSearchTarget.OPEN_LINKS -> SettingsIconRole.OPEN_LINKS
    SettingsSearchTarget.DONATE -> SettingsIconRole.DONATE
    SettingsSearchTarget.TELEGRAM -> SettingsIconRole.OPEN_LINKS
    SettingsSearchTarget.TWITTER -> SettingsIconRole.OPEN_LINKS
    SettingsSearchTarget.DISCLAIMER -> SettingsIconRole.DISCLAIMER
}

internal fun resolveSettingsSemanticIconSizeDp(
    role: SettingsIconRole,
    iconFamily: AppSemanticIconFamily,
): Int {
    if (iconFamily != AppSemanticIconFamily.MIUIX) return 20
    return when (role) {
        SettingsIconRole.HOME_FEED,
        SettingsIconRole.NAVIGATION,
        SettingsIconRole.DIAGNOSTICS,
        SettingsIconRole.ANIMATION,
        SettingsIconRole.BOTTOM_BAR,
        SettingsIconRole.DISPLAY_STYLE,
        SettingsIconRole.GRID_COLUMNS,
        SettingsIconRole.HOME_HERO_CAROUSEL,
        SettingsIconRole.APP_ICON -> 19

        SettingsIconRole.PLAYBACK_QUALITY,
        SettingsIconRole.FOLLOW_BUTTON,
        SettingsIconRole.BUILD_VERIFICATION,
        SettingsIconRole.AUTO_EXIT_FULLSCREEN,
        SettingsIconRole.HEADER_COLLAPSE -> 21

        else -> 20
    }
}

/**
 * Miuix 设置页只使用 miuix-icons 字形。这里按设置语义集中映射，并确保一级入口、
 * 二级页面和更深层设置项之间不复用同一图标资产。
 */
internal fun resolveMiuixSettingsSemanticIcon(role: SettingsIconRole): ImageVector = when (role) {
    SettingsIconRole.INTERFACE_THEME -> MiuixIcons.Theme
    SettingsIconRole.HOME_FEED -> MiuixIcons.Home
    SettingsIconRole.NAVIGATION -> MiuixIcons.Sidebar
    SettingsIconRole.PLAYBACK_QUALITY -> MiuixIcons.TopDownloads
    SettingsIconRole.FULLSCREEN_GESTURE -> MiuixIcons.ScreenCapture
    SettingsIconRole.COPY_TEXT -> MiuixIcons.Copy
    SettingsIconRole.INTERACTION_COMMENT -> MiuixIcons.Messages
    SettingsIconRole.DATA_BACKUP -> MiuixIcons.Backup
    SettingsIconRole.PRIVACY_PERMISSION -> MiuixIcons.Lock
    SettingsIconRole.DIAGNOSTICS -> MiuixIcons.AppRecording
    SettingsIconRole.ABOUT_SUPPORT -> MiuixIcons.Info
    SettingsIconRole.APPEARANCE -> MiuixIcons.Create
    SettingsIconRole.ANIMATION -> MiuixIcons.RecordingTape
    SettingsIconRole.PLAYBACK -> MiuixIcons.Play
    SettingsIconRole.BOTTOM_BAR -> MiuixIcons.HorizontalSplit
    SettingsIconRole.PERMISSION -> MiuixIcons.Unlock
    SettingsIconRole.MESSAGE_NOTIFICATION -> MiuixIcons.Messages
    SettingsIconRole.BLOCKED_LIST -> MiuixIcons.Blocklist
    SettingsIconRole.SETTINGS_SHARE -> MiuixIcons.Share
    SettingsIconRole.WEBDAV_BACKUP -> MiuixIcons.UploadCloud
    SettingsIconRole.DOWNLOAD_PATH -> MiuixIcons.FileDownloads
    SettingsIconRole.IMAGE_SAVE_PATH -> MiuixIcons.Photos
    SettingsIconRole.CLEAR_CACHE -> MiuixIcons.Clear
    SettingsIconRole.PLUGINS -> MiuixIcons.Add
    SettingsIconRole.EXPORT_LOGS -> MiuixIcons.File
    SettingsIconRole.OPEN_SOURCE_LICENSES -> MiuixIcons.Notes
    SettingsIconRole.OPEN_SOURCE_HOME -> MiuixIcons.Link
    SettingsIconRole.CHECK_UPDATE -> MiuixIcons.Update
    SettingsIconRole.VIEW_RELEASE_NOTES -> MiuixIcons.NotesFill
    SettingsIconRole.REPLAY_ONBOARDING -> MiuixIcons.Reset
    SettingsIconRole.TIPS -> MiuixIcons.Help
    SettingsIconRole.OPEN_LINKS -> MiuixIcons.ChevronForward
    SettingsIconRole.DONATE -> MiuixIcons.BankCards
    SettingsIconRole.DISCLAIMER -> MiuixIcons.Report
    SettingsIconRole.RELEASE_CHANNEL -> MiuixIcons.Update
    SettingsIconRole.CRASH_TRACKING -> MiuixIcons.Report
    SettingsIconRole.ANALYTICS -> MiuixIcons.Sort
    SettingsIconRole.FEED_API -> MiuixIcons.Link
    SettingsIconRole.REFRESH_COUNT -> MiuixIcons.Refresh
    SettingsIconRole.DYNAMIC_PREVIEW_TEXT -> MiuixIcons.ConvertFile
    SettingsIconRole.DYNAMIC_TAB_VISIBILITY -> MiuixIcons.SelectAll
    SettingsIconRole.EASTER_EGG -> MiuixIcons.Help
    SettingsIconRole.AUTO_CHECK_UPDATE -> MiuixIcons.Update
    SettingsIconRole.BUILD_SOURCE -> MiuixIcons.File
    SettingsIconRole.BUILD_FINGERPRINT -> MiuixIcons.Scan
    SettingsIconRole.BUILD_VERIFICATION -> MiuixIcons.Ok
    SettingsIconRole.ANDROID_LIQUID_GLASS -> MiuixIcons.Layers
    SettingsIconRole.DYNAMIC_COLOR -> MiuixIcons.Create
    SettingsIconRole.THEME_COLOR_PICKER -> MiuixIcons.Tune
    SettingsIconRole.COLOR_STYLE -> MiuixIcons.Edit
    SettingsIconRole.COLOR_SPEC -> MiuixIcons.Filter
    SettingsIconRole.APP_LANGUAGE -> MiuixIcons.Translate
    SettingsIconRole.FONT_FILE -> MiuixIcons.Folder
    SettingsIconRole.SPLASH_WALLPAPER -> MiuixIcons.Image
    SettingsIconRole.RANDOM_WALLPAPER -> MiuixIcons.Replace
    SettingsIconRole.DISPLAY_STYLE -> MiuixIcons.ListView
    SettingsIconRole.HOME_COVER_GLASS -> MiuixIcons.Background
    SettingsIconRole.VIDEO_DURATION_BADGES -> MiuixIcons.Timer
    SettingsIconRole.HOME_INFO_GLASS -> MiuixIcons.Info
    SettingsIconRole.HOME_WALLPAPER -> MiuixIcons.MapAlbum
    SettingsIconRole.WALLPAPER_EFFECT -> MiuixIcons.Layers
    SettingsIconRole.HOME_UP_BADGES -> MiuixIcons.Promotions
    SettingsIconRole.HOME_UP_AVATAR -> MiuixIcons.ContactsCircle
    SettingsIconRole.FULL_VIDEO_CARD_CONTENT -> MiuixIcons.ListView
    SettingsIconRole.ONLINE_COUNT -> MiuixIcons.Contacts
    SettingsIconRole.GRID_COLUMNS -> MiuixIcons.GridView
    SettingsIconRole.HOME_CARD_WIDTH -> MiuixIcons.HorizontalSplit
    SettingsIconRole.CARD_ENTRANCE_ANIMATION -> MiuixIcons.Forward
    SettingsIconRole.CARD_TRANSITION_ANIMATION -> MiuixIcons.Replace
    SettingsIconRole.LIVE_SURFACE_TRANSITION -> MiuixIcons.MoveFile
    SettingsIconRole.PREDICTIVE_BACK -> MiuixIcons.Back
    SettingsIconRole.MIUIX_TRANSITION_BLUR -> MiuixIcons.Background
    SettingsIconRole.TOP_DOCK_GLASS -> MiuixIcons.HorizontalSplit
    SettingsIconRole.HOME_SEARCH_GLASS -> MiuixIcons.Search
    SettingsIconRole.BOTTOM_BAR_GLASS -> MiuixIcons.Sidebar
    SettingsIconRole.TOP_BAR_BLUR -> MiuixIcons.Background
    SettingsIconRole.HEADER_COLLAPSE -> MiuixIcons.ExpandLess
    SettingsIconRole.BOTTOM_BAR_BLUR -> MiuixIcons.Layers
    SettingsIconRole.FLOATING_BOTTOM_BAR -> MiuixIcons.MoreCircle
    SettingsIconRole.HARDWARE_DECODER -> MiuixIcons.Settings
    SettingsIconRole.PLAYBACK_SPEED -> MiuixIcons.Stopwatch
    SettingsIconRole.NATIVE_MIUIX_DIALOG -> MiuixIcons.Messages
    SettingsIconRole.LONG_PRESS_SPEED_HINT -> MiuixIcons.Stopwatch
    SettingsIconRole.RESUME_PLAYBACK_PROMPT -> MiuixIcons.Undo
    SettingsIconRole.STOP_ON_EXIT -> MiuixIcons.Pause
    SettingsIconRole.BACKGROUND_PLAYBACK -> MiuixIcons.Music
    SettingsIconRole.PLAYLIST_AUTO_CONTINUE -> MiuixIcons.Playlist
    SettingsIconRole.AUDIO_FOCUS -> MiuixIcons.VolumeUp
    SettingsIconRole.SLIDE_VOLUME_BRIGHTNESS -> MiuixIcons.VerticalSplit
    SettingsIconRole.PIP_DANMAKU -> MiuixIcons.Messages
    SettingsIconRole.DANMAKU_CLOUD_SYNC -> MiuixIcons.CloudFill
    SettingsIconRole.AUDIO_MODE_PIP -> MiuixIcons.Music
    SettingsIconRole.PLAYER_DIAGNOSTICS -> MiuixIcons.AppRecording
    SettingsIconRole.QUALITY_WARNING -> MiuixIcons.Report
    SettingsIconRole.SUBTITLE -> MiuixIcons.Notes
    SettingsIconRole.COMMENT_DECORATION -> MiuixIcons.Community
    SettingsIconRole.AI_SUMMARY -> MiuixIcons.MindMap
    SettingsIconRole.VIDEO_NOTE -> MiuixIcons.NotesFill
    SettingsIconRole.LIKE_INTERACTION -> MiuixIcons.FavoritesFill
    SettingsIconRole.VIDEO_DESCRIPTION -> MiuixIcons.ConvertFile
    SettingsIconRole.FULLSCREEN_ORIENTATION -> MiuixIcons.RotateLeft
    SettingsIconRole.HORIZONTAL_ADAPTATION -> MiuixIcons.HorizontalSplit
    SettingsIconRole.FULLSCREEN_GESTURE_REVERSE -> MiuixIcons.VerticalSplit
    SettingsIconRole.IMMERSIVE_STATUS_BAR -> MiuixIcons.ExpandMore
    SettingsIconRole.AUTO_ENTER_FULLSCREEN -> MiuixIcons.ZoomOut
    SettingsIconRole.AUTO_EXIT_FULLSCREEN -> MiuixIcons.Close2
    SettingsIconRole.FULLSCREEN_LOCK -> MiuixIcons.Pin
    SettingsIconRole.FULLSCREEN_SCREENSHOT -> MiuixIcons.ScreenCapture
    SettingsIconRole.CLEAN_SCREENSHOT -> MiuixIcons.Photos
    SettingsIconRole.BATTERY_STATUS -> MiuixIcons.Phone
    SettingsIconRole.TIME_STATUS -> MiuixIcons.WorldClock
    SettingsIconRole.PLAYER_ACTIONS -> MiuixIcons.More
    SettingsIconRole.PRIVACY_CONTENT_AUTHENTICATION -> MiuixIcons.Scan
    SettingsIconRole.PLAYER_STATS -> MiuixIcons.Sort
    SettingsIconRole.PLAYER_DIAGNOSTIC_LOGS -> MiuixIcons.File
    SettingsIconRole.QUALITY_WARNING_ONCE -> MiuixIcons.Alarm
    SettingsIconRole.DIRECTED_TRAFFIC -> MiuixIcons.SearchDevice
    SettingsIconRole.AUTO_HIGHEST_QUALITY -> MiuixIcons.TopDownloads
    SettingsIconRole.AUTO_PLAY_ON_OPEN -> MiuixIcons.Play
    SettingsIconRole.STARTUP_PORTRAIT_FEED -> MiuixIcons.GridView
    SettingsIconRole.HOME_HERO_AUTOPLAY -> MiuixIcons.Recording
    SettingsIconRole.AUTO_PLAY_NEXT -> MiuixIcons.Playlist
    SettingsIconRole.VIDEO_NOTE_COLLAPSE -> MiuixIcons.MoreCircle
    SettingsIconRole.INTERACTIVE_COMMANDS -> MiuixIcons.MicSlash
    SettingsIconRole.PORTRAIT_SWIPE_FULLSCREEN -> MiuixIcons.ExpandMore
    SettingsIconRole.CENTER_SWIPE_FULLSCREEN -> MiuixIcons.ScreenCapture
    SettingsIconRole.SYSTEM_BRIGHTNESS -> MiuixIcons.Show
    SettingsIconRole.APP_ICON -> MiuixIcons.All
    SettingsIconRole.HOME_CARD_STATS_COMPACT -> MiuixIcons.Sort
    SettingsIconRole.HOME_HERO_CAROUSEL -> MiuixIcons.Album
    SettingsIconRole.HOME_ONLINE_COUNT -> MiuixIcons.ContactsBook
    SettingsIconRole.PORTRAIT_STORY_ENTRY -> MiuixIcons.Phone
    SettingsIconRole.DISPLAY_SCALE -> MiuixIcons.ZoomOut
    SettingsIconRole.UI_ENTRANCE_ANIMATION -> MiuixIcons.Forward
    SettingsIconRole.FULLSCREEN_SWIPE_BACK -> MiuixIcons.ChevronBackward
    SettingsIconRole.FOLLOW_BUTTON -> MiuixIcons.AddCircle
    SettingsIconRole.PRIVACY_HISTORY -> MiuixIcons.Recent
    SettingsIconRole.CUSTOM_MD3_COLOR -> MiuixIcons.Theme
    SettingsIconRole.THEME_LIGHT_BACKGROUND -> MiuixIcons.Background
    SettingsIconRole.THEME_LIGHT_PRIMARY_TEXT -> MiuixIcons.Rename
    SettingsIconRole.THEME_LIGHT_SECONDARY_TEXT -> MiuixIcons.Notes
    SettingsIconRole.THEME_LIGHT_CONTROL -> MiuixIcons.Tune
    SettingsIconRole.THEME_DARK_BACKGROUND -> MiuixIcons.Background
    SettingsIconRole.THEME_DARK_PRIMARY_TEXT -> MiuixIcons.Rename
    SettingsIconRole.THEME_DARK_SECONDARY_TEXT -> MiuixIcons.NotesFill
    SettingsIconRole.THEME_DARK_CONTROL -> MiuixIcons.Settings
    SettingsIconRole.DEVELOPER_CRASH_TRACKING -> MiuixIcons.Report
    SettingsIconRole.DEVELOPER_ANALYTICS -> MiuixIcons.Sort
    SettingsIconRole.APP_VERSION -> MiuixIcons.Info
    SettingsIconRole.BOTTOM_BAR_GLASS_PREVIEW -> MiuixIcons.Sidebar
    SettingsIconRole.ADVANCED_COLOR -> MiuixIcons.Theme
    SettingsIconRole.CAST_BUTTON -> MiuixIcons.ScreenMirroring
    SettingsIconRole.PROGRESS_PEAK_DANMAKU -> MiuixIcons.Sort
    SettingsIconRole.IMAGE_3D_PAGE -> MiuixIcons.Photos
    SettingsIconRole.SPLASH_ICON_ANIMATION -> MiuixIcons.Recording
    SettingsIconRole.NAV_ICON_CROSS_SCALE -> MiuixIcons.Replace
    SettingsIconRole.SUB_REPLY_LOADED_COUNT -> MiuixIcons.Answer
    SettingsIconRole.COMMENT_VISIBILITY_CHECK -> MiuixIcons.Tasks
    SettingsIconRole.PORTRAIT_AMBIENT_HAZE -> MiuixIcons.Background
    SettingsIconRole.BACK_TO_TOP -> MiuixIcons.ExpandLess
    SettingsIconRole.HOME_HEADER_COLLAPSE -> MiuixIcons.ExpandLess
    SettingsIconRole.PGC_TIMELINE -> MiuixIcons.Months
    SettingsIconRole.RELATED_VIDEO_TRANSITION -> MiuixIcons.Playlist
    SettingsIconRole.RETURN_GESTURE_POSE -> MiuixIcons.Undo
    SettingsIconRole.AUTO_SKIP_OP_ED -> MiuixIcons.Trim
    SettingsIconRole.BLUR_INTENSITY -> MiuixIcons.Filter
    SettingsIconRole.HAPTIC_FEEDBACK -> MiuixIcons.Phone
    SettingsIconRole.REMEMBER_PLAYBACK_SPEED -> MiuixIcons.Recent
    SettingsIconRole.SPACE_PLAYED_VIDEO_LOCATE -> MiuixIcons.Search
    SettingsIconRole.IMAGE_LONG_PRESS_ACTION -> MiuixIcons.MoreCircle
    SettingsIconRole.PLAYER_COLLAPSE_PAUSE -> MiuixIcons.Pause
    SettingsIconRole.BOTTOM_BAR_SEARCH -> MiuixIcons.Search
    SettingsIconRole.DATA_SAVER_COVER_QUALITY -> MiuixIcons.Download
    SettingsIconRole.SEGMENT_LOADING_COMPATIBILITY -> MiuixIcons.Merge
}
