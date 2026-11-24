package com.android.purebilibili.feature.settings

import androidx.annotation.StringRes
import com.android.purebilibili.R

@StringRes
internal fun SettingsRootCategory.titleResource(): Int = when (canonicalSettingsRootCategory(this)) {
    SettingsRootCategory.APPEARANCE_THEME -> R.string.settings_category_appearance_title
    SettingsRootCategory.ANIMATION_HAPTICS -> R.string.animation_effects_title
    SettingsRootCategory.PLAYBACK_QUALITY -> R.string.settings_category_playback_title
    SettingsRootCategory.HOME_RECOMMENDATION -> R.string.settings_category_home_title
    SettingsRootCategory.NAVIGATION_INTERACTION -> R.string.settings_category_navigation_title
    SettingsRootCategory.PRIVACY_PERMISSION -> R.string.settings_category_privacy_title
    SettingsRootCategory.STORAGE_BACKUP -> R.string.settings_category_storage_title
    SettingsRootCategory.PLUGINS_EXTENSIONS -> R.string.settings_category_plugins_title
    SettingsRootCategory.GAMEPAD_CONTROL -> R.string.settings_category_gamepad_title
    SettingsRootCategory.SYSTEM_ABOUT -> R.string.settings_category_support_title
    else -> R.string.settings_title
}

@StringRes
internal fun SettingsRootCategory.subtitleResource(): Int = when (canonicalSettingsRootCategory(this)) {
    SettingsRootCategory.APPEARANCE_THEME -> R.string.settings_category_appearance_summary
    SettingsRootCategory.ANIMATION_HAPTICS -> R.string.settings_category_animation_summary
    SettingsRootCategory.PLAYBACK_QUALITY -> R.string.settings_category_playback_summary
    SettingsRootCategory.HOME_RECOMMENDATION -> R.string.settings_category_home_summary
    SettingsRootCategory.NAVIGATION_INTERACTION -> R.string.settings_category_navigation_summary
    SettingsRootCategory.PRIVACY_PERMISSION -> R.string.settings_category_privacy_summary
    SettingsRootCategory.STORAGE_BACKUP -> R.string.settings_category_storage_summary
    SettingsRootCategory.PLUGINS_EXTENSIONS -> R.string.settings_category_plugins_summary
    SettingsRootCategory.GAMEPAD_CONTROL -> R.string.settings_category_gamepad_summary
    SettingsRootCategory.SYSTEM_ABOUT -> R.string.settings_category_support_summary
    else -> R.string.settings_title
}
