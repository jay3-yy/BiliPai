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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
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

private val LocalFloatingBottomBarTapFeedback = staticCompositionLocalOf<(Int) -> Unit> { { _ -> } }

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

private val LocalFloatingBottomBarContentVerticalPadding = staticCompositionLocalOf { 0.dp }

// Keep wide dock shells calmer than individual elastic buttons.
private const val DOCK_SHELL_ELASTIC_GAIN = 0.6f
private const val DOCK_SHELL_MAX_EXTRA_STRETCH = 0.025f

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
    indicatorContainerColor: Color = colors.indicatorColor.copy(alpha = 0.14f),
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
                .fillMaxHeight()
                .clip(shape)
                .background(indicatorContainerColor, shape),
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
    hasExternalPagerFollow: Boolean = false,
): Boolean {
    if (isDragging || isPagerScrolling) return false
    // Continuous external pager follow already owns the pill. A second discrete spring on top of
    // it is what makes multi-page tab switches look stepped.
    if (hasExternalPagerFollow && ownedTargetIndex == null) return false
    if (abs(indicatorTarget - selectedIndex.toFloat()) <= 0.001f) return false
    if (ownedTargetIndex != null && ownedTargetIndex != selectedIndex) return false
    return true
}

/** A scroll mutation without page displacement must not start a lens press pulse. */
internal fun shouldStartExternalPagerIndicatorPress(
    effectsEnabled: Boolean,
    isPagerScrolling: Boolean,
    externalPosition: Float?,
): Boolean {
    if (!effectsEnabled || !isPagerScrolling || externalPosition == null ||
        !externalPosition.isFinite()
    ) return false
    return abs(externalPosition - externalPosition.fastRoundToInt().toFloat()) > 0.001f
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
    val tapFeedback = LocalFloatingBottomBarTapFeedback.current
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
                onClick = {
                    if (!selected && !activeContent) itemIndex?.let(tapFeedback)
                    onClick()
                }
            )
            .semantics {
                this.selected = selected
            }
            .fillMaxHeight()
            .weight(1f)
            .padding(vertical = LocalFloatingBottomBarContentVerticalPadding.current)
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
    indicatorContentWidths: List<Dp>? = null,
    minimumIndicatorWidth: Dp = 0.dp,
    proportionalIndicatorReferenceWidth: Dp? = null,
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
    onIndicatorDragStateChanged: ((Boolean) -> Unit)? = null,
    externalPagerMotionEffectsEnabled: Boolean = false,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    drawShell: Boolean = true,
    onDockTranslationProviderChanged: (((() -> Float)) -> Unit)? = null,
    dragScrollState: ScrollState? = null,
    dragScrollViewportWidthPx: () -> Float = { 0f },
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

    val tabsBackdropSource = if (isLiquidGlassMode) {
        rememberChromeBackdropSource(drawContentToScreen = false)
    } else null
    val tabsBackdrop = tabsBackdropSource?.backdrop
    val density = LocalDensity.current
    val shellLensDp = resolveCompactDockLensDp(shellHeight.value)
    val pressBloomDp = resolveCompactDockPressBloomDp(shellHeight.value) * DOCK_SHELL_ELASTIC_GAIN
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
    val contentIndicatorWidthsPx = remember(indicatorContentWidths, density) {
        indicatorContentWidths?.map { with(density) { it.toPx() } }.orEmpty()
    }
    val fittedIndicatorHeight = resolveFloatingDockIndicatorHeightDp(
        requestedHeightDp = indicatorHeight.value,
        tabWidthDp = fittedIndicatorWidth.value,
        geometryMode = geometryMode,
        shellHeightDp = shellHeight.value,
        proportionalReferenceWidthDp = proportionalIndicatorReferenceWidth?.value,
    ).dp
    // Navigation capsules share the shell's rounded contour, inset by the current
    // resting margin. Derive the radius again as the dock changes size.
    val indicatorShape = if (geometryMode != FloatingBottomBarGeometryMode.Segmented) {
        val restingInset = ((shellHeight - fittedIndicatorHeight) / 2).coerceAtLeast(0.dp)
        RoundedCornerShape(
            (shellHeight / 2 - restingInset)
                .coerceIn(0.dp, minOf(fittedIndicatorWidth, fittedIndicatorHeight) / 2),
        )
    } else {
        pillShape
    }
    // Match the home top dock: capture unscaled labels at indicator height.
    // The consuming glass layer owns the capsule and label deformation together.
    val capturedContentHeight = fittedIndicatorHeight
    val matchedGeometry = remember(shellHeight, fittedIndicatorHeight, geometryMode) {
        resolveFloatingDockIndicatorGeometry(
            shellHeightDp = shellHeight.value,
            indicatorHeightDp = fittedIndicatorHeight.value,
            geometryMode = geometryMode,
        )
    }
    val pressedScale = matchedGeometry.pressedScale
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
    val offsetJobHolder = remember { object { var job: Job? = null } }
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
    val onIndicatorDragStateChangedLatest = rememberUpdatedState(onIndicatorDragStateChanged)
    val dragScrollViewportWidthLatest = rememberUpdatedState(dragScrollViewportWidthPx)
    val indicatorPositionLatest by rememberUpdatedState(indicatorPositionProvider)
    val isScrollInProgressLatest by rememberUpdatedState(isScrollInProgressProvider)
    val externalPagerMotionEffectsLatest by rememberUpdatedState(externalPagerMotionEffectsEnabled)
    val pagerFollowGate = remember { ExternalPagerIndicatorFollowGate() }
    var selectionTapFeedbackActive by remember { mutableStateOf(false) }
    val selectionTapFeedbackJob = remember {
        object {
            var job: Job? = null
            var generation: Long = 0L
        }
    }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }
    val holder = remember { DampedDragAnimationHolder() }
    val interactiveHighlight =
        if (com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current) {
            remember(animationScope, holder) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    // The captured material must receive exactly the same press progress
                    // for pointer drags, tap springs and externally driven pager motion.
                    pressProgressProvider = { holder.instance?.pressProgress ?: 0f },
                )
            }
        } else {
            null
        }

    val dampedDragAnimation = remember(
        animationScope,
        safeTabsCount,
        density,
        isLtr,
        dragTrackingMode,
        interactiveHighlight,
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
            onDragStarted = { point ->
                selectionTapFeedbackJob.generation++
                selectionTapFeedbackJob.job?.cancel()
                selectionTapFeedbackActive = false
                val padding = with(density) { horizontalPaddingLatest.value.toPx() }
                val slotStart = if (isLtr) {
                    padding + targetValue * tabWidthPx
                } else {
                    totalWidthPx - padding - tabWidthPx - targetValue * tabWidthPx
                }
                interactiveHighlight?.start(Offset(slotStart + point.x, point.y))
                onIndicatorDragStateChangedLatest.value?.invoke(true)
                pagerFollowGate.ownedTargetIndex = null
                pagerFollowGate.previousExternalPosition = null
            },
            onDragStopped = {
                interactiveHighlight?.release()
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
                onIndicatorDragStateChangedLatest.value?.invoke(false)
                offsetJobHolder.job?.cancel()
                offsetJobHolder.job = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                interactiveHighlight?.let { light ->
                    light.updatePosition(light.currentPosition + dragAmount)
                }
                if (tabWidthPx > 0f) {
                    val nextPosition =
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, maxTabIndex.toFloat())
                    updateValue(nextPosition)
                    offsetJobHolder.job?.cancel()
                    offsetJobHolder.job = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }
    val selectionTapFeedback: (Int) -> Unit = { targetIndex ->
        if (interactiveHighlight != null) {
            val generation = ++selectionTapFeedbackJob.generation
            selectionTapFeedbackJob.job?.cancel()
            selectionTapFeedbackActive = true
            dampedDragAnimation.press()
            // This independent job cannot be cancelled by the pager's snapTo.
            // The business click below does not wait for it or the scale spring.
            selectionTapFeedbackJob.job = animationScope.launch {
                try {
                    withTimeoutOrNull(com.android.purebilibili.core.ui.animation.LiquidIndicatorMotion.BloomWaitMillis) {
                        snapshotFlow { minOf(dampedDragAnimation.scaleX, dampedDragAnimation.scaleY) }
                            .first { it >= pressedScale }
                    }
                    // Pager snapTo continuously updates targetValue, so comparing the
                    // local animation with its own target cannot detect click completion.
                    // Hold the bloom until the clicked destination really lands; a new
                    // click or a pointer drag cancels this job instead of a fixed timeout.
                    snapshotFlow {
                        val position = indicatorPositionLatest?.invoke() ?: dampedDragAnimation.value
                        !isScrollInProgressLatest() && abs(position - targetIndex.toFloat()) <= com.android.purebilibili.core.ui.animation.LiquidIndicatorMotion.SettleTolerance
                    }.first { it }
                    if (!dampedDragAnimation.isDragging) dampedDragAnimation.release()
                } finally {
                    if (selectionTapFeedbackJob.generation == generation) selectionTapFeedbackActive = false
                }
            }
        }
    }
    SideEffect {
        // Search reserving space beside the dock can retarget indicator geometry. Updating the
        // field keeps press bloom in sync without recreating the pointerInput owner.
        dampedDragAnimation.pressedScale = pressedScale
    }
    LaunchedEffect(dampedDragAnimation, dragScrollState, density) {
        val scroll = dragScrollState ?: return@LaunchedEffect
        snapshotFlow { dampedDragAnimation.isDragging }.collectLatest { dragging ->
            if (!dragging) return@collectLatest
            scroll.scroll {
                var previousFrame = withFrameNanos { it }
                while (dampedDragAnimation.isDragging) {
                    val frame = withFrameNanos { it }
                    val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.032f)
                    previousFrame = frame
                    if (tabWidthPx <= 0f) continue
                    // Logical coordinates start at the first tab, so RTL uses the same
                    // edge rule and ScrollState direction as LTR.
                    val center = with(density) { horizontalPaddingLatest.value.toPx() } +
                        (dampedDragAnimation.targetValue + 0.5f) * tabWidthPx - scroll.value
                    val viewport = dragScrollViewportWidthLatest.value()
                    val edge = minOf(tabWidthPx * 0.65f, viewport / 3f)
                    val velocity = resolveDockDragEdgeScrollFraction(center, viewport, edge) *
                        with(density) { 360.dp.toPx() }
                    val consumed = scrollBy(velocity * seconds)
                    if (consumed != 0f) {
                        dampedDragAnimation.updateValue(
                            (dampedDragAnimation.targetValue + consumed / tabWidthPx)
                                .coerceIn(0f, maxTabIndex.toFloat())
                        )
                    }
                }
            }
        }
    }
    // Pager swipes are already continuous state. When explicitly requested, read that position
    // in layout/draw instead of depending solely on the coroutine mirror above. This keeps the
    // indicator attached to the finger while an adjacent control is resizing the dock.
    val rawIndicatorPositionProvider: () -> Float = {
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
    val indicatorWidthPxProvider: () -> Float = {
        // Fixed-width docks do not depend on animated position during measurement.
        if (contentIndicatorWidthsPx.isEmpty()) fittedIndicatorWidthPx else {
            resolveFloatingDockContentIndicatorWidthPx(
                rawIndicatorPositionProvider(), contentIndicatorWidthsPx, fittedIndicatorWidthPx,
            )
        }
    }
    // Material boundary: native/blur and fixed-width docks retain their original width
    // modifier. Glass keeps continuous optical interpolation, while measurement observes
    // only the rounded pixel width, not every position/velocity update along the gesture.
    val indicatorWidthModifier = if (!isLiquidGlassMode || contentIndicatorWidthsPx.isEmpty()) {
        Modifier.width(fittedIndicatorWidth)
    } else {
        val latestWidthProvider = rememberUpdatedState(indicatorWidthPxProvider)
        val measuredWidth = remember {
            derivedStateOf(structuralEqualityPolicy()) {
                latestWidthProvider.value().roundToInt()
            }
        }
        Modifier.liquidIndicatorWidth { measuredWidth.value }
    }
    val shellContentInsetProvider: (Boolean) -> Float = { leading ->
        if (contentIndicatorWidthsPx.isEmpty() || !drawShell) 0f else {
            val index = if (leading) 0f else maxTabIndex.toFloat()
            resolveFloatingDockContentShellInsetPx(
                tabWidthPx,
                resolveFloatingDockContentIndicatorWidthPx(index, contentIndicatorWidthsPx, fittedIndicatorWidthPx),
            )
        }
    }
    val visualIndicatorPositionProvider: () -> Float = {
        val position = rawIndicatorPositionProvider()
        val scroll = dragScrollState
        if (scroll == null) position else resolveFloatingDockViewportIndicatorPosition(
            position = position,
            tabWidthPx = tabWidthPx,
            tabsCount = safeTabsCount,
            indicatorWidthPx = indicatorWidthPxProvider(),
            scrollOffsetPx = scroll.value.toFloat(),
            viewportWidthPx = dragScrollViewportWidthLatest.value(),
            horizontalPaddingPx = with(density) { horizontalPaddingLatest.value.toPx() },
            clampToViewport = dampedDragAnimation.isDragging &&
                (dragSelectionEnabled || longPressDragSelectionEnabled),
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { dampedDragAnimation.value }
            .collect { position -> onIndicatorPositionChangedLatest.value?.invoke(position) }
    }
    SideEffect {
        interactiveHighlight?.drawPositionProvider = { drawSize ->
            if (dampedDragAnimation.isDragging || tabWidthPx <= 0f) {
                null // The pointer owns the light during a real drag.
            } else {
                // Use the exact clamped capsule geometry, including compact controls
                // whose indicator is narrower than one slot. No coroutine mirror or
                // press-only gate can strand the light at the previous selection.
                val offset = resolveFloatingDockIndicatorOffsetPx(
                    position = visualIndicatorPositionProvider(),
                    tabWidthPx = tabWidthPx,
                    tabsCount = safeTabsCount,
                    indicatorWidthPx = indicatorWidthPxProvider(),
                )
                val centerX = with(density) { horizontalPaddingLatest.value.toPx() } +
                    offset + indicatorWidthPxProvider() / 2f
                val leadingInset = shellContentInsetProvider(true)
                val shellWidth = totalWidthPx - leadingInset - shellContentInsetProvider(false)
                val shellInset = if (leadingInset > 0f && abs(drawSize.width - shellWidth) < 1f) {
                    leadingInset
                } else 0f
                val localCenterX = centerX - shellInset
                Offset(
                    x = if (isLtr) localCenterX else drawSize.width - localCenterX,
                    y = drawSize.height / 2f,
                )
            }
        }
    }
    val indicatorOpticalProgressProvider: () -> Float = {
        // The press spring settles before the size springs. Keep the lens and surface
        // transition alive through the complete shrink, including spring undershoot.
        val sizeProgress = if (pressedScale > 1f) {
            maxOf(
                abs(dampedDragAnimation.scaleX - 1f),
                abs(dampedDragAnimation.scaleY - 1f),
            ) / (pressedScale - 1f)
        } else 0f
        resolveFloatingDockRefractionProgress(
            pressProgress = maxOf(dampedDragAnimation.pressProgress, sizeProgress),
            tapPressRefractionEnabled = tapPressRefractionEnabled,
            isDragging = dampedDragAnimation.isDragging,
            isPagerScrolling = shouldStartExternalPagerIndicatorPress(
                effectsEnabled = externalPagerMotionEffectsLatest,
                isPagerScrolling = isScrollInProgressLatest(),
                externalPosition = indicatorPositionLatest?.invoke(),
            ),
            selectionMotionProgress = resolveFloatingDockSelectionMotionProgress(
                position = visualIndicatorPositionProvider(),
                targetPosition = dampedDragAnimation.targetValue,
            ),
        )
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
                    indicatorWidthPx = indicatorWidthPxProvider(),
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
                        hasExternalPagerFollow = externalPagerMotionEffectsEnabled &&
                            indicatorPositionLatest != null,
                    )
                ) {
                    // Tap selection keeps the same enlarge/move/shrink process as the home dock.
                    // The tightened scale springs keep it brief without removing the feedback.
                    dampedDragAnimation.animateToValue(index.toFloat(), animatePress = !selectionTapFeedbackActive)
                }
            }
    }
    LaunchedEffect(dampedDragAnimation, maxTabIndex) {
        var pagerMotionActive = false
        snapshotFlow {
            val external = indicatorPositionLatest?.invoke()
            val scrolling = isScrollInProgressLatest()
            Triple(external, scrolling, dampedDragAnimation.isDragging)
        }.collect { (external, scrolling, dragging) ->
            if (dragging) {
                pagerMotionActive = false
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
                val followPagerBloom = !selectionTapFeedbackActive &&
                    (pagerMotionActive || shouldStartExternalPagerIndicatorPress(
                        effectsEnabled = externalPagerMotionEffectsLatest,
                        isPagerScrolling = scrolling,
                        externalPosition = external,
                    ))
                if (followPagerBloom) {
                    pagerMotionActive = true
                    dampedDragAnimation.followPagerPosition(external)
                } else {
                    dampedDragAnimation.snapTo(
                        external.coerceIn(0f, maxTabIndex.toFloat()),
                        trackVelocity = externalPagerMotionEffectsLatest,
                    )
                }
            } else if (pagerMotionActive) {
                pagerMotionActive = false
                external?.let {
                    dampedDragAnimation.snapTo(it.coerceIn(0f, maxTabIndex.toFloat()))
                }
                // Page displacement already completed the bloom. Never grow it again on release.
                if (!selectionTapFeedbackActive) dampedDragAnimation.release(waitForBloom = false)
            } else if (external != null) {
                dampedDragAnimation.snapTo(external.coerceIn(0f, maxTabIndex.toFloat()))
            }
        }
    }

    // Kyant button rubber band, driven by the indicator pointer (including vertical deltas).
    // Releasing the shared light position spring also returns the complete dock to its anchor.
    val dockDragTranslationY: () -> Float = remember(density, shellHeight, interactiveHighlight) {
        {
            val heightPx = with(density) { shellHeight.toPx() }.coerceAtLeast(1f)
            heightPx * kotlin.math.tanh(0.05f * (interactiveHighlight?.offset?.y ?: 0f) / heightPx) * DOCK_SHELL_ELASTIC_GAIN
        }
    }
    SideEffect { onDockTranslationProviderChanged?.invoke(dockDragTranslationY) }

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
            LocalFloatingBottomBarTapFeedback provides selectionTapFeedback,
            LocalFloatingBottomBarIndicatorPosition provides visualIndicatorPositionProvider,
            LocalFloatingBottomBarItemAlignmentOffset provides itemAlignmentOffsetProvider,
            LocalFloatingBottomBarBaseContentAlpha provides baseContentAlphaProvider,
            LocalFloatingBottomBarContentVerticalPadding provides verticalPadding,
        ) {
            val shellModifier = if (drawShell) {
                Modifier
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
                                        val dragY = kotlin.math.abs(interactiveHighlight?.offset?.y ?: 0f)
                                        val extraStretch = (4.dp.toPx() / size.height.coerceAtLeast(1f)) *
                                            (dragY / width) * (size.height / width).coerceAtMost(1f) *
                                            DOCK_SHELL_ELASTIC_GAIN
                                        scaleY = s + extraStretch.coerceAtMost(DOCK_SHELL_MAX_EXTRA_STRETCH)
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
                            else -> Modifier
                                .graphicsLayer {
                                    val bloom = if (interactiveHighlight != null) dampedDragAnimation.pressProgress else 0f
                                    val scale = 1f + pressBloomPx / size.width.coerceAtLeast(1f) * bloom
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .background(containerColor, pillShape)
                        }
                    )
                    .then(
                        if (interactiveHighlight != null) {
                            interactiveHighlight.modifier
                        } else {
                            Modifier
                        }
                    )
            } else {
                Modifier
            }
            if (drawShell && contentIndicatorWidthsPx.isNotEmpty()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .layout { measurable, constraints ->
                            val leading = shellContentInsetProvider(true).roundToInt()
                            val trailing = shellContentInsetProvider(false).roundToInt()
                            val shellWidth = (constraints.maxWidth - leading - trailing).coerceAtLeast(0)
                            val placeable = measurable.measure(constraints.copy(
                                minWidth = shellWidth, maxWidth = shellWidth,
                            ))
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.placeRelative(leading, 0)
                            }
                        }
                        .graphicsLayer {
                            translationX = panelOffset
                            translationY = dockDragTranslationY()
                        }
                        .then(shellModifier)
                )
            }
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
                        translationY = dockDragTranslationY()
                        if (allowOverflow) {
                            clip = false
                        }
                    }
                    .then(if (contentIndicatorWidthsPx.isEmpty()) shellModifier else Modifier)
                    .drawWithContent {
                        val scroll = dragScrollState
                        val viewportWidth = dragScrollViewportWidthLatest.value()
                        if (scroll == null || viewportWidth <= 0f) {
                            drawContent()
                        } else {
                            // The scroll viewport has lens overflow padding. Only the
                            // optical layers may use it; ordinary labels stay in the dock.
                            val left = if (isLtr) {
                                scroll.value.toFloat()
                            } else {
                                (size.width - viewportWidth - scroll.value).coerceAtLeast(0f)
                            }
                            clipRect(
                                left = left,
                                right = left + viewportWidth,
                                top = -size.height,
                                bottom = size.height * 2f,
                            ) { this@drawWithContent.drawContent() }
                        }
                    }
                    .height(shellHeight)
                    .padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = { content(this) }
            )
        }

        // No inverse stretch or extra press zoom in the exported labels. The lens
        // applies the same single transform as the home top dock's capture path.
        val indicatorStretchXProvider: () -> Float = { 1f }
        if (isLiquidGlassMode && backdrop != null) {
            CompositionLocalProvider(
                LocalFloatingBottomBarTabScale provides { 1f },
                LocalFloatingBottomBarContentColor provides colors.activeContentColor,
                LocalFloatingBottomBarActiveContent provides true,
                LocalFloatingBottomBarIndicatorPosition provides visualIndicatorPositionProvider,
                LocalFloatingBottomBarItemAlignmentOffset provides itemAlignmentOffsetProvider,
                LocalFloatingBottomBarIndicatorStretchX provides indicatorStretchXProvider,
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .then(tabsBackdropSource?.modifier ?: Modifier)
                        .graphicsLayer {
                            translationX = panelOffset
                            translationY = dockDragTranslationY()
                            if (allowOverflow) {
                                clip = false
                            }
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { indicatorShape },
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
                        // Upstream records the shell's press light into the tinted material.
                        // The indicator refracts this light even over a uniform page backdrop.
                        .then(interactiveHighlight?.modifier ?: Modifier)
                        .height(capturedContentHeight)
                        .align(Alignment.CenterStart)
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
                                indicatorWidthPx = indicatorWidthPxProvider(),
                            )
                            translationX = if (isLtr) {
                                indicatorOffsetPx + panelOffset
                            } else {
                                -indicatorOffsetPx + panelOffset
                            }
                            translationY = dockDragTranslationY()
                            if (allowOverflow) {
                                clip = false
                            }
                        }
                        .clearAndSetSemantics {}
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { indicatorShape },
                            effects = {
                                val progress = indicatorOpticalProgressProvider()
                                val shellWidth = totalWidthPx.coerceAtLeast(1f)
                                val shellHeightPx = shellHeight.toPx().coerceAtLeast(1f)
                                val shellBloom = 1f + pressBloomPx / shellWidth * dampedDragAnimation.pressProgress
                                val dragY = abs(interactiveHighlight?.offset?.y ?: 0f)
                                val shellStretch = (4.dp.toPx() / shellHeightPx) * (dragY / shellWidth) *
                                    (shellHeightPx / shellWidth).coerceAtMost(1f) * DOCK_SHELL_ELASTIC_GAIN
                                homeIndicatorLens(
                                    restHeightDp = fittedIndicatorHeight.value,
                                    progress = progress,
                                    tuning = liquidGlassTuning,
                                    shellHeightDp = shellHeight.value,
                                    indicatorScaleY = resolveFloatingDockIndicatorLayerScaleY(
                                        baseScaleY = dampedDragAnimation.scaleY,
                                        velocity = dampedDragAnimation.velocity,
                                    ),
                                    shellScaleY = if (drawShell) {
                                        shellBloom + shellStretch.coerceAtMost(DOCK_SHELL_MAX_EXTRA_STRETCH)
                                    } else 1f,
                                )
                            },
                            highlight = {
                                pillHighlight?.value?.copy(alpha = indicatorOpticalProgressProvider())
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
                                drawHomeIndicatorSurface(
                                    indicatorOpticalProgressProvider(),
                                    isInDark,
                                    indicatorIdleSurfaceColorOverride,
                                )
                            },
                        )
                        .innerShadow(shape = indicatorShape) {
                            InnerShadow(
                                radius = 8.dp * homeIndicatorOpticalScale(fittedIndicatorHeight.value) * indicatorOpticalProgressProvider(),
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = indicatorOpticalProgressProvider(),
                            )
                        }
                        .height(fittedIndicatorHeight)
                        .then(indicatorWidthModifier)
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
                                indicatorWidthPx = indicatorWidthPxProvider(),
                            )
                            translationX = if (isLtr) {
                                indicatorOffsetPx + panelOffset
                            } else {
                                -indicatorOffsetPx + panelOffset
                            }
                            translationY = dockDragTranslationY()
                            if (allowOverflow) {
                                clip = false
                            }
                        }
                        .clip(indicatorShape)
                        .then(
                            if (indicatorIdleSurfaceColorOverride != null || isLiquidGlassMode) {
                                Modifier.background(
                                    indicatorIdleSurfaceColorOverride ?: colors.contentColor.copy(alpha = 0.1f),
                                    indicatorShape,
                                )
                            } else {
                                Modifier
                            }
                        )
                        .height(fittedIndicatorHeight)
                        .then(indicatorWidthModifier),
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
                                            indicatorWidthPx = indicatorWidthPxProvider(),
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
                        translationY = dockDragTranslationY()
                        if (allowOverflow) {
                            clip = false
                        }
                    }
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

/** Read animated width during measurement so the tab row itself stays stable. */
private fun Modifier.liquidIndicatorWidth(widthPx: () -> Int): Modifier = layout { measurable, constraints ->
    val width = widthPx().coerceIn(constraints.minWidth, constraints.maxWidth)
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}
