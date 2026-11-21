package com.android.purebilibili.feature.video.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.transition.LocalVideoCardMorphProgressReporter
import com.android.purebilibili.core.ui.transition.LocalMiuixVideoCardTransitionState
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionBackgroundState
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionClock
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.shouldEnableVideoCoverSharedTransition
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellContainerTransform

@OptIn(ExperimentalSharedTransitionApi::class)
internal data class VideoDetailTransitionState(
    val animatedVisibilityScope: AnimatedVisibilityScope?,
    val sharedTransitionScope: SharedTransitionScope?,
    val entryOwnsMiuixCardTransition: Boolean,
    val isExitTransitionInProgress: Boolean,
    val detailShellSharedBoundsEnabled: Boolean,
    val suppressEnterFadeAfterBackPreview: Boolean,
    val progress: State<Float>,
    val detailChildTransitionEnabled: Boolean,
    val coverSharedBoundsActive: Boolean,
    val sharedBoundsActive: Boolean,
    val routeSheetFrameProvider: () -> VideoDetailRouteSheetFrame,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun rememberVideoDetailTransitionState(
    bvid: String,
    sourceRoute: String?,
    transitionEnabled: Boolean,
    keepLoadedContentForBackPreview: Boolean,
    motionSpec: VideoSharedTransitionMotionSpec,
    routeSheetMotion: VideoDetailRouteSheetMotion,
): VideoDetailTransitionState {
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val videoCardClock = LocalVideoCardTransitionClock.current
    val miuixCardTransition = LocalMiuixVideoCardTransitionState.current
    val transitionBackgroundState = LocalVideoCardTransitionBackgroundState.current
    val entryOwnsMiuixCardTransition = isVideoDetailEntryActiveMiuixTransitionSource(
        entrySourceRoute = sourceRoute,
        activeSourceRoute = transitionBackgroundState.sourceRouteProvider(),
        miuixTransitionEnabled = miuixCardTransition.enabled,
    )
    val entryMiuixTransitionEnabled =
        miuixCardTransition.enabled && entryOwnsMiuixCardTransition
    // Nav3 1.2 + ExitTransition.None can finish AnimatedContent before PostExit is observed.
    // Also treat card-clock RETURNING as exit so secondary chrome / live morph stay in sync.
    val scopedVideoCardPhase = videoCardClock?.phase?.takeIf {
        !miuixCardTransition.enabled || entryOwnsMiuixCardTransition
    }
    val isExitTransitionInProgress = shouldTreatVideoDetailExitTransitionInProgress(
        animatedVisibilityTargetIsPostExit =
            animatedVisibilityScope?.transition?.targetState == EnterExitState.PostExit,
        videoCardBackgroundPhase = scopedVideoCardPhase,
    ) || (entryMiuixTransitionEnabled && (
        miuixCardTransition.isGestureInProgressProvider() ||
            videoCardClock?.gestureRestoreInProgress == true
    ))
    val detailShellSharedBoundsEnabled = if (miuixCardTransition.enabled) {
        entryMiuixTransitionEnabled
    } else {
        shouldUseVideoCardShellContainerTransform(
            sourceRoute = sourceRoute,
            transitionEnabled = transitionEnabled,
            hasSharedTransitionScope = sharedTransitionScope != null,
            hasAnimatedVisibilityScope = animatedVisibilityScope != null,
        )
    }
    var wasKeptAsBackPreview by rememberSaveable(bvid) { mutableStateOf(false) }
    SideEffect {
        if (keepLoadedContentForBackPreview) wasKeptAsBackPreview = true
    }
    val suppressEnterFadeAfterBackPreview = shouldSuppressVideoDetailEnterFadeAfterBackPreview(
        wasKeptAsBackPreview = wasKeptAsBackPreview,
        keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
    )
    // Shell sharedBounds 路径：根 progress 必须与 boundsTransform 同 duration/easing，
    // 再回灌 VideoCardTransitionClock，形成单时钟。
    val progress = if (entryMiuixTransitionEnabled) {
        remember(miuixCardTransition) {
            derivedStateOf { miuixCardTransition.progressProvider() }
        }
    } else if (
        shouldUseVideoDetailRootTransitionProgress(
            detailShellSharedBoundsEnabled = detailShellSharedBoundsEnabled,
            hasAnimatedVisibilityScope = animatedVisibilityScope != null,
            keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
        )
    ) {
        requireNotNull(animatedVisibilityScope).transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.PostExit) {
                    // 返回：Linear + 满 morph 时长——spec 直读 bounds 侧的单一事实来源
                    // （videoSharedElementReturnMorph*），与 sharedBounds 严格同源。
                    tween(
                        durationMillis =
                            com.android.purebilibili.core.ui.transition
                                .videoSharedElementReturnMorphDurationMillis(
                                    motionSpec.durationMillis.coerceAtLeast(0),
                                ),
                        easing = com.android.purebilibili.core.ui.transition
                            .videoSharedElementReturnMorphEasing(),
                    )
                } else {
                    // 进场：Continuity + 满 morph 时长，与 bounds enter 一致
                    tween(
                        durationMillis = motionSpec.durationMillis.coerceAtLeast(0),
                        easing = motionSpec.enterAlphaEasing,
                    )
                }
            },
            label = "video-detail-shared-morph-clock",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val detailChildTransitionEnabled = transitionEnabled && !detailShellSharedBoundsEnabled
    val coverSharedBoundsActive = shouldEnableVideoCoverSharedTransition(
        transitionEnabled = detailChildTransitionEnabled,
        hasSharedTransitionScope = sharedTransitionScope != null,
        hasAnimatedVisibilityScope = animatedVisibilityScope != null,
    ) && !sourceRoute.isNullOrBlank()
    val sharedBoundsActive = detailShellSharedBoundsEnabled || coverSharedBoundsActive
    // 单时钟回灌：与 sharedBounds 共用 AVS Transition 的 progress。
    // progress.value 每帧变化，必须圈在独立重组作用域内读取：本函数带返回值、
    // 不可重启，若在这里读会把每帧重组放大到整个详情 StateHolder，
    // morph 期间掉帧直接表现为进场/返回画面抖动。
    ReportVideoDetailMorphProgressToClock(
        enabled = detailShellSharedBoundsEnabled && !entryMiuixTransitionEnabled,
        progress = progress,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
    val routeSheetFrameProvider = rememberVideoDetailRouteSheetFrameProvider(
        motion = routeSheetMotion,
        isExitTransitionInProgress = isExitTransitionInProgress,
        sharedBoundsActive = sharedBoundsActive,
    )
    return VideoDetailTransitionState(
        animatedVisibilityScope = animatedVisibilityScope,
        sharedTransitionScope = sharedTransitionScope,
        entryOwnsMiuixCardTransition = entryOwnsMiuixCardTransition,
        isExitTransitionInProgress = isExitTransitionInProgress,
        detailShellSharedBoundsEnabled = detailShellSharedBoundsEnabled,
        suppressEnterFadeAfterBackPreview = suppressEnterFadeAfterBackPreview,
        progress = progress,
        detailChildTransitionEnabled = detailChildTransitionEnabled,
        coverSharedBoundsActive = coverSharedBoundsActive,
        sharedBoundsActive = sharedBoundsActive,
        routeSheetFrameProvider = routeSheetFrameProvider,
    )
}

/**
 * 把详情 AVS 的 morph progress 逐帧回灌 [VideoCardTransitionClock]（单时钟）。
 *
 * 独立 Unit composable：progress / isTransitionActive / isRunning 这些每帧
 * 变化的快照读取被圈在这个近零成本的重组作用域内，宿主（详情 StateHolder）
 * 不再随 morph 每帧重组。仍用 [SideEffect] 回灌，保证发生在本帧组合完成后、
 * 绘制前，与 sharedBounds overlay 同帧同源，不引入一帧滞后。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ReportVideoDetailMorphProgressToClock(
    enabled: Boolean,
    progress: State<Float>,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val morphReporter = LocalVideoCardMorphProgressReporter.current
    if (!enabled || morphReporter == null) return
    val morphFraction = progress.value
    val morphActive = sharedTransitionScope?.isTransitionActive == true ||
        animatedVisibilityScope?.transition?.isRunning == true ||
        animatedVisibilityScope?.transition?.targetState == EnterExitState.PostExit
    SideEffect {
        morphReporter.report(
            morphFraction = morphFraction,
            active = morphActive,
        )
    }
    DisposableEffect(morphReporter) {
        onDispose {
            morphReporter.report(morphFraction = progress.value, active = false)
        }
    }
}

@Composable
internal fun rememberVideoDetailRouteSheetFrameProvider(
    motion: VideoDetailRouteSheetMotion,
    isExitTransitionInProgress: Boolean,
    sharedBoundsActive: Boolean = false
): () -> VideoDetailRouteSheetFrame {
    // shell sharedBounds 接管整张详情壳的 morph 时，sheet 自身的 scale/translation/corner/scrim
    // 必须全部停摆——否则会与共享元素同时形变导致撕裂。等价于 motion.enabled = false。
    val effectiveMotion = if (sharedBoundsActive) motion.copy(enabled = false) else motion
    val routeSheetProgress = remember(effectiveMotion.enabled) {
        Animatable(if (effectiveMotion.enabled) 0f else 1f)
    }
    val routeSheetSettleProgress = remember(effectiveMotion.enabled) {
        Animatable(0f)
    }
    var settleDirection by remember {
        mutableStateOf(VideoDetailRouteSheetSettleDirection.None)
    }
    // predictive back 预览被取消时（exit → 重新 enter），tween 若仍用全时长会显得拖沓；
    // 按剩余进度比例缩短恢复段时长，保持原 easing 设计。
    var previousExitInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(
        effectiveMotion.enabled,
        effectiveMotion.mainDurationMillis,
        effectiveMotion.settleDurationMillis,
        effectiveMotion.enterEasing,
        effectiveMotion.returnEasing,
        isExitTransitionInProgress
    ) {
        if (!effectiveMotion.enabled) {
            settleDirection = VideoDetailRouteSheetSettleDirection.None
            routeSheetSettleProgress.snapTo(0f)
            routeSheetProgress.snapTo(1f)
            previousExitInProgress = false
            return@LaunchedEffect
        }

        val isCancelRecovery = previousExitInProgress && !isExitTransitionInProgress
        previousExitInProgress = isExitTransitionInProgress
        settleDirection = VideoDetailRouteSheetSettleDirection.None
        routeSheetSettleProgress.snapTo(0f)
        val targetProgress = if (isExitTransitionInProgress) 0f else 1f
        routeSheetProgress.animateTo(
            targetValue = targetProgress,
            animationSpec = tween(
                durationMillis = if (isCancelRecovery) {
                    val remaining = 1f - routeSheetProgress.value
                    (effectiveMotion.mainDurationMillis * remaining)
                        .roundToInt()
                        .coerceIn(60, effectiveMotion.mainDurationMillis)
                } else {
                    effectiveMotion.mainDurationMillis
                },
                easing = if (isExitTransitionInProgress) {
                    effectiveMotion.returnEasing
                } else {
                    effectiveMotion.enterEasing
                }
            )
        )
        settleDirection = if (isExitTransitionInProgress) {
            VideoDetailRouteSheetSettleDirection.Return
        } else {
            VideoDetailRouteSheetSettleDirection.Enter
        }
        routeSheetSettleProgress.snapTo(1f)
        routeSheetSettleProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = effectiveMotion.settleDurationMillis,
                easing = if (isExitTransitionInProgress) {
                    effectiveMotion.returnEasing
                } else {
                    effectiveMotion.enterEasing
                }
            )
        )
        settleDirection = VideoDetailRouteSheetSettleDirection.None
    }

    return remember(effectiveMotion, routeSheetProgress, routeSheetSettleProgress) {
        {
            resolveVideoDetailRouteSheetFrame(
                rawProgress = routeSheetProgress.value,
                settleProgress = routeSheetSettleProgress.value,
                settleDirection = settleDirection,
                motion = effectiveMotion
            )
        }
    }
}

/** Keep the regular detail backing outside wallpaper-card handoffs. */
internal fun Modifier.videoDetailBacking(
    color: Color,
    alphaProvider: (() -> Float)?,
): Modifier = if (alphaProvider == null) {
    background(color)
} else {
    drawBehind {
        val alpha = alphaProvider()
        if (alpha > 0f) drawRect(color, alpha = alpha)
    }
}

@Composable
internal fun VideoDetailRouteSheetHost(
    frameProvider: () -> VideoDetailRouteSheetFrame,
    motion: VideoDetailRouteSheetMotion,
    isFullscreenMode: Boolean,
    backgroundColor: Color,
    backgroundAlpha: Float = 1f,
    backgroundAlphaProvider: () -> Float = { backgroundAlpha },
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlayContent: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                val frame = frameProvider()
                if (frame.backgroundScrimAlpha > 0.001f) {
                    drawRect(Color.Black.copy(alpha = frame.backgroundScrimAlpha))
                }
                drawContent()
            }
            .graphicsLayer {
                val frame = frameProvider()
                scaleX = if (isFullscreenMode) 1f else frame.scale
                scaleY = if (isFullscreenMode) 1f else frame.scale
                translationY = if (isFullscreenMode) 0f else with(density) {
                    frame.translationYDp.dp.toPx()
                }
                transformOrigin = TransformOrigin(0.5f, 0f)
                clip = !isFullscreenMode && motion.enabled && frame.cornerDp > 0.01f
                shape = RoundedCornerShape(frame.cornerDp.dp)
            }
            .drawBehind {
                val background = if (isFullscreenMode) Color.Black else backgroundColor
                drawRect(background.copy(alpha = backgroundAlphaProvider().coerceIn(0f, 1f)))
            },
    ) {
        content()
        overlayContent()
    }
}
