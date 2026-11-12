package com.android.purebilibili.core.util

import android.content.pm.ActivityInfo

/**
 * Shared player presentation for ordinary video, bangumi, live, offline, and the
 * standalone player activity.
 *
 * Window size classes decide layout. Foldable display role decides cover-orientation
 * safety. Fullscreen is split into user intent, orientation-generated state, and the
 * final in-window presentation so a fold/unfold never promotes an auto landscape
 * fullscreen into a sticky user request.
 */
data class PlayerPresentationPolicy(
    val userFullscreenIntent: Boolean,
    val orientationGeneratedFullscreen: Boolean,
    val usesInWindowFullscreen: Boolean,
    val isOrientationDriven: Boolean,
    val isFullscreen: Boolean,
    val shouldHandleBackAsExitFullscreen: Boolean,
    val shouldSuppressAxisOrientationRequest: Boolean,
    val foldableDisplayRole: AppFoldableDisplayRole,
)

internal fun shouldUsePhonePlayerOrientation(
    displayContext: AppDisplayContext,
): Boolean {
    if (displayContext.usesInWindowFullscreen) return false
    return when (displayContext.foldableDisplayRole) {
        AppFoldableDisplayRole.Inner,
        AppFoldableDisplayRole.UnknownFoldable -> false
        AppFoldableDisplayRole.Cover -> true
        AppFoldableDisplayRole.Standard -> {
            // 方向策略按稳定设备宽度识别平板，不随系统栏、分屏或入场窗口尺寸波动。
            if (displayContext.smallestScreenWidthDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP) return false
            val maxShortSideDp = if (
                displayContext.maximumWindowWidthDp != null &&
                displayContext.maximumWindowHeightDp != null
            ) {
                minOf(
                    displayContext.maximumWindowWidthDp,
                    displayContext.maximumWindowHeightDp,
                )
            } else {
                minOf(
                    displayContext.currentWindowWidthDp,
                    displayContext.currentWindowHeightDp,
                )
            }
            maxShortSideDp < LARGE_SCREEN_SMALLEST_WIDTH_DP
        }
    }
}

internal fun resolvePlayerPresentationPolicy(
    displayContext: AppDisplayContext,
    isLandscape: Boolean,
    userFullscreenIntent: Boolean,
    prefersManualFullscreen: Boolean,
    isInMultiWindowMode: Boolean,
): PlayerPresentationPolicy {
    val usesInWindowFullscreen = displayContext.usesInWindowFullscreen
    val isOrientationDriven = !prefersManualFullscreen &&
        !usesInWindowFullscreen &&
        shouldUsePhonePlayerOrientation(displayContext)
    val isFullscreen = if (!isOrientationDriven) {
        userFullscreenIntent
    } else {
        isLandscape || (isInMultiWindowMode && userFullscreenIntent)
    }
    val orientationGeneratedFullscreen = isOrientationDriven &&
        isLandscape &&
        !userFullscreenIntent
    return PlayerPresentationPolicy(
        userFullscreenIntent = userFullscreenIntent,
        orientationGeneratedFullscreen = orientationGeneratedFullscreen,
        usesInWindowFullscreen = usesInWindowFullscreen,
        isOrientationDriven = isOrientationDriven,
        isFullscreen = isFullscreen,
        shouldHandleBackAsExitFullscreen = isFullscreen,
        shouldSuppressAxisOrientationRequest = usesInWindowFullscreen,
        foldableDisplayRole = displayContext.foldableDisplayRole,
    )
}

internal fun resolveUserFullscreenIntentAfterDisplayRoleChange(
    previousRole: AppFoldableDisplayRole?,
    nextRole: AppFoldableDisplayRole,
    previousUserFullscreenIntent: Boolean,
): Boolean {
    if (previousRole == null || previousRole == nextRole) {
        return previousUserFullscreenIntent
    }
    return previousUserFullscreenIntent
}

internal fun shouldReleaseOrientationLockOnDisplayRoleChange(
    previousRole: AppFoldableDisplayRole?,
    nextRole: AppFoldableDisplayRole,
): Boolean = previousRole != null && previousRole != nextRole

internal fun resolvePlayerBackConsumption(
    isFullscreen: Boolean,
): Boolean = isFullscreen

internal fun resolvePlayerAxisOrientationRequest(
    requestedOrientation: Int,
    usesInWindowFullscreen: Boolean,
): Int = resolveEffectivePlayerRequestedOrientation(
    requestedOrientation = requestedOrientation,
    usesInWindowFullscreen = usesInWindowFullscreen,
)

internal fun isPlayerAxisOrientationRequestValue(requestedOrientation: Int): Boolean {
    return requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
        requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LOCKED &&
        requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_BEHIND
}
