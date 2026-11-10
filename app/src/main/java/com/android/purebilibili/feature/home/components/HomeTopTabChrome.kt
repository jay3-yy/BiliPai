package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.OpticalContrastPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.purebilibili.feature.home.HomeTopTabGestureAction
import com.android.purebilibili.feature.home.resolveHomeTopTabGestureAction
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset
import com.android.purebilibili.core.store.LiquidGlassStyle
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import dev.chrisbanes.haze.HazeState
import coil3.compose.AsyncImage
import java.io.File
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop

@Composable
internal fun HomeTopTabChrome(
    currentTabHeight: Dp,
    tabAlpha: Float,
    tabContentAlpha: Float,
    containerZIndex: Float = -1f,
    tabHorizontalPadding: Dp,
    tabVerticalPadding: Dp,
    tabVerticalOffset: Dp,
    isTabFloating: Boolean,
    effectiveTabShadowElevation: Dp,
    tabShape: Shape,
    tabChromeRenderMode: HomeTopChromeRenderMode,
    tabSurfaceColor: Color,
    hazeState: HazeState?,
    miuixBackdrop: MiuixBackdrop? = null,
    liquidStyle: LiquidGlassStyle,
    liquidGlassTuning: LiquidGlassTuning? = null,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    motionTier: MotionTier,
    isScrolling: Boolean,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    preferFlatGlass: Boolean,
    tabBorderAlpha: Float,
    tabHighlightColor: Color,
    tabContentUnderlayColor: Color,
    gestureEnabled: Boolean = false,
    isTabsCollapsed: Boolean = false,
    onTabsCollapsedChange: ((Boolean) -> Unit)? = null,
    drawChromeSurface: Boolean = true,
    useBottomBarMatchedSurface: Boolean = false,
    /**
     * 矮 dock 的 shell lens 按高度缩放，避免 24dp 折射在中间对撞成虾线。
     */
    drawMatchedShellLens: Boolean = true,
    matchedShellLensIntensity: Float = TOP_DOCK_SHELL_LENS_INTENSITY,
    /**
     * When true, the floating dock shell shrinks to tab content width (icon/text density ×
     * count) and centers in the padded track — no full-bleed empty glass on the right.
     */
    wrapDockWidth: Boolean = false,
    dockCategoryCount: Int = 0,
    dockLabelMode: Int = 2,
    /**
     * Cap on the dock width so the tab strip never exceeds the top controls'
     * combined width (avatar + search pill + settings) — keeps left/right edges
     * aligned with the search row. [Dp.Infinity] keeps legacy full-bleed docks.
     */
    maxDockWidth: Dp = Dp.Infinity,
    skinBackgroundImagePath: String? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val gestureThresholdPx = with(density) {
        (AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small).toPx()
    }
    val showCollapsedHandle = gestureEnabled && isTabsCollapsed
    val safeTabHorizontalPadding = tabHorizontalPadding.coerceAtLeast(AppSpacingTokens.None)
    val safeTabVerticalPadding = tabVerticalPadding.coerceAtLeast(AppSpacingTokens.None)
    val containerAlpha = if (showCollapsedHandle) {
        tabAlpha
    } else {
        tabAlpha * tabContentAlpha
    }
    // Alpha is draw-only during scroll. Keep it out of the tab content's composition path.
    val containerAlphaProvider = rememberUpdatedState(containerAlpha)
    val tabContentAlphaProvider = rememberUpdatedState(tabContentAlpha)
    val hasSkinBackground = !skinBackgroundImagePath.isNullOrBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(containerZIndex)
            .height(currentTabHeight)
            .graphicsLayer { alpha = containerAlphaProvider.value }
            .offset { IntOffset(x = 0, y = tabVerticalOffset.roundToPx()) }
            .then(
                if (gestureEnabled && onTabsCollapsedChange != null) {
                    Modifier.pointerInput(isTabsCollapsed, gestureThresholdPx) {
                        var accumulatedDragY = 0f
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDragY += dragAmount
                            },
                            onDragCancel = {
                                accumulatedDragY = 0f
                            },
                            onDragEnd = {
                                when (
                                    resolveHomeTopTabGestureAction(
                                        dragDeltaPx = accumulatedDragY,
                                        isCollapsed = isTabsCollapsed,
                                        thresholdPx = gestureThresholdPx
                                    )
                                ) {
                                    HomeTopTabGestureAction.COLLAPSE -> onTabsCollapsedChange(true)
                                    HomeTopTabGestureAction.EXPAND -> onTabsCollapsedChange(false)
                                    HomeTopTabGestureAction.NONE -> Unit
                                }
                                accumulatedDragY = 0f
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = safeTabHorizontalPadding, vertical = safeTabVerticalPadding)
        ) {
            val shouldWrap = wrapDockWidth &&
                dockCategoryCount > 0 &&
                maxWidth > AppSpacingTokens.None
            // 分栏 dock 宽度封顶于顶部三控件合计宽度。
            val cappedMaxWidth = minOf(maxWidth.value, maxDockWidth.value)
            val dockWidth = if (shouldWrap) {
                if (isTabFloating) {
                    resolveHomeTopTabFloatingDockWidth(
                        containerWidth = cappedMaxWidth.dp,
                        itemCount = dockCategoryCount,
                        labelMode = dockLabelMode,
                        shellHeight = resolveHomeTopDockShellHeight(isFloatingStyle = isTabFloating),
                    )
                } else {
                    val preferredItem = resolveTopTabWrapItemWidthDp(
                        labelMode = dockLabelMode,
                        isFloatingStyle = false
                    )
                    resolveTopTabDockWrapWidthDp(
                        itemWidthDp = preferredItem,
                        categoryCount = dockCategoryCount,
                        maxWidthDp = cappedMaxWidth,
                        contentPaddingHorizontalDp = resolveTopTabDockEndInsetDp(
                            wrapContent = true,
                            isFloatingStyle = false
                        )
                    ).dp
                }
            } else {
                maxWidth
            }
            // 包裹 dock 始终在可用顶部区域内居中，不受标签样式和数量影响。
            val dockAlignment = Alignment.Center
            val dockModifier = Modifier
                .align(dockAlignment)
                .width(dockWidth)
                .widthIn(max = maxDockWidth)
                .fillMaxHeight()

            // 内容层独立占据同一槽位；无外壳和皮肤时不创建空的装饰层。
            if (drawChromeSurface || hasSkinBackground) {
                Box(
                    modifier = dockModifier
                        .then(
                            if (drawChromeSurface && !hasSkinBackground && effectiveTabShadowElevation > AppSpacingTokens.None) {
                                Modifier.shadow(
                                    elevation = effectiveTabShadowElevation,
                                    shape = tabShape,
                                    ambientColor = AppContentColors.primaryText().copy(alpha = 0.12f),
                                    spotColor = AppContentColors.primaryText().copy(alpha = 0.12f)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (drawChromeSurface && !hasSkinBackground) {
                                if (useBottomBarMatchedSurface) {
                                    Modifier.homeTopBottomBarMatchedSurface(
                                        renderMode = tabChromeRenderMode,
                                        shape = tabShape,
                                        hazeState = hazeState,
                                        miuixBackdrop = miuixBackdrop,
                                        liquidGlassStyle = liquidStyle,
                                        liquidGlassTuning = liquidGlassTuning,
                                        liquidGlassPreset = liquidGlassPreset,
                                        motionTier = motionTier,
                                        isTransitionRunning = isTransitionRunning,
                                        forceLowBlurBudget = forceLowBlurBudget,
                                        drawShellLens = drawMatchedShellLens,
                                        shellLensIntensity = matchedShellLensIntensity,
                                        isScrolling = isScrolling
                                    )
                                } else {
                                    Modifier.homeTopChromeSurface(
                                        renderMode = tabChromeRenderMode,
                                        shape = tabShape,
                                        surfaceColor = tabSurfaceColor,
                                        hazeState = hazeState,
                                        miuixBackdrop = miuixBackdrop,
                                        liquidStyle = liquidStyle,
                                        liquidGlassTuning = liquidGlassTuning,
                                        liquidGlassPreset = liquidGlassPreset,
                                        motionTier = motionTier,
                                        isScrolling = isScrolling,
                                        isTransitionRunning = isTransitionRunning,
                                        forceLowBlurBudget = forceLowBlurBudget,
                                        preferFlatGlass = preferFlatGlass
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (drawChromeSurface && !hasSkinBackground && isTabFloating) {
                                Modifier.border(
                                    width = AppSpacingTokens.Micro * 0.4f,
                                    color = OpticalContrastPalette.Highlight.copy(alpha = tabBorderAlpha),
                                    shape = tabShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer { alpha = tabContentAlphaProvider.value }
                ) {
                    if (drawChromeSurface && !hasSkinBackground) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(tabContentUnderlayColor, tabShape)
                        )
                        if (isTabFloating && tabHighlightColor.alpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(AppSpacingTokens.Large)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                tabHighlightColor,
                                                Color.Transparent
                                            )
                                        ),
                                        shape = tabShape
                                    )
                            )
                        }
                    }
                    if (!skinBackgroundImagePath.isNullOrBlank()) {
                        AsyncImage(
                            model = File(skinBackgroundImagePath),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            alignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(tabShape)
                                .alpha(0.94f)
                                .clearAndSetSemantics {}
                        )
                    }
                }
            }

            // Do not clip: liquid capsule drag-scale should slightly overflow the dock like bottom bar.
            Box(
                modifier = dockModifier
                    .graphicsLayer {
                        alpha = tabContentAlphaProvider.value
                        clip = false
                    },
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }

        if (showCollapsedHandle) {
            CollapsedTopTabHandle()
        }
    }
}

@Composable
private fun BoxScope.CollapsedTopTabHandle() {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(width = AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Micro, height = AppSpacingTokens.ExtraSmall)
            .clip(AppShapes.container(ContainerLevel.Pill))
            .background(AppContentColors.primaryText().copy(alpha = 0.28f))
    )
}
