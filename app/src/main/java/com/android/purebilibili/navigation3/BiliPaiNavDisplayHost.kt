package com.android.purebilibili.navigation3

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalMiuixVideoCardTransitionState
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionBackgroundState
import com.android.purebilibili.core.ui.transition.MiuixVideoCardTransitionState
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundState
import com.android.purebilibili.core.ui.transition.VideoCardTransitionExposure
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionClock
import com.android.purebilibili.core.ui.transition.LocalPredictiveBackBackgroundState
import com.android.purebilibili.core.ui.transition.PredictiveBackBackgroundState
import com.android.purebilibili.core.ui.transition.VideoCardTransitionClock
import com.android.purebilibili.core.ui.transition.VideoCardTransitionHostDepthLayer
import com.android.purebilibili.core.ui.transition.VideoCardTransitionNavBackdrop
import com.android.purebilibili.core.ui.transition.rememberVideoCardTransitionSnapshotHandle
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionExposure
import com.android.purebilibili.core.ui.transition.resolveVideoHeroMotionSpec
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.VideoCardTransitionSettleState
import com.android.purebilibili.core.ui.transition.VideoCardTransitionDiagnostics
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.resolvePredictiveBackGestureBlurProgress
import com.android.purebilibili.core.ui.transition.shouldReleaseHostOwnedDepthLayer
import com.android.purebilibili.core.ui.transition.shouldShowVideoCardTransitionNavBackdrop
import com.android.purebilibili.core.ui.transition.shouldUseHostOwnedVideoCardTransitionSnapshot
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackAnimationStyle
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackExitDirection
import com.android.purebilibili.navigation3.predictiveback.MIUIX_PREDICTIVE_BACK_DEFAULT_MAX_PROGRESS_PERCENT
import com.android.purebilibili.navigation3.predictiveback.biliPaiMiuixNavTransition
import com.android.purebilibili.navigation3.predictiveback.miuixVideoCardNavTransition
import com.android.purebilibili.navigation3.predictiveback.MiuixVideoCardContentScale
import com.android.purebilibili.navigation3.predictiveback.resolveMiuixVideoCardContentScaleForSourceLayout
import com.android.purebilibili.navigation3.predictiveback.MiuixVideoCardTransitionProgress
import com.android.purebilibili.navigation3.predictiveback.shouldUseMiuixPredictiveBackProgress
import kotlinx.coroutines.flow.collect
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

internal class BiliPaiProgrammaticBackDispatcher {
    private var callback: (() -> Unit)? = null

    fun register(callback: () -> Unit) {
        this.callback = callback
    }

    fun unregister(callback: () -> Unit) {
        if (this.callback === callback) this.callback = null
    }

    fun dispatch(): Boolean {
        val action = callback ?: return false
        action()
        return true
    }
}

@Composable
internal fun BiliPaiNavDisplayHost(
    backStack: SnapshotStateList<BiliPaiNavKey>,
    cardTransitionEnabled: Boolean = true,
    videoTransitionRealtimeBlurEnabled: Boolean = false,
    isLightBackground: Boolean = false,
    reduceMotion: Boolean = false,
    videoSharedTransitionDurationMillis: Int,
    videoCardClock: VideoCardTransitionClock,
    predictiveBackAnimationStyle: BiliPaiPredictiveBackAnimationStyle =
        BiliPaiPredictiveBackAnimationStyle.MIUIX,
    predictiveBackExitDirection: BiliPaiPredictiveBackExitDirection =
        BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT,
    miuixTransitionBlurEnabled: Boolean = true,
    miuixPredictiveBackMaxProgressPercent: Int =
        MIUIX_PREDICTIVE_BACK_DEFAULT_MAX_PROGRESS_PERCENT,
    videoSharedReturnGestureFollowEnabled: Boolean = true,
    sourceMetadata: BiliPaiNavSourceMetadata,
    programmaticBackDispatcher: BiliPaiProgrammaticBackDispatcher,
    preferWholeCardReturn: Boolean = false,
    onBack: () -> Unit,
    onPrepareVideoCardSharedReturn: () -> Boolean = { false },
    onRelatedVideoDetailReturned: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (BiliPaiNavKey) -> Unit,
) = BoxWithConstraints(modifier = modifier) {
    val density = LocalDensity.current.density
    val hostBounds = if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
        Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
    } else null
    // Resolve geometry once in host-local px. Never independently retime individual layers.
    val heroMotion = remember(sourceMetadata.sourceBounds, hostBounds, density,
        videoSharedTransitionDurationMillis, reduceMotion) {
        resolveVideoHeroMotionSpec(videoSharedTransitionDurationMillis,
            sourceMetadata.sourceBounds, hostBounds, density, reduceMotion)
    }
    val application = LocalContext.current.applicationContext as Application
    val speedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val diagnosticConfiguration by rememberUpdatedState(
        "speed=${speedSettings.speed} custom_duration=${speedSettings.customDurationMillis} " +
            "realtime_blur=$videoTransitionRealtimeBlurEnabled gesture_follow=$videoSharedReturnGestureFollowEnabled " +
            "predictive_style=$predictiveBackAnimationStyle reduced_motion=$reduceMotion",
    )
    val stackSnapshot = backStack.toList()
    val currentKey = stackSnapshot.lastOrNull()
    val latestOnBack by rememberUpdatedState(onBack)
    val latestPrepareReturn by rememberUpdatedState(onPrepareVideoCardSharedReturn)
    val latestRelatedReturn by rememberUpdatedState(onRelatedVideoDetailReturned)
    val latestPreferWholeCardReturn by rememberUpdatedState(preferWholeCardReturn)
    val cardMorphMode = resolveBiliPaiVideoCardMorphMode(
        cardTransitionEnabled = cardTransitionEnabled,
        reduceMotion = reduceMotion,
        sourceRoute = sourceMetadata.sourceRoute,
        hasUsableSourceBounds = sourceMetadata.sourceBounds
            ?.let { it.width > 1f && it.height > 1f } == true,
    )
    val cardMorphAvailable = cardMorphMode != BiliPaiVideoCardMorphMode.NONE
    var relatedReturnRestorePending by remember { mutableStateOf(false) }
    var relatedReturnTransitionObserved by remember { mutableStateOf(false) }
    val style = if (reduceMotion) {
        BiliPaiPredictiveBackAnimationStyle.NONE
    } else {
        predictiveBackAnimationStyle
    }
    val videoReturnAnimated = cardMorphAvailable || style != BiliPaiPredictiveBackAnimationStyle.NONE
    val performBack = remember(
        backStack,
        cardMorphAvailable,
        videoReturnAnimated,
        sourceMetadata.sourceRoute,
    ) {
        {
            val leavingKey = backStack.lastOrNull()
            if (leavingKey is BiliPaiNavKey.VideoDetail) {
                latestPrepareReturn()
                if (cardMorphAvailable) {
                    videoCardClock.beginReturning(sourceMetadata.sourceRoute, videoCardClock.depthProgress())
                }
            }
            val returningFromRelated = (leavingKey as? BiliPaiNavKey.VideoDetail)
                ?.sourceRoute
                ?.substringBefore('?')
                ?.startsWith("video/") == true
            latestOnBack()
            if (returningFromRelated) {
                if (videoReturnAnimated) {
                    relatedReturnTransitionObserved = false
                    relatedReturnRestorePending = true
                } else {
                    latestRelatedReturn()
                }
            }
        }
    }

    DisposableEffect(programmaticBackDispatcher, performBack) {
        programmaticBackDispatcher.register(performBack)
        onDispose { programmaticBackDispatcher.unregister(performBack) }
    }

    val globalTransition = remember(
        style,
        predictiveBackExitDirection,
        isLightBackground,
        miuixTransitionBlurEnabled,
        miuixPredictiveBackMaxProgressPercent,
    ) {
        biliPaiMiuixNavTransition(
            animation = style,
            exitDirection = predictiveBackExitDirection,
            isLightBackground = isLightBackground,
            miuixTransitionBlurEnabled = miuixTransitionBlurEnabled,
            miuixPredictiveBackMaxProgressPercent =
                miuixPredictiveBackMaxProgressPercent,
        )
    }
    val predictiveBackExcludedTransition = remember(
        globalTransition,
        style,
        predictiveBackExitDirection,
        isLightBackground,
        miuixTransitionBlurEnabled,
    ) {
        if (shouldUseMiuixPredictiveBackProgress(style, enabled = true)) {
            biliPaiMiuixNavTransition(
                animation = BiliPaiPredictiveBackAnimationStyle.MIUIX,
                exitDirection = predictiveBackExitDirection,
                isLightBackground = isLightBackground,
                miuixTransitionBlurEnabled = miuixTransitionBlurEnabled,
                miuixPredictiveBackProgressEnabled = false,
            )
        } else {
            globalTransition
        }
    }
    // A restored parent session must not keep the departed child's scope at depth -1.
    val videoCardTransitionProgress = remember(sourceMetadata.sourceKey) { MiuixVideoCardTransitionProgress() }
    val videoFallbackTransition = if (cardTransitionEnabled) {
        // 卡片形变开启时，fallback 只负责接住源卡片不可用等降级场景，避免再接管
        // Miuix 预测返回进度。
        predictiveBackExcludedTransition
    } else {
        // 关闭卡片形变后，视频页完整沿用“全局导航动画”：Miuix、AOSP、缩放、
        // 经典与无动画都由同一个 Miuix NavDisplay 驱动，不叠加 Compose 转场。
        globalTransition
    }
    val observedVideoFallbackTransition = remember(
        videoFallbackTransition,
        videoCardTransitionProgress,
    ) {
        videoCardTransitionProgress.observe(videoFallbackTransition)
    }
    val returningProvider = remember(videoCardClock) {
        { videoCardClock.phase != VideoCardTransitionBackgroundPhase.OPENING }
    }
    val videoCardContentScale = resolveMiuixVideoCardContentScaleForSourceLayout(
        sourceLayout = sourceMetadata.sourceLayout,
        fullscreen = false,
    )
    val navCornerRadius = rememberDeviceCornerRadius(defaultRadius = 0.dp)
    val effectiveDeviceCornerDp = if (navCornerRadius > 0.dp) navCornerRadius else 32.dp
    val videoCardTransition = remember(
        cardMorphAvailable,
        sourceMetadata.sourceBounds,
        sourceMetadata.sourceCornerDp,
        videoSharedTransitionDurationMillis,
        heroMotion,
        videoCardTransitionProgress,
        observedVideoFallbackTransition,
        videoCardContentScale,
        videoSharedReturnGestureFollowEnabled,
        effectiveDeviceCornerDp,
    ) {
        if (cardMorphAvailable) {
            miuixVideoCardNavTransition(
                sourceBounds = sourceMetadata.sourceBounds,
                sourceCornerDp = sourceMetadata.sourceCornerDp,
                durationMillis = videoSharedTransitionDurationMillis,
                fallback = observedVideoFallbackTransition,
                progress = videoCardTransitionProgress,
                contentScale = videoCardContentScale,
                gestureFollowEnabled = videoSharedReturnGestureFollowEnabled,
                heroMotionSpec = heroMotion,
                returningProvider = returningProvider,
                deviceCornerDp = effectiveDeviceCornerDp,
            )
        } else {
            observedVideoFallbackTransition
        }
    }
    val fullscreenVideoCardTransition = remember(
        cardMorphAvailable,
        sourceMetadata.sourceBounds,
        sourceMetadata.sourceCornerDp,
        videoSharedTransitionDurationMillis,
        heroMotion,
        videoCardTransitionProgress,
        observedVideoFallbackTransition,
        videoSharedReturnGestureFollowEnabled,
        effectiveDeviceCornerDp,
    ) {
        if (cardMorphAvailable) {
            miuixVideoCardNavTransition(
                sourceBounds = sourceMetadata.sourceBounds,
                sourceCornerDp = sourceMetadata.sourceCornerDp,
                durationMillis = videoSharedTransitionDurationMillis,
                fallback = observedVideoFallbackTransition,
                progress = videoCardTransitionProgress,
                contentScale = MiuixVideoCardContentScale.CropCenter,
                gestureFollowEnabled = videoSharedReturnGestureFollowEnabled,
                heroMotionSpec = heroMotion,
                returningProvider = returningProvider,
                deviceCornerDp = effectiveDeviceCornerDp,
            )
        } else {
            observedVideoFallbackTransition
        }
    }

    DisposableEffect(cardMorphAvailable, videoCardClock, videoCardTransitionProgress) {
        videoCardClock.bindNavigationDriver(
            if (cardMorphAvailable) ({ videoCardTransitionProgress.depthOrNull() }) else null,
        )
        onDispose { videoCardClock.bindNavigationDriver(null) }
    }
    var previousStack by remember { mutableStateOf(stackSnapshot) }
    LaunchedEffect(stackSnapshot, cardMorphAvailable) {
        val previous = previousStack
        previousStack = stackSnapshot
        if (!cardMorphAvailable) {
            videoCardClock.snapClearAndIdle()
            CardPositionManager.clearNativeVideoCardLayers()
            return@LaunchedEffect
        }
        val previousTop = previous.lastOrNull()
        val openedCardDestination = isCardMorphDestinationNavKey(currentKey) &&
            stackSnapshot.size > previous.size
        val returnedFromCardDestination = isCardMorphDestinationNavKey(previousTop) &&
            stackSnapshot.size < previous.size
        when {
            openedCardDestination -> {
                videoCardClock.beginOpeningIfNeeded(sourceMetadata.sourceRoute)
            }
            returnedFromCardDestination -> {
                videoCardClock.beginReturning(sourceMetadata.sourceRoute,
                    startDepth = videoCardClock.depthProgress())
            }
        }
    }
    LaunchedEffect(cardMorphAvailable, videoCardTransitionProgress, heroMotion, sourceMetadata.sourceKey) {
        if (!cardMorphAvailable) return@LaunchedEffect
        // Coarse states only: no frame-rate composition reads or competing fallback jobs.
        snapshotFlow { videoCardTransitionProgress.settleStateOrNull() }.collect { state ->
            if (state != null) {
                videoCardClock.followNavigationDriver(state, videoCardTransitionProgress.releaseVelocity())
                if (state == VideoCardTransitionSettleState.Idle) {
                    // LiveNavTransitionScope reads the shared navigation presentation even after
                    // its video entry leaves. Release it before another route reuses that driver.
                    videoCardTransitionProgress.clear()
                    CardPositionManager.clearNativeVideoCardLayers()
                }
                VideoCardTransitionDiagnostics.onMotionPhase(
                    state, heroMotion, sourceMetadata.sourceLayout, diagnosticConfiguration,
                )
            }
        }
    }

    val videoCardSnapshotHandle = rememberVideoCardTransitionSnapshotHandle()
    val transitionMotionTier = if (reduceMotion) MotionTier.Reduced else MotionTier.Normal
    val videoCardProgressProvider = remember(
        cardMorphAvailable,
        videoCardClock,
        videoCardTransitionProgress,
    ) {
        {
            if (cardMorphAvailable) {
                videoCardTransitionProgress.depthOr(videoCardClock.depthProgress())
            } else {
                videoCardClock.depthProgress()
            }
        }
    }
    val videoCardGestureProvider = remember(cardMorphAvailable, videoCardTransitionProgress) {
        { cardMorphAvailable && videoCardTransitionProgress.isGestureInProgress() }
    }
    val videoCardExposureProvider = remember(
        videoCardClock,
        videoCardGestureProvider,
        videoCardTransitionProgress,
    ) {
        {
            val settleState = videoCardTransitionProgress.settleStateOrNull()
            val effectivePhase = when (settleState) {
                VideoCardTransitionSettleState.AutoReturn -> VideoCardTransitionBackgroundPhase.RETURNING
                else -> videoCardClock.phase
            }
            val effectiveRestore = videoCardClock.gestureRestoreInProgress ||
                settleState == VideoCardTransitionSettleState.CancelRestore
            resolveVideoCardTransitionExposure(
                phase = effectivePhase,
                predictiveBackInProgress = videoCardGestureProvider(),
                gestureRestoreInProgress = effectiveRestore,
            )
        }
    }
    val effectiveVideoCardExposure = videoCardExposureProvider()
    // Ordinary related-video slides have no card clock. Read their actual navigation settle
    // instead of restoring the parent's morph while the child is still moving off screen.
    val fallbackReturnExposure by remember(videoCardTransitionProgress) {
        derivedStateOf {
            when (videoCardTransitionProgress.settleStateOrNull()) {
                VideoCardTransitionSettleState.AutoReturn -> VideoCardTransitionExposure.Returning
                VideoCardTransitionSettleState.Idle,
                VideoCardTransitionSettleState.Held,
                null -> VideoCardTransitionExposure.Idle
                else -> VideoCardTransitionExposure.Opening
            }
        }
    }
    val relatedReturnExposure = if (cardMorphAvailable) effectiveVideoCardExposure else fallbackReturnExposure
    LaunchedEffect(
        relatedReturnRestorePending,
        relatedReturnExposure,
        videoReturnAnimated,
    ) {
        val restoreDecision = resolveRelatedReturnSourceRestoreDecision(
            restorePending = relatedReturnRestorePending,
            transitionObserved = relatedReturnTransitionObserved,
            transitionAnimated = videoReturnAnimated,
            exposure = relatedReturnExposure,
        )
        relatedReturnTransitionObserved = restoreDecision.transitionObserved
        if (restoreDecision.shouldRestore) {
            // The nested source geometry remains immutable through the complete predictive
            // settle. Only arm the parent's older session after the navigation driver is idle.
            relatedReturnRestorePending = false
            relatedReturnTransitionObserved = false
            latestRelatedReturn()
        }
    }
    LaunchedEffect(effectiveVideoCardExposure) {
        if (shouldReleaseHostOwnedDepthLayer(effectiveVideoCardExposure)) {
            videoCardSnapshotHandle.releaseSession()
        }
    }
    val currentBackTarget = stackSnapshot.getOrNull(stackSnapshot.lastIndex - 1)
    val showVideoCardNavBackdrop = shouldShowVideoCardTransitionNavBackdrop(
        cardTransitionEnabled = cardMorphAvailable,
        exposure = effectiveVideoCardExposure,
        isVideoDetailOnStack = isCardMorphDestinationNavKey(currentKey),
        isReturningToVideoDetail = isCardMorphDestinationNavKey(currentBackTarget),
    )
    val transitionBackgroundState = remember(
        sourceMetadata.sourceKey,
        sourceMetadata.sourceRoute,
        sourceMetadata.sourceCornerDp,
        sourceMetadata.sourceBounds,
        videoCardProgressProvider,
        videoCardExposureProvider,
        videoCardSnapshotHandle,
        transitionMotionTier,
        isLightBackground,
        videoTransitionRealtimeBlurEnabled,
    ) {
        VideoCardTransitionBackgroundState(
            progressProvider = videoCardProgressProvider,
            sourceRouteProvider = { sourceMetadata.sourceRoute },
            sourceKeyProvider = { sourceMetadata.sourceKey },
            phaseProvider = { videoCardClock.phase },
            exposureProvider = videoCardExposureProvider,
            sourceCornerDpProvider = { sourceMetadata.sourceCornerDp },
            sourceBoundsProvider = { sourceMetadata.sourceBounds },
            snapshotHandle = videoCardSnapshotHandle,
            isReturnGestureInProgressProvider = videoCardGestureProvider,
            isGestureRestoreInProgressProvider = { videoCardClock.gestureRestoreInProgress },
            preferWholeCardReturnProvider = { latestPreferWholeCardReturn },
            motionTierProvider = { transitionMotionTier },
            isLightBackgroundProvider = { isLightBackground },
            realtimeBlurEnabledProvider = { videoTransitionRealtimeBlurEnabled },
        )
    }
    val videoCardLayoutWidthProvider = remember(videoCardTransitionProgress) {
        {
            videoCardTransitionProgress.layoutWidthOr(
                fallback = 1f, // overwritten after first transformEntry bind
            )
        }
    }
    val videoCardLayoutHeightProvider = remember(videoCardTransitionProgress) {
        {
            videoCardTransitionProgress.layoutHeightOr(
                fallback = 1f,
            )
        }
    }
    val miuixCardTransitionState = remember(
        cardMorphAvailable,
        heroMotion,
        videoCardProgressProvider,
        videoCardGestureProvider,
        videoCardLayoutWidthProvider,
        videoCardLayoutHeightProvider,
        sourceMetadata.sourceBounds,
        sourceMetadata.sourceCoverBounds,
        sourceMetadata.sourceLayout,
        sourceMetadata.sourceChromeSnapshot,
    ) {
        MiuixVideoCardTransitionState(
            enabled = cardMorphAvailable,
            motionSpec = heroMotion,
            progressProvider = videoCardProgressProvider,
            isGestureInProgressProvider = videoCardGestureProvider,
            layoutWidthProvider = videoCardLayoutWidthProvider,
            layoutHeightProvider = videoCardLayoutHeightProvider,
            sourceBoundsProvider = { sourceMetadata.sourceBounds },
            sourceCoverBoundsProvider = { sourceMetadata.sourceCoverBounds },
            sourceLayout = sourceMetadata.sourceLayout,
            sourceChromeSnapshot = sourceMetadata.sourceChromeSnapshot,
        )
    }
    // 恢复 0.2.2 的预测返回背景链路：目标返回页（栈前一 key）在预测返回手势中
    // 随手势进度模糊/消退，迁移到 Miuix 导航时该 provide 曾丢失。
    val predictiveBackBackgroundState = remember(
        currentKey,
        cardMorphAvailable,
        videoCardTransitionProgress,
        currentBackTarget,
        transitionMotionTier,
        isLightBackground,
        videoTransitionRealtimeBlurEnabled,
        miuixTransitionBlurEnabled,
    ) {
        PredictiveBackBackgroundState(
            progressProvider = {
                val blurEnabled = if (cardMorphAvailable) {
                    videoTransitionRealtimeBlurEnabled
                } else {
                    miuixTransitionBlurEnabled
                }
                if (!blurEnabled || !isCardMorphDestinationNavKey(currentKey)) {
                    0f
                } else {
                    videoCardTransitionProgress.gestureBackProgress()
                        ?.takeIf { cardMorphAvailable }
                        ?.let { resolvePredictiveBackGestureBlurProgress(it) }
                        ?: 0f
                }
            },
            targetKeyProvider = { currentBackTarget },
            motionTierProvider = { transitionMotionTier },
            isLightBackgroundProvider = { isLightBackground },
        )
    }

    val roundAllCorners = style == BiliPaiPredictiveBackAnimationStyle.AOSP ||
        style == BiliPaiPredictiveBackAnimationStyle.SCALE ||
        style == BiliPaiPredictiveBackAnimationStyle.CLASSIC
    // Video-card morph owns all four corners. Keeping NavDisplay's Leading clip enabled here
    // applies a second, device-radius clip only to the left edge and makes it visibly rounder
    // than the right edge during return.
    val videoCardMorphOwnsCorners = cardMorphAvailable && (
        isCardMorphDestinationNavKey(currentKey) ||
            effectiveVideoCardExposure == VideoCardTransitionExposure.Opening ||
            effectiveVideoCardExposure == VideoCardTransitionExposure.BackPreview ||
            effectiveVideoCardExposure == VideoCardTransitionExposure.Returning ||
            effectiveVideoCardExposure == VideoCardTransitionExposure.Restoring
    )
    val enableHostCornerClip = !videoCardMorphOwnsCorners
    // The retained source page already owns blur/scrim through the video-card depth layer.
    // Miuix's generic covered-entry dim can be resolved from the lower VideoDetail transition
    // during nested related-video navigation, which darkens that page a second time.
    val hostDimAmount = if (videoCardMorphOwnsCorners) 0f else 0.5f
    val backdropColor = AppSurfaceTokens.surface()
    val effects = remember(
        navCornerRadius,
        roundAllCorners,
        enableHostCornerClip,
        hostDimAmount,
        backdropColor,
    ) {
        NavDisplayEffects(
            enableCornerClip = enableHostCornerClip,
            cornerClipRadius = if (roundAllCorners && navCornerRadius <= 0.dp) 32.dp else navCornerRadius,
            cornerClipMode = if (roundAllCorners) {
                NavCornerClipMode.All
            } else {
                NavCornerClipMode.Leading
            },
            dimAmount = hostDimAmount,
            backdropColor = backdropColor,
            blockInputDuringTransition = false,
        )
    }
    // 全屏滑动返回默认关闭（仅系统边缘预测返回），可在设置中开启。
    // 开启后仅对列表/设置等纵向页面生效，播放器、详情、WebView 等
    // 横滑冲突页面始终禁用（见 BiliPaiNavEntryProvider）。
    val fullScreenSwipeBackEnabled by
        com.android.purebilibili.core.store.SettingsManager
            .getFullScreenSwipeBackEnabled(LocalContext.current)
            .collectAsStateWithLifecycle(initialValue = false)
    val swipeBackDirection = if (fullScreenSwipeBackEnabled) {
        when (LocalLayoutDirection.current) {
            LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
            LayoutDirection.Ltr -> NavSwipeDirection.LeftToRight
        }
    } else {
        NavSwipeDirection.None
    }
    val interceptPredictiveBack =
        style == BiliPaiPredictiveBackAnimationStyle.NONE && backStack.size > 1
    val globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (globalWallpaperVisible) {
                    Color.Transparent
                } else {
                    AppSurfaceTokens.groupedListContainer()
                }
            ),
    ) {
        VideoCardTransitionHostDepthLayer(
            enabled = cardMorphAvailable &&
                videoTransitionRealtimeBlurEnabled &&
                shouldUseHostOwnedVideoCardTransitionSnapshot(sourceMetadata.sourceRoute),
            snapshotHandle = videoCardSnapshotHandle,
            progressProvider = videoCardProgressProvider,
            phaseProvider = { videoCardClock.phase },
            exposureProvider = videoCardExposureProvider,
            isGestureRestoreInProgressProvider = { videoCardClock.gestureRestoreInProgress },
            motionTierProvider = { transitionMotionTier },
            isLightBackgroundProvider = { isLightBackground },
            realtimeBlurEnabledProvider = { videoTransitionRealtimeBlurEnabled },
            sourceBoundsProvider = { sourceMetadata.sourceBounds },
        )
        VideoCardTransitionNavBackdrop(
            visible = showVideoCardNavBackdrop,
            progressProvider = videoCardProgressProvider,
            phase = videoCardClock.phase,
            isLightBackground = isLightBackground,
        )
        @Suppress("UNCHECKED_CAST")
        NavDisplay(
            backStack = backStack as NavBackStack,
            onBack = performBack,
            transition = globalTransition,
            effects = effects,
        ) {
            biliPaiNavEntries(
                swipeBackDirection = swipeBackDirection,
                predictiveBackExcludedTransition = predictiveBackExcludedTransition,
                videoCardTransition = videoCardTransition,
                fullscreenVideoCardTransition = fullscreenVideoCardTransition,
            ) { key ->
                // Freeze this per entry so popping the key cannot remove its backing during exit.
                val opaqueVideoChild = remember(key) {
                    shouldUseOpaqueVideoChildBackground(key, stackSnapshot)
                }
                BiliPaiMiuixNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = performBack,
                ) {
                    CompositionLocalProvider(
                        LocalGlobalWallpaperBackdropVisible provides
                            (globalWallpaperVisible && !opaqueVideoChild),
                        LocalVideoCardSharedElementSourceRoute provides key.toLegacyRoute(),
                        LocalVideoCardTransitionClock provides videoCardClock,
                        LocalVideoCardTransitionBackgroundState provides transitionBackgroundState,
                        LocalMiuixVideoCardTransitionState provides miuixCardTransitionState,
                        LocalPredictiveBackBackgroundState provides predictiveBackBackgroundState,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().then(
                                if (opaqueVideoChild) {
                                    Modifier.background(AppSurfaceTokens.groupedListContainer().copy(alpha = 1f))
                                } else Modifier
                            ),
                        ) {
                            ProvideMiuixNavViewModelApplicationExtras(application) {
                                content(key)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiliPaiMiuixNavEntry(
    interceptPredictiveBack: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = interceptPredictiveBack,
        onBackCompleted = onBack,
    )
    content()
}

@Composable
private fun ProvideMiuixNavViewModelApplicationExtras(
    application: Application,
    content: @Composable () -> Unit,
) {
    val navEntryOwner = LocalViewModelStoreOwner.current
    if (navEntryOwner == null) {
        content()
        return
    }
    val patchedOwner = remember(navEntryOwner, application) {
        buildMiuixNavViewModelStoreOwner(navEntryOwner, application)
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides patchedOwner) {
        content()
    }
}

private fun buildMiuixNavViewModelStoreOwner(
    navEntryOwner: ViewModelStoreOwner,
    application: Application,
): ViewModelStoreOwner {
    val defaultFactoryOwner = navEntryOwner as? HasDefaultViewModelProviderFactory
    val defaultCreationExtras = defaultFactoryOwner?.defaultViewModelCreationExtras
        ?: CreationExtras.Empty
    val patchedCreationExtras = MutableCreationExtras(defaultCreationExtras).apply {
        set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
    }
    return object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = navEntryOwner.viewModelStore
        override val defaultViewModelProviderFactory =
            defaultFactoryOwner?.defaultViewModelProviderFactory
                ?: ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        override val defaultViewModelCreationExtras: CreationExtras = patchedCreationExtras
    }
}
