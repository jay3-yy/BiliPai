package com.android.purebilibili.navigation3

import com.android.purebilibili.feature.settings.isSettingsNavPopTransition
import com.android.purebilibili.navigation.AppSystemBackAction
import com.android.purebilibili.navigation.shouldInterceptSystemBackForAppAction

internal enum class BiliPaiNavMotionMode {
    CARD_DISABLED,
    CLASSIC_CARD
}

internal enum class BiliPaiNavRouteTransition {
    NO_OP_SHARED_ELEMENT,
    CARD_DISABLED_VIDEO_FORWARD_FROM_LEFT,
    CARD_DISABLED_VIDEO_FORWARD_FROM_RIGHT,
    CARD_DISABLED_VIDEO_RETURN_TO_LEFT,
    CARD_DISABLED_VIDEO_RETURN_TO_RIGHT,
    SPACE_FORWARD,
    LIGHT_SIBLING_FORWARD,
    LIGHT_SIBLING_POP,
    BOTTOM_BAR_SIBLING_FORWARD,
    BOTTOM_BAR_SIBLING_POP,
    SETTINGS_IOS_PUSH_FORWARD,
    SETTINGS_IOS_PUSH_POP,
    CLASSIC_CARD,
    FALLBACK
}

internal data class BiliPaiNavMotionDecision(
    val mode: BiliPaiNavMotionMode,
    val routeTransition: BiliPaiNavRouteTransition,
    val interceptSystemBack: Boolean
)

internal data class BiliPaiBackGestureDecision(
    val routeTransition: BiliPaiNavRouteTransition,
    val interceptSystemBack: Boolean
)

internal fun resolveBiliPaiNavMotionMode(
    cardTransitionEnabled: Boolean
): BiliPaiNavMotionMode {
    return if (cardTransitionEnabled) {
        BiliPaiNavMotionMode.CLASSIC_CARD
    } else {
        BiliPaiNavMotionMode.CARD_DISABLED
    }
}

internal fun resolveBiliPaiNavMotionDecision(
    fromKey: BiliPaiNavKey?,
    toKey: BiliPaiNavKey?,
    cardTransitionEnabled: Boolean,
    sharedTransitionReady: Boolean,
    appBackActionRequiresInterception: Boolean = false
): BiliPaiNavMotionDecision {
    val mode = resolveBiliPaiNavMotionMode(cardTransitionEnabled = cardTransitionEnabled)
    val isVideoToCardReturn = fromKey is BiliPaiNavKey.VideoDetail &&
        toKey != null &&
        isCardReturnTargetNavKey(toKey)
    val isCardToVideoForward = fromKey != null &&
        isCardReturnTargetNavKey(fromKey) &&
        toKey is BiliPaiNavKey.VideoDetail
    val routeTransition = when {
        cardTransitionEnabled &&
            sharedTransitionReady &&
            (isVideoToCardReturn || isCardToVideoForward) ->
            BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT
        mode == BiliPaiNavMotionMode.CLASSIC_CARD ->
            BiliPaiNavRouteTransition.CLASSIC_CARD
        else -> BiliPaiNavRouteTransition.FALLBACK
    }

    return BiliPaiNavMotionDecision(
        mode = mode,
        routeTransition = routeTransition,
        interceptSystemBack = shouldInterceptSystemBackForNavigation3(
            mode = mode,
            appBackActionRequiresInterception = appBackActionRequiresInterception
        )
    )
}

internal fun resolveBiliPaiBackGestureDecision(
    cardTransitionEnabled: Boolean,
    systemBackAction: AppSystemBackAction,
    currentKey: BiliPaiNavKey?,
    previousKey: BiliPaiNavKey?,
    sourceMetadata: BiliPaiNavSourceMetadata
): BiliPaiBackGestureDecision {
    val motionMode = resolveBiliPaiNavMotionMode(cardTransitionEnabled = cardTransitionEnabled)
    val routeTransition = resolveBiliPaiNavDisplayPopRouteTransition(
        cardTransitionEnabled = cardTransitionEnabled,
        sourceMetadata = sourceMetadata,
        fromKey = currentKey,
        toKey = previousKey
    )
    val isAppAction = systemBackAction == AppSystemBackAction.RETURN_TO_HOME_TAB
    return BiliPaiBackGestureDecision(
        routeTransition = if (isAppAction) {
            BiliPaiNavRouteTransition.FALLBACK
        } else {
            routeTransition
        },
        interceptSystemBack = shouldInterceptSystemBackForAppAction(systemBackAction)
    )
}

/**
 * 解析 [BiliPaiNavDisplayHost] 全局 `popTransitionSpec` / `predictivePopTransitionSpec` 使用的过渡。
 *
 * 实际生效场景：
 *   - **预测式返回手势**（Android 13+ swipe-back）：entry metadata 不注入 PREDICTIVE_POP_TRANSITION_SPEC，
 *     所以此函数的输出是唯一来源；
 *   - 普通 pop：entry metadata 会注入 POP_TRANSITION_SPEC 并优先生效（见
 *     [resolveBiliPaiNavEntryPopRouteTransition]），此函数仅作兜底。
 *
 * 两条路径需要保持视觉一致——任何对此函数的逻辑修改都应同步检查
 * [resolveBiliPaiNavEntryPopRouteTransition]，反之亦然。
 */
internal fun resolveBiliPaiNavDisplayPopRouteTransition(
    cardTransitionEnabled: Boolean = true,
    sourceMetadata: BiliPaiNavSourceMetadata,
    fromKey: BiliPaiNavKey?,
    toKey: BiliPaiNavKey?
): BiliPaiNavRouteTransition {
    val fromVideoKey = fromKey as? BiliPaiNavKey.VideoDetail
    val toIsCardReturnTarget = toKey != null && isCardReturnTargetNavKey(toKey)
    if (cardTransitionEnabled) {
        val sharedReadyFavoriteCollectionReturn =
            fromKey is BiliPaiNavKey.SeasonSeriesDetail &&
                fromKey.sharedElementTransition &&
                (toKey == BiliPaiNavKey.MainHost || toKey == BiliPaiNavKey.Favorite)
        if (sharedReadyFavoriteCollectionReturn) {
            return BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT
        }

        val normalizedSourceRoute = sourceMetadata.sourceRoute?.substringBefore("?")
        val normalizedVideoRoute = fromVideoKey?.sourceRoute?.substringBefore("?")
        val sourceMatchesCurrentVideo = fromVideoKey != null &&
            normalizedSourceRoute != null &&
            normalizedVideoRoute == normalizedSourceRoute &&
            sourceMetadata.sourceKey == "$normalizedSourceRoute:${fromVideoKey.bvid}"
        val sharedReadyVideoToSourceCard = sourceMetadata.sharedTransitionEntryReady &&
            sourceMatchesCurrentVideo &&
            toIsCardReturnTarget
        if (sharedReadyVideoToSourceCard) {
            return BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT
        }
        if (isSettingsNavPopTransition(fromKey = fromKey, toKey = toKey)) {
            return BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP
        }
        return BiliPaiNavRouteTransition.CLASSIC_CARD
    }
    // 关闭共享元素时：VideoDetail → 任意 card-return-target 一律走方向化横向过渡，
    // 没有源方向信息（单列、居中、未点击源、卡片已滚出视口等）时兜底向右滑出。
    if (fromVideoKey != null && toIsCardReturnTarget) {
        return resolveCardDisabledReturnTransition(sourceMetadata.cardSourceDirection)
    }
    return BiliPaiNavRouteTransition.FALLBACK
}

internal fun shouldInterceptSystemBackForNavigation3(
    mode: BiliPaiNavMotionMode,
    appBackActionRequiresInterception: Boolean
): Boolean {
    return appBackActionRequiresInterception
}

internal fun resolveCardDisabledReturnTransition(
    sourceDirection: BiliPaiNavCardSourceDirection
): BiliPaiNavRouteTransition {
    return when (sourceDirection) {
        BiliPaiNavCardSourceDirection.SOURCE_LEFT ->
            BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_RETURN_TO_LEFT
        BiliPaiNavCardSourceDirection.SOURCE_RIGHT,
        BiliPaiNavCardSourceDirection.NONE ->
            BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_RETURN_TO_RIGHT
    }
}
