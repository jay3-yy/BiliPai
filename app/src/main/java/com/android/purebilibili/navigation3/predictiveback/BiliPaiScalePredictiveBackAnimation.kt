package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultTransitionSpec

import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import com.android.purebilibili.core.ui.util.rememberDeviceCornerShape
import com.android.purebilibili.navigation3.BiliPaiNavKey
import kotlinx.coroutines.CoroutineScope

internal class BiliPaiScalePredictiveBackAnimation(
    private val exitDirection: BiliPaiPredictiveBackExitDirection = BiliPaiPredictiveBackExitDirection.FOLLOW_GESTURE,
) : BiliPaiPredictiveBackAnimationHandler {
    private val exitingPageKey = mutableStateOf<String?>(null)
    private val exitAnimatable = Animatable(0f)
    private var inPredictiveBackAnimation = false

    override suspend fun onBackPressed(
        transitionState: NavigationEventTransitionState?,
        currentPageKey: BiliPaiNavKey?,
    ) {
        if (transitionState is InProgress) {
            exitingPageKey.value = currentPageKey.toString()
            if (inPredictiveBackAnimation) {
                val gestureProgress = transitionState.latestEvent?.progress ?: 0f
                // 从手势最后位置 snap 开始退出平移，避免手势中只有缩放、松手后平移从 0 突破的不连续感。
                exitAnimatable.snapTo(gestureProgress)
                exitAnimatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                )
                exitAnimatable.snapTo(0f)
            }
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
        val containerHeightPx = windowInfo.containerSize.height
        val containerWidthPx = windowInfo.containerSize.width.toFloat()
        val pageKey = contentPageKey.toString()
        val deviceCornerShape = rememberDeviceCornerShape()

        return if (pageKey == currentPageKey.toString() || exitingPageKey.value == pageKey) {
            val isCurrentlyPredictive = transitionState is InProgress || exitingPageKey.value != null
            SideEffect {
                inPredictiveBackAnimation = isCurrentlyPredictive
            }

            val progressInProgress = transitionState as? InProgress
            val edge = progressInProgress?.latestEvent?.swipeEdge ?: 0
            val touchY = progressInProgress?.latestEvent?.touchY
            val currentPivotY = if (touchY != null && containerHeightPx > 0) {
                (touchY / containerHeightPx).coerceIn(0.1f, 0.9f)
            } else {
                0.5f
            }
            val currentPivotX = if (edge == EDGE_LEFT) 0.8f else 0.2f
            val directionMultiplier = when (exitDirection) {
                BiliPaiPredictiveBackExitDirection.FOLLOW_GESTURE ->
                    if (edge == EDGE_LEFT) 1f else -1f
                BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT -> 1f
                BiliPaiPredictiveBackExitDirection.ALWAYS_LEFT -> -1f
            }
            val exitProgress = if (pageKey != currentPageKey.toString()) 1f else exitAnimatable.value
            val animatedTranslationX = containerWidthPx * exitProgress * directionMultiplier
            val needsClip = isCurrentlyPredictive || exitingPageKey.value != null

            this
                .graphicsLayer {
                    translationX = animatedTranslationX
                    transformOrigin = TransformOrigin(currentPivotX, currentPivotY)
                }
                .clip(
                    if (needsClip) deviceCornerShape
                    else RoundedCornerShape(0.dp),
                )
        } else {
            this
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
        ContentTransform(
            targetContentEnter = slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn(),
            initialContentExit = scaleOut(targetScale = 0.9f) + fadeOut(),
            sizeTransform = null,
        )

    override fun AnimatedContentTransitionScope<Scene<BiliPaiNavKey>>.onTransitionSpec(): ContentTransform =
        defaultTransitionSpec<BiliPaiNavKey>().invoke(this)
}
