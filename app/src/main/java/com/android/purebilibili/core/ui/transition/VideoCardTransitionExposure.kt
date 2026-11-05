package com.android.purebilibili.core.ui.transition

/**
 * Whether the retained source page is actually exposed by the card transition.
 *
 * This is deliberately separate from [VideoCardTransitionBackgroundPhase]: HELD keeps the
 * return contract alive, but the fully covered source page must not keep blur/composition work
 * alive while the user watches the detail page.
 */
internal enum class VideoCardTransitionExposure {
    Idle,
    Opening,
    SettledHidden,
    BackPreview,
    Returning,
    Restoring,
}

/** 首页与导航宿主保留各自坐标系，仅在可录制的玻璃卡片转场期间复用合成纹理。 */
internal fun shouldRasterizeVideoCardChromeBackdrop(
    liquidGlassEnabled: Boolean,
    cardTransitionEnabled: Boolean,
    reduceMotion: Boolean,
    recordingAllowed: Boolean,
    exposure: VideoCardTransitionExposure,
): Boolean {
    if (!liquidGlassEnabled || !cardTransitionEnabled || reduceMotion || !recordingAllowed) {
        return false
    }
    return when (exposure) {
        VideoCardTransitionExposure.Opening,
        VideoCardTransitionExposure.BackPreview,
        VideoCardTransitionExposure.Returning,
        VideoCardTransitionExposure.Restoring -> true
        VideoCardTransitionExposure.Idle,
        VideoCardTransitionExposure.SettledHidden -> false
    }
}

/** Background feed work resumes when the source is fully restored, including cancellation. */
internal fun shouldDeferVideoCardSourceHeavyWork(
    exposure: VideoCardTransitionExposure,
): Boolean = exposure != VideoCardTransitionExposure.Idle

/** 玻璃背景只在控件或其进出场需要时录制；完全被详情覆盖的底栏不算消费者。 */
internal fun shouldRecordVideoCardFloatingBackdrop(
    liquidGlassEnabled: Boolean,
    isVideoDetailDestination: Boolean,
    exposure: VideoCardTransitionExposure,
    hasBottomBarConsumer: Boolean,
    hasAudioBarConsumer: Boolean,
    hasFloatingConsumer: Boolean,
): Boolean = !liquidGlassEnabled || hasAudioBarConsumer || hasFloatingConsumer ||
    (hasBottomBarConsumer && (!isVideoDetailDestination ||
        exposure != VideoCardTransitionExposure.SettledHidden))

internal data class VideoCardTransitionRenderDecision(
    val retainSourceSnapshot: Boolean,
    val drawSourceNormally: Boolean,
    val drawTransitionBackground: Boolean,
    val updateBlurEffect: Boolean,
    val drawNavBackdrop: Boolean,
)

internal fun resolveVideoCardTransitionExposure(
    phase: VideoCardTransitionBackgroundPhase,
    predictiveBackInProgress: Boolean,
    gestureRestoreInProgress: Boolean,
): VideoCardTransitionExposure {
    return when (phase) {
        VideoCardTransitionBackgroundPhase.IDLE -> VideoCardTransitionExposure.Idle
        VideoCardTransitionBackgroundPhase.OPENING -> VideoCardTransitionExposure.Opening
        VideoCardTransitionBackgroundPhase.RETURNING -> VideoCardTransitionExposure.Returning
        VideoCardTransitionBackgroundPhase.HELD -> when {
            predictiveBackInProgress -> VideoCardTransitionExposure.BackPreview
            gestureRestoreInProgress -> VideoCardTransitionExposure.Restoring
            else -> VideoCardTransitionExposure.SettledHidden
        }
    }
}

internal fun resolveVideoCardTransitionRenderDecision(
    exposure: VideoCardTransitionExposure,
): VideoCardTransitionRenderDecision {
    return when (exposure) {
        VideoCardTransitionExposure.Idle -> VideoCardTransitionRenderDecision(
            retainSourceSnapshot = false,
            drawSourceNormally = true,
            drawTransitionBackground = false,
            updateBlurEffect = false,
            drawNavBackdrop = false,
        )
        VideoCardTransitionExposure.SettledHidden -> VideoCardTransitionRenderDecision(
            retainSourceSnapshot = true,
            // Keep false so we do not full-recompose the feed under a covering detail page.
            // Draw path still paints the frozen layer (or live fallback) when this route is
            // the sole composed scene — see [shouldPaintRetainedSourceWithoutTransitionBackground].
            drawSourceNormally = false,
            drawTransitionBackground = false,
            updateBlurEffect = false,
            drawNavBackdrop = false,
        )
        VideoCardTransitionExposure.Restoring -> VideoCardTransitionRenderDecision(
            retainSourceSnapshot = true,
            drawSourceNormally = false,
            drawTransitionBackground = true,
            updateBlurEffect = true,
            drawNavBackdrop = false,
        )
        VideoCardTransitionExposure.Opening,
        VideoCardTransitionExposure.BackPreview,
        VideoCardTransitionExposure.Returning -> VideoCardTransitionRenderDecision(
            retainSourceSnapshot = true,
            drawSourceNormally = false,
            drawTransitionBackground = true,
            updateBlurEffect = true,
            drawNavBackdrop = true,
        )
    }
}

/**
 * Nav3 1.2 + [EnterTransition.None]/[ExitTransition.None] can settle the source as the only
 * composed scene while the card clock is still [VideoCardTransitionBackgroundPhase.HELD]
 * (SettledHidden). Drawing nothing then yields a pure black frame under the shared-element
 * overlay. Paint retained snapshot / live content instead of a black hole.
 */
internal fun shouldPaintRetainedSourceWithoutTransitionBackground(
    decision: VideoCardTransitionRenderDecision,
): Boolean {
    if (decision.drawTransitionBackground || decision.drawSourceNormally) return false
    return decision.retainSourceSnapshot
}

/**
 * 全局底栏在卡片过渡中的显隐。
 *
 * 详情在栈顶时默认隐藏；预测返回 / 提交返回时滑入，取消手势再滑出。
 * 来源不是底栏一级入口（相关推荐、空间、历史等）时，不得把首页底栏带进这次交接。
 */
internal fun shouldShowVideoCardTransitionSourceChrome(
    isVideoDetailDestination: Boolean,
    exposure: VideoCardTransitionExposure,
    sourceIsBottomBarDestination: Boolean = true,
): Boolean {
    if (!isVideoDetailDestination) {
        return exposure != VideoCardTransitionExposure.Opening
    }
    if (!sourceIsBottomBarDestination) return false
    return when (exposure) {
        VideoCardTransitionExposure.BackPreview,
        VideoCardTransitionExposure.Returning -> true
        else -> false
    }
}

/**
 * 首页顶栏 overlay 的显隐。首页本身不是详情页，只看景深曝光。
 */
internal fun shouldShowHomeOverlayChromeDuringVideoCardTransition(
    exposure: VideoCardTransitionExposure,
): Boolean {
    return when (exposure) {
        VideoCardTransitionExposure.Opening,
        VideoCardTransitionExposure.SettledHidden,
        VideoCardTransitionExposure.Restoring -> false
        else -> true
    }
}

/**
 * 详情在栈顶时 [activeBottomTabRoute] 是 video/…，不在底栏目的地里。
 * 预测返回要滑出底栏时，改用进详情前的 tab 路由。
 */
internal fun resolveVideoCardTransitionChromeBottomBarRoute(
    isVideoDetailDestination: Boolean,
    activeBottomTabRoute: String?,
    retainedTabRoute: String?,
): String? {
    return if (isVideoDetailDestination) retainedTabRoute else activeBottomTabRoute
}

/** depth 1 = 详情满屏，chrome 藏；depth 0 = 首页，chrome 满显。跟手可打断。 */
internal fun resolveVideoCardTransitionChromeReveal(depthProgress: Float): Float {
    return 1f - depthProgress.coerceIn(0f, 1f)
}

internal fun shouldDriveVideoCardTransitionChromeByProgress(
    cardTransitionEnabled: Boolean,
    exposure: VideoCardTransitionExposure,
    sourceIsBottomBarDestination: Boolean = true,
): Boolean = cardTransitionEnabled &&
    sourceIsBottomBarDestination &&
    exposure != VideoCardTransitionExposure.Idle

/**
 * 只有从真正带底栏的一级入口进详情时，才让底栏跟着卡片景深滑入滑出。
 * `video/…` 相关推荐、空间、分区等来源会把首页底栏带到无关界面上。
 */
internal fun isVideoCardTransitionBottomBarSource(
    sourceRoute: String?,
    visibleBottomBarRoutes: Set<String>,
): Boolean {
    val route = sourceRoute?.substringBefore("?")?.trim().orEmpty()
    if (route.isEmpty() || route.startsWith("video/")) return false
    if (route == "main_host") return true
    return route in visibleBottomBarRoutes
}
