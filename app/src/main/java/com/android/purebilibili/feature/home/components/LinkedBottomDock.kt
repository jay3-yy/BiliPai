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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.rememberAppElasticPressState
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.ui.graphics.TransformOrigin
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.feature.audio.screen.AUDIO_NOW_PLAYING_PRESENCE_ENTER_SLIDE_DP
import com.android.purebilibili.feature.audio.screen.resolveAudioNowPlayingPresenceAlpha
import com.android.purebilibili.feature.audio.screen.resolveAudioNowPlayingPresenceAnimationSpec
import com.android.purebilibili.feature.audio.screen.resolveAudioNowPlayingPresenceEnterSpringSpec
import com.android.purebilibili.feature.home.LocalHomeScrollOffset
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
    firstItem: BottomNavItem,
    firstLabel: String,
    searchEnabled: Boolean,
    isFeedScrollInProgress: Boolean,
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
    currentLabel: String = firstLabel,
    animateNowPlayingPresence: Boolean = true,
    hasUiSkinDecoration: Boolean = false,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = false,
    hazeState: HazeState? = null,
    navigationContent: @Composable () -> Unit,
) {
    com.android.purebilibili.core.ui.components.ProvideAppElasticFeedback {
        val searchHaptic = com.android.purebilibili.core.util.rememberHapticFeedback(
            com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION,
        )
        val hasAudio = nowPlayingContent != null
        val searchMergeEnabled = searchEnabled && mergeOnScrollDownEnabled
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
        LaunchedEffect(phase) {
            if (phase == LinkedDockPhase.Search) compactNavigation = false
        }
        LaunchedEffect(hasAudio, searchMergeEnabled) {
            val reconciled = resolveLinkedDockPhaseOnAudioChange(phase, hasAudio, searchMergeEnabled)
            if (reconciled != phase) updatePhase(reconciled)
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
        val currentPhase by rememberUpdatedState(phase)
        val latestUpdatePhase by rememberUpdatedState(updatePhase)
        val scrolling by rememberUpdatedState(isFeedScrollInProgress)
        val threshold = with(LocalDensity.current) { 24.dp.toPx() }
        LaunchedEffect(currentItem, hasAudio, searchEnabled, scroll, threshold, isTopLevelDestination, mergeOnScrollDownEnabled, glassEnabled, useNavigationCompact) {
            var previous = scroll.floatValue
            var accumulated = 0f
            snapshotFlow { scroll.floatValue to scrolling }.collect { (offset, active) ->
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
                        searchEnabled = searchEnabled,
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
        LaunchedEffect(currentItem, collapseRequested, hasAudio, useNavigationCompact, searchMergeEnabled, mergeOnScrollDownEnabled) {
            if (
                isTopLevelDestination && !useNavigationCompact &&
                currentItem != BottomNavItem.HOME &&
                currentPhase != LinkedDockPhase.Search &&
                !isFeedScrollInProgress
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
        val reduceMotion = rememberSystemReduceMotion() ||
            !com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
        val dockMotionStyle = rememberGlassDockMotionStyle(
            enabled = glassEnabled && !hasUiSkinDecoration,
        )
        val transition = updateTransition(targetState = phase, label = "linkedBottomDock")
        // 共用弹簧保持合体、解体和中途反向时的速度连续。
        val merge = transition.animateFloat(
            transitionSpec = {
                if (reduceMotion) {
                    snap()
                } else {
                    glassDockMorphSpring(dockMotionStyle, expanding = linkedDockMotionExpanding(initialState, targetState))
                }
            },
            label = "dockMerge",
        ) { if (it == LinkedDockPhase.Expanded) 0f else 1f }
        val search = transition.animateFloat(
            transitionSpec = {
                if (reduceMotion) snap() else glassDockMorphSpring(dockMotionStyle,
                    expanding = linkedDockMotionExpanding(initialState, targetState))
            },
            label = "dockSearch",
        ) { if (it == LinkedDockPhase.Search || it == LinkedDockPhase.Compact) 1f else 0f }
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
        val nowPlayingLayoutStable = !transition.isRunning && !presence.isRunning && imeSettled
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
            modifier = modifier.fillMaxWidth().imePadding().navigationBarsPadding()
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
            with(density) {
                maximumWidth = constraints.maxWidth.coerceAtMost(600.dp.roundToPx())
                button = resolveBiliPaiBottomBarSearchCircleSize().roundToPx()
                barHeight = resolveBiliPaiBottomBarDockHeight(
                    searchExpanded = false,
                    hasUiSkinDecoration = hasUiSkinDecoration,
                    nativeMiuixNavigation = nativeMiuixNavigation,
                ).roundToPx()
                controlHeight = barHeight
                gap = 8.dp.roundToPx()
                verticalGap = 4.dp.roundToPx()
            }
            val searchHeight = button
            val expandedGlassSearchHeight = with(density) { 48.dp.roundToPx() }
            // 玻璃合体搜索从圆按钮过渡到 48dp 输入框；原生路径保持原有高度。
            fun searchVisualHeight(): Int = if (glassEnabled) {
                (searchHeight + (expandedGlassSearchHeight - searchHeight) * search.value.coerceIn(0f, 1f)).roundToInt()
            } else searchHeight
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
            val reservedSearchWidth = if (searchEnabled) button + gap else 0
            val expandedNavigationWidth = preferredNavigationWidth.coerceAtMost(
                (maximumWidth - reservedSearchWidth).coerceAtLeast(0)
            )
            val navWidth = expandedNavigationWidth.coerceAtMost(maximumWidth)
            val navigationX = resolveLinkedDockNavigationX(
                maximumWidth = maximumWidth,
                navigationWidth = navWidth,
                button = button,
                gap = gap,
                searchEnabled = searchEnabled,
            )
            // 小横条展开态与底栏整簇（导航胶囊 + 搜索圆钮）同宽同起点：
            // 两行胶囊长度一致、左右边缘对齐。
            val bottomBarClusterWidth = (navWidth + reservedSearchWidth).coerceAtMost(maximumWidth)

            // Glass keeps the navigation/backdrop mounted across both endpoints. At the compact
            // endpoint skip placement so the hidden row neither draws nor intercepts touches.
            // Native toolbars retain their original composition and motion behavior.
            val collapsedAtRest = phase != LinkedDockPhase.Expanded && !transition.isRunning

            // Glass alone owns the transitional union. Skin chrome, low-budget devices and
            // native MD3/Miuix keep their existing independent surfaces and default geometry.
            val contact = remember { LinkedDockContact() }
            val searchElasticState = rememberAppElasticPressState(
                feedbackStrength = com.android.purebilibili.core.ui.components.APP_SEARCH_ELASTIC_FEEDBACK_STRENGTH,
            )
            val fusionGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
            val fusionEnabled = glassEnabled && backdrop != null && !hasUiSkinDecoration &&
                !reduceMotion && !useNavigationCompact && navWidth > 0 && maximumWidth >= button &&
                top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported() &&
                !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
            val ownership = rememberUpdatedState<() -> Float> {
                if (fusionEnabled && isTopLevelDestination && fusionGate.active &&
                    (nowPlayingSlot == null || (hasAudio && !slotEntering.value && presence.value == 1f))
                ) {
                    if (searchEnabled && contact.navigationBounds != null) {
                        // 展开端也由共享材质持有，开始合体时不先闪回独立外壳。
                        val p = merge.value.coerceIn(0f, 1f)
                        val handoff = ((1f - p) / 0.08f).coerceIn(0f, 1f)
                        handoff * handoff * (3f - 2f * handoff)
                    } else linkedDockGlassOwnership(merge.value)
                } else 0f
            }
            val ownershipProvider = remember { { ownership.value() } }
            val shellAlphaProvider = remember { { 1f - ownership.value() } }
            val layoutDirection = LocalLayoutDirection.current
            val liveFrame = rememberUpdatedState<() -> LinkedDockGlassFrame> {
                val p = merge.value.coerceIn(0f, 1f)
                val collapse = EaseOutCubic.transform(p)
                val geometry = resolveLinkedDockGeometry(
                    width = maximumWidth, button = button, barHeight = barHeight, gap = gap,
                    hasAudio = hasAudio, searchEnabled = searchEnabled,
                    mergeProgress = p, searchProgress = search.value, verticalGap = verticalGap,
                    expandedAudioWidth = bottomBarClusterWidth, expandedAudioX = navigationX,
                )
                fun physical(rect: Rect): Rect = if (layoutDirection == LayoutDirection.Rtl) {
                    Rect(maximumWidth - rect.right, rect.top, maximumWidth - rect.left, rect.bottom)
                } else rect
                val navLeft = navigationX * (1f - collapse)
                val navHeight = barHeight * (1f - collapse) + controlHeight * collapse
                val navTop = navRowY + (barHeight - navHeight) / 2f
                val navRect = Rect(navLeft, navTop,
                    navLeft + navWidth * (1f - collapse) + button * collapse, navTop + navHeight)
                val playbackTop = navRowY - verticalGap - barHeight +
                    ((barHeight + verticalGap) * p).roundToInt()
                val searchLeft = resolveLinkedDockSearchX(
                    maximumWidth, navWidth, geometry.searchWidth, button, gap, p, search.value,
                ).toFloat()
                val searchOvershoot = (search.value - search.value.coerceIn(0f, 1f)) * with(density) { 24.dp.toPx() }
                val searchTop = controlRowY + (controlHeight - searchVisualHeight()) / 2
                val deformedNavigation = if (searchEnabled && p <= 0f) {
                    contact.navigationBounds?.invoke(physical(navRect))
                } else null
                val contactMode = deformedNavigation != null
                LinkedDockGlassFrame(
                    navigation = deformedNavigation ?: physical(navRect),
                    playback = if (!contactMode && hasAudio && geometry.audioWidth > 0) physical(Rect(
                        geometry.audioX.toFloat(), playbackTop.toFloat(),
                        (geometry.audioX + geometry.audioWidth).toFloat(), (playbackTop + barHeight).toFloat(),
                    )) else null,
                    search = if (searchEnabled) {
                        val bounds = physical(Rect(searchLeft, searchTop.toFloat(),
                            searchLeft + geometry.searchWidth, (searchTop + searchVisualHeight()).toFloat()))
                            .translate(Offset(searchOvershoot, 0f))
                        if (contactMode) searchElasticState.transformedBounds(bounds) else bounds
                    } else null,
                    // 接触带按当前底栏按压扩张量适配，静止的 8dp 间距仍保持分离。
                    smoothing = if (contactMode) 10f * density.density else linkedDockGlassSmoothing(p, density.density),
                    contactMode = contactMode,
                )
            }
            val frameProvider = remember { { liveFrame.value() } }
            Box(
                modifier = Modifier.size(
                    with(density) { maximumWidth.toDp() },
                    with(density) { containerHeight.toDp() },
                ).then(fusionGate.modifier)
            ) {
                CompositionLocalProvider(
                    LocalLinkedDockGlassShellAlpha provides shellAlphaProvider.takeIf { fusionEnabled },
                    LocalLinkedDockContact provides contact.takeIf { fusionEnabled },
                ) {
                    if (fusionEnabled && backdrop != null) {
                        LinkedDockGlassSurface(
                            backdrop = backdrop,
                            containerColor = containerColor,
                            tuning = liquidGlassTuning,
                            frameProvider = frameProvider,
                            ownershipProvider = ownershipProvider,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    if (glassEnabled || !collapsedAtRest) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(navigationX, navRowY) }
                                .size(
                                    with(density) { navWidth.toDp() },
                                    with(density) { barHeight.toDp() },
                                )
                                .then(
                                    if (glassEnabled) Modifier.layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        layout(placeable.width, placeable.height) {
                                            if (merge.value < 1f) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        }
                                    } else Modifier
                                )
                                .graphicsLayer {
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
                                    onExpand = { compactNavigation = false },
                                    content = navigationContent,
                                )
                            } else navigationContent()
                        }
                    }
                    val homeElasticState = rememberAppElasticPressState(
                        visualSize = with(density) { controlHeight.toDp() },
                    )
                    val homeGlassMotion = glassEnabled && backdrop != null
                    val homeDragEnabled = phase != LinkedDockPhase.Expanded &&
                        com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
                        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.globalDragFeedbackEnabled
                    var homeDragActive by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(0, controlRowY) }
                            .size(
                                with(density) { button.toDp() },
                                with(density) { controlHeight.toDp() },
                            ),
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
                                .then(
                                    if (phase != LinkedDockPhase.Expanded) {
                                        Modifier.clickable(role = Role.Button) { expand() }
                                    } else {
                                        Modifier.clearAndSetSemantics {}
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .biliPaiFloatingDockShell(
                                        backdrop = backdrop,
                                        interactionLayerBlock = homeElasticState.layerBlock.takeIf { homeGlassMotion },
                                        interactionState = homeElasticState.takeIf { homeGlassMotion },
                                        containerColor = containerColor,
                                        pressProgress = 0f,
                                        shape = shape,
                                        enabled = glassEnabled,
                                        blurEnabled = blurEnabled,
                                        hazeState = hazeState,
                                        liquidGlassTuning = liquidGlassTuning,
                                    )
                            )
                            AppIcon(
                                imageVector = if (iconStyle == SharedFloatingBottomBarIconStyle.MIUIX) {
                                    resolveHomeNavigationBarIcon(firstItem, currentItem == firstItem)
                                } else resolveMaterialBottomBarIcon(firstItem, currentItem == firstItem),
                                contentDescription = "$firstLabel，展开底栏",
                                tint = navigationColor,
                                modifier = if (homeGlassMotion) Modifier.graphicsLayer(homeElasticState.layerBlock) else Modifier,
                            )
                        }
                    }
                    if (nowPlayingSlot != null) {
                        val playbackShellAlpha = remember { { if (merge.value <= 0f) 1f else shellAlphaProvider() } }
                        CompositionLocalProvider(LocalLinkedDockGlassShellAlpha provides playbackShellAlpha.takeIf { fusionEnabled }) {
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
                                            expandedAudioWidth = bottomBarClusterWidth,
                                            expandedAudioX = navigationX,
                                        )
                                        // 槽高与导航行同为 barHeight：两行胶囊圆角（percent=50）
                                        // 严格一致，不会因槽更矮而出现更小的圆角。
                                        val placeable = measurable.measure(
                                            Constraints.fixed(geometry.audioWidth, barHeight)
                                        )
                                        layout(geometry.audioWidth, barHeight) {
                                            placeable.placeRelative(0, 0)
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
                                            expandedAudioWidth = bottomBarClusterWidth,
                                            expandedAudioX = navigationX,
                                        )
                                        // 槽高已与导航行同为 barHeight：展开时槽底贴导航行上方
                                        // verticalGap 处；收合时槽与导航行完全重合（圆心同中心）。
                                        IntOffset(
                                            geometry.audioX,
                                            navRowY - verticalGap - barHeight +
                                                ((barHeight + verticalGap) * merge.value).roundToInt(),
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
                                                if (!transition.isRunning) {
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                    updatePhase(LinkedDockPhase.Playback)
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                        nowPlayingLayoutStable,
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                // Geometry remains bounded; the spring's overshoot is visible at the edge.
                                val overshoot = search.value - search.value.coerceIn(0f, 1f)
                                translationX = overshoot * 24.dp.toPx()
                            }
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
                                val placeable = measurable.measure(
                                    Constraints.fixed(geometry.searchWidth, searchVisualHeight())
                                )
                                layout(geometry.searchWidth, searchVisualHeight()) {
                                    placeable.placeRelative(0, 0)
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
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .biliPaiFloatingDockShell(
                                            backdrop = backdrop,
                                            interactionLayerBlock = searchElasticState.layerBlock.takeIf { searchGlassMotion },
                                            interactionState = searchElasticState.takeIf { searchGlassMotion },
                                            containerColor = containerColor,
                                            pressProgress = 0f,
                                            shape = shape,
                                            enabled = glassEnabled,
                                            blurEnabled = blurEnabled,
                                            hazeState = hazeState,
                                            liquidGlassTuning = liquidGlassTuning,
                                        )
                                )
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(if (searchGlassMotion) Modifier.graphicsLayer(searchElasticState.layerBlock) else Modifier)
                                        .clip(shape)
                                        .then(
                                            if (phase != LinkedDockPhase.Search) {
                                                Modifier.clickable(role = Role.Button) {
                                                    searchHaptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                                                    phaseBeforeSearch = phase
                                                    pendingUserImeRequest = true
                                                    updatePhase(LinkedDockPhase.Search)
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
}
