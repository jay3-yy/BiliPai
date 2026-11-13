package com.android.purebilibili.core.store

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BiliPaiThemeIconPaletteTest {
    @Test
    fun exactPaletteColorsRoundTripWithoutColorLoss() {
        for (index in 0 until BILIPAI_THEME_ICON_PALETTE_SIZE) {
            val background = if (index < BILIPAI_THEME_ICON_BASE_PALETTE_SIZE) null else biliPaiThemeIconBackground(index)
            assertEquals(index, resolveBiliPaiThemeIconIndex(biliPaiThemeIconColor(index), background))
        }
    }

    @Test
    fun arbitraryThemeColorsChooseTheNearestChannelValues() {
        for (channel in 0..255) {
            val input = (255 shl 24) or (channel shl 16) or (channel shl 8) or channel
            val matched = biliPaiThemeIconColor(resolveBiliPaiThemeIconIndex(input))
            for (shift in listOf(0, 8, 16)) {
                assertTrue(abs((matched ushr shift and 255) - channel) <= 25)
            }
        }
        assertEquals(0, resolveBiliPaiThemeIconIndex(0xFF191919.toInt()))
        assertEquals(43, resolveBiliPaiThemeIconIndex(0xFF1A1A1A.toInt()))
        assertEquals(resolveBiliPaiThemeIconIndex(0xFF007AFF.toInt()), resolveBiliPaiThemeIconIndex(0x00007AFF))
    }

    @Test
    fun backgroundSelectionDoesNotReplaceTheChosenMarkColor() {
        val foreground = 0xFFFF0000.toInt()
        for (background in biliPaiIconBackgroundChoices) {
            val index = resolveBiliPaiThemeIconIndex(foreground, background)
            assertEquals(foreground, biliPaiThemeIconColor(index))
            assertEquals(background, biliPaiThemeIconBackground(index))
        }
    }

    @Test
    fun themeSelectionPersistsAcrossPaletteAndSplashChanges() {
        val managed = allManagedAppIconLauncherAliases("com.android.purebilibili")
        assertEquals(BILIPAI_THEME_ICON_KEY, normalizeAppIconKey(BILIPAI_THEME_ICON_KEY))
        for (index in 0 until BILIPAI_THEME_ICON_PALETTE_SIZE) {
            for (splash in listOf(false, true)) {
                val alias = resolveAppIconLauncherAlias(
                    "com.android.purebilibili.debug", BILIPAI_THEME_ICON_KEY,
                    splashIconVisible = splash, themeIconIndex = index,
                )
                assertTrue(alias in managed)
                assertEquals(
                    "com.android.purebilibili.MainActivityAliasBiliPaiTheme" +
                        index.toString().padStart(3, '0') + if (splash) "" else "NoIcon",
                    alias,
                )
            }
        }
    }
}
