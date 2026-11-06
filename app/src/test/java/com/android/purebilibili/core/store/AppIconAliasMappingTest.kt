package com.android.purebilibili.core.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppIconAliasMappingTest {

    @Test
    fun resolveAppIconLauncherAlias_supportsCanonicalAndLegacyKeys() {
        val packageName = "com.android.purebilibili"

        assertEquals(
            "com.android.purebilibili.MainActivityAliasBlueSnowMaid",
            resolveAppIconLauncherAlias(packageName, "icon_blue_snow_maid")
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidFront",
            resolveAppIconLauncherAlias(packageName, "icon_blue_snow_maid_front")
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidAnnouncement",
            resolveAppIconLauncherAlias(packageName, "icon_blue_snow_maid_announcement")
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBiliPai",
            resolveAppIconLauncherAlias(packageName, "icon_bilipai")
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBiliPai",
            resolveAppIconLauncherAlias(packageName, "BiliPai")
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBiliPaiPink",
            resolveAppIconLauncherAlias(packageName, "icon_bilipai_pink")
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBiliPaiWhite",
            resolveAppIconLauncherAlias(packageName, "BiliPai White")
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBiliPaiMonet",
            resolveAppIconLauncherAlias(packageName, "BiliPai Monet")
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBlueSnowMaid",
            resolveAppIconLauncherAlias(packageName, "icon_headphone")
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBlueSnowMaid",
            resolveAppIconLauncherAlias(packageName, "unknown")
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBiliPaiNoIcon",
            resolveAppIconLauncherAlias(packageName, "icon_bilipai", splashIconVisible = false)
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidNoIcon",
            resolveAppIconLauncherAlias(packageName, "unknown", splashIconVisible = false)
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidFrontNoIcon",
            resolveAppIconLauncherAlias(packageName, "icon_blue_snow_maid_front", splashIconVisible = false)
        )
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBlueSnowMaidAnnouncementNoIcon",
            resolveAppIconLauncherAlias(packageName, "icon_blue_snow_maid_announcement", splashIconVisible = false)
        )
    }

    @Test
    fun resolveAppIconLauncherAlias_supportsFixedMaidAppearances() {
        val packageName = "com.android.purebilibili"

        assertEquals(
            "$packageName.MainActivityAliasBlueSnowMaidLight",
            resolveAppIconLauncherAlias(
                packageName,
                "icon_blue_snow_maid",
                appearance = AppIconAppearance.LIGHT
            )
        )
        assertEquals(
            "$packageName.MainActivityAliasBlueSnowMaidDarkNoIcon",
            resolveAppIconLauncherAlias(
                packageName,
                "icon_blue_snow_maid",
                splashIconVisible = false,
                appearance = AppIconAppearance.DARK
            )
        )
        assertEquals(
            "$packageName.MainActivityAliasBlueSnowMaidFrontDark",
            resolveAppIconLauncherAlias(
                packageName,
                "icon_blue_snow_maid_front",
                appearance = AppIconAppearance.DARK
            )
        )
        assertEquals(
            "$packageName.MainActivityAliasBlueSnowMaidFrontLightNoIcon",
            resolveAppIconLauncherAlias(
                packageName,
                "icon_blue_snow_maid_front",
                splashIconVisible = false,
                appearance = AppIconAppearance.LIGHT
            )
        )
        assertEquals(
            "$packageName.MainActivityAliasBlueSnowMaidAnnouncementLight",
            resolveAppIconLauncherAlias(
                packageName,
                "icon_blue_snow_maid_announcement",
                appearance = AppIconAppearance.LIGHT
            )
        )
        assertEquals(
            "$packageName.MainActivityAliasBlueSnowMaidAnnouncementDarkNoIcon",
            resolveAppIconLauncherAlias(
                packageName,
                "icon_blue_snow_maid_announcement",
                splashIconVisible = false,
                appearance = AppIconAppearance.DARK
            )
        )
    }

    @Test
    fun playWindowAppearanceSelectsItsLauncherWithAndWithoutSplashIcon() {
        val packageName = "com.android.purebilibili"
        val entries = listOf(
            Triple(AppIconAppearance.FOLLOW_SYSTEM, "MainActivityAlias3DLauncher", "MainActivityAlias3DNoIcon"),
            Triple(AppIconAppearance.LIGHT, "MainActivityAlias3DLight", "MainActivityAlias3DLightNoIcon"),
            Triple(AppIconAppearance.DARK, "MainActivityAlias3DDark", "MainActivityAlias3DDarkNoIcon"),
        )
        val managed = allManagedAppIconLauncherAliases(packageName)
        for ((appearance, withSplash, withoutSplash) in entries) {
            assertEquals("$packageName.$withSplash", resolveAppIconLauncherAlias(packageName, "icon_3d", appearance = appearance))
            assertEquals("$packageName.$withoutSplash", resolveAppIconLauncherAlias(packageName, "icon_3d", splashIconVisible = false, appearance = appearance))
            assertTrue("$packageName.$withSplash" in managed)
            assertTrue("$packageName.$withoutSplash" in managed)
        }
    }

    @Test
    fun resolveAppIconLauncherAlias_ignoresAppearanceForFixedPaletteIcons() {
        assertEquals(
            "com.android.purebilibili.MainActivityAliasBiliPai",
            resolveAppIconLauncherAlias(
                "com.android.purebilibili",
                "icon_bilipai",
                appearance = AppIconAppearance.DARK
            )
        )
    }

    @Test
    fun resolveAppIconLauncherAlias_keepsStableComponentNamespaceForDebugBuilds() {
        assertEquals(
            "com.android.purebilibili.MainActivityAlias3DLauncher",
            resolveAppIconLauncherAlias("com.android.purebilibili.debug", "icon_3d")
        )
    }

    @Test
    fun allManagedAppIconLauncherAliases_containsBiliPaiAndHeadphone_withoutRemovedAliases() {
        val aliases = allManagedAppIconLauncherAliases("com.android.purebilibili")
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaid"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidNoIcon"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidAnnouncement"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidAnnouncementNoIcon"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidAnnouncementLight"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidAnnouncementDarkNoIcon"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidFront"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidFrontNoIcon"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidLight"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidDarkNoIcon"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidFrontLightNoIcon"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBlueSnowMaidFrontDark"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBiliPai"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBiliPaiPink"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBiliPaiWhite"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBiliPaiMonet"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasHeadphone"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAlias3D"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAliasBiliPaiNoIcon"))
        assertTrue(aliases.contains("com.android.purebilibili.MainActivityAlias3DNoIcon"))
        kotlin.test.assertFalse(aliases.contains("com.android.purebilibili.MainActivityAliasBlue"))
        kotlin.test.assertFalse(aliases.contains("com.android.purebilibili.MainActivityAliasNeon"))
        kotlin.test.assertFalse(aliases.contains("com.android.purebilibili.MainActivityAliasTelegramBlueCoin"))
        kotlin.test.assertFalse(aliases.contains("com.android.purebilibili.MainActivityAliasPink"))
        kotlin.test.assertFalse(aliases.contains("com.android.purebilibili.MainActivityAliasPurple"))
        kotlin.test.assertFalse(aliases.contains("com.android.purebilibili.MainActivityAliasGreen"))
        kotlin.test.assertFalse(aliases.contains("com.android.purebilibili.MainActivityAliasFlatMaterial"))
        kotlin.test.assertFalse(aliases.contains("com.android.purebilibili.MainActivityAliasRetro"))
    }
}
