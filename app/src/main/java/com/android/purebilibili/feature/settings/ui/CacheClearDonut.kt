package com.android.purebilibili.feature.settings

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import com.android.purebilibili.core.util.CacheClearTarget
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val CacheClearChartColors = listOf(
    Color(0xFF39B94A), Color(0xFFEDAB16), Color(0xFF397BF0),
    Color(0xFF9360DE), Color(0xFFF28B3C), Color(0xFF2BA6A4)
)
private val CacheDonutEasing = com.android.purebilibili.core.ui.motion.AppMotionEasing.EaseOutQuint

@Composable
internal fun CacheClearUsageDonut(
    segments: List<CacheClearDonutSegment>,
    centerSize: String,
    loading: Boolean,
    complete: Boolean,
    active: Boolean,
    onSegmentClick: (CacheClearTarget) -> Unit,
    onSegmentPress: (CacheClearTarget?) -> Unit,
    modifier: Modifier = Modifier
) {
    val gate = rememberDecorativeAnimationGate(respectComponentMotion = false)
    val reduceMotion = LocalSystemReduceMotion.current
    val phase = remember { Animatable(0f) }
    val animateParticles = active && gate.active && !loading && !complete && segments.any { it.sweepAngle > 0f }
    // 协程退出后不再请求帧；恢复沿当前相位继续，遮挡时也由 active 显式暂停。
    LaunchedEffect(animateParticles) {
        if (!animateParticles) return@LaunchedEffect
        while (isActive) {
            phase.animateTo(1f, tween(((1f - phase.value) * 10000).toInt().coerceAtLeast(1), easing = androidx.compose.animation.core.LinearEasing))
            phase.snapTo(0f)
        }
    }
    var pressed by remember { mutableStateOf<CacheClearTarget?>(null) }
    LaunchedEffect(active) { if (!active) { pressed = null; onSegmentPress(null) } }
    val latestSegments by rememberUpdatedState(segments)
    val latestClick by rememberUpdatedState(onSegmentClick)
    val latestPress by rememberUpdatedState(onSegmentPress)
    val motion = if (reduceMotion || !active) snap<Float>() else tween(650, easing = CacheDonutEasing)
    val sweeps = segments.map { animateFloatAsState(it.sweepAngle, motion, label = "cacheSweep-${it.target}") }
    val starts = segments.map { animateFloatAsState(it.startAngle, motion, label = "cacheStart-${it.target}") }
    val expansion = segments.map {
        animateFloatAsState(if (pressed == it.target) 1f else 0f,
            if (reduceMotion || !active) snap() else tween(200, easing = CacheDonutEasing), label = "cachePress-${it.target}")
    }
    val finished = animateFloatAsState(if (complete) 1f else 0f, motion, label = "cacheEmptyCompletion")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
    }
    Box(
        modifier = modifier.size(220.dp).then(gate.modifier).semantics {
            contentDescription = when {
                loading -> "正在统计缓存"
                complete -> "缓存已清空"
                else -> "已选缓存 $centerSize；可点击扇区或分类行修改选择"
            }
        }.pointerInput(active, loading, complete) {
            if (!active || loading || complete) return@pointerInput
            detectTapGestures(onPress = { offset ->
                val outer = minOf(86.dp.toPx(), size.width / 2f - 10.dp.toPx())
                val target = resolveCacheClearDonutHitTarget(latestSegments, offset.x - size.width / 2f,
                    offset.y - size.height / 2f, outer - 38.dp.toPx(), outer + 9.dp.toPx())
                pressed = target
                latestPress(target)
                try { if (tryAwaitRelease() && target != null) latestClick(target) }
                finally { pressed = null; latestPress(null) }
            })
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val outer = minOf(86.dp.toPx(), size.minDimension / 2f - 10.dp.toPx())
            val baseThickness = 38.dp.toPx()
            val t = finished.value
            val thickness = baseThickness + (10.dp.toPx() - baseThickness) * t
            val radius = outer - thickness / 2f
            drawCircle(track.copy(alpha = .32f), radius, style = Stroke(thickness))
            segments.forEachIndexed { index, segment ->
                val sweep = sweeps[index].value
                if (sweep <= .1f || t >= 1f) return@forEachIndexed
                val start = starts[index].value
                val expand = expansion[index].value * 9.dp.toPx()
                val stroke = baseThickness + expand
                val arcRadius = outer + expand - stroke / 2f
                val color = CacheClearChartColors[segment.colorIndex % CacheClearChartColors.size]
                val gap = if (sweep < 359f) minOf(2f, sweep * .2f) else 0f
                drawArc(
                    brush = Brush.radialGradient(listOf(blendCacheColor(color), color), center, outer + expand),
                    startAngle = start + gap / 2, sweepAngle = sweep - gap, useCenter = false,
                    topLeft = center - Offset(arcRadius, arcRadius), size = Size(arcRadius * 2, arcRadius * 2),
                    alpha = 1f - t, style = Stroke(stroke, cap = StrokeCap.Butt)
                )
                val middleAngle = (start + sweep / 2f) * PI.toFloat() / 180f
                val textPosition = center + Offset(cos(middleAngle), sin(middleAngle)) * arcRadius
                // 分类图标保持在扇区内；标签周围留白，不让漂移图案穿过读数。
                if (animateParticles) {
                    val ringClip = Path().apply {
                        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                        addOval(androidx.compose.ui.geometry.Rect(center - Offset(outer + expand, outer + expand), Size((outer + expand) * 2, (outer + expand) * 2)))
                        addOval(androidx.compose.ui.geometry.Rect(center - Offset(outer - baseThickness, outer - baseThickness), Size((outer - baseThickness) * 2, (outer - baseThickness) * 2)))
                    }
                    val wedge = Path().apply {
                        moveTo(center.x, center.y)
                        arcTo(androidx.compose.ui.geometry.Rect(center - Offset(outer + expand, outer + expand), Size((outer + expand) * 2, (outer + expand) * 2)), start + gap / 2, sweep - gap, false)
                        close()
                    }
                    clipPath(ringClip) { clipPath(wedge) {
                        val count = (sweep / 12f).toInt().coerceIn(1, 30)
                        repeat(count) { particle ->
                            val angle = start + gap + (sweep - gap * 2) * (particle + .5f) / count
                            val radians = angle * PI.toFloat() / 180f
                            val drift = (phase.value * (1f + particle % 3 * .17f) + particle * .381966f) % 1f
                            val distance = outer - baseThickness - 6.dp.toPx() + (baseThickness + 12.dp.toPx()) * drift
                            val position = center + Offset(cos(radians), sin(radians)) * distance
                            val nearLabel = segment.fraction > .05f && segment.fraction < 1f && (position - textPosition).getDistance() < 20.dp.toPx()
                            if (!nearLabel) translate(position.x - 4.dp.toPx(), position.y - 4.dp.toPx()) {
                                drawCacheCategoryGlyph(segment.target, Color.White.copy(alpha = .22f * sin(PI.toFloat() * drift)), 8.dp.toPx())
                            }
                        }
                    } }
                }
                if (segment.fraction > .05f && segment.fraction < 1f) {
                    labelPaint.textSize = (if (segment.fraction < .08f) 12.sp else 15.sp).toPx()
                    labelPaint.alpha = ((1f - t) * 255).toInt()
                    drawIntoCanvas { it.nativeCanvas.drawText(segment.chartPercentLabel, textPosition.x,
                        textPosition.y - (labelPaint.ascent() + labelPaint.descent()) / 2, labelPaint) }
                }
            }
            if (t > 0f) {
                val green = Brush.linearGradient(listOf(Color(0xFF6ED556), Color(0xFF41BA71)))
                drawCircle(green, radius, alpha = t, style = Stroke(thickness, cap = StrokeCap.Round))
                val check = Path().apply {
                    moveTo(center.x - outer * .28f, center.y)
                    lineTo(center.x - outer * .08f, center.y + outer * .2f)
                    lineTo(center.x + outer * .32f, center.y - outer * .23f)
                }
                drawPath(check, green, alpha = t, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                if (!reduceMotion && t < 1f) repeat(25) { i ->
                    val angle = (i * 2f * PI / 25).toFloat()
                    val r = outer * (.8f + t * .6f)
                    val position = center + Offset(cos(angle), sin(angle)) * r
                    drawCircle(Color(0xFF41BA71), (1f - t) * 2.dp.toPx(), position, alpha = t * (1f - t) * 2)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = 1f - finished.value }) {
            val parts = centerSize.split(' ', limit = 2)
            AppText(if (loading) "—" else parts[0], fontSize = if (parts[0].length > 4) 24.sp else 32.sp,
                fontWeight = FontWeight.Bold, color = AppContentColors.primaryText())
            AppText(if (loading) "统计中" else parts.getOrElse(1) { "" },
                style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        }
    }
}

private fun blendCacheColor(color: Color): Color = Color(
    red = color.red + (1f - color.red) * .14f,
    green = color.green + (1f - color.green) * .14f,
    blue = color.blue + (1f - color.blue) * .14f
)

/** 统一 24 格线性语义图形，同时用于分类标识和环内装饰。 */
internal fun DrawScope.drawCacheCategoryGlyph(target: CacheClearTarget, color: Color, extent: Float) {
    val unit = extent / 24f
    val path = Path()
    fun move(x: Float, y: Float) = path.moveTo(x * unit, y * unit)
    fun line(x: Float, y: Float) = path.lineTo(x * unit, y * unit)
    when (target) {
        CacheClearTarget.PLAYBACK_QUALITY -> { move(8f, 5f); line(19f, 12f); line(8f, 19f); path.close() }
        CacheClearTarget.NETWORK -> {
            move(4f, 8f); line(4f, 5f); line(20f, 5f); line(20f, 8f)
            move(12f, 5f); line(12f, 19f); move(4f, 16f); line(4f, 19f); line(20f, 19f); line(20f, 16f)
        }
        CacheClearTarget.IMAGE_PREVIEW -> {
            move(4f, 4f); line(20f, 4f); line(20f, 20f); line(4f, 20f); path.close()
            move(4f, 17f); line(10f, 11f); line(14f, 15f); line(17f, 12f); line(20f, 15f)
            drawCircle(color, 1.5f * unit, Offset(15f * unit, 8f * unit))
        }
        CacheClearTarget.SUBTITLE_DANMAKU -> {
            move(4f, 5f); line(20f, 5f); line(20f, 17f); line(11f, 17f); line(6f, 21f); line(6f, 17f); line(4f, 17f); path.close()
            move(8f, 10f); line(16f, 10f); move(8f, 13f); line(14f, 13f)
        }
        CacheClearTarget.TEMP_FILES_AND_LOGS -> {
            move(6f, 3f); line(14f, 3f); line(19f, 8f); line(19f, 21f); line(6f, 21f); path.close()
            move(14f, 3f); line(14f, 8f); line(19f, 8f); move(9f, 12f); line(16f, 12f); move(9f, 16f); line(14f, 16f)
        }
        CacheClearTarget.APP_METADATA -> {
            drawCircle(color, 4f * unit, Offset(12f * unit, 7f * unit), style = Stroke(1.8f * unit))
            path.arcTo(androidx.compose.ui.geometry.Rect(4f * unit, 13f * unit, 20f * unit, 25f * unit), 180f, 180f, false)
        }
    }
    drawPath(path, color, style = Stroke(1.8f * unit, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}
