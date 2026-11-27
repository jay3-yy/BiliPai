package com.android.purebilibili.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.AccountSessionSnapshot
import com.android.purebilibili.core.store.AccountSessionStore
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.blur.FloatingChromeBackdropDemand
import com.android.purebilibili.core.ui.transition.NowPlayingBarHandoffState
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionChromeReveal
import com.android.purebilibili.core.ui.transition.videoCardTransitionChromeReveal
import com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
import com.android.purebilibili.feature.audio.screen.AudioNowPlayingBar
import com.android.purebilibili.feature.audio.screen.AudioNowPlayingBarPresenceHost
import com.android.purebilibili.feature.audio.screen.AudioNowPlayingCover
import com.android.purebilibili.feature.audio.screen.AudioNowPlayingBarState
import com.android.purebilibili.feature.dynamic.DynamicScrollRequest
import com.android.purebilibili.feature.home.HomeScrollRequest
import com.android.purebilibili.feature.home.HomeViewModel
import com.android.purebilibili.feature.home.components.BottomBarMatchedDockEdge
import com.android.purebilibili.feature.home.components.BottomBarMatchedDockVisibility
import com.android.purebilibili.feature.home.components.BottomBarUiSkinDecoration
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.FrostedBottomBar
import com.android.purebilibili.feature.home.components.LinkedDockNowPlayingSlot
import com.android.purebilibili.feature.home.components.LinkedDockPhase
import com.android.purebilibili.feature.home.components.LiquidGlassRenderConfig
import com.android.purebilibili.feature.profile.AccountSwitchDialog
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.player.PlaylistItem
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.legacyRouteToBiliPaiNavKey
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 拆出侧栏账号切换弹层与底部导航/独立听视频横条宿主，降低 AppNavigation 巨型 Composable 的 DEX 指令规模。
 */
@Composable
internal fun AppNavigationSidebarAccountSwitchDialogHost(
    context: Context,
    coroutineScope: CoroutineScope,
    snapshot: AccountSessionSnapshot?,
    homeViewModel: HomeViewModel,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    onSidebarSessionUpdated: () -> Unit,
    onAccountActivated: () -> Unit,
) {
    val activeAccountMid = snapshot?.activeAccountMid
    AccountSwitchDialog(
        accounts = snapshot?.accounts.orEmpty(),
        activeAccountMid = activeAccountMid,
        playbackAccountMid = snapshot?.playbackAccountMid,
        purposeAccountMids = snapshot?.purposeAccountMids,
        onSetPurpose = { purpose, mid ->
            coroutineScope.launch(Dispatchers.IO) {
                val updated = AccountSessionStore.setPurposeAccountMid(context, purpose, mid)
                withContext(Dispatchers.Main.immediate) {
                    if (updated) {
                        onAccountActivated()
                        com.android.purebilibili.data.repository.VideoRepository.clearVideoDetailCaches()
                        homeViewModel.refresh()
                        Toast.makeText(context, "账号用途已更新，下次请求生效", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "账号不可用，请重新登录后再试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        onDismiss = onDismiss,
        onAddAccount = onAddAccount,
        onSwitch = { mid ->
            coroutineScope.launch(Dispatchers.IO) {
                val switched = AccountSessionStore.activateAccount(context, mid)
                withContext(Dispatchers.Main.immediate) {
                    if (!switched) {
                        Toast.makeText(context, "切换账号失败", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    onAccountActivated()
                    homeViewModel.refresh()
                    onDismiss()
                    Toast.makeText(context, "已切换账号", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onSetPlayback = { mid ->
            coroutineScope.launch(Dispatchers.IO) {
                val updated = AccountSessionStore.setPlaybackAccountMid(context, mid)
                withContext(Dispatchers.Main.immediate) {
                    if (updated) {
                        onSidebarSessionUpdated()
                    } else {
                        Toast.makeText(
                            context,
                            "播放账号不可用，请重新登录后再试",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        },
        onRemove = { mid ->
            if (mid == activeAccountMid) {
                Toast.makeText(context, "请先切换到其他账号后再移除当前账号", Toast.LENGTH_SHORT).show()
            } else {
                coroutineScope.launch(Dispatchers.IO) {
                    val removed = AccountSessionStore.removeAccount(context, mid)
                    withContext(Dispatchers.Main.immediate) {
                        if (removed) {
                            onSidebarSessionUpdated()
                        } else {
                            Toast.makeText(context, "移除账号失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        },
    )
}

@Composable
internal fun AppNavigationBottomBarDock(
    context: Context,
    coroutineScope: CoroutineScope,
    bottomBarAlignment: Modifier,
    companionFeedWidth: Dp?,
    companionSessionVisible: Boolean,
    companionContentWidth: Dp,
    companionPaneState: CompanionPaneState,
    driveBottomBarByProgress: Boolean,
    sharedNowPlayingTransitionVisible: Boolean,
    depthProgressProvider: () -> Float,
    bottomBarVisibilityState: MutableTransitionState<Boolean>,
    isReturningFromDetail: Boolean,
    navMotionSpec: AppNavigationMotionSpec,
    finalBottomBarVisible: Boolean,
    showAudioNowPlayingInDock: Boolean,
    audioNowPlayingItem: PlaylistItem?,
    audioNowPlayingCover: AudioNowPlayingCover?,
    miniPlayerManager: MiniPlayerManager?,
    pauseNowPlayingArtworkRotation: Boolean,
    showDeleteButton: Boolean,
    nowPlayingSourceRoute: String,
    audioNowPlayingHandoff: NowPlayingBarHandoffState,
    audioNowPlayingBarOpensAudioMode: Boolean,
    nowPlayingGlassEnabled: Boolean,
    isBottomBarBlurEnabled: Boolean,
    mainHazeState: HazeState?,
    bottomBarBackdrop: Backdrop?,
    liquidGlassRenderConfig: LiquidGlassRenderConfig,
    isBottomBarFloating: Boolean,
    isBookPosture: Boolean,
    currentBottomNavItem: BottomNavItem,
    onNavItemClick: (BottomNavItem) -> Unit,
    homeScrollChannel: Channel<HomeScrollRequest>,
    dynamicScrollChannel: Channel<DynamicScrollRequest>,
    onSearchClick: () -> Unit,
    onSearchKeywordSubmit: (String) -> Unit,
    bottomBarSearchLaunchKey: Int,
    bottomBarLabelMode: Int,
    visibleBottomBarItems: List<BottomNavItem>,
    bottomBarItemColors: Map<String, Int>,
    bottomBarItemLabels: Map<String, String>,
    dynamicUnreadDisplay: Int,
    bottomBarHomeSettings: HomeSettings,
    isPagerTransitionRunning: Boolean,
    isFeedScrollInProgress: Boolean,
    collapseLinkedPlaybackDock: Boolean,
    mainBottomPagerState: MainBottomPagerState,
    bottomPagerDraggingProvider: () -> Boolean,
    bottomBarUiSkinDecoration: BottomBarUiSkinDecoration?,
    linkedDockPhase: LinkedDockPhase,
    onLinkedDockPhaseChange: (LinkedDockPhase) -> Unit,
    isTopLevelDestination: Boolean,
    isBottomBarSearchSelected: Boolean,
    canToggleSidebar: Boolean,
    onNavigateToSystemCardWindowOrPush: (BiliPaiNavKey, Boolean) -> Unit,
    onNavigateToVideoRoute: (String, String, Boolean) -> Unit,
    onPushTarget: (BiliPaiNavKey) -> Unit,
) {
    val bottomBarModifier = Modifier
        .then(bottomBarAlignment)
        .zIndex(1f)
        .then(
            if (companionFeedWidth != null && companionSessionVisible) {
                // 双栏共用同一内容宽度；玻璃与原生底栏都留在列表面板内。
                Modifier.layout { measurable, constraints ->
                    val feedWidth = (companionContentWidth.roundToPx() *
                        companionPaneState.measuredFeedFraction(companionContentWidth.value))
                        .toInt().coerceAtMost(constraints.maxWidth)
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = feedWidth, maxWidth = feedWidth),
                    )
                    layout(constraints.maxWidth, placeable.height) {
                        if (companionPaneState.mode != CompanionPaneMode.Video) {
                            placeable.placeRelative(
                                (constraints.maxWidth - companionContentWidth.roundToPx()).coerceAtLeast(0),
                                0,
                            )
                        }
                    }
                }
            } else {
                Modifier
            },
        )
        .then(
            if (driveBottomBarByProgress && !sharedNowPlayingTransitionVisible) {
                Modifier.videoCardTransitionChromeReveal(
                    revealProvider = {
                        resolveVideoCardTransitionChromeReveal(depthProgressProvider())
                    },
                    slideDown = true,
                )
            } else {
                Modifier
            },
        )

    Box(modifier = bottomBarModifier) {
        BottomBarMatchedDockVisibility(
            visibleState = bottomBarVisibilityState,
            edge = BottomBarMatchedDockEdge.BOTTOM,
            // 普通视频返回只保留滑入和淡入；玻璃与原生共用，其他显隐沿用默认缩放。
            animateScale = !isReturningFromDetail,
            enterFadeDurationMillis = navMotionSpec.slowFadeDurationMillis,
            exitFadeDurationMillis = navMotionSpec.fastFadeDurationMillis,
        ) {
            // The dock may remain mounted at zero reveal during a card handoff.
            // Gate visual sampling explicitly instead of relying on viewport bounds.
            CompositionLocalProvider(
                LocalVisualWorkAllowed provides (finalBottomBarVisible && !driveBottomBarByProgress),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val dockAudioContent: LinkedDockNowPlayingSlot? =
                        if (showAudioNowPlayingInDock && audioNowPlayingItem != null && audioNowPlayingCover != null) {
                            { audioModifier, dockMergeProgress, iconOnlyProgress, surfaceMergeProgress,
                                compactClick, layoutStable ->
                                val playbackManager = miniPlayerManager ?: MiniPlayerManager.getInstance(context)
                                AudioNowPlayingBar(
                                    state = AudioNowPlayingBarState(
                                        bvid = audioNowPlayingItem.bvid,
                                        title = audioNowPlayingItem.title,
                                        artist = audioNowPlayingItem.owner,
                                        artistAvatarUrl = audioNowPlayingItem.ownerFace,
                                        cover = audioNowPlayingCover,
                                        isPlaying = playbackManager.isPlaying,
                                        playbackSpeed = playbackManager.player?.playbackParameters?.speed ?: 1f,
                                    ),
                                    onCompactClick = compactClick,
                                    isLayoutStable = layoutStable && bottomBarVisibilityState.isIdle &&
                                        !driveBottomBarByProgress,
                                    pauseArtworkRotation = pauseNowPlayingArtworkRotation,
                                    showDeleteButton = showDeleteButton,
                                    sourceRoute = nowPlayingSourceRoute,
                                    handoff = audioNowPlayingHandoff,
                                    onExpand = { hasRecordedSource ->
                                        val expandRoute = resolveAudioNowPlayingBarExpandRoute(
                                            opensAudioMode = audioNowPlayingBarOpensAudioMode,
                                            bvid = audioNowPlayingItem.bvid,
                                            cid = audioNowPlayingItem.cid,
                                            coverUrl = audioNowPlayingItem.cover,
                                            title = audioNowPlayingItem.title,
                                        )
                                        if (audioNowPlayingBarOpensAudioMode) {
                                            onNavigateToSystemCardWindowOrPush(
                                                legacyRouteToBiliPaiNavKey(expandRoute),
                                                hasRecordedSource,
                                            )
                                        } else {
                                            onNavigateToVideoRoute(
                                                expandRoute,
                                                nowPlayingSourceRoute,
                                                hasRecordedSource,
                                            )
                                        }
                                    },
                                    onPlayPause = {
                                        if (!playbackManager.isActive || !playbackManager.togglePlayPause()) {
                                            onPushTarget(
                                                BiliPaiNavKey.AudioMode(
                                                    sourceBvid = audioNowPlayingItem.bvid,
                                                    sourceCid = audioNowPlayingItem.cid,
                                                )
                                            )
                                        }
                                    },
                                    onSkipNext = { playbackManager.playNext() },
                                    onSkipPrevious = { playbackManager.playPrevious() },
                                    onCancelPlayback = { playbackManager.pausePlayback() },
                                    onDismiss = {
                                        playbackManager.pausePlayback()
                                        AudioNowPlayingSession.dismiss()
                                    },
                                    expandDestinationLabel = if (audioNowPlayingBarOpensAudioMode) {
                                        "听视频"
                                    } else {
                                        "视频详情页"
                                    },
                                    glassEnabled = nowPlayingGlassEnabled,
                                    blurEnabled = isBottomBarBlurEnabled,
                                    hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                                    miuixBackdrop = bottomBarBackdrop,
                                    liquidGlassTuning = liquidGlassRenderConfig.tuning,
                                    liftAboveBottomBar = false,
                                    consumeNavigationBarsPadding = false,
                                    dockHosted = isBottomBarFloating,
                                    dockMergeProgress = dockMergeProgress,
                                    surfaceMergeProgress = surfaceMergeProgress,
                                    iconOnlyProgress = iconOnlyProgress,
                                    modifier = audioModifier,
                                )
                            }
                        } else {
                            null
                        }
                    if (!isBottomBarFloating) {
                        dockAudioContent?.invoke(Modifier, { 0f }, { 0f }, { 0f }, null, true)
                    }
                    val toggleSidebarAction: (() -> Unit)? = if (canToggleSidebar) {
                        {
                            coroutineScope.launch {
                                SettingsManager.setTabletUseSidebar(context, true)
                            }
                        }
                    } else {
                        null
                    }
                    if (isBottomBarFloating) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isBookPosture) {
                                        Modifier.padding(start = 24.dp)
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = if (isBookPosture) Alignment.CenterStart else Alignment.Center,
                        ) {
                            FrostedBottomBar(
                                nowPlayingContent = dockAudioContent,
                                currentItem = currentBottomNavItem,
                                onItemClick = onNavItemClick,
                                onHomeDoubleTap = {
                                    homeScrollChannel.trySend(HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH)
                                },
                                onDynamicDoubleTap = {
                                    dynamicScrollChannel.trySend(DynamicScrollRequest.SCROLL_TO_TOP_AND_REFRESH)
                                },
                                onSearchClick = onSearchClick,
                                onSearchKeywordSubmit = onSearchKeywordSubmit,
                                searchLaunchKey = bottomBarSearchLaunchKey,
                                hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                                isFloating = true,
                                labelMode = bottomBarLabelMode,
                                visibleItems = visibleBottomBarItems,
                                itemColorIndices = bottomBarItemColors,
                                itemLabels = bottomBarItemLabels,
                                dynamicUnreadCount = dynamicUnreadDisplay,
                                homeSettings = bottomBarHomeSettings,
                                miuixBackdrop = bottomBarBackdrop,
                                motionTier = MotionTier.Normal,
                                isTransitionRunning = isPagerTransitionRunning,
                                // 底栏是独立的常驻材质层。栏目切换时保持液态玻璃渲染树，
                                // 避免先卸载折射效果、页面落定后再等待 backdrop 重新捕获。
                                forceLowBlurBudget = false,
                                isFeedScrollInProgress = isFeedScrollInProgress,
                                collapseLinkedDock = collapseLinkedPlaybackDock,
                                indicatorPositionProvider = mainBottomPagerState.indicatorPositionProvider,
                                isPagerScrollInProgressProvider = mainBottomPagerState.scrollInProgressProvider,
                                isPagerDraggingProvider = bottomPagerDraggingProvider,
                                pagerTargetPositionProvider = mainBottomPagerState.targetPositionProvider,
                                isPagerNavigatingProvider = mainBottomPagerState.programmaticNavigationProvider,
                                uiSkinDecoration = bottomBarUiSkinDecoration,
                                linkedDockPhase = linkedDockPhase,
                                onLinkedDockPhaseChange = onLinkedDockPhaseChange,
                                isTopLevelDestination = isTopLevelDestination,
                                isSearchSelected = isBottomBarSearchSelected,
                                // 共享过渡驱动的开关（点条进详情/返回落位）瞬时切换小横条
                                // presence，morph 是唯一几何时间轴；仅会话起止播放动画。
                                animateNowPlayingPresence = !driveBottomBarByProgress,
                                onToggleSidebar = toggleSidebarAction,
                            )
                        }
                    } else {
                        FrostedBottomBar(
                            currentItem = currentBottomNavItem,
                            onItemClick = onNavItemClick,
                            onHomeDoubleTap = {
                                homeScrollChannel.trySend(HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH)
                            },
                            onDynamicDoubleTap = {
                                dynamicScrollChannel.trySend(DynamicScrollRequest.SCROLL_TO_TOP_AND_REFRESH)
                            },
                            onSearchClick = onSearchClick,
                            onSearchKeywordSubmit = onSearchKeywordSubmit,
                            searchLaunchKey = bottomBarSearchLaunchKey,
                            hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                            isFloating = false,
                            labelMode = bottomBarLabelMode,
                            visibleItems = visibleBottomBarItems,
                            itemColorIndices = bottomBarItemColors,
                            itemLabels = bottomBarItemLabels,
                            dynamicUnreadCount = dynamicUnreadDisplay,
                            homeSettings = bottomBarHomeSettings,
                            miuixBackdrop = bottomBarBackdrop,
                            motionTier = MotionTier.Normal,
                            isTransitionRunning = isPagerTransitionRunning,
                            // 固定底栏同样保持材质连续，切页预算只作用于页面内容。
                            forceLowBlurBudget = false,
                            isFeedScrollInProgress = isFeedScrollInProgress,
                            collapseLinkedDock = collapseLinkedPlaybackDock,
                            indicatorPositionProvider = mainBottomPagerState.indicatorPositionProvider,
                            isPagerScrollInProgressProvider = mainBottomPagerState.scrollInProgressProvider,
                            isPagerDraggingProvider = bottomPagerDraggingProvider,
                            pagerTargetPositionProvider = mainBottomPagerState.targetPositionProvider,
                            isPagerNavigatingProvider = mainBottomPagerState.programmaticNavigationProvider,
                            uiSkinDecoration = bottomBarUiSkinDecoration,
                            linkedDockPhase = linkedDockPhase,
                            onLinkedDockPhaseChange = onLinkedDockPhaseChange,
                            isTopLevelDestination = isTopLevelDestination,
                            isSearchSelected = isBottomBarSearchSelected,
                            animateNowPlayingPresence = !driveBottomBarByProgress,
                            onToggleSidebar = toggleSidebarAction,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppNavigationIndependentAudioNowPlayingHost(
    context: Context,
    bottomBarAlignment: Modifier,
    audioNowPlayingItem: PlaylistItem,
    audioNowPlayingCover: AudioNowPlayingCover,
    miniPlayerManager: MiniPlayerManager?,
    currentNavigation3Key: BiliPaiNavKey?,
    inboxConversationVisible: Boolean,
    showAudioNowPlayingIndependent: Boolean,
    driveBottomBarByProgress: Boolean,
    isReturningSameAudioVideo: Boolean,
    navigationContentWidthPx: Int,
    isTabletLayout: Boolean,
    nowPlayingGlassEnabled: Boolean,
    bottomBarBackdrop: Backdrop?,
    floatingBackdropDemand: FloatingChromeBackdropDemand,
    pauseNowPlayingArtworkRotation: Boolean,
    showDeleteButton: Boolean,
    nowPlayingSourceRoute: String,
    audioNowPlayingHandoff: NowPlayingBarHandoffState,
    audioNowPlayingBarOpensAudioMode: Boolean,
    isBottomBarBlurEnabled: Boolean,
    mainHazeState: HazeState?,
    liquidGlassRenderConfig: LiquidGlassRenderConfig,
    onNavigateToSystemCardWindowOrPush: (BiliPaiNavKey, Boolean) -> Unit,
    onNavigateToVideoRoute: (String, String, Boolean) -> Unit,
    onPushTarget: (BiliPaiNavKey) -> Unit,
) {
    val playbackManager = miniPlayerManager ?: MiniPlayerManager.getInstance(context)
    // 听视频标题横条自动沉浸：听视频页 + 播放中静置 5 秒隐藏，点把柄恢复。
    val immersiveBarEnabled by remember(context) {
        SettingsManager.getAudioNowPlayingBarImmersiveEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    var immersiveBarHidden by remember { mutableStateOf(false) }
    val isOnListenVideoScreen = currentNavigation3Key is BiliPaiNavKey.AudioMode
    LaunchedEffect(
        immersiveBarEnabled,
        isOnListenVideoScreen,
        playbackManager.isPlaying,
        audioNowPlayingItem.bvid,
    ) {
        if (immersiveBarEnabled && isOnListenVideoScreen && playbackManager.isPlaying) {
            immersiveBarHidden = false
            delay(5000)
            immersiveBarHidden = true
        } else {
            immersiveBarHidden = false
        }
    }
    val inboxChatBottomBarVisible = currentNavigation3Key == BiliPaiNavKey.Inbox && inboxConversationVisible
    AudioNowPlayingBarPresenceHost(
        visible = showAudioNowPlayingIndependent &&
            !inboxChatBottomBarVisible &&
            !(immersiveBarEnabled && immersiveBarHidden),
        animatePresence = !driveBottomBarByProgress && !isReturningSameAudioVideo,
        modifier = Modifier
            .then(bottomBarAlignment)
            .zIndex(2f)
            .layout { measurable, constraints ->
                val windowWidth = constraints.maxWidth
                val contentWidth = navigationContentWidthPx
                    .takeIf { it > 0 }
                    ?.coerceAtMost(windowWidth) ?: windowWidth
                // 大屏独立横条沿用悬浮底栏 600dp 上限，手机保留原有宽度。
                val barWidth = if (isTabletLayout) {
                    contentWidth.coerceAtMost(600.dp.roundToPx())
                } else {
                    contentWidth
                }
                val placeable = measurable.measure(
                    constraints.copy(minWidth = barWidth, maxWidth = barWidth)
                )
                layout(windowWidth, placeable.height) {
                    placeable.placeRelative(
                        windowWidth - contentWidth + (contentWidth - barWidth) / 2,
                        0,
                    )
                }
            },
    ) {
        if (immersiveBarEnabled && immersiveBarHidden) {
            // 沉浸态把柄：低调的居中小胶囊，点击唤回标题横条。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { immersiveBarHidden = false },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
                )
            }
        } else {
            // 独立横条退场仍需要玻璃；到本体退出组合才释放背景采样。
            if (nowPlayingGlassEnabled && bottomBarBackdrop != null) {
                DisposableEffect(floatingBackdropDemand) {
                    val token = Any()
                    floatingBackdropDemand.acquire(token)
                    onDispose { floatingBackdropDemand.release(token) }
                }
            }
            AudioNowPlayingBar(
                state = AudioNowPlayingBarState(
                    bvid = audioNowPlayingItem.bvid,
                    title = audioNowPlayingItem.title,
                    artist = audioNowPlayingItem.owner,
                    artistAvatarUrl = audioNowPlayingItem.ownerFace,
                    cover = audioNowPlayingCover,
                    isPlaying = playbackManager.isPlaying,
                    playbackSpeed = playbackManager.player?.playbackParameters?.speed ?: 1f,
                ),
                isLayoutStable = !transition.isRunning &&
                    transition.currentState == transition.targetState && !driveBottomBarByProgress,
                pauseArtworkRotation = pauseNowPlayingArtworkRotation,
                showDeleteButton = showDeleteButton,
                sourceRoute = nowPlayingSourceRoute,
                handoff = audioNowPlayingHandoff,
                onExpand = { hasRecordedSource ->
                    val expandRoute = resolveAudioNowPlayingBarExpandRoute(
                        opensAudioMode = audioNowPlayingBarOpensAudioMode,
                        bvid = audioNowPlayingItem.bvid,
                        cid = audioNowPlayingItem.cid,
                        coverUrl = audioNowPlayingItem.cover,
                        title = audioNowPlayingItem.title,
                    )
                    if (audioNowPlayingBarOpensAudioMode) {
                        onNavigateToSystemCardWindowOrPush(
                            legacyRouteToBiliPaiNavKey(expandRoute),
                            hasRecordedSource,
                        )
                    } else {
                        onNavigateToVideoRoute(
                            expandRoute,
                            nowPlayingSourceRoute,
                            hasRecordedSource,
                        )
                    }
                },
                onPlayPause = {
                    if (!playbackManager.isActive || !playbackManager.togglePlayPause()) {
                        onPushTarget(
                            BiliPaiNavKey.AudioMode(
                                sourceBvid = audioNowPlayingItem.bvid,
                                sourceCid = audioNowPlayingItem.cid,
                            )
                        )
                    }
                },
                onSkipNext = { playbackManager.playNext() },
                onSkipPrevious = { playbackManager.playPrevious() },
                onCancelPlayback = { playbackManager.pausePlayback() },
                onDismiss = {
                    playbackManager.pausePlayback()
                    AudioNowPlayingSession.dismiss()
                },
                expandDestinationLabel = if (audioNowPlayingBarOpensAudioMode) {
                    "听视频"
                } else {
                    "视频详情页"
                },
                glassEnabled = nowPlayingGlassEnabled,
                blurEnabled = isBottomBarBlurEnabled,
                hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                miuixBackdrop = bottomBarBackdrop,
                liquidGlassTuning = liquidGlassRenderConfig.tuning,
                liftAboveBottomBar = false,
                consumeNavigationBarsPadding = true,
                bottomSpacing = 16.dp,
            )
        }
    }
}
