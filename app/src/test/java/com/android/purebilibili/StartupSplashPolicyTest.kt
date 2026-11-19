package com.android.purebilibili

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSplashPolicyTest {

    @Test
    fun alwaysReadsCustomSplashPreferences() {
        assertTrue(shouldReadCustomSplashPreferences())
    }

    @Test
    fun doesNotStartLocalProxyDuringColdStartByDefault() {
        assertFalse(shouldStartLocalProxyOnAppLaunch())
    }

    @Test
    fun splashWallpaperAlignmentBias_prefersTabletBiasOnTabletLayout() {
        assertEquals(
            0.55f,
            resolveSplashWallpaperAlignmentBias(
                isTabletLayout = true,
                mobileBias = -0.2f,
                tabletBias = 0.55f
            ),
            0.001f
        )
        assertEquals(
            -0.2f,
            resolveSplashWallpaperAlignmentBias(
                isTabletLayout = false,
                mobileBias = -0.2f,
                tabletBias = 0.55f
            ),
            0.001f
        )
    }

    @Test
    fun customSplashFade_usesSlowIosLikeDurationWindow() {
        assertEquals(1900L, customSplashHoldDurationMs())
        assertEquals(1450, customSplashFadeDurationMs())
        assertTrue(customSplashFadeDurationMs() > 1000)
    }

    @Test
    fun customSplashOverlayRenderPolicy_keepsOverlayDuringFadeTail() {
        assertTrue(customSplashShouldRender(showSplash = true, overlayAlpha = 1f))
        assertTrue(customSplashShouldRender(showSplash = false, overlayAlpha = 0.12f))
        assertFalse(customSplashShouldRender(showSplash = false, overlayAlpha = 0.01f))
    }

    @Test
    fun customSplashOverlayVisualCurve_usesGentleTailForIosLikeTransition() {
        assertEquals(0f, customSplashFadeProgress(overlayAlpha = 1f), 0.001f)
        assertEquals(1f, customSplashFadeProgress(overlayAlpha = 0f), 0.001f)

        assertEquals(1f, customSplashOverlayScale(fadeProgress = 0f), 0.001f)
        assertTrue(customSplashOverlayScale(fadeProgress = 1f) > 1f)

        assertEquals(0f, customSplashOverlayScrimAlpha(fadeProgress = 0f), 0.001f)
        assertTrue(customSplashOverlayScrimAlpha(fadeProgress = 1f) > 0.1f)

        assertEquals(0f, customSplashExtraBlurDp(fadeProgress = 0f), 0.001f)
        assertTrue(customSplashExtraBlurDp(fadeProgress = 1f) >= 10f)
    }

    @Test
    fun resolveSplashWallpaperUriForLaunch_usesVisiblePoolWhenRandomEnabled() {
        val resolved = resolveSplashWallpaperUriForLaunch(
            randomEnabled = true,
            fixedSplashUri = "content://fixed",
            poolUris = listOf("content://a", "content://b", "content://c"),
            launchSeed = 4L
        )
        assertEquals("content://b", resolved)
    }

    @Test
    fun resolveSplashWallpaperUriForLaunch_fallsBackToFixedWhenPoolUnavailable() {
        assertEquals(
            "content://fixed",
            resolveSplashWallpaperUriForLaunch(
                randomEnabled = true,
                fixedSplashUri = "content://fixed",
                poolUris = emptyList(),
                launchSeed = 9L
            )
        )
        assertEquals(
            "content://fixed",
            resolveSplashWallpaperUriForLaunch(
                randomEnabled = false,
                fixedSplashUri = "content://fixed",
                poolUris = listOf("content://a", "content://b"),
                launchSeed = 9L
            )
        )
    }

    @Test
    fun enablesSplashFlyoutOnlyAfterPrivacyAgreementOnSupportedAndroidWithAnimationEnabled() {
        assertFalse(
            shouldEnableSplashFlyoutAnimation(
                sdkInt = 31,
                agreementAccepted = false,
                splashIconAnimationEnabled = true
            )
        )
        assertFalse(
            shouldEnableSplashFlyoutAnimation(
                sdkInt = 30,
                agreementAccepted = true,
                splashIconAnimationEnabled = true
            )
        )
        assertFalse(
            shouldEnableSplashFlyoutAnimation(
                sdkInt = 31,
                agreementAccepted = true,
                splashIconAnimationEnabled = false
            )
        )
        assertTrue(
            shouldEnableSplashFlyoutAnimation(
                sdkInt = 31,
                agreementAccepted = true,
                splashIconAnimationEnabled = true
            )
        )
    }

    @Test
    fun systemSplashPreloadHold_runsForEveryColdStartEvenWithoutOptionalIcon() {
        assertTrue(
            shouldKeepSystemSplashForPreload(
                runColdStartSplash = true
            )
        )
        assertFalse(
            shouldKeepSystemSplashForPreload(
                runColdStartSplash = false
            )
        )
    }

    @Test
    fun coldStartSplash_runsOnlyWithoutSavedInstanceState() {
        assertTrue(shouldRunColdStartSplash(savedInstanceStatePresent = false))
        assertFalse(shouldRunColdStartSplash(savedInstanceStatePresent = true))
    }
}
