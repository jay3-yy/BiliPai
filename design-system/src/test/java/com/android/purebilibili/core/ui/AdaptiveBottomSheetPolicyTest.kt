package com.android.purebilibili.core.ui

import com.android.purebilibili.core.theme.AppUiStyle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveBottomSheetPolicyTest {

    @Test
    fun `app sheet facade preserves caller content color`() {
        val path = "src/main/java/com/android/purebilibili/core/ui/AppSheetComponents.kt"
        val source = listOf(File(path), File("design-system/$path"))
            .firstOrNull(File::exists)
            ?.readText()
            ?: error("Cannot locate AppSheetComponents.kt from ${File(".").absolutePath}")

        assertTrue(source.contains("contentColor: Color = AppContentColors.primaryText()"))
        assertTrue(source.contains("contentColor = contentColor"))
        assertTrue(source.contains("content: @Composable ColumnScope.() -> Unit"))
    }

    @Test
    fun `material3 style uses material drag handle and material corner radius`() {
        val spec = resolveAdaptiveBottomSheetVisualSpec(AppUiStyle.MATERIAL3)

        assertEquals(28, spec.cornerRadiusDp)
        assertTrue(spec.useMaterialDragHandle)
    }

    @Test
    fun `miuix style uses native drag handle and miuix corner radius`() {
        val spec = resolveAdaptiveBottomSheetVisualSpec(AppUiStyle.MIUIX)

        // 两值风格统一使用胶囊圆角（MIUIX 22 / MATERIAL3 28）。
        assertEquals(22, spec.cornerRadiusDp)
        assertTrue(spec.useMaterialDragHandle)
    }

    @Test
    fun `miuix style uses softer sheet motion`() {
        val spec = resolveAdaptiveBottomSheetMotionSpec(AppUiStyle.MIUIX)

        assertEquals(240, spec.scrimEnterDurationMillis)
        assertEquals(180, spec.scrimExitDurationMillis)
        assertEquals(240, spec.contentEnterFadeDurationMillis)
        assertEquals(180, spec.contentExitFadeDurationMillis)
    }

    @Test
    fun `material3 style keeps sheet dismiss faster than enter`() {
        val spec = resolveAdaptiveBottomSheetMotionSpec(AppUiStyle.MATERIAL3)

        assertTrue(spec.scrimExitDurationMillis < spec.scrimEnterDurationMillis)
        assertTrue(spec.contentExitFadeDurationMillis < spec.contentEnterFadeDurationMillis)
    }

    @Test
    fun `reduced custom sheet motion uses brief consistent fades for both themes`() {
        listOf(AppUiStyle.MATERIAL3, AppUiStyle.MIUIX).forEach { style ->
            val spec = resolveAdaptiveBottomSheetMotionSpec(style, reducedMotion = true)
            assertEquals(120, spec.scrimEnterDurationMillis)
            assertEquals(120, spec.contentEnterFadeDurationMillis)
            assertEquals(90, spec.scrimExitDurationMillis)
            assertEquals(90, spec.contentExitFadeDurationMillis)
        }
    }

    @Test
    fun `non glass Miuix wide windows keep a compact centered sheet`() {
        val phone = resolveAppModalLayoutSpec(windowWidthDp = 412, miuixNonGlass = true)
        val tablet = resolveAppModalLayoutSpec(windowWidthDp = 840, miuixNonGlass = true)
        val glassTablet = resolveAppModalLayoutSpec(windowWidthDp = 840, miuixNonGlass = false)

        assertEquals(AppModalPresentation.BottomSheet, phone.presentation)
        assertEquals(AppModalPresentation.CenteredDialog, tablet.presentation)
        assertEquals(420, tablet.maxWidthDp)
        assertEquals(640, glassTablet.maxWidthDp)

        val nonGlassVisual = resolveAdaptiveBottomSheetVisualSpec(
            uiStyle = AppUiStyle.MIUIX,
            miuixNonGlass = true,
        )
        val glassVisual = resolveAdaptiveBottomSheetVisualSpec(
            uiStyle = AppUiStyle.MIUIX,
            miuixNonGlass = false,
        )
        assertEquals(28, nonGlassVisual.cornerRadiusDp)
        assertEquals(22, glassVisual.cornerRadiusDp)
    }

    @Test
    fun `host contract resolves miuix to window host and material3 to material host`() {
        assertEquals(BottomSheetHost.MIUIX_WINDOW, resolveBottomSheetHost(AppUiStyle.MIUIX))
        assertEquals(BottomSheetHost.MATERIAL3, resolveBottomSheetHost(AppUiStyle.MATERIAL3))
    }

    @Test
    fun `app sheet keeps native predictive back and binds fallback dialogs to their window`() {
        val path = "src/main/java/com/android/purebilibili/core/ui/AppSheetComponents.kt"
        val source = listOf(File(path), File("design-system/$path"))
            .firstOrNull(File::exists)
            ?.readText()
            ?: error("Cannot locate AppSheetComponents.kt from ${File(".").absolutePath}")

        // MD3 使用原生预测返回；居中/折叠屏 Dialog 单独绑定窗口并提供跟手反馈。
        assertTrue(source.contains("fun ModalSheetNavigationHost("))
        assertTrue(source.contains("findViewTreeNavigationEventDispatcherOwner()"))
        assertTrue(source.contains("LocalNavigationEventDispatcherOwner provides owner"))
        assertTrue(source.contains("NavigationBackHandler("))
        assertTrue(source.contains("ModalSheetNavigationHost("))
        assertTrue(source.contains("shouldDismissOnBackPress = dismissOnBackPress"))
        assertFalse(source.contains("ModalSheetNavigationHost(dismissOnBackPress, requestMaterialDismiss)"))
        assertTrue(source.contains("NavigationEventTransitionState.TRANSITIONING_BACK"))
        assertTrue(source.contains("val progress = backProgress.value"))
        assertTrue(source.contains("DialogProperties("))
        assertTrue(source.contains("dismissOnBackPress = false"))
    }

    @Test
    fun `overlay visual progress should scale scrim and disable blur when hidden`() {
        val hidden = resolveInteractiveOverlayProgressVisual(
            presentationProgress = 0f,
            surfaceType = InteractiveOverlaySurfaceType.BOTTOM_SHEET,
            blurActive = true,
            maxScrimAlpha = 0.5f
        )
        val half = resolveInteractiveOverlayProgressVisual(
            presentationProgress = 0.5f,
            surfaceType = InteractiveOverlaySurfaceType.BOTTOM_SHEET,
            blurActive = true,
            maxScrimAlpha = 0.5f
        )
        val shown = resolveInteractiveOverlayProgressVisual(
            presentationProgress = 1f,
            surfaceType = InteractiveOverlaySurfaceType.BOTTOM_SHEET,
            blurActive = true,
            maxScrimAlpha = 0.5f
        )

        assertEquals(0f, hidden.scrimAlpha, 0.001f)
        assertFalse(hidden.blurEnabled)
        assertTrue(hidden.forceLowBlurBudget)

        assertEquals(0.25f, half.scrimAlpha, 0.001f)
        assertTrue(half.blurEnabled)
        assertTrue(half.forceLowBlurBudget)
        assertTrue(half.surfaceAlphaMultiplier < shown.surfaceAlphaMultiplier)

        assertEquals(0.5f, shown.scrimAlpha, 0.001f)
        assertTrue(shown.blurEnabled)
        assertFalse(shown.forceLowBlurBudget)
        assertEquals(1f, shown.surfaceAlphaMultiplier, 0.001f)
    }
}
