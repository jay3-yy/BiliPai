package com.android.purebilibili.core.ui

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.iOSSystemGray6
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSurfaceTokensTest {

    private val scheme = lightColorScheme(
        surface = Color.White,
        surfaceContainer = Color(0xFFEEEEEE),
        surfaceContainerHigh = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFF666666),
        background = iOSSystemGray6,
        outlineVariant = Color(0xFFC7C7CC)
    )

    @Test
    fun cardContainer_returnsSurfaceContainer() {
        val color = AppSurfaceTokens.resolveCardContainer(scheme)
        assertEquals(Color(0xFFEEEEEE), color)
    }

    @Test
    fun groupedListContainer_usesBackground() {
        val color = AppSurfaceTokens.resolveGroupedListContainer(scheme)
        assertEquals(iOSSystemGray6, color)
    }

    @Test
    fun chromeBackground_returnsBackground() {
        val color = AppSurfaceTokens.resolveChromeBackground(scheme)
        assertEquals(iOSSystemGray6, color)
    }

    @Test
    fun divider_returnsOutlineVariant() {
        val color = AppSurfaceTokens.resolveDivider(scheme)
        assertEquals(Color(0xFFC7C7CC), color)
    }

    @Test
    fun search_usesHighSurfaceContainerAndMatchingContentRole() {
        assertEquals(Color(0xFFE8E8E8), AppSurfaceTokens.resolveSearchContainer(scheme))
        assertEquals(Color(0xFF666666), AppSurfaceTokens.resolveSearchContent(scheme))
    }

    @Test
    fun resolveMiuixSemanticColor_prefersMiuixWhenEnabled() {
        val miuix = Color(0xFF112233)
        val material = Color(0xFF445566)
        assertEquals(miuix, AppSurfaceTokens.resolveMiuixSemanticColor(true, miuix, material))
        assertEquals(material, AppSurfaceTokens.resolveMiuixSemanticColor(false, miuix, material))
    }
}
