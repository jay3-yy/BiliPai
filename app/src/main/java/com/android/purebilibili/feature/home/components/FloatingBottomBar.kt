// Floating bottom bar structure and interactions. Liquid effects use project-local
// Miuix-aligned helpers.
// Drag/press/highlight use the local DampedDragAnimation + InteractiveHighlight — not
// the design-system damped-drag state stack used by top tabs / segmented controls.
//
// Three layers:
//   Box (caller owns width; do not force IntrinsicSize.Min or fillMaxWidth)
//   ├─ Base Row (unselected)     // dropShadow + drawBackdrop(vibrancy+blur+lens) + shellHeight
//   ├─ Foreground Row (active)   // alpha=0 + replayed capture + drawBackdrop + indicatorHeight
//   └─ Moving indicator Box      // combinedBackdrop + lens(depth, chromatic) + innerShadow
// Segmented controls share the same captured content, lens and dispersion as the home dock.
//
// BiliPai-only knobs: nullable backdrop, shellHeight / indicatorHeight parameters.
package com.android.purebilibili.feature.home.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.android.purebilibili.feature.home.components.liquid.InnerShadow
import com.android.purebilibili.feature.home.components.liquid.innerShadow
import com.android.purebilibili.feature.home.components.liquid.lens
import com.android.purebilibili.feature.home.components.liquid.rememberCombinedBackdrop
import com.android.purebilibili.feature.home.components.liquid.vibrancy
import com.android.purebilibili.core.store.LiquidGlassReadabilityMode
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorGeometry
import com.android.purebilibili.feature.home.components.miuix.DampedDragAnimation
import com.android.purebilibili.feature.home.components.miuix.DampedDragTrackingMode
import com.android.purebilibili.feature.home.components.miuix.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.LocalContentColor as M3LocalContentColor
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor

val LocalFloatingBottomBarContentColor = staticCompositionLocalOf { Color.Unspecified }

internal val LocalFloatingBottomBarSelectedContentColor =
    staticCompositionLocalOf { Color.Unspecified }

val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

internal val LocalFloatingBottomBarIndicatorPosition = staticCompositionLocalOf { { 0f } }

internal val LocalFloatingBottomBarItemSelectionScale = staticCompositionLocalOf { { 1f } }

internal val LocalFloatingBottomBarItemAlignmentOffset =
    staticCompositionLocalOf<(Int) -> Float> { { 0f } }

internal val LocalFloatingBottomBarBaseContentAlpha =
    staticCompositionLocalOf<(Int) -> Float> { { 1f } }

internal val LocalFloatingBottomBarIndicatorStretchX =
    staticCompositionLocalOf { { 1f } }

/** 激活内容捕获层会为指示器提供每个槽位的选中态图标。 */
internal val LocalFloatingBottomBarActiveContent = staticCompositionLocalOf { false }

@Immutable
class FloatingBottomBarColors(
    val containerColor: Color,
    val indicatorColor: Color,
    val contentColor: Color,
    val activeContentColor: Color
)

object FloatingBottomBarDefaults {
    @Composable
    fun colors(
        containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        indicatorColor: Color = MiuixTheme.colorScheme.primary,
        contentColor: Color = MiuixTheme.colorScheme.onSurface,
        activeContentColor: Color = indicatorColor
    ): FloatingBottomBarColors = FloatingBottomBarColors(
        containerColor = containerColor,
        indicatorColor = indicatorColor,
        contentColor = contentColor,
        activeContentColor = activeContentColor
    )
}

enum class FloatingBottomBarMode {
    LiquidGlass,
    Blur,
    None
}

/** Solid Miuix fallback: keeps BiliPai slots without creating any backdrop or glass state. */
@Composable
fun PlainMiuixFloatingBottomBar(
    selectedIndex: Int,
    onSelected: (index: Int) -> Unit,
    onReselected: () -> Unit = {},
    tabsCount: Int,
    modifier: Modifier = Modifier,
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
    content: @Composable RowScope.() -> Unit,
) {
    val safeCount = tabsCount.coerceAtLeast(1)
    val maxIndex = safeCount - 1
    val shape = remember { resolveSharedBottomBarCapsuleShape() }
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val selectedIndexLatest = rememberUpdatedState(selectedIndex)
    val onSelectedLatest = rememberUpdatedState(onSelected)
    val onReselectedLatest = rememberUpdatedState(onReselected)
    BoxWithConstraints(
        modifier = modifier
            .dropShadow(
                shape = shape,
                shadow = Shadow(radius = 10.dp, color = Color.Black, alpha = 0.12f),
            )
            .background(colors.containerColor, shape)
            .padding(4.dp),
    ) {
        val itemWidth = maxWidth / safeCount
        val itemWidthPx = with(density) { itemWidth.toPx() }
        val dragAnimation = remember(animationScope, safeCount, itemWidthPx, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndexLatest.value.coerceIn(0, maxIndex).toFloat(),
                valueRange = 0f..maxIndex.toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1f,
                trackingMode = DampedDragTrackingMode.DIRECT,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, maxIndex)
                    animateToValue(targetIndex.toFloat(), animatePress = false)
                    if (targetIndex != selectedIndexLatest.value.coerceIn(0, maxIndex)) {
                        onSelectedLatest.value(targetIndex)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (itemWidthPx > 0f) {
                        val direction = if (isLtr) 1f else -1f
                        updateValue(targetValue + dragAmount.x / itemWidthPx * direction)
                    }
                },
            )
        }
        LaunchedEffect(dragAnimation, selectedIndex, maxIndex) {
            if (!dragAnimation.isDragging) {
                dragAnimation.animateToValue(
                    selectedIndex.coerceIn(0, maxIndex).toFloat(),
                    animatePress = false,
                )
            }
        }
        val indicatorPositionModifier = Modifier.graphicsLayer {
            val direction = if (isLtr) 1f else -1f
            translationX = itemWidthPx * dragAnimation.value * direction
        }
        Box(
            modifier = indicatorPositionModifier
                .width(itemWidth)
                .fillMaxHeight(),
        )
        CompositionLocalProvider(
            LocalFloatingBottomBarContentColor provides colors.contentColor,
            LocalFloatingBottomBarSelectedContentColor provides colors.activeContentColor,
            LocalFloatingBottomBarIndicatorPosition provides { dragAnimation.value },
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
        Box(
            modifier = indicatorPositionModifier
                .then(if (safeCount > 1) dragAnimation.modifier else Modifier)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                    onClick = { onReselectedLatest.value() },
                )
                .clearAndSetSemantics {}
                .width(itemWidth)
                .fillMaxHeight(),
        )
    }
}

/** Flatter resting indicator; the shell and indicator retain the same capsule shape. */
val FloatingBottomBarIndicatorHeight: Dp = 56.dp

val FloatingBottomBarDefaultShellHeight: Dp = 56.dp

const val FloatingBottomBarPressedScale: Float =
    com.android.purebilibili.core.ui.BottomBarReferencePressedScale

internal const val EXTERNAL_PAGER_INDICATOR_CATCH_UP_EPSILON = 0.05f

/**
 * After the indicator is dragged onto a new tab, the host pager still reports the old page
 * for at least one frame. Keep that drag target so pager-follow cannot snap the pill back.
 */
internal fun resolveIndicatorOwnedTargetOnDragStop(
    targetIndex: Int,
    selectedIndex: Int,
    hasExternalPagerPosition: Boolean,
): Int? {
    if (!hasExternalPagerPosition) return null
    if (targetIndex == selectedIndex) return null
    return targetIndex
}

/**
 * Swallow pager-follow (snap + press) for the whole indicator-driven page animation.
 * Dropping ownership as soon as the pager is close re-enters follow `press()` and the
 * pill visibly blooms a second time while it is still moving.
 */
internal fun shouldSuppressExternalPagerIndicatorFollow(
    ownedTargetIndex: Int?,
    previousExternalPosition: Float?,
    externalPosition: Float?,
    isPagerScrolling: Boolean,
    catchUpEpsilon: Float = EXTERNAL_PAGER_INDICATOR_CATCH_UP_EPSILON,
): Boolean {
    if (ownedTargetIndex == null) return false
    val target = ownedTargetIndex.toFloat()
    if (
        previousExternalPosition != null &&
        externalPosition != null &&
        abs(externalPosition - target) > abs(previousExternalPosition - target) + 0.0001f
    ) {
        return false
    }
    if (isPagerScrolling) return true
    if (externalPosition == null) return true
    return !isExternalPagerCaughtUpToOwnedTarget(
        ownedTargetIndex = ownedTargetIndex,
        externalPosition = externalPosition,
        catchUpEpsilon = catchUpEpsilon,
    )
}

internal fun isExternalPagerCaughtUpToOwnedTarget(
    ownedTargetIndex: Int?,
    externalPosition: Float?,
    catchUpEpsilon: Float = EXTERNAL_PAGER_INDICATOR_CATCH_UP_EPSILON,
): Boolean {
    if (ownedTargetIndex == null || externalPosition == null) return false
    return abs(externalPosition - ownedTargetIndex.toFloat()) <= catchUpEpsilon
}

internal fun shouldAnimateIndicatorToSelectedIndex(
    isDragging: Boolean,
    isPagerScrolling: Boolean,
    indicatorTarget: Float,
    selectedIndex: Int,
    ownedTargetIndex: Int?,
): Boolean {
    if (isDragging || isPagerScrolling) return false
    if (abs(indicatorTarget - selectedIndex.toFloat()) <= 0.001f) return false
    if (ownedTargetIndex != null && ownedTargetIndex != selectedIndex) return false
    return true
}

internal fun resolveFloatingDockVisualIndicatorPosition(
    internalPosition: Float,
    externalPosition: Float?,
    maxTabIndex: Int,
    externalPagerMotionEffectsEnabled: Boolean,
    isDragging: Boolean,
    ownedTargetIndex: Int?,
    isPagerScrolling: Boolean = false,
): Float {
    if (
        externalPagerMotionEffectsEnabled &&
        !isDragging &&
        ownedTargetIndex == null &&
        externalPosition != null
    ) {
        return externalPosition.coerceIn(0f, maxTabIndex.coerceAtLeast(0).toFloat())
    }
    return internalPosition
}

private class ExternalPagerIndicatorFollowGate {
    var ownedTargetIndex: Int? = null
    var previousExternalPosition: Float? = null
}

@Composable
fun RowScope.FloatingBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    itemIndex: Int? = null,
    iconCrossScaleEnabled: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalFloatingBottomBarTabScale.current
    val indicatorPosition = LocalFloatingBottomBarIndicatorPosition.current
    val alignmentOffset = LocalFloatingBottomBarItemAlignmentOffset.current
    val baseContentAlpha = LocalFloatingBottomBarBaseContentAlpha.current
    val indicatorStretchX = LocalFloatingBottomBarIndicatorStretchX.current
    val activeContent = LocalFloatingBottomBarActiveContent.current
    val contentColor = LocalFloatingBottomBarContentColor.current
    val selectionScale = remember(itemIndex, indicatorPosition, iconCrossScaleEnabled) {
        {
            if (!iconCrossScaleEnabled || itemIndex == null) {
                1f
            } else {
                val coverage = (1f - abs(itemIndex.toFloat() - indicatorPosition()))
                    .coerceIn(0f, 1f)
                resolveNavigationIconCrossScale(
                    enabled = true,
                    coverage = coverage,
                )
            }
        }
    }

    // Do not clip(CircleShape): BiliPai reminder badges offset outside the icon and would be
    // cropped (BiliPai items are icon-only and can clip safely).
    Column(
        modifier
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics {
                this.selected = selected
            }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                val stretch = if (activeContent) indicatorStretchX() else 1f
                scaleX = resolveFloatingDockCapturedContentHorizontalScale(
                    itemScale = s,
                    indicatorScaleX = stretch,
                    indicatorScaleY = 1f,
                )
                scaleY = s
                translationX = itemIndex?.let(alignmentOffset) ?: 0f
                alpha = if (!activeContent && itemIndex != null) {
                    baseContentAlpha(itemIndex)
                } else {
                    1f
                }
                // Keep badge pixels outside the item bounds.
                clip = false
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val columnScope = this
        CompositionLocalProvider(
            MiuixLocalContentColor provides contentColor,
            M3LocalContentColor provides contentColor,
            LocalFloatingBottomBarItemSelectionScale provides selectionScale,
        ) {
            content(columnScope)
        }
    }
}

@Composable
fun FloatingBottomBar(
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    onReselected: () -> Unit = {},
    backdrop: Backdrop?,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    mode: FloatingBottomBarMode = FloatingBottomBarMode.LiquidGlass,
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
    shellHeight: Dp = FloatingBottomBarDefaultShellHeight,
    indicatorHeight: Dp = FloatingBottomBarIndicatorHeight,
    indicatorWidth: Dp? = null,
    minimumIndicatorWidth: Dp = 0.dp,
    geometryMode: FloatingBottomBarGeometryMode = FloatingBottomBarGeometryMode.Dock,
    contentHorizontalPadding: Dp = 4.dp,
    contentVerticalPadding: Dp = 4.dp,
    tapPressRefractionEnabled: Boolean = true,
    indicatorIdleSurfaceColorOverride: Color? = null,
    indicatorPositionProvider: (() -> Float)? = null,
    isScrollInProgressProvider: () -> Boolean = { false },
    dragSelectionEnabled: Boolean = true,
    longPressDragSelectionEnabled: Boolean = false,
    dragTrackingMode: DampedDragTrackingMode = DampedDragTrackingMode.SPRING,
    onIndicatorPositionChanged: ((Float) -> Unit)? = null,
    externalPagerMotionEffectsEnabled: Boolean = false,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    content: @Composable RowScope.() -> Unit
) {
    // Do not use the system night flag here: BiliPai supports an app-only dark theme.
    val isInDark = resolveBottomBarDarkTheme(AppSurfaceTokens.background())
    val isLiquidGlassMode = mode == FloatingBottomBarMode.LiquidGlass
    val segmentedGeometry = geometryMode != FloatingBottomBarGeometryMode.Dock
    val allowOverflow = isLiquidGlassMode || !segmentedGeometry
    val horizontalPadding = contentHorizontalPadding.coerceAtLeast(0.dp)
    val verticalPadding = contentVerticalPadding.coerceIn(0.dp, shellHeight.coerceAtLeast(0.dp) / 2)
    val horizontalPaddingLatest = rememberUpdatedState(horizontalPadding)
    val pillShape = remember { resolveSharedBottomBarCapsuleShape() }
    val isBlurMode = mode == FloatingBottomBarMode.Blur
    val adaptiveReadabilityEnabled = isLiquidGlassMode &&
        liquidGlassTuning.readabilityMode == LiquidGlassReadabilityMode.ADAPTIVE
    val adaptiveReadabilityState = rememberLiquidGlassAdaptiveReadabilityState(
        enabled = adaptiveReadabilityEnabled,
    )
    val resolvedContentColor = rememberLiquidGlassAdaptiveContentColor(
        stableColor = colors.contentColor,
        state = adaptiveReadabilityState,
        enabled = adaptiveReadabilityEnabled,
        contrastBackgroundColor = colors.containerColor,
    )
    val readabilityScrimColor = if (isInDark) Color.Black else Color.White
    val containerColor =
        if (isLiquidGlassMode) {
            colors.containerColor.copy(alpha = liquidGlassTuning.surfaceAlpha)
        } else {
            colors.containerColor
        }

    val tabsBackdropSource = if (isLiquidGlassMode) rememberChromeBackdropSource() else null
    val tabsBackdrop = tabsBackdropSource?.backdrop
    val density = LocalDensity.current
    val shellLensDp = resolveCompactDockLensDp(shellHeight.value)
    val pressBloomDp = resolveCompactDockPressBloomDp(shellHeight.value)
    val shellRefractionHeightDp = shellLensDp *
        liquidGlassTuning.refractionHeight / MIUIX_UPSTREAM_DOCK_SHELL_LENS_DP *
        liquidGlassTuning.contentDistortionScale
    val shellRefractionAmountDp = shellLensDp *
        liquidGlassTuning.refractionAmount / MIUIX_UPSTREAM_DOCK_SHELL_LENS_DP *
        liquidGlassTuning.contentDistortionScale
    val shellRefractionHeightPx = with(density) { shellRefractionHeightDp.dp.toPx() }
    val shellRefractionAmountPx = with(density) { shellRefractionAmountDp.dp.toPx() }
    val shellEffectPaddingPx = with(density) {
        resolveFloatingDockEffectPaddingDp(
            refractionAmountDp = shellRefractionAmountDp,
            pressBloomDp = pressBloomDp,
        ).dp.toPx()
    }
    val pressBloomPx = with(density) { pressBloomDp.dp.toPx() }
    val indicatorLensHeightPx = with(density) {
        resolveCompactDockIndicatorLensHeightDp(shellHeight.value).dp.toPx()
    }
    val indicatorLensAmountPx = with(density) {
        resolveCompactDockIndicatorLensAmountDp(shellHeight.value).dp.toPx()
    }
    val innerShadowRadius = resolveCompactDockInnerShadowRadiusDp(shellHeight.value).dp
    val tabPressScale = remember(shellHeight) {
        resolveCompactDockTabPressScale(shellHeight.value)
    }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val layoutDirection = LocalLayoutDirection.current
    val configuration = LocalConfiguration.current
    val systemGestures = WindowInsets.systemGestures
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val tabWidth = with(density) { tabWidthPx.toDp() }
    val fittedIndicatorWidth = if (indicatorWidth != null) {
        minOf(indicatorWidth, tabWidth).coerceAtLeast(minimumIndicatorWidth)
    } else {
        maxOf(tabWidth, minimumIndicatorWidth)
    }
    val fittedIndicatorWidthPx = with(density) { fittedIndicatorWidth.toPx() }
    val fittedIndicatorHeight = resolveFloatingDockIndicatorHeightDp(
        requestedHeightDp = indicatorHeight.value,
        tabWidthDp = fittedIndicatorWidth.value,
        geometryMode = geometryMode,
        shellHeightDp = shellHeight.value,
    ).dp
    val indicatorLensHeightRatio = if (segmentedGeometry) {
        (fittedIndicatorHeight.value / indicatorHeight.value.coerceAtLeast(0.001f))
            .coerceIn(0f, 1f)
    } else {
        1f
    }
    // Record the full shell behind segmented indicators, including their top/bottom edges.
    // Keep the capture capsule-shaped: the moving lens magnifies this layer while dragging,
    // so a rectangular capture leaks square inner corners into the liquid indicator.
    val capturedContentHeight = if (segmentedGeometry) {
        maxOf(shellHeight, fittedIndicatorHeight)
    } else {
        fittedIndicatorHeight
    }
    val matchedGeometry = remember(shellHeight, fittedIndicatorHeight) {
        resolveMatchedLiquidIndicatorGeometry(
            dockHeightDp = shellHeight.value,
            indicatorHeightDp = fittedIndicatorHeight.value,
        )
    }
    val pressedScale = matchedGeometry.pressedScale
    val scaleOverflowDp = remember(shellHeight, fittedIndicatorHeight, pressedScale) {
        ((fittedIndicatorHeight.value * pressedScale - shellHeight.value) / 2f)
            .coerceAtLeast(0f)
            .dp
    }
    class DockDragHitTest {
        var dockWindowLeftPx = 0f
        var screenWidthPx = 0f
        var leftInsetPx = 0f
        var rightInsetPx = 0f
    }

    val dragHitTest = remember { DockDragHitTest() }
    val fallbackEdgePx = with(density) { FLOATING_DOCK_PREDICTIVE_BACK_EDGE_DP.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val leftInsetPx = resolveFloatingDockDragEdgeInsetPx(
        systemInsetPx = systemGestures.getLeft(density, layoutDirection).toFloat(),
        fallbackPx = fallbackEdgePx,
    )
    val rightInsetPx = resolveFloatingDockDragEdgeInsetPx(
        systemInsetPx = systemGestures.getRight(density, layoutDirection).toFloat(),
        fallbackPx = fallbackEdgePx,
    )
    SideEffect {
        dragHitTest.screenWidthPx = screenWidthPx
        dragHitTest.leftInsetPx = leftInsetPx
        dragHitTest.rightInsetPx = rightInsetPx
    }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    val safeTabsCount = tabsCount.coerceAtLeast(1)
    val maxTabIndex = (safeTabsCount - 1).coerceAtLeast(0)
    val selectedIndexLatest = rememberUpdatedState(selectedIndex)
    val onSelectedLatest = rememberUpdatedState(onSelected)
    val onIndicatorPositionChangedLatest = rememberUpdatedState(onIndicatorPositionChanged)
    val indicatorPositionLatest by rememberUpdatedState(indicatorPositionProvider)
    val isScrollInProgressLatest by rememberUpdatedState(isScrollInProgressProvider)
    val pagerFollowGate = remember { ExternalPagerIndicatorFollowGate() }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DampedDragAnimationHolder() }

    val dampedDragAnimation = remember(
        animationScope,
        safeTabsCount,
        density,
        isLtr,
        dragTrackingMode,
    ) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndexLatest.value().coerceIn(0, maxTabIndex).toFloat(),
            valueRange = 0f..maxTabIndex.toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = pressedScale,
            trackingMode = dragTrackingMode,
            canDrag = { offset ->
                val animation = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false

                val indicatorX = animation.value * tabWidthPx
                val padding = with(density) { horizontalPaddingLatest.value.toPx() }
                val globalTouchX = if (isLtr) {
                    padding + indicatorX + offset.x
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                if (globalTouchX !in 0f..totalWidthPx) return@DampedDragAnimation false
                if (segmentedGeometry) return@DampedDragAnimation true
                shouldAcceptFloatingDockDragAtWindowX(
                    windowX = dragHitTest.dockWindowLeftPx + globalTouchX,
                    screenWidthPx = dragHitTest.screenWidthPx,
                    leftInsetPx = dragHitTest.leftInsetPx,
                    rightInsetPx = dragHitTest.rightInsetPx,
                )
            },
            onDragStarted = {
                pagerFollowGate.ownedTargetIndex = null
                pagerFollowGate.previousExternalPosition = null
            },
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, maxTabIndex)
                // The pointer gesture already owns press/release. Only settle the value here so
                // release is not launched twice and the indicator cannot visibly rebound twice.
                animateToValue(targetIndex.toFloat(), animatePress = false)
                val selected = selectedIndexLatest.value().coerceIn(0, maxTabIndex)
                pagerFollowGate.ownedTargetIndex = resolveIndicatorOwnedTargetOnDragStop(
                    targetIndex = targetIndex,
                    selectedIndex = selected,
                    hasExternalPagerPosition = indicatorPositionLatest != null,
                )
                pagerFollowGate.previousExternalPosition = null
                if (targetIndex != selected) {
                    onSelectedLatest.value(targetIndex)
                }
                animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    val nextPosition =
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, maxTabIndex.toFloat())
                    updateValue(nextPosition)
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }
    SideEffect {
        // Search reserving space beside the dock can retarget indicator geometry. Updating the
        // field keeps press bloom in sync without recreating the pointerInput owner.
        dampedDragAnimation.pressedScale = pressedScale
    }
    // Pager swipes are already continuous state. When explicitly requested, read that position
    // in layout/draw instead of depending solely on the coroutine mirror above. This keeps the
    // indicator attached to the finger while an adjacent control is resizing the dock.
    val visualIndicatorPositionProvider: () -> Float = {
        resolveFloatingDockVisualIndicatorPosition(
            internalPosition = dampedDragAnimation.value,
            externalPosition = indicatorPositionLatest?.invoke(),
            maxTabIndex = maxTabIndex,
            externalPagerMotionEffectsEnabled = externalPagerMotionEffectsEnabled,
            isDragging = dampedDragAnimation.isDragging,
            ownedTargetIndex = pagerFollowGate.ownedTargetIndex,
            isPagerScrolling = isScrollInProgressLatest(),
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { dampedDragAnimation.value }
            .collect { position -> onIndicatorPositionChangedLatest.value?.invoke(position) }
    }
    val itemAlignmentOffsetProvider: (Int) -> Float = { itemIndex ->
        if (tabWidthPx <= 0f) {
            0f
        } else {
            val position = visualIndicatorPositionProvider()
            if (itemIndex != position.fastRoundToInt().fastCoerceIn(0, maxTabIndex)) {
                0f
            } else {
                val alignmentPx = resolveFloatingDockIndicatorContentAlignmentPx(
                    position = position,
                    tabWidthPx = tabWidthPx,
                    tabsCount = safeTabsCount,
                    indicatorWidthPx = fittedIndicatorWidthPx,
                )
                if (isLtr) alignmentPx else -alignmentPx
            }
        }
    }
    val baseContentAlphaProvider: (Int) -> Float = { itemIndex ->
        // Fade the dock copy of the selected label in every mode. Leaving it opaque
        // under a clamped right-edge indicator stacks two glyphs (重影).
        val coverage = (1f - abs(itemIndex.toFloat() - visualIndicatorPositionProvider()))
            .coerceIn(0f, 1f)
        1f - coverage
    }

    LaunchedEffect(dampedDragAnimation, maxTabIndex) {
        snapshotFlow {
            Triple(
                selectedIndexLatest.value().coerceIn(0, maxTabIndex),
                dampedDragAnimation.isDragging,
                isScrollInProgressLatest(),
            )
        }
            .collectLatest { (index, isDragging, isPagerScrolling) ->
                if (
                    shouldAnimateIndicatorToSelectedIndex(
                        isDragging = isDragging,
                        isPagerScrolling = isPagerScrolling,
                        indicatorTarget = dampedDragAnimation.targetValue,
                        selectedIndex = index,
                        ownedTargetIndex = pagerFollowGate.ownedTargetIndex,
                    )
                ) {
                    // Tap selection keeps the same enlarge/move/shrink process as the home dock.
                    // The tightened scale springs keep it brief without removing the feedback.
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
            }
    }
    LaunchedEffect(dampedDragAnimation, maxTabIndex) {
        var pagerPressed = false
        snapshotFlow {
            val external = indicatorPositionLatest?.invoke()
            val scrolling = isScrollInProgressLatest()
            Triple(external, scrolling, dampedDragAnimation.isDragging)
        }.collect { (external, scrolling, dragging) ->
            if (dragging) {
                pagerPressed = false
                return@collect
            }
            if (
                shouldSuppressExternalPagerIndicatorFollow(
                    ownedTargetIndex = pagerFollowGate.ownedTargetIndex,
                    previousExternalPosition = pagerFollowGate.previousExternalPosition,
                    externalPosition = external,
                    isPagerScrolling = scrolling,
                )
            ) {
                pagerFollowGate.previousExternalPosition = external
                return@collect
            }
            pagerFollowGate.ownedTargetIndex = null
            pagerFollowGate.previousExternalPosition = external
            if (scrolling && external != null) {
                if (!pagerPressed) {
                    dampedDragAnimation.press()
                    pagerPressed = true
                }
                dampedDragAnimation.snapTo(external.coerceIn(0f, maxTabIndex.toFloat()))
            } else if (pagerPressed) {
                pagerPressed = false
                external?.let {
                    dampedDragAnimation.snapTo(it.coerceIn(0f, maxTabIndex.toFloat()))
                }
                dampedDragAnimation.release()
            } else if (external != null) {
                dampedDragAnimation.snapTo(external.coerceIn(0f, maxTabIndex.toFloat()))
            }
        }
    }

    val interactiveHighlight =
        if (isLiquidGlassMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            remember(animationScope) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        Offset(
                            if (isLtr) {
                                (visualIndicatorPositionProvider() + 0.5f) * tabWidthPx + panelOffset
                            } else {
                                size.width - (visualIndicatorPositionProvider() + 0.5f) * tabWidthPx + panelOffset
                            },
                            size.height / 2f
                        )
                    },
                    radius = { size ->
                        resolveDockInteractiveHighlightRadiusPx(
                            shellMinDimensionPx = size.minDimension,
                            tabWidthPx = tabWidthPx,
                        )
                    },
                )
            }
        } else {
            null
        }

    val baseHighlight = if (isLiquidGlassMode) rememberBiliPaiGravityHighlight(extraDegrees = -45f) else null
    val pillHighlight = if (isLiquidGlassMode) rememberBiliPaiGravityHighlight(
        extraDegrees = 90f,
        width = resolveDockPillHighlightWidthDp(
            indicatorWidthDp = fittedIndicatorWidth.value,
            indicatorHeightDp = fittedIndicatorHeight.value,
        ).dp,
    ) else null

    val combinedBackdrop = if (backdrop != null && tabsBackdrop != null) {
        rememberCombinedBackdrop(backdrop, tabsBackdrop)
    } else {
        backdrop
    }

    Box(
        modifier = modifier
            .trackLiquidGlassAdaptiveReadability(
                state = adaptiveReadabilityState,
                enabled = adaptiveReadabilityEnabled,
            )
            .floatingDockScaleOverflow(
                overflow = scaleOverflowDp,
                shellHeight = shellHeight,
            )
            .then(
                if (allowOverflow) {
                    Modifier.graphicsLayer { clip = false }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        CompositionLocalProvider(
            LocalFloatingBottomBarContentColor provides resolvedContentColor,
            LocalFloatingBottomBarIndicatorPosition provides visualIndicatorPositionProvider,
            LocalFloatingBottomBarItemAlignmentOffset provides itemAlignmentOffsetProvider,
            LocalFloatingBottomBarBaseContentAlpha provides baseContentAlphaProvider,
        ) {
            Row(
                Modifier
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                        dragHitTest.dockWindowLeftPx = coords.positionInWindow().x
                        tabWidthPx = resolveFloatingDockSlotWidthPx(
                            containerWidthPx = totalWidthPx,
                            horizontalPaddingPx = with(density) { horizontalPadding.toPx() },
                            itemCount = safeTabsCount,
                        )
                    }
                    .graphicsLayer {
                        translationX = panelOffset
                        if (allowOverflow) {
                            clip = false
                        }
                    }
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = if (isInDark) 0.2f else 0.1f,
                        ),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .then(
                        when {
                            isLiquidGlassMode && backdrop != null -> {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { pillShape },
                                    effects = {
                                        padding = maxOf(
                                            padding,
                                            shellEffectPaddingPx,
                                        )
                                        vibrancy(liquidGlassTuning.saturation)
                                        blur(
                                            liquidGlassTuning.backdropBlurRadius.dp.toPx(),
                                            liquidGlassTuning.backdropBlurRadius.dp.toPx()
                                        )
                                        lens(
                                            refractionHeight = shellRefractionHeightPx,
                                            refractionAmount = shellRefractionAmountPx,
                                            chromaticAberration =
                                                liquidGlassTuning.shellChromaticAberrationAmount,
                                        )
                                    },
                                    highlight = { baseHighlight?.value?.copy(alpha = 0.75f) },
                                    layerBlock = {
                                        val width = size.width.coerceAtLeast(1f)
                                        val s = lerp(
                                            1f,
                                            1f + pressBloomPx / width,
                                            dampedDragAnimation.pressProgress
                                        )
                                        scaleX = s
                                        scaleY = s
                                    },
                                    onDrawSurface = {
                                        drawRect(containerColor)
                                        if (liquidGlassTuning.contentReadabilityScrimAlpha > 0f) {
                                            drawRect(
                                                readabilityScrimColor.copy(
                                                    alpha = liquidGlassTuning.contentReadabilityScrimAlpha
                                                )
                                            )
                                        }
                                    },
                                )
                            }
                            isBlurMode && backdrop != null -> {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { pillShape },
                                    effects = {
                                        blur(25.dp.toPx(), 25.dp.toPx())
                                    },
                                    onDrawSurface = {
                                        drawRect(containerColor.copy(alpha = 0.65f))
                                    },
                                )
                            }
                            else -> {
                                Modifier.background(containerColor, pillShape)
                            }
                        }
                    )
                    .then(
                        if (isLiquidGlassMode && interactiveHighlight != null) {
                            interactiveHighlight.modifier
                        } else {
                            Modifier
                        }
                    )
                    .height(shellHeight)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = { content(this) }
            )
        }

        val indicatorStretchXProvider: () -> Float = {
            val scaleY = resolveFloatingDockIndicatorLayerScaleY(
                baseScaleY = dampedDragAnimation.scaleY,
                velocity = dampedDragAnimation.velocity,
            ).coerceAtLeast(0.001f)
            resolveFloatingDockIndicatorLayerScaleX(
                baseScaleX = dampedDragAnimation.scaleX,
                velocity = dampedDragAnimation.velocity,
            ) / scaleY
        }
        if (isLiquidGlassMode && backdrop != null) {
            CompositionLocalProvider(
                LocalFloatingBottomBarTabScale provides {
                    lerp(1f, tabPressScale, dampedDragAnimation.pressProgress)
                },
                LocalFloatingBottomBarContentColor provides colors.activeContentColor,
                LocalFloatingBottomBarActiveContent provides true,
                LocalFloatingBottomBarIndicatorPosition provides visualIndicatorPositionProvider,
                LocalFloatingBottomBarItemAlignmentOffset provides itemAlignmentOffsetProvider,
                LocalFloatingBottomBarIndicatorStretchX provides indicatorStretchXProvider,
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .then(tabsBackdropSource?.modifier ?: Modifier)
                        .graphicsLayer {
                            translationX = panelOffset
                            if (allowOverflow) {
                                clip = false
                            }
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy(liquidGlassTuning.saturation)
                                blur(
                                    liquidGlassTuning.backdropBlurRadius.dp.toPx(),
                                    liquidGlassTuning.backdropBlurRadius.dp.toPx()
                                )
                                lens(
                                    refractionHeight = shellRefractionHeightPx,
                                    refractionAmount = shellRefractionAmountPx,
                                    chromaticAberration =
                                        liquidGlassTuning.shellChromaticAberrationAmount,
                                )
                            },
                            onDrawSurface = {
                                drawRect(containerColor)
                                if (liquidGlassTuning.contentReadabilityScrimAlpha > 0f) {
                                    drawRect(
                                        readabilityScrimColor.copy(
                                            alpha = liquidGlassTuning.contentReadabilityScrimAlpha
                                        )
                                    )
                                }
                            },
                        )
                        .then(interactiveHighlight?.modifier ?: Modifier)
                        .height(capturedContentHeight)
                        .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    content(this)
                }
            }
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val tabsContentStartPx = with(density) { horizontalPadding.toPx() }

            if (isLiquidGlassMode && combinedBackdrop != null) {
                Box(
                    Modifier
                        .padding(horizontal = horizontalPadding)
                        .graphicsLayer {
                            val indicatorOffsetPx = resolveFloatingDockIndicatorOffsetPx(
                                position = visualIndicatorPositionProvider(),
                                tabWidthPx = tabWidthPx,
                                tabsCount = safeTabsCount,
                                indicatorWidthPx = fittedIndicatorWidthPx,
                            )
                            translationX = if (isLtr) {
                                indicatorOffsetPx + panelOffset
                            } else {
                                -indicatorOffsetPx + panelOffset
                            }
                            if (allowOverflow) {
                                clip = false
                            }
                        }
                        .clearAndSetSemantics {}
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = resolveFloatingDockRefractionProgress(
                                    pressProgress = dampedDragAnimation.pressProgress,
                                    tapPressRefractionEnabled = tapPressRefractionEnabled,
                                    isDragging = dampedDragAnimation.isDragging,
                                    isPagerScrolling = isScrollInProgressLatest(),
                                )
                                lens(
                                    refractionHeight = indicatorLensHeightPx * progress * indicatorLensHeightRatio *
                                        liquidGlassTuning.indicatorLensBoost *
                                        liquidGlassTuning.contentDistortionScale,
                                    refractionAmount = indicatorLensAmountPx * progress * indicatorLensHeightRatio *
                                        liquidGlassTuning.indicatorEdgeWarpBoost *
                                        liquidGlassTuning.contentDistortionScale,
                                    depthEffect = true,
                                    chromaticAberration =
                                        resolveLiquidGlassIndicatorChromaticAberration(
                                            liquidGlassTuning
                                        ),
                                )
                            },
                            highlight = {
                                pillHighlight?.value?.copy(alpha = dampedDragAnimation.pressProgress)
                            },
                            layerBlock = {
                                scaleY = resolveFloatingDockIndicatorLayerScaleY(
                                    baseScaleY = dampedDragAnimation.scaleY,
                                    velocity = dampedDragAnimation.velocity,
                                )
                                scaleX = resolveFloatingDockIndicatorLayerScaleX(
                                    baseScaleX = dampedDragAnimation.scaleX,
                                    velocity = dampedDragAnimation.velocity,
                                )
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    color = indicatorIdleSurfaceColorOverride ?: if (!isInDark) {
                                        Color.Black.copy(alpha = 0.1f)
                                    } else {
                                        Color.White.copy(alpha = 0.1f)
                                    },
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            InnerShadow(
                                radius = innerShadowRadius * dampedDragAnimation.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = dampedDragAnimation.pressProgress,
                            )
                        }
                        .height(fittedIndicatorHeight)
                        .width(fittedIndicatorWidth)
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding)
                        .graphicsLayer {
                            val indicatorOffsetPx = resolveFloatingDockIndicatorOffsetPx(
                                position = visualIndicatorPositionProvider(),
                                tabWidthPx = tabWidthPx,
                                tabsCount = safeTabsCount,
                                indicatorWidthPx = fittedIndicatorWidthPx,
                            )
                            translationX = if (isLtr) {
                                indicatorOffsetPx + panelOffset
                            } else {
                                -indicatorOffsetPx + panelOffset
                            }
                            if (allowOverflow) {
                                clip = false
                            }
                        }
                        .clip(pillShape)
                        .then(
                            if (indicatorIdleSurfaceColorOverride != null || isLiquidGlassMode) {
                                Modifier.background(
                                    indicatorIdleSurfaceColorOverride ?: colors.contentColor.copy(alpha = 0.1f),
                                    pillShape,
                                )
                            } else {
                                Modifier
                            }
                        )
                        .height(fittedIndicatorHeight)
                        .width(fittedIndicatorWidth),
                    contentAlignment = Alignment.CenterStart
                ) {
                    CompositionLocalProvider(
                        LocalFloatingBottomBarContentColor provides colors.activeContentColor,
                        LocalFloatingBottomBarActiveContent provides true,
                        LocalFloatingBottomBarIndicatorPosition provides visualIndicatorPositionProvider,
                        LocalFloatingBottomBarItemAlignmentOffset provides itemAlignmentOffsetProvider,
                    ) {
                        Row(
                            Modifier
                                .clearAndSetSemantics {}
                                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                .requiredWidth(
                                    with(density) {
                                        (totalWidthPx - (horizontalPadding * 2).toPx()).coerceAtLeast(0f).toDp()
                                    }
                                )
                                .requiredHeight(capturedContentHeight)
                                .graphicsLayer {
                                    val contentTranslationPx =
                                        resolveFloatingDockClippedContentTranslationPx(
                                            position = visualIndicatorPositionProvider(),
                                            tabWidthPx = tabWidthPx,
                                            tabsCount = safeTabsCount,
                                            indicatorWidthPx = fittedIndicatorWidthPx,
                                        )
                                    translationX = if (isLtr) {
                                        contentTranslationPx
                                    } else {
                                        -contentTranslationPx
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            content = { content(this) }
                        )
                    }
                }
            }

            // The selected capsule can be wider than its tab when the adjacent search button
            // compresses the dock. Keep pointer input in the logical tab slot so the visual
            // overflow cannot steal taps from neighbouring destinations.
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val slotOffsetPx = visualIndicatorPositionProvider() * tabWidthPx
                        translationX = if (isLtr) {
                            tabsContentStartPx + slotOffsetPx + panelOffset
                        } else {
                            -tabsContentStartPx - slotOffsetPx + panelOffset
                        }
                        if (allowOverflow) {
                            clip = false
                        }
                    }
                    .then(interactiveHighlight?.gestureModifier ?: Modifier)
                    .then(
                        when {
                            // Legacy long-press callers also use immediate indicator dragging.
                            // The hit target is only the selected slot, leaving the rest of a
                            // scrollable rail available for ordinary horizontal scrolling.
                            (dragSelectionEnabled || longPressDragSelectionEnabled) && safeTabsCount > 1 ->
                                dampedDragAnimation.modifier
                            else -> Modifier
                        }
                    )
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = onReselected,
                    )
                    .clearAndSetSemantics {}
                    .height(if (segmentedGeometry) shellHeight else fittedIndicatorHeight)
                    .width(tabWidthDp),
            )
        }
    }
}
