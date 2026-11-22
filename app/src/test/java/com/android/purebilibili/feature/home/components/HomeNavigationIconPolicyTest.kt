package com.android.purebilibili.feature.home.components

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeNavigationIconPolicyTest {

    @Test
    fun `uses native Miuix icons for every home navigation role`() {
        listOf(
            "HOME",
            "DYNAMIC",
            "STORY",
            "HISTORY",
            "LISTEN_VIDEO",
            "PROFILE",
            "FAVORITE",
            "LIVE",
            "WATCHLATER",
            "SETTINGS",
            "PLUGINS",
            "FOLLOW",
            "POPULAR",
            "ANIME",
            "GAME",
            "PARTITION",
            "KNOWLEDGE",
            "TECH",
        ).forEach { tabId ->
            assertEquals(
                HomeNavigationIconSource.MIUIX,
                resolveMiuixPreferredHomeNavigationIconSource(tabId),
            )
        }
    }

    @Test
    fun `miuix bottom navigation keeps one Miuix glyph across selection`() {
        val expectedNames = mapOf(
            BottomNavItem.HOME to "Home.Regular",
            BottomNavItem.DYNAMIC to "Community.Regular",
            BottomNavItem.STORY to "Play.Regular",
            BottomNavItem.HISTORY to "Recent.Regular",
            BottomNavItem.LISTEN_VIDEO to "Music.Regular",
            BottomNavItem.PROFILE to "ContactsCircle.Regular",
            BottomNavItem.FAVORITE to "Favorites.Regular",
            BottomNavItem.LIVE to "Recording.Regular",
            BottomNavItem.WATCHLATER to "Stopwatch.Regular",
            BottomNavItem.SETTINGS to "Settings.Regular",
            BottomNavItem.PLUGINS to "Folder.Regular",
        )

        expectedNames.forEach { (item, name) ->
            assertEquals(
                name,
                resolveMiuixBottomNavigationIcon(item, selected = false).name,
            )
            assertEquals(
                name,
                resolveMiuixBottomNavigationIcon(item, selected = true).name,
            )
        }
    }
}
