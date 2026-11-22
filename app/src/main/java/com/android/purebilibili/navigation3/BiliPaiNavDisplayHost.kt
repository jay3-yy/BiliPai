package com.android.purebilibili.navigation3

import com.android.purebilibili.core.performance.performanceCaptureScroll
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
import androidx.navigation3.runtime.NavBackStack
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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
import com.android.purebilibili.feature.settings.LocalSettingsContainerOwner
import com.android.purebilibili.feature.settings.LocalSettingsContainerSources
import com.android.purebilibili.feature.settings.SettingsSharedContainerSources
import com.android.purebilibili.feature.settings.screen.settingsHasPersistentPanes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.feature.home.shouldRenderGlobalHomeWallpaperBackdrop
import com.android.purebilibili.core.store.HomeWallpaperEffectScope
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
import com.android.purebilibili.core.ui.wallpaper.sharedGlobalWallpaperBackground
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.transition.LocalSystemVideoCardCaptureEnabled
import com.android.purebilibili.core.ui.LocalVideoDetailNavigationTransitionScope
import com.android.purebilibili.core.ui.NavigationEntryTransition
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
import com.android.purebilibili.core.ui.transition.rememberPredictiveBackBackgroundState
import com.android.purebilibili.core.ui.transition.VideoCardTransitionClock
import com.android.purebilibili.core.ui.transition.VideoCardTransitionHostDepthLayer
import com.android.purebilibili.core.ui.transition.rememberVideoCardTransitionSnapshotHandle
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionExposure
import com.android.purebilibili.core.ui.transition.resolveVideoHeroMotionSpec
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.VideoCardTransitionSettleState
import com.android.purebilibili.core.ui.transition.shouldPublishVideoCardReturnLanding
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
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.LocalNavEntryTransition
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavRole

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
    backStack: NavBackStack<BiliPaiNavKey>,
    activeMainHostRoute: String? = null,
    cardTransitionEnabled: Boolean = true,
    videoCardTransitionEnabled: Boolean = cardTransitionEnabled,
    systemVideoCardCaptureEnabled: Boolean = false,
    systemWindowEntry: BiliPaiNavKey? = null,
    videoMiniPlayerDeparture: Boolean = false,
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
    sourceMetadata: BiliPaiNavSourceMetadata,
    programmaticBackDispatcher: BiliPaiProgrammaticBackDispatcher,
    preferWholeCardReturn: Boolean = false,
    allowFullScreenSwipeBack: Boolean = true,
    onBack: () -> Unit,
    onInterceptVideoBack: () -> Boolean = { false },
    onOpenCardContainer: (BiliPaiNavKey) -> Unit = { backStack.add(it) },
    onPrepareVideoCardSharedReturn: () -> Boolean = { false },
    onPrepareVideoCardGesture: () -> Unit = {},
    onStartMiniPlayerBackGesture: (() -> Float) -> Boolean = { false },
    onCancelVideoCardGesture: () -> Unit = {},
    pendingRelatedReturnCount: Int = 0,
    relatedReturnRequestId: Long = 0L,
    onRelatedVideoDetailReturned: (Long, Long) -> Boolean = { _, _ -> false },
    videoSourceGeneration: Long = 0L,
    isTopEntryNavigationSettled: Boolean = false,
    forceCardTransitionResetSignal: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable (BiliPaiNavKey) -> Unit,
) = BoxWithConstraints(modifier = modifier) {
    val context = LocalContext.current
    val stackSnapshot = backStack.toList()
    val fullScreenSwipeBackEnabled by remember(context) {
        com.android.purebilibili.core.store.SettingsManager.getFullScreenSwipeBackEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = false)
    val predictiveGestureEnabled = com.android.purebilibili.core.ui.LocalPredictiveBackGestureEnabled.current
    val settingsPersistentPanes = settingsHasPersistentPanes()
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
    val settingsContainerEnabled = settingsContainerMorphEnabled &&
        !settingsPersistentPanes && !reduceMotion
    // 重新开启后只使用新点击登记的来源，已在栈中的页面不会恢复旧的共享边界。
    val settingsContainerSources = remember(settingsContainerEnabled) { SettingsSharedContainerSources() }
    // 颜色按当前主题读取，不能以调色动画的每一帧重建容器转场。
    val settingsContainerPageColor = rememberUpdatedState(AppSurfaceTokens.groupedListContainer())
    val settingsContainerSnapshot = settingsContainerSources.sources.toMap()
    val availableSettingsContainerTransitions = remember(
        settingsContainerSnapshot, videoTransitionRealtimeBlurEnabled, navBlurMotionTierProvider,
        stackSnapshot,
    ) {
        settingsContainerSnapshot.mapValues { (target, source) ->
            SettingsSharedContainerTransition(
                origin = source,
                destinationZIndex = stackSnapshot.indexOf(target).toFloat(),
                pageColor = { settingsContainerPageColor.value },
                realtimeBlurEnabled = videoTransitionRealtimeBlurEnabled,
                blurMotionTier = navBlurMotionTierProvider,
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
        videoTransitionRealtimeBlurEnabled, navBlurMotionTierProvider,
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
                useSharedSnapshotBlur = target is BiliPaiNavKey.Live,
                realtimeBlurEnabled = videoTransitionRealtimeBlurEnabled,
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
    val performanceWindow = LocalView.current.rootView
    val performanceDestination = remember(currentKey, activeMainHostRoute.takeIf { currentKey == BiliPaiNavKey.MainHost }) { Any() }
    SideEffect {
        com.android.purebilibili.core.performance.PerformanceCapture.setPage(
            performanceWindow,
            if (currentKey == BiliPaiNavKey.MainHost) activeMainHostRoute else currentKey?.toLegacyRoute(),
            performanceDestination,
        )
    }
    val latestOnBack by rememberUpdatedState(onBack)
    val latestInterceptVideoBack by rememberUpdatedState(onInterceptVideoBack)
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
        back@{
            val leavingKey = backStack.lastOrNull()
            if (leavingKey is BiliPaiNavKey.VideoDetail) {
                if (latestInterceptVideoBack()) return@back
                VideoCardTransitionDiagnostics.measureWork("return_prepare") { latestPrepareReturn() }
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
    // 设备未上报圆角时保留方角，与透明窗口及系统窗口的实际边界一致。
    val effectiveDeviceCornerDp = navCornerRadius
    val sourceCoverCornerDp = sourceMetadata.sourceChromeSnapshot?.coverPresentation?.cornerRadiusDp
    val videoCardTransition = remember(
        videoMiniPlayerDeparture,
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
        sourceCoverCornerDp,
    ) {
        if (videoMiniPlayerDeparture) {
            com.android.purebilibili.navigation3.predictiveback.pagePresetNavTransition(
                BiliPaiPredictiveBackAnimationStyle.FADE_IN,
            )
        } else if (cardMorphAvailable) {
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
                sourceCoverCornerDp = sourceCoverCornerDp,
            )
        } else {
            observedVideoFallbackTransition
        }
    }
    val fullscreenVideoCardTransition = remember(
        videoMiniPlayerDeparture,
        cardMorphAvailable,
        sourceMetadata.sourceBounds,
        sourceMetadata.sourceCornerDp,
        sourceMetadata.sourceLayout,
        videoSharedTransitionDurationMillis,
        heroMotion,
        videoCardTransitionProgress,
        observedVideoFallbackTransition,
        effectiveDeviceCornerDp,
        sourceCoverCornerDp,
    ) {
        if (videoMiniPlayerDeparture) {
            com.android.purebilibili.navigation3.predictiveback.pagePresetNavTransition(
                BiliPaiPredictiveBackAnimationStyle.FADE_IN,
            )
        } else if (cardMorphAvailable) {
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
                sourceCoverCornerDp = sourceCoverCornerDp,
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
    // 落位冲量是否已在本轮返回里发布过：morph 末段提前发布一次后，
    // Idle 收尾与兜底路径不再重发，避免替换事件把压缩弹簧重置回起点。
    val landingPublishedForReturn = remember { mutableStateOf(false) }
    LaunchedEffect(
        cardMorphAvailable,
        videoCardTransitionProgress,
        heroMotion,
        sourceMetadata.sourceKey,
        currentKey,
    ) {
        if (!cardMorphAvailable) return@LaunchedEffect
        fun finishCardReturn() {
            if (!landingPublishedForReturn.value) {
                videoCardClock.landingFeedback.publish(
                    sourceKey = sourceMetadata.sourceKey,
                    sourceLayout = sourceMetadata.sourceLayout,
                    committedReturn = videoCardClock.phase == VideoCardTransitionBackgroundPhase.RETURNING,
                    releaseVelocity = videoCardTransitionProgress.releaseVelocity(),
                )
            }
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
    // 落位冲量提前发布：提交返回的 morph 进入末段（剩余行程低于阈值）就发布事件，
    // 源卡压缩弹簧提前起跑，峰位压住落位尾巴；飞行壳卸层帧即为压缩近峰帧。
    // 只读 settle 相位与缓存的 morph 帧，不推进状态机以外的任何写路径。
    LaunchedEffect(
        cardMorphAvailable,
        videoCardTransitionProgress,
        sourceMetadata.sourceKey,
    ) {
        if (!cardMorphAvailable) return@LaunchedEffect
        snapshotFlow {
            val state = videoCardTransitionProgress.settleStateOrNull()
            val depth = if (state == VideoCardTransitionSettleState.AutoReturn) {
                videoCardTransitionProgress.depthOrNull()
            } else {
                null
            }
            state to depth
        }.collect { (state, depth) ->
            if (state != VideoCardTransitionSettleState.AutoReturn) {
                landingPublishedForReturn.value = false
                return@collect
            }
            if (landingPublishedForReturn.value) return@collect
            if (depth != null && shouldPublishVideoCardReturnLanding(depth)) {
                landingPublishedForReturn.value = true
                videoCardClock.landingFeedback.publish(
                    sourceKey = sourceMetadata.sourceKey,
                    sourceLayout = sourceMetadata.sourceLayout,
                    committedReturn = videoCardClock.phase == VideoCardTransitionBackgroundPhase.RETURNING,
                    releaseVelocity = videoCardTransitionProgress.releaseVelocity(),
                )
            }
        }
    }
    val stackHasCardMorphDestination = hasCardMorphDestination(stackSnapshot)
    val cardTransitionProgressSettle = videoCardTransitionProgress.settleStateOrNull()
    val cardTransitionGestureActive = videoCardTransitionProgress.isGestureInProgress()
    val cardTransitionDriverIdle =
        videoCardClock.phase == VideoCardTransitionBackgroundPhase.IDLE &&
            cardTransitionProgressSettle == null &&
            videoCardClock.settleState == VideoCardTransitionSettleState.Idle
    // Plan A: Reconcile from current state when no card morph destination remains on the stack
    // and the host navigation has already settled, catching dropped NavDisplay Idle transforms.
    LaunchedEffect(
        cardMorphAvailable,
        stackHasCardMorphDestination,
        isTopEntryNavigationSettled,
        cardTransitionDriverIdle,
        cardTransitionGestureActive,
    ) {
        if (!cardMorphAvailable) return@LaunchedEffect
        if (!shouldResetStuckCardTransition(
                hasCardMorphDestinationOnStack = stackHasCardMorphDestination,
                isCardTransitionIdle = cardTransitionDriverIdle,
                isCardGestureInProgress = cardTransitionGestureActive,
                isHostNavigationSettled = isTopEntryNavigationSettled,
            )
        ) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        val stillIdle = videoCardClock.phase == VideoCardTransitionBackgroundPhase.IDLE &&
            videoCardTransitionProgress.settleStateOrNull() == null &&
            videoCardClock.settleState == VideoCardTransitionSettleState.Idle
        if (shouldResetStuckCardTransition(
                hasCardMorphDestinationOnStack = hasCardMorphDestination(backStack),
                isCardTransitionIdle = stillIdle,
                isCardGestureInProgress = videoCardTransitionProgress.isGestureInProgress(),
                isHostNavigationSettled = isTopEntryNavigationSettled,
            )
        ) {
            com.android.purebilibili.core.util.Logger.w(
                "BiliPaiNavDisplayHost",
                "Reconciling stale card transition state after stack settled: phase=${videoCardClock.phase}, settle=${videoCardTransitionProgress.settleStateOrNull()}",
            )
            videoCardClock.landingFeedback.publish(
                sourceKey = sourceMetadata.sourceKey,
                sourceLayout = sourceMetadata.sourceLayout,
                committedReturn = videoCardClock.phase == VideoCardTransitionBackgroundPhase.RETURNING,
            )
            videoCardClock.followNavigationDriver(VideoCardTransitionSettleState.Idle)
            videoCardTransitionProgress.clear()
        }
    }
    // Plan C: Source-level timeout watchdog if NavDisplay neither settled nor delivered Idle.
    LaunchedEffect(
        cardMorphAvailable,
        stackHasCardMorphDestination,
        cardTransitionDriverIdle,
        cardTransitionGestureActive,
    ) {
        if (!cardMorphAvailable) return@LaunchedEffect
        if (!shouldForceResetCardTransitionOnTimeout(
                hasCardMorphDestinationOnStack = stackHasCardMorphDestination,
                isCardTransitionIdle = cardTransitionDriverIdle,
                isCardGestureInProgress = cardTransitionGestureActive,
            )
        ) {
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(CARD_TRANSITION_STUCK_RECOVERY_TIMEOUT_MS)
        val stillIdle = videoCardClock.phase == VideoCardTransitionBackgroundPhase.IDLE &&
            videoCardTransitionProgress.settleStateOrNull() == null &&
            videoCardClock.settleState == VideoCardTransitionSettleState.Idle
        if (shouldForceResetCardTransitionOnTimeout(
                hasCardMorphDestinationOnStack = hasCardMorphDestination(backStack),
                isCardTransitionIdle = stillIdle,
                isCardGestureInProgress = videoCardTransitionProgress.isGestureInProgress(),
            )
        ) {
            com.android.purebilibili.core.util.Logger.w(
                "BiliPaiNavDisplayHost",
                "Card transition timed out (${CARD_TRANSITION_STUCK_RECOVERY_TIMEOUT_MS}ms) without destination on stack; forcing Idle (phase=${videoCardClock.phase}, settle=${videoCardTransitionProgress.settleStateOrNull()})",
            )
            videoCardClock.followNavigationDriver(VideoCardTransitionSettleState.Idle)
            videoCardTransitionProgress.clear()
        }
    }
    LaunchedEffect(forceCardTransitionResetSignal) {
        if (forceCardTransitionResetSignal <= 0) return@LaunchedEffect
        if (!videoCardTransitionProgress.isGestureInProgress() &&
            !hasCardMorphDestination(backStack) &&
            (videoCardClock.phase != VideoCardTransitionBackgroundPhase.IDLE ||
                videoCardTransitionProgress.settleStateOrNull() != null ||
                videoCardClock.settleState != VideoCardTransitionSettleState.Idle)
        ) {
            com.android.purebilibili.core.util.Logger.w(
                "BiliPaiNavDisplayHost",
                "User interaction forced card transition reset (phase=${videoCardClock.phase}, settle=${videoCardTransitionProgress.settleStateOrNull()})",
            )
            videoCardClock.followNavigationDriver(VideoCardTransitionSettleState.Idle)
            videoCardTransitionProgress.clear()
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
    val relatedReturnExposure =
        if (cardMorphAvailable) effectiveVideoCardExposure else fallbackReturnExposure
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
    // 预测返回使用最新栈目标和手势进度；固定 provider 身份，避免 push/pop 重建入口包装。
    val predictiveBackBackgroundState = rememberPredictiveBackBackgroundState(
        progressProvider = {
            if (!miuixTransitionBlurEnabled || !isCardMorphDestinationNavKey(currentKey)) {
                0f
            } else {
                videoCardTransitionProgress.gestureBackProgress()
                    ?.takeIf { cardMorphAvailable }
                    ?.let { resolvePredictiveBackGestureBlurProgress(it) }
                    ?: 0f
            }
        },
        targetKey = currentBackTarget,
        motionTier = transitionMotionTier,
        isLightBackground = isLightBackground,
    )

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
    val swipeBackDirection = if (allowFullScreenSwipeBack && fullScreenSwipeBackEnabled && predictiveGestureEnabled) {
        when (LocalLayoutDirection.current) {
            LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
            LayoutDirection.Ltr -> NavSwipeDirection.LeftToRight
        }
    } else {
        NavSwipeDirection.None
    }
    val interceptPredictiveBack =
        (!predictiveGestureEnabled || style == BiliPaiPredictiveBackAnimationStyle.NONE) && backStack.size > 1
    val globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
    val hasSharedWallpaperBacking = LocalGlobalWallpaperBackdropSource.current != null
    val clickToPlayEnabled by remember(context) { com.android.purebilibili.core.store.SettingsManager.getClickToPlay(context) }
        .collectAsStateWithLifecycle(
            initialValue = remember(context) { com.android.purebilibili.core.store.SettingsManager.getClickToPlaySync(context) }
        )
    val dynamicImagePreviewTextVisible by remember(context) { com.android.purebilibili.core.store.SettingsManager.getDynamicImagePreviewTextVisible(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val dynamicCommentPreviewVisible by remember(context) { com.android.purebilibili.core.store.home.DynamicSettingsStore.observeCommentPreviewVisible(context) }
        .collectAsStateWithLifecycle(initialValue = false)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .performanceCaptureScroll()
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
            wallpaperBackdropVisible = globalWallpaperVisible,
        )
        // content / onOpenCardContainer 是上游每次重组都会新建的 lambda 实例，直接放进
        // remember 键会让下面的稳定化完全失效。用 rememberUpdatedState 包装成恒定引用：
        // 调用时经状态读取拿最新实例，捕获值不经过 entryProvider 重建链路传播。
        val latestOnOpenCardContainer by rememberUpdatedState(onOpenCardContainer)
        val stableOnOpenCardContainer =
            remember { { key: BiliPaiNavKey -> latestOnOpenCardContainer(key) } }
        val latestNavEntryContent by rememberUpdatedState(content)
        val stableNavEntryContent: @Composable (BiliPaiNavKey) -> Unit =
            remember { { key: BiliPaiNavKey -> latestNavEntryContent(key) } }
        // 返回能力只让返回处理器失效；返回事件执行时读取最新动作。
        // 不以每次 push/pop 的栈快照重建所有入口的内容包装函数。
        val latestEntryBack = rememberUpdatedState(performBack)
        val stableEntryBack = remember { { latestEntryBack.value() } }
        val entryBackEnabled = rememberUpdatedState(interceptPredictiveBack)
        // 容器资格只在入口首次挂载时冻结；栏目路由只由壁纸判断消费。
        // 两者不应因过滤目录或切换底栏而重建所有入口的注册内容。
        val latestLiveContainerTransitions = rememberUpdatedState(liveContainerTransitions)
        val latestMainHostRoute = rememberUpdatedState(activeMainHostRoute)
        // 内容 lambda 以前只作为 NavDisplay 的尾部字面量存在：宿主任何一次无关重组
        //（曝光边界、设置流、窗口焦点……）都会生成新实例，令 entryProvider 与全部
        // NavEntry 连带重建、所有在场 entry 的宿主跟着重组。这里按真实输入 remember，
        // 转场对象及内容实际使用的配置变化时才重建；栈变化由下方注册表处理。
        val navEntryContent: @Composable (BiliPaiNavKey) -> Unit = remember(
            stableOnOpenCardContainer,
            liveContainerEnabled,
            settingsContainerEnabled,
            settingsContainerMorphEnabled,
            settingsContainerSources,
            settingsContainerPageColor,
            liveContainerSources,
            latestLiveContainerTransitions,
            globalWallpaperVisible,
            hasSharedWallpaperBacking,
            latestMainHostRoute,
            videoCardClock,
            transitionBackgroundState,
            miuixCardTransitionState,
            predictiveBackBackgroundState,
            videoCardTransitionEnabled,
            systemVideoCardCaptureEnabled,
            systemWindowEntry,
            stableEntryBack,
            entryBackEnabled,
            backStack,
            sourceMetadata,
            clickToPlayEnabled,
            dynamicImagePreviewTextVisible,
            dynamicCommentPreviewVisible,
            application,
            stableNavEntryContent,
        ) {
            { key ->
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
                    shouldUseOpaqueVideoChildBackground(key, backStack)
                }
                // 静态壁纸回放本身就是不透明底图，可遮住栈底播放器。
                val entryWallpaperVisible = globalWallpaperVisible &&
                    (!opaqueVideoChild || hasSharedWallpaperBacking) &&
                    shouldRenderGlobalHomeWallpaperBackdrop(
                        effectScope = HomeWallpaperEffectScope.GLOBAL,
                        currentRoute = key.toLegacyRoute(),
                        mainHostTabRoute = latestMainHostRoute.value,
                    )
                // 每个直播 entry 固定表面类型，返回动画或目录清理时不重新绑定播放器。
                val liveContainerEntry = remember(key) { latestLiveContainerTransitions.value.containsKey(key) }
                ProvideBiliPaiMotionEntry(
                    interceptPredictiveBack = entryBackEnabled,
                    onBack = stableEntryBack,
                ) {
                    CompositionLocalProvider(
                        com.android.purebilibili.core.ui.LocalNativeCardNavigate provides stableOnOpenCardContainer,
                        com.android.purebilibili.core.ui.LocalNativeArticleCardMotionEnabled provides liveContainerEnabled,
                        com.android.purebilibili.feature.space.LocalShopContainerMotionEnabled provides
                            (liveContainerEnabled && settingsContainerMorphEnabled),
                        com.android.purebilibili.feature.live.LocalLiveCardContainerSources provides
                            (if (liveContainerEnabled) liveContainerSources else null),
                        com.android.purebilibili.feature.live.LocalLiveCardContainerOwner provides key,
                        com.android.purebilibili.feature.live.LocalLiveCardContainerActive provides liveContainerEntry,
                        LocalSettingsContainerSources provides
                            (if (settingsContainerEnabled) settingsContainerSources else null),
                        LocalSettingsContainerOwner provides key,
                        LocalGlobalWallpaperBackdropVisible provides entryWallpaperVisible,
                        LocalVideoCardSharedElementSourceRoute provides key.toLegacyRoute(),
                        LocalVideoCardTransitionClock provides videoCardClock,
                        LocalVideoCardTransitionBackgroundState provides transitionBackgroundState,
                        LocalMiuixVideoCardTransitionState provides entryCardTransitionState,
                        LocalPredictiveBackBackgroundState provides predictiveBackBackgroundState,
                        LocalClickToPlayEnabled provides clickToPlayEnabled,
                        LocalDynamicImagePreviewTextVisible provides dynamicImagePreviewTextVisible,
                        LocalDynamicCommentPreviewVisible provides dynamicCommentPreviewVisible,
                        LocalSharedTransitionEnabled provides videoCardTransitionEnabled,
                        LocalSystemVideoCardCaptureEnabled provides systemVideoCardCaptureEnabled,
                    ) {
                        ProvideNavViewModelApplicationExtras(application) {
                            val settingsTransition = LocalNavEntryTransition.current as? SettingsSharedContainerTransition
                            // 只在共享匹配真正可用时接管内容交接；来源失效的降级淡入
                            // 仍保留内容入场节奏，避免页面既无容器也无入场动画。
                            val useSettingsContainer = settingsTransition != null && settingsContainerEnabled &&
                                settingsTransition.origin.available
                            val replayWallpaperInsideContainer =
                                useSettingsContainer && entryWallpaperVisible && hasSharedWallpaperBacking
                            val containerBounds = if (settingsTransition != null && settingsContainerEnabled) {
                                settingsSharedContainerDestination(
                                    origin = settingsTransition.origin,
                                    navScope = LocalNavTransitionScope.current,
                                    // 全局壁纸固定在窗口中，展开容器时让来源底色淡出到壁纸。
                                    color = if (entryWallpaperVisible) {
                                        Color.Transparent
                                    } else {
                                        settingsContainerPageColor.value
                                    },
                                    destinationZIndex = settingsTransition.destinationZIndex,
                                    wallpaperBacking = if (replayWallpaperInsideContainer) {
                                        Modifier.sharedGlobalWallpaperBackground(settingsContainerPageColor.value)
                                    } else Modifier,
                                )
                            } else Modifier
                            Box(
                                modifier = Modifier.fillMaxSize().then(containerBounds).then(
                                    if (replayWallpaperInsideContainer) {
                                        Modifier
                                    } else if (entryWallpaperVisible) {
                                        Modifier.globalWallpaperAwareBackground()
                                    } else if (opaqueVideoChild) {
                                        Modifier.background(AppSurfaceTokens.groupedListContainer().copy(alpha = 1f))
                                    } else Modifier
                                ),
                            ) {
                                // 窗口首屏及共享容器的内容随各自的转场入场。
                                CompositionLocalProvider(
                                    com.android.purebilibili.core.ui.animation.LocalNavigationOwnsContentEntrance
                                        provides (useSettingsContainer || key == systemWindowEntry),
                                ) {
                                    stableNavEntryContent(key)
                                }
                            }
                        }
                    }
                }
            }
        }
        val routeMotion: top.yukonga.miuix.kmp.nav.core.NavEntryBuilder.() -> Unit = remember(
            swipeBackDirection,
            stackSnapshot,
            settingsPersistentPanes,
            settingsContainerTransitions,
            liveContainerTransitions,
            settingsContainerEnabled,
            activeMainHostRoute,
            globalTransition,
            predictiveBackExcludedTransition,
            videoCardTransition,
            fullscreenVideoCardTransition,
        ) {
            {
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
                )
            }
        }
        SettingsSharedTransitionLayout(enabled = settingsContainerEnabled) {
            BiliPaiAndroidXNavDisplay(
                backStack = backStack,
                onBack = performBack,
                onBackProgressStarted = { progress ->
                    if (backStack.lastOrNull() is BiliPaiNavKey.VideoDetail &&
                        !onStartMiniPlayerBackGesture(progress)
                    ) {
                        latestPrepareGesture()
                    }
                },
                onBackCancelled = {
                    if (backStack.lastOrNull() is BiliPaiNavKey.VideoDetail) onCancelVideoCardGesture()
                },
                transition = globalTransition,
                effects = effects,
                backCompletionPolicy = if (cardMorphAvailable && isCardMorphDestinationNavKey(currentKey)) {
                    VideoCardBackCompletionPolicy
                } else null,
                motion = routeMotion,
                content = navEntryContent,
            )
        }
    }
}

@Composable
private fun ProvideBiliPaiMotionEntry(
    interceptPredictiveBack: androidx.compose.runtime.State<Boolean>,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = interceptPredictiveBack.value,
        onBackCompleted = onBack,
    )
    ObserveNavEntryPerformance()
    val exposed = top.yukonga.miuix.kmp.nav.core.LocalNavEntryContentVisible.current
    val transitionScope = LocalNavTransitionScope.current
    val entryTransition = remember(transitionScope) {
        object : NavigationEntryTransition {
            override val isTop: Boolean get() = transitionScope.role == NavRole.Top
            override val isRunning: Boolean get() = transitionScope.isRunning
            override val isRemoving: Boolean get() = transitionScope.isRemoving
            override val relativeDepth: Float get() = transitionScope.relativeDepth
        }
    }
    // 材质共用曝光边界：遮挡后暂停图片与背景录制，返回手势露出时恢复。
    // 不以 RESUMED 替代曝光，否则转场中仍可见的两页会丢失背景采样。
    CompositionLocalProvider(
        LocalVideoDetailNavigationTransitionScope provides entryTransition,
        com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed provides
            (com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed.current && exposed),
        com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed provides
            (com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current && exposed),
    ) {
        com.android.purebilibili.core.ui.animation.ProvideNavigationEntranceState(
            isEntryReady = {
                com.android.purebilibili.core.ui.animation.isNavigationEntryReadyForEntrance(
                    transitionScope.role,
                    transitionScope.isRunning,
                )
            },
            content = content,
        )
    }
}

/** 转场采集只更新诊断状态，动画开始和结束不应使入口内容重新组合。 */
@Composable
private fun ObserveNavEntryPerformance() {
    val performanceOwner = remember { Any() }
    val performanceWindow = LocalView.current.rootView
    val performanceTransition = LocalNavTransitionScope.current
    val navigationTransitionRunning = performanceTransition.isRunning
    val performanceGestureActive by remember(performanceTransition) {
        derivedStateOf(structuralEqualityPolicy()) { performanceTransition.gesture != null }
    }
    val performancePhase = performanceTransition.settle?.phase?.name
    val performanceChange = if (performanceGestureActive || performancePhase == "Commit" || performancePhase == "Cancel") {
        "gesture_back"
    } else when (performanceTransition.change) {
        top.yukonga.miuix.kmp.nav.runtime.NavChange.None -> "none"
        top.yukonga.miuix.kmp.nav.runtime.NavChange.Push -> "push"
        top.yukonga.miuix.kmp.nav.runtime.NavChange.Pop -> "pop"
        is top.yukonga.miuix.kmp.nav.runtime.NavChange.MultiPush -> "multi_push"
        is top.yukonga.miuix.kmp.nav.runtime.NavChange.MultiPop -> "multi_pop"
        top.yukonga.miuix.kmp.nav.runtime.NavChange.Replace -> "replace"
        top.yukonga.miuix.kmp.nav.runtime.NavChange.ReplaceAll -> "replace_all"
    }
    SideEffect {
        com.android.purebilibili.core.performance.PerformanceCapture.setTransition(performanceWindow, performanceOwner, navigationTransitionRunning, performanceChange, performancePhase)
    }
    DisposableEffect(performanceWindow, performanceOwner) {
        onDispose { com.android.purebilibili.core.performance.PerformanceCapture.setTransition(performanceWindow, performanceOwner, false, disposed = true) }
    }
}

@Composable
private fun ProvideNavViewModelApplicationExtras(
    application: Application,
    content: @Composable () -> Unit,
) {
    val navEntryOwner = LocalViewModelStoreOwner.current
    if (navEntryOwner == null) {
        content()
        return
    }
    val patchedOwner = remember(navEntryOwner, application) {
        buildNavViewModelStoreOwner(navEntryOwner, application)
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides patchedOwner) {
        content()
    }
}

private fun buildNavViewModelStoreOwner(
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
