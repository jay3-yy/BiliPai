package com.android.purebilibili.core.ui

import com.android.purebilibili.core.theme.AppUiStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class AppThemeRendererPolicyTest {

    @Test
    fun `liquid glass selects dedicated glass renderer on API 33 and above`() {
        assertEquals(
            AppThemeRenderer.LIQUID_GLASS,
            resolveAppThemeRenderer(AppUiStyle.MATERIAL3, liquidGlassEnabled = true, sdkInt = 33),
        )
        assertEquals(
            AppThemeRenderer.LIQUID_GLASS,
            resolveAppThemeRenderer(AppUiStyle.MIUIX, liquidGlassEnabled = true, sdkInt = 35),
        )
    }

    @Test
    fun `unsupported sdk or disabled glass falls back cleanly to native renderer`() {
        assertEquals(
            AppThemeRenderer.MATERIAL3,
            resolveAppThemeRenderer(AppUiStyle.MATERIAL3, liquidGlassEnabled = true, sdkInt = 32),
        )
        assertEquals(
            AppThemeRenderer.MIUIX,
            resolveAppThemeRenderer(AppUiStyle.MIUIX, liquidGlassEnabled = true, sdkInt = 32),
        )
        assertEquals(
            AppThemeRenderer.MATERIAL3,
            resolveAppThemeRenderer(AppUiStyle.MATERIAL3, liquidGlassEnabled = false, sdkInt = 34),
        )
        assertEquals(
            AppThemeRenderer.MIUIX,
            resolveAppThemeRenderer(AppUiStyle.MIUIX, liquidGlassEnabled = false, sdkInt = 34),
        )
    }
}
