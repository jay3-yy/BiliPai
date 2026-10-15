package com.android.purebilibili.core.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.ACCESSIBLE_TEXT_MIN_CONTRAST
import com.android.purebilibili.core.theme.ThemeColors
import com.android.purebilibili.core.theme.calculateContrastRatio
import com.android.purebilibili.core.theme.opaqueCompositeOver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppContentColorsTest {
    @Test
    fun glassSelectionUsesExactCustomAccentWhileNativeSelectionKeepsGeneratedPalette() {
        val pickedBlue = Color(0xFF007AFF)
        val generatedBlue = Color(0xFF4A608A)
        assertEquals(pickedBlue, resolveSelectionAccentColor(true, generatedBlue, pickedBlue))
        assertEquals(generatedBlue, resolveSelectionAccentColor(false, generatedBlue, pickedBlue))
    }

    @Test
    fun glassSelectionFallsBackToNativeAccentWhenNoExactColorIsProvided() {
        val wallpaperAccent = Color(0xFF4A608A)
        assertEquals(wallpaperAccent, resolveSelectionAccentColor(true, wallpaperAccent, Color.Unspecified))
    }

    @Test
    fun supportingTextIsReadableAcrossCanvasAndCardSurfaces() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { scheme ->
            val requested = scheme.onSurface.copy(alpha = 0.25f)
            val actual = resolveReadableNeutralContent(requested, scheme)
            listOf(scheme.background, scheme.surface, scheme.surfaceContainer, scheme.surfaceContainerHigh).forEach { surface ->
                assertTrue(calculateContrastRatio(opaqueCompositeOver(actual, surface), surface) >= ACCESSIBLE_TEXT_MIN_CONTRAST)
            }
        }
    }

    @Test
    fun readableSupportingColorIsPreserved() {
        val scheme = darkColorScheme()
        val readable = scheme.onSurface
        assertEquals(readable, resolveReadableNeutralContent(readable, scheme))
    }

    @Test
    fun successAndWarningRemainDistinctAndIndependentOfAccentSelection() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { base ->
            val success = resolveStatusContentColor(AppContentStatus.SUCCESS, base)
            val warning = resolveStatusContentColor(AppContentStatus.WARNING, base)
            assertNotEquals(success, warning)
            ThemeColors.forEach { accent ->
                val scheme = base.copy(primary = accent)
                assertEquals(success, resolveStatusContentColor(AppContentStatus.SUCCESS, scheme))
                assertEquals(warning, resolveStatusContentColor(AppContentStatus.WARNING, scheme))
                listOf(scheme.background, scheme.surface, scheme.surfaceContainer, scheme.surfaceContainerHigh).forEach { surface ->
                    assertTrue(calculateContrastRatio(success, surface) >= ACCESSIBLE_TEXT_MIN_CONTRAST)
                    assertTrue(calculateContrastRatio(warning, surface) >= ACCESSIBLE_TEXT_MIN_CONTRAST)
                }
            }
        }
    }
}
