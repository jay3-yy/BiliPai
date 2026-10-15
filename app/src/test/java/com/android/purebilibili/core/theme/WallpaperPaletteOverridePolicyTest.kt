package com.android.purebilibili.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.store.WallpaperPaletteOverrides
import com.android.purebilibili.core.store.WallpaperPaletteRole
import com.android.purebilibili.feature.settings.Md3ColorSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WallpaperPaletteOverridePolicyTest {
    private val source = Md3ColorSource.WALLPAPER_RETONED

    @Test
    fun editsAreIsolatedBySourceAndAppearance() {
        val base = lightColorScheme()
        val edits = WallpaperPaletteOverrides(
            light = mapOf(WallpaperPaletteRole.PRIMARY to "#007AFF"),
            dark = mapOf(WallpaperPaletteRole.PRIMARY to "#34C759"),
        )
        assertEquals(Color(0xFF007AFF), applyWallpaperPaletteOverrides(base, edits, source, false).primary)
        assertEquals(Color(0xFF34C759), applyWallpaperPaletteOverrides(base, edits, source, true).primary)
        for (otherSource in listOf(Md3ColorSource.CUSTOM, Md3ColorSource.FOLLOW_WALLPAPER)) {
            assertSame(base, applyWallpaperPaletteOverrides(base, edits, otherSource, false))
        }
        assertSame(base, applyWallpaperPaletteOverrides(base, WallpaperPaletteOverrides(), source, false))
    }

    @Test
    fun wallpaperChangesKeepEditedRolesButUpdateAllUneditedRoles() {
        val old = lightColorScheme()
        val next = old.copy(secondary = Color.Green, primaryContainer = Color.Yellow)
        val edits = WallpaperPaletteOverrides(light = mapOf(WallpaperPaletteRole.PRIMARY to "#007AFF"))
        val resolved = applyWallpaperPaletteOverrides(next, edits, source, false)
        assertEquals(Color(0xFF007AFF), resolved.primary)
        assertEquals(next.secondary, resolved.secondary)
        assertEquals(next.tertiary, resolved.tertiary)
        assertEquals(next.primaryContainer, resolved.primaryContainer)
        assertEquals(next.surface, resolved.surface)
        assertEquals(next.error, resolved.error)
        assertSame(next, applyWallpaperPaletteOverrides(next, WallpaperPaletteOverrides(), source, false))
    }

    @Test
    fun exactRoleColorsHaveReadablePairedTextIncludingExtremeColors() {
        for (base in listOf(lightColorScheme(), darkColorScheme())) {
            for (hex in listOf("#000000", "#FFFFFF", "#007AFF", "#FFFF00", "#FF00FF")) {
                val colors = WallpaperPaletteRole.entries.associateWith { hex }
                val scheme = applyWallpaperPaletteOverrides(base, WallpaperPaletteOverrides(colors, colors), source, false)
                val expected = parseMd3CustomColorHex(hex)
                assertEquals(expected, scheme.primary)
                assertEquals(expected, scheme.secondary)
                assertEquals(expected, scheme.tertiary)
                assertEquals(expected, scheme.primaryContainer)
                for ((foreground, background) in listOf(
                    scheme.onPrimary to scheme.primary,
                    scheme.onSecondary to scheme.secondary,
                    scheme.onTertiary to scheme.tertiary,
                    scheme.onPrimaryContainer to scheme.primaryContainer,
                    scheme.onPrimaryFixed to scheme.primaryFixed,
                    scheme.onPrimaryFixedVariant to scheme.primaryFixedDim,
                )) {
                    assertTrue(calculateContrastRatio(foreground, background) >= ACCESSIBLE_TEXT_MIN_CONTRAST)
                }
            }
        }
    }

    @Test
    fun nativeMiuixSecondaryOnlyChangesOnExplicitOptIn() {
        val base = lightColorScheme()
        val scheme = applyWallpaperPaletteOverrides(base, WallpaperPaletteOverrides(
            light = mapOf(WallpaperPaletteRole.SECONDARY to "#007AFF"),
        ), source, false)
        val native = resolveNativeMiuixColors(base, false)
        assertEquals(native.secondary, resolveNativeMiuixColors(scheme, false).secondary)
        val edited = resolveAppThemeEndpointPalette(
            base, AppUiStyle.MIUIX, source, false,
            wallpaperOverrides = WallpaperPaletteOverrides(
                light = mapOf(WallpaperPaletteRole.SECONDARY to "#007AFF"),
            ),
        ).miuix
        assertEquals(scheme.secondary, edited.secondary)
        assertEquals(scheme.onSecondary, edited.onSecondary)
        assertEquals(native.surface, edited.surface)
        assertEquals(native.secondaryContainer, edited.secondaryContainer)
    }
}
