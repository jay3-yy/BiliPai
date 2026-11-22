package com.android.purebilibili.feature.onboarding

import android.os.Build
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.feature.settings.AppThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirstLaunchSetupPolicyTest {
    @Test
    fun android12AndBelowSkipsLiquidGlassStep() {
        val steps = resolveFirstLaunchSetupSteps(Build.VERSION_CODES.S_V2)
        assertEquals(
            listOf(
                FirstLaunchSetupStep.THEME_STYLE,
                FirstLaunchSetupStep.APPEARANCE_COLOR,
                FirstLaunchSetupStep.HOME_LAYOUT,
            ),
            steps,
        )
        assertEquals(
            FirstLaunchSetupStep.APPEARANCE_COLOR,
            resolveNextFirstLaunchStep(FirstLaunchSetupStep.THEME_STYLE, steps),
        )
        assertNull(resolveNextFirstLaunchStep(FirstLaunchSetupStep.HOME_LAYOUT, steps))
        assertNull(resolvePreviousFirstLaunchStep(FirstLaunchSetupStep.THEME_STYLE, steps))
        assertFalse(shouldPersistLiquidGlassChoice(Build.VERSION_CODES.S_V2))
    }

    @Test
    fun android13AndAboveIncludesLiquidGlassStep() {
        val steps = resolveFirstLaunchSetupSteps(Build.VERSION_CODES.TIRAMISU)
        assertEquals(
            listOf(
                FirstLaunchSetupStep.THEME_STYLE,
                FirstLaunchSetupStep.APPEARANCE_COLOR,
                FirstLaunchSetupStep.LIQUID_GLASS,
                FirstLaunchSetupStep.HOME_LAYOUT,
            ),
            steps,
        )
        assertEquals(
            FirstLaunchSetupStep.LIQUID_GLASS,
            resolveNextFirstLaunchStep(FirstLaunchSetupStep.APPEARANCE_COLOR, steps),
        )
        assertEquals(
            FirstLaunchSetupStep.HOME_LAYOUT,
            resolveNextFirstLaunchStep(FirstLaunchSetupStep.LIQUID_GLASS, steps),
        )
        assertNull(resolveNextFirstLaunchStep(FirstLaunchSetupStep.HOME_LAYOUT, steps))
        assertEquals(
            FirstLaunchSetupStep.APPEARANCE_COLOR,
            resolvePreviousFirstLaunchStep(FirstLaunchSetupStep.LIQUID_GLASS, steps),
        )
        assertTrue(shouldPersistLiquidGlassChoice(Build.VERSION_CODES.TIRAMISU))
    }

    @Test
    fun initialDraftGatesSdkSpecificOptionsAndClampsLayoutValues() {
        val android11 = resolveInitialFirstLaunchSetupDraft(
            currentUiStyle = AppUiStyle.MIUIX,
            currentLiquidGlassEnabled = true,
            currentDynamicColor = true,
            currentDisplayMode = 99,
            currentBottomBarLabelMode = 9,
            sdkInt = Build.VERSION_CODES.R,
        )
        assertEquals(AppUiStyle.MIUIX, android11.uiStyle)
        assertFalse(android11.liquidGlassEnabled)
        assertFalse(android11.dynamicColor)
        assertEquals(0, android11.displayMode)
        assertEquals(2, android11.bottomBarLabelMode)
        assertFalse(shouldOfferDynamicColorOption(Build.VERSION_CODES.R))

        val android12 = resolveInitialFirstLaunchSetupDraft(
            currentUiStyle = AppUiStyle.MATERIAL3,
            currentLiquidGlassEnabled = true,
            currentDynamicColor = true,
            currentDisplayMode = 1,
            currentBottomBarFloating = false,
            currentBottomBarLabelMode = 1,
            sdkInt = Build.VERSION_CODES.S,
        )
        assertFalse(android12.liquidGlassEnabled)
        assertTrue(android12.dynamicColor)
        assertEquals(1, android12.displayMode)
        assertFalse(android12.bottomBarFloating)
        assertEquals(1, android12.bottomBarLabelMode)
        assertTrue(shouldOfferDynamicColorOption(Build.VERSION_CODES.S))

        val android13 = resolveInitialFirstLaunchSetupDraft(
            currentUiStyle = AppUiStyle.MATERIAL3,
            currentLiquidGlassEnabled = true,
            currentDynamicColor = true,
            sdkInt = Build.VERSION_CODES.TIRAMISU,
        )
        assertTrue(android13.liquidGlassEnabled)
        assertTrue(android13.dynamicColor)
    }

    @Test
    fun darkThemeResolutionRespectsThemeMode() {
        assertTrue(resolveFirstLaunchSetupDarkTheme(AppThemeMode.FOLLOW_SYSTEM, systemInDarkTheme = true))
        assertFalse(resolveFirstLaunchSetupDarkTheme(AppThemeMode.FOLLOW_SYSTEM, systemInDarkTheme = false))
        assertFalse(resolveFirstLaunchSetupDarkTheme(AppThemeMode.LIGHT, systemInDarkTheme = true))
        assertTrue(resolveFirstLaunchSetupDarkTheme(AppThemeMode.DARK, systemInDarkTheme = false))
    }

    @Test
    fun backgroundAmbientFollowsUiStyleAndLiquidGlassSelection() {
        assertTrue(
            resolveFirstLaunchBackgroundUsesMiuixAmbient(
                uiStyle = AppUiStyle.MIUIX,
                currentStep = FirstLaunchSetupStep.THEME_STYLE,
                liquidGlassEnabled = false,
            )
        )
        assertFalse(
            resolveFirstLaunchBackgroundUsesMiuixAmbient(
                uiStyle = AppUiStyle.MATERIAL3,
                currentStep = FirstLaunchSetupStep.THEME_STYLE,
                liquidGlassEnabled = true,
            )
        )
        assertTrue(
            resolveFirstLaunchBackgroundUsesMiuixAmbient(
                uiStyle = AppUiStyle.MATERIAL3,
                currentStep = FirstLaunchSetupStep.LIQUID_GLASS,
                liquidGlassEnabled = true,
            )
        )
        assertFalse(
            resolveFirstLaunchBackgroundUsesMiuixAmbient(
                uiStyle = AppUiStyle.MATERIAL3,
                currentStep = FirstLaunchSetupStep.LIQUID_GLASS,
                liquidGlassEnabled = false,
            )
        )
    }

    @Test
    fun mockupAmbientMotionOnlyRunsForSelectedMiuixWithoutReduceMotion() {
        assertTrue(
            resolveFirstLaunchMockupAmbientMotion(
                style = AppUiStyle.MIUIX,
                selected = true,
                reduceMotion = false,
            )
        )
        assertFalse(
            resolveFirstLaunchMockupAmbientMotion(
                style = AppUiStyle.MIUIX,
                selected = false,
                reduceMotion = false,
            )
        )
        assertFalse(
            resolveFirstLaunchMockupAmbientMotion(
                style = AppUiStyle.MIUIX,
                selected = true,
                reduceMotion = true,
            )
        )
        assertFalse(
            resolveFirstLaunchMockupAmbientMotion(
                style = AppUiStyle.MATERIAL3,
                selected = true,
                reduceMotion = false,
            )
        )
    }

    @Test
    fun liquidGlassPreviewAutoDemoStopsWhenDisabledReducedInteractedOrInactive() {
        assertTrue(
            shouldRunLiquidGlassPreviewAutoDemo(
                liquidGlassEnabled = true,
                reduceMotion = false,
                userInteracted = false,
                gateActive = true,
            )
        )
        assertFalse(
            shouldRunLiquidGlassPreviewAutoDemo(
                liquidGlassEnabled = false,
                reduceMotion = false,
                userInteracted = false,
                gateActive = true,
            )
        )
        assertFalse(
            shouldRunLiquidGlassPreviewAutoDemo(
                liquidGlassEnabled = true,
                reduceMotion = true,
                userInteracted = false,
                gateActive = true,
            )
        )
        assertFalse(
            shouldRunLiquidGlassPreviewAutoDemo(
                liquidGlassEnabled = true,
                reduceMotion = false,
                userInteracted = true,
                gateActive = true,
            )
        )
        assertFalse(
            shouldRunLiquidGlassPreviewAutoDemo(
                liquidGlassEnabled = true,
                reduceMotion = false,
                userInteracted = false,
                gateActive = false,
            )
        )
    }
}
