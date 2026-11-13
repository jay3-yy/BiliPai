package com.android.purebilibili.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidNativeVariantThemePolicyTest {

    @Test
    fun themeTypographyDoesNotChangeWithGlassMaterial() {
        for (glassEnabled in listOf(false, true)) {
            assertSame(BiliMiuixTypography, resolveMaterialTypography(AppUiStyle.MIUIX, glassEnabled))
            assertSame(Md3Typography, resolveMaterialTypography(AppUiStyle.MATERIAL3, glassEnabled))
        }
    }

    @Test
    fun themeTextSupportsRelativeSizingAcrossMaterials() {
        for (uiStyle in AppUiStyle.entries) {
            for (glassEnabled in listOf(false, true)) {
                for (preset in AppFontSizePreset.entries) {
                    val typography = resolveMaterialTypography(uiStyle, glassEnabled).scaled(preset.multiplier)
                    val styles = listOf(
                        typography.displayLarge, typography.displayMedium, typography.displaySmall,
                        typography.headlineLarge, typography.headlineMedium, typography.headlineSmall,
                        typography.titleLarge, typography.titleMedium, typography.titleSmall,
                        typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
                        typography.labelLarge, typography.labelMedium, typography.labelSmall,
                    )
                    for (style in styles) {
                        // 页面会直接缩放行高；未指定单位会在这里复现崩溃。
                        assertTrue((style.fontSize * 0.9f).isSp)
                        assertTrue((style.lineHeight * 0.9f).isSp)
                        assertTrue((style.letterSpacing * 0.9f).isSp)
                    }
                }
            }
        }
    }

    @Test
    fun miuixVariant_enablesSmoothRoundingAndLargerCornerScale() {
        assertTrue(shouldUseMiuixSmoothRounding(AppUiStyle.MIUIX))
        assertEquals(
            MIUIX_CORNER_RADIUS_SCALE,
            resolveCornerRadiusScale(AppUiStyle.MIUIX)
        )
    }

    @Test
    fun material3Variant_keepsCompactCornerScaleWithoutSmoothRounding() {
        assertFalse(shouldUseMiuixSmoothRounding(AppUiStyle.MATERIAL3))
        assertEquals(
            MD3_CORNER_RADIUS_SCALE,
            resolveCornerRadiusScale(AppUiStyle.MATERIAL3)
        )
    }

    @Test
    fun material3Variant_usesStandardMotionScheme() {
        val motionScheme = resolveMaterialMotionScheme(AppUiStyle.MATERIAL3)

        assertSame(MotionScheme.standard(), motionScheme)
    }

    @Test
    fun miuixVariant_keepsStandardMotionScheme() {
        val miuix = resolveMaterialMotionScheme(AppUiStyle.MIUIX)

        assertSame(MotionScheme.standard(), miuix)
    }

    @Test
    fun dualValueStyles_resolveChromeTokens() {
        val miuix = resolveAndroidNativeChromeTokens(AppUiStyle.MIUIX)
        val material = resolveAndroidNativeChromeTokens(AppUiStyle.MATERIAL3)

        assertEquals(24, material.containerCornerRadiusDp)
        assertEquals(20, miuix.containerCornerRadiusDp)
        assertTrue(material.pillCornerRadiusDp > miuix.pillCornerRadiusDp)
        assertTrue(material.selectedContainerAlpha < miuix.selectedContainerAlpha)
        assertEquals(1f, material.motionScale)
        assertEquals(1f, miuix.motionScale)
        assertEquals(3, material.tonalSurfaceElevationDp)
        assertEquals(0, miuix.tonalSurfaceElevationDp)
        assertEquals(240, miuix.motionEmphasizedMillis)
        assertEquals(300, material.motionEmphasizedMillis)
        assertEquals(180, miuix.motionStandardMillis)
        assertEquals(200, material.motionStandardMillis)
        assertEquals(44, miuix.rowMinTouchTargetDp)
        assertEquals(48, material.rowMinTouchTargetDp)
    }

    @Test
    fun dualValueStyles_resolveShapes() {
        val miuix = resolveMaterialShapes(AppUiStyle.MIUIX)
        val material = resolveMaterialShapes(AppUiStyle.MATERIAL3)

        assertSame(MiuixAlignedShapes, miuix)
        assertSame(Md3Shapes, material)
    }

    @Test
    fun md3Shapes_alignWithChromeCornerTokens() {
        // MD3 路径的 MaterialTheme.shapes 必须与 chrome token(容器 24dp / 胶囊 28dp)同一套圆角语言,
        // small/extraSmall 保持官方默认以保护输入框与芯片观感。
        val expected = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        )

        assertEquals(expected, Md3Shapes)
    }
}
