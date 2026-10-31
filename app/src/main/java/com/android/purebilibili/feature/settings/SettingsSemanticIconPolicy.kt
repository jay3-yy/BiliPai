package com.android.purebilibili.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy

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
    SPACE_PRIVACY,
    LOGIN_ACTIVITY,
    MESSAGE_NOTIFICATION,
    BLOCKED_LIST,
    SETTINGS_SHARE,
    WEBDAV_BACKUP,
    DOWNLOAD_PATH,
    IMAGE_SAVE_PATH,
    LIST_IMAGE_QUALITY,
    PREVIEW_IMAGE_QUALITY,
    DARK_IMAGE_DIM,
    SCROLL_FEEDBACK,
    PLAYER_CONTROLS_TIMEOUT,
    PLAYER_ADJUSTMENT_PERSISTENCE,
    PLAYER_ROTATION_SENSITIVITY,
    DYNAMIC_VISIBILITY_CHECK,
    RECOMMEND_RETENTION,
    CLEAR_CACHE,
    AUTO_CLEAR_CACHE,
    CACHE_CAPACITY_LIMIT,
    PLUGINS,
    NEARBY_HANDOFF,
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
    SEED_PALETTE,
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
    SETTINGS_CONTAINER_MORPH,
    SETTINGS_CONTAINER_BLUR,
    HOME_UP_BADGES,
    HOME_REFRESH_TIP,
    HOME_REFRESH_UNDO,
    HOME_CARD_DYNAMIC_TINT,
    NOW_PLAYING_BAR,
    AUDIO_MODE_ENTRY,
    AUDIO_TITLE_AUTO_HIDE,
    LOUDNESS_NORMALIZATION,
    AUDIO_SPECTRUM,
    STARTUP_RESUME,
    NEXT_VIDEO_HINT,
    VIDEO_CONTENT_NOTICE,
    DYNAMIC_CONTENT_NOTICE,
    MEMBER_SHOP,
    BLOCK_GOODS_DYNAMIC,
    BLOCK_GOODS_REPLY,
    COMMENT_EXACT_TIME,
    COMMENT_AVATAR_PUSH,
    VERTICAL_RECOMMENDATIONS,
    COMPACT_PLAYER_CONTROLS,
    VIDEO_AMBIENT_LIGHT,
    DRAG_FEEDBACK,
    LIQUID_INDICATOR_WOBBLE,
    RETURN_CONTENT_FOLLOW,
    RETURN_GESTURE_TRANSLATION,
    SKELETON_BREATHING,
    PROGRESSIVE_TOP_BLUR,
    PROGRESSIVE_BLUR_RENDERER,
    PROGRESSIVE_TOP_FADE,
    KEEP_TOP_SEARCH,
    LINKED_DOCK_MERGE,
    BOTTOM_BAR_SCROLL_COMPACT,
    LIST_SCOPED_SEARCH,
    NOTIFICATION_DELIVERY_MODE,
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
    CONTINUE_PART_NOTICE,
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
    FAVORITE_TAP_MODE,
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
    PLAYER_ACTION_COUNTS,
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
    PRIVACY_SEARCH_RECOMMENDATION,
    SEARCH_HOT_SECTION,
    SEARCH_HISTORY_SECTION,
    SEARCH_DISCOVER_SECTION,
    PERSONAL_RECAP,
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
    RESILIENT_VIDEO_BUFFER,
    SEGMENT_LOADING_COMPATIBILITY,
    NOTIFICATION_SCOPE_MESSAGE,
    NOTIFICATION_SCOPE_REPLY,
    NOTIFICATION_SCOPE_AT_ME,
    NOTIFICATION_SCOPE_LIKE,
    NOTIFICATION_SCOPE_SYSTEM,
    NOTIFICATION_SCOPE_DYNAMIC_UP,
    NOTIFICATION_SCOPE_LIVE,
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
    SettingsIconRole.LOGIN_ACTIVITY -> R.drawable.ic_settings_login_activity_24
    SettingsIconRole.SPACE_PRIVACY -> R.drawable.ms_visibility_24
    SettingsIconRole.PERMISSION -> R.drawable.ms_security_24
    SettingsIconRole.MESSAGE_NOTIFICATION -> R.drawable.ms_notifications_24
    SettingsIconRole.BLOCKED_LIST -> R.drawable.ms_block_24
    SettingsIconRole.SETTINGS_SHARE -> R.drawable.ms_share_24
    SettingsIconRole.WEBDAV_BACKUP -> R.drawable.ms_cloud_upload_24
    SettingsIconRole.DOWNLOAD_PATH -> R.drawable.ms_folder_24
    SettingsIconRole.LIST_IMAGE_QUALITY -> R.drawable.ic_settings_list_image_quality_24
    SettingsIconRole.PREVIEW_IMAGE_QUALITY -> R.drawable.ic_settings_preview_image_quality_24
    SettingsIconRole.DARK_IMAGE_DIM -> R.drawable.ic_settings_dark_image_dim_24
    SettingsIconRole.PLAYER_ADJUSTMENT_PERSISTENCE -> R.drawable.ic_settings_player_adjustments_24
    SettingsIconRole.PLAYER_ROTATION_SENSITIVITY -> R.drawable.ic_settings_rotation_sensitivity_24
    SettingsIconRole.PLAYER_CONTROLS_TIMEOUT -> R.drawable.ic_settings_player_controls_timeout_24
    SettingsIconRole.DYNAMIC_VISIBILITY_CHECK -> R.drawable.ic_settings_dynamic_visibility_check_24
    SettingsIconRole.SCROLL_FEEDBACK -> R.drawable.ic_settings_scroll_feedback_24
    SettingsIconRole.RECOMMEND_RETENTION -> R.drawable.ic_settings_recommend_retention_24
    SettingsIconRole.IMAGE_SAVE_PATH -> R.drawable.ms_photo_24
    SettingsIconRole.CLEAR_CACHE -> R.drawable.ms_delete_outline_24
    SettingsIconRole.AUTO_CLEAR_CACHE -> R.drawable.ms_auto_delete_24
    SettingsIconRole.CACHE_CAPACITY_LIMIT -> R.drawable.ms_storage_fill_24
    SettingsIconRole.PLUGINS -> R.drawable.ms_extension_24
    SettingsIconRole.NEARBY_HANDOFF -> R.drawable.ic_settings_nearby_handoff_24
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
    SettingsIconRole.SEED_PALETTE -> R.drawable.ic_settings_palette_swatches_24
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
    SettingsIconRole.SETTINGS_CONTAINER_BLUR -> R.drawable.ms_lens_blur_24
    SettingsIconRole.SETTINGS_CONTAINER_MORPH -> R.drawable.ms_bottom_panel_open_24
    SettingsIconRole.HOME_UP_BADGES -> R.drawable.ms_workspace_premium_24
    SettingsIconRole.HOME_REFRESH_TIP -> R.drawable.ms_history_24
    SettingsIconRole.HOME_REFRESH_UNDO -> R.drawable.ms_undo_24
    SettingsIconRole.HOME_CARD_DYNAMIC_TINT -> R.drawable.ms_colors_24
    SettingsIconRole.NOW_PLAYING_BAR -> R.drawable.ms_bottom_panel_open_24
    SettingsIconRole.AUDIO_MODE_ENTRY -> R.drawable.ms_library_music_24
    SettingsIconRole.AUDIO_TITLE_AUTO_HIDE -> R.drawable.ms_bottom_panel_close_24
    SettingsIconRole.LOUDNESS_NORMALIZATION -> R.drawable.ms_instant_mix_24
    SettingsIconRole.AUDIO_SPECTRUM -> R.drawable.ic_settings_cover_pulse_24
    SettingsIconRole.STARTUP_RESUME -> R.drawable.ms_resume_24
    SettingsIconRole.NEXT_VIDEO_HINT -> R.drawable.ms_preview_24
    SettingsIconRole.VIDEO_CONTENT_NOTICE -> R.drawable.ms_report_24
    SettingsIconRole.MEMBER_SHOP -> R.drawable.ic_settings_member_shop_24
    SettingsIconRole.DYNAMIC_CONTENT_NOTICE -> R.drawable.ic_settings_dynamic_warning_24
    SettingsIconRole.BLOCK_GOODS_DYNAMIC -> R.drawable.ic_settings_block_goods_dynamic_24
    SettingsIconRole.BLOCK_GOODS_REPLY -> R.drawable.ic_settings_block_goods_reply_24
    SettingsIconRole.COMMENT_EXACT_TIME -> R.drawable.ms_calendar_clock_24
    SettingsIconRole.COMMENT_AVATAR_PUSH -> R.drawable.ic_settings_comment_avatar_push_24
    SettingsIconRole.VERTICAL_RECOMMENDATIONS -> R.drawable.ms_view_stream_24
    SettingsIconRole.COMPACT_PLAYER_CONTROLS -> R.drawable.ms_collapse_content_24
    SettingsIconRole.VIDEO_AMBIENT_LIGHT -> R.drawable.ms_lightbulb_circle_24
    SettingsIconRole.DRAG_FEEDBACK -> R.drawable.ms_drag_pan_24
    SettingsIconRole.LIQUID_INDICATOR_WOBBLE -> R.drawable.ic_settings_indicator_wobble_24
    SettingsIconRole.RETURN_CONTENT_FOLLOW -> R.drawable.ms_move_down_24
    SettingsIconRole.RETURN_GESTURE_TRANSLATION -> R.drawable.ms_drag_indicator_24
    SettingsIconRole.SKELETON_BREATHING -> R.drawable.ic_settings_skeleton_breathing_24
    SettingsIconRole.PROGRESSIVE_TOP_BLUR -> R.drawable.ic_settings_progressive_top_blur_24
    SettingsIconRole.PROGRESSIVE_BLUR_RENDERER -> R.drawable.ic_settings_progressive_blur_renderer_24
    SettingsIconRole.PROGRESSIVE_TOP_FADE -> R.drawable.ic_settings_progressive_top_fade_24
    SettingsIconRole.KEEP_TOP_SEARCH -> R.drawable.ic_settings_top_search_24
    SettingsIconRole.BOTTOM_BAR_SCROLL_COMPACT -> R.drawable.ic_settings_bottom_bar_compact_24
    SettingsIconRole.LINKED_DOCK_MERGE -> R.drawable.ms_merge_24
    SettingsIconRole.LIST_SCOPED_SEARCH -> R.drawable.ms_find_in_page_24
    SettingsIconRole.NOTIFICATION_DELIVERY_MODE -> R.drawable.ms_notification_settings_24
    SettingsIconRole.HOME_UP_AVATAR -> R.drawable.ms_account_circle_24
    SettingsIconRole.FULL_VIDEO_CARD_CONTENT -> R.drawable.ms_notes_24
    SettingsIconRole.ONLINE_COUNT -> R.drawable.ms_groups_24
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
    SettingsIconRole.NATIVE_MIUIX_DIALOG -> R.drawable.ms_dialogs_24
    SettingsIconRole.LONG_PRESS_SPEED_HINT -> R.drawable.ms_visibility_off_24
    SettingsIconRole.RESUME_PLAYBACK_PROMPT -> R.drawable.ms_restore_24
    SettingsIconRole.CONTINUE_PART_NOTICE -> R.drawable.ic_settings_part_resume_24
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
    SettingsIconRole.FAVORITE_TAP_MODE -> R.drawable.ms_collections_bookmark_24
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
    SettingsIconRole.PLAYER_ACTION_COUNTS -> R.drawable.ic_settings_action_counts_24
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
    SettingsIconRole.SEARCH_HOT_SECTION -> R.drawable.ms_trending_up_24
    SettingsIconRole.SEARCH_HISTORY_SECTION -> R.drawable.ms_history_24
    SettingsIconRole.SEARCH_DISCOVER_SECTION -> R.drawable.ms_manage_search_24
    SettingsIconRole.PRIVACY_SEARCH_RECOMMENDATION -> R.drawable.ms_search_24
    SettingsIconRole.PERSONAL_RECAP -> R.drawable.ms_query_stats_24
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
    SettingsIconRole.PORTRAIT_AMBIENT_HAZE -> R.drawable.ic_settings_portrait_ambient_24
    SettingsIconRole.BACK_TO_TOP -> R.drawable.ms_keyboard_double_arrow_up_24
    SettingsIconRole.HOME_HEADER_COLLAPSE -> R.drawable.ms_compress_24
    SettingsIconRole.PGC_TIMELINE -> R.drawable.ms_calendar_month_24
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
    SettingsIconRole.RESILIENT_VIDEO_BUFFER -> R.drawable.ic_settings_resilient_buffer_24
    SettingsIconRole.SEGMENT_LOADING_COMPATIBILITY -> R.drawable.ms_cloud_download_24
    SettingsIconRole.NOTIFICATION_SCOPE_MESSAGE -> R.drawable.ms_mail_24
    SettingsIconRole.NOTIFICATION_SCOPE_REPLY -> R.drawable.ms_reply_24
    SettingsIconRole.NOTIFICATION_SCOPE_AT_ME -> R.drawable.ms_alternate_email_24
    SettingsIconRole.NOTIFICATION_SCOPE_LIKE -> R.drawable.ms_thumb_up_fill_24
    SettingsIconRole.NOTIFICATION_SCOPE_SYSTEM -> R.drawable.ms_campaign_24
    SettingsIconRole.NOTIFICATION_SCOPE_DYNAMIC_UP -> R.drawable.ms_person_24
    SettingsIconRole.NOTIFICATION_SCOPE_LIVE -> R.drawable.ms_live_tv_24
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
    SettingsSearchTarget.SPACE_PRIVACY -> SettingsIconRole.SPACE_PRIVACY
    SettingsSearchTarget.LOGIN_ACTIVITY -> SettingsIconRole.LOGIN_ACTIVITY
    SettingsSearchTarget.MESSAGE_NOTIFICATION -> SettingsIconRole.MESSAGE_NOTIFICATION
    SettingsSearchTarget.BLOCKED_LIST -> SettingsIconRole.BLOCKED_LIST
    SettingsSearchTarget.SETTINGS_SHARE -> SettingsIconRole.SETTINGS_SHARE
    SettingsSearchTarget.WEBDAV_BACKUP -> SettingsIconRole.WEBDAV_BACKUP
    SettingsSearchTarget.DOWNLOAD_PATH -> SettingsIconRole.DOWNLOAD_PATH
    SettingsSearchTarget.IMAGE_SAVE_PATH -> SettingsIconRole.IMAGE_SAVE_PATH
    SettingsSearchTarget.CLEAR_CACHE -> SettingsIconRole.CLEAR_CACHE
    SettingsSearchTarget.PLUGINS -> SettingsIconRole.PLUGINS
    SettingsSearchTarget.NEARBY_HANDOFF -> SettingsIconRole.NEARBY_HANDOFF
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
