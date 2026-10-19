package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.graphicsLayer

import com.android.purebilibili.core.ui.components.appElasticPress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.animation.DampedDragAnimationState
import com.android.purebilibili.core.ui.animation.DampedDragTrackingMode
import com.android.purebilibili.core.ui.animation.rememberDampedDragAnimationState
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.motion.BottomBarMotionSpec
import com.android.purebilibili.core.ui.motion.emphasizedEnterTween
import com.android.purebilibili.core.ui.motion.emphasizedExitTween
import com.android.purebilibili.core.ui.motion.softLandingSpring
import dev.chrisbanes.haze.HazeState
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity

internal enum class BottomBarLiquidOrientation {
    HORIZONTAL,
    VERTICAL
}

/** Narrow render configuration provided once by the navigation root. */
@Immutable
internal data class LiquidGlassRenderConfig(
    val tuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    val preset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
)

internal val LocalLiquidGlassRenderConfig = staticCompositionLocalOf {
    LiquidGlassRenderConfig()
}

internal enum class BottomBarMatchedDockEdge {
    TOP,
    BOTTOM
}

internal fun Modifier.bottomBarMatchedCaptureOverflow(inset: Dp): Modifier =
    bottomBarMatchedCaptureOverflow(
        horizontalInset = inset,
        verticalInset = inset,
    )

internal fun Modifier.bottomBarMatchedCaptureOverflow(
    horizontalInset: Dp,
    verticalInset: Dp,
): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    } else {
        val horizontalInsetPx = horizontalInset.roundToPx().coerceAtLeast(0)
        val verticalInsetPx = verticalInset.roundToPx().coerceAtLeast(0)
        val expandedWidth = (constraints.maxWidth.toLong() + horizontalInsetPx.toLong() * 2L)
            .coerceAtMost(Constraints.Infinity.toLong())
            .toInt()
        val expandedHeight = (constraints.maxHeight.toLong() + verticalInsetPx.toLong() * 2L)
            .coerceAtMost(Constraints.Infinity.toLong())
            .toInt()
        val placeable = measurable.measure(
            Constraints.fixed(
                width = expandedWidth,
                height = expandedHeight
            )
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(-horizontalInsetPx, -verticalInsetPx)
        }
    }
}

/**
 * UI-only interaction state shared by the home bottom bar and every opted-in liquid Chrome.
 * Business selection remains owned by the caller.
 */
@Stable
internal class BottomBarMatchedLiquidChromeState internal constructor(
    internal val dragState: DampedDragAnimationState,
    val orientation: BottomBarLiquidOrientation,
    internal val isScrollInProgressProvider: () -> Boolean
) {
    val position: Float get() = dragState.value
    val targetPosition: Float get() = dragState.targetValue
    val velocityPxPerSecond: Float get() = dragState.velocityPxPerSecond
    val deformationVelocityItemsPerSecond: Float
        get() = dragState.deformationVelocityItemsPerSecond
    val pressProgress: Float get() = dragState.pressProgress
    val dragOffsetPx: Float get() = dragState.dragOffset
    val isDragging: Boolean get() = dragState.isDragging

    fun updateIndex(index: Int) = dragState.updateIndex(index)

    fun setPressed(pressed: Boolean) = dragState.setPressed(pressed)
}

@Composable
internal fun rememberBottomBarMatchedLiquidChromeState(
    initialIndex: Int,
    itemCount: Int,
    onIndexChanged: (Int) -> Unit,
    orientation: BottomBarLiquidOrientation = BottomBarLiquidOrientation.HORIZONTAL,
    isScrollInProgressProvider: () -> Boolean = { false },
    notifyIndexChangedOnReleaseStart: Boolean = false,
    pressedScale: Float = FloatingBottomBarPressedScale,
    trackingMode: DampedDragTrackingMode = DampedDragTrackingMode.BILIPAI_SPRING,
): BottomBarMatchedLiquidChromeState {
    val motionSpec = remember { resolveSegmentedControlMotionSpec() }
    val dragState = rememberDampedDragAnimationState(
        initialIndex = initialIndex,
        itemCount = itemCount,
        motionSpec = motionSpec,
        pressedScale = pressedScale,
        trackingMode = trackingMode,
        notifyIndexChangedOnReleaseStart = notifyIndexChangedOnReleaseStart,
        holdPressUntilReleaseTargetSettles = true,
        onIndexChanged = onIndexChanged
    )
    return remember(dragState, orientation, isScrollInProgressProvider) {
        BottomBarMatchedLiquidChromeState(
            dragState = dragState,
            orientation = orientation,
            isScrollInProgressProvider = isScrollInProgressProvider
        )
    }
}

/**
 * Exact Miuix/BiliPai material used by the home floating bottom bar.
 */
@Composable
internal fun BottomBarMatchedLiquidDock(
    backdrop: Backdrop?,
    containerColor: Color,
    shape: Shape,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    drawShellLens: Boolean = true,
    shellLensIntensity: Float = 1f,
    blurRadius: Dp,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    isScrollInProgressProvider: () -> Boolean = { false },
    materialScrollProgressOverride: Float? = null,
    materialMotionProgress: Float = 0f,
    interactionState: com.android.purebilibili.core.ui.components.AppElasticPressState? = null,
    materialPressProgress: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .bottomBarMatchedLiquidDockSurface(
                    shape = shape,
                    backdrop = backdrop,
                    containerColor = containerColor,
                    blurEnabled = blurEnabled,
                    glassEnabled = glassEnabled,
                    drawShellLens = drawShellLens,
                    shellLensIntensity = shellLensIntensity,
                    blurRadius = blurRadius,
                    hazeState = hazeState,
                    motionTier = motionTier,
                    isTransitionRunning = isTransitionRunning,
                    forceLowBlurBudget = forceLowBlurBudget,
                    liquidGlassPreset = liquidGlassPreset,
                    liquidGlassTuning = liquidGlassTuning,
                    isScrollInProgressProvider = isScrollInProgressProvider,
                    materialScrollProgressOverride = materialScrollProgressOverride,
                    materialMotionProgress = materialMotionProgress,
                    interactionState = interactionState,
                    materialPressProgress = materialPressProgress
                )
        )
        content()
    }
}

@Composable
internal fun Modifier.bottomBarMatchedLiquidDockSurface(
    backdrop: Backdrop?,
    containerColor: Color,
    shape: Shape,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    blurRadius: Dp,
    hazeState: HazeState? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    isScrollInProgressProvider: () -> Boolean = { false },
    materialScrollProgressOverride: Float? = null,
    materialMotionProgress: Float = 0f,
    interactionState: com.android.purebilibili.core.ui.components.AppElasticPressState? = null,
    materialPressProgress: Float = 0f,
    drawShellLens: Boolean = true,
    shellLensIntensity: Float = 1f
): Modifier = composed {
    val isScrolling = isScrollInProgressProvider()
    // The tuned glass material does not read scroll progress. Avoid running a
    // composition-driven tail animation during card navigation for an unused value.
    // Non-glass callers and the refined preset keep their original material behavior.
    val materialScrollProgress = if (materialScrollProgressOverride != null) {
        materialScrollProgressOverride
    } else if (glassEnabled && liquidGlassPreset == BottomBarLiquidGlassPreset.BILIPAI_TUNED) {
        0f
    } else {
        val animatedScrollProgress by animateFloatAsState(
            targetValue = if (isScrolling) 1f else 0f,
            animationSpec = tween(
                durationMillis = resolveBottomBarMaterialScrollAnimationDurationMillis(isScrolling),
                easing = AppMotionEasing.Continuity
            ),
            label = "bottomBarMatchedMaterialScrollProgress"
        )
        animatedScrollProgress
    }
    // Miuix-only: no legacy fallback. Null backdrop degrades inside biliPaiMiuixFloatingDockSurface.
    biliPaiMiuixFloatingDockSurface(
        shape = shape,
        backdrop = backdrop,
        containerColor = containerColor,
        blurEnabled = blurEnabled,
        glassEnabled = glassEnabled,
        drawShellLens = drawShellLens,
        shellLensIntensity = shellLensIntensity,
        blurRadius = blurRadius,
        hazeState = hazeState,
        motionTier = motionTier,
        isTransitionRunning = isTransitionRunning,
        forceLowBlurBudget = forceLowBlurBudget,
        liquidGlassPreset = liquidGlassPreset,
        liquidGlassTuning = liquidGlassTuning,
        isScrolling = isScrolling,
        materialScrollProgress = materialScrollProgress,
        materialMotionProgress = materialMotionProgress,
        interactionState = interactionState,
                    materialPressProgress = materialPressProgress
    )
}

/**
 * Content-slot entry point for search fields, comment/action bars, and other inline chrome.
 * When global reuse is disabled, [content] is emitted unchanged.
 *
 * 黑虾线防回归规则：
 * 1. 黑/亮细线来自短胶囊使用 64dp 底栏的满强度折射，导致上下 refraction 在中线相撞，
 *    或来自一个液态壳内部再次采样、折射自身的嵌套 lens。
 * 2. 多个视觉上独立的胶囊必须各自拥有 backdrop 和 lens；不要为了消线把它们合成一个长壳。
 * 3. 独立短胶囊不要直接关闭 lens，否则会丢失液态玻璃折射。应传入
 *    `resolveFloatingDockGeometryScale(actualHeightDp)`，按实际高度相对 64dp 基准缩放。
 * 4. 只有已经位于液态外壳内部、且不应再次折射的内容层才使用 [drawShellLens] = false。
 * 5. lens 开启时必须保留 capture overflow / safe inset，避免折射采样越界产生黑边。
 *
 * @param drawShellLens 独立液态表面应保留 lens；只有确实嵌套在另一个液态壳内的内容层才关闭。
 * @param shellLensIntensity 矮 dock 应按实际高度相对 64dp 基准缩放 lens，避免上下折射边沿相撞成虾线。
 */
@Composable
internal fun BottomBarMatchedReusableLiquidDock(
    shape: Shape,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    liquidGlassEffectsEnabled: Boolean = true,
    /**
     * Allowlisted callers only: home search field, comment [BottomInputBar], and dynamic
     * composer/detail chrome that explicitly follows the same floating-dock contract.
     * Other chrome must leave this false.
     */
    reuseEnabled: Boolean = false,
    elasticFeedbackEnabled: Boolean = false,
    elasticFeedbackStrength: Float = 1f,
    allowConsumedDrag: () -> Boolean = { false },
    useNeutralLiquidContainer: Boolean = false,
    drawShellLens: Boolean = true,
    shellLensIntensity: Float = 1f,
    isScrollInProgressProvider: () -> Boolean = { false },
    content: @Composable BoxScope.(liquidChromeActive: Boolean) -> Unit
) {
    val elasticState = com.android.purebilibili.core.ui.components.rememberAppElasticPressState(
        feedbackStrength = elasticFeedbackStrength,
    )
    val reuseAllowed = LocalAppThemeConfig.current.liquidGlassEnabled
    if (!reuseEnabled || !reuseAllowed || !liquidGlassEffectsEnabled) {
        Box(modifier = modifier.appElasticPress(
            enabled = elasticFeedbackEnabled,
            state = elasticState,
            allowConsumedDrag = allowConsumedDrag,
        )) {
            CompositionLocalProvider(
                com.android.purebilibili.core.ui.components.LocalElasticPressEnabled provides
                    (!elasticFeedbackEnabled && com.android.purebilibili.core.ui.components.LocalElasticPressEnabled.current),
            ) {
                content(false)
            }
        }
        return
    }

    val renderConfig = LocalLiquidGlassRenderConfig.current
    val glassEnabled = resolveAndroidNativeBottomBarGlassEnabled(
        liquidGlassEnabled = reuseEnabled && reuseAllowed,
        blurEnabled = true
    )
    // Match floating segmented chrome: a supplied sibling/page source already
    // contains the background pixels. Do not record a second blank source and
    // sample both on every optical pass. Each dock still owns its lens/effects.
    val localBackdrop = if (backdrop == null) rememberLayerBackdrop() else null
    val effectiveBackdrop = backdrop ?: localBackdrop
    val isDarkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val blurIntensity = currentUnifiedBlurIntensity()
    val tuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = true,
        darkTheme = isDarkTheme
    )
    val liquidGlassTuning = renderConfig.tuning
    val containerColor = if (useNeutralLiquidContainer && glassEnabled) {
        resolveBiliPaiBottomBarContainerColor(
            darkTheme = isDarkTheme,
            liquidGlassTuning = liquidGlassTuning,
            surfaceColor = AppSurfaceTokens.surfaceContainer(),
        )
    } else {
        resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = AppSurfaceTokens.cardContainer(),
            tuning = tuning,
            glassEnabled = glassEnabled,
            blurEnabled = true,
            blurIntensity = blurIntensity,
            liquidGlassPreset = renderConfig.preset,
            liquidGlassTuning = liquidGlassTuning,
        )
    }
    // lens 的 refraction 会读取壳体边界外像素；开启时必须预留安全采样区。
    // 若调用者是短胶囊，应缩放 shellLensIntensity，而不是通过关闭 lens 跳过这里。
    val captureSafeInset = if (drawShellLens) {
        resolveBottomBarCaptureSafeInsetDp(
            indicatorWidthDp = 0f,
            refractionHeightDp = liquidGlassTuning.refractionHeight,
            refractionAmountDp = liquidGlassTuning.refractionAmount,
            panelOffsetDp = 0f
        ).dp
    } else {
        AppSpacingTokens.None
    }

    Box(modifier = modifier.appElasticPress(
        enabled = elasticFeedbackEnabled,
        state = elasticState,
        transformInBackdrop = glassEnabled,
        allowConsumedDrag = allowConsumedDrag,
    )) {
        if (localBackdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(if (drawShellLens) {
                        Modifier.bottomBarMatchedCaptureOverflow(captureSafeInset)
                    } else Modifier)
                    .alpha(0f)
                    .layerBackdrop(localBackdrop)
                    .background(AppSurfaceTokens.background())
            )
        }
        BottomBarMatchedLiquidDock(
            backdrop = effectiveBackdrop,
            containerColor = containerColor,
            shape = shape,
            blurEnabled = true,
            glassEnabled = glassEnabled,
            interactionState = elasticState.takeIf { elasticFeedbackEnabled && glassEnabled },
            drawShellLens = drawShellLens,
            shellLensIntensity = shellLensIntensity,
            blurRadius = tuning.shellBlurRadiusDp.dp,
            modifier = Modifier.matchParentSize(),
            liquidGlassPreset = renderConfig.preset,
            liquidGlassTuning = liquidGlassTuning,
            isScrollInProgressProvider = isScrollInProgressProvider
        ) {}
        Box(modifier = if (elasticFeedbackEnabled && glassEnabled) {
            Modifier.graphicsLayer(elasticState.layerBlock)
        } else Modifier) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.android.purebilibili.core.ui.components.LocalElasticPressEnabled provides (!elasticFeedbackEnabled && com.android.purebilibili.core.ui.components.LocalElasticPressEnabled.current),
            ) { content(true) }
        }
    }
}

/**
 * Exact moving indicator used by the home floating bottom bar. Orientation only swaps axes.
 */
@Composable
internal fun BoxScope.BottomBarMatchedLiquidIndicator(
    visible: Boolean,
    dockContentAlpha: Float,
    indicatorTranslationXPx: Float,
    indicatorTranslationYPx: Float = 0f,
    indicatorPanelOffsetPx: Float,
    indicatorPanelOffsetYPx: Float = 0f,
    indicatorWidth: Dp,
    indicatorHeight: Dp,
    shellShape: Shape,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    contentBackdrop: Backdrop?,
    backdrop: Backdrop?,
    indicatorLensSpec: BottomBarBackdropPresetLensSpec,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    effectivePressProgress: Float,
    indicatorIdleSurfaceColor: Color,
    glassEnabled: Boolean,
    indicatorEffectsEnabled: Boolean = glassEnabled,
    motionProgress: Float,
    velocityItemsPerSecond: Float,
    isDragging: Boolean,
    indicatorLayerScaleProgress: Float,
    indicatorLayerScaleTransform: BottomBarIndicatorLayerTransform? = null,
    dragScaleTarget: Float = BOTTOM_BAR_INDICATOR_DRAG_SCALE_TARGET,
    bottomBarMotionSpec: BottomBarMotionSpec,
    isDarkTheme: Boolean,
    indicatorSettleReboundTransform: BottomBarClickPulseTransform =
        BottomBarClickPulseTransform(scaleX = 1f),
    orientation: BottomBarLiquidOrientation = BottomBarLiquidOrientation.HORIZONTAL,
    indicatorAlignment: Alignment = Alignment.CenterStart,
    interactionModifier: Modifier = Modifier
) {
    // Miuix-only indicator path. Null backdrop degrades to solid surface inside the layer.
    BiliPaiMiuixBottomBarIndicatorLayer(
        visible = visible,
        dockContentAlpha = dockContentAlpha,
        indicatorTranslationXPx = indicatorTranslationXPx,
        indicatorTranslationYPx = indicatorTranslationYPx,
        indicatorPanelOffsetPx = indicatorPanelOffsetPx,
        indicatorPanelOffsetYPx = indicatorPanelOffsetYPx,
        indicatorWidth = indicatorWidth,
        indicatorHeight = indicatorHeight,
        shellShape = shellShape,
        liquidGlassPreset = liquidGlassPreset,
        contentBackdrop = contentBackdrop,
        backdrop = backdrop,
        indicatorLensSpec = indicatorLensSpec,
        liquidGlassTuning = liquidGlassTuning,
        effectivePressProgress = effectivePressProgress,
        indicatorIdleSurfaceColor = indicatorIdleSurfaceColor,
        glassEnabled = glassEnabled,
        indicatorEffectsEnabled = indicatorEffectsEnabled,
        motionProgress = motionProgress,
        velocityItemsPerSecond = velocityItemsPerSecond,
        isDragging = isDragging,
        indicatorLayerScaleProgress = indicatorLayerScaleProgress,
        indicatorLayerScaleTransform = indicatorLayerScaleTransform,
        dragScaleTarget = dragScaleTarget,
        bottomBarMotionSpec = bottomBarMotionSpec,
        isDarkTheme = isDarkTheme,
        swapMotionAxes = orientation == BottomBarLiquidOrientation.VERTICAL,
        indicatorAlignment = indicatorAlignment,
        interactionModifier = interactionModifier
    )
}

@Composable
internal fun BottomBarMatchedDockVisibility(
    visible: Boolean,
    edge: BottomBarMatchedDockEdge,
    modifier: Modifier = Modifier,
    enterFadeDurationMillis: Int = 255,
    exitFadeDurationMillis: Int = 160,
    animateScale: Boolean = true,
    content: @Composable () -> Unit
) {
    val direction = if (edge == BottomBarMatchedDockEdge.BOTTOM) 1 else -1
    val transformOrigin = if (edge == BottomBarMatchedDockEdge.BOTTOM) {
        TransformOrigin(0.5f, 1f)
    } else {
        TransformOrigin(0.5f, 0f)
    }
    val enterTransition = slideInVertically(
        animationSpec = softLandingSpring(),
        initialOffsetY = { height -> direction * height }
    ) + fadeIn(animationSpec = emphasizedEnterTween(enterFadeDurationMillis))
    val exitTransition = slideOutVertically(
        animationSpec = emphasizedExitTween(exitFadeDurationMillis),
        targetOffsetY = { height -> direction * height }
    ) + fadeOut(animationSpec = emphasizedExitTween(exitFadeDurationMillis))
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (animateScale) {
            enterTransition + scaleIn(
                animationSpec = softLandingSpring(),
                initialScale = 0.96f,
                transformOrigin = transformOrigin
            )
        } else {
            enterTransition
        },
        exit = if (animateScale) {
            exitTransition + scaleOut(
                animationSpec = emphasizedExitTween(exitFadeDurationMillis),
                targetScale = 0.92f,
                transformOrigin = transformOrigin
            )
        } else {
            exitTransition
        },
        content = { content() }
    )
}

@Composable
internal fun BottomBarMatchedDockVisibility(
    visibleState: MutableTransitionState<Boolean>,
    edge: BottomBarMatchedDockEdge,
    modifier: Modifier = Modifier,
    enterFadeDurationMillis: Int = 255,
    exitFadeDurationMillis: Int = 160,
    animateScale: Boolean = true,
    content: @Composable () -> Unit
) {
    val direction = if (edge == BottomBarMatchedDockEdge.BOTTOM) 1 else -1
    val transformOrigin = if (edge == BottomBarMatchedDockEdge.BOTTOM) {
        TransformOrigin(0.5f, 1f)
    } else {
        TransformOrigin(0.5f, 0f)
    }
    val enterTransition = slideInVertically(
        animationSpec = softLandingSpring(),
        initialOffsetY = { height -> direction * height }
    ) + fadeIn(animationSpec = emphasizedEnterTween(enterFadeDurationMillis))
    val exitTransition = slideOutVertically(
        animationSpec = emphasizedExitTween(exitFadeDurationMillis),
        targetOffsetY = { height -> direction * height }
    ) + fadeOut(animationSpec = emphasizedExitTween(exitFadeDurationMillis))
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = if (animateScale) {
            enterTransition + scaleIn(
                animationSpec = softLandingSpring(),
                initialScale = 0.96f,
                transformOrigin = transformOrigin
            )
        } else {
            enterTransition
        },
        exit = if (animateScale) {
            exitTransition + scaleOut(
                animationSpec = emphasizedExitTween(exitFadeDurationMillis),
                targetScale = 0.92f,
                transformOrigin = transformOrigin
            )
        } else {
            exitTransition
        },
        content = { content() }
    )
}
