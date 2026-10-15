package com.android.purebilibili.core.theme

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.store.ThemeRoleOverrides
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WallpaperRetonePolicyTest {
    private val source = Md3ColorSource.WALLPAPER_RETONED
    private val wallpaperSeed = Color(0xFF8054A8)

    @Test
    fun retoneUsesWallpaperSeedAndObservesWallpaperChangesOnSupportedSystems() {
        assertEquals(wallpaperSeed, resolveMd3ThemeSeedColor(source, "#FF5722", 0, wallpaperSeed))
        assertTrue(resolveMd3DynamicColorEnabled(source, 31))
        assertFalse(resolveMd3DynamicColorEnabled(source, 30))
        assertEquals(ThemeColors[2], resolveMd3ThemeSeedColor(source, "#FF5722", 2))
        assertEquals(ThemeColors.last(), resolveMd3ThemeSeedColor(source, "#FF5722", Int.MAX_VALUE))
    }

    @Test
    fun retoneUsesGeneratedLightAndDarkRolesAndHonorsStyle() {
        for (dark in listOf(false, true)) {
            fun palette(style: PaletteStyle) = createBiliPaiStyleColorScheme(
                seedColor = resolveMd3ThemeSeedColor(source, "#007AFF", 0, wallpaperSeed),
                darkTheme = dark,
                amoledDarkTheme = false,
                paletteStyle = style,
                colorSpec = ColorSpec.SpecVersion.SPEC_2025,
            )
            val tonal = palette(PaletteStyle.TonalSpot)
            val expressive = palette(PaletteStyle.Expressive)
            assertNotEquals(tonal.primary, expressive.primary)
            assertTrue(calculateContrastRatio(tonal.onPrimary, tonal.primary) >= ACCESSIBLE_TEXT_MIN_CONTRAST)
            // Glass must retain generated roles in this mode; forcing the seed back in
            // would erase the chosen palette style and break the preview/content agreement.
            assertSame(tonal, alignLiquidAccentColorScheme(tonal, wallpaperSeed, true, source))
            assertEquals(tonal.primary, resolveLiquidControlAccentColor(
                source, Color(0xFF007AFF), tonal.primary, roleOverridesEnabled = true,
                roleControlHex = "#FF5722",
            ))
        }
    }

    @Test
    fun savedManualOverridesDoNotReplaceWallpaperPalette() {
        val saved = ThemeRoleOverrides(enabled = true)
        val resolved = resolveEffectiveThemeRoleOverrides(source, saved)
        assertFalse(resolved.enabled)
        assertEquals(saved.light, resolved.light)
        assertEquals(saved.dark, resolved.dark)
    }
}
