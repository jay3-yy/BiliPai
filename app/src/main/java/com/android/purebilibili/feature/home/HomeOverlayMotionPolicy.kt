package com.android.purebilibili.feature.home

internal data class HomeOverlayMotionSpec(
    val refreshTipEnterFadeDurationMillis: Int,
    val refreshTipExitFadeDurationMillis: Int,
    val refreshTipSlideDurationMillis: Int,
    val undoFabFadeDurationMillis: Int,
    val undoFabSlideDurationMillis: Int,
    val previewOverlayFadeDurationMillis: Int,
    val previewOverlayScaleDurationMillis: Int,
    val sideNavEnterSlideDurationMillis: Int,
    val sideNavExitSlideDurationMillis: Int,
    val sideNavFadeDurationMillis: Int
)

internal fun resolveHomeOverlayMotionSpec(): HomeOverlayMotionSpec {
    return HomeOverlayMotionSpec(
        refreshTipEnterFadeDurationMillis = 180,
        refreshTipExitFadeDurationMillis = 220,
        refreshTipSlideDurationMillis = 220,
        undoFabFadeDurationMillis = 200,
        undoFabSlideDurationMillis = 250,
        previewOverlayFadeDurationMillis = 200,
        previewOverlayScaleDurationMillis = 200,
        sideNavEnterSlideDurationMillis = 300,
        sideNavExitSlideDurationMillis = 250,
        sideNavFadeDurationMillis = 200
    )
}
