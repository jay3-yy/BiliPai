package com.android.purebilibili.feature.home.components

import androidx.compose.ui.unit.sp
import kotlin.math.abs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarTypographySpecTest {
    @Test
    fun skinDockTypography_keepsItsCompactOverlayDensity() {
        assertEquals(12.sp, resolveBottomBarSkinDockLabelFontSize())
        assertEquals(18.sp, resolveBottomBarSkinDockLabelLineHeight())
    }

    @Test
    fun floatingDockTextOnlyLabelIsLargerThanIconAndTextCaption() {
        assertEquals(11.sp, resolveFloatingDockIconAndTextLabelFontSize())
        assertEquals(15.sp, resolveFloatingDockTextOnlyLabelFontSize())
        assertEquals(
            11.sp,
            resolveFloatingDockLabelFontSize(showIcon = true, showText = true),
        )
        assertEquals(
            15.sp,
            resolveFloatingDockLabelFontSize(showIcon = false, showText = true),
        )
        assertEquals(
            0.sp,
            resolveFloatingDockLabelFontSize(showIcon = true, showText = false),
        )
        assertEquals(14.sp, resolveFloatingDockLabelLineHeight(showIcon = true))
        assertEquals(19.sp, resolveFloatingDockLabelLineHeight(showIcon = false))
    }

    @Test
    fun dockLabels_capRenderedGrowthOnceSystemFontScaleExceedsCap() {
        // 渲染尺寸 = 字号 TextUnit × 系统 fontScale;预期恒等于 基准 × min(fontScale, cap)。
        fun assertRendered(style: androidx.compose.ui.unit.TextUnit, baseSp: Float, fontScale: Float) {
            val expected = baseSp * minOf(fontScale, DOCK_LABEL_FONT_SCALE_CAP)
            val rendered = style.value * fontScale
            assertTrue(
                abs(rendered - expected) < 0.05f,
                "rendered ${rendered}sp, expected ${expected}sp",
            )
        }
        // 未超阈值:字号保持基准,由 sp 随系统缩放。
        assertRendered(resolveBottomBarSkinDockLabelFontSize(fontScale = 1.1f), 12f, 1.1f)
        assertRendered(resolveFloatingDockTextOnlyLabelFontSize(fontScale = 1f), 15f, 1f)
        // 超过阈值:渲染尺寸封顶在 基准 × cap。
        assertRendered(resolveBottomBarSkinDockLabelFontSize(fontScale = 1.5f), 12f, 1.5f)
        assertRendered(resolveBottomBarSkinDockLabelLineHeight(fontScale = 2.0f), 18f, 2.0f)
        assertRendered(resolveFloatingDockIconAndTextLabelFontSize(fontScale = 2.0f), 11f, 2.0f)
        assertRendered(resolveFloatingDockTextOnlyLabelFontSize(fontScale = 1.8f), 15f, 1.8f)
        assertRendered(resolveFloatingDockTextOnlyLabelFontSize(fontScale = DOCK_LABEL_FONT_SCALE_CAP), 15f, DOCK_LABEL_FONT_SCALE_CAP)
    }

    @Test
    fun liquidGlassTextOnlyDocksShareTheLargerLabelSize() {
        val bottomBar = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/BottomBar.kt"
        )
        val topDock = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/HomeTopTabFloatingDock.kt"
        )
        val topBar = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/home/components/TopBar.kt"
        )
        val floatingVisual = bottomBar
            .substringAfter("private fun ColumnScope.FloatingBottomBarTabVisual(")
            .substringBefore("internal fun resolveMaterialBottomBarIcon(")
        assertTrue(floatingVisual.contains("resolveFloatingDockLabelFontSize("))
        assertTrue(topBar.contains("resolveFloatingDockLabelFontSize("))
        assertTrue(topDock.contains("fontSize = labelFontSize"))
        assertTrue(topDock.contains("lineHeight = resolveFloatingDockLabelLineHeight("))
        assertTrue(topDock.contains("autoSize = TextAutoSize.StepBased("))
        assertTrue(topDock.contains("modifier = Modifier.size(resolveTopTabIconSizeDp("))
        assertFalse(topDock.contains("labelSmall.fontSize"))
        // Dock 槽宽固定,标签必须省略号截断而不是切半字。
        assertTrue(bottomBar.contains("overflow = TextOverflow.Ellipsis"), "dock 标签需要 Ellipsis")
        assertTrue(topBar.contains("fontScale = density.fontScale"), "TopBar dock 需要传 fontScale")
        assertTrue(bottomBar.contains("fontScale = LocalDensity.current.fontScale"), "底栏 dock 需要传 fontScale")
    }

    @Test
    fun dockAndTopTabTypography_followsActiveMiuixAndMd3ThemeScale() {
        val miuix = com.android.purebilibili.core.theme.BiliMiuixTypography
        val md3 = com.android.purebilibili.core.theme.Md3Typography

        assertEquals(
            miuix.labelMedium.fontSize,
            resolveBottomBarSkinDockLabelFontSize(fontScale = 1f, typography = miuix),
        )
        assertEquals(
            miuix.labelSmall.fontSize,
            resolveFloatingDockLabelFontSize(showIcon = true, showText = true, fontScale = 1f, typography = miuix),
        )
        assertEquals(
            miuix.titleSmall.fontSize,
            resolveFloatingDockLabelFontSize(showIcon = false, showText = true, fontScale = 1f, typography = miuix),
        )
        assertEquals(
            miuix.titleSmall.fontSize.value,
            resolveTopTabLabelTextSizeSp(labelMode = 2, typography = miuix),
        )
        assertEquals(
            miuix.labelSmall.fontSize.value,
            resolveTopTabLabelTextSizeSp(labelMode = 0, typography = miuix),
        )

        assertEquals(
            md3.labelMedium.fontSize,
            resolveBottomBarSkinDockLabelFontSize(fontScale = 1f, typography = md3),
        )
        assertEquals(
            md3.titleSmall.fontSize,
            resolveFloatingDockLabelFontSize(showIcon = false, showText = true, fontScale = 1f, typography = md3),
        )
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        return listOf(File(path), File(normalizedPath)).first { it.exists() }.readText()
    }
}
