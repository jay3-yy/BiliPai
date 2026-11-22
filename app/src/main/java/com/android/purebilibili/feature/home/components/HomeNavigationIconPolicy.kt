package com.android.purebilibili.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.android.purebilibili.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Recording
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.TopDownloads

private enum class HomeNavigationIconRole {
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

internal enum class HomeNavigationIconSource {
    MIUIX,
}

private fun resolveHomeNavigationIconRole(tabId: String): HomeNavigationIconRole = when (tabId.trim().uppercase()) {
    "HOME", "RECOMMEND" -> HomeNavigationIconRole.HOME
    "DYNAMIC" -> HomeNavigationIconRole.DYNAMIC
    "STORY" -> HomeNavigationIconRole.STORY
    "HISTORY" -> HomeNavigationIconRole.HISTORY
    "LISTEN_VIDEO" -> HomeNavigationIconRole.LISTEN_VIDEO
    "PROFILE" -> HomeNavigationIconRole.PROFILE
    "FAVORITE" -> HomeNavigationIconRole.FAVORITE
    "LIVE" -> HomeNavigationIconRole.LIVE
    "WATCHLATER", "WATCH_LATER" -> HomeNavigationIconRole.WATCH_LATER
    "SETTINGS" -> HomeNavigationIconRole.SETTINGS
    "PLUGINS" -> HomeNavigationIconRole.PLUGINS
    "FOLLOW" -> HomeNavigationIconRole.FOLLOW
    "POPULAR" -> HomeNavigationIconRole.POPULAR
    "ANIME" -> HomeNavigationIconRole.ANIME
    "GAME" -> HomeNavigationIconRole.GAME
    "PARTITION" -> HomeNavigationIconRole.PARTITION
    "KNOWLEDGE" -> HomeNavigationIconRole.KNOWLEDGE
    "TECH" -> HomeNavigationIconRole.TECH
    "SUBSCRIPTIONS" -> HomeNavigationIconRole.SUBSCRIPTIONS
    else -> HomeNavigationIconRole.HOME
}

internal fun resolveMiuixPreferredHomeNavigationIconSource(
    tabId: String,
): HomeNavigationIconSource {
    resolveHomeNavigationIconRole(tabId)
    return HomeNavigationIconSource.MIUIX
}

/**
 * 首页底栏、侧栏和顶部分区的唯一图标入口。
 */
@Composable
internal fun resolveMiuixPreferredHomeNavigationIcon(
    tabId: String,
    selected: Boolean = false,
): ImageVector {
    val role = resolveHomeNavigationIconRole(tabId)
    return resolveMiuixHomeNavigationIcon(role, selected)
}

/** Top tabs share the same selected glyph for the same role. */
private fun resolveMiuixNavigationFilledResource(role: HomeNavigationIconRole): Int? = when (role) {
    HomeNavigationIconRole.HOME -> R.drawable.ms_home_fill_24
    HomeNavigationIconRole.DYNAMIC -> R.drawable.bp_nav_community_filled_24
    HomeNavigationIconRole.STORY, HomeNavigationIconRole.ANIME -> R.drawable.ic_home_nav_story_filled
    HomeNavigationIconRole.HISTORY -> R.drawable.ms_history_fill_24
    HomeNavigationIconRole.LISTEN_VIDEO -> R.drawable.bp_nav_music_filled_24
    HomeNavigationIconRole.PROFILE -> R.drawable.ic_home_nav_profile_filled
    HomeNavigationIconRole.FAVORITE -> R.drawable.bp_nav_favorite_filled_24
    HomeNavigationIconRole.LIVE -> R.drawable.ic_home_nav_live_filled
    HomeNavigationIconRole.WATCH_LATER -> R.drawable.ms_watch_later_fill_24
    HomeNavigationIconRole.SETTINGS -> null
    HomeNavigationIconRole.PLUGINS -> R.drawable.bp_nav_folder_filled_24
    HomeNavigationIconRole.FOLLOW -> R.drawable.ms_person_fill_24
    HomeNavigationIconRole.POPULAR -> R.drawable.ms_trending_up_fill_24
    HomeNavigationIconRole.GAME -> R.drawable.bp_nav_store_filled_24
    HomeNavigationIconRole.PARTITION -> R.drawable.ms_grid_view_fill_24
    HomeNavigationIconRole.KNOWLEDGE -> R.drawable.bp_nav_notes_filled_24
    HomeNavigationIconRole.TECH -> R.drawable.bp_nav_palette_filled_24
    HomeNavigationIconRole.SUBSCRIPTIONS -> R.drawable.ms_rss_feed_24
}

/**
 * 底栏与侧栏固定使用同一个 Miuix 字形，选中态由颜色、胶囊和标签字重表达。
 * 顶部分区仍沿用 [resolveMiuixPreferredHomeNavigationIcon] 的共享映射。
 */
@Suppress("UNUSED_PARAMETER")
internal fun resolveMiuixBottomNavigationIcon(
    item: BottomNavItem,
    selected: Boolean,
): ImageVector = when (item) {
    BottomNavItem.HOME -> MiuixIcons.Regular.Home
    BottomNavItem.DYNAMIC -> MiuixIcons.Regular.Community
    BottomNavItem.STORY -> MiuixIcons.Regular.Play
    BottomNavItem.HISTORY -> MiuixIcons.Regular.Recent
    BottomNavItem.LISTEN_VIDEO -> MiuixIcons.Regular.Music
    BottomNavItem.PROFILE -> MiuixIcons.Regular.ContactsCircle
    BottomNavItem.FAVORITE -> MiuixIcons.Regular.Favorites
    BottomNavItem.LIVE -> MiuixIcons.Regular.Recording
    BottomNavItem.WATCHLATER -> MiuixIcons.Regular.Stopwatch
    BottomNavItem.SETTINGS -> MiuixIcons.Regular.Settings
    BottomNavItem.PLUGINS -> MiuixIcons.Regular.Folder
}

@Composable
private fun resolveMiuixHomeNavigationIcon(
    role: HomeNavigationIconRole,
    selected: Boolean,
): ImageVector {
    if (selected) {
        val filledResource = resolveMiuixNavigationFilledResource(role)
        if (filledResource != null) return ImageVector.vectorResource(filledResource)
    }
    return when (role) {
        HomeNavigationIconRole.HOME -> ImageVector.vectorResource(R.drawable.bp_nav_home_outline_24)
        HomeNavigationIconRole.DYNAMIC -> MiuixIcons.Light.Community
        HomeNavigationIconRole.STORY -> MiuixIcons.Light.Play
        HomeNavigationIconRole.HISTORY -> ImageVector.vectorResource(R.drawable.bp_nav_history_outline_24)
        HomeNavigationIconRole.LISTEN_VIDEO -> MiuixIcons.Light.Music
        HomeNavigationIconRole.PROFILE -> MiuixIcons.Light.ContactsCircle
        HomeNavigationIconRole.FAVORITE -> MiuixIcons.Light.Favorites
        HomeNavigationIconRole.LIVE -> MiuixIcons.Light.Recording
        HomeNavigationIconRole.WATCH_LATER -> MiuixIcons.Light.Stopwatch
        HomeNavigationIconRole.SETTINGS -> if (selected) MiuixIcons.Demibold.Settings else MiuixIcons.Light.Settings
        HomeNavigationIconRole.PLUGINS -> MiuixIcons.Light.Folder
        HomeNavigationIconRole.FOLLOW -> MiuixIcons.Light.Contacts
        HomeNavigationIconRole.POPULAR -> MiuixIcons.Light.TopDownloads
        HomeNavigationIconRole.ANIME -> MiuixIcons.Light.Play
        HomeNavigationIconRole.GAME -> MiuixIcons.Light.Store
        HomeNavigationIconRole.PARTITION -> MiuixIcons.Light.GridView
        HomeNavigationIconRole.KNOWLEDGE -> MiuixIcons.Light.Notes
        HomeNavigationIconRole.TECH -> MiuixIcons.Light.Theme
        HomeNavigationIconRole.SUBSCRIPTIONS -> ImageVector.vectorResource(R.drawable.ms_rss_feed_24)
    }
}
