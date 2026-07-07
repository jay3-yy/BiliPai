package com.android.purebilibili.navigation3.predictiveback

import com.android.purebilibili.navigation3.BiliPaiNavRouteTransition

internal fun resolveBiliPaiPredictiveBackAnimationHandler(
    routeTransition: BiliPaiNavRouteTransition,
    predictiveBackEnabled: Boolean = true,
    style: BiliPaiPredictiveBackAnimationStyle = BiliPaiPredictiveBackAnimationStyle.SCALE,
    exitDirection: BiliPaiPredictiveBackExitDirection = BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT,
): BiliPaiPredictiveBackAnimationHandler {
    if (routeTransition == BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT) {
        return BiliPaiSharedElementPredictiveBackAnimation()
    }

    if (!predictiveBackEnabled) {
        return BiliPaiDisabledPredictiveBackAnimation()
    }

    return when (routeTransition) {
        BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP ->
            BiliPaiIosPushPredictiveBackAnimation(exitDirection = exitDirection)
        BiliPaiNavRouteTransition.CLASSIC_CARD -> resolveClassicCardPredictiveBackHandler(
            style = style,
            exitDirection = exitDirection,
        )
        else -> BiliPaiDefaultPredictiveBackAnimation()
    }
}

private fun resolveClassicCardPredictiveBackHandler(
    style: BiliPaiPredictiveBackAnimationStyle,
    exitDirection: BiliPaiPredictiveBackExitDirection,
): BiliPaiPredictiveBackAnimationHandler {
    return when (style) {
        BiliPaiPredictiveBackAnimationStyle.DEFAULT ->
            BiliPaiDefaultPredictiveBackAnimation()
        BiliPaiPredictiveBackAnimationStyle.SCALE ->
            BiliPaiScalePredictiveBackAnimation(exitDirection)
        BiliPaiPredictiveBackAnimationStyle.AOSP ->
            BiliPaiAospCrossActivityPredictiveBackAnimation(exitDirection)
        BiliPaiPredictiveBackAnimationStyle.CLASSIC ->
            BiliPaiClassicPredictiveBackAnimation()
    }
}
