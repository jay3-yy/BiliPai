// 文件路径: feature/home/components/LiquidIndicator.kt
package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.OpticalContrastPalette



import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.android.purebilibili.feature.home.components.liquid.lens
import com.android.purebilibili.core.store.LiquidGlassMode
import com.android.purebilibili.core.store.LiquidGlassStyle
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.motion.BottomBarMotionSpec
import com.android.purebilibili.core.ui.motion.resolveBottomBarMotionSpec
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

/**
 * 🌊 液态玻璃选中指示器
 *
 * 实现类似 visionOS 的玻璃折射效果：
 * - 透镜折射效果 (Android 13+ 支持)
 * - 拖拽时放大形变
 * - 高光和内阴影
 *
 * @param position 当前位置（浮点索引）
 * @param itemWidth 单个项目宽度
 * @param itemCount 项目数量
 * @param isDragging 是否正在拖拽
 * @param velocity 当前速度（用于形变）
 * @param hazeState HazeState 实例（用于模糊效果）
 * @param modifier Modifier
 */
@Composable
internal fun LiquidIndicator(
    position: Float,
    itemWidth: Dp,
    itemCount: Int,
    isDragging: Boolean,
    velocity: Float = 0f,
    startPadding: Dp = AppSpacingTokens.None,
    modifier: Modifier = Modifier,
    color: Color = AppContentColors.accent().copy(alpha = 0.18f),
    isLiquidGlassEnabled: Boolean = false,
    clampToBounds: Boolean = false,
    edgeInset: Dp = AppSpacingTokens.None,
    viewportShiftPx: Float = 0f,
    indicatorWidthMultiplier: Float = 1.42f,
    indicatorMinWidth: Dp = AppSpacingTokens.TripleExtraLarge * 2 + AppSpacingTokens.Small,
    indicatorMaxWidth: Dp = AppSpacingTokens.TripleExtraLarge * 3 - AppSpacingTokens.Small,
    maxWidthToItemRatio: Float = Float.POSITIVE_INFINITY,
    indicatorHeight: Dp = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro,
    lensIntensityBoost: Float = 1f,
    edgeWarpBoost: Float = 1f,
    chromaticBoost: Float = 1f,
    lensAmountScale: Float = 1f,
    lensHeightScale: Float = 1f,
    forceChromaticAberration: Boolean = false,
    liquidGlassStyle: LiquidGlassStyle = LiquidGlassStyle.CLASSIC, // [New]
    liquidGlassTuning: LiquidGlassTuning? = null,
    motionSpec: BottomBarMotionSpec = resolveBottomBarMotionSpec(),
    backdrop: Backdrop? = null // [New] Backdrop for refraction
) {
    val density = LocalDensity.current
    val resolvedTuning = remember(liquidGlassStyle, liquidGlassTuning) {
        liquidGlassTuning ?: resolveLiquidGlassTuning(liquidGlassStyle)
    }
    val styleTuning = remember(resolvedTuning) { resolveLiquidStyleTuning(resolvedTuning) }
    val lensProfile = remember(
        isDragging,
        velocity,
        lensIntensityBoost,
        edgeWarpBoost,
        chromaticBoost,
        resolvedTuning,
        motionSpec
    ) {
        resolveLiquidLensProfile(
            isDragging = isDragging,
            velocityPxPerSecond = velocity,
            idleThresholdPxPerSecond = styleTuning.idleThresholdPxPerSecond,
            dragMotionFloor = styleTuning.dragMotionFloor,
            lensIntensityBoost = lensIntensityBoost * styleTuning.lensIntensityMultiplier,
            edgeWarpBoost = edgeWarpBoost * styleTuning.edgeWarpMultiplier,
            chromaticBoost = chromaticBoost * styleTuning.chromaticMultiplier,
            velocityRangePxPerSecond = motionSpec.indicator.lensVelocityRangePxPerSecond
        )
    }

    val itemWidthPx = with(density) { itemWidth.toPx() }
    val indicatorWidthPx = resolveLiquidIndicatorWidthPx(
        itemWidthPx = itemWidthPx,
        widthMultiplier = indicatorWidthMultiplier,
        minWidthPx = with(density) { indicatorMinWidth.toPx() },
        maxWidthPx = with(density) { indicatorMaxWidth.toPx() },
        maxWidthToItemRatio = maxWidthToItemRatio
    )
    val indicatorWidth = with(density) { indicatorWidthPx.toDp() }

    // [优化] 使用 graphicsLayer 进行位移，避免 Layout 重排
    // 计算位置 (Px)
    val startPaddingPx = with(density) { startPadding.toPx() }
    val edgeInsetPx = with(density) { edgeInset.toPx() }
    // 居中偏移：(Item宽度 - 指示器宽度) / 2
    val centerOffsetPx = (itemWidthPx - indicatorWidthPx) / 2f

    // 速度形变
    val deformation = lensProfile.motionFraction *
        (motionSpec.indicator.deformationScaleXDelta * styleTuning.deformationMultiplier)

    val targetScaleX = 1f + deformation
    val targetScaleY = 1f - (deformation * motionSpec.indicator.deformationScaleYCompressionRatio)

    // 放大弹簧照搬 HyperIsland LiquidGlassNavigationBar：X 用 scaleSpring(0.6/250)、
    // Y 用 scaleYSpring(0.7/250)，不再叠加 BiliPai 早先恒为 1f 的 dragScale 层。
    val scaleXState = animateFloatAsState(
        targetValue = targetScaleX,
        animationSpec = motionSpec.indicator.scaleSpring.toSpringSpec(),
        label = "scaleX"
    )
    val scaleYState = animateFloatAsState(
        targetValue = targetScaleY,
        animationSpec = motionSpec.indicator.scaleYSpring.toSpringSpec(),
        label = "scaleY"
    )

    // 指示器形状
    val shape = remember(indicatorHeight) { RoundedCornerShape(indicatorHeight / 2) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerWidthPx = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
         Box(
            modifier = Modifier
                .graphicsLayer {
                    // [核心优化] 在绘制阶段计算位移
                    translationX = resolveIndicatorTranslationXPx(
                        position = position,
                        itemWidthPx = itemWidthPx,
                        indicatorWidthPx = indicatorWidthPx,
                        startPaddingPx = startPaddingPx,
                        containerWidthPx = containerWidthPx,
                        clampToBounds = clampToBounds,
                        edgeInsetPx = edgeInsetPx,
                        viewportShiftPx = viewportShiftPx
                    )

                    this.scaleX = scaleXState.value
                    this.scaleY = scaleYState.value
                    shadowElevation = 0f
                }
                .size(indicatorWidth, indicatorHeight)
                .clip(shape)
                .run {
                    if (isLiquidGlassEnabled && backdrop != null) {
                        this.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                val blurRadius = styleTuning.idleBlurRadius *
                                    (0.06f + resolvedTuning.progress * 0.94f)
                                blur(blurRadius, blurRadius)
                                if (lensProfile.shouldRefract && resolvedTuning.refractionAmount > 0.5f) {
                                    lens(
                                        refractionHeight = lensProfile.refractionHeight *
                                            lensHeightScale.coerceIn(0.1f, 1f) *
                                            blendFloat(1f, 0.35f, resolvedTuning.progress) *
                                            resolvedTuning.contentDistortionScale,
                                        refractionAmount = lensProfile.refractionAmount *
                                            lensAmountScale.coerceIn(0.1f, 1f) *
                                            blendFloat(1f, 0.18f, resolvedTuning.progress) *
                                            resolvedTuning.contentDistortionScale,
                                        depthEffect = styleTuning.depthEffectEnabled,
                                        chromaticAberration = if (
                                            forceChromaticAberration ||
                                            (styleTuning.allowChromaticAberration &&
                                                lensProfile.aberrationStrength > 0.01f)
                                        ) {
                                            resolveLiquidGlassIndicatorChromaticAberration(
                                                resolvedTuning
                                            )
                                        } else {
                                            0f
                                        }
                                    )
                                }
                            },
                            onDrawSurface = {
                                drawLiquidSphereSurface(
                                    baseColor = color,
                                    lensProfile = lensProfile,
                                    tuning = resolvedTuning
                                )
                            }
                        )
                    } else {
                        // Fallback
                         this.background(color)
                    }
                }
        )
    }
}


/**
 * 简化版液态指示器（不依赖 Backdrop）
 *
 * 使用标准 Compose 动画实现类似效果
 */
/**
 * 简化版液态指示器（适用于 TabRow 等变长场景）
 *
 * 使用标准 Compose 动画实现类似效果
 */
@Composable
fun SimpleLiquidIndicator(
    position: Float, // [修复] 直接接受 Float 而非 State，简化 API
    itemWidthPx: Float, // [修复] 使用像素值计算
    isDragging: Boolean,
    velocityPxPerSecond: Float = 0f,
    isLiquidGlassEnabled: Boolean = false,
    liquidGlassStyle: LiquidGlassStyle = LiquidGlassStyle.CLASSIC,
    liquidGlassTuning: LiquidGlassTuning? = null,
    backdrop: Backdrop? = null,
    indicatorColor: Color = AppContentColors.accent().copy(alpha = 0.12f),
    indicatorHeight: Dp = AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Micro,
    cornerRadius: Dp = AppSpacingTokens.Large,
    widthRatio: Float = 0.78f,
    minWidth: Dp = AppSpacingTokens.TripleExtraLarge,
    horizontalInset: Dp = AppSpacingTokens.Large,
    /** Home chrome keeps a thin optical highlight; soft tonal tabs can turn it off. */
    drawHighlightBorder: Boolean = true,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val resolvedTuning = remember(liquidGlassStyle, liquidGlassTuning) {
        liquidGlassTuning ?: resolveLiquidGlassTuning(liquidGlassStyle)
    }
    val styleTuning = remember(resolvedTuning) { resolveLiquidStyleTuning(resolvedTuning) }
    val lensProfile = remember(isDragging, velocityPxPerSecond, resolvedTuning) {
        resolveLiquidLensProfile(
            isDragging = isDragging,
            velocityPxPerSecond = velocityPxPerSecond,
            idleThresholdPxPerSecond = styleTuning.idleThresholdPxPerSecond,
            dragMotionFloor = styleTuning.dragMotionFloor,
            lensIntensityBoost = styleTuning.lensIntensityMultiplier,
            edgeWarpBoost = styleTuning.edgeWarpMultiplier,
            chromaticBoost = styleTuning.chromaticMultiplier
        )
    }
    val minWidthPx = with(density) { minWidth.toPx() }
    val horizontalInsetPx = with(density) { horizontalInset.toPx() }
    val indicatorWidthPx = resolveTopTabIndicatorWidthPx(
        itemWidthPx = itemWidthPx,
        widthRatio = widthRatio,
        minWidthPx = minWidthPx,
        horizontalInsetPx = horizontalInsetPx
    )
    val indicatorWidth = with(density) { indicatorWidthPx.toDp() }
    val indicatorHeightPx = with(density) { indicatorHeight.toPx() }

    // [修复] 居中偏移：将指示器居中放置在每个 Tab 单元格内
    val centerOffsetPx = (itemWidthPx - indicatorWidthPx) / 2f

    val scale by animateFloatAsState(
        targetValue = 1f + lensProfile.motionFraction * (0.12f * styleTuning.deformationMultiplier),
        animationSpec = AppMotionTokens.expressiveSpec(),
        label = "scale"
    )
    val indicatorAlphaScale by animateFloatAsState(
        targetValue = if (isLiquidGlassEnabled) 0.92f else 1f,
        animationSpec = AppMotionTokens.standardSpec(),
        label = "indicatorAlphaScale"
    )
    val resolvedIndicatorColor = indicatorColor.copy(
        alpha = (indicatorColor.alpha * indicatorAlphaScale).coerceIn(0f, 1f)
    )

    // 居中放置指示器，避免使用 BoxWithConstraints 产生子组合 (SubcomposeLayout) 开销
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = position * itemWidthPx + centerOffsetPx

                    this.scaleX = scale
                    this.scaleY = 1f - lensProfile.motionFraction * (0.08f * styleTuning.deformationMultiplier)
                }
                .size(indicatorWidth, indicatorHeight)
                .clip(RoundedCornerShape(cornerRadius))
                .run {
                    if (isLiquidGlassEnabled && backdrop != null) {
                        this.drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(cornerRadius) },
                            effects = {
                                val blurRadius = styleTuning.idleBlurRadius *
                                    (0.06f + resolvedTuning.progress * 0.94f)
                                blur(blurRadius, blurRadius)
                                if (lensProfile.shouldRefract && resolvedTuning.refractionAmount > 0.5f) {
                                    lens(
                                        refractionHeight = lensProfile.refractionHeight *
                                            blendFloat(1f, 0.35f, resolvedTuning.progress) *
                                            resolvedTuning.contentDistortionScale,
                                        refractionAmount = lensProfile.refractionAmount *
                                            blendFloat(1f, 0.18f, resolvedTuning.progress) *
                                            resolvedTuning.contentDistortionScale,
                                        depthEffect = styleTuning.depthEffectEnabled,
                                        chromaticAberration = if (
                                            styleTuning.allowChromaticAberration &&
                                            lensProfile.aberrationStrength > 0.01f
                                        ) {
                                            resolveLiquidGlassIndicatorChromaticAberration(
                                                resolvedTuning
                                            )
                                        } else {
                                            0f
                                        }
                                    )
                                }
                            },
                            onDrawSurface = {
                                drawLiquidSphereSurface(
                                    baseColor = resolvedIndicatorColor,
                                    lensProfile = lensProfile,
                                    tuning = resolvedTuning
                                )
                            }
                        )
                    } else {
                        this.background(resolvedIndicatorColor)
                    }
                }
                .then(
                    if (drawHighlightBorder) {
                        Modifier.border(
                            width = AppSpacingTokens.Micro * 0.35f,
                            color = OpticalContrastPalette.Highlight.copy(
                                alpha = if (isLiquidGlassEnabled) 0.62f else 0.25f
                            ),
                            shape = RoundedCornerShape(cornerRadius),
                        )
                    } else {
                        Modifier
                    }
                )
        )
    }
}

internal fun resolveTopTabIndicatorWidthPx(
    itemWidthPx: Float,
    widthRatio: Float,
    minWidthPx: Float,
    horizontalInsetPx: Float
): Float {
    if (itemWidthPx <= 0f) return 0f
    val minBound = minWidthPx.coerceAtMost(itemWidthPx)
    val maxWidth = (itemWidthPx - horizontalInsetPx).coerceAtLeast(minBound)
    val desired = itemWidthPx * widthRatio
    return desired.coerceIn(minBound, maxWidth)
}

internal fun resolveLiquidIndicatorWidthPx(
    itemWidthPx: Float,
    widthMultiplier: Float,
    minWidthPx: Float,
    maxWidthPx: Float,
    maxWidthToItemRatio: Float = Float.POSITIVE_INFINITY
): Float {
    if (itemWidthPx <= 0f) return 0f

    val desiredWidth = itemWidthPx * widthMultiplier
    val designMaxWidth = maxWidthPx.coerceAtLeast(0f)
    val ratioCapWidth = if (maxWidthToItemRatio.isFinite() && maxWidthToItemRatio > 0f) {
        itemWidthPx * maxWidthToItemRatio
    } else {
        Float.POSITIVE_INFINITY
    }
    val effectiveMaxWidth = minOf(designMaxWidth, ratioCapWidth)
    val effectiveMinWidth = minWidthPx.coerceAtLeast(0f).coerceAtMost(effectiveMaxWidth)
    return desiredWidth.coerceIn(effectiveMinWidth, effectiveMaxWidth)
}

internal fun resolveIndicatorTranslationXPx(
    position: Float,
    itemWidthPx: Float,
    indicatorWidthPx: Float,
    startPaddingPx: Float,
    containerWidthPx: Float,
    clampToBounds: Boolean,
    edgeInsetPx: Float,
    viewportShiftPx: Float = 0f
): Float {
    val centerOffsetPx = (itemWidthPx - indicatorWidthPx) / 2f
    val raw = startPaddingPx + position * itemWidthPx + centerOffsetPx
    if (!clampToBounds) return raw

    val minX = edgeInsetPx.coerceAtLeast(0f) + viewportShiftPx
    val maxX = (containerWidthPx - indicatorWidthPx - edgeInsetPx + viewportShiftPx).coerceAtLeast(minX)
    return raw.coerceIn(minX, maxX)
}

internal data class LiquidLensProfile(
    val shouldRefract: Boolean,
    val motionFraction: Float,
    val refractionAmount: Float,
    val refractionHeight: Float,
    val centerHighlightAlpha: Float,
    val edgeCompressionAlpha: Float,
    val aberrationStrength: Float
)

internal data class LiquidStyleTuning(
    val idleThresholdPxPerSecond: Float,
    val dragMotionFloor: Float,
    val lensIntensityMultiplier: Float,
    val edgeWarpMultiplier: Float,
    val chromaticMultiplier: Float,
    val deformationMultiplier: Float,
    val idleBlurRadius: Float,
    val depthEffectEnabled: Boolean,
    val allowChromaticAberration: Boolean
)

internal fun resolveLiquidStyleTuning(tuning: LiquidGlassTuning): LiquidStyleTuning =
    when (tuning.mode) {
        LiquidGlassMode.CLEAR -> LiquidStyleTuning(
            idleThresholdPxPerSecond = 150f,
            dragMotionFloor = 0.10f,
            lensIntensityMultiplier = blendFloat(1.18f, 1.42f, tuning.strength),
            edgeWarpMultiplier = blendFloat(1.16f, 1.38f, tuning.strength),
            chromaticMultiplier = blendFloat(0.88f, 1.04f, tuning.strength),
            deformationMultiplier = 0.70f + tuning.strength * 0.14f,
            idleBlurRadius = tuning.backdropBlurRadius,
            depthEffectEnabled = true,
            allowChromaticAberration = false
        )
        LiquidGlassMode.BALANCED -> LiquidStyleTuning(
            idleThresholdPxPerSecond = 120f,
            dragMotionFloor = 0.24f + tuning.strength * 0.10f,
            lensIntensityMultiplier = blendFloat(1.42f, 1.72f, tuning.strength),
            edgeWarpMultiplier = blendFloat(1.40f, 1.78f, tuning.strength),
            chromaticMultiplier = blendFloat(1.08f, 1.42f, tuning.strength),
            deformationMultiplier = 0.92f + tuning.strength * 0.14f,
            idleBlurRadius = tuning.backdropBlurRadius,
            depthEffectEnabled = true,
            allowChromaticAberration = tuning.indicatorChromaticAberrationAmount > 0.01f
        )
        LiquidGlassMode.FROSTED -> LiquidStyleTuning(
            idleThresholdPxPerSecond = 220f,
            dragMotionFloor = 0.08f,
            lensIntensityMultiplier = blendFloat(1.00f, 1.16f, tuning.strength),
            edgeWarpMultiplier = blendFloat(0.98f, 1.14f, tuning.strength),
            chromaticMultiplier = 0.82f,
            deformationMultiplier = 0.42f + tuning.strength * 0.06f,
            idleBlurRadius = tuning.backdropBlurRadius,
            depthEffectEnabled = false,
            allowChromaticAberration = false
        )
    }

internal fun resolveLiquidStyleTuning(style: LiquidGlassStyle): LiquidStyleTuning =
    resolveLiquidStyleTuning(resolveLiquidGlassTuning(style))

internal fun resolveLiquidLensProfile(
    isDragging: Boolean,
    velocityPxPerSecond: Float,
    idleThresholdPxPerSecond: Float = 110f,
    dragMotionFloor: Float = 0.22f,
    lensIntensityBoost: Float = 1f,
    edgeWarpBoost: Float = 1f,
    chromaticBoost: Float = 1f,
    velocityRangePxPerSecond: Float = 2600f
): LiquidLensProfile {
    val speed = abs(velocityPxPerSecond)
    val threshold = idleThresholdPxPerSecond
    val safeVelocityRange = velocityRangePxPerSecond.coerceAtLeast(1f)
    val safeDragFloor = dragMotionFloor.coerceIn(0f, 0.8f)
    val safeLensBoost = lensIntensityBoost.coerceIn(0.8f, 2.2f)
    val safeEdgeWarpBoost = edgeWarpBoost.coerceIn(0.8f, 2.2f)
    val safeChromaBoost = chromaticBoost.coerceIn(0.8f, 2.2f)
    val baseMotion = if (isDragging) safeDragFloor else 0f
    val speedMotion = if (isDragging) {
        (speed / safeVelocityRange).coerceIn(0f, 1f)
    } else {
        ((speed - threshold).coerceAtLeast(0f) / safeVelocityRange).coerceIn(0f, 1f)
    }
    val motionFraction = (baseMotion + speedMotion * (1f - baseMotion)).coerceIn(0f, 1f)
    val shouldRefract = isDragging || speed > threshold

    if (!shouldRefract) {
        return LiquidLensProfile(
            shouldRefract = false,
            motionFraction = 0f,
            refractionAmount = 0f,
            refractionHeight = 0f,
            centerHighlightAlpha = 0f,
            edgeCompressionAlpha = 0f,
            aberrationStrength = 0f
        )
    }

    val eased = motionFraction * motionFraction * (3f - 2f * motionFraction)
    return LiquidLensProfile(
        shouldRefract = true,
        motionFraction = motionFraction,
        refractionAmount = (58f + eased * 54f) * safeLensBoost,
        refractionHeight = (84f + eased * 96f) * (0.9f + safeLensBoost * 0.1f),
        centerHighlightAlpha = 0.12f + eased * 0.16f,
        edgeCompressionAlpha = (0.06f + eased * 0.16f) * safeEdgeWarpBoost,
        aberrationStrength = ((0.008f + eased * 0.024f) * safeChromaBoost).coerceIn(0f, 0.06f)
    )
}

private fun DrawScope.drawLiquidSphereSurface(
    baseColor: Color,
    lensProfile: LiquidLensProfile,
    tuning: LiquidGlassTuning
) {
    val isMoving = lensProfile.shouldRefract
    val clearWeight = (1f - tuning.progress).coerceIn(0f, 1f)
    val frostWeight = tuning.progress.coerceIn(0f, 1f)
    val centerGlowAlpha = blendFloat(
        start = if (isMoving) lensProfile.centerHighlightAlpha else 0.12f,
        stop = tuning.whiteOverlayAlpha * 0.52f,
        fraction = frostWeight
    )
    val edgeShadeAlpha = blendFloat(
        start = if (isMoving) lensProfile.edgeCompressionAlpha else 0.03f,
        stop = 0.03f,
        fraction = frostWeight
    )
    val baseAlpha = blendFloat(
        start = if (isMoving) tuning.surfaceAlpha * 0.46f else tuning.surfaceAlpha * 0.58f,
        stop = tuning.surfaceAlpha,
        fraction = frostWeight
    )

    drawRect(baseColor.copy(alpha = baseAlpha))

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                OpticalContrastPalette.Highlight.copy(alpha = centerGlowAlpha),
                OpticalContrastPalette.Highlight.copy(alpha = centerGlowAlpha * 0.35f),
                Color.Transparent
            ),
            center = Offset(x = size.width / 2f, y = size.height * 0.54f),
            radius = size.minDimension * 0.9f
        )
    )

    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                OpticalContrastPalette.Shadow.copy(alpha = edgeShadeAlpha),
                Color.Transparent,
                Color.Transparent,
                OpticalContrastPalette.Shadow.copy(alpha = edgeShadeAlpha)
            )
        )
    )

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                OpticalContrastPalette.Highlight.copy(
                    alpha = blendFloat(
                        start = if (isMoving) 0.10f else 0.06f,
                        stop = tuning.whiteOverlayAlpha * 1.2f,
                        fraction = frostWeight
                    )
                ),
                Color.Transparent,
                OpticalContrastPalette.Shadow.copy(alpha = if (isMoving) 0.09f else 0.04f)
            )
        )
    )

    val ringAlpha = clearWeight * if (isMoving) 0.22f else 0.16f
    if (ringAlpha > 0.01f) {
        val ringStroke = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val ringHighlight = lerp(baseColor, OpticalContrastPalette.Highlight, 0.48f).copy(alpha = ringAlpha)
        val ringMid = lerp(baseColor, OpticalContrastPalette.Highlight, 0.22f).copy(alpha = ringAlpha * 0.86f)
        val ringShadow = lerp(baseColor, OpticalContrastPalette.Shadow, 0.24f).copy(alpha = ringAlpha * 0.70f)
        drawRoundRect(
            brush = Brush.sweepGradient(
                colors = listOf(
                    ringHighlight,
                    ringMid,
                    ringShadow,
                    ringMid,
                    ringHighlight
                ),
                center = Offset(size.width / 2f, size.height / 2f)
            ),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            style = Stroke(width = ringStroke)
        )
    }

    if (
        isMoving &&
        lensProfile.aberrationStrength > 0f &&
        tuning.indicatorChromaticAberrationAmount > 0f
    ) {
        val fringe = (lensProfile.aberrationStrength * 3.2f * clearWeight).coerceIn(0f, 0.18f)
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    lerp(baseColor, OpticalContrastPalette.Highlight, 0.45f).copy(alpha = fringe),
                    Color.Transparent,
                    Color.Transparent,
                    lerp(baseColor, OpticalContrastPalette.Shadow, 0.18f).copy(alpha = fringe)
                )
            )
        )
    }
}

private fun blendFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
