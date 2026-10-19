package com.android.purebilibili.core.theme

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.store.AppThemeSettings
import com.android.purebilibili.core.store.WallpaperPaletteOverrides
import com.android.purebilibili.core.store.WallpaperPaletteRole
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.android.purebilibili.feature.video.player.resolvePlaybackNotificationAccentColor
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AppThemeEndpointPolicyTest {
    @Test
    fun darkMiuixPinkIsSofterAndWhiteHandlesRemainReadable() {
        val pink = Color(0xFFF36595)
        val adapted = resolveMiuixDarkCustomAccent(pink)
        assertTrue(calculateContrastRatio(Color.Black, adapted) < calculateContrastRatio(Color.Black, pink))
        assertTrue(calculateContrastRatio(Color.White, adapted) >= ACCESSIBLE_UI_MIN_CONTRAST)
        assertTrue(calculateContrastRatio(adapted, Color(0xFF212121)) >= ACCESSIBLE_TEXT_MIN_CONTRAST)
    }

    @Test
    fun nativeSelectionUsesTheModeAdjustedAccentAcrossBothRenderers() {
        for (style in AppUiStyle.entries) for (dark in listOf(false, true)) {
            for (accent in listOf(Color(0xFF007AFF), Color(0xFF34C759), Color.White, Color.Black)) {
                val base = createBiliPaiStyleColorScheme(
                    accent, dark, false, PaletteStyle.Expressive, ColorSpec.SpecVersion.SPEC_2025,
                )
                val endpoint = resolveAppThemeEndpointPalette(
                    base, style, Md3ColorSource.CUSTOM, dark,
                    customSeed = accent, liquidGlassEnabled = false,
                )
                val expectedAccent = if (style == AppUiStyle.MIUIX && dark) {
                    resolveMiuixDarkCustomAccent(accent)
                } else accent
                assertEquals(expectedAccent, endpoint.material.primary)
                assertEquals(expectedAccent, endpoint.miuix.primary)
                // A bright custom accent legitimately needs dark text. Fixing
                // Miuix handles must not force the shared foreground to white;
                // MD3's native SwitchDefaults also consumes this semantic role.
                assertEquals(resolveReadableForeground(expectedAccent), endpoint.material.onPrimary)
                assertTrue(calculateContrastRatio(
                    endpoint.material.onPrimary, endpoint.material.primary,
                ) >= ACCESSIBLE_TEXT_MIN_CONTRAST)
                val expectedContainer = opaqueCompositeOver(expectedAccent.copy(alpha = 0.14f), endpoint.material.surface)
                assertEquals(expectedContainer, endpoint.material.secondaryContainer)
                assertEquals(expectedContainer, endpoint.miuix.secondaryContainer)
                assertEquals(endpoint.material.onSecondaryContainer, endpoint.miuix.onSecondaryContainer)
                assertTrue(calculateContrastRatio(
                    endpoint.material.onSecondaryContainer, expectedContainer,
                ) >= ACCESSIBLE_TEXT_MIN_CONTRAST)
                // Native canvas and error roles must not inherit the selection fill.
                val glassEndpoint = resolveAppThemeEndpointPalette(
                    base, style, Md3ColorSource.CUSTOM, dark,
                    customSeed = accent, liquidGlassEnabled = true,
                )
                assertEquals(glassEndpoint.material.surface, endpoint.material.surface)
                assertEquals(glassEndpoint.material.error, endpoint.material.error)
                if (style == AppUiStyle.MATERIAL3) {
                    assertEquals(base.secondaryContainer, glassEndpoint.material.secondaryContainer)
                } else {
                    val native = resolveNativeMiuixColors(base, dark)
                    assertEquals(native.secondaryContainer, glassEndpoint.miuix.secondaryContainer)
                }
            }
        }
    }

    @Test
    fun editedWallpaperRolesAgreeAcrossMaterialMiuixGlassAndNotifications() {
        val seed = Color(0xFF8054A8)
        val edits = mapOf(
            WallpaperPaletteRole.PRIMARY to "#007AFF",
            WallpaperPaletteRole.SECONDARY to "#34C759",
            WallpaperPaletteRole.TERTIARY to "#AF52DE",
            WallpaperPaletteRole.CONTAINER to "#123456",
        )
        val overrides = WallpaperPaletteOverrides(edits, edits)
        for (style in AppUiStyle.entries) for (dark in listOf(false, true)) {
            val base = createBiliPaiStyleColorScheme(
                seed, dark, false, PaletteStyle.Expressive, ColorSpec.SpecVersion.SPEC_2025,
            )
            val endpoint = resolveAppThemeEndpointPalette(
                base, style, Md3ColorSource.WALLPAPER_RETONED, dark, wallpaperOverrides = overrides,
            )
            assertEquals(Color(0xFF007AFF), endpoint.material.primary)
            assertEquals(endpoint.material.primary, endpoint.miuix.primary)
            assertEquals(endpoint.material.primary, endpoint.liquidAccent)
            assertEquals(Color(0xFF34C759), endpoint.material.secondary)
            assertEquals(endpoint.material.secondary, endpoint.miuix.secondary)
            assertEquals(Color(0xFFAF52DE), endpoint.material.tertiary)
            assertEquals(Color(0xFF123456), endpoint.material.primaryContainer)
            assertEquals(endpoint.material.primaryContainer, endpoint.miuix.primaryContainer)
            assertEquals(endpoint.material.onPrimary, endpoint.miuix.onPrimary)
            val settings = AppThemeSettings(
                uiStyle = style,
                md3ColorSource = Md3ColorSource.WALLPAPER_RETONED,
                colorStyle = PaletteStyle.Expressive,
                wallpaperPaletteOverrides = overrides,
            )
            assertEquals(endpoint.material.primary, resolvePlaybackNotificationAccentColor(
                settings, dark, wallpaperSeed = seed,
            ))
        }
    }

    @Test
    fun restoringWallpaperRolesRestoresTheSameEndpointAsFreshSettings() {
        val base = createBiliPaiStyleColorScheme(
            Color(0xFF8054A8), false, false, PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025,
        )
        for (style in AppUiStyle.entries) {
            val fresh = resolveAppThemeEndpointPalette(base, style, Md3ColorSource.WALLPAPER_RETONED, false)
            val reset = resolveAppThemeEndpointPalette(
                base, style, Md3ColorSource.WALLPAPER_RETONED, false,
                wallpaperOverrides = WallpaperPaletteOverrides(),
            )
            assertEquals(fresh.material.primary, reset.material.primary)
            assertEquals(fresh.material.secondary, reset.material.secondary)
            assertEquals(fresh.material.tertiary, reset.material.tertiary)
            assertEquals(fresh.material.primaryContainer, reset.material.primaryContainer)
            assertEquals(fresh.liquidAccent, reset.liquidAccent)
        }
    }
}
