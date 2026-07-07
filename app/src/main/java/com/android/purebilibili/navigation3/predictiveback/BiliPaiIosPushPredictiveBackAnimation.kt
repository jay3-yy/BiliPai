package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import com.android.purebilibili.core.ui.motion.SETTINGS_IOS_PUSH_PARALLAX_FACTOR
import com.android.purebilibili.core.ui.motion.resolveSettingsIosPushPopContentTransform
import com.android.purebilibili.navigation3.BiliPaiNavKey
import kotlinx.coroutines.CoroutineScope

internal class BiliPaiIosPushPredictiveBackAnimation(
    private val exitDirection: BiliPaiPredictiveBackExitDirection =
        BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT,
) : BiliPaiPredictiveBackAnimationHandler {
    private val exitingPageKey = mutableStateOf<String?>(null)
    private val exitAnimatable = Animatable(0f)
    private var inPredictiveBackAnimation = false

    override suspend fun onBackPressed(
        transitionState: NavigationEventTransitionState?,
        currentPageKey: BiliPaiNavKey?,
    ) {
        exitingPageKey.value = currentPageKey.toString()
        if (transitionState is InProgress && inPredictiveBackAnimation) {
            val gestureProgress = transitionState.latestEvent?.progress ?: 0f
            exitAnimatable.snapTo(gestureProgress)
            exitAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
            exitAnimatable.snapTo(0f)
        }
    }

    override fun onPagePop(contentPageKey: Any, animationScope: CoroutineScope) {
        if (exitingPageKey.value == contentPageKey) {
            exitingPageKey.value = null
        }
    }

    @Composable
    override fun Modifier.predictiveBackAnimationDecorator(
        transitionState: NavigationEventTransitionState?,
        contentPageKey: Any,
        currentPageKey: BiliPaiNavKey?,
    ): Modifier {
        val windowInfo = LocalWindowInfo.current
        val containerWidthPx = windowInfo.containerSize.width.toFloat()
        val pageKey = contentPageKey.toString()
        val progressInProgress = transitionState as? InProgress
        val edge = progressInProgress?.latestEvent?.swipeEdge ?: 0
        val gestureProgress = progressInProgress?.latestEvent?.progress ?: 0f
        val directionMultiplier = when (exitDirection) {
            BiliPaiPredictiveBackExitDirection.FOLLOW_GESTURE ->
                if (edge == EDGE_LEFT) 1f else -1f
            BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT -> 1f
            BiliPaiPredictiveBackExitDirection.ALWAYS_LEFT -> -1f
        }
        val isExitingPage = exitingPageKey.value != null && exitingPageKey.value == pageKey
        val isCurrentNavTarget = exitingPageKey.value == null && pageKey == currentPageKey.toString()
        val isUnderlayPage = !isExitingPage && !isCurrentNavTarget
        val isCurrentlyPredictive = transitionState is InProgress || exitingPageKey.value != null

        if (pageKey == currentPageKey.toString() || exitingPageKey.value == pageKey || isUnderlayPage) {
            SideEffect {
                inPredictiveBackAnimation = isCurrentlyPredictive
            }
        }

        if (!isCurrentlyPredictive && exitingPageKey.value == null) {
            return this
        }

        val exitProgress = if (isExitingPage) exitAnimatable.value else gestureProgress
        val activeProgress = when {
            isExitingPage -> exitProgress
            isCurrentNavTarget -> gestureProgress
            else -> gestureProgress
        }

        return this.graphicsLayer {
            when {
                isExitingPage || isCurrentNavTarget -> {
                    translationX = containerWidthPx * activeProgress * directionMultiplier
                }
                isUnderlayPage && (transitionState is InProgress || exitingPageKey.value != null) -> {
                    val parallaxShift = containerWidthPx * SETTINGS_IOS_PUSH_PARALLAX_FACTOR
                    translationX = -parallaxShift * directionMultiplier * (1f - activeProgress)
                }
            }
        }
    }

    override fun AnimatedContentTransitionScope<Scene<BiliPaiNavKey>>.onPredictivePopTransitionSpec(
        swipeEdge: Int,
    ): ContentTransform = ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = ExitTransition.None,
        sizeTransform = null,
    )

    override fun AnimatedContentTransitionScope<Scene<BiliPaiNavKey>>.onPopTransitionSpec(): ContentTransform =
        resolveSettingsIosPushPopContentTransform()

    override fun AnimatedContentTransitionScope<Scene<BiliPaiNavKey>>.onTransitionSpec(): ContentTransform =
        defaultTransitionSpec<BiliPaiNavKey>().invoke(this)
}
