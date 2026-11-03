package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.AppContentColors

import android.os.Build
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle

import com.android.purebilibili.core.ui.OpticalContrastPalette

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppNativeTabRow
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.MiuixNonGlassTabItemWidthMode
import com.android.purebilibili.core.ui.components.AppExactTouchTargets
import com.android.purebilibili.core.ui.components.AppNativeSegmentedControl
import com.android.purebilibili.core.ui.components.AppTabRowIndicatorPresentation
import com.android.purebilibili.core.ui.components.rememberMeasuredTabMinWidth
import com.android.purebilibili.core.ui.components.resolveLiquidGlassTabSlotHorizontalPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy
import com.android.purebilibili.core.ui.AppSurfaceTokens
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.android.purebilibili.core.ui.animation.DampedDragAnimationState
import com.android.purebilibili.core.ui.animation.DampedDragTrackingMode
import com.android.purebilibili.feature.home.components.miuix.inspectDragGestures
import kotlinx.coroutines.flow.distinctUntilChanged
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.core.ui.motion.BottomBarMotionProfile
import com.android.purebilibili.core.ui.motion.BottomBarMotionSpec
import com.android.purebilibili.core.ui.motion.resolveBottomBarMotionSpec
import com.android.purebilibili.feature.home.components.liquid.lens as miuixLens
import com.android.purebilibili.feature.home.components.liquid.rememberCombinedBackdrop as rememberMiuixCombinedBackdrop
import com.android.purebilibili.feature.home.components.liquid.vibrancy as miuixVibrancy
import com.android.purebilibili.feature.home.components.miuix.InteractiveHighlight
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.blur as miuixBlur
import top.yukonga.miuix.kmp.blur.drawBackdrop as miuixDrawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

internal fun resolveSegmentedControlLiquidGlassEnabled(
    storedLiquidGlassEnabled: Boolean,
    liquidGlassEffectsEnabled: Boolean,
    supportsIndependentLiquidGlass: Boolean,
    androidNativeLiquidGlassEnabled: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean {
    @Suppress("UNUSED_PARAMETER")
    val ignoredStored = storedLiquidGlassEnabled
    @Suppress("UNUSED_PARAMETER")
    val ignoredIndependent = supportsIndependentLiquidGlass
    if (!liquidGlassEffectsEnabled || !shouldAllowHomeChromeLiquidGlass(sdkInt)) return false
    return androidNativeLiquidGlassEnabled
}

internal enum class SegmentedControlChromeStyle {
    LIQUID_PILL,
    ANDROID_NATIVE_UNDERLINE
}

internal const val BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_HEIGHT_DP = 58
internal val BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_INDICATOR_HEIGHT_DP =
    com.android.purebilibili.core.ui.roundMatchedLiquidIndicatorHeightDp(
        BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_HEIGHT_DP.toFloat()
    )
private const val SEGMENTED_CONTROL_MIN_INDICATOR_ASPECT_RATIO = 1.6f
private const val SEGMENTED_CONTROL_MAX_INDICATOR_ASPECT_RATIO = 3.2f
private const val SEGMENTED_CONTROL_INDICATOR_HORIZONTAL_INSET_DP = 4f

internal fun resolveSegmentedControlChromeStyle(
    uiStyle: AppUiStyle,
    prefersNativeChrome: Boolean,
    androidNativeLiquidGlassEnabled: Boolean,
    preferInlineContentStyle: Boolean = false
): SegmentedControlChromeStyle {
    @Suppress("UNUSED_PARAMETER")
    val ignoredNativeChrome = prefersNativeChrome
    @Suppress("UNUSED_PARAMETER")
    val ignoredInline = preferInlineContentStyle
    return when (
        resolveHomeSelectionIndicatorStyle(
            uiStyle = uiStyle,
            liquidGlassEnabled = androidNativeLiquidGlassEnabled,
        )
    ) {
        HomeSelectionIndicatorStyle.CAPSULE -> SegmentedControlChromeStyle.LIQUID_PILL
        HomeSelectionIndicatorStyle.MD3_UNDERLINE ->
            SegmentedControlChromeStyle.ANDROID_NATIVE_UNDERLINE
    }
}

internal fun resolveLiquidSegmentedControlUnselectedTextColor(
    onSurface: Color,
    enabled: Boolean
): Color = if (enabled) onSurface else onSurface.copy(alpha = 0.42f)

internal fun resolveSegmentedControlIndicatorWidthDp(
    slotWidthDp: Float,
    indicatorHeightDp: Float,
    itemCount: Int
): Float {
    if (slotWidthDp <= 0f || indicatorHeightDp <= 0f || itemCount <= 0) return 0f
    val insetWidth = (slotWidthDp - SEGMENTED_CONTROL_INDICATOR_HORIZONTAL_INSET_DP * 2f)
        .coerceAtLeast(0f)
    // Every selectable row follows the home dock exactly: the moving indicator owns
    // the complete tab slot, so two-item rows do not accumulate an extra inner inset.
    if (itemCount >= 2) return slotWidthDp
    return min(
        insetWidth,
        indicatorHeightDp * SEGMENTED_CONTROL_MAX_INDICATOR_ASPECT_RATIO,
    )
}

internal fun resolveSegmentedControlIndicatorHeightDp(
    slotWidthDp: Float,
    indicatorHeightDp: Float
): Float {
    if (slotWidthDp <= 0f || indicatorHeightDp <= 0f) return 0f
    return min(
        indicatorHeightDp,
        slotWidthDp / SEGMENTED_CONTROL_MIN_INDICATOR_ASPECT_RATIO
    )
}

internal fun resolveSegmentedControlIndicatorOffsetDp(
    position: Float,
    slotWidthDp: Float,
    contentPaddingDp: Float
): Float {
    return contentPaddingDp + (slotWidthDp * position)
}

internal fun shouldFollowSegmentedControlIndicatorDrag(
    pointerX: Float,
    indicatorPosition: Float,
    itemWidthPx: Float
): Boolean {
    if (itemWidthPx <= 0f) return false
    val startX = indicatorPosition * itemWidthPx
    val endX = startX + itemWidthPx
    return pointerX in startX..endX
}

internal fun resolveSegmentedControlSweepSelectionIndex(
    pointerX: Float,
    itemWidthPx: Float,
    itemCount: Int
): Int {
    if (itemWidthPx <= 0f || itemCount <= 0) return 0
    return (pointerX.coerceAtLeast(0f) / itemWidthPx)
        .toInt()
        .coerceIn(0, itemCount - 1)
}

internal fun resolveSegmentedControlIndicatorPosition(
    internalPosition: Float,
    externalPosition: Float?,
    itemCount: Int
): Float {
    if (itemCount <= 0) return 0f
    return (externalPosition ?: internalPosition)
        .coerceIn(0f, (itemCount - 1).toFloat())
}

internal data class NativeUnderlineGeometry(
    val offsetDp: Float,
    val widthDp: Float,
)

internal fun resolveNativeUnderlineGeometry(
    indicatorPosition: Float,
    segmentWidthDp: Float,
    labelWidthsDp: List<Float>,
    minimumWidthDp: Float = 24f,
    fallbackWidthFraction: Float = 0.42f,
): NativeUnderlineGeometry {
    if (segmentWidthDp <= 0f || labelWidthsDp.isEmpty()) {
        return NativeUnderlineGeometry(offsetDp = 0f, widthDp = 0f)
    }

    val lastIndex = labelWidthsDp.lastIndex
    val safePosition = indicatorPosition.coerceIn(0f, lastIndex.toFloat())
    val startIndex = floor(safePosition).toInt()
    val endIndex = (startIndex + 1).coerceAtMost(lastIndex)
    val fraction = safePosition - startIndex
    val fallbackWidth = segmentWidthDp * fallbackWidthFraction
    val effectiveMinimumWidth = minimumWidthDp.coerceAtMost(segmentWidthDp)

    fun resolvedWidth(index: Int): Float = labelWidthsDp[index]
        .takeIf { it > 0f }
        ?.coerceIn(effectiveMinimumWidth, segmentWidthDp)
        ?: fallbackWidth.coerceIn(effectiveMinimumWidth, segmentWidthDp)

    val startWidth = resolvedWidth(startIndex)
    val endWidth = resolvedWidth(endIndex)
    // Flutter TabIndicatorAnimation.elastic: target-side edge decelerates with sine while
    // the trailing edge accelerates with cosine, producing PiliPlus' fast-then-slow pull.
    val trailingEdgeProgress = 1f - kotlin.math.cos(fraction * Math.PI.toFloat() / 2f)
    val leadingEdgeProgress = kotlin.math.sin(fraction * Math.PI.toFloat() / 2f)
    val startLeft = segmentWidthDp * (startIndex + 0.5f) - startWidth / 2f
    val startRight = startLeft + startWidth
    val endLeft = segmentWidthDp * (endIndex + 0.5f) - endWidth / 2f
    val endRight = endLeft + endWidth
    val left = startLeft + (endLeft - startLeft) * trailingEdgeProgress
    val right = startRight + (endRight - startRight) * leadingEdgeProgress
    return NativeUnderlineGeometry(
        offsetDp = left,
        widthDp = (right - left).coerceAtLeast(0f),
    )
}

internal fun shouldDrawSegmentedControlIndicatorBackdrop(
    liquidGlassEnabled: Boolean,
    motionProgress: Float,
    hasExternalBackdrop: Boolean
): Boolean {
    if (!liquidGlassEnabled) return false
    return hasExternalBackdrop || motionProgress > 0.001f
}

/**
 * Export capture may drawBackdrop only from an external page LayerBackdrop.
 * Sampling the same tabs LayerBackdrop being recorded on that node creates a
 * cyclic RenderNode graph and overflows HyperOS MiBackgroundBlurBlend.
 */
internal fun shouldDrawSegmentedControlExportCaptureBackdrop(
    liquidGlassEnabled: Boolean,
    hasExternalBackdrop: Boolean
): Boolean {
    return liquidGlassEnabled && hasExternalBackdrop
}

internal fun resolveSegmentedControlMotionProgress(
    pressProgress: Float,
    refractionProgress: Float,
    tapPressRefractionEnabled: Boolean
): Float {
    val resolvedPressProgress = if (tapPressRefractionEnabled) pressProgress else 0f
    return maxOf(resolvedPressProgress, refractionProgress)
}

internal fun resolveSegmentedControlExternalPagerVelocityItemsPerSecond(
    currentPosition: Float,
    previousPosition: Float,
    elapsedNanos: Long,
): Float {
    if (elapsedNanos <= 0L) return 0f
    val elapsedSeconds = elapsedNanos / 1_000_000_000f
    if (elapsedSeconds <= 0f) return 0f
    return ((currentPosition - previousPosition) / elapsedSeconds)
        .coerceIn(-12f, 12f)
}

internal fun shouldStretchSegmentedControlExternalPagerIndicator(
    position: Float,
    externalPagerMotionActive: Boolean,
    positionEpsilon: Float = 0.015f,
): Boolean {
    if (!externalPagerMotionActive) return false
    return abs(position - position.roundToInt().toFloat()) > positionEpsilon
}

/**
 * Shared liquid segmented/top-tab indicator motion must match the home floating bottom bar.
 * Do not soften springs/offsets here — any divergence makes swipe stretch/settle feel wrong.
 */
internal fun resolveSegmentedControlMotionSpec(): BottomBarMotionSpec {
    return resolveBottomBarMotionSpec(profile = BottomBarMotionProfile.ANDROID_NATIVE_FLOATING)
}

/**
 * Same panel-offset formula as [BiliPaiFloatingBottomBar]: fraction of full dock width,
 * capped at AppSpacingTokens.ExtraSmall, EaseOut mapped.
 */
internal fun resolveSharedLiquidIndicatorPanelOffsetPx(
    dragOffsetPx: Float,
    dockWidthPx: Float,
    maxOffsetPx: Float
): Float {
    if (dockWidthPx <= 0f) return 0f
    val fraction = (dragOffsetPx / dockWidthPx).coerceIn(-1f, 1f)
    return maxOffsetPx * fraction.sign * EaseOut.transform(abs(fraction))
}

/**
 * Lens/refraction progress for shared liquid indicators.
 * Bottom bar keeps a drag floor so slow swipes still show glass stretch instead of fading out.
 */
internal fun resolveSharedLiquidIndicatorLensProgress(
    pressProgress: Float,
    motionProgress: Float,
    isDragging: Boolean
): Float {
    val dragFloor = if (isDragging) 0.6f else 0f
    return maxOf(pressProgress, motionProgress, dragFloor).coerceIn(0f, 1f)
}

/**
 * When glass is active and the capsule is moving, visible labels stay neutral and the
 * selected color is carried by the export layer + tint (same as home bottom bar).
 */
internal fun resolveSharedLiquidIndicatorUseGlassColorPath(
    liquidGlassEnabled: Boolean,
    lensProgress: Float
): Boolean = liquidGlassEnabled && lensProgress > 0.001f

/** Capture lens strength: full 24dp while interacting, like BiliPai bottom bar capture. */
internal fun resolveSharedLiquidIndicatorCaptureLensProgress(
    lensProgress: Float,
    isDragging: Boolean
): Float {
    if (isDragging) return 1f
    return lensProgress.coerceIn(0f, 1f)
}

/**
 * Export-layer glyph color before [ColorFilter.tint].
 * Must stay near-white so SrcIn tint resolves to pure theme/primary color.
 */
internal fun resolveSharedLiquidExportMonochromeColor(
    darkTheme: Boolean
): Color = if (darkTheme) {
    OpticalContrastPalette.Highlight.copy(alpha = 0.96f)
} else {
    OpticalContrastPalette.Highlight
}

@Composable
fun BottomBarLiquidSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    enabled: Boolean = true,
    itemWidth: Dp? = null,
    height: Dp = BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_HEIGHT_DP.dp,
    indicatorHeight: Dp = BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_INDICATOR_HEIGHT_DP.dp,
    labelFontSize: TextUnit = TextUnit.Unspecified,
    allowNativeLabelOverflow: Boolean = false,
    containerHorizontalPadding: Dp = AppSpacingTokens.ExtraSmall,
    containerVerticalPadding: Dp = AppSpacingTokens.ExtraSmall,
    liquidGlassEffectsEnabled: Boolean = true,
    // False reserves horizontal gestures for the scrollable rail; taps still select a tab.
    dragSelectionEnabled: Boolean = true,
    // Compatibility alias for drag enablement; indicators now always start dragging directly.
    longPressDragSelectionEnabled: Boolean = false,
    preferInlineContentStyle: Boolean = false,
    forceEqualWidth: Boolean = false,
    compactMiuixWhenTwoOptions: Boolean = true,
    nativeSegmentedControl: Boolean = false,
    nativeIndicatorPresentation: AppTabRowIndicatorPresentation = AppTabRowIndicatorPresentation.UNDERLINE,
    equalizeMiuixNonGlassItemWidths: Boolean = false,
    // Keep non-glass Miuix tabs as two independent items instead of adding a
    // redundant middle dock behind them.
    drawMiuixNonGlassTrack: Boolean = false,
    miuixBackdrop: MiuixBackdrop? = null,
    tapPressRefractionEnabled: Boolean = true,
    containerColorOverride: Color? = null,
    selectedTextColorOverride: Color? = null,
    unselectedTextColorOverride: Color? = null,
    indicatorIdleSurfaceColorOverride: Color? = null,
    indicatorPositionProvider: (() -> Float)? = null,
    onIndicatorPositionChanged: ((Float) -> Unit)? = null,
    onIndicatorDragStateChanged: ((Boolean) -> Unit)? = null,
    isScrollInProgressProvider: () -> Boolean = { false },
    externalPagerMotionEffectsEnabled: Boolean = false,
    // Glass pager release and landing signals; native branches keep their existing motion.
    isPagerDraggingProvider: (() -> Boolean)? = null,
    pagerTargetPositionProvider: (() -> Float)? = null,
    liquidGlassTuningOverride: LiquidGlassTuning? = null,
    geometryMode: FloatingBottomBarGeometryMode = FloatingBottomBarGeometryMode.Segmented,
    centerGlassContent: Boolean = false,
    // Applied below the native renderer returns; native indicator geometry stays unchanged.
    liquidGlassAdaptIndicatorToLabel: Boolean = true,
    liquidGlassAlignShellStart: Boolean = false,
    liquidGlassLabelHorizontalPadding: Dp? = null,
    // Geometry-only labels for asynchronous badges. Native renderers and visible text ignore them.
    liquidGlassMeasurementLabels: List<String>? = null,
    liquidGlassAnimateLabelWidthChanges: Boolean = false,
    // Explicit native placement opt-in, independent from glass-only geometry and shared defaults.
    centerNativeContent: Boolean = false,
    // Glass-only indicator measurement can follow visible labels while slots reserve badge limits.
    liquidGlassIndicatorMeasurementLabels: List<String>? = null,
): Unit = AppExactTouchTargets {
    if (items.isEmpty()) return@AppExactTouchTargets
    val effectiveLabelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(labelFontSize)
    if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) {
        val nativeOptions = remember(items) {
            items.mapIndexed { index, label -> AppSegmentOption(index, label) }
        }
        // Fixed liquid slots and capture heights are visual hints, not native
        // content constraints. Let the opaque renderer fit labels and font scale.
        AppNativeTabRow(
            options = nativeOptions,
            selectedValue = selectedIndex.coerceIn(0, items.lastIndex),
            onSelectionChange = onSelected,
            modifier = modifier,
            enabled = enabled,
            centerContent = centerNativeContent,
            scrollable = !nativeSegmentedControl && !forceEqualWidth &&
                (scrollState != null || items.size > 2),
            forceEqualWidth = forceEqualWidth,
            minTabWidth = AppChromeSizeTokens.MinimumTouchTarget,
            labelFontSize = effectiveLabelFontSize,
            containerHorizontalPadding = containerHorizontalPadding,
            compactMiuixWhenTwoOptions = compactMiuixWhenTwoOptions,
            indicatorPositionProvider = indicatorPositionProvider,
            miuixNonGlassItemWidthMode = if (equalizeMiuixNonGlassItemWidths) {
                MiuixNonGlassTabItemWidthMode.EQUAL_TO_LONGEST_LABEL
            } else MiuixNonGlassTabItemWidthMode.CONTENT,
            drawMiuixNonGlassTrack = drawMiuixNonGlassTrack,
        )
        return@AppExactTouchTargets
    }
    val shellHeight = height.coerceAtLeast(0.dp)
    val horizontalPadding = containerHorizontalPadding.coerceAtLeast(0.dp)
    val fixedItemWidth = itemWidth?.coerceAtLeast(0.dp)
    val shellModifier = if (fixedItemWidth != null) {
        modifier.width(fixedItemWidth * items.size + horizontalPadding * 2)
    } else {
        modifier.fillMaxWidth()
    }
    // Geometry is independent from material sampling. Sampling and press
    // bloom are draw-only children and cannot change this layout or hit viewport.
    BoxWithConstraints(modifier = shellModifier.height(shellHeight)) {
        val renderedItemWidth = fixedItemWidth ?: if (!constraints.hasBoundedWidth) {
            rememberMeasuredTabMinWidth(
                requestedMinWidth = AppChromeSizeTokens.CompactTabMinWidth,
                labels = items,
                textStyle = MaterialTheme.typography.labelLarge.copy(
                    fontSize = effectiveLabelFontSize,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        } else {
            null
        }
        val visualPolicy = rememberAppSemanticVisualPolicy()
        // Material selection shares the same resolved theme as its parent and glass child.
        val androidNativeLiquidGlassEnabled =
            com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
        if (!androidNativeLiquidGlassEnabled) {
            val nativeOptions = remember(items) {
                items.mapIndexed { index, label -> AppSegmentOption(index, label) }
            }
            val nativeItemWidth = renderedItemWidth ?:
                (maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp) / items.size
            val nativeModifier = (if (renderedItemWidth == null) Modifier.fillMaxWidth() else Modifier)
                .then(if (centerNativeContent) Modifier.align(Alignment.Center) else Modifier)
            if (nativeSegmentedControl) {
                AppNativeSegmentedControl(
                    options = nativeOptions,
                    selectedValue = selectedIndex.coerceIn(0, items.lastIndex),
                    onSelectionChange = onSelected,
                    modifier = nativeModifier,
                    enabled = enabled,
                    height = shellHeight,
                    itemWidth = nativeItemWidth,
                    labelFontSize = effectiveLabelFontSize,
                    containerHorizontalPadding = horizontalPadding,
                    indicatorPositionProvider = indicatorPositionProvider,
                )
            } else {
                AppNativeTabRow(
                    options = nativeOptions,
                    selectedValue = selectedIndex.coerceIn(0, items.lastIndex),
                    onSelectionChange = onSelected,
                    modifier = nativeModifier,
                    enabled = enabled,
                    centerContent = centerNativeContent,
                    scrollable = scrollState != null,
                    forceEqualWidth = forceEqualWidth,
                    fixedItemWidth = nativeItemWidth,
                    height = shellHeight,
                    labelFontSize = effectiveLabelFontSize,
                    containerHorizontalPadding = horizontalPadding,
                    compactMiuixWhenTwoOptions = compactMiuixWhenTwoOptions,
                    allowLabelOverflow = allowNativeLabelOverflow,
                    indicatorPresentation = nativeIndicatorPresentation,
                    indicatorPositionProvider = indicatorPositionProvider,
                    miuixNonGlassItemWidthMode = if (equalizeMiuixNonGlassItemWidths) {
                        MiuixNonGlassTabItemWidthMode.EQUAL_TO_LONGEST_LABEL
                    } else {
                        MiuixNonGlassTabItemWidthMode.CONTENT
                    },
                    drawMiuixNonGlassTrack = drawMiuixNonGlassTrack,
                )
            }
            return@BoxWithConstraints
        }
        val chromeStyle = resolveSegmentedControlChromeStyle(
            uiStyle = LocalAppUiStyle.current,
            prefersNativeChrome = visualPolicy.prefersNativeChrome,
            androidNativeLiquidGlassEnabled = androidNativeLiquidGlassEnabled,
            preferInlineContentStyle = preferInlineContentStyle,
        )
        if (chromeStyle == SegmentedControlChromeStyle.ANDROID_NATIVE_UNDERLINE) {
            AndroidNativeUnderlinedSegmentedControl(
                items = items,
                selectedIndex = selectedIndex,
                onSelected = onSelected,
                modifier = Modifier,
                enabled = enabled,
                itemWidth = fixedItemWidth,
                height = shellHeight,
                labelFontSize = effectiveLabelFontSize,
                allowLabelOverflow = allowNativeLabelOverflow,
                selectedTextColorOverride = selectedTextColorOverride,
                unselectedTextColorOverride = unselectedTextColorOverride,
                indicatorPositionProvider = indicatorPositionProvider,
                onIndicatorPositionChanged = onIndicatorPositionChanged,
            )
            return@BoxWithConstraints
        }
        // Material boundary: only tune glass sizing below the native-renderer returns above.
        // Direct consumers (search, listen-video, settings and page filters) bypass
        // AppLiquidAwareTabRow. Give them the same measured compact slots here.
        val glassMeasurementLabels = liquidGlassMeasurementLabels
            ?.takeIf { it.size == items.size } ?: items
        val measuredLiquidItemWidth = renderedItemWidth ?: if (
            !forceEqualWidth && geometryMode == FloatingBottomBarGeometryMode.Segmented
        ) {
            rememberMeasuredTabMinWidth(
                requestedMinWidth = AppChromeSizeTokens.MinimumTouchTarget,
                labels = glassMeasurementLabels,
                textStyle = MaterialTheme.typography.labelLarge.copy(
                    fontSize = effectiveLabelFontSize,
                    fontWeight = FontWeight.SemiBold,
                ),
                horizontalPadding = liquidGlassLabelHorizontalPadding ?: resolveLiquidGlassTabSlotHorizontalPadding(items.size),
            )
        } else null
        val animateLabelWidths = liquidGlassAnimateLabelWidthChanges &&
            com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
            !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
        // Native paths have already returned. Retarget only data-driven glass geometry.
        val liquidItemWidth = if (animateLabelWidths && renderedItemWidth == null && measuredLiquidItemWidth != null) {
            animateDpAsState(
                targetValue = measuredLiquidItemWidth,
                animationSpec = tween(180, easing = AppMotionEasing.M1Standard),
                label = "glassAsyncBadgeSlotWidth",
            ).value
        } else measuredLiquidItemWidth
        BottomBarFloatingSegmentedControl(
            adaptIndicatorToLabel = liquidGlassAdaptIndicatorToLabel,
            liquidGlassAlignShellStart = liquidGlassAlignShellStart,
            items = items,
            liquidGlassMeasurementLabels = liquidGlassIndicatorMeasurementLabels
                ?.takeIf { it.size == items.size } ?: glassMeasurementLabels,
            liquidGlassAnimateLabelWidthChanges = liquidGlassAnimateLabelWidthChanges,
            liquidGlassLabelHorizontalPadding = liquidGlassLabelHorizontalPadding,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            // Only the glass child is centered; native branches keep their established placement.
            modifier = if (centerGlassContent) Modifier.align(Alignment.Center) else Modifier,
            enabled = enabled,
            itemWidth = liquidItemWidth,
            height = shellHeight,
            indicatorHeight = indicatorHeight,
            labelFontSize = effectiveLabelFontSize,
            allowLabelOverflow = allowNativeLabelOverflow,
            containerHorizontalPadding = horizontalPadding,
            containerVerticalPadding = containerVerticalPadding,
            liquidGlassEffectsEnabled = liquidGlassEffectsEnabled,
            dragSelectionEnabled = dragSelectionEnabled,
            longPressDragSelectionEnabled = longPressDragSelectionEnabled,
            tapPressRefractionEnabled = tapPressRefractionEnabled,
            indicatorIdleSurfaceColorOverride = indicatorIdleSurfaceColorOverride,
            miuixBackdrop = miuixBackdrop,
            containerColorOverride = containerColorOverride,
            selectedTextColorOverride = selectedTextColorOverride,
            unselectedTextColorOverride = unselectedTextColorOverride,
            indicatorPositionProvider = indicatorPositionProvider,
            onIndicatorPositionChanged = onIndicatorPositionChanged,
            isScrollInProgressProvider = isScrollInProgressProvider,
            onIndicatorDragStateChanged = onIndicatorDragStateChanged,
            externalPagerMotionEffectsEnabled = externalPagerMotionEffectsEnabled,
            isPagerDraggingProvider = isPagerDraggingProvider,
            pagerTargetPositionProvider = pagerTargetPositionProvider,
            liquidGlassTuningOverride = liquidGlassTuningOverride,
            geometryMode = geometryMode,
            scrollState = scrollState,
        )
    }
}

@Composable
internal fun AndroidNativeUnderlinedSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemWidth: Dp? = null,
    height: Dp,
    labelFontSize: TextUnit,
    allowLabelOverflow: Boolean = false,
    selectedTextColorOverride: Color? = null,
    unselectedTextColorOverride: Color? = null,
    indicatorPositionProvider: (() -> Float)? = null,
    onIndicatorPositionChanged: ((Float) -> Unit)? = null
) {
    val itemCount = items.size
    val safeSelectedIndex = selectedIndex.coerceIn(0, itemCount - 1)
    val selectedTextColor = selectedTextColorOverride ?: AppContentColors.accent()
    val unselectedTextColor = unselectedTextColorOverride
        ?: resolveLiquidSegmentedControlUnselectedTextColor(AppContentColors.primaryText(), enabled)
    val underlineShape = CircleShape
    val density = LocalDensity.current
    val measuredLabelWidths: SnapshotStateList<Float> = remember(items) {
        List(itemCount) { 0f }.toMutableStateList()
    }
    val animatedSelectedIndex by animateFloatAsState(
        targetValue = safeSelectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 250, easing = EaseOut),
        label = "nativeUnderlinePosition",
    )
    val indicatorPosition = resolveSegmentedControlIndicatorPosition(
        internalPosition = animatedSelectedIndex,
        externalPosition = indicatorPositionProvider?.invoke(),
        itemCount = itemCount
    )

    SideEffect {
        onIndicatorPositionChanged?.invoke(indicatorPosition)
    }

    BoxWithConstraints(
        modifier = modifier
            .then(
                if (itemWidth != null) {
                    Modifier.width(itemWidth * itemCount)
                } else {
                    Modifier.fillMaxWidth()
                }
            )
            .height(height)
    ) {
        val segmentWidth = maxWidth / itemCount
        val underlineGeometry = resolveNativeUnderlineGeometry(
            indicatorPosition = indicatorPosition,
            segmentWidthDp = segmentWidth.value,
            labelWidthsDp = measuredLabelWidths,
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == safeSelectedIndex
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clickable(enabled = enabled) { onSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    AppText(
                        text = label,
                        modifier = Modifier.then(
                            if (allowLabelOverflow) {
                                Modifier.wrapContentWidth(unbounded = true)
                            } else {
                                Modifier
                            }
                        ),
                        tapToCopyEnabled = false,
                        color = if (selected) selectedTextColor else unselectedTextColor,
                        fontSize = labelFontSize,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            val measuredWidthDp = with(density) { result.size.width.toDp().value }
                            if (measuredLabelWidths[index] != measuredWidthDp) {
                                measuredLabelWidths[index] = measuredWidthDp
                            }
                        },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = underlineGeometry.offsetDp.dp)
                .width(underlineGeometry.widthDp.dp)
                .height(AppSpacingTokens.ExtraSmall - AppSpacingTokens.Micro / 2)
                .clip(underlineShape)
                .background(selectedTextColor)
        )
    }
}

@Composable
internal fun BottomBarLiquidSegmentedLabels(
    items: List<String>,
    selectedIndex: Int,
    indicatorPosition: Float,
    motionProgress: Float,
    selectionEmphasis: Float,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    enabled: Boolean,
    labelFontSize: TextUnit,
    indicatorCorner: Dp,
    onSelected: (Int) -> Unit,
    interactive: Boolean,
    onPressChanged: ((Boolean) -> Unit)? = null,
    applyItemScale: Boolean = true,
    forceUnselectedColor: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, label ->
            val interactionSource = remember { MutableInteractionSource() }
            if (interactive && onPressChanged != null) {
                val pressed by interactionSource.collectIsPressedAsState()
                LaunchedEffect(pressed) {
                    onPressChanged(pressed)
                }
            }
            val visual = resolveBottomBarItemMotionVisual(
                itemIndex = index,
                indicatorPosition = indicatorPosition,
                currentSelectedIndex = selectedIndex,
                motionProgress = motionProgress,
                selectionEmphasis = selectionEmphasis
            )
            val contentColors = resolveLiquidGlassSelectionContentColors(
                unselectedColor = unselectedTextColor,
                selectedColor = selectedTextColor,
                themeWeight = visual.themeWeight,
                glassEnabled = forceUnselectedColor,
                indicatorProgress = motionProgress,
                indicatorBackdropEnabled = true
            )
            val textColor = if (!enabled) {
                unselectedTextColor.copy(alpha = 0.44f)
            } else {
                contentColors.visibleColor
            }
            val labelScale = if (applyItemScale) visual.scale else 1f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(indicatorCorner))
                    .then(
                        if (interactive) {
                            Modifier.clickable(
                                enabled = enabled,
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onSelected(index)
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    text = label,
                    tapToCopyEnabled = false,
                    color = textColor,
                    fontSize = labelFontSize,
                    fontWeight = if (visual.themeWeight > 0.5f && !forceUnselectedColor) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        scaleX = labelScale
                        scaleY = labelScale
                    }
                )
            }
        }
    }
}
