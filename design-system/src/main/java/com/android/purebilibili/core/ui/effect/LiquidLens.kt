// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.android.purebilibili.core.ui.effect

// Matched to compose-miuix-ui/miuix revision 5c91d5e5, component/liquid/Lens.kt.
// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).

import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import kotlin.math.ceil

/**
 * Rounded-rect refraction lens with optional chromatic dispersion.
 *
 * @param chromaticAberration Strength of the rim chromatic dispersion. `0` disables the
 *  effect (cheaper non-dispersion shader is used). Typical values: `0.1` for subtle,
 *  `0.2` for Apple-pill-like, `0.3+` for pronounced rainbow halo. The dispersion offset
 *  scales with the refraction depth so it concentrates at the rim band's outer edge.
 */
fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
    /** Optional top/bottom overflow band in local pixels; zero disables gap dispersion. */
    chromaticEdgeBand: Float? = null,
    /** 仅供玻璃弹窗的局部轮廓使用；默认仍按原生组件的完整尺寸和圆角采样。 */
    outlineBounds: Rect? = null,
    outlineRadius: Float? = null,
    /** 纵向 dock 显式切到左右外溢带；默认仍沿用横向 dock 的上下外溢带。 */
    chromaticBandHorizontal: Boolean = false,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding < refractionAmount) {
        padding = refractionAmount
    }

    // 圆角直接以四个标量传入 shader，避免每次效果求值分配原始与缩放数组。
    val explicitRadius = if (outlineBounds != null && outlineRadius != null) {
        outlineRadius.coerceIn(0f, outlineBounds.minDimension / 2f)
    } else null
    val topLeft: Float
    val topRight: Float
    val bottomRight: Float
    val bottomLeft: Float
    if (explicitRadius != null) {
        topLeft = explicitRadius
        topRight = explicitRadius
        bottomRight = explicitRadius
        bottomLeft = explicitRadius
    } else {
        val cornerShape = shape as? CornerBasedShape ?: return
        val maxRadius = size.minDimension / 2f
        val isLtr = layoutDirection == LayoutDirection.Ltr
        topLeft = (if (isLtr) cornerShape.topStart else cornerShape.topEnd)
            .toPx(size, this).fastCoerceAtMost(maxRadius)
        topRight = (if (isLtr) cornerShape.topEnd else cornerShape.topStart)
            .toPx(size, this).fastCoerceAtMost(maxRadius)
        bottomRight = (if (isLtr) cornerShape.bottomEnd else cornerShape.bottomStart)
            .toPx(size, this).fastCoerceAtMost(maxRadius)
        bottomLeft = (if (isLtr) cornerShape.bottomStart else cornerShape.bottomEnd)
            .toPx(size, this).fastCoerceAtMost(maxRadius)
    }

    // A moving overflow band can cross zero on every spring/velocity reversal.
    // Keep the same shader and let its mask reach zero continuously instead of
    // replacing the whole effect chain at the track boundary.
    val dispersionEnabled = chromaticAberration > 0f
    val shaderString =
        if (dispersionEnabled) {
            ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER
        } else {
            ROUNDED_RECT_REFRACTION_SHADER
        }
    val key = if (dispersionEnabled) "LiquidGlassLensDispersion" else "LiquidGlassLens"

    val sf = downscaleFactor.coerceAtLeast(1).toFloat()
    if (chromaticEdgeBand != null) {
        // Miuix records at integer scaled padding and places the texture with an
        // integer topLeft. Keep the rim's shader origin on that same grid; a
        // fractional origin shifts the top and bottom masks in opposite directions.
        // Round outwards so the lens never loses its requested sampling reach.
        padding = ceil(padding / sf) * sf
    }
    val scaledSizeW = (outlineBounds?.width ?: size.width) / sf
    val scaledSizeH = (outlineBounds?.height ?: size.height) / sf
    val scaledPadding = padding / sf
    val scaledRefractionHeight = refractionHeight / sf
    val scaledRefractionAmount = refractionAmount / sf
    val offsetX = -scaledPadding - (outlineBounds?.left ?: 0f) / sf
    val offsetY = -scaledPadding - (outlineBounds?.top ?: 0f) / sf
    val scaledTopLeft = topLeft / sf
    val scaledTopRight = topRight / sf
    val scaledBottomRight = bottomRight / sf
    val scaledBottomLeft = bottomLeft / sf
    val depth = if (depthEffect) 1f else 0f
    val horizontalBand = if (chromaticBandHorizontal) 1f else 0f
    val edgeBand = chromaticEdgeBand?.coerceAtLeast(0f)?.div(sf) ?: -1f

    // 完整记录 shader 类型及 uniform；背景移动不改变局部光学参数时直接复用效果。
    val cache = liquidLensEffectCache()
    cache[0] = scaledSizeW
    cache[1] = scaledSizeH
    cache[2] = offsetX
    cache[3] = offsetY
    cache[4] = scaledTopLeft
    cache[5] = scaledTopRight
    cache[6] = scaledBottomRight
    cache[7] = scaledBottomLeft
    cache[8] = scaledRefractionHeight
    cache[9] = scaledRefractionAmount
    cache[10] = depth
    cache[11] = horizontalBand
    cache[12] = chromaticAberration
    cache[13] = edgeBand
    cache[14] = if (dispersionEnabled) 1f else 0f
    if (cache.needsUpdate) {
        val input = renderEffect
        try {
            // 缓存独立镜片，不把上游模糊、调色或背景输入固化在镜片中。
            renderEffect = null
            runtimeShaderEffect(
                key = key,
                shaderString = shaderString,
                uniformShaderName = "content",
            ) {
                setFloatUniform("size", scaledSizeW, scaledSizeH)
                setFloatUniform("offset", offsetX, offsetY)
                setFloatUniform("cornerRadii", scaledTopLeft, scaledTopRight, scaledBottomRight, scaledBottomLeft)
                setFloatUniform("refractionHeight", scaledRefractionHeight)
                setFloatUniform("refractionAmount", -scaledRefractionAmount)
                setFloatUniform("depthEffect", depth)
                if (dispersionEnabled) {
                    setFloatUniform("chromaticBandHorizontal", horizontalBand)
                    setFloatUniform("chromaticAberration", chromaticAberration)
                    setFloatUniform("chromaticEdgeBand", edgeBand)
                }
            }
            cache.store(renderEffect)
        } finally {
            renderEffect = input
        }
    }
    cache.applyTo(this)
}

private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

private const val ROUNDED_RECT_REFRACTION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

private const val ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;
uniform float chromaticEdgeBand;
uniform float chromaticBandHorizontal;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    // 普通透镜保留原光谱权重；dock 指示器只在选定轴超出轨道的区域色散。
    float dispersionWeight = (centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y);
    if (chromaticEdgeBand >= 0.0) {
        float edgeDistance = chromaticBandHorizontal > 0.5
            ? halfSize.x - abs(centeredCoord.x)
            : halfSize.y - abs(centeredCoord.y);
        // A fixed local-pixel feather makes subpixel bands fade in from zero.
        // Shrinking feather with the band made the outermost pixel jump straight
        // to full dispersion for any positive band (and degenerate at zero).
        float feather = 1.0;
        dispersionWeight = 1.0 - smoothstep(chromaticEdgeBand - feather, chromaticEdgeBand, edgeDistance);
        if (dispersionWeight <= 0.0) return content.eval(refractedCoord);
    }
    float dispersionIntensity = chromaticAberration * dispersionWeight;
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""
