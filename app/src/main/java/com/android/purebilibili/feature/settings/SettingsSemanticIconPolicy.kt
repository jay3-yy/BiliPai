package com.android.purebilibili.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.android.purebilibili.R
import com.android.purebilibili.core.store.SettingsIconGlyphs
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy

internal enum class SettingsIconRole(
    @DrawableRes val original: Int,
    @DrawableRes val phosphor: Int = original,
) {
    INTERFACE_THEME(R.drawable.ms_color_lens_24, R.drawable.ph_palette_24),
    HOME_FEED(R.drawable.ms_home_24, R.drawable.ph_house_24),
    NAVIGATION(R.drawable.ms_dashboard_24, R.drawable.ph_compass_24),
    PARTITION_RAIL_POSITION(R.drawable.ic_settings_partition_rail_position_24),
    PLAYBACK_QUALITY(R.drawable.ms_high_quality_24, R.drawable.ph_high_definition_24),
    FULLSCREEN_GESTURE(R.drawable.ms_touch_app_24, R.drawable.ph_hand_tap_24),
    COPY_TEXT(R.drawable.ms_content_copy_24, R.drawable.ph_copy_24),
    INTERACTION_COMMENT(R.drawable.ms_chat_bubble_outline_24, R.drawable.ph_chat_circle_24),
    DATA_BACKUP(R.drawable.ms_backup_24, R.drawable.ph_archive_24),
    PRIVACY_PERMISSION(R.drawable.ms_lock_24, R.drawable.ph_lock_24),
    DIAGNOSTICS(R.drawable.ms_terminal_24, R.drawable.ph_terminal_window_24),
    ABOUT_SUPPORT(R.drawable.ms_info_24, R.drawable.ph_info_24),
    APPEARANCE(R.drawable.ms_palette_24, R.drawable.ph_paint_brush_broad_24),
    ABOUT_CARD_SHAPE(R.drawable.ic_settings_about_card_shape_24),
    ANIMATION(R.drawable.ms_animation_24, R.drawable.ph_film_strip_24),
    PLAYBACK(R.drawable.ms_play_circle_24, R.drawable.ph_play_circle_24),
    BOTTOM_BAR(R.drawable.ms_widgets_24, R.drawable.ph_square_half_bottom_24),
    PERMISSION(R.drawable.ms_security_24, R.drawable.ph_shield_check_24),
    SPACE_PRIVACY(R.drawable.ms_visibility_24, R.drawable.ph_eye_24),
    SPACE_PRIVACY_HIDDEN(R.drawable.ph_eye_closed_24),
    LOGIN_ACTIVITY(R.drawable.ic_settings_login_activity_24),
    MESSAGE_NOTIFICATION(R.drawable.ms_notifications_24, R.drawable.ph_bell_24),
    BLOCKED_LIST(R.drawable.ms_block_24, R.drawable.ph_prohibit_24),
    SETTINGS_SHARE(R.drawable.ms_share_24, R.drawable.ph_share_network_24),
    WEBDAV_BACKUP(R.drawable.ms_cloud_upload_24, R.drawable.ph_cloud_arrow_up_24),
    DOWNLOAD_PATH(R.drawable.ms_folder_24, R.drawable.ph_folder_24),
    IMAGE_SAVE_PATH(R.drawable.ms_photo_24, R.drawable.ph_image_24),
    LIST_IMAGE_QUALITY(R.drawable.ic_settings_list_image_quality_24),
    PREVIEW_IMAGE_QUALITY(R.drawable.ic_settings_preview_image_quality_24),
    DARK_IMAGE_DIM(R.drawable.ic_settings_dark_image_dim_24),
    SCROLL_FEEDBACK(R.drawable.ic_settings_scroll_feedback_24),
    PLAYER_CONTROLS_TIMEOUT(R.drawable.ic_settings_player_controls_timeout_24),
    REFRESH_LOCATOR_DURATION(R.drawable.ic_settings_refresh_locator_duration_24),
    PLAYER_ADJUSTMENT_PERSISTENCE(R.drawable.ic_settings_player_adjustments_24),
    PLAYER_ROTATION_SENSITIVITY(R.drawable.ic_settings_rotation_sensitivity_24),
    DYNAMIC_VISIBILITY_CHECK(R.drawable.ic_settings_dynamic_visibility_check_24),
    RECOMMEND_RETENTION(R.drawable.ic_settings_recommend_retention_24),
    CLEAR_CACHE(R.drawable.ms_delete_outline_24, R.drawable.ph_trash_24),
    AUTO_CLEAR_CACHE(R.drawable.ms_auto_delete_24),
    CACHE_CAPACITY_LIMIT(R.drawable.ms_storage_fill_24, R.drawable.ph_hard_drives_24),
    PLUGINS(R.drawable.ms_extension_24, R.drawable.ph_puzzle_piece_24),
    NEARBY_HANDOFF(R.drawable.ic_settings_nearby_handoff_24),
    EXPORT_LOGS(R.drawable.ms_article_24, R.drawable.ph_file_text_24),
    OPEN_SOURCE_LICENSES(R.drawable.ms_gavel_24, R.drawable.ph_scales_24),
    OPEN_SOURCE_HOME(R.drawable.ms_open_in_new_24, R.drawable.ph_arrow_square_out_24),
    CHECK_UPDATE(R.drawable.ms_system_update_24, R.drawable.ph_download_24),
    VIEW_RELEASE_NOTES(R.drawable.ms_newspaper_24, R.drawable.ph_newspaper_24),
    REPLAY_ONBOARDING(R.drawable.ms_replay_24, R.drawable.ph_arrow_counter_clockwise_24),
    TIPS(R.drawable.ms_lightbulb_24, R.drawable.ph_lightbulb_24),
    KEYBOARD_SHORTCUTS(R.drawable.ms_keyboard_24, R.drawable.ph_keyboard_24),
    VIDEO_COMPANION(R.drawable.ic_settings_video_split_24),
    OPEN_LINKS(R.drawable.ms_link_24, R.drawable.ph_link_24),
    DONATE(R.drawable.ms_card_giftcard_24, R.drawable.ph_gift_24),
    DISCLAIMER(R.drawable.ms_warning_amber_24, R.drawable.ph_warning_24),
    RELEASE_CHANNEL(R.drawable.ms_rocket_24, R.drawable.ph_rocket_launch_24),
    CRASH_TRACKING(R.drawable.ms_bug_report_24, R.drawable.ph_bug_24),
    ANALYTICS(R.drawable.ms_analytics_24, R.drawable.ph_chart_line_24),
    PERFORMANCE_CAPTURE(R.drawable.ms_speed_24, R.drawable.ph_pulse_24),
    PERFORMANCE_MARK(R.drawable.ic_settings_performance_mark_24),
    PERFORMANCE_REPORT(R.drawable.ms_share_24, R.drawable.ph_file_arrow_up_24),
    PERFORMANCE_PREVIEW(R.drawable.ms_find_in_page_24, R.drawable.ph_file_magnifying_glass_24),
    PERFORMANCE_HISTORY(R.drawable.ic_settings_performance_history_24),
    FEED_API(R.drawable.ms_rss_feed_24, R.drawable.ph_rss_24),
    REFRESH_COUNT(R.drawable.ms_refresh_24, R.drawable.ph_arrows_clockwise_24),
    DYNAMIC_PREVIEW_TEXT(R.drawable.ms_text_snippet_24, R.drawable.ph_article_24),
    DYNAMIC_TAB_VISIBILITY(R.drawable.ms_visibility_24, R.drawable.ph_eye_24),
    DYNAMIC_UP_LIST(R.drawable.ic_settings_dynamic_up_list_24),
    EASTER_EGG(R.drawable.ms_auto_awesome_24, R.drawable.ph_sparkle_24),
    AUTO_CHECK_UPDATE(R.drawable.ms_update_24, R.drawable.ph_clock_countdown_24),
    BUILD_SOURCE(R.drawable.ms_tag_24, R.drawable.ph_tag_24),
    BUILD_FINGERPRINT(R.drawable.ms_fingerprint_24, R.drawable.ph_fingerprint_24),
    BUILD_VERIFICATION(R.drawable.ms_verified_user_24, R.drawable.ph_shield_chevron_24),
    ANDROID_LIQUID_GLASS(R.drawable.ms_water_drop_24, R.drawable.ph_drop_24),
    DYNAMIC_COLOR(R.drawable.ms_format_color_text_24, R.drawable.ph_text_a_underline_24),
    THEME_COLOR_PICKER(R.drawable.ms_colorize_24, R.drawable.ph_eyedropper_24),
    SEED_PALETTE(R.drawable.ic_settings_palette_swatches_24),
    COLOR_STYLE(R.drawable.ms_brush_24, R.drawable.ph_paint_brush_24),
    COLOR_SPEC(R.drawable.ms_auto_fix_high_24, R.drawable.ph_magic_wand_24),
    APP_LANGUAGE(R.drawable.ms_language_24, R.drawable.ph_translate_24),
    FONT_FILE(R.drawable.ms_font_download_24),
    SPLASH_WALLPAPER(R.drawable.ms_wallpaper_24, R.drawable.ph_image_square_24),
    RANDOM_WALLPAPER(R.drawable.ms_shuffle_24, R.drawable.ph_shuffle_24),
    DISPLAY_STYLE(R.drawable.ms_view_carousel_24, R.drawable.ph_slideshow_24),
    HOME_COVER_GLASS(R.drawable.ms_opacity_24, R.drawable.ph_drop_half_24),
    VIDEO_DURATION_BADGES(R.drawable.ms_timer_24, R.drawable.ph_timer_24),
    HOME_INFO_GLASS(R.drawable.ms_badge_24, R.drawable.ph_identification_badge_24),
    HOME_WALLPAPER(R.drawable.ms_image_24, R.drawable.ph_panorama_24),
    WALLPAPER_EFFECT(R.drawable.ms_blur_on_24),
    SETTINGS_CONTAINER_MORPH(R.drawable.ms_bottom_panel_open_24, R.drawable.ph_corners_out_24),
    SETTINGS_CONTAINER_BLUR(R.drawable.ms_lens_blur_24, R.drawable.ic_settings_container_blur_24),
    HOME_UP_BADGES(R.drawable.ms_workspace_premium_24, R.drawable.ph_medal_24),
    HOME_REFRESH_TIP(R.drawable.ms_history_24, R.drawable.ph_clock_counter_clockwise_24),
    HOME_REFRESH_UNDO(R.drawable.ms_undo_24, R.drawable.ph_arrow_u_up_left_24),
    HOME_CARD_DYNAMIC_TINT(R.drawable.ms_colors_24, R.drawable.ph_selection_all_24),
    NOW_PLAYING_BAR(R.drawable.ms_bottom_panel_open_24, R.drawable.ph_music_note_simple_24),
    AUDIO_MODE_ENTRY(R.drawable.ms_library_music_24, R.drawable.ph_music_notes_24),
    AUDIO_TITLE_AUTO_HIDE(R.drawable.ms_bottom_panel_close_24, R.drawable.ph_align_bottom_24),
    AUDIO_BAR_DELETE_BUTTON(R.drawable.ms_delete_outline_24, R.drawable.ph_backspace_24),
    LOUDNESS_NORMALIZATION(R.drawable.ms_instant_mix_24, R.drawable.ph_sliders_24),
    AUDIO_SPECTRUM(R.drawable.ic_settings_cover_pulse_24),
    STARTUP_RESUME(R.drawable.ms_resume_24, R.drawable.ph_play_pause_24),
    NEXT_VIDEO_HINT(R.drawable.ms_preview_24, R.drawable.ph_eyes_24),
    VIDEO_CONTENT_NOTICE(R.drawable.ms_report_24, R.drawable.ph_flag_24),
    DYNAMIC_CONTENT_NOTICE(R.drawable.ic_settings_dynamic_warning_24),
    MEMBER_SHOP(R.drawable.ic_settings_member_shop_24),
    BLOCK_GOODS_DYNAMIC(R.drawable.ic_settings_block_goods_dynamic_24),
    BLOCK_GOODS_REPLY(R.drawable.ic_settings_block_goods_reply_24),
    COMMENT_BLOCKED_KEYWORDS(R.drawable.ic_settings_comment_keywords_24, R.drawable.ph_chat_text_24),
    AI_COMMENT_FILTER(R.drawable.ic_settings_ai_comment_filter_24, R.drawable.ph_robot_24),
    REPEATED_COMMENT_FILTER(R.drawable.ms_content_copy_24, R.drawable.ph_copy_simple_24),
    CONTENT_FILTER_HISTORY(R.drawable.ms_fact_check_24, R.drawable.ph_list_checks_24),
    COMMENT_IP_LOCATION(R.drawable.ic_settings_comment_ip_location_24),
    COMMENT_EXACT_TIME(R.drawable.ms_calendar_clock_24, R.drawable.ph_calendar_dots_24),
    COMMENT_AVATAR_PUSH(R.drawable.ic_settings_comment_avatar_push_24),
    SUB_REPLY_PREVIEW_LIKES(R.drawable.ic_settings_sub_reply_likes_24),
    VERTICAL_RECOMMENDATIONS(R.drawable.ms_view_stream_24, R.drawable.ph_rows_24),
    COMPACT_PLAYER_CONTROLS(R.drawable.ms_collapse_content_24, R.drawable.ph_arrows_in_simple_24),
    VIDEO_AMBIENT_LIGHT(R.drawable.ms_lightbulb_circle_24, R.drawable.ph_lightbulb_filament_24),
    DRAG_FEEDBACK(R.drawable.ms_drag_pan_24, R.drawable.ph_hand_grabbing_24),
    LIQUID_INDICATOR_WOBBLE(R.drawable.ic_settings_indicator_wobble_24),
    LIQUID_INDICATOR_DYNAMIC_LENGTH(R.drawable.ic_settings_indicator_dynamic_length_24),
    SKELETON_BREATHING(R.drawable.ic_settings_skeleton_breathing_24),
    PROGRESSIVE_TOP_BLUR(R.drawable.ic_settings_progressive_top_blur_24),
    PROGRESSIVE_BLUR_RENDERER(R.drawable.ic_settings_progressive_blur_renderer_24),
    PROGRESSIVE_TOP_FADE(R.drawable.ic_settings_progressive_top_fade_24),
    KEEP_TOP_SEARCH(R.drawable.ic_settings_top_search_24),
    LINKED_DOCK_MERGE(R.drawable.ms_merge_24, R.drawable.ph_arrows_merge_24),
    BOTTOM_BAR_SCROLL_COMPACT(R.drawable.ic_settings_bottom_bar_compact_24),
    LIST_SCOPED_SEARCH(R.drawable.ms_find_in_page_24, R.drawable.ph_list_magnifying_glass_24),
    NOTIFICATION_DELIVERY_MODE(R.drawable.ms_notification_settings_24, R.drawable.ph_bell_simple_ringing_24),
    HOME_UP_AVATAR(R.drawable.ms_account_circle_24, R.drawable.ph_user_circle_24),
    FULL_VIDEO_CARD_CONTENT(R.drawable.ms_notes_24, R.drawable.ph_article_24),
    ONLINE_COUNT(R.drawable.ms_groups_24, R.drawable.ph_users_24),
    GRID_COLUMNS(R.drawable.ms_grid_view_24, R.drawable.ph_grid_four_24),
    HOME_CARD_WIDTH(R.drawable.ms_width_normal_24, R.drawable.ph_arrows_out_line_horizontal_24),
    CARD_ENTRANCE_ANIMATION(R.drawable.ms_auto_awesome_motion_24, R.drawable.ph_stack_24),
    CARD_TRANSITION_ANIMATION(R.drawable.ms_sync_alt_24, R.drawable.ph_arrows_left_right_24),
    LIVE_SURFACE_TRANSITION(R.drawable.ms_movie_24, R.drawable.ph_film_slate_24),
    PREDICTIVE_BACK(R.drawable.ms_arrow_back_24, R.drawable.ph_arrow_left_24),
    PREDICTIVE_BACK_GESTURE(R.drawable.ic_settings_predictive_back_24),
    MIUIX_TRANSITION_BLUR(R.drawable.ms_gradient_24),
    TOP_DOCK_GLASS(R.drawable.ms_layers_24, R.drawable.ph_stack_simple_24),
    HOME_SEARCH_GLASS(R.drawable.ms_manage_search_24, R.drawable.ph_list_magnifying_glass_24),
    BOTTOM_BAR_GLASS(R.drawable.ms_blur_circular_24),
    TOP_BAR_BLUR(R.drawable.ms_view_headline_24),
    HEADER_COLLAPSE(R.drawable.ms_keyboard_arrow_up_24, R.drawable.ph_caret_up_24),
    BOTTOM_BAR_BLUR(R.drawable.ms_blur_linear_24),
    FLOATING_BOTTOM_BAR(R.drawable.ms_view_agenda_24, R.drawable.ph_layout_24),
    HARDWARE_DECODER(R.drawable.ms_memory_24, R.drawable.ph_cpu_24),
    PLAYBACK_SPEED(R.drawable.ms_speed_24, R.drawable.ph_gauge_24),
    NATIVE_MIUIX_DIALOG(R.drawable.ms_dialogs_24, R.drawable.ph_app_window_24),
    LONG_PRESS_SPEED_HINT(R.drawable.ms_visibility_off_24, R.drawable.ph_eye_slash_24),
    RESUME_PLAYBACK_PROMPT(R.drawable.ms_restore_24, R.drawable.ph_arrow_u_down_left_24),
    CONTINUE_PART_NOTICE(R.drawable.ic_settings_part_resume_24),
    STOP_ON_EXIT(R.drawable.ms_stop_circle_24, R.drawable.ph_stop_circle_24),
    BACKGROUND_PLAYBACK(R.drawable.ms_music_note_24, R.drawable.ph_music_note_24),
    PLAYLIST_AUTO_CONTINUE(R.drawable.ms_queue_play_next_24, R.drawable.ph_queue_24),
    AUDIO_FOCUS(R.drawable.ms_headphones_24, R.drawable.ph_headphones_24),
    SLIDE_VOLUME_BRIGHTNESS(R.drawable.ms_swap_vert_24, R.drawable.ph_arrows_down_up_24),
    PIP_DANMAKU(R.drawable.ms_textsms_24, R.drawable.ph_chat_centered_text_24),
    DANMAKU_CLOUD_SYNC(R.drawable.ms_cloud_sync_24, R.drawable.ph_cloud_24),
    AUDIO_MODE_PIP(R.drawable.ms_picture_in_picture_24, R.drawable.ph_picture_in_picture_24),
    PLAYER_DIAGNOSTICS(R.drawable.ms_query_stats_24, R.drawable.ph_chart_line_up_24),
    QUALITY_WARNING(R.drawable.ms_report_problem_24, R.drawable.ph_warning_circle_24),
    SUBTITLE(R.drawable.ms_subtitles_24, R.drawable.ph_subtitles_24),
    COMMENT_DECORATION(R.drawable.ms_mode_comment_24, R.drawable.ph_chat_teardrop_24),
    AI_SUMMARY(R.drawable.ms_smart_toy_24, R.drawable.ph_robot_24),
    VIDEO_NOTE(R.drawable.ms_edit_note_24, R.drawable.ph_note_pencil_24),
    LIKE_INTERACTION(R.drawable.ms_thumb_up_off_alt_24, R.drawable.ph_thumbs_up_24),
    FAVORITE_TAP_MODE(R.drawable.ms_collections_bookmark_24, R.drawable.ph_bookmarks_24),
    VIDEO_DESCRIPTION(R.drawable.ms_subject_24, R.drawable.ph_text_align_justify_24),
    FULLSCREEN_ORIENTATION(R.drawable.ms_screen_rotation_24, R.drawable.ph_device_rotate_24),
    HORIZONTAL_ADAPTATION(R.drawable.ms_aspect_ratio_24, R.drawable.ph_frame_corners_24),
    LANDSCAPE_BACK_COMMENTS(R.drawable.ic_settings_landscape_back_comments_24),
    FULLSCREEN_GESTURE_REVERSE(R.drawable.ms_swipe_vertical_24, R.drawable.ph_arrows_vertical_24),
    IMMERSIVE_STATUS_BAR(R.drawable.ms_fullscreen_24, R.drawable.ph_corners_out_24),
    AUTO_ENTER_FULLSCREEN(R.drawable.ms_open_in_full_24, R.drawable.ph_arrows_out_24),
    AUTO_EXIT_FULLSCREEN(R.drawable.ms_fullscreen_exit_24, R.drawable.ph_corners_in_24),
    FULLSCREEN_LOCK(R.drawable.ms_screen_lock_rotation_24, R.drawable.ph_lock_key_24),
    FULLSCREEN_SCREENSHOT(R.drawable.ms_screenshot_24),
    CLEAN_SCREENSHOT(R.drawable.ms_screenshot_monitor_24),
    BATTERY_STATUS(R.drawable.ms_battery_full_24, R.drawable.ph_battery_full_24),
    TIME_STATUS(R.drawable.ms_access_time_24, R.drawable.ph_clock_24),
    PLAYER_ACTIONS(R.drawable.ms_more_horiz_24, R.drawable.ph_dots_three_24),
    PLAYER_ACTION_COUNTS(R.drawable.ic_settings_action_counts_24),
    PRIVACY_CONTENT_AUTHENTICATION(R.drawable.ms_verified_24, R.drawable.ph_seal_check_24),
    PLAYER_STATS(R.drawable.ms_insert_chart_outlined_24, R.drawable.ph_chart_bar_24),
    PLAYER_DIAGNOSTIC_LOGS(R.drawable.ms_report_gmailerrorred_24, R.drawable.ph_file_x_24),
    QUALITY_WARNING_ONCE(R.drawable.ms_notification_important_24, R.drawable.ph_bell_ringing_24),
    DIRECTED_TRAFFIC(R.drawable.ms_network_locked_24),
    AUTO_HIGHEST_QUALITY(R.drawable.ms_settings_suggest_24),
    AUTO_PLAY_ON_OPEN(R.drawable.ms_play_arrow_24, R.drawable.ph_play_24),
    STARTUP_PORTRAIT_FEED(R.drawable.ms_vertical_align_top_24, R.drawable.ph_arrow_line_up_24),
    HOME_HERO_AUTOPLAY(R.drawable.ms_smart_display_24, R.drawable.ph_monitor_play_24),
    AUTO_PLAY_NEXT(R.drawable.ms_playlist_play_24, R.drawable.ph_playlist_24),
    VIDEO_NOTE_COLLAPSE(R.drawable.ms_short_text_24, R.drawable.ph_text_align_right_24),
    DANMAKU_TAP(R.drawable.ic_settings_danmaku_tap_24),
    INTERACTIVE_COMMANDS(R.drawable.ms_comments_disabled_24, R.drawable.ph_chat_slash_24),
    PORTRAIT_SWIPE_FULLSCREEN(R.drawable.ms_swipe_up_24, R.drawable.ph_hand_arrow_up_24),
    CENTER_SWIPE_FULLSCREEN(R.drawable.ms_swipe_24, R.drawable.ph_hand_swipe_left_24),
    SYSTEM_BRIGHTNESS(R.drawable.ms_brightness_medium_24, R.drawable.ph_sun_dim_24),
    APP_ICON(R.drawable.ms_apps_24, R.drawable.ph_app_window_24),
    HOME_CARD_STATS_COMPACT(R.drawable.ms_stacked_bar_chart_24, R.drawable.ph_chart_bar_horizontal_24),
    HOME_HERO_CAROUSEL(R.drawable.ms_view_day_24, R.drawable.ph_images_square_24),
    HOME_ONLINE_COUNT(R.drawable.ms_groups_24, R.drawable.ph_users_three_24),
    PORTRAIT_STORY_ENTRY(R.drawable.ms_stay_current_portrait_24, R.drawable.ph_device_mobile_24),
    DISPLAY_SCALE(R.drawable.ms_zoom_out_map_24, R.drawable.ph_arrows_out_cardinal_24),
    UI_ENTRANCE_ANIMATION(R.drawable.ms_motion_photos_on_24, R.drawable.ph_aperture_24),
    FULLSCREEN_SWIPE_BACK(R.drawable.ms_swipe_right_24, R.drawable.ph_hand_swipe_right_24),
    FOLLOW_BUTTON(R.drawable.ms_person_add_24, R.drawable.ph_user_plus_24),
    PRIVACY_HISTORY(R.drawable.ms_history_toggle_off_24),
    PRIVACY_SEARCH_RECOMMENDATION(R.drawable.ms_search_24, R.drawable.ph_magnifying_glass_24),
    SEARCH_HOT_SECTION(R.drawable.ms_trending_up_24, R.drawable.ph_trend_up_24),
    SEARCH_HISTORY_SECTION(R.drawable.ms_history_24, R.drawable.ph_clock_afternoon_24),
    SEARCH_DISCOVER_SECTION(R.drawable.ms_manage_search_24, R.drawable.ph_binoculars_24),
    PERSONAL_RECAP(R.drawable.ms_query_stats_24, R.drawable.ph_chart_bar_24),
    CUSTOM_MD3_COLOR(R.drawable.ms_format_paint_24, R.drawable.ph_paint_roller_24),
    THEME_LIGHT_BACKGROUND(R.drawable.ms_light_mode_24, R.drawable.ph_sun_24),
    THEME_LIGHT_PRIMARY_TEXT(R.drawable.ms_text_fields_24, R.drawable.ph_text_t_24),
    THEME_LIGHT_SECONDARY_TEXT(R.drawable.ms_format_size_24, R.drawable.ph_text_align_left_24),
    THEME_LIGHT_CONTROL(R.drawable.ms_tune_24, R.drawable.ph_sliders_24),
    THEME_DARK_BACKGROUND(R.drawable.ms_dark_mode_24, R.drawable.ph_moon_24),
    THEME_DARK_PRIMARY_TEXT(R.drawable.ms_text_format_24, R.drawable.ph_text_h_24),
    THEME_DARK_SECONDARY_TEXT(R.drawable.ms_subtitles_off_24, R.drawable.ph_text_indent_24),
    THEME_DARK_CONTROL(R.drawable.ms_control_point_24, R.drawable.ph_plus_circle_24),
    DEVELOPER_CRASH_TRACKING(R.drawable.ms_health_and_safety_24, R.drawable.ph_first_aid_24),
    DEVELOPER_ANALYTICS(R.drawable.ms_data_usage_24, R.drawable.ph_chart_donut_24),
    APP_VERSION(R.drawable.ms_new_releases_24, R.drawable.ph_seal_24),
    BOTTOM_BAR_GLASS_PREVIEW(R.drawable.ms_lens_blur_24),
    ADVANCED_COLOR(R.drawable.ms_invert_colors_24, R.drawable.ph_drop_half_bottom_24),
    CAST_BUTTON(R.drawable.ms_cast_24, R.drawable.ph_screencast_24),
    PROGRESS_PEAK_DANMAKU(R.drawable.ms_graphic_eq_24, R.drawable.ph_waveform_24),
    IMAGE_3D_PAGE(R.drawable.ms_3d_rotation_24, R.drawable.ph_cube_24),
    SPLASH_ICON_ANIMATION(R.drawable.ms_filter_frames_24),
    NAV_ICON_CROSS_SCALE(R.drawable.ms_compare_arrows_24, R.drawable.ph_arrows_horizontal_24),
    SUB_REPLY_LOADED_COUNT(R.drawable.ms_numbers_24, R.drawable.ph_hash_24),
    COMMENT_VISIBILITY_CHECK(R.drawable.ms_fact_check_24, R.drawable.ph_list_checks_24),
    PORTRAIT_AMBIENT_HAZE(R.drawable.ic_settings_portrait_ambient_24),
    BACK_TO_TOP(R.drawable.ms_keyboard_double_arrow_up_24, R.drawable.ph_caret_double_up_24),
    HOME_HEADER_COLLAPSE(R.drawable.ms_compress_24, R.drawable.ph_arrows_in_24),
    PGC_TIMELINE(R.drawable.ms_calendar_month_24, R.drawable.ph_calendar_24),
    AUTO_SKIP_OP_ED(R.drawable.ms_skip_next_24, R.drawable.ph_skip_forward_24),
    BLUR_INTENSITY(R.drawable.ms_flare_24),
    HAPTIC_FEEDBACK(R.drawable.ms_touch_app_fill_24, R.drawable.ph_vibrate_24),
    REMEMBER_PLAYBACK_SPEED(R.drawable.ms_history_fill_24, R.drawable.ph_clock_countdown_24),
    SPACE_PLAYED_VIDEO_LOCATE(R.drawable.ms_search_24, R.drawable.ph_file_magnifying_glass_24),
    IMAGE_LONG_PRESS_ACTION(R.drawable.ms_photo_library_24, R.drawable.ph_images_24),
    PLAYER_COLLAPSE_PAUSE(R.drawable.ms_pause_24, R.drawable.ph_pause_24),
    BOTTOM_BAR_SEARCH(R.drawable.ms_search_fill_24, R.drawable.ph_magnifying_glass_plus_24),
    DATA_SAVER_COVER_QUALITY(R.drawable.ms_wifi_24, R.drawable.ph_wifi_high_24),
    RESILIENT_VIDEO_BUFFER(R.drawable.ic_settings_resilient_buffer_24),
    SEGMENT_LOADING_COMPATIBILITY(R.drawable.ms_cloud_download_24, R.drawable.ph_cloud_arrow_down_24),
    NOTIFICATION_SCOPE_MESSAGE(R.drawable.ms_mail_24, R.drawable.ph_envelope_simple_24),
    NOTIFICATION_SCOPE_REPLY(R.drawable.ms_reply_24, R.drawable.ph_arrow_bend_up_left_24),
    NOTIFICATION_SCOPE_AT_ME(R.drawable.ms_alternate_email_24, R.drawable.ph_at_24),
    NOTIFICATION_SCOPE_LIKE(R.drawable.ms_thumb_up_fill_24, R.drawable.ph_thumbs_up_24),
    NOTIFICATION_SCOPE_SYSTEM(R.drawable.ms_campaign_24, R.drawable.ph_megaphone_24),
    NOTIFICATION_SCOPE_DYNAMIC_UP(R.drawable.ms_person_24, R.drawable.ph_user_24),
    NOTIFICATION_SCOPE_LIVE(R.drawable.ms_live_tv_24, R.drawable.ph_broadcast_24),
    VIDEO_CODEC(R.drawable.ms_video_settings_24, R.drawable.ph_file_video_24),
    FALLBACK_VIDEO_CODEC(R.drawable.ms_video_camera_back_24, R.drawable.ph_video_camera_24),
    AUDIO_LYRICS(R.drawable.ms_lyrics_24, R.drawable.ph_microphone_stage_24),
    WIFI_PLAYBACK_QUALITY(R.drawable.ms_network_wifi_24, R.drawable.ph_wifi_medium_24),
    MOBILE_PLAYBACK_QUALITY(R.drawable.ms_signal_cellular_alt_24, R.drawable.ph_cell_signal_full_24),
    AUDIO_QUALITY(R.drawable.ms_speaker_24, R.drawable.ph_speaker_high_24),
    DATA_SAVER_MODE(R.drawable.ms_data_saver_on_24, R.drawable.ph_chart_pie_slice_24),
    PLAYBACK_COMPLETION(R.drawable.ms_next_plan_24, R.drawable.ph_arrow_circle_right_24),
    SUBTITLE_TRACK(R.drawable.ms_closed_caption_24, R.drawable.ph_closed_captioning_24),
    COMMENT_REPLY_PREVIEW(R.drawable.ms_comment_24, R.drawable.ph_chats_24),
    SEEK_FORWARD(R.drawable.ms_forward_10_24),
    SEEK_BACKWARD(R.drawable.ms_replay_10_24),
    PORTRAIT_COLLAPSE_MODE(R.drawable.ms_collapse_content_24, R.drawable.ph_arrows_in_line_vertical_24),
    TABLET_COMMENT_PANEL_WIDTH(R.drawable.ms_width_normal_24, R.drawable.ph_arrows_out_line_horizontal_24),
    TABLET_SECONDARY_TAB(R.drawable.ms_view_column_2_24, R.drawable.ph_columns_24),
    LANDSCAPE_HOLD(R.drawable.ms_screen_rotation_alt_24),
    SWIPE_SEEK(R.drawable.ic_settings_swipe_seek_24),
    SCREENSHOT_GESTURE(R.drawable.ic_settings_screenshot_gesture_24),
    APP_SCREENSHOT(R.drawable.ms_screenshot_region_24, R.drawable.ph_selection_24),
    CHAPTER_DISPLAY(R.drawable.ic_settings_chapter_display_24),
    BOTTOM_PROGRESS(R.drawable.ic_settings_bottom_progress_24),
    EXIT_PLAYER_MODE(R.drawable.ic_settings_exit_player_mode_24),
    AMBIENT_STRENGTH(R.drawable.ic_settings_ambient_strength_24),
    AMBIENT_POWER_SAVING(R.drawable.ic_settings_ambient_power_saving_24),
    EXIT_DIRECTION(R.drawable.ic_settings_exit_direction_24),
    TRANSITION_MODE(R.drawable.ic_settings_transition_mode_24),
    UI_STYLE(R.drawable.ms_view_carousel_24, R.drawable.ph_layout_24),
    LIST_ITEM_STYLE(R.drawable.ms_view_stream_24, R.drawable.ph_list_dashes_24),
    ICON_STYLE(R.drawable.ms_apps_24, R.drawable.ph_squares_four_24),
    LINE_ICON_COLOR(R.drawable.ic_settings_line_icon_color_24),
    CHOICE_MENU_STYLE(R.drawable.ms_dialogs_24, R.drawable.ph_list_checks_24),
    THEME_MODE(R.drawable.ms_light_mode_24, R.drawable.ph_circle_half_tilt_24),
    DARK_THEME_STYLE(R.drawable.ic_settings_dark_theme_style_24),
    MD3_COLOR_SOURCE(R.drawable.ms_colorize_24, R.drawable.ph_swatches_24),
    SCREEN_REFRESH_RATE(R.drawable.ms_monitor_24, R.drawable.ph_desktop_24),
    FONT_SIZE(R.drawable.ms_format_size_24, R.drawable.ph_text_aa_24),
    FONT_WEIGHT(R.drawable.ms_text_format_24, R.drawable.ph_text_b_24),
    DISPLAY_SCALE_LARGE(R.drawable.ic_settings_display_scale_large_24),
    SPLASH_ANIMATION_STYLE(R.drawable.ic_settings_splash_animation_24),
    GRID_SHARE_COLUMNS(R.drawable.ic_settings_grid_share_24),
    COMPACT_GRID_COLUMNS(R.drawable.ic_settings_grid_compact_24),
    WIDE_PORTRAIT_GRID_COLUMNS(R.drawable.ic_settings_grid_wide_portrait_24),
    WIDE_GRID_COLUMNS(R.drawable.ic_settings_grid_wide_24),
    PINCH_GRID_COLUMNS(R.drawable.ic_settings_pinch_grid_24),
    CARD_COVER_RATIO(R.drawable.ms_aspect_ratio_24, R.drawable.ph_rectangle_dashed_24),
    VIDEO_TAG_SIZE(R.drawable.ms_format_size_24, R.drawable.ph_tag_chevron_24),
    WALLPAPER_SCOPE(R.drawable.ic_settings_wallpaper_scope_24),
    LIQUID_CONTROLS_THEME_COLOR(R.drawable.ms_color_lens_24, R.drawable.ph_drop_simple_24),
    SWITCH_THUMB_MODE(R.drawable.ms_toggle_on_24, R.drawable.ph_toggle_right_24),
    HAPTIC_SCENE_SWITCH(R.drawable.ms_vibration_24, R.drawable.ph_device_mobile_24),
    GLASS_SCENE_SWITCH(R.drawable.ms_opacity_24, R.drawable.ph_drop_half_bottom_24),
    SCHEDULED_DARK_MODE(R.drawable.ic_settings_scheduled_dark_mode_24),
    SCHEDULED_DARK_TIME(R.drawable.ms_access_time_24, R.drawable.ph_moon_stars_24),
    MAID_ICON_APPEARANCE(R.drawable.ic_settings_maid_icon_24),
    ACCOUNT_LOGIN(R.drawable.ms_account_circle_24, R.drawable.ph_sign_in_24),
    REMEMBER_FILTER_STATE(R.drawable.ms_history_fill_24, R.drawable.ph_funnel_simple_24),
    FSR_SHARPNESS(R.drawable.ms_deblur_24, R.drawable.ph_crosshair_simple_24),
    FILTER_EXEMPT_FOLLOWED(R.drawable.ms_person_check_24, R.drawable.ph_user_check_24),
    FILTER_HOT_VIDEOS(R.drawable.ms_local_fire_department_24, R.drawable.ph_fire_24),
    FILTER_RANKING(R.drawable.ms_leaderboard_24, R.drawable.ph_ranking_24),
    SPONSOR_MARKER(R.drawable.ic_settings_sponsor_marker_24),
    SETTINGS_ICON_GLYPHS(R.drawable.ph_shapes_24),
}

@Composable
internal fun rememberSettingsSemanticIcon(role: SettingsIconRole): ImageVector =
    rememberSettingsVector(resolveSettingsIconResource(role, currentSettingsIconGlyphs()))

internal fun resolveSettingsIconResource(
    role: SettingsIconRole,
    glyphs: SettingsIconGlyphs = SettingsIconGlyphs.PHOSPHOR,
): Int = glyphs.selectResource(phosphor = role.phosphor, original = role.original)

@Composable
@ReadOnlyComposable
internal fun currentSettingsIconGlyphs(): SettingsIconGlyphs =
    LocalSettingsThemeConfiguration.current?.settingsIconGlyphs ?: SettingsIconGlyphs.PHOSPHOR

internal fun SettingsIconGlyphs.selectResource(
    @DrawableRes phosphor: Int,
    @DrawableRes original: Int,
): Int = if (this == SettingsIconGlyphs.ORIGINAL) original else phosphor

@Composable
@ReadOnlyComposable
internal fun settingsIconResource(@DrawableRes phosphor: Int, @DrawableRes original: Int): Int =
    currentSettingsIconGlyphs().selectResource(phosphor, original)

@Composable
internal fun rememberSettingsVector(@DrawableRes phosphor: Int, @DrawableRes original: Int): ImageVector =
    rememberSettingsVector(settingsIconResource(phosphor, original))

@Composable
internal fun rememberSettingsVector(
    @DrawableRes phosphor: Int,
    original: @Composable () -> ImageVector,
): ImageVector = if (currentSettingsIconGlyphs() == SettingsIconGlyphs.ORIGINAL) {
    original()
} else {
    rememberSettingsVector(phosphor)
}

@Composable
internal fun rememberOriginalThemeAwareSettingsIcon(
    @DrawableRes material: Int,
    miuix: ImageVector,
): ImageVector = when (rememberAppSemanticVisualPolicy().effectiveIconFamily) {
    AppSemanticIconFamily.MATERIAL -> rememberSettingsVector(material)
    AppSemanticIconFamily.MIUIX -> miuix
}

/** 设置界面的 VectorDrawable 入口。 */
@Composable
internal fun rememberSettingsVector(
    drawableResId: Int,
): ImageVector = ImageVector.vectorResource(drawableResId)

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
    SettingsSearchTarget.KEYBOARD_SHORTCUTS -> SettingsIconRole.KEYBOARD_SHORTCUTS
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
