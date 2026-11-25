package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.rememberAppElasticPressState
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.ui.graphics.TransformOrigin
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.feature.audio.screen.AUDIO_NOW_PLAYING_PRESENCE_ENTER_SLIDE_DP
import com.android.purebilibili.feature.audio.screen.resolveAudioNowPlayingPresenceAlpha
import com.android.purebilibili.feature.audio.screen.resolveAudioNowPlayingPresenceAnimationSpec
import com.android.purebilibili.feature.audio.screen.resolveAudioNowPlayingPresenceEnterSpringSpec
import com.android.purebilibili.feature.home.LocalHomeScrollOffset
import com.android.purebilibili.feature.home.LocalHomeFeedScrollInProgress
import kotlinx.coroutines.flow.collect
import dev.chrisbanes.haze.HazeState
import top.yukonga.miuix.kmp.blur.Backdrop
import kotlin.math.roundToInt

typealias LinkedDockNowPlayingSlot = @Composable (
    Modifier,
    () -> Float,
    () -> Float,
    () -> Float,
    (() -> Unit)?,
    Boolean,
) -> Unit

@Composable
internal fun LinkedBottomDock(
    currentItem: BottomNavItem,
    searchEnabled: Boolean,
    searchInsideDock: Boolean = false,
    collapseRequested: Boolean,
    onSearchClick: () -> Unit,
    onSearchKeywordSubmit: (String) -> Unit,
    containerColor: Color,
    backdrop: Backdrop?,
    glassEnabled: Boolean,
    liquidGlassTuning: LiquidGlassTuning,
    iconStyle: SharedFloatingBottomBarIconStyle,
    navigationItemCount: Int,
    navigationLabelMode: Int,
    navigationMinEdgePadding: androidx.compose.ui.unit.Dp,
    nowPlayingContent: LinkedDockNowPlayingSlot?,
    dockPhase: LinkedDockPhase? = null,
    onDockPhaseChange: ((LinkedDockPhase) -> Unit)? = null,
    isTopLevelDestination: Boolean = true,
    mergeOnScrollDownEnabled: Boolean = true,
    compactOnScrollEnabled: Boolean = false,
    currentLabel: String = "",
    animateNowPlayingPresence: Boolean = true,
    hasUiSkinDecoration: Boolean = false,
    uiSkinDecoration: BottomBarUiSkinDecoration? = null,
    visibleItems: List<BottomNavItem> = emptyList(),
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = false,
    hazeState: HazeState? = null,
    navigationContent: @Composable (allocatedWidth: Dp, onInlineSearchClick: (() -> Unit)?) -> Unit,
) {
    com.android.purebilibili.core.ui.components.ProvideAppElasticFeedback {
        val searchHaptic = com.android.purebilibili.core.util.rememberHapticFeedback(
            com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION,
        )
        val hasAudio = nowPlayingContent != null
        val effectiveSearchInsideDock = searchEnabled && searchInsideDock
        val searchMergeEnabled = searchEnabled && mergeOnScrollDownEnabled && !effectiveSearchInsideDock
        var keepSlotComposed by remember { mutableStateOf(hasAudio) }
        val useNavigationCompact = shouldUseScrollCompactNavigation(
            enabled = compactOnScrollEnabled,
            hasAudio = hasAudio,
            retainingAudio = keepSlotComposed,
            searchMergeEnabled = searchMergeEnabled,
        )
        var compactNavigation by remember(currentItem, useNavigationCompact) { mutableStateOf(false) }
        var internalPhase by remember(currentItem, searchEnabled) {
            mutableStateOf(
                resolveLinkedDockInitialPhase(
                    currentItem = currentItem,
                    collapseRequested = collapseRequested && mergeOnScrollDownEnabled,
                    hasAudio = hasAudio,
                    savedPhase = dockPhase,
                    searchMergeEnabled = searchMergeEnabled,
                )
            )
        }
        val phase = dockPhase ?: internalPhase
        val updatePhase: (LinkedDockPhase) -> Unit = { newPhase ->
            if (dockPhase != null && onDockPhaseChange != null) {
                onDockPhaseChange(newPhase)
            } else {
                internalPhase = newPhase
            }
        }
        LaunchedEffect(useNavigationCompact, currentItem, isTopLevelDestination) {
            if (useNavigationCompact && isTopLevelDestination && phase != LinkedDockPhase.Search) {
                updatePhase(LinkedDockPhase.Expanded)
            }
        }
        LaunchedEffect(effectiveSearchInsideDock, phase) {
            if (effectiveSearchInsideDock && phase == LinkedDockPhase.Search) {
                updatePhase(
                    resolveLinkedDockPhaseOnSearchDismiss(
                        hasAudio = hasAudio,
                        previousPhase = LinkedDockPhase.Expanded,
                        searchMergeEnabled = false,
                    )
                )
            }
        }
        LaunchedEffect(phase) {
            if (phase == LinkedDockPhase.Search) compactNavigation = false
        }
        LaunchedEffect(hasAudio, searchMergeEnabled, dockPhase != null) {
            // 外部相位由实际播放会话驱动，导航暂时撤掉播放槽时保留原来的合体落点。
            if (dockPhase == null) {
                val reconciled = resolveLinkedDockPhaseOnAudioChange(phase, hasAudio, searchMergeEnabled)
                if (reconciled != phase) updatePhase(reconciled)
            }
        }
        var query by remember { mutableStateOf("") }
        LaunchedEffect(phase) {
            if (shouldResetLinkedDockSearchQuery(phase)) {
                query = ""
            }
        }
        var pendingUserImeRequest by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val scroll = LocalHomeScrollOffset.current
        val scrolling = LocalHomeFeedScrollInProgress.current
        val currentPhase by rememberUpdatedState(phase)
        val latestUpdatePhase by rememberUpdatedState(updatePhase)
        val threshold = with(LocalDensity.current) { 24.dp.toPx() }
        LaunchedEffect(currentItem, hasAudio, searchEnabled, scroll, scrolling, threshold, isTopLevelDestination, mergeOnScrollDownEnabled, glassEnabled, useNavigationCompact) {
            var previous = scroll.floatValue
            var accumulated = 0f
            snapshotFlow { scroll.floatValue to scrolling.value }.collect { (offset, active) ->
                val delta = offset - previous
                previous = offset
                if (!active || !isTopLevelDestination || currentPhase == LinkedDockPhase.Search) {
                    accumulated = 0f
                } else if (useNavigationCompact) {
                    // Without playback, only navigation compacts; playback retains its original phases.
                    accumulated = accumulateDockScroll(accumulated, delta)
                    resolveScrollCompactIntent(accumulated, threshold)?.let { compact ->
                        compactNavigation = compact
                        accumulated = 0f
                    }
                } else {
                    accumulated = accumulateDockScroll(accumulated, delta)
                    // 原生路径仍支持回到顶部立即展开；玻璃按完整反向阈值恢复。
                    val target = if (!glassEnabled && offset <= 0f && delta < 0f) {
                        LinkedDockPhase.Expanded
                    } else resolveLinkedDockScrollPhase(
                        accumulatedPx = accumulated,
                        thresholdPx = threshold,
                        hasAudio = hasAudio,
                        searchEnabled = searchEnabled && !effectiveSearchInsideDock,
                        mergeOnScrollDownEnabled = mergeOnScrollDownEnabled,
                    )
                    if (target != null) {
                        latestUpdatePhase(target)
                        accumulated = 0f
                    }
                }
            }
        }
        // Keep the dock phase while a child destination covers the current tab. Keying this effect
        // by isTopLevelDestination made the returning page re-expand/re-collapse the playback strip,
        // which also shifted the predictive-back target after the gesture had started.
        // Skip while the list is scrolling so resting phase does not fight scroll-driven search size.
        LaunchedEffect(currentItem, useNavigationCompact, searchMergeEnabled, mergeOnScrollDownEnabled) {
            if (
                isTopLevelDestination && !useNavigationCompact &&
                currentItem != BottomNavItem.HOME &&
                currentPhase != LinkedDockPhase.Search &&
                !scrolling.value
            ) {
                updatePhase(resolveLinkedDockRestingPhase(
                    collapseRequested && mergeOnScrollDownEnabled, hasAudio, searchMergeEnabled,
                ))
            }
        }
        fun expand() {
            focusManager.clearFocus()
            keyboardController?.hide()
            updatePhase(LinkedDockPhase.Expanded)
        }
        var phaseBeforeSearch by remember { mutableStateOf(LinkedDockPhase.Expanded) }
        val backEnabled = shouldEnableLinkedDockBackHandler(
            phase = phase,
            isTopLevelDestination = isTopLevelDestination,
        )
        BackHandler(enabled = backEnabled) {
            focusManager.clearFocus()
            keyboardController?.hide()
            updatePhase(resolveLinkedDockPhaseOnSearchDismiss(hasAudio, phaseBeforeSearch, searchMergeEnabled))
        }
        val reduceMotion = LocalSystemReduceMotion.current ||
            !com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
        val glassMorphMotion = glassEnabled && !hasUiSkinDecoration
        val dockMotionStyle = rememberGlassDockMotionStyle(
            enabled = glassMorphMotion,
        )
        val transition = updateTransition(targetState = phase, label = "linkedBottomDock")
        // 液态玻璃路径拆分水平/垂直两轴弹簧（收起先缩窄再落槽、展开先抬升再铺开）；
        // 非玻璃路径保持双轴共用同一原生弹簧，反向时均由 Compose 保留速度。
        val merge = transition.animateFloat(
            transitionSpec = {
                if (reduceMotion) {
                    snap()
                } else {
                    glassDockHorizontalMorphSpring(
                        glassEnabled = glassMorphMotion,
                        style = dockMotionStyle,
                        expanding = linkedDockMotionExpanding(initialState, targetState),
                    )
                }
            },
            label = "dockMerge",
        ) { if (it == LinkedDockPhase.Expanded) 0f else 1f }
        val mergeVertical = transition.animateFloat(
            transitionSpec = {
                if (reduceMotion) {
                    snap()
                } else {
                    glassDockVerticalMorphSpring(
                        glassEnabled = glassMorphMotion,
                        style = dockMotionStyle,
                        expanding = linkedDockMotionExpanding(initialState, targetState),
                    )
                }
            },
            label = "dockMergeVertical",
        ) { if (it == LinkedDockPhase.Expanded) 0f else 1f }
        val search = transition.animateFloat(
            transitionSpec = {
                if (reduceMotion) {
                    snap()
                } else {
                    glassDockHorizontalMorphSpring(
                        glassEnabled = glassMorphMotion,
                        style = dockMotionStyle,
                        expanding = linkedDockMotionExpanding(initialState, targetState),
                    )
                }
            },
            label = "dockSearch",
        ) { if (it == LinkedDockPhase.Search || it == LinkedDockPhase.Compact) 1f else 0f }
        fun openSearch() {
            searchHaptic(com.android.purebilibili.core.util.HapticType.LIGHT)
            phaseBeforeSearch = phase
            pendingUserImeRequest = true
            updatePhase(LinkedDockPhase.Search)
        }
        val imeSettled = WindowInsets.ime
            .getBottom(LocalDensity.current) == 0
        val mergeProgressProvider = remember(merge) {
            { merge.value.coerceIn(0f, 1f) }
        }
        val searchProgressProvider = remember(search) {
            { search.value.coerceIn(0f, 1f) }
        }
        val zeroProgressProvider = remember { { 0f } }
        val identityIconScaleProvider = remember { { 1f } }

        // Shared morph owns both directions. Keep the real slot at its endpoint while
        // handoff alpha hides it; replaying an entrance spring causes a second landing.
        val presence = remember { Animatable(if (hasAudio) 1f else 0f) }
        val slotEntering = remember { mutableStateOf(false) }
        val nowPlayingLayoutStable = transition.currentState == transition.targetState &&
            !transition.isRunning && !presence.isRunning && imeSettled
        LaunchedEffect(hasAudio, animateNowPlayingPresence, reduceMotion) {
            val target = if (hasAudio) 1f else 0f
            when {
                !animateNowPlayingPresence -> {
                    slotEntering.value = false
                    presence.snapTo(target)
                    keepSlotComposed = hasAudio
                }
                presence.value == target -> {
                    slotEntering.value = false
                    keepSlotComposed = hasAudio
                }
                hasAudio -> {
                    keepSlotComposed = true
                    if (reduceMotion) {
                        slotEntering.value = false
                        presence.snapTo(1f)
                    } else {
                        slotEntering.value = true
                        presence.animateTo(
                            targetValue = 1f,
                            animationSpec = resolveAudioNowPlayingPresenceEnterSpringSpec(),
                        )
                        slotEntering.value = false
                    }
                }
                animateNowPlayingPresence -> {
                    slotEntering.value = false
                    presence.animateTo(
                        targetValue = 0f,
                        animationSpec = resolveAudioNowPlayingPresenceAnimationSpec(
                            active = false,
                            reduceMotion = reduceMotion,
                        ),
                    )
                    keepSlotComposed = false
                }
                else -> {
                    slotEntering.value = false
                    presence.snapTo(0f)
                    keepSlotComposed = false
                }
            }
        }
        val presenceProgressProvider = remember(presence) {
            { presence.value.coerceIn(0f, 1f) }
        }
        // 入场期间槽宽直接取目标值（上滑 + fade 已足够），宽度收放只在退出时生效。
        val slotGeometryPresenceProvider = remember {
            { if (slotEntering.value) 1f else presence.value.coerceIn(0f, 1f) }
        }
        // 保留最后一个有效播放条，退出动画完成后再释放；最新值在关闭时已是 null。
        var retainedNowPlayingContent by remember { mutableStateOf(nowPlayingContent) }
        SideEffect {
            if (nowPlayingContent != null || !keepSlotComposed) {
                retainedNowPlayingContent = nowPlayingContent
            }
        }
        val nowPlayingSlot = nowPlayingContent
            ?: retainedNowPlayingContent.takeIf { keepSlotComposed }

        val nativeMiuixNavigation = com.android.purebilibili.core.ui.isMiuixNonGlassEnabled() && !hasUiSkinDecoration
        val shape = resolveSharedBottomBarCapsuleShape()
        val contentColor = AppContentColors.primaryText()
        // Only glass accents use the exact picked color; native and neutral actions retain their roles.
        val accentColor = AppContentColors.selectionAccent(glassEnabled)
        val navigationColor = com.android.purebilibili.core.ui.AppInteractionColors.nativeNavigation(
            com.android.purebilibili.core.ui.AppNavigationColorRole.BAR,
        )?.selectedContent ?: accentColor
        BoxWithConstraints(
            // 仅底栏自己的输入态避让键盘。顶部搜索或其他页面收键盘时，
            // 首页底栏保持原落点，不能跟随尚未归零的 IME inset 上下跳动。
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    if (shouldLinkedDockAvoidIme(phase, isTopLevelDestination)) WindowInsets.ime
                    else WindowInsets(0, 0, 0, 0),
                )
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val density = LocalDensity.current
            val maximumWidth: Int
            val button: Int
            val barHeight: Int
            val controlHeight: Int
            val gap: Int
            val verticalGap: Int
            val fusedDockCapable = glassEnabled && searchEnabled && !effectiveSearchInsideDock &&
                backdrop != null && !hasUiSkinDecoration
            with(density) {
                maximumWidth = constraints.maxWidth.coerceAtMost(600.dp.roundToPx())
                button = resolveBiliPaiBottomBarSearchCircleSize().roundToPx()
                barHeight = resolveBiliPaiBottomBarDockHeight(
                    searchExpanded = false,
                    hasUiSkinDecoration = hasUiSkinDecoration,
                    nativeMiuixNavigation = nativeMiuixNavigation,
                ).roundToPx()
                controlHeight = barHeight
                // 静止态保持 8.dp 清晰分离，仅在搜索框与底栏相互靠近（点击展开/收起或弹性拖动）时动态触发吸附融合。
                gap = 8.dp.roundToPx()
                verticalGap = 4.dp.roundToPx()
            }
            fun compactControlSize(): Int {
                val compact = with(density) {
                    com.android.purebilibili.feature.audio.screen.resolveAudioNowPlayingBarHeightDp(
                        merge.value, search.value,
                    ).dp.roundToPx()
                }
                val progress = if (hasAudio) merge.value.coerceIn(0f, 1f) else 0f
                return (button + (compact - button) * progress).roundToInt()
            }
            val expandedGlassSearchHeight = with(density) { 48.dp.roundToPx() }
            fun searchVisualHeight(): Int {
                val restingHeight = compactControlSize()
                return if (glassEnabled) {
                    (restingHeight + (expandedGlassSearchHeight - restingHeight) *
                        search.value.coerceIn(0f, 1f)).roundToInt()
                } else restingHeight
            }
            // 两行宿主与导航行高度固定；搜索框仅在自身测量、放置阶段读取展开进度，
            // 不把搜索框高度变化传回页面组合或整条底栏的测量。
            val containerHeight = barHeight + verticalGap + barHeight
            val navRowY = containerHeight - barHeight
            val controlRowY = navRowY + (barHeight - controlHeight) / 2

            val preferredNavigationWidth = with(density) {
                resolveBiliPaiFloatingBottomBarWidth(
                    containerWidth = maximumWidth.toDp(),
                    itemCount = navigationItemCount,
                    minEdgePadding = navigationMinEdgePadding,
                    labelMode = navigationLabelMode,
                    cornerRadius = with(density) { barHeight.toDp() / 2 },
                ).roundToPx()
            }
            val reservedSearchWidth = if (searchEnabled && !effectiveSearchInsideDock) button + gap else 0
            val expandedNavigationWidth = preferredNavigationWidth.coerceAtMost(
                (maximumWidth - reservedSearchWidth).coerceAtLeast(0)
            )
            val navWidth = expandedNavigationWidth.coerceAtMost(maximumWidth)
            val allocatedNavigationWidth = with(density) { navWidth.toDp() }
            val navigationX = resolveLinkedDockNavigationX(
                maximumWidth = maximumWidth,
                navigationWidth = navWidth,
                button = button,
                gap = gap,
                searchEnabled = searchEnabled,
                searchInsideDock = effectiveSearchInsideDock,
            )
            val navMorphClipShape = remember(merge, navWidth, barHeight, button, hasAudio, search) {
                object : androidx.compose.ui.graphics.Shape {
                    override fun createOutline(
                        size: androidx.compose.ui.geometry.Size,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        density: androidx.compose.ui.unit.Density,
                    ): androidx.compose.ui.graphics.Outline {
                        val amount = merge.value.coerceIn(0f, 1f)
                        val targetSize = compactControlSize()
                        val width = resolveScrollCompactExtent(navWidth, targetSize, amount).toFloat()
                        val height = resolveScrollCompactExtent(barHeight, targetSize, amount).toFloat()
                        val top = (barHeight - height) / 2f
                        return androidx.compose.ui.graphics.Outline.Rounded(
                            androidx.compose.ui.geometry.RoundRect(
                                androidx.compose.ui.geometry.Rect(0f, top, width, top + height),
                                androidx.compose.ui.geometry.CornerRadius(height / 2f),
                            )
                        )
                    }
                }
            }

            // Glass keeps the navigation/backdrop mounted across both endpoints. At the compact
            // endpoint skip placement so the hidden row neither draws nor intercepts touches.
            // Native toolbars retain their original composition and motion behavior.
            val collapsedAtRest = phase != LinkedDockPhase.Expanded && !transition.isRunning
            val isNavCompacted = useNavigationCompact && compactNavigation
            val searchElasticState = rememberAppElasticPressState(
                feedbackStrength = com.android.purebilibili.core.ui.components.APP_SEARCH_ELASTIC_FEEDBACK_STRENGTH,
            )
            // 实时读取可打断弹簧进度与搜索圆钮弹性形变，仅在两胶囊实际靠近（边间距 < 阈值）时动态形成融合凹颈。
            val fusionThresholdPx = with(density) { 6.5.dp.toPx() }
            val maxFusionRadiusPx = with(density) { 14.dp.toPx() }
            val liveDockGeometry = if (fusedDockCapable && !isNavCompacted) {
                resolveLinkedDockGeometry(
                    width = maximumWidth,
                    button = button,
                    barHeight = barHeight,
                    gap = gap,
                    hasAudio = hasAudio,
                    searchEnabled = searchEnabled,
                    mergeProgress = merge.value,
                    searchProgress = search.value,
                    verticalGap = verticalGap,
                )
            } else null
            val liveSearchVisualWidth = if (liveDockGeometry != null) {
                (compactControlSize() +
                    (liveDockGeometry.searchWidth - compactControlSize()) *
                    search.value.coerceIn(0f, 1f)).roundToInt().coerceAtMost(liveDockGeometry.searchWidth)
            } else button
            val liveSearchVisualHeight = if (liveDockGeometry != null) searchVisualHeight() else button
            val liveSearchBaseX = if (liveDockGeometry != null) {
                resolveLinkedDockSearchX(
                    maximumWidth = maximumWidth,
                    navigationWidth = navWidth,
                    searchWidth = liveDockGeometry.searchWidth,
                    button = button,
                    gap = gap,
                    mergeProgress = merge.value,
                    searchProgress = search.value,
                    searchInsideDock = effectiveSearchInsideDock,
                ) + (liveDockGeometry.searchWidth - liveSearchVisualWidth) / 2
            } else navigationX + navWidth + gap
            val liveSearchBaseY = controlRowY + (controlHeight - liveSearchVisualHeight) / 2
            val liveSearchRect = searchElasticState.transformedBounds(
                androidx.compose.ui.geometry.Rect(
                    left = liveSearchBaseX.toFloat(),
                    top = liveSearchBaseY.toFloat(),
                    right = (liveSearchBaseX + liveSearchVisualWidth).toFloat(),
                    bottom = (liveSearchBaseY + liveSearchVisualHeight).toFloat(),
                )
            )
            val navCollapseScale = 1f - 0.10f * EaseOutCubic.transform(merge.value.coerceIn(0f, 1f))
            val leftHeightPx = barHeight * navCollapseScale
            val leftWidthPx = (navWidth * navCollapseScale).coerceAtLeast(leftHeightPx)
            val leftRadiusPx = leftHeightPx / 2f
            val leftCenterYInDock = navRowY + barHeight / 2f
            val leftRightCenterXInDock = navigationX + leftWidthPx - leftRadiusPx

            // 搜索圆钮在垂直拖拽时保持正圆直径（不拉成长椭圆），并按真实二维圆心距计算边间距。
            val searchIsCircle = search.value <= 0.01f
            val rightDiameterPx = if (searchIsCircle) {
                minOf(liveSearchRect.width, liveSearchRect.height).coerceAtLeast(1f)
            } else {
                liveSearchRect.height.coerceAtLeast(1f)
            }
            val rightWidthPx = if (searchIsCircle) rightDiameterPx else liveSearchRect.width.coerceAtLeast(rightDiameterPx)
            val rightHeightPx = rightDiameterPx
            val rightRadiusPx = rightHeightPx / 2f
            val rightCenterYInDock = liveSearchRect.center.y
            val rightLeftInDock = if (searchIsCircle) {
                liveSearchRect.center.x - rightRadiusPx
            } else {
                liveSearchRect.left
            }
            val rightLeftCenterXInDock = rightLeftInDock + rightRadiusPx
            val centerDistance2D = kotlin.math.hypot(
                rightLeftCenterXInDock - leftRightCenterXInDock,
                rightCenterYInDock - leftCenterYInDock,
            )
            val liveEdgeGapPx = centerDistance2D - leftRadiusPx - rightRadiusPx
            val navVisibilityWeight = (1f - resolveLinkedDockGlassHandoffProgress(merge.value)).coerceIn(0f, 1f)
            val liveFusionAmount = if (fusedDockCapable && !isNavCompacted && navWidth > 0 && rightWidthPx > 1f &&
                navVisibilityWeight > 0.05f && liveEdgeGapPx < fusionThresholdPx && liveEdgeGapPx > -barHeight * 0.25f &&
                rightLeftCenterXInDock > leftRightCenterXInDock + 1f
            ) {
                ((fusionThresholdPx - liveEdgeGapPx) / fusionThresholdPx).coerceIn(0f, 1f) * navVisibilityWeight
            } else 0f
            val fusedDockActive = liveFusionAmount > 0.02f

            Box(
                modifier = Modifier.size(
                    with(density) { maximumWidth.toDp() },
                    with(density) { containerHeight.toDp() },
                )
            ) {
                if (fusedDockActive && backdrop != null) {
                    val leftTopInDock = leftCenterYInDock - leftRadiusPx
                    val leftBottomInDock = leftCenterYInDock + leftRadiusPx
                    val rightTopInDock = rightCenterYInDock - rightRadiusPx
                    val rightBottomInDock = rightCenterYInDock + rightRadiusPx
                    val fusedTopInDock = minOf(leftTopInDock, rightTopInDock)
                    val fusedBottomInDock = maxOf(leftBottomInDock, rightBottomInDock)
                    val rightLeftLocal = (rightLeftInDock - navigationX).coerceAtLeast(0f)
                    val fusedWidthPx = maxOf(leftWidthPx, rightLeftLocal + rightWidthPx).roundToInt().coerceAtLeast(1)
                    val fusedHeightPx = (fusedBottomInDock - fusedTopInDock).roundToInt().coerceAtLeast(1)
                    val fusionRadiusPx = (maxFusionRadiusPx * liveFusionAmount).coerceAtLeast(1f)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(navigationX, fusedTopInDock.roundToInt()) }
                            .size(
                                with(density) { fusedWidthPx.toDp() },
                                with(density) { fusedHeightPx.toDp() },
                            )
                            .biliPaiFusedDockShell(
                                backdrop = backdrop,
                                containerColor = containerColor,
                                leftWidthPx = leftWidthPx,
                                leftHeightPx = leftHeightPx,
                                leftCenterYPx = leftCenterYInDock - fusedTopInDock,
                                rightLeftPx = rightLeftLocal,
                                rightWidthPx = rightWidthPx,
                                rightHeightPx = rightHeightPx,
                                rightCenterYPx = rightCenterYInDock - fusedTopInDock,
                                fusionRadiusPx = fusionRadiusPx,
                                liquidGlassTuning = liquidGlassTuning,
                            )
                    )
                }
                if (glassEnabled || !collapsedAtRest) {
                    Box(
                        modifier = Modifier
                            .offset {
                                if (glassMorphMotion) {
                                    val compactX = (button - compactControlSize()) / 2
                                    val x = (navigationX + (compactX - navigationX) *
                                        merge.value.coerceIn(0f, 1f)).roundToInt()
                                    IntOffset(x, navRowY)
                                } else {
                                    IntOffset(navigationX, navRowY)
                                }
                            }
                            .size(
                                allocatedNavigationWidth,
                                with(density) { barHeight.toDp() },
                            )
                            .then(
                                if (glassEnabled) Modifier.layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) {
                                        if (merge.value < 0.999f) {
                                            placeable.placeRelative(0, 0)
                                        }
                                    }
                                } else Modifier
                            )
                            .graphicsLayer {
                                if (glassMorphMotion) {
                                    alpha = 1f - resolveLinkedDockGlassHandoffProgress(merge.value)
                                    val shouldClip = merge.value > 0f
                                    if (shouldClip) {
                                        this.shape = navMorphClipShape
                                    }
                                    clip = shouldClip
                                    scaleX = 1f
                                    scaleY = 1f
                                } else {
                                    // 退出窗口（前 34%）ease-in 淡出；全程向左侧圆钮方向
                                    // 轻微收拢缩放，读作"并入圆钮"而非原地溶解。
                                    val exitProgress = (merge.value / 0.34f).coerceIn(0f, 1f)
                                    alpha = if (glassEnabled) {
                                        1f - resolveLinkedDockGlassHandoffProgress(merge.value)
                                    } else 1f - EaseInCubic.transform(exitProgress)
                                    val collapse = EaseOutCubic.transform(merge.value.coerceIn(0f, 1f))
                                    scaleX = 1f - 0.10f * collapse
                                    scaleY = 1f - 0.10f * collapse
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                            }
                            .pointerInput(phase) {
                                if (phase != LinkedDockPhase.Expanded) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial)
                                                .changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                            .then(
                                if (phase != LinkedDockPhase.Expanded) {
                                    Modifier.clearAndSetSemantics {}
                                } else {
                                    Modifier
                                }
                            ),
                    ) {
                        val inlineSearchAction: (() -> Unit)? = if (effectiveSearchInsideDock) {
                            {
                                searchHaptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                                onSearchClick()
                            }
                        } else null
                        if (compactOnScrollEnabled) {
                            ScrollCompactNavigation(
                                compact = useNavigationCompact && compactNavigation && phase == LinkedDockPhase.Expanded,
                                currentItem = currentItem,
                                currentLabel = currentLabel,
                                iconStyle = iconStyle,
                                expandedWidthPx = navWidth,
                                expandedHeightPx = barHeight,
                                buttonSizePx = button.coerceAtMost(navWidth),
                                backdrop = backdrop,
                                containerColor = containerColor,
                                glassEnabled = glassEnabled,
                                blurEnabled = blurEnabled,
                                hazeState = hazeState,
                                liquidGlassTuning = liquidGlassTuning,
                                uiSkinDecoration = uiSkinDecoration,
                                onExpand = { compactNavigation = false },
                                content = {
                                    CompositionLocalProvider(LocalLinkedDockFusedGlassActive provides fusedDockActive) {
                                        navigationContent(allocatedNavigationWidth, inlineSearchAction)
                                    }
                                },
                            )
                        } else CompositionLocalProvider(LocalLinkedDockFusedGlassActive provides fusedDockActive) {
                            navigationContent(allocatedNavigationWidth, inlineSearchAction)
                        }
                    }
                }
                val homeElasticState = rememberAppElasticPressState(
                    visualSize = with(density) { controlHeight.toDp() },
                    feedbackStrength = com.android.purebilibili.core.ui.components.APP_SEARCH_ELASTIC_FEEDBACK_STRENGTH,
                )
                val homeGlassMotion = glassEnabled && backdrop != null
                val homeDragEnabled = phase != LinkedDockPhase.Expanded &&
                    com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
                    com.android.purebilibili.core.ui.LocalAppThemeConfig.current.globalDragFeedbackEnabled
                var homeDragActive by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .offset {
                            val size = compactControlSize()
                            if (glassMorphMotion) {
                                val progress = merge.value.coerceIn(0f, 1f)
                                val compactX = (button - size) / 2
                                val x = (navigationX + (compactX - navigationX) * progress).roundToInt()
                                val h = resolveScrollCompactExtent(barHeight, size, progress)
                                IntOffset(x, navRowY + (barHeight - h) / 2)
                            } else {
                                IntOffset((button - size) / 2, controlRowY + (controlHeight - size) / 2)
                            }
                        }
                        .layout { measurable, _ ->
                            val size = compactControlSize()
                            val progress = merge.value.coerceIn(0f, 1f)
                            val width = if (glassMorphMotion) {
                                resolveScrollCompactExtent(navWidth, size, progress)
                            } else size
                            val height = if (glassMorphMotion) {
                                resolveScrollCompactExtent(barHeight, size, progress)
                            } else size
                            val child = measurable.measure(Constraints.fixed(width, height))
                            layout(width, height) {
                                if (merge.value > 0f) child.placeRelative(0, 0)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Keep placement outside the elastic observer: its gesture geometry must
                    // be the visible button, rather than the full dock before offset/size.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .appElasticPress(
                                enabled = phase != LinkedDockPhase.Expanded,
                                state = homeElasticState,
                                transformInBackdrop = homeGlassMotion,
                                allowConsumedDrag = { homeDragActive },
                            )
                            .then(
                                // A pointerInput node still participates in hit testing even
                                // when its coroutine returns immediately. The transparent home
                                // button overlaps the first navigation slot in expanded mode.
                                if (homeDragEnabled) Modifier.pointerInput(homeElasticState) {
                                    // Own decorative drag after touch slop; cancel the click
                                    // instead of expanding the dock on drag release.
                                    try {
                                        detectDragGestures(
                                            onDragStart = { homeDragActive = true },
                                            onDragEnd = { homeDragActive = false },
                                            onDragCancel = { homeDragActive = false },
                                        ) { change, _ -> change.consume() }
                                    } finally {
                                        homeDragActive = false
                                    }
                                } else Modifier
                            )
                            .graphicsLayer {
                                if (glassMorphMotion) {
                                    // 物理缩宽路径：与导航条玻璃壳互补淡切，不做二次缩放弹出。
                                    alpha = resolveLinkedDockGlassHandoffProgress(merge.value)
                                } else {
                                    // 进入窗口（前 50%）ease-out 淡入并从 92% 缩放弹出，落定干净无过冲。
                                    val enterProgress = (merge.value / 0.5f).coerceIn(0f, 1f)
                                    val eased = EaseOutCubic.transform(enterProgress)
                                    // One complementary handoff prevents both glass shells from
                                    // brightening the same region during merge and reversal.
                                    alpha = if (glassEnabled) {
                                        resolveLinkedDockGlassHandoffProgress(merge.value)
                                    } else eased
                                    val scale = 0.92f + 0.08f * eased
                                    scaleX = scale
                                    scaleY = scale
                                }
                            }
                            .then(
                                if (phase != LinkedDockPhase.Expanded) {
                                    Modifier.clickable(role = Role.Button) { expand() }
                                } else {
                                    Modifier.clearAndSetSemantics {}
                                }
                            ),
                        contentAlignment = if (glassMorphMotion) Alignment.CenterStart else Alignment.Center,
                    ) {
                        val skinCollapsedContainerColor = if (uiSkinDecoration != null) {
                            val skinTint = uiSkinDecoration.liquidGlassSurfaceTint ?: uiSkinDecoration.bottomTrimTint
                            androidx.compose.ui.graphics.lerp(
                                containerColor,
                                skinTint.copy(alpha = containerColor.alpha.coerceAtLeast(if (glassEnabled) 0.56f else 0.90f)),
                                if (glassEnabled) 0.34f else 0.65f,
                            )
                        } else {
                            containerColor
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .biliPaiFloatingDockShell(
                                    backdrop = backdrop,
                                    interactionLayerBlock = homeElasticState.layerBlock.takeIf { homeGlassMotion },
                                    interactionState = homeElasticState.takeIf { homeGlassMotion },
                                    containerColor = skinCollapsedContainerColor,
                                    pressProgress = 0f,
                                    shape = shape,
                                    enabled = glassEnabled,
                                    blurEnabled = blurEnabled,
                                    hazeState = hazeState,
                                    liquidGlassTuning = liquidGlassTuning,
                                )
                        )
                        val collapsedSkinIconPath = uiSkinDecoration?.iconPathFor(
                            item = currentItem,
                            selected = true,
                            visibleItems = visibleItems,
                        )
                        Box(
                            modifier = if (glassMorphMotion) {
                                Modifier.layout { measurable, _ ->
                                    val s = compactControlSize()
                                    val p = measurable.measure(Constraints.fixed(s, s))
                                    layout(s, s) { p.placeRelative(0, 0) }
                                }
                            } else {
                                Modifier
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (collapsedSkinIconPath != null) {
                                BottomBarSkinIcon(
                                    iconPath = collapsedSkinIconPath,
                                    contentDescription = "$currentLabel，展开底栏",
                                    selected = true,
                                    size = resolveBottomBarCompactSkinHomeIconSize(),
                                    modifier = if (homeGlassMotion) {
                                        Modifier.graphicsLayer(homeElasticState.layerBlock)
                                    } else {
                                        Modifier
                                    },
                                )
                            } else {
                                AppIcon(
                                    imageVector = if (iconStyle == SharedFloatingBottomBarIconStyle.MIUIX) {
                                        resolveHomeNavigationBarIcon(currentItem, selected = true)
                                    } else resolveMaterialBottomBarIcon(currentItem, selected = true),
                                    contentDescription = "$currentLabel，展开底栏",
                                    tint = navigationColor,
                                    modifier = if (homeGlassMotion) Modifier.graphicsLayer(homeElasticState.layerBlock) else Modifier,
                                )
                            }
                        }
                    }
                }
                if (nowPlayingSlot != null) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = resolveAudioNowPlayingPresenceAlpha(presenceProgressProvider())
                                // 首次出现：从下方上滑落位，弹簧 overshoot 时越过终点
                                // 再回落即为轻微回弹；非入场（退出/静止）不附加位移。
                                translationY = if (!slotEntering.value || reduceMotion) {
                                    0f
                                } else {
                                    (1f - presence.value) *
                                        AUDIO_NOW_PLAYING_PRESENCE_ENTER_SLIDE_DP.dp.toPx()
                                }
                            }
                            .layout { measurable, _ ->
                                // 小横条槽宽度真实随 morph/presence 变化，是唯一保留
                                // 每帧测量的子树；读进度只触发本槽自身的重新测量。
                                val geometry = resolveLinkedDockGeometry(
                                    width = maximumWidth,
                                    button = button,
                                    barHeight = barHeight,
                                    gap = gap,
                                    hasAudio = true,
                                    searchEnabled = searchEnabled,
                                    mergeProgress = merge.value,
                                    searchProgress = search.value,
                                    verticalGap = verticalGap,
                                    presenceProgress = slotGeometryPresenceProvider(),
                                )
                                // 高度跟随封面与内容留白，合体时继续收紧，不拉伸到导航行高。
                                val audioHeight = with(density) {
                                    com.android.purebilibili.feature.audio.screen.resolveAudioNowPlayingBarHeightDp(
                                        merge.value, search.value,
                                    ).dp.roundToPx()
                                }
                                val placeable = measurable.measure(
                                    Constraints.fixed(geometry.audioWidth, audioHeight)
                                )
                                layout(geometry.audioWidth, barHeight) {
                                    val top = ((barHeight - audioHeight) *
                                        (1f - 0.5f * merge.value.coerceIn(0f, 1f))).roundToInt()
                                    placeable.placeRelative(0, top)
                                }
                            }
                            .offset {
                                val geometry = resolveLinkedDockGeometry(
                                    width = maximumWidth,
                                    button = button,
                                    barHeight = barHeight,
                                    gap = gap,
                                    hasAudio = true,
                                    searchEnabled = searchEnabled,
                                    mergeProgress = merge.value,
                                    searchProgress = search.value,
                                    verticalGap = verticalGap,
                                    presenceProgress = slotGeometryPresenceProvider(),
                                )
                                // 外层槽位保留导航行高，紧凑横条在槽内贴底，展开时底边贴导航行上方。
                                // verticalGap 处；收合时槽与导航行完全重合（圆心同中心）。
                                // 液态玻璃路径允许垂直轴保留轻微自然过冲（~2.5%），还原录屏落槽回弹。
                                val verticalMerge = if (glassMorphMotion) {
                                    mergeVertical.value.coerceIn(-0.04f, 1.04f)
                                } else {
                                    mergeVertical.value.coerceIn(0f, 1f)
                                }
                                IntOffset(
                                    geometry.audioX,
                                    navRowY - verticalGap - barHeight +
                                        ((barHeight + verticalGap) * verticalMerge).roundToInt(),
                                )
                            },
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.android.purebilibili.core.ui.LocalVisualWorkAllowed provides
                                (com.android.purebilibili.core.ui.LocalVisualWorkAllowed.current && hasAudio),
                        ) {
                            nowPlayingSlot.invoke(
                                Modifier.fillMaxSize(),
                                mergeProgressProvider,
                                searchProgressProvider,
                                zeroProgressProvider,
                                if (shouldExpandPlaybackFromSearch(phase, hasAudio)) {
                                    {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        updatePhase(LinkedDockPhase.Playback)
                                    }
                                } else {
                                    null
                                },
                                nowPlayingLayoutStable,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .offset {
                            val geometry = resolveLinkedDockGeometry(
                                width = maximumWidth,
                                button = button,
                                barHeight = barHeight,
                                gap = gap,
                                hasAudio = hasAudio,
                                searchEnabled = searchEnabled,
                                mergeProgress = merge.value,
                                searchProgress = search.value,
                                verticalGap = verticalGap,
                            )
                            IntOffset(
                                resolveLinkedDockSearchX(
                                    maximumWidth = maximumWidth,
                                    navigationWidth = navWidth,
                                    searchWidth = geometry.searchWidth,
                                    button = button,
                                    gap = gap,
                                    mergeProgress = merge.value,
                                    searchProgress = search.value,
                                    searchInsideDock = effectiveSearchInsideDock,
                                ),
                                controlRowY + (controlHeight - searchVisualHeight()) / 2,
                            )
                        }
                        .layout { measurable, _ ->
                            val geometry = resolveLinkedDockGeometry(
                                width = maximumWidth,
                                button = button,
                                barHeight = barHeight,
                                gap = gap,
                                hasAudio = hasAudio,
                                searchEnabled = searchEnabled,
                                mergeProgress = merge.value,
                                searchProgress = search.value,
                                verticalGap = verticalGap,
                            )
                            val width = (compactControlSize() +
                                (geometry.searchWidth - compactControlSize()) *
                                search.value.coerceIn(0f, 1f)).roundToInt().coerceAtMost(geometry.searchWidth)
                            val placeable = measurable.measure(
                                Constraints.fixed(width, searchVisualHeight())
                            )
                            layout(geometry.searchWidth, searchVisualHeight()) {
                                if (!effectiveSearchInsideDock || maxOf(merge.value, search.value) > 0.005f) {
                                    placeable.placeRelative((geometry.searchWidth - width) / 2, 0)
                                }
                            }
                        }
                        .graphicsLayer {
                            if (effectiveSearchInsideDock) {
                                val detachedAlpha = resolveLinkedDockDetachedSearchAlpha(
                                    mergeProgress = merge.value,
                                    searchProgress = search.value,
                                    searchInsideDock = true,
                                )
                                alpha = detachedAlpha
                                val detachedScale = 0.86f + 0.14f * detachedAlpha
                                scaleX = detachedScale
                                scaleY = detachedScale
                            }
                        },
                ) {
                    if (searchEnabled) {
                        val searchGlassMotion = glassEnabled && backdrop != null
                        // The shell and hit target are siblings. Deform their common
                        // parent so glass, icon and pointer feedback stay together.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .appElasticPress(
                                    enabled = phase != LinkedDockPhase.Search,
                                    state = searchElasticState,
                                    transformInBackdrop = searchGlassMotion,
                                )
                        ) {
                            if (!fusedDockActive) {
                                val skinSearchContainerColor = if (uiSkinDecoration != null) {
                                    val skinTint = uiSkinDecoration.liquidGlassSurfaceTint ?: uiSkinDecoration.bottomTrimTint
                                    androidx.compose.ui.graphics.lerp(
                                        containerColor,
                                        skinTint.copy(alpha = containerColor.alpha.coerceAtLeast(if (glassEnabled) 0.62f else 0.90f)),
                                        if (glassEnabled) 0.28f else 0.45f,
                                    )
                                } else {
                                    containerColor
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .biliPaiFloatingDockShell(
                                            backdrop = backdrop,
                                            interactionLayerBlock = searchElasticState.layerBlock.takeIf { searchGlassMotion },
                                            interactionState = searchElasticState.takeIf { searchGlassMotion },
                                            containerColor = skinSearchContainerColor,
                                            pressProgress = 0f,
                                            shape = shape,
                                            enabled = glassEnabled,
                                            blurEnabled = blurEnabled,
                                            hazeState = hazeState,
                                            liquidGlassTuning = liquidGlassTuning,
                                        )
                                )
                            }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .then(if (searchGlassMotion) Modifier.graphicsLayer(searchElasticState.layerBlock) else Modifier)
                                    .clip(shape)
                                    .then(
                                        if (phase != LinkedDockPhase.Search) {
                                            Modifier.clickable(role = Role.Button) {
                                                if (effectiveSearchInsideDock) {
                                                    searchHaptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                                                    onSearchClick()
                                                } else {
                                                    openSearch()
                                                }
                                            }
                                        } else Modifier
                                    )
                            ) {
                                BiliPaiBottomBarSearchVisualContent(
                                    expanded = phase == LinkedDockPhase.Search ||
                                        phase == LinkedDockPhase.Compact,
                                    query = query,
                                    onQueryChange = { query = it },
                                    onSubmit = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        val keyword = query.trim()
                                        query = ""
                                        if (keyword.isBlank()) {
                                            onSearchClick()
                                        } else {
                                            onSearchKeywordSubmit(keyword)
                                            pendingUserImeRequest = false
                                            updatePhase(
                                                resolveLinkedDockPhaseOnSearchDismiss(
                                                    hasAudio = hasAudio,
                                                    previousPhase = phaseBeforeSearch,
                                                    searchMergeEnabled = searchMergeEnabled,
                                                )
                                            )
                                        }
                                    },
                                    contentColor = contentColor,
                                    accentColor = accentColor,
                                    iconScale = identityIconScaleProvider,
                                    fieldAlpha = searchProgressProvider,
                                    interactive = phase == LinkedDockPhase.Search,
                                    iconStyle = iconStyle,
                                    glassSearchVisuals = glassEnabled,
                                    pendingUserImeRequest = pendingUserImeRequest,
                                    onUserImeRequestConsumed = { pendingUserImeRequest = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
