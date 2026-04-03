package com.android.purebilibili.navigation

import androidx.lifecycle.Lifecycle
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionProfile
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionProfile

internal enum class VideoPopExitDirection {
    LEFT,
    RIGHT,
    DOWN
}

internal enum class BackRouteMotionMode {
    CARD_DISABLED,
    CLASSIC_CARD,
    PREDICTIVE_STABLE
}

internal enum class VideoCardReturnEnterAction {
    NO_OP,
    RIGHT_SLIDE,
    SOFT_FADE,
    SEAMLESS_FADE
}

internal enum class VideoPopExitAction {
    NO_OP,
    RIGHT_SLIDE,
    DIRECTIONAL_SLIDE,
    SOFT_FADE,
    SEAMLESS_FADE
}

internal enum class VideoPushEnterAction {
    NO_OP,
    HERO_EXPAND_FADE,
    SOFT_FADE,
    LEFT_SLIDE
}

internal data class VideoPopExitDecision(
    val action: VideoPopExitAction,
    val direction: VideoPopExitDirection? = null
)

internal fun resolveVideoPushEnterAction(
    cardTransitionEnabled: Boolean,
    predictiveBackAnimationEnabled: Boolean,
    fromRoute: String?,
    toRoute: String?,
    sharedTransitionReady: Boolean
): VideoPushEnterAction {
    if (
        shouldUseNoOpRouteTransitionBetweenVideoDetails(
            cardTransitionEnabled = cardTransitionEnabled,
            fromRoute = fromRoute,
            toRoute = toRoute
        )
    ) {
        return VideoPushEnterAction.NO_OP
    }

    val backRouteMotionMode = resolveBackRouteMotionMode(
        predictiveBackAnimationEnabled = predictiveBackAnimationEnabled,
        cardTransitionEnabled = cardTransitionEnabled
    )

    if (shouldUseClassicBackRouteMotion(backRouteMotionMode)) {
        return if (sharedTransitionReady) {
            VideoPushEnterAction.HERO_EXPAND_FADE
        } else {
            VideoPushEnterAction.SOFT_FADE
        }
    }

    return VideoPushEnterAction.LEFT_SLIDE
}

internal fun resolveVideoCardReturnEnterAction(
    fromRoute: String?,
    targetRoute: String?,
    cardTransitionEnabled: Boolean,
    predictiveBackAnimationEnabled: Boolean,
    isQuickReturnFromDetail: Boolean,
    sharedTransitionReady: Boolean,
    isTabletLayout: Boolean,
    allowNoOpSharedElement: Boolean,
    noCardTransitionAction: VideoCardReturnEnterAction = VideoCardReturnEnterAction.RIGHT_SLIDE
): VideoCardReturnEnterAction? {
    if (!isVideoDetailRoute(fromRoute)) return null

    val backRouteMotionMode = resolveBackRouteMotionMode(
        predictiveBackAnimationEnabled = predictiveBackAnimationEnabled,
        cardTransitionEnabled = cardTransitionEnabled
    )

    if (
        allowNoOpSharedElement &&
        shouldUseNoOpSharedElementRouteTransition(
            cardTransitionEnabled = cardTransitionEnabled,
            sharedTransitionReady = sharedTransitionReady,
            predictiveBackAnimationEnabled = predictiveBackAnimationEnabled
        )
    ) {
        return VideoCardReturnEnterAction.NO_OP
    }

    if (backRouteMotionMode == BackRouteMotionMode.CARD_DISABLED) {
        return noCardTransitionAction
    }

    if (shouldUsePredictiveStableBackRouteMotion(backRouteMotionMode)) {
        return VideoCardReturnEnterAction.NO_OP
    }

    if (
        shouldUseTabletSeamlessBackTransition(
            isTabletLayout = isTabletLayout,
            cardTransitionEnabled = cardTransitionEnabled,
            fromRoute = fromRoute,
            toRoute = targetRoute
        )
    ) {
        return VideoCardReturnEnterAction.SEAMLESS_FADE
    }

    val targetIsCardReturnTarget = isVideoCardReturnTargetRoute(targetRoute)
    if (
        shouldUseClassicBackRouteMotion(backRouteMotionMode) &&
        targetIsCardReturnTarget &&
        isQuickReturnFromDetail
    ) {
        return if (
            shouldUseNoOpRouteTransitionOnQuickReturn(
                cardTransitionEnabled = cardTransitionEnabled,
                isQuickReturnFromDetail = isQuickReturnFromDetail,
                sharedTransitionReady = sharedTransitionReady
            )
        ) {
            VideoCardReturnEnterAction.NO_OP
        } else {
            VideoCardReturnEnterAction.SOFT_FADE
        }
    }

    if (shouldUseClassicBackRouteMotion(backRouteMotionMode)) {
        return VideoCardReturnEnterAction.RIGHT_SLIDE
    }

    return VideoCardReturnEnterAction.SOFT_FADE
}

internal fun resolveVideoPopExitAction(
    cardTransitionEnabled: Boolean,
    predictiveBackAnimationEnabled: Boolean,
    isTabletLayout: Boolean,
    fromRoute: String?,
    targetRoute: String?,
    isQuickReturnFromDetail: Boolean,
    sharedTransitionReady: Boolean,
    isSingleColumnCard: Boolean,
    lastClickedCardCenterX: Float?,
    profile: VideoSharedTransitionProfile = resolveVideoSharedTransitionProfile()
): VideoPopExitDecision {
    val backRouteMotionMode = resolveBackRouteMotionMode(
        predictiveBackAnimationEnabled = predictiveBackAnimationEnabled,
        cardTransitionEnabled = cardTransitionEnabled
    )

    if (
        shouldUseNoOpRouteTransitionBetweenVideoDetails(
            cardTransitionEnabled = cardTransitionEnabled,
            fromRoute = fromRoute,
            toRoute = targetRoute
        )
    ) {
        return VideoPopExitDecision(action = VideoPopExitAction.NO_OP)
    }

    val targetIsCardReturnTarget = isVideoCardReturnTargetRoute(targetRoute)

    if (
        targetIsCardReturnTarget &&
        shouldUsePredictiveStableBackRouteMotion(backRouteMotionMode)
    ) {
        return VideoPopExitDecision(action = VideoPopExitAction.NO_OP)
    }

    if (
        shouldUseTabletSeamlessBackTransition(
            isTabletLayout = isTabletLayout,
            cardTransitionEnabled = cardTransitionEnabled,
            fromRoute = fromRoute,
            toRoute = targetRoute
        )
    ) {
        return VideoPopExitDecision(action = VideoPopExitAction.SEAMLESS_FADE)
    }

    if (
        targetIsCardReturnTarget &&
        shouldUseClassicBackRouteMotion(backRouteMotionMode)
    ) {
        return VideoPopExitDecision(
            action = VideoPopExitAction.DIRECTIONAL_SLIDE,
            direction = resolveVideoPopExitDirection(
                targetRoute = targetRoute,
                isSingleColumnCard = isSingleColumnCard,
                lastClickedCardCenterX = lastClickedCardCenterX
            )
        )
    }

    if (shouldUseClassicBackRouteMotion(backRouteMotionMode) && isQuickReturnFromDetail) {
        return if (
            shouldUseNoOpRouteTransitionOnQuickReturn(
                cardTransitionEnabled = cardTransitionEnabled,
                isQuickReturnFromDetail = isQuickReturnFromDetail,
                sharedTransitionReady = sharedTransitionReady,
                profile = profile
            )
        ) {
            VideoPopExitDecision(action = VideoPopExitAction.NO_OP)
        } else {
            VideoPopExitDecision(action = VideoPopExitAction.SOFT_FADE)
        }
    }

    if (cardTransitionEnabled) {
        return VideoPopExitDecision(action = VideoPopExitAction.NO_OP)
    }

    return VideoPopExitDecision(
        action = VideoPopExitAction.DIRECTIONAL_SLIDE,
        direction = resolveVideoPopExitDirection(
            targetRoute = targetRoute,
            isSingleColumnCard = isSingleColumnCard,
            lastClickedCardCenterX = lastClickedCardCenterX
        )
    )
}

internal fun shouldUseNoOpRouteTransitionOnQuickReturn(
    cardTransitionEnabled: Boolean,
    isQuickReturnFromDetail: Boolean,
    sharedTransitionReady: Boolean,
    profile: VideoSharedTransitionProfile = resolveVideoSharedTransitionProfile()
): Boolean {
    if (!cardTransitionEnabled || !isQuickReturnFromDetail) return false
    return when (profile) {
        VideoSharedTransitionProfile.COVER_ONLY -> sharedTransitionReady
        VideoSharedTransitionProfile.COVER_AND_METADATA -> true
    }
}

internal fun shouldUseNoOpRouteTransitionBetweenVideoDetails(
    cardTransitionEnabled: Boolean,
    fromRoute: String?,
    toRoute: String?
): Boolean {
    return cardTransitionEnabled &&
        isVideoDetailRoute(fromRoute) &&
        isVideoDetailRoute(toRoute)
}

internal fun shouldUseNoOpQuickReturnForNonHomeCardRoute(
    targetRoute: String?,
    cardTransitionEnabled: Boolean,
    isQuickReturnFromDetail: Boolean,
    sharedTransitionReady: Boolean,
    profile: VideoSharedTransitionProfile = resolveVideoSharedTransitionProfile()
): Boolean {
    // Align non-home card return behavior with Home:
    // do not force a dedicated quick-return no-op branch for non-home routes.
    return false
}

internal fun shouldPreferOneTakeVideoToHomeReturn(
    predictiveBackAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean,
    sharedTransitionReady: Boolean
): Boolean {
    if (!predictiveBackAnimationEnabled) return false
    if (!cardTransitionEnabled) return false
    if (!sharedTransitionReady) return false
    // Phase 1 stability fallback:
    // predictive back enabled 时先禁用视频<->首页的一镜到底 route no-op，
    // 避免 Surface/overlay 链路抖动导致黑屏与长时间滞留。
    return false
}

internal fun resolveBackRouteMotionMode(
    predictiveBackAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean
): BackRouteMotionMode {
    if (!cardTransitionEnabled) return BackRouteMotionMode.CARD_DISABLED
    return if (predictiveBackAnimationEnabled) {
        BackRouteMotionMode.PREDICTIVE_STABLE
    } else {
        BackRouteMotionMode.CLASSIC_CARD
    }
}

internal fun shouldUseClassicBackRouteMotion(
    mode: BackRouteMotionMode
): Boolean {
    return mode == BackRouteMotionMode.CLASSIC_CARD
}

internal fun shouldUsePredictiveStableBackRouteMotion(
    mode: BackRouteMotionMode
): Boolean {
    return mode == BackRouteMotionMode.PREDICTIVE_STABLE
}

internal fun shouldUseLinkedSettingsBackMotion(
    mode: BackRouteMotionMode
): Boolean {
    return mode == BackRouteMotionMode.PREDICTIVE_STABLE
}

internal fun shouldDeferBottomBarRevealOnVideoReturn(
    isReturningFromDetail: Boolean,
    currentRoute: String?
): Boolean {
    if (!isReturningFromDetail || currentRoute != ScreenRoutes.Home.route) return false
    // 返回首页时不再延迟底栏显示，避免“先隐藏再出现”造成的闪烁感。
    return false
}

internal fun shouldClearReturningStateWhenDisposingVideoDestination(
    stillInVideoRoute: Boolean
): Boolean {
    return stillInVideoRoute
}

internal fun shouldShareAudioModeViewModelWithPreviousEntry(
    previousRoute: String?,
    previousLifecycleState: Lifecycle.State?
): Boolean {
    return previousLifecycleState?.isAtLeast(Lifecycle.State.CREATED) == true &&
        isVideoDetailRoute(previousRoute)
}

internal fun shouldUseTabletSeamlessBackTransition(
    isTabletLayout: Boolean,
    cardTransitionEnabled: Boolean,
    fromRoute: String?,
    toRoute: String?
): Boolean {
    return isTabletLayout &&
        cardTransitionEnabled &&
        isVideoDetailRoute(fromRoute) &&
        isVideoCardReturnTargetRoute(toRoute)
}

internal fun shouldStopPlaybackEagerlyOnVideoRouteExit(
    fromRoute: String?,
    toRoute: String?
): Boolean {
    if (toRoute.isNullOrBlank()) return false
    return isVideoDetailRoute(fromRoute) &&
        !isVideoDetailRoute(toRoute) &&
        toRoute != ScreenRoutes.AudioMode.route
}

internal fun resolveVideoPopExitDirection(
    targetRoute: String?,
    isSingleColumnCard: Boolean,
    lastClickedCardCenterX: Float?
): VideoPopExitDirection {
    val isCardOnLeft = (lastClickedCardCenterX ?: 0.5f) < 0.5f
    if (isVideoCardReturnTargetRoute(targetRoute)) {
        return if (isCardOnLeft) VideoPopExitDirection.LEFT else VideoPopExitDirection.RIGHT
    }
    if (isSingleColumnCard) return VideoPopExitDirection.DOWN
    return if (isCardOnLeft) VideoPopExitDirection.LEFT else VideoPopExitDirection.RIGHT
}

internal fun isVideoCardReturnTargetRoute(route: String?): Boolean {
    val routeBase = route?.substringBefore("?") ?: return false
    return routeBase == ScreenRoutes.Home.route ||
        routeBase == ScreenRoutes.History.route ||
        routeBase == ScreenRoutes.Favorite.route ||
        routeBase == ScreenRoutes.WatchLater.route ||
        routeBase == ScreenRoutes.Search.route ||
        routeBase == ScreenRoutes.Dynamic.route ||
        routeBase.startsWith("dynamic_detail/") ||
        routeBase == ScreenRoutes.Partition.route ||
        routeBase.startsWith("category/") ||
        routeBase.startsWith("season_series_detail/") ||
        routeBase.startsWith("space/")
}

private fun isVideoDetailRoute(route: String?): Boolean {
    return route?.startsWith("${VideoRoute.base}/") == true
}

/**
 * 统一判断：当共享元素过渡就绪时，路由级转场应使用 NoOp（None），
 * 让 sharedBounds 成为唯一的视觉过渡驱动。
 *
 * 条件：卡片过渡已启用 + 共享元素已就绪 + 非 predictive back 模式。
 */
internal fun shouldUseNoOpSharedElementRouteTransition(
    cardTransitionEnabled: Boolean,
    sharedTransitionReady: Boolean,
    predictiveBackAnimationEnabled: Boolean
): Boolean {
    return cardTransitionEnabled &&
        sharedTransitionReady &&
        !predictiveBackAnimationEnabled
}
