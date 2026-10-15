package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HomeTopTabColorPolicyTest {
    @Test
    fun glassSelectionPreservesAccentWhenShellOnlyContrastWouldReplaceIt() {
        val accent = Color(0xFF007AFF)
        val translucentShell = Color(0xFF808080).copy(alpha = 0.08f)
        val nativeColor = resolveReadableBottomBarSkinForeground(accent, translucentShell)
        assertNotEquals(accent, nativeColor)
        assertEquals(
            accent,
            resolveHomeTopTabSelectedContentColor(accent, translucentShell, liquidGlassEnabled = true),
        )
    }

    @Test
    fun nativeSelectionRetainsReadableForegroundPolicy() {
        val accent = Color(0xFF007AFF)
        listOf(Color.White, Color.Black, Color.White.copy(alpha = 0.08f)).forEach { shell ->
            assertEquals(
                resolveReadableBottomBarSkinForeground(accent, shell),
                resolveHomeTopTabSelectedContentColor(accent, shell, liquidGlassEnabled = false),
            )
        }
    }
}
