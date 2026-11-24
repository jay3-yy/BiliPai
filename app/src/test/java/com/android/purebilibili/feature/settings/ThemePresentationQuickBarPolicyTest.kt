package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.theme.AppUiStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePresentationQuickBarPolicyTest {

    @Test
    fun `active presentation resolves native ui style independently of liquid glass`() {
        assertEquals(
            AppThemePresentationOption.MATERIAL3,
            resolveActiveThemePresentation(uiStyle = AppUiStyle.MATERIAL3),
        )
        assertEquals(
            AppThemePresentationOption.MIUIX,
            resolveActiveThemePresentation(uiStyle = AppUiStyle.MIUIX),
        )
    }

    @Test
    fun `selecting native ui style preserves current liquid glass toggle`() {
        assertEquals(
            AppUiStyle.MATERIAL3 to false,
            resolveThemePresentationTarget(AppThemePresentationOption.MATERIAL3, currentLiquidGlassEnabled = false),
        )
        assertEquals(
            AppUiStyle.MATERIAL3 to true,
            resolveThemePresentationTarget(AppThemePresentationOption.MATERIAL3, currentLiquidGlassEnabled = true),
        )
        assertEquals(
            AppUiStyle.MIUIX to false,
            resolveThemePresentationTarget(AppThemePresentationOption.MIUIX, currentLiquidGlassEnabled = false),
        )
        assertEquals(
            AppUiStyle.MIUIX to true,
            resolveThemePresentationTarget(AppThemePresentationOption.MIUIX, currentLiquidGlassEnabled = true),
        )
    }
}
