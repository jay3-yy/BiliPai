package com.android.purebilibili.core.ui

import com.android.purebilibili.core.theme.AppUiStyle

fun resolveAdaptivePullToRefreshRenderer(
    uiStyle: AppUiStyle
): PresetPrimitiveRenderer {
    return resolvePresetPrimitiveRenderer(uiStyle)
}

fun resolveMiuixPullToRefreshTexts(): List<String> = listOf(
    "下拉刷新...",
    "松手刷新",
    "正在刷新...",
    "刷新完成",
)

/**
 * Top inset (dp) for the pull-to-refresh indicator relative to the
 * [AdaptivePullToRefreshBox] top edge.
 *
 * Use the **height of chrome that overlays the refresh box** (status bar + floating
 * top bar / home header). When the box is already laid out *below* a Scaffold
 * topBar (body already padded), pass **0**.
 *
 * Do **not** reuse home [listTopPadding] on other screens — each surface has its
 * own chrome stack.
 */
fun resolvePullRefreshIndicatorTopInsetDp(overlayChromeHeightDp: Float): Float {
    return overlayChromeHeightDp.coerceAtLeast(0f)
}

/**
 * Scaffold body already receives topBar padding → indicator sits at the box top.
 */
fun resolveScaffoldedPullRefreshIndicatorTopInsetDp(): Float = 0f

/**
 * Only unmount Miuix's native pull container when disabled, using a non-blocking hint,
 * or genuinely offscreen/paused while actively refreshing. Idle containers never submit
 * frames, so keeping them mounted ensures user pull gestures stay responsive.
 */
fun shouldMountMiuixPullToRefresh(
    enabled: Boolean,
    isRefreshing: Boolean,
    allowContentScrollWhileRefreshing: Boolean,
    reduceMotion: Boolean,
    inViewport: Boolean,
    resumed: Boolean,
): Boolean {
    if (!enabled) return false
    if ((allowContentScrollWhileRefreshing || reduceMotion) && isRefreshing) return false
    if (isRefreshing) return inViewport && resumed
    return true
}

/**
 * Non-blocking text hint replaces the Miuix spinner while refreshing when the box is
 * actually visible on screen.
 */
fun shouldShowMiuixNonBlockingRefreshHint(
    allowContentScrollWhileRefreshing: Boolean,
    reduceMotion: Boolean,
    isRefreshing: Boolean,
    inViewport: Boolean,
    resumed: Boolean,
): Boolean = isRefreshing && (allowContentScrollWhileRefreshing || reduceMotion) && inViewport && resumed

/**
 * Material3 pull indicator must remain visible whenever the user is pulling or the
 * indicator is resting in view, while suppressing offscreen spinning loops.
 */
fun shouldRenderMaterialPullToRefreshIndicator(
    gateActive: Boolean,
    distanceFraction: Float,
    isRefreshing: Boolean,
    inViewport: Boolean,
    resumed: Boolean,
): Boolean {
    if (distanceFraction > 0f) return true
    if (isRefreshing) return gateActive || (inViewport && resumed)
    return gateActive
}
