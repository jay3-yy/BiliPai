package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailSystemBarsPolicyTest {

    @Test
    fun restorePolicy_defaultsToFallbackValuesWhenSnapshotMissing() {
        val snapshot = resolveVideoDetailSystemBarsSnapshot(
            statusBarColor = null,
            navigationBarColor = null,
            lightStatusBars = null,
            lightNavigationBars = null,
            systemBarsBehavior = null,
            fallbackColor = 0x00000000,
            fallbackLightBars = true,
            fallbackSystemBarsBehavior = 1
        )

        assertEquals(0x00000000, snapshot.statusBarColor)
        assertEquals(0x00000000, snapshot.navigationBarColor)
        assertEquals(true, snapshot.lightStatusBars)
        assertEquals(true, snapshot.lightNavigationBars)
        assertEquals(1, snapshot.systemBarsBehavior)
    }

    @Test
    fun restorePolicy_usesCapturedSystemBarsSnapshotWhenAvailable() {
        val snapshot = resolveVideoDetailSystemBarsSnapshot(
            statusBarColor = 0x11223344,
            navigationBarColor = 0x55667788,
            lightStatusBars = false,
            lightNavigationBars = false,
            systemBarsBehavior = 2,
            fallbackColor = 0x00000000,
            fallbackLightBars = true,
            fallbackSystemBarsBehavior = 1
        )

        assertEquals(0x11223344, snapshot.statusBarColor)
        assertEquals(0x55667788, snapshot.navigationBarColor)
        assertEquals(false, snapshot.lightStatusBars)
        assertEquals(false, snapshot.lightNavigationBars)
        assertEquals(2, snapshot.systemBarsBehavior)
    }

    @Test
    fun restorePolicy_alwaysShowsSystemBarsOnExit() {
        assertTrue(shouldShowSystemBarsOnVideoDetailExit())
    }

    @Test
    fun visibilityPolicy_defaultOrdinaryPageShowsSystemBars() {
        val policy = resolveVideoDetailSystemBarsVisibilityPolicy(
            isFullscreenMode = false,
            hideVideoPageStatusBar = false,
            isInPipMode = false,
            isScreenActive = true
        )

        assertEquals(false, policy.hideStatusBars)
        assertEquals(false, policy.hideNavigationBars)
    }

    @Test
    fun visibilityPolicy_immersiveSettingKeepsStatusBarVisibleOnOrdinaryPage() {
        val policy = resolveVideoDetailSystemBarsVisibilityPolicy(
            isFullscreenMode = false,
            hideVideoPageStatusBar = true,
            isInPipMode = false,
            isScreenActive = true
        )

        assertEquals(false, policy.hideStatusBars)
        assertEquals(false, policy.hideNavigationBars)
    }

    @Test
    fun visibilityPolicy_fullscreenStillHidesAllSystemBars() {
        val policy = resolveVideoDetailSystemBarsVisibilityPolicy(
            isFullscreenMode = true,
            hideVideoPageStatusBar = false,
            isInPipMode = false,
            isScreenActive = true
        )

        assertEquals(true, policy.hideStatusBars)
        assertEquals(true, policy.hideNavigationBars)
    }

    @Test
    fun visibilityPolicy_portraitFullscreenHidesAllSystemBarsForImmersion() {
        val policy = resolveVideoDetailSystemBarsVisibilityPolicy(
            isFullscreenMode = false,
            hideVideoPageStatusBar = false,
            isInPipMode = false,
            isScreenActive = true,
            isPortraitFullscreen = true
        )

        assertEquals(true, policy.hideStatusBars)
        assertEquals(true, policy.hideNavigationBars)
    }

    @Test
    fun visibilityPolicy_portraitFullscreenCanForceShowBars() {
        val policy = resolveVideoDetailSystemBarsVisibilityPolicy(
            isFullscreenMode = false,
            hideVideoPageStatusBar = false,
            isInPipMode = false,
            isScreenActive = true,
            isPortraitFullscreen = true,
            forceShowSystemBarsInPortrait = true
        )

        assertEquals(false, policy.hideStatusBars)
        assertEquals(false, policy.hideNavigationBars)
    }

    @Test
    fun visibilityPolicy_pipRestoresSystemBarsEvenWhenSettingEnabled() {
        val policy = resolveVideoDetailSystemBarsVisibilityPolicy(
            isFullscreenMode = false,
            hideVideoPageStatusBar = true,
            isInPipMode = true,
            isScreenActive = true
        )

        assertEquals(false, policy.hideStatusBars)
        assertEquals(false, policy.hideNavigationBars)
    }

    @Test
    fun applySpec_ordinaryHiddenStatusBarOnlyHidesStatusBars() {
        val spec = resolveVideoDetailSystemBarsApplySpec(
            visibilityPolicy = VideoDetailSystemBarsVisibilityPolicy(
                hideStatusBars = true,
                hideNavigationBars = false
            ),
            useTabletLayout = false,
            isLightBackground = true,
            backgroundColor = 0x12345678,
            transparentColor = 0x00000000,
            blackColor = 0xFF000000.toInt(),
            transientBarsBehavior = 2
        )

        assertEquals(VideoDetailHiddenSystemBars.STATUS_BARS, spec.hiddenBars)
        assertEquals(2, spec.systemBarsBehavior)
        assertEquals(0x00000000, spec.statusBarColor)
        assertEquals(0x00000000, spec.navigationBarColor)
        assertEquals(false, spec.lightStatusBars)
        assertEquals(false, spec.lightNavigationBars)
    }

    @Test
    fun applySpec_fullscreenHidesAllSystemBarsWithBlackBars() {
        val spec = resolveVideoDetailSystemBarsApplySpec(
            visibilityPolicy = VideoDetailSystemBarsVisibilityPolicy(
                hideStatusBars = true,
                hideNavigationBars = true
            ),
            useTabletLayout = false,
            isLightBackground = true,
            backgroundColor = 0x12345678,
            transparentColor = 0x00000000,
            blackColor = 0xFF000000.toInt(),
            transientBarsBehavior = 2
        )

        assertEquals(VideoDetailHiddenSystemBars.SYSTEM_BARS, spec.hiddenBars)
        assertEquals(0xFF000000.toInt(), spec.statusBarColor)
        assertEquals(0xFF000000.toInt(), spec.navigationBarColor)
        assertEquals(false, spec.lightStatusBars)
        assertEquals(false, spec.lightNavigationBars)
    }

    @Test
    fun applySpec_tabletVisibleSystemBarsUseBackgroundAndLightFlags() {
        val spec = resolveVideoDetailSystemBarsApplySpec(
            visibilityPolicy = VideoDetailSystemBarsVisibilityPolicy(
                hideStatusBars = false,
                hideNavigationBars = false
            ),
            useTabletLayout = true,
            isLightBackground = true,
            backgroundColor = 0x12345678,
            transparentColor = 0x00000000,
            blackColor = 0xFF000000.toInt(),
            transientBarsBehavior = 2
        )

        assertEquals(VideoDetailHiddenSystemBars.NONE, spec.hiddenBars)
        assertEquals(0x12345678, spec.statusBarColor)
        assertEquals(0x12345678, spec.navigationBarColor)
        assertEquals(true, spec.lightStatusBars)
        assertEquals(true, spec.lightNavigationBars)
    }

    @Test
    fun inlinePlaybackRestoresPersistentBarsAfterFullscreenForBothLayouts() {
        for (tablet in listOf(false, true)) {
            val policy = resolveVideoDetailSystemBarsVisibilityPolicy(
                isFullscreenMode = false,
                hideVideoPageStatusBar = true,
                isInPipMode = false,
                isScreenActive = true,
            )
            val spec = resolveVideoDetailSystemBarsApplySpec(
                visibilityPolicy = policy,
                useTabletLayout = tablet,
                isLightBackground = false,
                backgroundColor = 0,
                transparentColor = 0,
                blackColor = 0xFF000000.toInt(),
                transientBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
            )
            assertEquals(VideoDetailHiddenSystemBars.NONE, spec.hiddenBars)
            assertEquals(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT, spec.systemBarsBehavior)
        }
    }

    @Test
    fun applySpec_sameInputsProduceEqualStableSpec() {
        val first = resolveVideoDetailSystemBarsApplySpec(
            visibilityPolicy = VideoDetailSystemBarsVisibilityPolicy(
                hideStatusBars = true,
                hideNavigationBars = false
            ),
            useTabletLayout = false,
            isLightBackground = false,
            backgroundColor = 0x12345678,
            transparentColor = 0x00000000,
            blackColor = 0xFF000000.toInt(),
            transientBarsBehavior = 2
        )
        val second = resolveVideoDetailSystemBarsApplySpec(
            visibilityPolicy = VideoDetailSystemBarsVisibilityPolicy(
                hideStatusBars = true,
                hideNavigationBars = false
            ),
            useTabletLayout = false,
            isLightBackground = false,
            backgroundColor = 0x12345678,
            transparentColor = 0x00000000,
            blackColor = 0xFF000000.toInt(),
            transientBarsBehavior = 2
        )

        assertEquals(first, second)
    }

    @Test
    fun statusBarInset_usesVisibleInsetWhenStatusBarRemainsVisible() {
        assertEquals(
            24f,
            resolveVideoDetailStableStatusBarHeightDp(
                visibleStatusBarHeightDp = 24f,
                statusBarIgnoringVisibilityHeightDp = 24f,
                hideStatusBars = false
            )
        )
    }

    @Test
    fun statusBarInset_keepsIgnoringVisibilityInsetWhenStatusBarIsHidden() {
        assertEquals(
            24f,
            resolveVideoDetailStableStatusBarHeightDp(
                visibleStatusBarHeightDp = 0f,
                statusBarIgnoringVisibilityHeightDp = 24f,
                hideStatusBars = true
            )
        )
    }

    @Test
    fun statusBarInset_clampsInvalidInsets() {
        assertEquals(
            0f,
            resolveVideoDetailStableStatusBarHeightDp(
                visibleStatusBarHeightDp = Float.NaN,
                statusBarIgnoringVisibilityHeightDp = -1f,
                hideStatusBars = true
            )
        )
    }

    @Test
    fun portraitPlayerTopInset_alwaysReservesStatusBarWhenBarsVisible() {
        // 详情页内联播放器始终沉浸：开关只切换背景条样式（实时模糊 / 纯黑），
        // 不再决定是否预留状态栏高度。
        assertEquals(
            0f,
            resolveVideoDetailPortraitPlayerTopInsetDp(
                stableStatusBarHeightDp = 24f,
                hideStatusBars = true,
                immersiveStatusBarBackdropEnabled = false,
            )
        )
        assertEquals(
            24f,
            resolveVideoDetailPortraitPlayerTopInsetDp(
                stableStatusBarHeightDp = 24f,
                hideStatusBars = false,
                immersiveStatusBarBackdropEnabled = false,
            )
        )
        assertEquals(
            24f,
            resolveVideoDetailPortraitPlayerTopInsetDp(
                stableStatusBarHeightDp = 24f,
                hideStatusBars = false,
                immersiveStatusBarBackdropEnabled = true,
                isSharedCardTransition = true,
            )
        )
    }

    @Test
    fun collapsedPlayerChrome_usesReadableStatusBarIconsOnLightSurface() {
        val spec = resolveVideoDetailSystemBarsApplySpec(
            visibilityPolicy = VideoDetailSystemBarsVisibilityPolicy(
                hideStatusBars = false,
                hideNavigationBars = false,
            ),
            useTabletLayout = false,
            isLightBackground = true,
            useCollapsedPlayerChromeAppearance = true,
            backgroundColor = 1,
            transparentColor = 2,
            blackColor = 3,
            transientBarsBehavior = 4,
        )

        assertTrue(spec.lightStatusBars)
        assertFalse(spec.lightNavigationBars)
    }

    @Test
    fun playerChrome_padsOnlyWhenStatusBarVisible() {
        assertTrue(shouldApplyStatusBarPaddingToVideoPlayerChrome(statusBarVisible = true))
        assertFalse(shouldApplyStatusBarPaddingToVideoPlayerChrome(statusBarVisible = false))
    }

    @Test
    fun restorePolicy_restoresSystemBarsAsSoonAsExitTransitionStarts() {
        assertTrue(
            shouldRestoreSystemBarsDuringVideoDetailExitTransition(
                isExitTransitionInProgress = true,
                isActuallyLeaving = false
            )
        )
    }

    @Test
    fun restorePolicy_skipsDuplicateRestoreWhenExitWasAlreadyHandledExplicitly() {
        assertTrue(
            !shouldRestoreSystemBarsDuringVideoDetailExitTransition(
                isExitTransitionInProgress = true,
                isActuallyLeaving = true
            )
        )
    }

    @Test
    fun restorePolicy_doesNotRestoreBeforeExitTransitionBegins() {
        assertTrue(
            !shouldRestoreSystemBarsDuringVideoDetailExitTransition(
                isExitTransitionInProgress = false,
                isActuallyLeaving = false
            )
        )
    }

    @Test
    fun reactivatePolicy_restoresImmersiveAfterCancelledPredictiveExit() {
        assertTrue(
            shouldReactivateVideoDetailSystemBarsAfterCancelledExit(
                isExitTransitionInProgress = false,
                isActuallyLeaving = false,
                isScreenActive = false,
            )
        )
    }

    @Test
    fun reactivatePolicy_skipsWhileExitStillInProgressOrAlreadyActive() {
        assertTrue(
            !shouldReactivateVideoDetailSystemBarsAfterCancelledExit(
                isExitTransitionInProgress = true,
                isActuallyLeaving = false,
                isScreenActive = false,
            )
        )
        assertTrue(
            !shouldReactivateVideoDetailSystemBarsAfterCancelledExit(
                isExitTransitionInProgress = false,
                isActuallyLeaving = false,
                isScreenActive = true,
            )
        )
        assertTrue(
            !shouldReactivateVideoDetailSystemBarsAfterCancelledExit(
                isExitTransitionInProgress = false,
                isActuallyLeaving = true,
                isScreenActive = false,
            )
        )
    }

    @Test
    fun reapplyPolicy_whenReturningFromBackPreviewToTopDetail() {
        assertTrue(
            shouldReapplyVideoDetailSystemBarsAfterBecomingTop(
                wasKeepLoadedContentForBackPreview = true,
                keepLoadedContentForBackPreview = false,
                isActuallyLeaving = false,
            )
        )
        assertTrue(
            !shouldReapplyVideoDetailSystemBarsAfterBecomingTop(
                wasKeepLoadedContentForBackPreview = false,
                keepLoadedContentForBackPreview = false,
                isActuallyLeaving = false,
            )
        )
        assertTrue(
            !shouldReapplyVideoDetailSystemBarsAfterBecomingTop(
                wasKeepLoadedContentForBackPreview = true,
                keepLoadedContentForBackPreview = false,
                isActuallyLeaving = true,
            )
        )
    }
}
