package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomePerformancePolicyTest {

    @Test
    fun keepsHomeVisualSettingsWhenDataSaverOff() {
        val config = resolveHomePerformanceConfig(
            supportsIndependentLiquidGlass = true,
            headerBlurEnabled = true,
            bottomBarBlurEnabled = false,
            topBarLiquidGlassEnabled = true,
            bottomBarLiquidGlassEnabled = false,
            androidNativeLiquidGlassEnabled = true,
            cardAnimationEnabled = false,
            cardTransitionEnabled = true,
            isDataSaverActive = false,
            smartVisualGuardEnabled = false,
            normalPreloadAheadCount = 5
        )

        assertTrue(config.headerBlurEnabled)
        assertFalse(config.bottomBarBlurEnabled)
        assertTrue(config.topBarLiquidGlassEnabled)
        assertFalse(config.bottomBarLiquidGlassEnabled)
        assertTrue(config.isAnyLiquidGlassEnabled)
        assertFalse(config.cardAnimationEnabled)
        assertTrue(config.cardTransitionEnabled)
        assertFalse(config.isDataSaverActive)
        assertEquals(2, config.preloadAheadCount)
    }

    @Test
    fun dataSaverDisablesPreloadAhead() {
        val config = resolveHomePerformanceConfig(
            supportsIndependentLiquidGlass = true,
            headerBlurEnabled = true,
            bottomBarBlurEnabled = true,
            topBarLiquidGlassEnabled = true,
            bottomBarLiquidGlassEnabled = true,
            androidNativeLiquidGlassEnabled = true,
            cardAnimationEnabled = true,
            cardTransitionEnabled = true,
            isDataSaverActive = true,
            smartVisualGuardEnabled = false,
            normalPreloadAheadCount = 5
        )

        assertTrue(config.isDataSaverActive)
        assertTrue(config.preloadAheadCount == 0)
    }

    @Test
    fun smartGuardFlag_noLongerAffectsHomePerformanceConfig() {
        val config = resolveHomePerformanceConfig(
            supportsIndependentLiquidGlass = true,
            headerBlurEnabled = true,
            bottomBarBlurEnabled = true,
            topBarLiquidGlassEnabled = true,
            bottomBarLiquidGlassEnabled = true,
            androidNativeLiquidGlassEnabled = true,
            cardAnimationEnabled = true,
            cardTransitionEnabled = true,
            isDataSaverActive = false,
            smartVisualGuardEnabled = true,
            normalPreloadAheadCount = 5
        )

        assertFalse(config.isDataSaverActive)
        assertTrue(config.isAnyLiquidGlassEnabled)
        assertEquals(2, config.preloadAheadCount)
    }

    @Test
    fun normalMode_capsPreloadAheadToConservativeBudget() {
        val config = resolveHomePerformanceConfig(
            supportsIndependentLiquidGlass = true,
            headerBlurEnabled = true,
            bottomBarBlurEnabled = true,
            topBarLiquidGlassEnabled = true,
            bottomBarLiquidGlassEnabled = true,
            cardAnimationEnabled = true,
            cardTransitionEnabled = true,
            isDataSaverActive = false,
            smartVisualGuardEnabled = false,
            normalPreloadAheadCount = 5
        )

        assertEquals(2, config.preloadAheadCount)
    }

    @Test
    fun coverPreloadRange_waitsUntilFeedScrollSettles() {
        assertNull(
            resolveHomeCoverPreloadRange(
                isDataSaverActive = false,
                isScrollInProgress = true,
                lastVisibleIndex = 8,
                totalItemCount = 20,
                preloadAheadCount = 2
            )
        )
    }

    @Test
    fun coverPreloadRange_usesConservativeWindowAfterSettledScroll() {
        assertEquals(
            9 until 11,
            resolveHomeCoverPreloadRange(
                isDataSaverActive = false,
                isScrollInProgress = false,
                lastVisibleIndex = 8,
                totalItemCount = 20,
                preloadAheadCount = 4
            )
        )
    }

    @Test
    fun coverPreloadRange_disablesWhenDataSaverActive() {
        assertNull(
            resolveHomeCoverPreloadRange(
                isDataSaverActive = true,
                isScrollInProgress = false,
                lastVisibleIndex = 8,
                totalItemCount = 20,
                preloadAheadCount = 2
            )
        )
    }

    @Test
    fun coverPreloadRange_doesNotRestartFromTheBeginningWithoutAVisibleVideo() {
        assertNull(resolveHomeCoverPreloadRange(false, false, -1, 20, 2))
        assertNull(resolveHomeCoverPreloadRange(false, false, 20, 20, 2))
        assertNull(resolveHomeCoverPreloadRange(false, false, 0, 0, 2))
        assertNull(resolveHomeCoverPreloadRange(false, false, 19, 20, 2))
    }

    @Test
    fun md3Preset_requiresAndroidNativeGlobalOptInForSharedLiquidGlass() {
        val config = resolveHomePerformanceConfig(
            supportsIndependentLiquidGlass = false,
            headerBlurEnabled = true,
            bottomBarBlurEnabled = true,
            topBarLiquidGlassEnabled = true,
            homeSearchLiquidGlassEnabled = true,
            bottomBarLiquidGlassEnabled = true,
            androidNativeLiquidGlassEnabled = false,
            cardAnimationEnabled = true,
            cardTransitionEnabled = true,
            isDataSaverActive = false,
            smartVisualGuardEnabled = false,
            normalPreloadAheadCount = 5
        )

        assertFalse(config.topBarLiquidGlassEnabled)
        assertFalse(config.homeSearchLiquidGlassEnabled)
        assertFalse(config.bottomBarLiquidGlassEnabled)
        assertFalse(config.isAnyLiquidGlassEnabled)
    }

    @Test
    fun legacyIndependentValuesCannotEnableGlassWithoutGlobalEntry() {
        val config = resolveHomePerformanceConfig(
            supportsIndependentLiquidGlass = true,
            headerBlurEnabled = false,
            bottomBarBlurEnabled = false,
            topBarLiquidGlassEnabled = true,
            homeSearchLiquidGlassEnabled = true,
            bottomBarLiquidGlassEnabled = true,
            androidNativeLiquidGlassEnabled = false,
            cardAnimationEnabled = false,
            cardTransitionEnabled = false,
            isDataSaverActive = false,
            smartVisualGuardEnabled = false,
        )

        assertFalse(config.topBarLiquidGlassEnabled)
        assertFalse(config.homeSearchLiquidGlassEnabled)
        assertFalse(config.bottomBarLiquidGlassEnabled)
    }

    @Test
    fun md3Preset_dockOnlyDoesNotEnableTopOrSearch() {
        val config = resolveHomePerformanceConfig(
            supportsIndependentLiquidGlass = false,
            headerBlurEnabled = true,
            bottomBarBlurEnabled = true,
            topBarLiquidGlassEnabled = false,
            homeSearchLiquidGlassEnabled = false,
            bottomBarLiquidGlassEnabled = true,
            androidNativeLiquidGlassEnabled = true,
            cardAnimationEnabled = true,
            cardTransitionEnabled = true,
            isDataSaverActive = false,
            smartVisualGuardEnabled = false,
            normalPreloadAheadCount = 5
        )

        assertFalse(config.topBarLiquidGlassEnabled)
        assertFalse(config.homeSearchLiquidGlassEnabled)
        assertTrue(config.bottomBarLiquidGlassEnabled)
        assertTrue(config.isAnyLiquidGlassEnabled)
    }
}
