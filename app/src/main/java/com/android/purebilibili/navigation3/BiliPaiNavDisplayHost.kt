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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import com.android.purebilibili.feature.settings.screen.settingsHasPersistentPanes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.feature.home.shouldRenderGlobalHomeWallpaperBackdrop
import com.android.purebilibili.core.store.HomeWallpaperEffectScope
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.LocalVideoDetailNavigationTransitionScope
import com.android.purebilibili.core.ui.transition.LocalClickToPlayEnabled
import com.android.purebilibili.core.ui.transition.LocalDynamicImagePreviewTextVisible
import com.android.purebilibili.core.ui.transition.LocalDynamicCommentPreviewVisible
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
import com.android.purebilibili.core.ui.transition.rememberVideoCardTransitionSnapshotHandle
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionExposure
import com.android.purebilibili.core.ui.transition.resolveVideoHeroMotionSpec
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.VideoCardTransitionSettleState
import com.android.purebilibili.core.ui.transition.VideoCardTransitionDiagnostics
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.resolvePredictiveBackGestureBlurProgress
import com.android.purebilibili.core.ui.transition.shouldReleaseHostOwnedDepthLayer
import com.android.purebilibili.core.ui.transition.shouldUseHostOwnedVideoCardTransitionSnapshot
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.navigation3.predictiveback.VideoCardBackCompletionPolicy
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackAnimationStyle
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackExitDirection
import com.android.purebilibili.navigation3.predictiveback.MIUIX_PREDICTIVE_BACK_DEFAULT_MAX_PROGRESS_PERCENT
import com.android.purebilibili.navigation3.predictiveback.biliPaiMiuixNavTransition
import com.android.purebilibili.navigation3.predictiveback.miuixVideoCardNavTransition
import com.android.purebilibili.navigation3.predictiveback.MiuixVideoCardContentScale
import com.android.purebilibili.navigation3.predictiveback.resolveMiuixVideoCardContentScaleForSourceLayout
import com.android.purebilibili.navigation3.predictiveback.MiuixVideoCardTransitionProgress
import com.android.purebilibili.navigation3.predictiveback.resolveMiuixVideoCardBackingColor
import com.android.purebilibili.navigation3.predictiveback.shouldUseMiuixPredictiveBackProgress
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
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
    activeMainHostRoute: String? = null,
    cardTransitionEnabled: Boolean = true,
    videoCardTransitionEnabled: Boolean = cardTransitionEnabled,
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
    settingsContainerMorphEnabled: Boolean = true,
    settingsContainerRealtimeBlurEnabled: Boolean = false,
    sourceMetadata: BiliPaiNavSourceMetadata,
    programmaticBackDispatcher: BiliPaiProgrammaticBackDispatcher,
    preferWholeCardReturn: Boolean = false,
    onBack: () -> Unit,
    onOpenCardContainer: (BiliPaiNavKey) -> Unit = { backStack.add(it) },
    onPrepareVideoCardSharedReturn: () -> Boolean = { false },
    onPrepareVideoCardGesture: () -> Unit = {},
    pendingRelatedReturnCount: Int = 0,
    relatedReturnRequestId: Long = 0L,
    onRelatedVideoDetailReturned: (Long, Long) -> Boolean = { _, _ -> false },
    videoSourceGeneration: Long = 0L,
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
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val visualGuard = com.android.purebilibili.core.ui.performance.LocalRuntimeVisualGuard.current
    // 在图层阶段读取运行时预算，降级不重建整棵导航树；玻璃与原生路径共用此保护。
    val navBlurMotionTierProvider = remember(visualGuard) {
        {
            val decision = visualGuard.value
            if (decision.forceLowBlurBudget) MotionTier.Reduced else decision.effectiveMotionTier
        }
    }
    val speedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val diagnosticConfiguration by rememberUpdatedState(
        "speed=${speedSettings.speed} custom_duration=${speedSettings.customDurationMillis} " +
            "realtime_blur=$videoTransitionRealtimeBlurEnabled " +
            "predictive_style=$predictiveBackAnimationStyle reduced_motion=$reduceMotion",
    )
    val stackSnapshot = backStack.toList()
    val settingsPersistentPanes = settingsHasPersistentPanes()
    val settingsContainerSources = remember { com.android.purebilibili.feature.settings.SettingsContainerSources() }
    val settingsContainerEnabled = settingsContainerMorphEnabled && !settingsPersistentPanes && !reduceMotion
    // 颜色按当前主题读取，不能以调色动画的每一帧重建容器转场。
    val settingsContainerPageColor = rememberUpdatedState(AppSurfaceTokens.groupedListContainer())
    val settingsContainerSnapshot = settingsContainerSources.sources.toMap()
    val availableSettingsContainerTransitions = remember(
        settingsContainerSnapshot, settingsContainerRealtimeBlurEnabled, navBlurMotionTierProvider,
    ) {
        settingsContainerSnapshot.mapValues { (_, source) ->
            SettingsContainerTransition(
                source, pageColor = { settingsContainerPageColor.value },
                realtimeBlurEnabled = settingsContainerRealtimeBlurEnabled,
                blurMotionTierProvider = navBlurMotionTierProvider,
            )
        }
    }
    val settingsContainerTransitions = if (!settingsContainerEnabled) emptyMap() else
        availableSettingsContainerTransitions.filterKeys { target ->
            isSettingsContainerBoundary(
                enabled = settingsContainerEnabled,
                backStack = stackSnapshot,
                target = target,
                owner = settingsContainerSnapshot[target]?.owner,
            )
        }
    LaunchedEffect(stackSnapshot, settingsContainerSnapshot.keys) {
        // 离场 entry 保留自己的转场对象；目录中清除旧来源，搜索直达不能复用上次点击。
        settingsContainerSources.sources.keys.retainAll(stackSnapshot.toSet())
    }
    val liveContainerSources = remember { com.android.purebilibili.feature.settings.SettingsContainerSources() }
    val liveContainerEnabled = cardTransitionEnabled && !reduceMotion &&
        com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    val liveContainerSnapshot = liveContainerSources.sources.toMap()
    val liveContainerPageColor = rememberUpdatedState(
        com.android.purebilibili.feature.live.resolveLiveBiliPaiRoomColorTokens().baseBackgroundColor,
    )
    val webContainerPageColor = rememberUpdatedState(androidx.compose.material3.MaterialTheme.colorScheme.surface)
    val articleContainerPageColor = rememberUpdatedState(androidx.compose.material3.MaterialTheme.colorScheme.background)
    val liveContainerIsLightBackground = rememberUpdatedState(isLightBackground)
    val availableLiveContainerTransitions = remember(
        liveContainerSnapshot, videoSharedTransitionDurationMillis,
        videoTransitionRealtimeBlurEnabled, settingsContainerRealtimeBlurEnabled, navBlurMotionTierProvider,
    ) {
        liveContainerSnapshot.mapValues { (target, source) ->
            val articleTarget = target is BiliPaiNavKey.ArticleDetail || target is BiliPaiNavKey.DynamicDetail
            val bangumiPlayerTarget = target is BiliPaiNavKey.BangumiPlayer
            val generalContainer = target is BiliPaiNavKey.Web || target is BiliPaiNavKey.Space || target is BiliPaiNavKey.BangumiDetail
            SettingsContainerTransition(
                source = source,
                pageColor = { when {
                    target is BiliPaiNavKey.BangumiPlayer -> androidx.compose.ui.graphics.Color.Black
                    articleTarget -> articleContainerPageColor.value
                    generalContainer -> webContainerPageColor.value
                    else -> liveContainerPageColor.value
                } },
                durationMillis = if (generalContainer) SETTINGS_CONTAINER_DURATION_MILLIS
                    else videoSharedTransitionDurationMillis.coerceAtLeast(1),
                // 专栏与番剧播放页复用视频卡片的导航时钟、曲线与景深。
                videoCardMotionEnabled = articleTarget || bangumiPlayerTarget,
                cardBackgroundEffectsEnabled = articleTarget || bangumiPlayerTarget ||
                    target is BiliPaiNavKey.Live || target is BiliPaiNavKey.LiveAreaDetail,
                isolateContentDrawing = target is BiliPaiNavKey.Live,
                useSharedSnapshotBlur = target is BiliPaiNavKey.Live,
                realtimeBlurEnabled = if (generalContainer) settingsContainerRealtimeBlurEnabled
                    else videoTransitionRealtimeBlurEnabled,
                isLightBackground = { liveContainerIsLightBackground.value },
                blurMotionTierProvider = navBlurMotionTierProvider,
            )
        }
    }
    val liveContainerTransitions = if (!liveContainerEnabled) emptyMap() else
        availableLiveContainerTransitions.filterKeys { target ->
            (target is BiliPaiNavKey.BangumiPlayer || target is BiliPaiNavKey.Live || target is BiliPaiNavKey.LiveAreaDetail || target is BiliPaiNavKey.ArticleDetail || target is BiliPaiNavKey.DynamicDetail || target is BiliPaiNavKey.Web || target is BiliPaiNavKey.Space || target is BiliPaiNavKey.BangumiDetail) && isSettingsContainerBoundary(
                enabled = true,
                backStack = stackSnapshot,
                target = target,
                owner = liveContainerSnapshot[target]?.owner,
            )
        }
    LaunchedEffect(stackSnapshot, liveContainerSnapshot.keys) {
        liveContainerSources.sources.keys.retainAll(stackSnapshot.toSet())
    }
    val currentKey = stackSnapshot.lastOrNull()
    val latestOnBack by rememberUpdatedState(onBack)
    val latestPrepareReturn by rememberUpdatedState(onPrepareVideoCardSharedReturn)
    val latestPrepareGesture by rememberUpdatedState(onPrepareVideoCardGesture)
    val latestRelatedReturn by rememberUpdatedState(onRelatedVideoDetailReturned)
    val latestPreferWholeCardReturn by rememberUpdatedState(preferWholeCardReturn)
    val cardMorphMode = resolveBiliPaiVideoCardMorphMode(
        cardTransitionEnabled = videoCardTransitionEnabled,
        reduceMotion = reduceMotion,
        sourceRoute = sourceMetadata.sourceRoute,
        hasUsableSourceBounds = sourceMetadata.sourceBounds
            ?.let { it.width > 1f && it.height > 1f } == true,
    )
    val cardMorphAvailable = cardMorphMode != BiliPaiVideoCardMorphMode.NONE
    var relatedReturnTransitionObserved by remember(videoSourceGeneration) { mutableStateOf(false) }
    val style = if (reduceMotion) {
        // NONE 仅关闭预测跟手，仍有普通侧滑；减少动态效果使用真正的零时长过渡。
        BiliPaiPredictiveBackAnimationStyle.NO_TRANSITION
    } else {
        predictiveBackAnimationStyle
    }
    // NONE 仍播放普通页面返回；只有零时长过渡无需等待导航暴露阶段。
    val videoReturnAnimated = cardMorphAvailable || style != BiliPaiPredictiveBackAnimationStyle.NO_TRANSITION
    val performBack = remember(
        backStack,
        cardMorphAvailable,
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
            latestOnBack()
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
        navBlurMotionTierProvider,
    ) {
        biliPaiMiuixNavTransition(
            animation = style,
            exitDirection = predictiveBackExitDirection,
            isLightBackground = isLightBackground,
            miuixTransitionBlurEnabled = miuixTransitionBlurEnabled,
            miuixPredictiveBackMaxProgressPercent =
                miuixPredictiveBackMaxProgressPercent,
            blurMotionTierProvider = navBlurMotionTierProvider,
        )
    }
    val predictiveBackExcludedTransition = remember(
        globalTransition,
        style,
        predictiveBackExitDirection,
        isLightBackground,
        miuixTransitionBlurEnabled,
        navBlurMotionTierProvider,
    ) {
        if (shouldUseMiuixPredictiveBackProgress(style, enabled = true)) {
            biliPaiMiuixNavTransition(
                animation = BiliPaiPredictiveBackAnimationStyle.MIUIX,
                exitDirection = predictiveBackExitDirection,
                isLightBackground = isLightBackground,
                miuixTransitionBlurEnabled = miuixTransitionBlurEnabled,
                miuixPredictiveBackProgressEnabled = false,
                blurMotionTierProvider = navBlurMotionTierProvider,
            )
        } else {
            globalTransition
        }
    }
    // A restored parent session must not keep the departed child's scope at depth -1.
    val motionDurationScale = androidx.compose.runtime.rememberCoroutineScope()
        .coroutineContext[androidx.compose.ui.MotionDurationScale]
    val videoCardTransitionProgress = remember(sourceMetadata.sourceKey, videoSourceGeneration, motionDurationScale) {
        MiuixVideoCardTransitionProgress { motionDurationScale?.scaleFactor ?: 1f }
    }
    // 卡片形变开启时优先使用专属过渡，缺少来源时使用固定回退；关闭后使用当前页面过渡。
    val videoFallbackTransition = resolveVideoCardFallbackTransition(
        cardTransitionEnabled = videoCardTransitionEnabled,
        animationStyle = style,
        globalTransition = globalTransition,
        cardEnabledFallback = if (reduceMotion) globalTransition else
            top.yukonga.miuix.kmp.nav.transition.NavTransitions.MiuixDefault,
    )
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
        sourceMetadata.sourceLayout,
        videoSharedTransitionDurationMillis,
        heroMotion,
        videoCardTransitionProgress,
        observedVideoFallbackTransition,
        videoCardContentScale,
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
                heroMotionSpec = heroMotion,
                returningProvider = returningProvider,
                deviceCornerDp = effectiveDeviceCornerDp,
                sourceLayout = sourceMetadata.sourceLayout,
            )
        } else {
            observedVideoFallbackTransition
        }
    }
    val fullscreenVideoCardTransition = remember(
        cardMorphAvailable,
        sourceMetadata.sourceBounds,
        sourceMetadata.sourceCornerDp,
        sourceMetadata.sourceLayout,
        videoSharedTransitionDurationMillis,
        heroMotion,
        videoCardTransitionProgress,
        observedVideoFallbackTransition,
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
                heroMotionSpec = heroMotion,
                returningProvider = returningProvider,
                deviceCornerDp = effectiveDeviceCornerDp,
                sourceLayout = sourceMetadata.sourceLayout,
            )
        } else {
            observedVideoFallbackTransition
        }
    }

    DisposableEffect(cardMorphAvailable, videoCardClock, videoCardTransitionProgress) {
        videoCardClock.bindNavigationDriver(
            if (cardMorphAvailable) ({ videoCardTransitionProgress.effectsDepthOrNull() }) else null,
            if (cardMorphAvailable) ({ videoCardTransitionProgress.maskDepthOrNull() }) else null,
        )
        onDispose { videoCardClock.bindNavigationDriver(null) }
    }
    // 卡片转场唯一逐帧驱动：详情 entry 的 transformEntry 会在同一帧挂多层 graphicsLayer，
    // 每层都读 visualDepth 且该函数会推进状态机。这里每帧只在这里打一次帧序号，
    // 让同帧内的重复读取走帧内记忆（见 MiuixVideoCardTransitionProgress.visualDepthMemoized），
    // 避免状态机同帧被推进多次。
    //
    // 只在**会动**的相位打拍：OPENING / RETURNING 才有逐帧插值。HELD 是稳态（用户正在看
    // 详情），此时不持有帧回调，避免长时间播放期间空转。帧序号跨帧单调递增，因此每次进入
    // 活动相位都从新序号开始，不会命中上一段的缓存。
    val hostVisualActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    LaunchedEffect(cardMorphAvailable, videoCardTransitionProgress, videoCardClock, hostVisualActive) {
        if (!cardMorphAvailable || !hostVisualActive) return@LaunchedEffect
        snapshotFlow {
            when (videoCardClock.phase) {
                VideoCardTransitionBackgroundPhase.OPENING,
                VideoCardTransitionBackgroundPhase.RETURNING,
                -> true
                // HELD 稳态一般不需要逐帧，但预测返回手势/取消恢复期间仍会逐帧变化，
                // 此时也要打拍，才能让同帧多层读走同一份采样。
                else -> videoCardTransitionProgress.isGestureInProgress() ||
                    videoCardClock.gestureRestoreInProgress
            }
        }.collectLatest { active ->
            if (!active) return@collectLatest
            while (true) {
                withFrameNanos { videoCardTransitionProgress.advanceTransitionFrame() }
            }
        }
    }
    var previousStack by remember { mutableStateOf(stackSnapshot) }
    LaunchedEffect(stackSnapshot, cardMorphAvailable) {
        val previous = previousStack
        previousStack = stackSnapshot
        if (!cardMorphAvailable) {
            videoCardClock.snapClearAndIdle()
            if (!stackSnapshot.any { it is BiliPaiNavKey.VideoDetail }) {
                CardPositionManager.clearNativeVideoCardLayers()
            }
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
    LaunchedEffect(
        cardMorphAvailable,
        videoCardTransitionProgress,
        heroMotion,
        sourceMetadata.sourceKey,
        currentKey,
    ) {
        if (!cardMorphAvailable) return@LaunchedEffect
        fun finishCardReturn() {
            videoCardClock.landingFeedback.publish(
                sourceKey = sourceMetadata.sourceKey,
                sourceLayout = sourceMetadata.sourceLayout,
                committedReturn = videoCardClock.phase == VideoCardTransitionBackgroundPhase.RETURNING,
            )
            videoCardClock.followNavigationDriver(VideoCardTransitionSettleState.Idle)
            videoCardTransitionProgress.clear()
        }
        // Coarse states only: no frame-rate composition reads or competing fallback jobs.
        snapshotFlow { videoCardTransitionProgress.settleStateOrNull() }.collect { state ->
            if (state == null) {
                if (isCardMorphDestinationNavKey(currentKey)) return@collect
                if (videoCardClock.phase == VideoCardTransitionBackgroundPhase.IDLE) return@collect
                // A pop can dispose its final transition scope without a last Idle transform.
                // Give NavDisplay one frame to bind any outgoing scope, then release a retained
                // HELD/RETURNING phase once the card destination is no longer on top.
                withFrameNanos { }
                if (
                    videoCardTransitionProgress.settleStateOrNull() == null &&
                    !isCardMorphDestinationNavKey(currentKey)
                ) {
                    finishCardReturn()
                }
                return@collect
            }
            if (state == VideoCardTransitionSettleState.Idle) {
                // LiveNavTransitionScope reads the shared navigation presentation even after
                // its video entry leaves. Release it before another route reuses that driver.
                finishCardReturn()
            } else {
                videoCardClock.followNavigationDriver(state, videoCardTransitionProgress.releaseVelocity())
            }
            VideoCardTransitionDiagnostics.onMotionPhase(
                state, heroMotion, sourceMetadata.sourceLayout, diagnosticConfiguration,
            )
        }
    }

    // Retain click-time pixels for the entire detail round trip, including child pages and
    // cancelled back gestures. An Idle driver alone does not prove the owning entry has left.
    val sourceCardOwnerRetained = stackSnapshot.any { key ->
        key is BiliPaiNavKey.VideoDetail &&
            key.sourceRoute == sourceMetadata.sourceRoute &&
            sourceMetadata.sourceKey == "${key.sourceRoute}:${key.bvid}"
    }
    LaunchedEffect(videoCardClock, cardMorphAvailable, videoCardClock.phase, sourceCardOwnerRetained) {
        if (!cardMorphAvailable || sourceCardOwnerRetained ||
            videoCardClock.phase != VideoCardTransitionBackgroundPhase.IDLE
        ) {
            return@LaunchedEffect
        }
        val retiringLayer = CardPositionManager.lastClickedNativeCardLayer
        val retiringSourceKey = CardPositionManager.lastClickedVideoSourceKey
        withFrameNanos { }
        withFrameNanos { }
        if (videoCardClock.phase == VideoCardTransitionBackgroundPhase.IDLE &&
            CardPositionManager.lastClickedNativeCardLayer === retiringLayer &&
            CardPositionManager.lastClickedVideoSourceKey == retiringSourceKey
        ) {
            CardPositionManager.clearNativeVideoCardLayers()
        }
    }

    val videoCardSnapshotHandle = rememberVideoCardTransitionSnapshotHandle()
    val transitionMotionTier = if (reduceMotion) MotionTier.Reduced else MotionTier.Normal
    // Card depth blur and generic Miuix return blur have separate user controls.
    val effectiveVideoCardBlurEnabled = videoTransitionRealtimeBlurEnabled
    val videoCardProgressProvider = remember(
        cardMorphAvailable,
        videoCardClock,
        videoCardTransitionProgress,
    ) {
        {
            if (cardMorphAvailable) {
                videoCardTransitionProgress.depthOrNull() ?: videoCardClock.depthProgress()
            } else {
                videoCardClock.depthProgress()
            }
        }
    }
    val videoCardGestureProvider = remember(cardMorphAvailable, videoCardTransitionProgress) {
        { cardMorphAvailable && videoCardTransitionProgress.isGestureInProgress() }
    }
    val videoCardExposure = remember(
        videoCardClock,
        videoCardGestureProvider,
        videoCardTransitionProgress,
    ) {
        // Readable from composition or draw: only exposure boundaries propagate, never frames.
        derivedStateOf(structuralEqualityPolicy()) {
            val settleState = videoCardTransitionProgress.settleStateOrNull()
            val effectivePhase = when (settleState) {
                // Reveal the list slot on the same endpoint frame that removes the flying
                // entry; waiting for the clock collector can expose an empty slot for a frame.
                VideoCardTransitionSettleState.Idle -> VideoCardTransitionBackgroundPhase.IDLE
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
    val videoCardExposureProvider = remember(videoCardExposure) { { videoCardExposure.value } }
    val effectiveVideoCardExposure = videoCardExposureProvider()
    // Ordinary related-video slides have no card clock. Read their actual navigation settle
    // instead of restoring the parent's morph while the child is still moving off screen.
    val fallbackReturnExposure by remember(videoCardTransitionProgress) {
        derivedStateOf {
            when (videoCardTransitionProgress.settleStateOrNull()) {
                VideoCardTransitionSettleState.AutoReturn -> VideoCardTransitionExposure.Returning
                VideoCardTransitionSettleState.CancelRestore -> VideoCardTransitionExposure.Restoring
                VideoCardTransitionSettleState.InteractiveSeek -> VideoCardTransitionExposure.BackPreview
                VideoCardTransitionSettleState.Idle,
                VideoCardTransitionSettleState.Held,
                null -> VideoCardTransitionExposure.Idle
                else -> VideoCardTransitionExposure.Opening
            }
        }
    }
    val relatedReturnExposure = if (cardMorphAvailable) effectiveVideoCardExposure else fallbackReturnExposure
    LaunchedEffect(
        pendingRelatedReturnCount,
        relatedReturnRequestId,
        videoSourceGeneration,
        relatedReturnExposure,
        videoReturnAnimated,
    ) {
        val restoreDecision = resolveRelatedReturnSourceRestoreDecision(
            restorePending = pendingRelatedReturnCount > 0,
            transitionObserved = relatedReturnTransitionObserved,
            transitionAnimated = videoReturnAnimated,
            exposure = relatedReturnExposure,
        )
        relatedReturnTransitionObserved = restoreDecision.transitionObserved
        if (restoreDecision.shouldRestore) {
            // The nested source geometry remains immutable through the complete predictive
            // settle. Only arm the parent's older session after the navigation driver is idle.
            if (latestRelatedReturn(videoSourceGeneration, relatedReturnRequestId)) {
                relatedReturnTransitionObserved = false
            }
        }
    }
    LaunchedEffect(effectiveVideoCardExposure) {
        // 新点击可在旧 Idle effect 执行前同步开启会话，不能清空刚接手的背景层。
        if (shouldReleaseHostOwnedDepthLayer(effectiveVideoCardExposure) &&
            shouldReleaseHostOwnedDepthLayer(videoCardExposureProvider()) &&
            videoCardClock.phase == VideoCardTransitionBackgroundPhase.IDLE
        ) {
            videoCardSnapshotHandle.releaseSession()
        }
    }
    val currentBackTarget = stackSnapshot.getOrNull(stackSnapshot.lastIndex - 1)
    // 只以真正会改变「被捕获数据」的值作为键。provider 一律是上面已 remember 的稳定引用，
    // 把它们放进键里会让任意一次重组都重建整个 state —— 下游模糊/缩放/圆角/scrim effect
    // 全部换新实例，本帧参数不连续，视觉上就是「闪一下」。state 只捕获引用，读值发生在
    // 下游 draw/graphicsLayer，因此不需要随 provider 重建。
    val transitionBackgroundState = remember(
        sourceMetadata.sourceKey,
        sourceMetadata.sourceRoute,
        sourceMetadata.sourceCornerDp,
        sourceMetadata.sourceBounds,
        videoCardSnapshotHandle,
        transitionMotionTier,
        isLightBackground,
        effectiveVideoCardBlurEnabled,
    ) {
        VideoCardTransitionBackgroundState(
            progressProvider = videoCardProgressProvider,
            maskProgressProvider = { videoCardClock.maskProgress() },
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
            realtimeBlurEnabledProvider = { effectiveVideoCardBlurEnabled },
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
        sourceMetadata.sourceInfoBounds,
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
            sourceInfoBoundsProvider = { sourceMetadata.sourceInfoBounds },
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
        miuixTransitionBlurEnabled,
    ) {
        PredictiveBackBackgroundState(
            progressProvider = {
                val blurEnabled = miuixTransitionBlurEnabled
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
    val videoCardBackingColor = resolveMiuixVideoCardBackingColor(sourceMetadata.sourceRoute, backdropColor)
    val effects = remember(
        navCornerRadius,
        roundAllCorners,
        enableHostCornerClip,
        hostDimAmount,
        backdropColor,
        videoCardMorphOwnsCorners,
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
            // The fixed host backing must sit BELOW the retained source snapshot. A second
            // opaque backing inside NavDisplay would cover that snapshot on its preparation frame.
            backdropColor = if (videoCardMorphOwnsCorners) Color.Unspecified else backdropColor,
        )
    }
    // 全屏滑动返回默认关闭（仅系统边缘预测返回），可在设置中开启。
    // 开启后仅对列表/设置等纵向页面生效，播放器、详情、WebView 等
    // 横滑冲突页面始终禁用（见 BiliPaiNavEntryProvider）。
    val fullScreenSwipeBackEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getFullScreenSwipeBackEnabled(context) }
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
    val clickToPlayEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getClickToPlay(context) }
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.SettingsManager.getClickToPlaySync(LocalContext.current)
        )
    val dynamicImagePreviewTextVisible by remember(context) { com.android.purebilibili.core.store.SettingsManager.getDynamicImagePreviewTextVisible(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val dynamicCommentPreviewVisible by remember(context) { com.android.purebilibili.core.store.home.DynamicSettingsStore.observeCommentPreviewVisible(context) }
        .collectAsStateWithLifecycle(initialValue = false)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                settingsContainerSources.hostOrigin = coordinates.positionInRoot()
                settingsContainerSources.hostSize = coordinates.size
                liveContainerSources.hostOrigin = coordinates.positionInRoot()
                liveContainerSources.hostSize = coordinates.size
            }
            .background(
                if (videoCardMorphOwnsCorners) {
                    videoCardBackingColor
                } else if (globalWallpaperVisible) {
                    Color.Transparent
                } else {
                    AppSurfaceTokens.groupedListContainer()
                }
            ),
    ) {
        VideoCardTransitionHostDepthLayer(
            enabled = cardMorphAvailable &&
                shouldUseHostOwnedVideoCardTransitionSnapshot(sourceMetadata.sourceRoute),
            snapshotHandle = videoCardSnapshotHandle,
            progressProvider = videoCardProgressProvider,
            phaseProvider = { videoCardClock.phase },
            exposureProvider = videoCardExposureProvider,
            isGestureRestoreInProgressProvider = { videoCardClock.gestureRestoreInProgress },
            motionTierProvider = { transitionMotionTier },
            isLightBackgroundProvider = { isLightBackground },
            realtimeBlurEnabledProvider = { effectiveVideoCardBlurEnabled },
            sourceBoundsProvider = { sourceMetadata.sourceBounds },
            maskProgressProvider = { videoCardClock.maskProgress() },
        )
        @Suppress("UNCHECKED_CAST")
        NavDisplay(
            backStack = backStack as NavBackStack,
            onBack = performBack,
            onBackStarted = {
                if (cardMorphAvailable && backStack.lastOrNull() is BiliPaiNavKey.VideoDetail) {
                    latestPrepareGesture()
                }
            },
            transition = globalTransition,
            effects = effects,
            backCompletionPolicy = if (cardMorphAvailable) VideoCardBackCompletionPolicy else null,
        ) {
            biliPaiNavEntries(
                swipeBackDirection = swipeBackDirection,
                settingsBackStack = stackSnapshot,
                settingsPersistentPanes = settingsPersistentPanes,
                settingsContainerTransitions = settingsContainerTransitions,
                settingsMorphFallback = if (settingsContainerEnabled) {
                    top.yukonga.miuix.kmp.nav.transition.NavTransitions.MiuixDefault
                } else null,
                liveContainerTransitions = liveContainerTransitions,
                activeMainHostRoute = activeMainHostRoute,
                pageTransition = globalTransition,
                predictiveBackExcludedTransition = predictiveBackExcludedTransition,
                videoCardTransition = videoCardTransition,
                fullscreenVideoCardTransition = fullscreenVideoCardTransition,
            ) { key ->
                // 返回尚未落位时可开始另一张卡片的转场。旧 entry 保留自身的文字、
                // 几何和进度驱动，不能因同样来自首页而接收新卡片的快照。
                var retainedCardTransition by remember(key) {
                    mutableStateOf(MiuixVideoCardTransitionState())
                }
                val entryCardTransitionState = resolveVideoCardEntryTransitionState(
                    key = key,
                    source = sourceMetadata,
                    current = miuixCardTransitionState,
                    retained = retainedCardTransition,
                )
                SideEffect { retainedCardTransition = entryCardTransitionState }
                // Freeze this per entry so popping the key cannot remove its backing during exit.
                val opaqueVideoChild = remember(key) {
                    shouldUseOpaqueVideoChildBackground(key, stackSnapshot)
                }
                // 每个直播 entry 固定表面类型，返回动画或目录清理时不重新绑定播放器。
                val liveContainerEntry = remember(key) { liveContainerTransitions.containsKey(key) }
                BiliPaiMiuixNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = performBack,
                ) {
                    CompositionLocalProvider(
                        com.android.purebilibili.core.ui.LocalNativeCardNavigate provides onOpenCardContainer,
                        com.android.purebilibili.core.ui.LocalNativeArticleCardMotionEnabled provides liveContainerEnabled,
                        com.android.purebilibili.feature.space.LocalShopContainerMotionEnabled provides
                            (liveContainerEnabled && settingsContainerMorphEnabled),
                        com.android.purebilibili.feature.live.LocalLiveCardContainerSources provides
                            (if (liveContainerEnabled) liveContainerSources else null),
                        com.android.purebilibili.feature.live.LocalLiveCardContainerOwner provides key,
                        com.android.purebilibili.feature.live.LocalLiveCardContainerActive provides liveContainerEntry,
                        com.android.purebilibili.feature.settings.LocalSettingsContainerSources provides
                            (if (settingsContainerEnabled) settingsContainerSources else null),
                        com.android.purebilibili.feature.settings.LocalSettingsContainerOwner provides key,
                        LocalVideoDetailNavigationTransitionScope provides LocalNavTransitionScope.current,
                        LocalGlobalWallpaperBackdropVisible provides
                            (globalWallpaperVisible && !opaqueVideoChild &&
                                shouldRenderGlobalHomeWallpaperBackdrop(
                                    effectScope = HomeWallpaperEffectScope.GLOBAL,
                                    currentRoute = key.toLegacyRoute(),
                                    mainHostTabRoute = activeMainHostRoute,
                                )),
                        LocalVideoCardSharedElementSourceRoute provides key.toLegacyRoute(),
                        LocalVideoCardTransitionClock provides videoCardClock,
                        LocalVideoCardTransitionBackgroundState provides transitionBackgroundState,
                        LocalMiuixVideoCardTransitionState provides entryCardTransitionState,
                        LocalPredictiveBackBackgroundState provides predictiveBackBackgroundState,
                        LocalClickToPlayEnabled provides clickToPlayEnabled,
                        LocalDynamicImagePreviewTextVisible provides dynamicImagePreviewTextVisible,
                        LocalDynamicCommentPreviewVisible provides dynamicCommentPreviewVisible,
                        LocalSharedTransitionEnabled provides videoCardTransitionEnabled,
                    ) {
                        ProvideMiuixNavViewModelApplicationExtras(application) {
                            Box(
                                modifier = Modifier.fillMaxSize().then(
                                    if (opaqueVideoChild) {
                                        Modifier.background(AppSurfaceTokens.groupedListContainer().copy(alpha = 1f))
                                    } else if (LocalGlobalWallpaperBackdropVisible.current) {
                                        Modifier.globalWallpaperAwareBackground()
                                    } else Modifier
                                ),
                            ) { content(key) }
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
    com.android.purebilibili.core.ui.animation.ProvideNavigationEntranceState(content)
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
