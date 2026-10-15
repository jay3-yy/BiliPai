// Shared BiliPai liquid-glass dock shell + moving indicator (theme-independent chrome).
// Used by FloatingBottomBar (bottom) and home top tabs so both share one material stack —
// no legacy backdrop / BottomBarGlassMaterialPreset / LiquidIndicator mix-ins.
package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import com.android.purebilibili.core.ui.AppSurfaceTokens
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import kotlin.math.abs
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.android.purebilibili.feature.home.components.liquid.InnerShadow
import com.android.purebilibili.feature.home.components.liquid.innerShadow
import com.android.purebilibili.feature.home.components.liquid.lens
import com.android.purebilibili.feature.home.components.liquid.vibrancy
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.adaptive.MotionTier

private val iosIndicatorSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f
internal const val GRAVITY_HIGHLIGHT_QUANTIZE_DEGREES = 3f

internal fun quantizeGravityHighlightDirection(
    gravityX: Float,
    gravityY: Float,
    stepDegrees: Float = GRAVITY_HIGHLIGHT_QUANTIZE_DEGREES,
): Pair<Float, Float> {
    val gMagSq = gravityX * gravityX + gravityY * gravityY
    if (gMagSq <= GRAVITY_DIR_THRESHOLD_SQ) return 0f to -1f
    val invMag = 1f / sqrt(gMagSq)
    val nx = gravityX * invMag
    val ny = gravityY * invMag
    val stepRad = (stepDegrees * PI / 180.0).toFloat()
    if (stepRad <= 0f) return nx to ny
    val angle = atan2(ny, nx)
    val quantized = (angle / stepRad).roundToInt() * stepRad
    return cos(quantized) to sin(quantized)
}

@Composable
internal fun rememberBiliPaiGravityHighlight(
    base: Highlight = iosIndicatorSpecular,
    extraDegrees: Float = 0f,
    width: Dp = base.width,
): State<Highlight> {
    val tiltState = rememberDeviceTilt()
    val quantizedDirection = remember(tiltState) {
        derivedStateOf {
            quantizeGravityHighlightDirection(
                gravityX = tiltState.value.gravityX,
                gravityY = tiltState.value.gravityY,
            )
        }
    }
    return remember(base, extraDegrees, quantizedDirection, width) {
        derivedStateOf {
            val baseStyle = base.style as BloomStroke
            val basePrimary = baseStyle.primaryLight
            val (lx0, ly0) = quantizedDirection.value
            val rad = extraDegrees * PI / 180.0
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val lx = c * lx0 - s * ly0
            val ly = s * lx0 + c * ly0
            base.copy(
                width = width,
                style = baseStyle.copy(
                    primaryLight = basePrimary.copy(
                        position = LightPosition(
                            x = LIGHT_REF_X + lx,
                            y = LIGHT_REF_Y + ly,
                            z = basePrimary.position.z,
                        ),
                    ),
                ),
            )
        }
    }
}

/**
 * Outer floating dock shell — BiliPai FloatingBottomBar base layer:
 * dropShadow + tuning-driven vibrancy / blur / lens + gravity highlight.
 */
@Composable
internal fun Modifier.biliPaiFloatingDockShell(
    backdrop: Backdrop?,
    containerColor: Color,
    pressProgress: Float,
    panelOffsetPx: Float = 0f,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    blurEnabled: Boolean = false,
    hazeState: HazeState? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    drawLens: Boolean = true,
    lensIntensity: Float = 1f,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    interactionLayerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    interactionState: com.android.purebilibili.core.ui.components.AppElasticPressState? = null,
    tint: Color = Color.Unspecified,
    darkTheme: Boolean? = null,
    // Opt-in for surfaces following an already animated theme palette. Keep the
    // native/default dock's existing black/white material unchanged.
    readabilitySurfaceColor: Color = Color.Unspecified,
): Modifier {
    val isDark = darkTheme ?: com.android.purebilibili.core.ui.isAppInDarkTheme()
    val density = LocalDensity.current
    val shapeBlock = remember(shape) { { shape } }
    val baseModifier = this
        .graphicsLayer { translationX = panelOffsetPx }
        .dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = 10.dp,
                color = Color.Black,
                alpha = if (isDark) 0.2f else 0.1f,
            ),
        )

    if (enabled && backdrop != null) {
        val baseHighlight = rememberBiliPaiGravityHighlight(extraDegrees = -45f)
        val surfaceColor = containerColor.copy(alpha = liquidGlassTuning.surfaceAlpha)
        val readabilityScrimColor = if (readabilitySurfaceColor != Color.Unspecified) {
            readabilitySurfaceColor
        } else if (isDark) Color.Black else Color.White
        val resolvedLensIntensity = lensIntensity.coerceIn(0f, 1f) *
            liquidGlassTuning.contentDistortionScale.coerceIn(0f, 1.8f)
        val shouldDrawLens = drawLens && resolvedLensIntensity > 0.001f
        val refractionHeightDp = liquidGlassTuning.refractionHeight * resolvedLensIntensity
        val refractionAmountDp = liquidGlassTuning.refractionAmount * resolvedLensIntensity
        val effectPaddingDp = resolveFloatingDockEffectPaddingDp(
            refractionAmountDp = refractionAmountDp,
            pressBloomDp = MIUIX_UPSTREAM_DOCK_PRESS_BLOOM_DP,
        )
        val blurRadiusPx = with(density) { liquidGlassTuning.backdropBlurRadius.dp.toPx() }
        val refractionHeightPx = with(density) { refractionHeightDp.dp.toPx() }
        val refractionAmountPx = with(density) { refractionAmountDp.dp.toPx() }
        val effectPaddingPx = with(density) { effectPaddingDp.dp.toPx() }
        val highlightAlpha = if (shouldDrawLens) {
            (0.75f * resolvedLensIntensity).coerceIn(0f, 1f)
        } else {
            0f
        }
        val scrimAlpha = liquidGlassTuning.contentReadabilityScrimAlpha
        val effects = rememberFloatingDockBackdropEffects(
            blurRadiusPx = blurRadiusPx,
            saturation = liquidGlassTuning.saturation,
            shouldDrawLens = shouldDrawLens,
            refractionHeightPx = refractionHeightPx,
            refractionAmountPx = refractionAmountPx,
            chromaticAberration = liquidGlassTuning.shellChromaticAberrationAmount,
            effectPaddingPx = effectPaddingPx,
        )
        val highlightBlock = remember(baseHighlight, highlightAlpha) {
            val block: BackdropEffectScope.() -> Highlight? = {
                baseHighlight.value.copy(alpha = highlightAlpha)
            }
            block
        }
        val layerBlock = remember(pressProgress) {
            val block: GraphicsLayerScope.() -> Unit = {
                val width = size.width.coerceAtLeast(1f)
                val s = lerp(1f, 1f + 16.dp.toPx() / width, pressProgress)
                scaleX = s
                scaleY = s
            }
            block
        }
        val onDrawSurface = remember(surfaceColor, readabilityScrimColor, scrimAlpha, interactionState, tint) {
            val block: DrawScope.() -> Unit = {
                if (tint != Color.Unspecified) {
                    drawRect(tint, blendMode = androidx.compose.ui.graphics.BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.75f))
                }
                drawRect(surfaceColor)
                if (scrimAlpha > 0f) {
                    drawRect(readabilityScrimColor.copy(alpha = scrimAlpha))
                }
                interactionState?.drawHighlight(this)
            }
            block
        }
        return baseModifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = shapeBlock,
                effects = effects,
                // Inline capsules (search/input) disable the shell lens and its rim highlight
                // together; keeping the highlight alone leaves a one-pixel "shrimp line".
                highlight = highlightBlock,
                layerBlock = interactionLayerBlock ?: layerBlock,
                onDrawSurface = onDrawSurface,
            )
    }

    if (blurEnabled) {
        if (hazeState != null) {
            val blurSurfaceColor = containerColor.copy(alpha = 0.65f)
            return baseModifier
                .unifiedBlur(
                    hazeState = hazeState,
                    shape = shape,
                    surfaceType = BlurSurfaceType.BOTTOM_BAR,
                    motionTier = motionTier,
                    isScrolling = false,
                    isTransitionRunning = isTransitionRunning,
                    forceLowBudget = forceLowBlurBudget,
                )
                .background(blurSurfaceColor, shape)
        } else if (backdrop != null) {
            val blurRadiusPx = with(density) { 25.dp.toPx() }
            val blurSurfaceColor = containerColor.copy(alpha = 0.65f)
            val onDrawBlurSurface = remember(blurSurfaceColor) {
                val block: DrawScope.() -> Unit = {
                    drawRect(blurSurfaceColor)
                }
                block
            }
            return baseModifier.drawBackdrop(
                backdrop = backdrop,
                shape = shapeBlock,
                effects = {
                    blur(blurRadiusPx, blurRadiusPx)
                },
                onDrawSurface = onDrawBlurSurface,
            )
        }
    }

    val opaqueColor = if (containerColor.alpha == 0f) containerColor else containerColor.copy(alpha = 1f)
    return baseModifier.background(opaqueColor, shape)
}

/**
 * Hidden capture layer material — same shell glass as BiliPai foreground Row
 * Caller supplies layerBackdrop + alpha(0); material values match the visible shell.
 */
@Composable
internal fun Modifier.biliPaiFloatingDockCaptureSurface(
    backdrop: Backdrop,
    containerColor: Color,
    panelOffsetPx: Float = 0f,
    shape: Shape = CircleShape,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
): Modifier {
    val isDark = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val density = LocalDensity.current
    val surfaceColor = containerColor.copy(alpha = liquidGlassTuning.surfaceAlpha)
    val readabilityScrimColor = if (isDark) Color.Black else Color.White
    val distortionScale = liquidGlassTuning.contentDistortionScale.coerceIn(0f, 1.8f)
    val shouldDrawLens = distortionScale > 0.001f
    val blurRadiusPx = with(density) { liquidGlassTuning.backdropBlurRadius.dp.toPx() }
    val refractionHeightPx = with(density) {
        liquidGlassTuning.refractionHeight.dp.toPx() * distortionScale
    }
    val refractionAmountPx = with(density) {
        liquidGlassTuning.refractionAmount.dp.toPx() * distortionScale
    }
    val scrimAlpha = liquidGlassTuning.contentReadabilityScrimAlpha
    val shapeBlock = remember(shape) { { shape } }
    val effects = rememberFloatingDockBackdropEffects(
        blurRadiusPx = blurRadiusPx,
        saturation = liquidGlassTuning.saturation,
        shouldDrawLens = shouldDrawLens,
        refractionHeightPx = refractionHeightPx,
        refractionAmountPx = refractionAmountPx,
        chromaticAberration = liquidGlassTuning.shellChromaticAberrationAmount,
        effectPaddingPx = 0f,
    )
    val onDrawSurface = remember(surfaceColor, readabilityScrimColor, scrimAlpha) {
        val block: DrawScope.() -> Unit = {
            drawRect(surfaceColor)
            if (scrimAlpha > 0f) {
                drawRect(readabilityScrimColor.copy(alpha = scrimAlpha))
            }
        }
        block
    }
    return this
        .graphicsLayer { translationX = panelOffsetPx }
        .drawBackdrop(
            backdrop = backdrop,
            shape = shapeBlock,
            effects = effects,
            onDrawSurface = onDrawSurface,
        )
}

@Composable
private fun rememberFloatingDockBackdropEffects(
    blurRadiusPx: Float,
    saturation: Float,
    shouldDrawLens: Boolean,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    chromaticAberration: Float,
    effectPaddingPx: Float,
): BackdropEffectScope.() -> Unit = remember(
    blurRadiusPx,
    saturation,
    shouldDrawLens,
    refractionHeightPx,
    refractionAmountPx,
    chromaticAberration,
    effectPaddingPx,
) {
    {
        if (effectPaddingPx > 0f) {
            padding = maxOf(padding, effectPaddingPx)
        }
        vibrancy(saturation)
        blur(blurRadiusPx, blurRadiusPx)
        if (shouldDrawLens) {
            lens(
                refractionHeight = refractionHeightPx,
                refractionAmount = refractionAmountPx,
                chromaticAberration = chromaticAberration,
            )
        }
    }
}

/** One optical recipe for the home indicator and every compact/vertical reuse. */
internal fun homeIndicatorOpticalScale(restHeightDp: Float): Float =
    (restHeightDp / com.android.purebilibili.core.ui.BottomBarReferenceIndicatorHeightDp).coerceIn(0f, 1f)

internal fun BackdropEffectScope.homeIndicatorLens(
    restHeightDp: Float,
    progress: Float,
    tuning: LiquidGlassTuning,
    shellHeightDp: Float = restHeightDp * 64f / 56f,
    indicatorScaleY: Float = 1f,
    shellScaleY: Float = 1f,
) {
    val gapBandDp = resolveFloatingDockChromaticGapBand(
        indicatorHeight = restHeightDp,
        shellHeight = shellHeightDp,
        indicatorScaleY = indicatorScaleY,
        shellScaleY = shellScaleY,
    )
    val reach = homeIndicatorOpticalScale(restHeightDp) * progress.coerceIn(0f, 1f)
    lens(
        refractionHeight = 10.dp.toPx() * reach * tuning.indicatorLensBoost * tuning.contentDistortionScale,
        refractionAmount = 14.dp.toPx() * reach * tuning.indicatorEdgeWarpBoost * tuning.contentDistortionScale,
        depthEffect = true,
        chromaticAberration = resolveLiquidGlassIndicatorChromaticAberration(tuning),
        chromaticEdgeBand = gapBandDp.dp.toPx(),
    )
}

internal fun DrawScope.drawHomeIndicatorSurface(
    progress: Float,
    darkTheme: Boolean,
    idleColor: Color? = null,
) {
    val pressed = progress.coerceIn(0f, 1f)
    drawRect(
        color = idleColor ?: if (darkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
        alpha = 1f - pressed,
    )
    drawRect(Color.Black.copy(alpha = 0.03f * pressed))
}

/**
 * Moving liquid indicator — BiliPai FloatingBottomBar indicator box:
 * combined backdrop + tuning-driven lens + velocity stretch + innerShadow.
 */
@Composable
internal fun BoxScope.BiliPaiFloatingDockIndicator(
    visible: Boolean,
    translationXPx: Float,
    panelOffsetPx: Float,
    width: Dp,
    height: Dp,
    dockHeight: Dp = height * 64f / 56f,
    combinedBackdrop: Backdrop?,
    pressProgress: Float,
    scaleX: Float,
    scaleY: Float,
    velocity: Float,
    isDark: Boolean,
    shape: Shape = CircleShape,
    alignment: Alignment = Alignment.CenterStart,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
) {
    if (!visible) return
    val pillHighlight = rememberBiliPaiGravityHighlight(extraDegrees = 90f)
    Box(
        modifier = Modifier
            .align(alignment)
            .graphicsLayer {
                translationX = translationXPx + panelOffsetPx
            }
            .width(width)
            .height(height)
            .zIndex(2f)
            .then(
                if (combinedBackdrop != null) {
                    Modifier
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { shape },
                            effects = {
                                val progress = pressProgress
                                homeIndicatorLens(
                                    restHeightDp = height.value,
                                    progress = progress,
                                    tuning = liquidGlassTuning,
                                    shellHeightDp = dockHeight.value,
                                    indicatorScaleY = scaleY,
                                )
                            },
                            highlight = { pillHighlight.value.copy(alpha = pressProgress) },
                            layerBlock = {
                                this.scaleX = com.android.purebilibili.core.ui.animation.LiquidIndicatorMotion.scaleX(scaleX, velocity)
                                this.scaleY = com.android.purebilibili.core.ui.animation.LiquidIndicatorMotion.scaleY(scaleY, velocity)
                            },
                            onDrawSurface = {
                                drawHomeIndicatorSurface(pressProgress, isDark)
                            },
                        )
                        .innerShadow(shape = shape) {
                            InnerShadow(
                                radius = 8.dp * homeIndicatorOpticalScale(height.value) * pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = pressProgress,
                            )
                        }
                } else {
                    val solidCapsuleColor = if (!isDark) {
                        AppSurfaceTokens.surfaceContainer()
                    } else {
                        AppSurfaceTokens.surfaceContainerHighest()
                    }
                    Modifier
                        .then(
                            if (!isDark) {
                                Modifier.dropShadow(
                                    shape = shape,
                                    shadow = Shadow(radius = 3.dp, color = Color.Black, alpha = 0.08f)
                                )
                            } else Modifier
                        )
                        .clip(shape)
                        .background(solidCapsuleColor, shape)
                }
            )
    )
}
