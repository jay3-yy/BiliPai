package com.android.purebilibili.feature.settings

import androidx.annotation.StringRes
import com.android.purebilibili.R

@StringRes
internal fun settingsSearchResultTitleResource(settingId: String): Int? = when (settingId) {
    PROCESS_MEMORY_SETTING_ID -> R.string.process_memory_title
    SUB_REPLY_STYLE_SETTING_ID -> R.string.sub_reply_style_title
    "animation.predictive_back_enabled" -> R.string.animation_predictive_back_title
    "animation.card_transition_enabled" -> R.string.animation_title_card_transition
    CARD_TRANSITION_METHOD_SETTING_ID -> R.string.animation_card_transition_method_title
    CARD_REMOVAL_DISSOLVE_SETTING_ID -> R.string.animation_card_removal_dissolve_title
    "animation.app_navigation_settings.settings_container_morph_enabled" -> R.string.animation_settings_container_title
    "animation.page_transition_style" -> R.string.animation_back_title
    "animation.back_preview_progress" -> R.string.animation_back_preview_title
    "animation.back_exit_direction" -> R.string.animation_exit_direction_title
    "animation.header_blur_enabled" -> R.string.animation_title_header_blur
    "animation.progressive_top_blur_enabled" -> R.string.animation_title_progressive_blur
    "animation.maid_lottie_animation_enabled" -> R.string.animation_title_maid
    "appearance.splash_icon_animation_enabled" -> R.string.appearance_splash_animation_title
    "appearance.startup_animation_style" -> R.string.appearance_startup_animation_style_title
    PROGRESSIVE_BLUR_RENDERER_SETTING_ID -> R.string.animation_progressive_renderer_title
    SETTINGS_ICON_GLYPHS_SETTING_ID -> R.string.appearance_settings_icon_glyphs_title
    "legacy.001" -> SettingsRootCategory.APPEARANCE_THEME.titleResource()
    "legacy.045" -> SettingsRootCategory.ANIMATION_HAPTICS.titleResource()
    "legacy.002" -> R.string.settings_search_legacy_002_title
    "legacy.003" -> R.string.settings_search_legacy_003_title
    "legacy.004" -> R.string.settings_search_legacy_004_title
    "legacy.005" -> R.string.settings_search_legacy_005_title
    "legacy.006" -> R.string.settings_search_legacy_006_title
    "legacy.007" -> R.string.settings_search_legacy_007_title
    "legacy.008" -> R.string.settings_search_legacy_008_title
    "legacy.009" -> R.string.settings_search_legacy_009_title
    "legacy.010" -> R.string.settings_search_legacy_010_title
    "legacy.011" -> R.string.settings_search_legacy_011_title
    "legacy.012" -> R.string.settings_search_legacy_012_title
    "notification.enabled" -> R.string.notify_item_enabled
    "notification.mode" -> R.string.notify_item_mode
    "notification.notify_private_messages" -> R.string.notify_item_private_messages
    "notification.notify_replies" -> R.string.notify_item_replies
    "notification.notify_at_me" -> R.string.notify_item_at_me
    "notification.notify_likes" -> R.string.notify_item_likes
    "notification.notify_system_notices" -> R.string.notify_item_system
    "notification.notify_dynamic_updates" -> R.string.notify_item_dynamic
    "notification.notify_live_alerts" -> R.string.notify_item_live
    "notification.resident_enabled" -> R.string.notify_item_resident
    else -> null
}

@StringRes
internal fun settingsSearchResultSummaryResource(settingId: String): Int? = when (settingId) {
    PROCESS_MEMORY_SETTING_ID -> R.string.process_memory_summary
    SUB_REPLY_STYLE_SETTING_ID -> R.string.sub_reply_style_summary
    CARD_REMOVAL_DISSOLVE_SETTING_ID -> R.string.animation_card_removal_dissolve_summary
    SETTINGS_ICON_GLYPHS_SETTING_ID -> R.string.appearance_settings_icon_glyphs_summary
    "legacy.001" -> SettingsRootCategory.APPEARANCE_THEME.subtitleResource()
    "legacy.045" -> SettingsRootCategory.ANIMATION_HAPTICS.subtitleResource()
    "legacy.002" -> R.string.settings_search_legacy_002_summary
    "legacy.003" -> R.string.settings_search_legacy_003_summary
    "legacy.004" -> R.string.settings_search_legacy_004_summary
    "legacy.005" -> R.string.settings_search_legacy_005_summary
    "legacy.006" -> R.string.settings_search_legacy_006_summary
    "legacy.007" -> R.string.settings_search_legacy_007_summary
    "legacy.008" -> R.string.settings_search_legacy_008_summary
    "legacy.009" -> R.string.settings_search_legacy_009_summary
    "legacy.010" -> R.string.settings_search_legacy_010_summary
    "legacy.011" -> R.string.settings_search_legacy_011_summary
    "legacy.012" -> R.string.settings_search_legacy_012_summary
    "notification.enabled" -> R.string.notify_master_subtitle
    "notification.mode" -> R.string.notify_mode_subtitle
    "notification.notify_private_messages" -> R.string.notify_private_messages
    "notification.notify_replies" -> R.string.notify_replies
    "notification.notify_at_me" -> R.string.notify_at_me
    "notification.notify_likes" -> R.string.notify_likes
    "notification.notify_system_notices" -> R.string.notify_system
    "notification.notify_dynamic_updates" -> R.string.notify_dynamic
    "notification.notify_live_alerts" -> R.string.notify_live
    "notification.resident_enabled" -> R.string.notify_resident_enabled_desc
    else -> null
}
