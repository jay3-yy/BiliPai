// Shared BiliPai liquid-glass dock shell + moving indicator (theme-independent chrome).
// Used by FloatingBottomBar (bottom) and home top tabs so both share one material stack —
// no legacy backdrop / BottomBarGlassMaterialPreset / LiquidIndicator mix-ins.
package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.animation.rememberLiquidIndicatorWobble
import com.android.purebilibili.core.ui.animation.liquidWobbleScaleX
import com.android.purebilibili.core.ui.animation.liquidWobbleScaleY

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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
import kotlin.math.cos
import kotlin.math.sin
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.material.EdgeLightSource
import top.yukonga.miuix.kmp.material.EdgePeak
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.drawEdgeStroke
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.adaptive.MotionTier

private val iosIndicatorSpecular = EdgeStroke(
    width = 1.dp,
    bevel = 2.dp,
    color = Color.White.copy(alpha = 0.12f),
    primary = EdgeLightSource(0.5f, -0.3f, -0.05f),
    secondary = EdgeLightSource(0.5f, 0.8f, -0.5f, intensity = 0.4f),
    peak = EdgePeak.Dual,
)

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f

@Composable
internal fun rememberBiliPaiGravityHighlight(
    base: EdgeStroke = iosIndicatorSpecular,
    extraDegrees: Float = 0f,
    width: Dp = base.width,
    active: Boolean,
): State<EdgeStroke> {
    val highlightSector = rememberSharedGlassHighlightSector(active)
    return remember(base, extraDegrees, highlightSector, width) {
        derivedStateOf {
            val angleRadians = (highlightSector.value * GRAVITY_HIGHLIGHT_QUANTIZE_DEGREES + extraDegrees) *
                PI / 180.0
            base.copy(
                width = width,
                primary = base.primary.copy(
                    x = LIGHT_REF_X + cos(angleRadians).toFloat(),
                    y = LIGHT_REF_Y + sin(angleRadians).toFloat(),
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
    val materialBlur = rememberMaterialBackdropBlur(liquidGlassTuning)
    // 玻璃专属：嵌套外壳采样最近的材质层，原生磨砂路径继续使用调用方的背景。
    val parentGlass = com.android.purebilibili.core.ui.blur.LocalAppGlassContainer.current
        .takeIf { enabled && com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled }
    val samplingBackdrop = (if (parentGlass != null) parentGlass.backdrop else backdrop)?.let {
        com.android.purebilibili.core.ui.blur.rememberContinuousBackdrop(it)
    }
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

    if (enabled && samplingBackdrop != null) {
        val gravityGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
        val surfaceColor = containerColor.copy(alpha = liquidGlassTuning.surfaceAlpha)
        val readabilityScrimColor = if (readabilitySurfaceColor != Color.Unspecified) {
            readabilitySurfaceColor
        } else if (isDark) Color.Black else Color.White
        val resolvedLensIntensity = lensIntensity.coerceIn(0f, 1f) *
            liquidGlassTuning.contentDistortionScale.coerceIn(0f, 1.8f)
        val shouldDrawLens = parentGlass == null && drawLens && resolvedLensIntensity > 0.001f
        val baseHighlight = rememberBiliPaiGravityHighlight(
            extraDegrees = -45f,
            active = shouldDrawLens && gravityGate.active,
        )
        val refractionHeightDp = liquidGlassTuning.refractionHeight * resolvedLensIntensity
        val refractionAmountDp = liquidGlassTuning.refractionAmount * resolvedLensIntensity
        val effectPaddingDp = resolveFloatingDockEffectPaddingDp(
            refractionAmountDp = refractionAmountDp,
            pressBloomDp = MIUIX_UPSTREAM_DOCK_PRESS_BLOOM_DP,
        )
        val blurRadiusPx = with(density) {
            if (parentGlass == null) liquidGlassTuning.backdropBlurRadius.dp.toPx() else 0f
        }
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
            saturation = if (parentGlass == null) liquidGlassTuning.saturation else 1f,
            shouldDrawLens = shouldDrawLens,
            refractionHeightPx = refractionHeightPx,
            refractionAmountPx = refractionAmountPx,
            chromaticAberration = liquidGlassTuning.shellChromaticAberrationAmount,
            effectPaddingPx = effectPaddingPx,
            materialBlur = materialBlur,
        )
        val drawRim = remember(baseHighlight, highlightAlpha, shapeBlock) {
            val block: DrawScope.() -> Unit = {
                if (highlightAlpha > 0f) drawEdgeStroke(shapeBlock(), baseHighlight.value, highlightAlpha)
            }
            block
        }
        val inheritDeformation = parentGlass?.ownsDeformation == true
        val layerBlock = remember(pressProgress, inheritDeformation) {
            val block: GraphicsLayerScope.() -> Unit = {
                val width = size.width.coerceAtLeast(1f)
                val s = if (inheritDeformation) 1f else lerp(1f, 1f + 16.dp.toPx() / width, pressProgress)
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
            .then(gravityGate.modifier)
            .drawBackdrop(
                backdrop = samplingBackdrop,
                shape = shapeBlock,
                effects = effects,
                // Inline capsules (search/input) disable the shell lens and its rim highlight
                // together; keeping the highlight alone leaves a one-pixel "shrimp line".
                onDrawFront = drawRim,
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
        } else if (samplingBackdrop != null) {
            val blurRadiusPx = with(density) { 25.dp.toPx() }
            val blurSurfaceColor = containerColor.copy(alpha = 0.65f)
            val onDrawBlurSurface = remember(blurSurfaceColor) {
                val block: DrawScope.() -> Unit = {
                    drawRect(blurSurfaceColor)
                }
                block
            }
            return baseModifier.drawBackdrop(
                backdrop = samplingBackdrop,
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
    val materialBlur = rememberMaterialBackdropBlur(liquidGlassTuning)
    val effects = rememberFloatingDockBackdropEffects(
        blurRadiusPx = blurRadiusPx,
        saturation = liquidGlassTuning.saturation,
        shouldDrawLens = shouldDrawLens,
        refractionHeightPx = refractionHeightPx,
        refractionAmountPx = refractionAmountPx,
        chromaticAberration = liquidGlassTuning.shellChromaticAberrationAmount,
        effectPaddingPx = 0f,
        materialBlur = materialBlur,
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
    materialBlur: BackdropEffectScope.(Float) -> Unit,
): BackdropEffectScope.() -> Unit = remember(
    blurRadiusPx,
    saturation,
    shouldDrawLens,
    refractionHeightPx,
    refractionAmountPx,
    chromaticAberration,
    effectPaddingPx,
    materialBlur,
) {
    {
        if (effectPaddingPx > 0f) {
            padding = maxOf(padding, effectPaddingPx)
        }
        // 折射可能扩大采样范围；在模糊之前确定最终边距，避免效果链二次求值。
        if (shouldDrawLens && top.yukonga.miuix.kmp.material.isRuntimeShaderSupported() &&
            refractionHeightPx > 0f && refractionAmountPx > 0f
        ) {
            padding = maxOf(padding, refractionAmountPx)
        }
        vibrancy(saturation)
        materialBlur(blurRadiusPx)
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
    chromaticBandHorizontal: Boolean = false,
    gapOnlyDispersion: Boolean = true,
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
        chromaticEdgeBand = if (gapOnlyDispersion) gapBandDp.dp.toPx() else null,
        chromaticBandHorizontal = chromaticBandHorizontal,
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

/** 横纵 dock 共用同一透镜、色散、表面与阴影。形变和光学遮罩在绘制时读取相同的实时尺寸。 */
@Composable
internal fun Modifier.floatingDockIndicatorOptics(
    backdrop: Backdrop,
    shape: Shape,
    width: Dp,
    height: Dp,
    shellExtent: Dp,
    tuning: LiquidGlassTuning,
    darkTheme: Boolean,
    progressProvider: () -> Float,
    scaleXProvider: () -> Float,
    scaleYProvider: () -> Float,
    highlightProvider: () -> EdgeStroke?,
    highlightAlphaProvider: () -> Float,
    vertical: Boolean = false,
    shellScaleProvider: () -> Float = { 1f },
    idleColor: Color? = null,
    // 玻璃入口显式关闭缝隙遮罩；共享默认值保留原生调用行为。
    gapOnlyDispersion: Boolean = true,
): Modifier {
    val thickness = if (vertical) width.value else height.value
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            homeIndicatorLens(
                restHeightDp = thickness,
                progress = progressProvider(),
                tuning = tuning,
                shellHeightDp = shellExtent.value,
                indicatorScaleY = if (vertical) scaleXProvider() else scaleYProvider(),
                shellScaleY = shellScaleProvider(),
                chromaticBandHorizontal = vertical,
                gapOnlyDispersion = gapOnlyDispersion,
            )
        },
        onDrawFront = { highlightProvider()?.let { drawEdgeStroke(shape, it, highlightAlphaProvider()) } },
        layerBlock = {
            this.scaleX = scaleXProvider()
            this.scaleY = scaleYProvider()
        },
        onDrawSurface = { drawHomeIndicatorSurface(progressProvider(), darkTheme, idleColor) },
    ).innerShadow(shape = shape) {
        InnerShadow(
            offset = androidx.compose.ui.unit.DpOffset.Zero,
            radius = 8.dp * homeIndicatorOpticalScale(thickness) * progressProvider(),
            color = Color.Black.copy(alpha = 0.15f),
            alpha = progressProvider(),
        )
    }
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
    gapOnlyDispersion: Boolean = true,
    combinedBackdrop: Backdrop?,
    pressProgress: Float,
    scaleX: Float,
    scaleY: Float,
    velocity: Float,
    isDark: Boolean,
    shape: Shape = CircleShape,
    alignment: Alignment = Alignment.CenterStart,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    highlightEnabled: Boolean = true,
    wobbleEnabled: Boolean = false,
    wobblePosition: Float = 0f,
    wobbleSelection: Int? = null,
    wobbleLastIndex: Int = -1,
    wobbleEdgeInset: Dp = 4.dp,
    wobbleCanSettle: Boolean = true,
    wobbleInteracting: Boolean = false,
    wobbleSelectionMotionActive: Boolean? = null,
) {
    if (!visible) return
    val canRecoil = wobbleCanSettle
    val wobble = rememberLiquidIndicatorWobble(
        enabled = wobbleEnabled,
        geometryKey = wobbleLastIndex,
        canSettle = { canRecoil },
        selectionKey = { wobbleSelection },
        interactionActive = { wobbleInteracting },
        selectionMotionActive = { wobbleSelectionMotionActive },
    ) { wobblePosition }
    val recoilStretch: () -> Float = {
        com.android.purebilibili.core.ui.animation.resolveLiquidIndicatorRecoilStretch(
            wobble.value, !wobbleInteracting && canRecoil,
        )
    }
    val recoilDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val gravityGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val pillHighlight = rememberBiliPaiGravityHighlight(
        extraDegrees = 90f,
        active = highlightEnabled && combinedBackdrop != null && pressProgress > 0.001f && gravityGate.active,
    )
    Box(
        modifier = Modifier
            .align(alignment)
            .graphicsLayer {
                translationX = translationXPx + panelOffsetPx +
                    com.android.purebilibili.core.ui.animation.liquidWobbleEdgeTranslationPx(
                        recoilStretch(), wobblePosition, wobbleLastIndex, size.width,
                        wobbleEdgeInset.toPx(), baseScale = scaleX,
                    ) * recoilDirection
            }
            .width(width)
            .height(height)
            .zIndex(2f)
            .then(gravityGate.modifier)
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
                                    gapOnlyDispersion = gapOnlyDispersion,
                                    // Match the exact layer transform used below, including
                                    // velocity compression, so the rim follows the visible pill.
                                    indicatorScaleY = liquidWobbleScaleY(
                                        resolveFloatingDockIndicatorLayerScaleY(scaleY, velocity), recoilStretch(),
                                    ),
                                )
                            },
                            onDrawFront = {
                                if (highlightEnabled) drawEdgeStroke(shape, pillHighlight.value, pressProgress)
                            },
                            layerBlock = {
                                this.scaleX = liquidWobbleScaleX(
                                    com.android.purebilibili.core.ui.animation.LiquidIndicatorMotion.scaleX(scaleX, velocity), recoilStretch(),
                                )
                                this.scaleY = liquidWobbleScaleY(
                                    com.android.purebilibili.core.ui.animation.LiquidIndicatorMotion.scaleY(scaleY, velocity), recoilStretch(),
                                )
                            },
                            onDrawSurface = {
                                drawHomeIndicatorSurface(pressProgress, isDark)
                            },
                        )
                        .innerShadow(shape = shape) {
                            InnerShadow(
                                offset = androidx.compose.ui.unit.DpOffset.Zero,
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
                            if (wobble.enabled) Modifier.graphicsLayer {
                                this.scaleX = liquidWobbleScaleX(1f, recoilStretch())
                                this.scaleY = liquidWobbleScaleY(1f, recoilStretch())
                            } else Modifier
                        )
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
