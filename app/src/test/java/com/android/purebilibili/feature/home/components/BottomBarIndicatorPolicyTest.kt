package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset
import com.android.purebilibili.core.store.LiquidGlassMode
import com.android.purebilibili.core.ui.motion.BottomBarMotionProfile
import com.android.purebilibili.core.ui.motion.resolveBottomBarMotionSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarIndicatorPolicyTest {

    @Test
    fun `five or more items stays close to top floating indicator family`() {
        val policy = resolveBottomBarIndicatorPolicy(itemCount = 5)
        val topTuning = resolveTopTabVisualTuning()

        assertEquals(topTuning.floatingIndicatorWidthMultiplier + 0.02f, policy.widthMultiplier)
        assertEquals(topTuning.floatingIndicatorMinWidthDp + 2f, policy.minWidthDp)
        assertEquals(topTuning.floatingIndicatorMaxWidthDp + 2f, policy.maxWidthDp)
        assertEquals(true, policy.clampToBounds)
        assertEquals(topTuning.floatingIndicatorMaxWidthToItemRatio + 0.02f, policy.maxWidthToItemRatio)
    }

    @Test
    fun `four items is only slightly wider than five item geometry`() {
        val policy = resolveBottomBarIndicatorPolicy(itemCount = 4)
        val topTuning = resolveTopTabVisualTuning()

        assertEquals(topTuning.floatingIndicatorWidthMultiplier + 0.04f, policy.widthMultiplier)
        assertEquals(topTuning.floatingIndicatorMinWidthDp + 4f, policy.minWidthDp)
        assertEquals(topTuning.floatingIndicatorMaxWidthDp + 4f, policy.maxWidthDp)
        assertEquals(topTuning.floatingIndicatorMaxWidthToItemRatio + 0.04f, policy.maxWidthToItemRatio)
        assertEquals(true, policy.clampToBounds)
    }

    @Test
    fun `icon and text mode with five items uses flatter indicator height on phone`() {
        assertEquals(
            50f,
            resolveBottomIndicatorHeightDp(
                labelMode = 0,
                isTablet = false,
                itemCount = 5
            )
        )
    }

    @Test
    fun `static indicator keeps theme color and disables refraction`() {
        val policy = resolveBottomBarIndicatorVisualPolicy(
            position = 2f,
            isDragging = false,
            velocity = 0f,
            useNeutralIndicatorTint = true
        )

        assertFalse(policy.isInMotion)
        assertFalse(policy.shouldRefract)
        assertFalse(policy.useNeutralTint)
    }

    @Test
    fun `moving indicator can use neutral tint and refraction`() {
        val policy = resolveBottomBarIndicatorVisualPolicy(
            position = 2.15f,
            isDragging = false,
            velocity = 52f,
            useNeutralIndicatorTint = true
        )

        assertTrue(policy.isInMotion)
        assertTrue(policy.shouldRefract)
        assertTrue(policy.useNeutralTint)
    }

    @Test
    fun `refraction layer enables tinted export only for floating moving glass`() {
        val active = resolveBottomBarRefractionLayerPolicy(
            isFloating = true,
            isLiquidGlassEnabled = true,
            indicatorVisualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = true,
                shouldRefract = true,
                useNeutralTint = true
            )
        )
        val idle = resolveBottomBarRefractionLayerPolicy(
            isFloating = true,
            isLiquidGlassEnabled = true,
            indicatorVisualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = false,
                shouldRefract = false,
                useNeutralTint = false
            )
        )
        val docked = resolveBottomBarRefractionLayerPolicy(
            isFloating = false,
            isLiquidGlassEnabled = true,
            indicatorVisualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = true,
                shouldRefract = true,
                useNeutralTint = true
            )
        )

        assertTrue(active.captureTintedContentLayer)
        assertTrue(active.useCombinedBackdrop)
        assertFalse(idle.captureTintedContentLayer)
        assertFalse(idle.useCombinedBackdrop)
        assertFalse(docked.captureTintedContentLayer)
        assertFalse(docked.useCombinedBackdrop)
    }

    @Test
    fun `idle hold keeps refraction layer alive without marking indicator moving`() {
        val idle = BottomBarIndicatorVisualPolicy(
            isInMotion = false,
            shouldRefract = false,
            useNeutralTint = false
        )

        val held = resolveBottomBarIndicatorVisualPolicyWithHold(
            basePolicy = idle,
            keepRefractionLayerAlive = true
        )
        val released = resolveBottomBarIndicatorVisualPolicyWithHold(
            basePolicy = idle,
            keepRefractionLayerAlive = false
        )

        assertFalse(held.isInMotion)
        assertTrue(held.shouldRefract)
        assertFalse(released.shouldRefract)
    }

    @Test
    fun `ios moving indicator uses bottom dock surface on light floating bar`() {
        val color = resolveIosFloatingBottomIndicatorColor(
            isDarkTheme = false,
            visualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = true,
                shouldRefract = true,
                useNeutralTint = true
            ),
            liquidGlassTuning = resolveLiquidGlassTuning(progress = 0.2f)
        )
        val surface = resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = false)

        assertEquals(surface.red, color.red)
        assertEquals(surface.green, color.green)
        assertEquals(surface.blue, color.blue)
        assertTrue(color.alpha > 0f)
    }

    @Test
    fun `ios indicator surface uses neutral dock color instead of theme color`() {
        val themeColor = Color(0xFF6750A4)
        val color = resolveIosFloatingBottomIndicatorColor(
            isDarkTheme = false,
            visualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = true,
                shouldRefract = true,
                useNeutralTint = false
            ),
            liquidGlassTuning = resolveLiquidGlassTuning(progress = 0.7f)
        )
        val surface = resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = false)

        assertEquals(surface.red, color.red)
        assertEquals(surface.green, color.green)
        assertEquals(surface.blue, color.blue)
        assertTrue(color.blue > themeColor.blue)
        assertTrue(color.alpha > 0f)
    }

    @Test
    fun `ios moving indicator alpha has floor on bright floating bar`() {
        val alpha = resolveIosFloatingBottomIndicatorTintAlpha(
            visualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = true,
                shouldRefract = true,
                useNeutralTint = true
            ),
            isDarkTheme = false,
            liquidGlassProgress = 0.2f,
            configuredAlpha = 0.12f
        )

        assertTrue(alpha >= 0.40f)
    }

    @Test
    fun `ios neutral moving indicator keeps bottom dock surface hue`() {
        val color = resolveIosFloatingBottomIndicatorColor(
            isDarkTheme = false,
            visualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = true,
                shouldRefract = true,
                useNeutralTint = true
            ),
            liquidGlassTuning = resolveLiquidGlassTuning(progress = 0.2f)
        )
        val surface = resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = false)

        assertEquals(surface.red, color.red)
        assertEquals(surface.green, color.green)
        assertEquals(surface.blue, color.blue)
    }

    @Test
    fun `moving refraction profile adds panel offset and keeps low theme emphasis`() {
        val profile = resolveBottomBarRefractionMotionProfile(
            position = 1.32f,
            velocity = 860f,
            isDragging = true
        )

        assertTrue(profile.progress > 0f)
        assertTrue(profile.exportPanelOffsetFraction > 0f)
        assertTrue(profile.indicatorPanelOffsetFraction > profile.exportPanelOffsetFraction)
        assertTrue(profile.visiblePanelOffsetFraction > 0f)
        assertTrue(profile.forceChromaticAberration)
        assertTrue(profile.visibleSelectionEmphasis > 0.22f)
        assertTrue(profile.visibleSelectionEmphasis < 0.52f)
        assertTrue(profile.exportSelectionEmphasis > 0.45f)
        assertTrue(profile.exportSelectionEmphasis < 0.72f)
        assertTrue(profile.exportCaptureWidthScale > 1f)
        assertTrue(profile.indicatorLensAmountScale > 1f)
        assertTrue(profile.indicatorLensHeightScale > 1f)
        assertTrue(profile.chromaticBoostScale > 1.4f)
    }

    @Test
    fun `backdrop native preset damps bilipai horizontal refraction motion`() {
        val profile = resolveBottomBarRefractionMotionProfile(
            position = 1.32f,
            velocity = 860f,
            isDragging = true
        )
        val effectiveProfile = resolveBottomBarEffectiveRefractionMotionProfile(
            preset = BottomBarLiquidGlassPreset.BACKDROP_NATIVE,
            profile = profile
        )

        assertEquals(profile.progress, effectiveProfile.progress, 0.001f)
        assertTrue(effectiveProfile.exportPanelOffsetFraction < profile.exportPanelOffsetFraction)
        assertTrue(effectiveProfile.indicatorPanelOffsetFraction < profile.indicatorPanelOffsetFraction)
        assertEquals(0f, effectiveProfile.visiblePanelOffsetFraction, 0.001f)
        assertTrue(effectiveProfile.visibleSelectionEmphasis > profile.visibleSelectionEmphasis)
        assertTrue(effectiveProfile.exportSelectionEmphasis > profile.exportSelectionEmphasis)
        assertEquals(1f, effectiveProfile.exportCaptureWidthScale, 0.001f)
        assertFalse(effectiveProfile.forceChromaticAberration)
        assertEquals(1f, effectiveProfile.indicatorLensAmountScale, 0.001f)
        assertEquals(1f, effectiveProfile.indicatorLensHeightScale, 0.001f)
        assertEquals(1f, effectiveProfile.chromaticBoostScale, 0.001f)
    }

    @Test
    fun `liquid glass lens progress follows backdrop preset progress`() {
        val idle = resolveBottomBarLiquidGlassLensProgress(motionProgress = 0f)
        val moving = resolveBottomBarLiquidGlassLensProgress(motionProgress = 1f)

        assertEquals(0f, idle, 0.001f)
        assertTrue(idle < moving)
        assertEquals(1f, moving, 0.001f)
    }

    @Test
    fun `vertical glass motion is neutral when glass is disabled`() {
        val profile = resolveBottomBarVerticalGlassMotionProfile(
            scrollOffsetPx = 600f,
            glassEnabled = false
        )

        assertEquals(0f, profile.progress, 0.001f)
    }

    @Test
    fun `vertical glass motion only resolves backdrop preset progress`() {
        val idle = resolveBottomBarVerticalGlassMotionProfile(
            scrollOffsetPx = 0f,
            glassEnabled = true
        )
        val scrolled = resolveBottomBarVerticalGlassMotionProfile(
            scrollOffsetPx = 180f,
            glassEnabled = true
        )

        assertEquals(0f, idle.progress, 0.001f)
        assertTrue(scrolled.progress > idle.progress)
        assertEquals(1f, scrolled.progress, 0.001f)
    }

    @Test
    fun `backdrop preset lens dimensions match AndroidLiquidGlass bottom tabs`() {
        val capture = resolveBottomBarBackdropPresetCaptureLens(progress = 1f)
        val indicator = resolveBottomBarBackdropPresetIndicatorLens(progress = 1f)

        assertEquals(24f, capture.refractionHeightDp, 0.001f)
        assertEquals(24f, capture.refractionAmountDp, 0.001f)
        assertEquals(10f, indicator.refractionHeightDp, 0.001f)
        assertEquals(14f, indicator.refractionAmountDp, 0.001f)
    }

    @Test
    fun `backdrop native surface stays frosted without jelly distortion`() {
        val idle = resolveBottomBarBackdropNativeSurfaceSpec(
            blurRadiusDp = 18f,
            verticalProgress = 0f
        )
        val scrolled = resolveBottomBarBackdropNativeSurfaceSpec(
            blurRadiusDp = 18f,
            verticalProgress = 1f
        )

        assertTrue(idle.blurRadiusDp <= 7.2f)
        assertTrue(scrolled.blurRadiusDp >= idle.blurRadiusDp)
        assertTrue(scrolled.refractionHeightDp > idle.refractionHeightDp)
        assertTrue(scrolled.refractionAmountDp > idle.refractionAmountDp)
        assertTrue(idle.refractionHeightDp >= 16f)
        assertTrue(idle.refractionAmountDp >= 14f)
        assertTrue(scrolled.refractionHeightDp <= 26f)
        assertTrue(scrolled.refractionAmountDp <= 22f)
        assertTrue(scrolled.surfaceAlphaMultiplier < idle.surfaceAlphaMultiplier)
        assertTrue(idle.surfaceAlphaMultiplier <= 0.58f)
        assertTrue(scrolled.surfaceAlphaMultiplier <= 0.42f)
        assertTrue(scrolled.highlightAlpha > idle.highlightAlpha)
        assertTrue(scrolled.shadowAlpha > idle.shadowAlpha)
        assertFalse(scrolled.chromaticAberration)
    }

    @Test
    fun `home vertical scroll does not scale bottom bar shell or capture layers`() {
        val progress = resolveBottomBarBackdropPresetProgress(
            motionProgress = 0f,
            verticalProgress = 1f,
            pressProgress = 0f
        )
        val indicator = resolveBottomBarBackdropPresetIndicatorLens(
            progress = progress.indicatorProgress
        )

        assertEquals(0f, progress.shellProgress, 0.001f)
        assertEquals(0f, progress.captureProgress, 0.001f)
        assertEquals(0f, progress.indicatorProgress, 0.001f)
        assertEquals(0f, indicator.refractionHeightDp, 0.001f)
        assertEquals(0f, indicator.refractionAmountDp, 0.001f)
    }

    @Test
    fun `indicator layer transform uses android native motion spec`() {
        val transform = resolveBottomBarIndicatorLayerTransform(
            motionProgress = 1f,
            velocityItemsPerSecond = 0f,
            motionSpec = resolveBottomBarMotionSpec(BottomBarMotionProfile.ANDROID_NATIVE_FLOATING)
        )

        assertTrue(transform.scaleX > 1.5f)
        assertTrue(transform.scaleY > 1.5f)
    }

    @Test
    fun `idle refraction profile disables offset and keeps full visible emphasis`() {
        val profile = resolveBottomBarRefractionMotionProfile(
            position = 2f,
            velocity = 0f,
            isDragging = false
        )

        assertEquals(0f, profile.progress)
        assertEquals(0f, profile.exportPanelOffsetFraction)
        assertEquals(0f, profile.indicatorPanelOffsetFraction)
        assertEquals(0f, profile.visiblePanelOffsetFraction)
        assertFalse(profile.forceChromaticAberration)
        assertEquals(1f, profile.visibleSelectionEmphasis)
        assertEquals(1f, profile.exportSelectionEmphasis)
        assertEquals(1f, profile.exportCaptureWidthScale)
        assertEquals(1f, profile.indicatorLensAmountScale)
        assertEquals(1f, profile.indicatorLensHeightScale)
        assertEquals(1f, profile.chromaticBoostScale)
    }

    @Test
    fun `sliding item visuals follow indicator instead of fixed selected tab`() {
        val home = resolveBottomBarItemMotionVisual(
            itemIndex = 0,
            indicatorPosition = 0.8f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )
        val dynamic = resolveBottomBarItemMotionVisual(
            itemIndex = 1,
            indicatorPosition = 0.8f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )

        assertTrue(dynamic.themeWeight > home.themeWeight)
        assertTrue(dynamic.scale > home.scale)
    }

    @Test
    fun `sliding item visual fills icon for item covered by indicator`() {
        val home = resolveBottomBarItemMotionVisual(
            itemIndex = 0,
            indicatorPosition = 0.8f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )
        val dynamic = resolveBottomBarItemMotionVisual(
            itemIndex = 1,
            indicatorPosition = 0.8f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )

        assertTrue(home.themeWeight in 0f..1f)
        assertTrue(dynamic.themeWeight in 0f..1f)
        assertFalse(home.useSelectedIcon)
        assertTrue(dynamic.useSelectedIcon)
    }

    @Test
    fun `sliding item selected icon alpha follows indicator position continuously`() {
        val home = resolveBottomBarItemMotionVisual(
            itemIndex = 0,
            indicatorPosition = 0.8f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )
        val dynamic = resolveBottomBarItemMotionVisual(
            itemIndex = 1,
            indicatorPosition = 0.8f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )

        assertEquals(0.2f, home.selectedIconAlpha, 0.001f)
        assertEquals(0.8f, dynamic.selectedIconAlpha, 0.001f)
    }

    @Test
    fun `sliding item color weight follows indicator position without emphasis cap`() {
        val dynamic = resolveBottomBarItemMotionVisual(
            itemIndex = 1,
            indicatorPosition = 0.8f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )

        assertEquals(0.8f, dynamic.themeWeight, 0.001f)
    }

    @Test
    fun `settle pulse only triggers after non-initial selected indicator reaches target`() {
        assertFalse(
            shouldTriggerBottomBarSettlePulse(
                hasObservedInitialSelection = false,
                selectedIndex = 1,
                indicatorPosition = 1f,
                isIndicatorRunning = false,
                isIndicatorDragging = false
            )
        )
        assertFalse(
            shouldTriggerBottomBarSettlePulse(
                hasObservedInitialSelection = true,
                selectedIndex = 1,
                indicatorPosition = 0.82f,
                isIndicatorRunning = true,
                isIndicatorDragging = false
            )
        )
        assertTrue(
            shouldTriggerBottomBarSettlePulse(
                hasObservedInitialSelection = true,
                selectedIndex = 1,
                indicatorPosition = 1.002f,
                isIndicatorRunning = false,
                isIndicatorDragging = false
            )
        )
    }

    @Test
    fun `settle pulse transform scales up then rebounds`() {
        val peak = resolveBottomBarSettlePulseTransform(progress = 0.35f)
        val rebound = resolveBottomBarSettlePulseTransform(progress = 0.65f)
        val idle = resolveBottomBarSettlePulseTransform(progress = 1f)

        assertTrue(peak.scale > 1f)
        assertTrue(peak.translationYDp < 0f)
        assertTrue(rebound.scale < 1f)
        assertTrue(rebound.translationYDp > 0f)
        assertEquals(1f, idle.scale, 0.001f)
        assertEquals(0f, idle.translationYDp, 0.001f)
    }

    @Test
    fun `click pulse transform rebounds horizontally without vertical lift`() {
        val pressed = resolveBottomBarClickPulseTransform(progress = 0.18f)
        val overshoot = resolveBottomBarClickPulseTransform(progress = 0.46f)
        val settleBack = resolveBottomBarClickPulseTransform(progress = 0.72f)
        val idle = resolveBottomBarClickPulseTransform(progress = 1f)

        assertEquals(0.945f, pressed.scaleX, 0.001f)
        assertTrue(pressed.scaleX < 1f)
        assertTrue(overshoot.scaleX > 1f)
        assertTrue(overshoot.scaleX >= 1.03f)
        assertTrue(settleBack.scaleX > 1f)
        assertTrue(settleBack.scaleX < overshoot.scaleX)
        assertEquals(1f, pressed.scaleY, 0.001f)
        assertEquals(1f, overshoot.scaleY, 0.001f)
        assertEquals(1f, idle.scaleX, 0.001f)
        assertEquals(1f, idle.scaleY, 0.001f)
    }

    @Test
    fun `click pulse release decays without a second compression twitch`() {
        val rebound = resolveBottomBarClickPulseTransform(progress = 0.46f)
        val settle = resolveBottomBarClickPulseTransform(progress = 0.68f)
        val nearlyIdle = resolveBottomBarClickPulseTransform(progress = 0.88f)

        assertTrue(rebound.scaleX > settle.scaleX)
        assertTrue(settle.scaleX > nearlyIdle.scaleX)
        assertTrue(nearlyIdle.scaleX >= 1f)
    }

    @Test
    fun `sliding color transfers continuously from current tab to next tab`() {
        val home = resolveBottomBarItemMotionVisual(
            itemIndex = 0,
            indicatorPosition = 0.2f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )
        val dynamic = resolveBottomBarItemMotionVisual(
            itemIndex = 1,
            indicatorPosition = 0.2f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )

        assertEquals(0.8f, home.themeWeight, 0.001f)
        assertEquals(0.2f, dynamic.themeWeight, 0.001f)
    }

    @Test
    fun `sliding item scale only affects indicator neighbors`() {
        val home = resolveBottomBarItemMotionVisual(
            itemIndex = 0,
            indicatorPosition = 0.5f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )
        val dynamic = resolveBottomBarItemMotionVisual(
            itemIndex = 1,
            indicatorPosition = 0.5f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )
        val history = resolveBottomBarItemMotionVisual(
            itemIndex = 2,
            indicatorPosition = 0.5f,
            currentSelectedIndex = 0,
            motionProgress = 1f,
            selectionEmphasis = 0.28f
        )
        val profile = resolveBottomBarRefractionMotionProfile(
            position = 0.5f,
            velocity = 620f,
            isDragging = true
        )

        assertTrue(home.scale > 1f)
        assertTrue(dynamic.scale > 1f)
        assertEquals(1f, history.scale)
        assertEquals(0f, history.themeWeight)
        assertTrue(profile.progress > 0f)
    }

    @Test
    fun `frosted idle indicator keeps stronger alpha floor for visibility`() {
        val alpha = resolveBottomBarIndicatorTintAlpha(
            shouldRefract = false,
            liquidGlassProgress = 1f,
            configuredAlpha = 0.14f
        )

        assertTrue(alpha >= 0.56f)
    }

    @Test
    fun `clear idle indicator stays lighter than frosted idle indicator`() {
        val clear = resolveBottomBarIndicatorTintAlpha(
            shouldRefract = false,
            liquidGlassProgress = 0f,
            configuredAlpha = 0.14f
        )
        val frosted = resolveBottomBarIndicatorTintAlpha(
            shouldRefract = false,
            liquidGlassProgress = 1f,
            configuredAlpha = 0.14f
        )

        assertTrue(clear < frosted)
    }
}
