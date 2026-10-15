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
import com.android.purebilibili.core.ui.motion.emphasizedEnterTween
import com.android.purebilibili.core.ui.motion.emphasizedExitTween
import com.android.purebilibili.core.ui.motion.iosMorphTween
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.feature.audio.screen.AUDIO_NOW_PLAYING_PRESENCE_ENTER_SLIDE_DP
import com.android.purebilibili.feature.audio.screen.LINKED_DOCK_MERGE_DURATION_MILLIS
import com.android.purebilibili.feature.audio.screen.LINKED_DOCK_SEARCH_DURATION_MILLIS
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
    animateNowPlayingPresence: Boolean = true,
    hasUiSkinDecoration: Boolean = false,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = false,
    hazeState: HazeState? = null,
    navigationContent: @Composable () -> Unit,
) {
    com.android.purebilibili.core.ui.components.ProvideAppElasticFeedback {
        val hasAudio = nowPlayingContent != null
        var internalPhase by remember(currentItem, searchEnabled) {
            mutableStateOf(
                resolveLinkedDockInitialPhase(
                    currentItem = currentItem,
                    collapseRequested = collapseRequested,
                    hasAudio = hasAudio,
                    savedPhase = dockPhase,
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
        LaunchedEffect(hasAudio, dockPhase) {
            if (dockPhase == null) {
                internalPhase = resolveLinkedDockPhaseOnAudioChange(internalPhase, hasAudio)
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
        val currentPhase by rememberUpdatedState(phase)
        val scrolling by rememberUpdatedState(isFeedScrollInProgress)
        val threshold = with(LocalDensity.current) { 24.dp.toPx() }
        LaunchedEffect(currentItem, hasAudio, searchEnabled, scroll, threshold, isTopLevelDestination, mergeOnScrollDownEnabled) {
            var previous = scroll.floatValue
            var accumulated = 0f
            snapshotFlow { scroll.floatValue to scrolling }.collect { (offset, active) ->
                val delta = offset - previous
                previous = offset
                if (!active || !isTopLevelDestination || currentPhase == LinkedDockPhase.Search) {
                    accumulated = 0f
                } else if (!mergeOnScrollDownEnabled) {
                    // 用户选择下滑不合体：只保留向上滚动回到展开（分体）的能力。
                    accumulated = accumulateDockScroll(accumulated, delta)
                    if ((offset <= 0f && delta < 0f) || accumulated <= -threshold) {
                        updatePhase(LinkedDockPhase.Expanded)
                        accumulated = 0f
                    }
                } else {
                    accumulated = accumulateDockScroll(accumulated, delta)
                    if ((offset <= 0f && delta < 0f) || accumulated <= -threshold) {
                        updatePhase(LinkedDockPhase.Expanded)
                        accumulated = 0f
                    } else if (hasAudio && accumulated >= threshold) {
                        updatePhase(LinkedDockPhase.Playback)
                        accumulated = 0f
                    }
                }
            }
        }
        // Keep the dock phase while a child destination covers the current tab. Keying this effect
        // by isTopLevelDestination made the returning page re-expand/re-collapse the playback strip,
        // which also shifted the predictive-back target after the gesture had started.
        // Skip while the list is scrolling so resting phase does not fight scroll-driven search size.
        LaunchedEffect(currentItem, collapseRequested, hasAudio) {
            if (
                isTopLevelDestination &&
                currentItem != BottomNavItem.HOME &&
                currentPhase != LinkedDockPhase.Search &&
                !isFeedScrollInProgress
            ) {
                updatePhase(resolveLinkedDockRestingPhase(collapseRequested, hasAudio))
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
            updatePhase(resolveLinkedDockPhaseOnSearchDismiss(hasAudio, phaseBeforeSearch))
        }
        val reduceMotion = rememberSystemReduceMotion()
        val transition = updateTransition(targetState = phase, label = "linkedBottomDock")
        // 合体与解体使用方向感知曲线：收拢走 emphasized-exit（中段加速的压缩感），
        // 展开走 emphasized-enter（快起缓收的弹开感），比对称 easeInOut 更有方向性。
        val merge = transition.animateFloat(
            transitionSpec = {
                if (reduceMotion) {
                    snap()
                } else if (targetState == LinkedDockPhase.Expanded) {
                    emphasizedEnterTween(LINKED_DOCK_MERGE_DURATION_MILLIS + 20)
                } else {
                    emphasizedExitTween(LINKED_DOCK_MERGE_DURATION_MILLIS)
                }
            },
            label = "dockMerge",
        ) { if (it == LinkedDockPhase.Expanded) 0f else 1f }
        val search = transition.animateFloat(
            transitionSpec = {
                if (reduceMotion) snap() else iosMorphTween(LINKED_DOCK_SEARCH_DURATION_MILLIS)
            },
            label = "dockSearch",
        ) { if (it == LinkedDockPhase.Search || it == LinkedDockPhase.Compact) 1f else 0f }
        val imeSettled = WindowInsets.ime
            .getBottom(LocalDensity.current) == 0
        val nowPlayingLayoutStable = !transition.isRunning && imeSettled
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
        var keepSlotComposed by remember { mutableStateOf(hasAudio) }
        val slotEntering = remember { mutableStateOf(false) }
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

        val shape = resolveSharedBottomBarCapsuleShape()
        val contentColor = AppContentColors.primaryText()
        // Only glass accents use the exact picked color; native and neutral actions retain their roles.
        val accentColor = AppContentColors.selectionAccent(glassEnabled)
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
                ).roundToPx()
                controlHeight = barHeight
                gap = 8.dp.roundToPx()
                verticalGap = 4.dp.roundToPx()
            }
            val searchHeight = button
            // 容器高度固定为两行。小横条行收放只改变上方透明区，morph 进度不再进入
            // 容器 measure，导航行/首按钮/搜索胶囊不会被拖动每帧重测量（官方 deferred
            // reads 规范：帧率状态读取只应触发 placement/绘制）。
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

            // 折叠落定时不组合导航行（等价旧实现 progress>=0.999 不放置），
            // 避免透明导航层在静止折叠态拦截底栏区域外的触摸。
            val collapsedAtRest = phase != LinkedDockPhase.Expanded && !transition.isRunning

            Box(
                modifier = Modifier.size(
                    with(density) { maximumWidth.toDp() },
                    with(density) { containerHeight.toDp() },
                )
            ) {
                if (!collapsedAtRest) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(navigationX, navRowY) }
                            .size(
                                with(density) { navWidth.toDp() },
                                with(density) { barHeight.toDp() },
                            )
                            .graphicsLayer {
                                // 退出窗口（前 34%）ease-in 淡出；全程向左侧圆钮方向
                                // 轻微收拢缩放，读作"并入圆钮"而非原地溶解。
                                val exitProgress = (merge.value / 0.34f).coerceIn(0f, 1f)
                                alpha = 1f - EaseInCubic.transform(exitProgress)
                                val collapse = EaseOutCubic.transform(merge.value)
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
                        navigationContent()
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
                                alpha = eased
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
                            tint = accentColor,
                            modifier = if (homeGlassMotion) Modifier.graphicsLayer(homeElasticState.layerBlock) else Modifier,
                        )
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
                                ),
                                controlRowY,
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
                                Constraints.fixed(geometry.searchWidth, searchHeight)
                            )
                            layout(geometry.searchWidth, searchHeight) {
                                placeable.placeRelative(0, 0)
                            }
                        },
                ) {
                    if (searchEnabled) {
                        val searchElasticState = rememberAppElasticPressState(
                            feedbackStrength = com.android.purebilibili.core.ui.components.APP_SEARCH_ELASTIC_FEEDBACK_STRENGTH,
                        )
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
