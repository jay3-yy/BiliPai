package com.android.purebilibili.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.feature.settings.Md3ColorSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LiquidAccentColorSchemeTest {
    private val pickedBlue = Color(0xFF007AFF)

    @Test
    fun glassCustomAccent_replacesGeneratedAccentFamilyInBothModes() {
        for (base in listOf(lightColorScheme(), darkColorScheme())) {
            val resolved = alignLiquidAccentColorScheme(base, pickedBlue, true, Md3ColorSource.CUSTOM)
            assertEquals(pickedBlue, resolved.primary)
            assertEquals(pickedBlue, resolved.secondary)
            assertEquals(pickedBlue, resolved.tertiary)
            assertEquals(resolved.primaryContainer, resolved.secondaryContainer)
            assertEquals(resolved.primaryContainer, resolved.tertiaryContainer)
            assertEquals(base.background, resolved.background)
            assertEquals(base.surface, resolved.surface)
            assertEquals(base.surfaceContainer, resolved.surfaceContainer)
            assertEquals(base.onSurface, resolved.onSurface)
            assertEquals(base.error, resolved.error)
            assertEquals(base.surfaceTint, resolved.surfaceTint)
        }
    }

    @Test
    fun nativeAndWallpaperPalettes_areReturnedUnchanged() {
        for (base in listOf(lightColorScheme(), darkColorScheme())) {
            for (source in Md3ColorSource.entries) {
                assertSame(base, alignLiquidAccentColorScheme(base, pickedBlue, false, source))
            }
            assertSame(base, alignLiquidAccentColorScheme(base, pickedBlue, true, Md3ColorSource.FOLLOW_WALLPAPER))
        }
    }

    @Test
    fun allPresetAccents_keepReadableFilledAndTonalLabels() {
        for (base in listOf(lightColorScheme(), darkColorScheme())) {
            // Also cover extreme custom colors and an explicit advanced control accent.
            for (accent in ThemeColors + listOf(Color.White, Color.Black, Color(0xFF123456))) {
                val scheme = alignLiquidAccentColorScheme(base, accent, true, Md3ColorSource.CUSTOM)
                assertEquals(accent, scheme.primary)
                for ((foreground, background) in listOf(
                    scheme.onPrimary to scheme.primary,
                    scheme.onPrimaryContainer to scheme.primaryContainer,
                    scheme.onSecondary to scheme.secondary,
                    scheme.onSecondaryContainer to scheme.secondaryContainer,
                    scheme.onTertiary to scheme.tertiary,
                    scheme.onTertiaryContainer to scheme.tertiaryContainer,
                    scheme.onPrimaryFixed to scheme.primaryFixed,
                    scheme.onPrimaryFixedVariant to scheme.primaryFixedDim,
                    scheme.inversePrimary to scheme.inverseSurface,
                )) {
                    assertTrue(calculateContrastRatio(foreground, background) >= ACCESSIBLE_TEXT_MIN_CONTRAST)
                }
            }
        }
    }
}
