package com.android.purebilibili.core.ui.popup

import com.android.purebilibili.core.ui.effect.BackdropRenderEffectCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

private fun popupUnionBounds(a: Rect, b: Rect): Rect = Rect(
    min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom),
)

internal fun glassPopupSurfaceBounds(card: Rect, anchor: Rect, density: Float): Rect {
    val bounds = if (anchor.isEmpty) card else popupUnionBounds(card, anchor)
    val dragEnvelope = card.minDimension * 7f / 32f * 1.35f + 32f * density
    // 两侧各预留最大拉伸增量与有界位移，强拖拽时不裁断磨砂和边缘。
    val expanded = bounds.inflate(max(dragEnvelope, 32f * density + (card.width + card.height) * 0.25f))
    return Rect(floor(expanded.left), floor(expanded.top), ceil(expanded.right), ceil(expanded.bottom))
}

internal fun popupRoundRectDistance(x: Float, y: Float, rect: Rect, radius: Float): Float {
    val r = radius.coerceIn(0f, rect.minDimension / 2f)
    val dx = abs(x - rect.center.x) - rect.width / 2f + r
    val dy = abs(y - rect.center.y) - rect.height / 2f + r
    return sqrt(max(dx, 0f) * max(dx, 0f) + max(dy, 0f) * max(dy, 0f)) + min(max(dx, dy), 0f) - r
}

internal fun popupSurfaceDistance(x: Float, y: Float, frame: AppGlassPopupFrame): Float {
    val a = popupRoundRectDistance(x, y, frame.rect, frame.radius)
    val source = frame.sourceRect ?: return a
    val b = popupRoundRectDistance(x, y, source, source.minDimension / 2f)
    val k = frame.surfaceBlend
    if (k <= 0.001f) return min(a, b)
    val h = max(k - abs(a - b), 0f) / k
    return min(a, b) - h * h * k * 0.25f
}

/** 同一帧的联合轮廓供采样、描边和阴影复用；稳态不执行等值线采样。 */
internal class PopupSurfaceOutlineCache {
    private var previous: AppGlassPopupFrame? = null
    private var previousOrigin = Offset.Unspecified
    private var previousDensity = 0f
    private var outline: Outline = Outline.Rectangle(Rect.Zero)
    private var field = FloatArray(0)
    private var linkA = IntArray(0)
    private var linkB = IntArray(0)
    private var pointX = FloatArray(0)
    private var pointY = FloatArray(0)
    private var visited = BooleanArray(0)
    private val crossing = IntArray(4)

    fun outline(frame: AppGlassPopupFrame, origin: Offset, density: Float): Outline {
        val before = previous
        // 轮廓只由几何决定；正文、材质及模糊变化不重建采样网格。
        if (before != null && before.rect == frame.rect && before.radius == frame.radius &&
            before.sourceRect == frame.sourceRect && before.surfaceBlend == frame.surfaceBlend &&
            previousOrigin == origin && previousDensity == density
        ) return outline
        previous = frame
        previousOrigin = origin
        previousDensity = density
        val source = frame.sourceRect
        val inner = frame.rect.deflate(frame.radius + frame.surfaceBlend)
        val contained = source == null || (!inner.isEmpty && source.left >= inner.left &&
            source.right <= inner.right && source.top >= inner.top && source.bottom <= inner.bottom)
        outline = if (contained) {
            Outline.Rounded(RoundRect(frame.rect.translate(-origin), CornerRadius(frame.radius)))
        } else {
            Outline.Generic(contour(frame, density).apply { translate(-origin) })
        }
        return outline
    }

    private fun connect(a: Int, b: Int) {
        if (linkA[a] < 0) linkA[a] = b else linkB[a] = b
        if (linkA[b] < 0) linkA[b] = a else linkB[b] = a
    }

    private fun crosses(id: Int, a: Float, b: Float, ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        if ((a < 0f) == (b < 0f)) return false
        val t = (a / (a - b)).coerceIn(0f, 1f)
        pointX[id] = ax + (bx - ax) * t
        pointY[id] = ay + (by - ay) * t
        return true
    }

    private fun contour(frame: AppGlassPopupFrame, density: Float): Path {
        val bounds = popupUnionBounds(frame.rect, frame.sourceRect!!).inflate(frame.surfaceBlend / 4f + density * 2f)
        val step = max(2f * density, max(bounds.width, bounds.height) / 96f)
        val nx = ceil(bounds.width / step).toInt().coerceAtLeast(2)
        val ny = ceil(bounds.height / step).toInt().coerceAtLeast(2)
        val dx = bounds.width / nx
        val dy = bounds.height / ny
        val stride = nx + 1
        val horizontal = nx * (ny + 1)
        val edges = horizontal + (nx + 1) * ny
        val samples = stride * (ny + 1)
        if (field.size < samples) field = FloatArray(samples)
        if (linkA.size < edges) {
            linkA = IntArray(edges)
            linkB = IntArray(edges)
            pointX = FloatArray(edges)
            pointY = FloatArray(edges)
            visited = BooleanArray(edges)
        }
        linkA.fill(-1, 0, edges)
        linkB.fill(-1, 0, edges)
        visited.fill(false, 0, edges)
        for (y in 0..ny) for (x in 0..nx) {
            field[y * stride + x] = popupSurfaceDistance(bounds.left + x * dx, bounds.top + y * dy, frame)
        }
        for (y in 0 until ny) for (x in 0 until nx) {
            val tl = field[y * stride + x]
            val tr = field[y * stride + x + 1]
            val bl = field[(y + 1) * stride + x]
            val br = field[(y + 1) * stride + x + 1]
            var count = 0
            val left = bounds.left + x * dx
            val top = bounds.top + y * dy
            val topEdge = y * nx + x
            val rightEdge = horizontal + y * stride + x + 1
            val bottomEdge = (y + 1) * nx + x
            val leftEdge = horizontal + y * stride + x
            if (crosses(topEdge, tl, tr, left, top, left + dx, top)) crossing[count++] = topEdge
            if (crosses(rightEdge, tr, br, left + dx, top, left + dx, top + dy)) crossing[count++] = rightEdge
            if (crosses(bottomEdge, bl, br, left, top + dy, left + dx, top + dy)) crossing[count++] = bottomEdge
            if (crosses(leftEdge, tl, bl, left, top, left, top + dy)) crossing[count++] = leftEdge
            if (count == 2) connect(crossing[0], crossing[1])
            if (count == 4) {
                val centerInside = (tl + tr + bl + br) < 0f
                if ((tl < 0f) == centerInside) {
                    connect(crossing[0], crossing[1])
                    connect(crossing[2], crossing[3])
                } else {
                    connect(crossing[0], crossing[3])
                    connect(crossing[1], crossing[2])
                }
            }
        }
        return Path().apply {
            for (start in 0 until edges) {
                if (visited[start] || linkA[start] < 0) continue
                moveTo(pointX[start], pointY[start])
                var previousEdge = -1
                var current = start
                var remaining = edges
                do {
                    visited[current] = true
                    val next = if (linkA[current] != previousEdge) linkA[current] else linkB[current]
                    if (next < 0) break
                    previousEdge = current
                    current = next
                    lineTo(pointX[current], pointY[current])
                } while (current != start && !visited[current] && --remaining > 0)
                if (current == start) close()
            }
        }
    }
}

/** 同一弹窗的降采样档独立缓存，避免混合采样时反复替换效果。 */
internal class PopupSurfaceLensCache {
    private val levels = mutableMapOf<Int, BackdropRenderEffectCache>()
    fun forScale(factor: Int): BackdropRenderEffectCache =
        levels.getOrPut(factor) { BackdropRenderEffectCache(15) }
}

internal fun BackdropEffectScope.popupSurfaceLens(
    frame: AppGlassPopupFrame,
    origin: Offset,
    height: Float,
    amount: Float,
    cache: PopupSurfaceLensCache,
) {
    if (height <= 0f || amount <= 0f || frame.rect.isEmpty) return
    padding = max(padding, amount)
    val factor = downscaleFactor.coerceAtLeast(1)
    val sf = factor.toFloat()
    val main = frame.rect.translate(-origin)
    val source = (frame.sourceRect ?: frame.rect).translate(-origin)
    val effectCache = cache.forScale(factor)
    effectCache[0] = -padding / sf
    effectCache[1] = -padding / sf
    effectCache[2] = main.center.x / sf
    effectCache[3] = main.center.y / sf
    effectCache[4] = main.width / (2f * sf)
    effectCache[5] = main.height / (2f * sf)
    effectCache[6] = source.center.x / sf
    effectCache[7] = source.center.y / sf
    effectCache[8] = source.width / (2f * sf)
    effectCache[9] = source.height / (2f * sf)
    effectCache[10] = frame.radius / sf
    effectCache[11] = source.minDimension / (2f * sf)
    effectCache[12] = if (frame.sourceRect == null) 0f else frame.surfaceBlend / sf
    effectCache[13] = height / sf
    effectCache[14] = -amount / sf
    if (effectCache.needsUpdate) {
        val input = renderEffect
        try {
            renderEffect = null
            runtimeShaderEffect("SettingsPopupUnionLens", PopupUnionLensShader, "content") {
                setFloatUniform("offset", -padding / sf, -padding / sf)
                setFloatUniform("mainRect", main.center.x / sf, main.center.y / sf, main.width / (2f * sf), main.height / (2f * sf))
                setFloatUniform("sourceRect", source.center.x / sf, source.center.y / sf, source.width / (2f * sf), source.height / (2f * sf))
                setFloatUniform("radii", frame.radius / sf, source.minDimension / (2f * sf))
                setFloatUniform("blend", if (frame.sourceRect == null) 0f else frame.surfaceBlend / sf)
                setFloatUniform("band", height / sf)
                setFloatUniform("amount", -amount / sf)
            }
            effectCache.store(renderEffect)
        } finally {
            renderEffect = input
        }
    }
    effectCache.applyTo(this)
}

private const val PopupUnionLensShader = """
uniform shader content;
uniform float2 offset;
uniform float4 mainRect;
uniform float4 sourceRect;
uniform float2 radii;
uniform float blend;
uniform float band;
uniform float amount;
float roundedDistance(float2 p, float4 rect, float radius) {
    float r = min(radius, min(rect.z, rect.w));
    float2 q = abs(p - rect.xy) - rect.zw + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float surfaceDistance(float2 p) {
    float a = roundedDistance(p, mainRect, radii.x);
    float b = roundedDistance(p, sourceRect, radii.y);
    float h = max(blend - abs(a - b), 0.0) / max(blend, 0.001);
    return min(a, b) - h * h * blend * 0.25;
}
half4 main(float2 coord) {
    float2 p = coord + offset;
    float d = surfaceDistance(p);
    if (-d >= band) return content.eval(coord);
    float2 grad = float2(surfaceDistance(p + float2(0.5, 0.0)) - surfaceDistance(p - float2(0.5, 0.0)),
                         surfaceDistance(p + float2(0.0, 0.5)) - surfaceDistance(p - float2(0.0, 0.5)));
    grad /= max(length(grad), 0.001);
    float t = clamp(1.0 + d / band, 0.0, 1.0);
    float shift = (1.0 - sqrt(max(0.0, 1.0 - t * t))) * amount;
    return content.eval(coord + shift * grad);
}
"""
