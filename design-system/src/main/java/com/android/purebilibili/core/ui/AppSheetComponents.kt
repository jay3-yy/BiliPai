package com.android.purebilibili.core.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.resolveAndroidNativeChromeTokens
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class AdaptiveBottomSheetVisualSpec(
    val cornerRadiusDp: Int,
    val useMaterialDragHandle: Boolean
)

enum class AppModalPresentation {
    BottomSheet,
    CenteredDialog,
    SideSheet,
}

data class AppModalLayoutSpec(
    val presentation: AppModalPresentation,
    val maxWidthDp: Int,
    val maxHeightFraction: Float,
)

fun resolveAppModalLayoutSpec(
    windowWidthDp: Int,
    miuixNonGlass: Boolean = false,
): AppModalLayoutSpec = when {
    windowWidthDp < 600 -> AppModalLayoutSpec(
        presentation = AppModalPresentation.BottomSheet,
        maxWidthDp = windowWidthDp,
        maxHeightFraction = 1f,
    )
    miuixNonGlass -> AppModalLayoutSpec(
        presentation = AppModalPresentation.CenteredDialog,
        maxWidthDp = 420,
        maxHeightFraction = 0.86f,
    )
    windowWidthDp < 1200 -> AppModalLayoutSpec(
        presentation = AppModalPresentation.CenteredDialog,
        maxWidthDp = 640,
        maxHeightFraction = 0.86f,
    )
    else -> AppModalLayoutSpec(
        presentation = AppModalPresentation.CenteredDialog,
        maxWidthDp = 720,
        maxHeightFraction = 0.86f,
    )
}

internal data class AdaptiveBottomSheetMotionSpec(
    val scrimEnterDurationMillis: Int,
    val scrimExitDurationMillis: Int,
    val contentEnterFadeDurationMillis: Int,
    val contentExitFadeDurationMillis: Int
)

fun resolveAdaptiveBottomSheetVisualSpec(
    uiStyle: AppUiStyle,
    miuixNonGlass: Boolean = false,
): AdaptiveBottomSheetVisualSpec {
    val level = if (miuixNonGlass) ContainerLevel.Sheet else ContainerLevel.Pill
    val cornerRadiusDp = AppShapes.resolveContainerCornerDp(
        level = level,
        uiStyle = uiStyle,
        liquidGlassEnabled = !miuixNonGlass,
    ).value.toInt()
    return AdaptiveBottomSheetVisualSpec(
        cornerRadiusDp = cornerRadiusDp,
        useMaterialDragHandle = true,
    )
}

internal fun resolveAdaptiveBottomSheetMotionSpec(
    uiStyle: AppUiStyle,
    reducedMotion: Boolean = false,
): AdaptiveBottomSheetMotionSpec {
    if (reducedMotion) return AdaptiveBottomSheetMotionSpec(120, 90, 120, 90)
    val tokens = resolveAndroidNativeChromeTokens(uiStyle)
    return AdaptiveBottomSheetMotionSpec(
        scrimEnterDurationMillis = tokens.motionEmphasizedMillis,
        scrimExitDurationMillis = tokens.expressiveMotionDurationMillis,
        contentEnterFadeDurationMillis = tokens.motionEmphasizedMillis,
        contentExitFadeDurationMillis = tokens.expressiveMotionDurationMillis
    )
}

/** Window-level hosts do not depend on an AdaptiveScaffold overlay host. */
enum class BottomSheetHost {
    MIUIX_WINDOW,
    MATERIAL3,
}

fun resolveBottomSheetHost(uiStyle: AppUiStyle): BottomSheetHost = when (uiStyle) {
    AppUiStyle.MIUIX -> BottomSheetHost.MIUIX_WINDOW
    AppUiStyle.MATERIAL3 -> BottomSheetHost.MATERIAL3
}

internal fun bottomSheetScrimEnterTransition(
    uiStyle: AppUiStyle,
): EnterTransition = fadeIn(
    AppMotionTokens.resolveBottomSheetFadeEnterSpec(uiStyle)
)

internal fun bottomSheetScrimExitTransition(
    uiStyle: AppUiStyle,
): ExitTransition = fadeOut(
    AppMotionTokens.resolveBottomSheetFadeExitSpec(uiStyle)
)

internal fun bottomSheetContentEnterTransition(
    uiStyle: AppUiStyle,
): EnterTransition {
    return slideInVertically(
        initialOffsetY = { it },
        animationSpec = AppMotionTokens.resolveBottomSheetSlideSpec(uiStyle)
    ) + fadeIn(
        AppMotionTokens.resolveBottomSheetFadeEnterSpec(uiStyle)
    )
}

internal fun bottomSheetContentExitTransition(
    uiStyle: AppUiStyle,
): ExitTransition {
    return slideOutVertically(
        targetOffsetY = { it },
        animationSpec = AppMotionTokens.resolveBottomSheetSlideExitSpec(uiStyle)
    ) + fadeOut(
        AppMotionTokens.resolveBottomSheetFadeExitSpec<Float>(uiStyle)
    )
}

/**
 * 把返回事件绑到弹层所在 Dialog 窗口，而不是下层路由。
 *
 * 系统侧边/预测返回落在 Dialog 自己的 NavigationEventDispatcher 上；若不重绑，
 * handler 会挂在底层 Activity/路由 dispatcher，侧滑无法关闭弹层（点 scrim 仍可关）。
 */
@Composable
fun AppModalSheetNavigationHost(
    dismissOnBackPress: Boolean,
    onDismissRequest: () -> Unit,
    backgroundBlurBehind: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    val back = remember { AppDialogBackState() }
    AppModalSheetWindowNavigationScope {
        AppDialogPredictiveBackHandler(
            state = back,
            enabled = dismissOnBackPress,
            onBackCompleted = onDismissRequest,
        )
        ModalWindowBlurBehindEffect(enabled = backgroundBlurBehind, progress = { 1f - back.progress })
        content(Modifier.appDialogBackMotion(state = back) { back.progress })
    }
}

@Composable
private fun AppModalSheetWindowNavigationScope(content: @Composable () -> Unit) {
    val owner = LocalView.current.findViewTreeNavigationEventDispatcherOwner()
    if (owner == null) {
        content()
    } else {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
            content()
        }
    }
}

@Composable
private fun appModalSheetBackModifier(
    transitionState: NavigationEventTransitionState,
    enabled: Boolean,
): Modifier {
    val transition = transitionState as? NavigationEventTransitionState.InProgress
    val reducedMotion = LocalSystemReduceMotion.current || !LocalComponentMotionEnabled.current
    val backProgress = animateFloatAsState(
        targetValue = if (enabled && !reducedMotion && transition != null &&
            transition.direction == NavigationEventTransitionState.TRANSITIONING_BACK
        ) transition.latestEvent.progress.coerceIn(0f, 1f) else 0f,
        animationSpec = if (transition != null || reducedMotion) snap() else tween(180),
        label = "centered_sheet_predictive_back",
    )
    return Modifier.graphicsLayer {
        // Read gesture frames in the layer; keep the surface inside its adaptive region.
        val progress = backProgress.value
        scaleX = 1f - 0.08f * progress
        scaleY = 1f - 0.08f * progress
    }
}

/**
 * Compact windows use the theme's native sheet; wide windows use a centered dialog.
 * Material SheetState controls the Material branch only. Miuix owns its drag/exit state.
 * Hinge-safe presentation keeps the existing region host to avoid spanning a fold.
 * Controlled callers keep this composable mounted when [visible] becomes false;
 * [onDismissRequest] reports completion after native hiding finishes.
 * [useWindowPredictiveBack] opts the MD3 sheet into window-scoped navigation;
 * Miuix and adaptive dialog hosts keep their existing window gesture handling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape: Shape? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = AppContentColors.primaryText(),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    presentationProgress: Float = 1f,
    dismissOnBackPress: Boolean = true,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    windowInsets: androidx.compose.foundation.layout.WindowInsets = androidx.compose.material3.BottomSheetDefaults.modalWindowInsets,
    presentationOverride: AppModalPresentation? = null,
    sheetSurfaceModifier: Modifier = Modifier,
    backgroundBlurBehind: Boolean = false,
    visible: Boolean = true,
    onDismissStarted: () -> Unit = {},
    useTimedSlideMotion: Boolean = false,
    useWindowPredictiveBack: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val sceneConfig = LocalAppThemeConfig.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppThemeConfig provides sceneConfig.copy(liquidGlassEnabled = sceneConfig.popupGlassEnabled),
    ) popupScene@ {
        // Controlled callers keep this host in composition while native sheets finish hiding.
        // The default remains visible; native theme geometry, material and motion are unchanged.
        var retained by remember { mutableStateOf(visible) }
        val latestDismiss by rememberUpdatedState(onDismissRequest)
        val latestDismissStarted by rememberUpdatedState(onDismissStarted)
        LaunchedEffect(visible) {
            if (visible) retained = true
        }
        val finishDismiss: () -> Unit = {
            if (retained) {
                retained = false
                latestDismiss()
            }
        }
        if (!visible && !retained) return@popupScene
        val uiStyle = LocalAppUiStyle.current
        val miuix = uiStyle == AppUiStyle.MIUIX
        // Collection-style sheets keep the covered page sharp in glass, MD3 and Miuix,
        // including centered/hinge-safe hosts. Surface material and scrim stay independent.
        val blurBehind = backgroundBlurBehind
        val configuration = LocalConfiguration.current
        val hingeSafeRegions = LocalHingeSafeOverlayRegions.current.sheet
        val layoutSpec = remember(configuration.screenWidthDp, miuix) {
            resolveAppModalLayoutSpec(configuration.screenWidthDp, miuixNonGlass = miuix)
        }
        val centered = (presentationOverride ?: layoutSpec.presentation) == AppModalPresentation.CenteredDialog
        val resolvedColor = if (containerColor == MaterialTheme.colorScheme.surface) {
            if (isMiuixNonGlassEnabled()) top.yukonga.miuix.kmp.layout.BottomSheetDefaults.backgroundColor()
            else if (miuix) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow
        } else containerColor
        // 仅背景模糊已获调用方启用的玻璃面板透出下层；原生拖动与清晰背景策略保持原样。
        val glassWindowMaterial = LocalAppThemeConfig.current.liquidGlassEnabled && blurBehind &&
            android.os.Build.VERSION.SDK_INT >= 33
        val windowColor = if (glassWindowMaterial) {
            resolvedColor.copy(alpha = resolvedColor.alpha * if (isAppInDarkTheme()) 0.84f else 0.88f)
        } else resolvedColor

        // 播放器显式选择横屏侧栏；其他调用方的原生弹层尺寸与呈现策略保持不变。
        if (presentationOverride == AppModalPresentation.SideSheet) {
            LaunchedEffect(visible) { if (!visible) finishDismiss() }
            if (!visible) return@popupScene
            Dialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                AppPopupWindowHost(enableWindowMaterial = LocalAppThemeConfig.current.liquidGlassEnabled) {
                    AppModalSheetNavigationHost(dismissOnBackPress, onDismissRequest, backgroundBlurBehind = blurBehind) { backModifier ->
                        val panel: @Composable () -> Unit = {
                            BoxWithConstraints {
                                AppPopupSurface(
                                    type = AppPopupSurfaceType.SHEET,
                                    modifier = modifier
                                        .widthIn(max = minOf(360.dp, (configuration.screenWidthDp * 0.46f).dp, maxWidth))
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .then(backModifier)
                                        .pointerInput(Unit) { detectTapGestures { } },
                                    shape = shape ?: RoundedCornerShape(24.dp),
                                    containerColor = resolvedColor,
                                    contentColor = contentColor,
                                    tonalElevation = tonalElevation,
                                ) {
                                    Column(Modifier.fillMaxSize().then(sheetSurfaceModifier), content = content)
                                }
                            }
                        }
                        if (hingeSafeRegions != null) {
                            HingeSafeOverlayHost(
                                regionProvider = hingeSafeRegions,
                                modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(8.dp),
                                alignment = Alignment.CenterEnd,
                                onDismissRequest = onDismissRequest,
                            ) { panel() }
                        } else {
                            Box(
                                Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(8.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Box(Modifier.matchParentSize().pointerInput(onDismissRequest) {
                                    detectTapGestures { onDismissRequest() }
                                })
                                panel()
                            }
                        }
                    }
                }
            }
            return@popupScene
        }

        // 合集显式选择整面板位移；居中及铰链安全弹窗、其他原生面板不改变过渡。
        if (useTimedSlideMotion && !centered && hingeSafeRegions == null) {
            AppTimedSlideSheet(
                visible = visible,
                onDismissStarted = { latestDismissStarted() },
                onDismissFinished = finishDismiss,
                modifier = modifier,
                shape = shape ?: if (miuix) RoundedCornerShape(28.dp) else BottomSheetDefaults.ExpandedShape,
                containerColor = windowColor,
                contentColor = contentColor,
                scrimColor = scrimColor,
                dismissOnBackPress = dismissOnBackPress,
                dragHandle = dragHandle,
                windowInsets = windowInsets,
                sheetSurfaceModifier = sheetSurfaceModifier,
                content = content,
            )
            return@popupScene
        }

        if (miuix && hingeSafeRegions == null) {
            var show by remember { mutableStateOf(visible) }
            LaunchedEffect(visible) {
                // 业务收起与手势收起共用开始通知；已进入收起时不重复通知。
                if (show && !visible) latestDismissStarted()
                show = visible
            }
            val requestNativeDismiss: () -> Unit = {
                if (show) {
                    latestDismissStarted()
                    show = false
                }
            }
            val centeredBack = remember { AppDialogBackState() }
            val centeredBackModifier = Modifier.appDialogBackMotion(state = centeredBack) { centeredBack.progress }
            val body: @Composable () -> Unit = {
                val blurMotionModifier = if (centered) {
                    AppDialogPredictiveBackHandler(
                        state = centeredBack,
                        gesturesEnabled = dismissOnBackPress && show,
                        onBackCompleted = {
                            if (show) {
                                requestNativeDismiss()
                                finishDismiss()
                            }
                        },
                    )
                    ModalWindowBlurBehindEffect(enabled = blurBehind, progress = { 1f - centeredBack.progress })
                    Modifier
                } else rememberSheetBlurMotionModifier(blurBehind)
                // Consume back when the caller handles it inside the sheet (e.g. reply navigation).
                // Drag/outside dismissal remains available independently of this back policy.
                if (!dismissOnBackPress && !centered) {
                    NavigationBackHandler(
                        state = rememberNavigationEventState(NavigationEventInfo.None),
                        onBackCompleted = {},
                    )
                }
                com.android.purebilibili.core.ui.components.ProvideAppActionContentColor(contentColor) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(blurMotionModifier)
                            .then(sheetSurfaceModifier),
                        content = content,
                    )
                }
            }
            if (centered) {
                WindowDialog(
                    show = show,
                    modifier = modifier.then(centeredBackModifier)
                        .heightIn(max = (configuration.screenHeightDp * layoutSpec.maxHeightFraction).dp),
                    backgroundColor = windowColor,
                    maxWidth = layoutSpec.maxWidthDp.dp,
                    largeScreen = true,
                    insideMargin = DpSize(0.dp, 0.dp),
                    onDismissRequest = requestNativeDismiss,
                    onDismissFinished = { if (!show) finishDismiss() },
                    content = body,
                )
            } else {
                WindowBottomSheet(
                    show = show,
                    modifier = modifier,
                    backgroundColor = windowColor,
                    insideMargin = DpSize(0.dp, 0.dp),
                    onDismissRequest = requestNativeDismiss,
                    onDismissFinished = { if (!show) finishDismiss() },
                    content = body,
                )
            }
            return@popupScene
        }
        if (hingeSafeRegions != null || centered) {
            // This branch already uses a plain centered/hinge-safe dialog, not a native sheet.
            LaunchedEffect(visible) { if (!visible) finishDismiss() }
            if (!visible) return@popupScene
            Dialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                AppPopupWindowHost(enableWindowMaterial = blurBehind) {
                    AppModalSheetNavigationHost(dismissOnBackPress, onDismissRequest, backgroundBlurBehind = blurBehind) { backModifier ->
                        val surface: @Composable () -> Unit = {
                            BoxWithConstraints {
                                val surfaceModifier = modifier.then(backModifier).widthIn(max = layoutSpec.maxWidthDp.dp)
                                    .heightIn(max = minOf(maxHeight, (configuration.screenHeightDp * layoutSpec.maxHeightFraction).dp))
                                    .fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }
                                val surfaceContent: @Composable () -> Unit = {
                                    Column(Modifier.then(sheetSurfaceModifier), content = content)
                                }
                                if (miuix || glassWindowMaterial) {
                                    AppPopupSurface(
                                        type = AppPopupSurfaceType.DIALOG,
                                        modifier = surfaceModifier,
                                        shape = shape ?: if (miuix) RoundedCornerShape(28.dp) else MaterialTheme.shapes.extraLarge,
                                        containerColor = resolvedColor,
                                        contentColor = contentColor,
                                        tonalElevation = if (miuix) 0.dp else tonalElevation,
                                        content = surfaceContent,
                                    )
                                } else {
                                    Surface(
                                        modifier = surfaceModifier,
                                        shape = shape ?: MaterialTheme.shapes.extraLarge,
                                        color = resolvedColor,
                                        contentColor = contentColor,
                                        tonalElevation = tonalElevation,
                                        content = surfaceContent,
                                    )
                                }
                            }
                        }
                        if (hingeSafeRegions != null) {
                            HingeSafeOverlayHost(
                                regionProvider = hingeSafeRegions,
                                modifier = Modifier.fillMaxSize().imePadding(),
                                onDismissRequest = onDismissRequest,
                            ) { surface() }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { surface() }
                        }
                    }
                }
            }
            return@popupScene
        }
        var wasClosing by remember { mutableStateOf(false) }
        LaunchedEffect(sheetState) {
            // Covers native scrim, swipe and predictive-back dismissal.
            snapshotFlow { sheetState.isVisible && sheetState.targetValue == SheetValue.Hidden }
                .collect { closing -> if (closing) latestDismissStarted() }
        }
        LaunchedEffect(visible) {
            if (!visible) {
                wasClosing = true
                sheetState.hide()
                if (!sheetState.isVisible) finishDismiss()
            } else if (wasClosing) {
                wasClosing = false
                sheetState.show()
            }
        }
        val windowBackState = rememberNavigationEventState(NavigationEventInfo.None)
        val windowBackModifier = if (useWindowPredictiveBack) {
            appModalSheetBackModifier(windowBackState.transitionState, dismissOnBackPress)
        } else Modifier
        val dismissScope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = finishDismiss,
            modifier = modifier.then(windowBackModifier),
            sheetState = sheetState,
            // 默认保留 MD3 手势；显式启用窗口导航的入口自行接收进度，仍使用原生 hide。
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = dismissOnBackPress && !useWindowPredictiveBack,
            ),
            shape = shape ?: BottomSheetDefaults.ExpandedShape,
            containerColor = windowColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            contentWindowInsets = { windowInsets },
        ) {
            if (useWindowPredictiveBack) {
                // 分享入口将事件绑定到弹窗窗口，避免继承页面 dispatcher 后丢失手势进度。
                // 完成后仍走原生 hide；取消时只还原面板形变，不关闭分享或下层页面。
                AppModalSheetWindowNavigationScope {
                    NavigationBackHandler(
                        state = windowBackState,
                        isBackEnabled = dismissOnBackPress && sheetState.isVisible,
                        onBackCompleted = {
                            dismissScope.launch {
                                sheetState.hide()
                                if (!sheetState.isVisible) finishDismiss()
                            }
                        },
                    )
                }
            }
            val blurMotionModifier = rememberSheetBlurMotionModifier(blurBehind)
            Column(Modifier.fillMaxWidth().then(blurMotionModifier).then(sheetSurfaceModifier), content = content)
        }
    }
}

data class AppBottomSheetMotion(
    val scrimEnter: EnterTransition,
    val scrimExit: ExitTransition,
    val contentEnter: EnterTransition,
    val contentExit: ExitTransition,
    val scrimEnterDurationMillis: Int,
    val scrimExitDurationMillis: Int,
    val contentEnterFadeDurationMillis: Int,
    val contentExitFadeDurationMillis: Int,
    val reducedMotion: Boolean = false,
    val dragSettleSpec: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 600f),
)

@Composable
fun rememberAppBottomSheetMotion(): AppBottomSheetMotion {
    val uiStyle = LocalAppUiStyle.current
    // Only custom overlay consumers use this contract. Native MD3/Miuix window
    // sheets above keep their own motion, geometry and theme defaults.
    val reducedMotion = LocalSystemReduceMotion.current || !LocalComponentMotionEnabled.current
    return remember(uiStyle, reducedMotion) {
        val motionSpec = resolveAdaptiveBottomSheetMotionSpec(uiStyle, reducedMotion)
        AppBottomSheetMotion(
            scrimEnter = if (reducedMotion) fadeIn(tween(120)) else bottomSheetScrimEnterTransition(uiStyle),
            scrimExit = if (reducedMotion) fadeOut(tween(90)) else bottomSheetScrimExitTransition(uiStyle),
            contentEnter = if (reducedMotion) fadeIn(tween(120)) else bottomSheetContentEnterTransition(uiStyle),
            contentExit = if (reducedMotion) fadeOut(tween(90)) else bottomSheetContentExitTransition(uiStyle),
            scrimEnterDurationMillis = motionSpec.scrimEnterDurationMillis,
            scrimExitDurationMillis = motionSpec.scrimExitDurationMillis,
            contentEnterFadeDurationMillis = motionSpec.contentEnterFadeDurationMillis,
            contentExitFadeDurationMillis = motionSpec.contentExitFadeDurationMillis,
            reducedMotion = reducedMotion,
            dragSettleSpec = if (reducedMotion) tween(0) else spring(dampingRatio = 1f, stiffness = 600f),
        )
    }
}

@Composable
fun AppBottomSheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/** Follow the native sheet motion without a second animation or frame-rate recomposition. */
@Composable
private fun rememberSheetBlurMotionModifier(enabled: Boolean, trackPosition: Boolean = true): Modifier {
    val view = LocalView.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    ModalWindowBlurBehindEffect(enabled = enabled, progress = {
        if (!trackPosition) 1f else {
            val layout = coordinates[0]
            if (layout == null || !layout.isAttached || layout.size.height == 0) 0f
            else {
                val top = layout.localToWindow(Offset.Zero).y
                ((view.rootView.height - top) / layout.size.height.toFloat()).coerceIn(0f, 1f)
            }
        }
    })
    return Modifier.onGloballyPositioned { coordinates[0] = it }
}
