package com.android.purebilibili.core.ui.effect

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

private fun normalizeSweepClockwise(startDeg: Float, endDeg: Float): Float {
    var sweep = (endDeg - startDeg) % 360f
    if (sweep < 0f) sweep += 360f
    return sweep
}

private fun normalizeSweepCounterClockwise(startDeg: Float, endDeg: Float): Float {
    var sweep = (endDeg - startDeg) % 360f
    if (sweep > 0f) sweep -= 360f
    return sweep
}

/**
 * 两个液态玻璃胶囊（左侧导航胶囊 + 右侧搜索圆钮/搜索条）的 2D C1 连续平滑融合轮廓。
 * 支持右侧胶囊在上下拖拽时的真实二维偏移，使用正圆切弧桥接两胶囊，避免垂直拖拽时变形或图标偏离外壳。
 */
class FusedCapsulesShape(
    private val leftWidthPx: Float,
    private val leftHeightPx: Float,
    private val leftCenterYPx: Float,
    private val rightLeftPx: Float,
    private val rightWidthPx: Float,
    private val rightHeightPx: Float,
    private val rightCenterYPx: Float,
    private val fusionRadiusPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rA = (leftHeightPx / 2f).coerceAtMost(leftWidthPx / 2f).coerceAtLeast(1f)
        val rB = (rightHeightPx / 2f).coerceAtMost(rightWidthPx / 2f).coerceAtLeast(1f)
        val yA = leftCenterYPx - rA
        val yB = rightCenterYPx - rB
        val cAx = leftWidthPx - rA
        val cAy = leftCenterYPx
        val cBx = rightLeftPx + rB
        val cBy = rightCenterYPx
        val bRightCx = (rightLeftPx + rightWidthPx - rB).coerceAtLeast(cBx)

        val dx = cBx - cAx
        val dy = cBy - cAy
        val d = hypot(dx, dy).coerceAtLeast(1f)
        val rf = fusionRadiusPx.coerceAtLeast(1f)
        val r1 = rA + rf
        val r2 = rB + rf
        val xf = (d * d + r1 * r1 - r2 * r2) / (2f * d)
        val yfSq = r1 * r1 - xf * xf

        val path = Path()
        if (dx <= 0.5f || yfSq <= 1f || sqrt(yfSq) <= rf + 0.5f) {
            path.addRoundRect(
                RoundRect(
                    left = 0f,
                    top = yA,
                    right = leftWidthPx,
                    bottom = yA + 2f * rA,
                    cornerRadius = CornerRadius(rA, rA),
                )
            )
            path.addRoundRect(
                RoundRect(
                    left = rightLeftPx,
                    top = yB,
                    right = rightLeftPx + rightWidthPx,
                    bottom = yB + 2f * rB,
                    cornerRadius = CornerRadius(rB, rB),
                )
            )
            return Outline.Generic(path)
        }

        val yf = sqrt(yfSq)
        // 沿 A -> B 方向的单位向量 u，及其逆时针法向量 n（指向屏幕上方一侧）
        val ux = dx / d
        val uy = dy / d
        val nx = uy
        val ny = -ux

        val fTopX = cAx + xf * ux + yf * nx
        val fTopY = cAy + xf * uy + yf * ny
        val fBotX = cAx + xf * ux - yf * nx
        val fBotY = cAy + xf * uy - yf * ny

        val radToDeg = (180.0 / PI).toFloat()
        val alphaATopDeg = atan2(fTopY - cAy, fTopX - cAx) * radToDeg
        val alphaABotDeg = atan2(fBotY - cAy, fBotX - cAx) * radToDeg

        val fTopStartDeg = atan2(cAy - fTopY, cAx - fTopX) * radToDeg
        val fTopEndDeg = atan2(cBy - fTopY, cBx - fTopX) * radToDeg
        val fTopSweepDeg = normalizeSweepCounterClockwise(fTopStartDeg, fTopEndDeg)

        val angleBTopDeg = atan2(fTopY - cBy, fTopX - cBx) * radToDeg
        val angleBBotDeg = atan2(fBotY - cBy, fBotX - cBx) * radToDeg

        val fBotStartDeg = atan2(cBy - fBotY, cBx - fBotX) * radToDeg
        val fBotEndDeg = atan2(cAy - fBotY, cAx - fBotX) * radToDeg
        val fBotSweepDeg = normalizeSweepCounterClockwise(fBotStartDeg, fBotEndDeg)

        val aRightRect = Rect(cAx - rA, cAy - rA, cAx + rA, cAy + rA)
        val aLeftRect = Rect(0f, cAy - rA, 2f * rA, cAy + rA)
        val bLeftRect = Rect(cBx - rB, cBy - rB, cBx + rB, cBy + rB)
        val bRightRect = Rect(bRightCx - rB, cBy - rB, bRightCx + rB, cBy + rB)
        val fTopRect = Rect(fTopX - rf, fTopY - rf, fTopX + rf, fTopY + rf)
        val fBotRect = Rect(fBotX - rf, fBotY - rf, fBotX + rf, fBotY + rf)

        // 1. 左侧胶囊顶部直线 → 右端上半圆弧至上切点
        path.moveTo(rA, yA)
        path.lineTo(cAx, yA)
        path.arcTo(
            aRightRect,
            startAngleDegrees = -90f,
            sweepAngleDegrees = normalizeSweepClockwise(-90f, alphaATopDeg),
            forceMoveTo = false,
        )
        // 2. 顶部平滑凹颈切圆弧
        path.arcTo(fTopRect, startAngleDegrees = fTopStartDeg, sweepAngleDegrees = fTopSweepDeg, forceMoveTo = false)
        // 3. 右侧胶囊外轮廓（正圆或水平胶囊，顺时针从上切点绕至下切点）
        if (bRightCx > cBx + 0.5f) {
            path.arcTo(
                bLeftRect,
                startAngleDegrees = angleBTopDeg,
                sweepAngleDegrees = normalizeSweepClockwise(angleBTopDeg, -90f),
                forceMoveTo = false,
            )
            path.lineTo(bRightCx, yB)
            path.arcTo(bRightRect, startAngleDegrees = -90f, sweepAngleDegrees = 180f, forceMoveTo = false)
            path.lineTo(cBx, yB + 2f * rB)
            path.arcTo(
                bLeftRect,
                startAngleDegrees = 90f,
                sweepAngleDegrees = normalizeSweepClockwise(90f, angleBBotDeg),
                forceMoveTo = false,
            )
        } else {
            path.arcTo(
                bLeftRect,
                startAngleDegrees = angleBTopDeg,
                sweepAngleDegrees = normalizeSweepClockwise(angleBTopDeg, angleBBotDeg),
                forceMoveTo = false,
            )
        }
        // 4. 底部平滑凹颈切圆弧
        path.arcTo(fBotRect, startAngleDegrees = fBotStartDeg, sweepAngleDegrees = fBotSweepDeg, forceMoveTo = false)
        // 5. 左侧胶囊右端下半圆弧 → 底边直线 → 左端半圆弧闭合
        path.arcTo(
            aRightRect,
            startAngleDegrees = alphaABotDeg,
            sweepAngleDegrees = normalizeSweepClockwise(alphaABotDeg, 90f),
            forceMoveTo = false,
        )
        path.lineTo(rA, yA + 2f * rA)
        path.arcTo(aLeftRect, startAngleDegrees = 90f, sweepAngleDegrees = 180f, forceMoveTo = false)
        path.close()

        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is FusedCapsulesShape &&
        leftWidthPx == other.leftWidthPx &&
        leftHeightPx == other.leftHeightPx &&
        leftCenterYPx == other.leftCenterYPx &&
        rightLeftPx == other.rightLeftPx &&
        rightWidthPx == other.rightWidthPx &&
        rightHeightPx == other.rightHeightPx &&
        rightCenterYPx == other.rightCenterYPx &&
        fusionRadiusPx == other.fusionRadiusPx

    override fun hashCode(): Int {
        var result = leftWidthPx.hashCode()
        result = 31 * result + leftHeightPx.hashCode()
        result = 31 * result + leftCenterYPx.hashCode()
        result = 31 * result + rightLeftPx.hashCode()
        result = 31 * result + rightWidthPx.hashCode()
        result = 31 * result + rightHeightPx.hashCode()
        result = 31 * result + rightCenterYPx.hashCode()
        result = 31 * result + fusionRadiusPx.hashCode()
        return result
    }
}

/**
 * 双胶囊 2D SDF 平滑融合光学折射镜：
 * 在 Android 13+ 通过 AGSL polynomial smooth-min 对两胶囊及其二维凹颈统一求法线与折射偏移。
 */
fun BackdropEffectScope.fusedCapsulesLens(
    leftWidthPx: Float,
    leftHeightPx: Float,
    leftCenterYPx: Float,
    rightLeftPx: Float,
    rightWidthPx: Float,
    rightHeightPx: Float,
    rightCenterYPx: Float,
    smoothK: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    chromaticAberration: Float = 0f,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return
    val minHeight = minOf(leftHeightPx, rightHeightPx).coerceAtLeast(1f)
    val effectiveRefractionHeight = refractionHeight.coerceAtMost(minHeight * 0.45f)
    if (effectiveRefractionHeight <= 0f) return

    if (padding < refractionAmount) {
        padding = refractionAmount
    }

    val sf = downscaleFactor
    val scaledLeftW = leftWidthPx / sf
    val scaledLeftH = leftHeightPx / sf
    val scaledLeftCy = leftCenterYPx / sf
    val scaledRightLeft = rightLeftPx / sf
    val scaledRightW = rightWidthPx / sf
    val scaledRightH = rightHeightPx / sf
    val scaledRightCy = rightCenterYPx / sf
    val scaledK = smoothK.coerceAtLeast(1f) / sf
    val scaledPadding = padding / sf
    val scaledRefractionH = effectiveRefractionHeight / sf
    val scaledRefractionAmt = refractionAmount / sf
    val safeChromaticAberration = chromaticAberration.coerceAtLeast(0f)

    // 缓存融合镜片参数；仅背景滚动而不改变胶囊几何或光学参数时复用效果对象。
    val cache = fusedLiquidLensEffectCache()
    cache[0] = scaledLeftW
    cache[1] = scaledLeftH
    cache[2] = scaledLeftCy
    cache[3] = scaledRightLeft
    cache[4] = scaledRightW
    cache[5] = scaledRightH
    cache[6] = scaledRightCy
    cache[7] = scaledPadding
    cache[8] = scaledK
    cache[9] = scaledRefractionH
    cache[10] = scaledRefractionAmt
    cache[11] = safeChromaticAberration
    if (cache.needsUpdate) {
        val input = renderEffect
        try {
            renderEffect = null
            runtimeShaderEffect(
                key = "FusedLiquidCapsulesLens",
                shaderString = FUSED_CAPSULES_REFRACTION_SHADER,
                uniformShaderName = "content",
            ) {
                setFloatUniform("leftCenter", scaledLeftW * 0.5f, scaledLeftCy)
                setFloatUniform("leftHalfSize", scaledLeftW * 0.5f, scaledLeftH * 0.5f)
                setFloatUniform("rightCenter", scaledRightLeft + scaledRightW * 0.5f, scaledRightCy)
                setFloatUniform("rightHalfSize", scaledRightW * 0.5f, scaledRightH * 0.5f)
                setFloatUniform("offset", -scaledPadding, -scaledPadding)
                setFloatUniform("smoothK", scaledK)
                setFloatUniform("refractionHeight", scaledRefractionH)
                setFloatUniform("refractionAmount", -scaledRefractionAmt)
                setFloatUniform("chromaticAberration", safeChromaticAberration)
            }
            cache.store(renderEffect)
        } finally {
            renderEffect = input
        }
    }
    cache.applyTo(this)
}

private const val FUSED_CAPSULES_REFRACTION_SHADER = """
uniform shader content;
uniform float2 leftCenter;
uniform float2 leftHalfSize;
uniform float2 rightCenter;
uniform float2 rightHalfSize;
uniform float2 offset;
uniform float smoothK;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float chromaticAberration;

float sdCapsule(float2 p, float2 center, float2 halfSize) {
    float r = min(halfSize.x, halfSize.y);
    float2 q = abs(p - center) - (halfSize - float2(r));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float2 gradSdCapsule(float2 p, float2 center, float2 halfSize) {
    float2 d = p - center;
    float r = min(halfSize.x, halfSize.y);
    float2 q = abs(d) - (halfSize - float2(r));
    if (q.x > 0.0 || q.y > 0.0) {
        float2 m = max(q, 0.0);
        float ml = length(m);
        if (ml > 1e-4) {
            return sign(d) * (m / ml);
        }
    }
    float blend = smoothstep(-1.0, 1.0, q.x - q.y);
    return sign(d) * normalize(float2(blend, 1.0 - blend));
}

float circleMap(float x) {
    return 1.0 - sqrt(max(0.0, 1.0 - x * x));
}

half4 main(float2 coord) {
    float2 p = coord + offset;
    float a = sdCapsule(p, leftCenter, leftHalfSize);
    float b = sdCapsule(p, rightCenter, rightHalfSize);
    float h = clamp(0.5 + 0.5 * (b - a) / smoothK, 0.0, 1.0);
    float sd = mix(b, a, h) - smoothK * h * (1.0 - h);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    float d = min(sd, 0.0);
    // Polynomial smooth-min 解析梯度：对 h 的偏导项精确抵消为 0，
    // 梯度等于 h * grad(a) + (1 - h) * grad(b)，替代原先 4 点有限差分（8 次 SDF）。
    float2 g;
    if (h <= 0.0) {
        g = gradSdCapsule(p, rightCenter, rightHalfSize);
    } else if (h >= 1.0) {
        g = gradSdCapsule(p, leftCenter, leftHalfSize);
    } else {
        g = h * gradSdCapsule(p, leftCenter, leftHalfSize)
            + (1.0 - h) * gradSdCapsule(p, rightCenter, rightHalfSize);
    }
    float len = length(g);
    if (len < 1e-4) {
        return content.eval(coord);
    }
    float2 normal = g / len;
    float rimProgress = clamp(1.0 + d / refractionHeight, 0.0, 1.0);
    float refractedDistance = circleMap(rimProgress) * refractionAmount;
    float2 baseCoord = coord + normal * refractedDistance;

    if (chromaticAberration <= 0.001) {
        return content.eval(baseCoord);
    }

    float disp = chromaticAberration * rimProgress * rimProgress * abs(refractionAmount) * 0.25;
    float2 shift = normal * disp;
    half r = content.eval(baseCoord + shift).r;
    half4 centerSample = content.eval(baseCoord);
    half bSample = content.eval(baseCoord - shift).b;
    return half4(r, centerSample.g, bSample, centerSample.a);
}
"""
