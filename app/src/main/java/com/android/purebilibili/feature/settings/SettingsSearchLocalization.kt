package com.android.purebilibili.feature.settings

import androidx.annotation.StringRes
import com.android.purebilibili.R

@StringRes
internal fun settingsSearchResultTitleResource(settingId: String): Int? = when (settingId) {
    "animation.card_transition_enabled" -> R.string.animation_title_card_transition
    "legacy.001" -> R.string.settings_search_legacy_001_title
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
    "legacy.001" -> R.string.settings_search_legacy_001_summary
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
