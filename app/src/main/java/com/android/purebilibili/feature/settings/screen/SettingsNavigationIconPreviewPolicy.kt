package com.android.purebilibili.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.vectorResource
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.resolveMiuixBottomNavigationIcon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

private enum class SettingsNavigationIconRole {
    HOME,
    DYNAMIC,
    STORY,
    HISTORY,
    LISTEN_VIDEO,
    PROFILE,
    FAVORITE,
    LIVE,
    WATCH_LATER,
    SETTINGS,
    PLUGINS,
    FOLLOW,
    POPULAR,
    ANIME,
    GAME,
    PARTITION,
    KNOWLEDGE,
    TECH,
    SUBSCRIPTIONS,
}

internal fun resolveSettingsNavigationPreviewMaterialSymbolResource(
    tabId: String,
    selected: Boolean = false,
): Int = when (tabId.trim().uppercase()) {
    "HOME", "RECOMMEND" -> if (selected) R.drawable.ms_home_fill_24 else R.drawable.ms_home_24
    "DYNAMIC" -> if (selected) R.drawable.ms_notifications_fill_24 else R.drawable.ms_notifications_none_24
    "STORY" -> if (selected) R.drawable.ms_play_circle_fill_24 else R.drawable.ms_play_circle_outline_24
    "HISTORY" -> if (selected) R.drawable.ms_history_fill_24 else R.drawable.ms_history_24
    "LISTEN_VIDEO" -> if (selected) R.drawable.ms_library_music_fill_24 else R.drawable.ms_library_music_24
    "PROFILE", "FOLLOW" -> if (selected) R.drawable.ms_person_fill_24 else R.drawable.ms_person_24
    "FAVORITE" -> if (selected) R.drawable.ms_collections_bookmark_fill_24 else R.drawable.ms_collections_bookmark_24
    "LIVE" -> if (selected) R.drawable.ms_live_tv_fill_24 else R.drawable.ms_live_tv_24
    "WATCHLATER", "WATCH_LATER" -> if (selected) R.drawable.ms_watch_later_fill_24 else R.drawable.ms_watch_later_24
    "SETTINGS" -> if (selected) R.drawable.ms_settings_fill_24 else R.drawable.ms_settings_24
    "PLUGINS" -> if (selected) R.drawable.ms_extension_fill_24 else R.drawable.ms_extension_24
    "POPULAR" -> if (selected) R.drawable.ms_trending_up_fill_24 else R.drawable.ms_trending_up_24
    "ANIME" -> if (selected) R.drawable.ms_collections_bookmark_fill_24 else R.drawable.ms_collections_bookmark_24
    "GAME" -> if (selected) R.drawable.ms_sports_esports_fill_24 else R.drawable.ms_sports_esports_24
    "PARTITION" -> if (selected) R.drawable.ms_grid_view_fill_24 else R.drawable.ms_grid_view_24
    "KNOWLEDGE" -> if (selected) R.drawable.ms_lightbulb_fill_24 else R.drawable.ms_lightbulb_24
    "TECH" -> if (selected) R.drawable.ms_smart_toy_fill_24 else R.drawable.ms_smart_toy_24
    "SUBSCRIPTIONS" -> R.drawable.ms_rss_feed_24
    else -> if (selected) R.drawable.ms_home_fill_24 else R.drawable.ms_home_24
}

/** Uses the active native icon family so the navigation preview matches the runtime chrome. */
@Composable
internal fun resolveSettingsNavigationPreviewIcon(
    tabId: String,
    iconFamily: AppSemanticIconFamily,
    selected: Boolean = false,
    bottomBar: Boolean = false,
): ImageVector {
    if (bottomBar && iconFamily == AppSemanticIconFamily.MIUIX) {
        resolveSettingsBottomNavItem(tabId)?.let { item ->
            return resolveMiuixBottomNavigationIcon(item, selected)
        }
    }

    val role = when (tabId.trim().uppercase()) {
        "HOME", "RECOMMEND" -> SettingsNavigationIconRole.HOME
        "DYNAMIC" -> SettingsNavigationIconRole.DYNAMIC
        "STORY" -> SettingsNavigationIconRole.STORY
        "HISTORY" -> SettingsNavigationIconRole.HISTORY
        "LISTEN_VIDEO" -> SettingsNavigationIconRole.LISTEN_VIDEO
        "PROFILE" -> SettingsNavigationIconRole.PROFILE
        "FAVORITE" -> SettingsNavigationIconRole.FAVORITE
        "LIVE" -> SettingsNavigationIconRole.LIVE
        "WATCHLATER", "WATCH_LATER" -> SettingsNavigationIconRole.WATCH_LATER
        "SETTINGS" -> SettingsNavigationIconRole.SETTINGS
        "PLUGINS" -> SettingsNavigationIconRole.PLUGINS
        "FOLLOW" -> SettingsNavigationIconRole.FOLLOW
        "POPULAR" -> SettingsNavigationIconRole.POPULAR
        "ANIME" -> SettingsNavigationIconRole.ANIME
        "GAME" -> SettingsNavigationIconRole.GAME
        "PARTITION" -> SettingsNavigationIconRole.PARTITION
        "KNOWLEDGE" -> SettingsNavigationIconRole.KNOWLEDGE
        "TECH" -> SettingsNavigationIconRole.TECH
        "SUBSCRIPTIONS" -> SettingsNavigationIconRole.SUBSCRIPTIONS
        else -> SettingsNavigationIconRole.HOME
    }

    return when (iconFamily) {
        AppSemanticIconFamily.MIUIX -> when (role) {
            SettingsNavigationIconRole.HOME -> if (selected) MiuixIcons.Medium.Home else MiuixIcons.Home
            SettingsNavigationIconRole.DYNAMIC -> if (selected) MiuixIcons.Medium.Messages else MiuixIcons.Messages
            SettingsNavigationIconRole.STORY -> if (selected) MiuixIcons.Medium.Recording else MiuixIcons.Recording
            SettingsNavigationIconRole.HISTORY -> if (selected) MiuixIcons.Medium.Recent else MiuixIcons.Recent
            SettingsNavigationIconRole.LISTEN_VIDEO -> if (selected) MiuixIcons.Medium.Music else MiuixIcons.Music
            SettingsNavigationIconRole.PROFILE -> MiuixIcons.ContactsCircle
            SettingsNavigationIconRole.FAVORITE -> if (selected) MiuixIcons.FavoritesFill else MiuixIcons.Favorites
            SettingsNavigationIconRole.LIVE -> if (selected) MiuixIcons.Medium.RecordingTape else MiuixIcons.RecordingTape
            SettingsNavigationIconRole.WATCH_LATER -> if (selected) MiuixIcons.Medium.WorldClock else MiuixIcons.WorldClock
            SettingsNavigationIconRole.SETTINGS -> if (selected) MiuixIcons.Medium.Settings else MiuixIcons.Settings
            SettingsNavigationIconRole.PLUGINS -> if (selected) MiuixIcons.FolderFill else MiuixIcons.Folder
            SettingsNavigationIconRole.FOLLOW -> MiuixIcons.Contacts
            SettingsNavigationIconRole.POPULAR -> MiuixIcons.TopDownloads
            SettingsNavigationIconRole.ANIME -> if (selected) MiuixIcons.FavoritesFill else MiuixIcons.Favorites
            SettingsNavigationIconRole.GAME -> MiuixIcons.Store
            SettingsNavigationIconRole.PARTITION -> if (selected) MiuixIcons.Medium.GridView else MiuixIcons.GridView
            SettingsNavigationIconRole.KNOWLEDGE -> MiuixIcons.Notes
            SettingsNavigationIconRole.TECH -> MiuixIcons.MindMap
            SettingsNavigationIconRole.SUBSCRIPTIONS -> ImageVector.vectorResource(R.drawable.ms_rss_feed_24)
        }
        AppSemanticIconFamily.MATERIAL -> ImageVector.vectorResource(
            resolveSettingsNavigationPreviewMaterialSymbolResource(tabId, selected)
        )
    }
}

private fun resolveSettingsBottomNavItem(tabId: String): BottomNavItem? =
    when (tabId.trim().uppercase()) {
        "HOME", "RECOMMEND" -> BottomNavItem.HOME
        "DYNAMIC" -> BottomNavItem.DYNAMIC
        "STORY" -> BottomNavItem.STORY
        "HISTORY" -> BottomNavItem.HISTORY
        "LISTEN_VIDEO" -> BottomNavItem.LISTEN_VIDEO
        "PROFILE" -> BottomNavItem.PROFILE
        "FAVORITE" -> BottomNavItem.FAVORITE
        "LIVE" -> BottomNavItem.LIVE
        "WATCHLATER", "WATCH_LATER" -> BottomNavItem.WATCHLATER
        "SETTINGS" -> BottomNavItem.SETTINGS
        "PLUGINS" -> BottomNavItem.PLUGINS
        else -> null
    }
