package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.blur.rememberContinuousBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Only the linked glass dock provides this owner; native and unrelated shells keep alpha 1. */
internal val LocalLinkedDockGlassShellAlpha = compositionLocalOf<(() -> Float)?> { null }

internal fun Modifier.linkedDockGlassShellVisibility(alphaProvider: (() -> Float)?): Modifier =
    if (alphaProvider == null) this else this
        .drawWithContent { if (alphaProvider() > 0f) drawContent() }
        .graphicsLayer { alpha = alphaProvider() }

/** 导航只发布几何读取口；动画值仍在绘制阶段读取，退出宿主时解除绑定。 */
internal class LinkedDockContact {
    var navigationBounds: ((Rect) -> Rect)? by mutableStateOf(null)
}
internal val LocalLinkedDockContact = compositionLocalOf<LinkedDockContact?> { null }

internal data class LinkedDockGlassFrame(
    val navigation: Rect,
    val playback: Rect?,
    val search: Rect?,
    val smoothing: Float,
    val contactMode: Boolean = false,
) {
    val bounds: Rect get() {
        var result = navigation
        for (rect in listOfNotNull(playback, search)) result = Rect(
            min(result.left, rect.left), min(result.top, rect.top),
            max(result.right, rect.right), max(result.bottom, rect.bottom),
        )
        return result
    }
}

/** Endpoint shells retain their own press feedback. The finite transition owns only its middle. */
internal fun linkedDockGlassOwnership(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val edge = min(p, 1f - p).div(0.08f).coerceIn(0f, 1f)
    return edge * edge * (3f - 2f * edge)
}

internal fun linkedDockGlassSmoothing(progress: Float, density: Float): Float =
    if (progress <= 0f || progress >= 1f) 0f
    else sin(progress * Math.PI).toFloat() * 20f * density

private fun capsuleDistance(x: Float, y: Float, rect: Rect): Float {
    val r = rect.minDimension / 2f
    val dx = abs(x - rect.center.x) - rect.width / 2f + r
    val dy = abs(y - rect.center.y) - rect.height / 2f + r
    return sqrt(max(dx, 0f) * max(dx, 0f) + max(dy, 0f) * max(dy, 0f)) + min(max(dx, dy), 0f) - r
}

private fun capsuleNormal(x: Float, y: Float, rect: Rect): Offset {
    val r = rect.minDimension / 2f
    val dx = x - rect.center.x
    val dy = y - rect.center.y
    val qx = abs(dx) - rect.width / 2f + r
    val qy = abs(dy) - rect.height / 2f + r
    val nx = max(qx, 0f)
    val ny = max(qy, 0f)
    val length = sqrt(nx * nx + ny * ny)
    return if (length > 0f) Offset(kotlin.math.sign(dx) * nx / length, kotlin.math.sign(dy) * ny / length)
    else if (qx > qy) Offset(kotlin.math.sign(dx), 0f) else Offset(0f, kotlin.math.sign(dy))
}

internal fun linkedDockGlassDistance(x: Float, y: Float, frame: LinkedDockGlassFrame): Float {
    fun join(a: Float, b: Float): Float {
        if (frame.smoothing <= 0.001f) return min(a, b)
        val h = max(frame.smoothing - abs(a - b), 0f) / frame.smoothing
        return min(a, b) - h * h * frame.smoothing * 0.25f
    }
    if (frame.contactMode && frame.search != null) {
        val a = capsuleDistance(x, y, frame.navigation)
        val b = capsuleDistance(x, y, frame.search)
        val an = capsuleNormal(x, y, frame.navigation)
        val bn = capsuleNormal(x, y, frame.search)
        val radius = frame.smoothing * (0.5f - 0.5f * (an.x * bn.x + an.y * bn.y)).coerceIn(0f, 1f)
        if (radius <= 0.000001f) return min(a, b)
        val h = (0.5f + 0.5f * (b - a) / radius).coerceIn(0f, 1f)
        return b + (a - b) * h - radius * h * (1f - h)
    }
    var distance = capsuleDistance(x, y, frame.navigation)
    frame.playback?.let { distance = join(distance, capsuleDistance(x, y, it)) }
    frame.search?.let { distance = join(distance, capsuleDistance(x, y, it)) }
    return distance
}

/** One cached contour drives coverage, shadow and rim; the shader evaluates the same field. */
private class LinkedDockGlassOutline {
    private var previous: LinkedDockGlassFrame? = null
    private var previousDensity = 0f
    private var outline: Outline = Outline.Rectangle(Rect.Zero)
    private var field = FloatArray(0)
    private var linkA = IntArray(0)
    private var linkB = IntArray(0)
    private var pointX = FloatArray(0)
    private var pointY = FloatArray(0)
    private var visited = BooleanArray(0)

    fun outline(frame: LinkedDockGlassFrame, density: Float): Outline {
        if (previous == frame && previousDensity == density) return outline
        previous = frame
        previousDensity = density
        outline = if (frame.smoothing <= 0.001f) {
            Outline.Generic(Path().apply {
                for (rect in listOfNotNull(frame.navigation, frame.playback, frame.search)) {
                    addRoundRect(RoundRect(rect, CornerRadius(rect.minDimension / 2f)))
                }
            })
        } else Outline.Generic(contour(frame, density))
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

    private fun contour(frame: LinkedDockGlassFrame, density: Float): Path {
        val bounds = frame.bounds.inflate(frame.smoothing / 2f + density * 2f)
        val step = max(density, max(bounds.width, bounds.height) / 128f)
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
            field[y * stride + x] = linkedDockGlassDistance(bounds.left + x * dx, bounds.top + y * dy, frame)
        }
        val crossing = IntArray(4)
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

private class LinkedDockGlassShape(private val outline: Outline) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline = outline
}

/** No private animation clock, sensor or capture source: all motion reads the dock's live springs. */
@Composable
internal fun LinkedDockGlassSurface(
    backdrop: Backdrop,
    containerColor: Color,
    tuning: LiquidGlassTuning,
    frameProvider: () -> LinkedDockGlassFrame,
    ownershipProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val isDark = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val cache = remember { LinkedDockGlassOutline() }
    // Supply an immutable outline snapshot to RenderNode; a mutable Shape identity can
    // otherwise keep the previous clip/shadow even after the field has changed.
    val shapeProvider = remember(cache, frameProvider, density) {
        { LinkedDockGlassShape(cache.outline(frameProvider(), density.density)) }
    }
    val source = rememberContinuousBackdrop(backdrop)
    val effects = remember(tuning, frameProvider) {
        val block: BackdropEffectScope.() -> Unit = {
            val scale = tuning.contentDistortionScale.coerceIn(0f, 1.8f)
            val amount = tuning.refractionAmount.dp.toPx() * scale
            val height = tuning.refractionHeight.dp.toPx() * scale
            if (height > 0f && amount > 0f) padding = max(padding, amount)
            val frost = tuning.backdropBlurRadius.dp.toPx()
            blur(frost, frost)
            colorControls(brightness = 0f, saturation = tuning.saturation)
            if (height > 0f && amount > 0f) linkedDockGlassLens(frameProvider(), height, amount)
        }
        block
    }
    Box(modifier.drawWithContent {
        // A settled or hidden dock does not evaluate the union renderer or sample its backdrop.
        if (ownershipProvider() > 0f) drawContent()
    }.graphicsLayer {
        alpha = ownershipProvider()
        val ownsSurface = ownershipProvider() > 0f
        shape = if (ownsSurface) shapeProvider() else RectangleShape
        clip = false
        shadowElevation = if (ownsSurface) 10.dp.toPx() else 0f
        ambientShadowColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.1f)
        spotShadowColor = ambientShadowColor
    }.drawBackdrop(
        backdrop = source,
        shape = shapeProvider,
        effects = effects,
        highlight = null,
        onDrawSurface = {
            drawRect(containerColor.copy(alpha = tuning.surfaceAlpha))
            if (tuning.contentReadabilityScrimAlpha > 0f) drawRect(
                (if (isDark) Color.Black else Color.White).copy(alpha = tuning.contentReadabilityScrimAlpha),
            )
        },
        onDrawFront = {
            val frame = frameProvider()
            drawOutline(
                outline = cache.outline(frame, density.density),
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = if (isDark) 0.32f else 0.5f),
                        Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.12f)),
                    startY = frame.bounds.top,
                    endY = frame.bounds.bottom,
                ),
                style = Stroke(0.7.dp.toPx()),
            )
        },
    ))
}

private fun BackdropEffectScope.linkedDockGlassLens(frame: LinkedDockGlassFrame, height: Float, amount: Float) {
    val sf = downscaleFactor.coerceAtLeast(1).toFloat()
    runtimeShaderEffect("LinkedDockUnionLens", LinkedDockUnionLensShader, "content") {
        fun rect(name: String, rect: Rect) = setFloatUniform(name,
            rect.center.x / sf, rect.center.y / sf, rect.width / (2f * sf), rect.height / (2f * sf))
        rect("navigation", frame.navigation)
        rect("playback", frame.playback ?: frame.navigation)
        rect("search", frame.search ?: frame.navigation)
        setFloatUniform("members", if (frame.playback != null) 1f else 0f, if (frame.search != null) 1f else 0f)
        setFloatUniform("offset", -padding / sf, -padding / sf)
        setFloatUniform("smoothing", frame.smoothing / sf)
        setFloatUniform("contactMode", if (frame.contactMode) 1f else 0f)
        setFloatUniform("band", height / sf)
        setFloatUniform("amount", -amount / sf)
    }
}

private const val LinkedDockUnionLensShader = """
uniform shader content;
uniform float4 navigation;
uniform float4 playback;
uniform float4 search;
uniform float2 members;
uniform float2 offset;
uniform float smoothing;
uniform float contactMode;
uniform float band;
uniform float amount;
float capsule(float2 p, float4 rect) {
    float r = min(rect.z, rect.w);
    float2 q = abs(p - rect.xy) - rect.zw + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float join(float a, float b) {
    float h = max(smoothing - abs(a - b), 0.0) / max(smoothing, 0.001);
    return min(a, b) - h * h * smoothing * 0.25;
}
float2 capsuleNormal(float2 p, float4 rect) {
    float r = min(rect.z, rect.w);
    float2 d = p - rect.xy;
    float2 q = abs(d) - rect.zw + r;
    float2 n = max(q, 0.0);
    float len = length(n);
    if (len > 0.0) return sign(d) * n / len;
    return q.x > q.y ? float2(sign(d.x), 0.0) : float2(0.0, sign(d.y));
}
float field(float2 p) {
    if (contactMode > 0.5 && members.y > 0.5) {
        float a = capsule(p, navigation);
        float b = capsule(p, search);
        float k = smoothing * clamp(0.5 - 0.5 * dot(capsuleNormal(p, navigation), capsuleNormal(p, search)), 0.0, 1.0);
        if (k <= 0.000001) return min(a, b);
        float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
        return mix(b, a, h) - k * h * (1.0 - h);
    }
    float d = capsule(p, navigation);
    if (members.x > 0.5) d = join(d, capsule(p, playback));
    if (members.y > 0.5) d = join(d, capsule(p, search));
    return d;
}
half4 main(float2 coord) {
    float2 p = coord + offset;
    float d = field(p);
    if (-d >= band) return content.eval(coord);
    float2 grad = float2(field(p + float2(0.5, 0.0)) - field(p - float2(0.5, 0.0)),
                         field(p + float2(0.0, 0.5)) - field(p - float2(0.0, 0.5)));
    grad /= max(length(grad), 0.001);
    float t = clamp(1.0 + d / band, 0.0, 1.0);
    float shift = (1.0 - sqrt(max(0.0, 1.0 - t * t))) * amount;
    return content.eval(coord + shift * grad);
}
"""
