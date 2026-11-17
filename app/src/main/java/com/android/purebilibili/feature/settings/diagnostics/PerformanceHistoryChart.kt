package com.android.purebilibili.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.performance.performanceNumber
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

internal data class PerformanceChartPoint(val timeMs: Long, val label: String, val detail: String, val value: Double?, val formatted: String)

/** 每次只绘制一个量纲；未知和溢出值留空，不按零值或连续线段处理。 */
@Composable
internal fun PerformanceHistoryChart(
    title: String,
    unit: String,
    points: List<PerformanceChartPoint>,
    modifier: Modifier = Modifier,
    bars: Boolean = false,
) {
    val dark = AppSurfaceTokens.surface().luminance() < 0.5f
    val surface = if (dark) Color(0xFF1A1A19) else Color(0xFFFCFCFB)
    val series = if (dark) Color(0xFF3987E5) else Color(0xFF2A78D6)
    val grid = if (dark) Color(0xFF42423F) else Color(0xFFDEDEDA)
    val ink = if (dark) Color(0xFFC3C2B7) else Color(0xFF52514E)
    val textStyle = MaterialTheme.typography.labelSmall.copy(color = ink)
    val measurer = rememberTextMeasurer()
    val maximum = points.mapNotNull { it.value }.maxOrNull()
    val top = remember(maximum) { performanceChartCeiling(maximum ?: 0.0) }
    val ticks = remember(top) { (0..3).map { top * it / 3 } }
    val labels = remember(ticks) { ticks.map { performanceNumber(it) } }
    val tickWidth = labels.maxOf { measurer.measure(it, textStyle).size.width }.toFloat()
    val density = LocalDensity.current
    val left = tickWidth + with(density) { 12.dp.toPx() }
    val right = with(density) { 12.dp.toPx() }
    val upper = with(density) { 12.dp.toPx() }
    val axisTextHeight = measurer.measure("01/01 12:00", textStyle).size.height
    val lower = axisTextHeight * (if (bars) 1 else 2) + with(density) { 16.dp.toPx() }
    var selected by remember(points) { mutableIntStateOf(points.lastIndex.coerceAtLeast(0)) }
    fun xFraction(index: Int): Float = when {
        bars -> (index + 0.5f) / points.size
        points.first().timeMs == points.last().timeMs -> 0.5f
        else -> ((points[index].timeMs - points.first().timeMs).toDouble() /
            (points.last().timeMs - points.first().timeMs)).toFloat()
    }
    Column(modifier.background(surface, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText("$title · $unit", color = ink, style = MaterialTheme.typography.titleSmall)
        if (points.isEmpty() || maximum == null) {
            AppText("暂无可绘制的数值；未采集和 >500ms 的 P95 不绘成零值。", color = ink)
        } else {
            val selectedPoint = points[selected]
            Canvas(
                Modifier.fillMaxWidth().height(200.dp)
                    .semantics {
                        contentDescription = title
                        stateDescription = "${selectedPoint.detail}，${selectedPoint.formatted}"
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionLeft, Key.DirectionRight)) {
                            selected = (selected + if (event.key == Key.DirectionLeft) -1 else 1).coerceIn(points.indices)
                            true
                        } else false
                    }.focusable()
                    .pointerInput(points, left, right, bars) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press || event.type == PointerEventType.Move) {
                                    val pointer = event.changes.first().position.x
                                    selected = points.indices.minBy { abs(left + xFraction(it) * (size.width - left - right) - pointer) }
                                }
                            }
                        }
                    },
            ) {
                val bottom = size.height - lower
                val width = (size.width - left - right).coerceAtLeast(1f)
                val height = (bottom - upper).coerceAtLeast(1f)
                fun position(index: Int, value: Double) = Offset(left + xFraction(index) * width, bottom - (value / top * height).toFloat())
                ticks.forEachIndexed { index, tick ->
                    val y = bottom - (tick / top * height).toFloat()
                    drawLine(grid, Offset(left, y), Offset(size.width - right, y), 1.dp.toPx())
                    val text = measurer.measure(labels[index], textStyle)
                    drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y - text.size.height / 2f))
                }
                drawLine(grid, Offset(left, upper), Offset(left, bottom), 1.dp.toPx())
                if (bars) {
                    points.forEachIndexed { index, point -> point.value?.let { value ->
                        val p = position(index, value)
                        val half = minOf(12.dp.toPx(), width / points.size / 4)
                        val radius = minOf(4.dp.toPx(), (bottom - p.y) / 2)
                        val path = Path().apply {
                            moveTo(p.x - half, bottom)
                            lineTo(p.x - half, p.y + radius)
                            quadraticBezierTo(p.x - half, p.y, p.x - half + radius, p.y)
                            lineTo(p.x + half - radius, p.y)
                            quadraticBezierTo(p.x + half, p.y, p.x + half, p.y + radius)
                            lineTo(p.x + half, bottom)
                            close()
                        }
                        if (index == 0) drawPath(path, series, style = Stroke(2.dp.toPx())) else drawPath(path, series)
                        if (value == 0.0) drawLine(series, Offset(p.x - half, bottom), Offset(p.x + half, bottom), 2.dp.toPx())
                    } }
                } else {
                    var previous: Offset? = null
                    points.forEachIndexed { index, point ->
                        val p = point.value?.let { position(index, it) }
                        if (p != null) {
                            previous?.let { drawLine(series, it, p, 2.dp.toPx(), StrokeCap.Round) }
                            drawCircle(surface, 6.dp.toPx(), p)
                            drawCircle(series, 4.dp.toPx(), p)
                        }
                        previous = p
                    }
                }
                selectedPoint.value?.let { value ->
                    val p = position(selected, value)
                    drawLine(grid, Offset(p.x, upper), Offset(p.x, bottom), 1.dp.toPx())
                    drawCircle(surface, 7.dp.toPx(), p)
                    drawCircle(series, 5.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                }
                val axisIndices = if (bars) points.indices.toList() else listOf(0, points.lastIndex).distinct()
                var previousLabelEnd = Float.NEGATIVE_INFINITY
                axisIndices.forEach { index ->
                    val text = measurer.measure(points[index].label, textStyle)
                    val x = (left + xFraction(index) * width - text.size.width / 2f).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                    val nextLine = !bars && x < previousLabelEnd + 4.dp.toPx()
                    drawText(text, topLeft = Offset(x, bottom + 6.dp.toPx() + if (nextLine) axisTextHeight.toFloat() + 2.dp.toPx() else 0f))
                    previousLabelEnd = x + text.size.width
                }
            }
            if (bars) AppText("空心柱：基准　实心柱：当前", color = ink, style = MaterialTheme.typography.labelSmall)
            AppText("${selectedPoint.detail}：${selectedPoint.formatted}", color = ink, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AppTextButton(onClick = { selected-- }, enabled = selected > 0) { AppText("上一条") }
                AppTextButton(onClick = { selected++ }, enabled = selected < points.lastIndex) { AppText("下一条") }
            }
        }
    }
}

internal fun performanceChartCeiling(maximum: Double): Double {
    if (maximum == 0.0) return 1.0
    val step = maximum / 3
    val magnitude = 10.0.pow(floor(log10(step)))
    val multiple = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= step / magnitude }
    return magnitude * multiple * 3
}
