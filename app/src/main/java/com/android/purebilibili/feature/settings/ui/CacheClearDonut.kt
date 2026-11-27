package com.android.purebilibili.feature.settings

import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import com.android.purebilibili.core.util.CacheClearTarget
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

internal val CacheClearLightPalette = listOf(
    Color(0xFF61C752),
    Color(0xFFE3B727),
    Color(0xFF58A8ED),
    Color(0xFF7F79F3),
    Color(0xFFF28C39),
    Color(0xFF34B3C1),
    Color(0xFF327FE5),
    Color(0xFFE05356),
    Color(0xFF9ED448)
)

internal val CacheClearDarkPalette = listOf(
    Color(0xFF3DC23F),
    Color(0xFFDEAC1F),
    Color(0xFF3C78EC),
    Color(0xFF875CE5),
    Color(0xFFF28C39),
    Color(0xFF2EB8C6),
    Color(0xFF529FFF),
    Color(0xFFF34C44),
    Color(0xFF8FCF39)
)

internal val CacheClearChartColors = CacheClearLightPalette

@Composable
internal fun rememberCacheClearPalette(): List<Color> =
    if (isAppInDarkTheme()) CacheClearDarkPalette else CacheClearLightPalette

internal fun resolveShuffledCacheColorIndices(
    count: Int,
    paletteSize: Int,
    current: List<Int>
): List<Int> {
    if (count <= 0 || paletteSize <= 0) return emptyList()
    val pool = (0 until paletteSize).toMutableList()
    repeat(3) {
        pool.shuffle()
        val candidate = List(count) { i -> pool[i % pool.size] }
        if (candidate != current) return candidate
    }
    return List(count) { i -> (current.getOrElse(i) { i } + 1) % paletteSize }
}

internal val CacheClearCompleteGradientColors = listOf(
    Color(0xFF6ED556),
    Color(0xFF41BA71)
)

private val CacheDonutEasing = AppMotionEasing.EaseOutQuint

private fun computeLoadingArcSegments(t: Float, out: FloatArray) {
    out[0] = maxOf(0f, 1520f * t / 5400f - 20f)
    out[1] = 1520f * t / 5400f
    for (i in 0 until 4) {
        val headInput = ((t - i * 1350f) / 667f).coerceIn(0f, 1f)
        val tailInput = ((t - (667f + i * 1350f)) / 667f).coerceIn(0f, 1f)
        out[1] += FastOutSlowInEasing.transform(headInput) * 250f
        out[0] += FastOutSlowInEasing.transform(tailInput) * 250f
    }
}

private fun lerpFloat(a: Float, b: Float, f: Float): Float = a + (b - a) * f

@Composable
internal fun CacheClearUsageDonut(
    segments: List<CacheClearDonutSegment>,
    centerSize: String,
    centerCaption: String,
    loading: Boolean,
    complete: Boolean,
    active: Boolean,
    onSegmentClick: (CacheClearTarget) -> Unit,
    onSegmentPress: (CacheClearTarget?) -> Unit,
    modifier: Modifier = Modifier,
    donutSize: Dp = 148.dp,
    ringThickness: Dp = 24.dp,
    compactText: Boolean = donutSize < 160.dp,
    palette: List<Color> = rememberCacheClearPalette(),
    colorIndices: List<Int> = emptyList(),
) {
    val gate = rememberDecorativeAnimationGate(respectComponentMotion = false)
    val reduceMotion = LocalSystemReduceMotion.current
    val hasSegments = segments.any { it.sweepAngle > 0f }

    // 加载期多段旋转弧线到扇区展开的过渡权重（750ms EaseOutQuint）
    val loadingAnim = remember { Animatable(if (reduceMotion) 0f else 1f) }
    LaunchedEffect(loading, hasSegments, reduceMotion) {
        if (reduceMotion) {
            loadingAnim.snapTo(0f)
        } else if (loading) {
            loadingAnim.snapTo(1f)
        } else if (hasSegments) {
            loadingAnim.animateTo(0f, tween(750, easing = CacheDonutEasing))
        } else {
            loadingAnim.animateTo(0f, tween(350, easing = CacheDonutEasing))
        }
    }

    // 统一帧时钟：初始偏移确保首帧即有饱满旋转弧段；仅在可见且未减动态时推进。
    var frameClockMs by remember { mutableFloatStateOf(900f) }
    var particleTime by remember { mutableFloatStateOf(0f) }
    val needsContinuousClock = active && gate.active && !reduceMotion && !complete &&
        (loading || loadingAnim.value > 0.001f || hasSegments)
    LaunchedEffect(needsContinuousClock, loading) {
        if (!needsContinuousClock) return@LaunchedEffect
        var lastFrameMs = SystemClock.uptimeMillis()
        while (true) {
            withFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L)
                lastFrameMs = frameMs
                if (loading || loadingAnim.value > 0.001f) {
                    frameClockMs += delta * 0.6f
                }
                if (hasSegments && !loading) {
                    particleTime += delta / 10000f
                }
            }
        }
    }

    var pressed by remember { mutableStateOf<CacheClearTarget?>(null) }
    LaunchedEffect(active) {
        if (!active) {
            pressed = null
            onSegmentPress(null)
        }
    }
    val latestSegments by rememberUpdatedState(segments)
    val latestClick by rememberUpdatedState(onSegmentClick)
    val latestPress by rememberUpdatedState(onSegmentPress)
    val haptics = rememberAppHapticFeedback(AppHapticScene.MENUS)
    val sectorMotion = if (reduceMotion || !active) snap<Float>() else tween(650, easing = CacheDonutEasing)
    val labelMotion = if (reduceMotion || !active) snap<Float>() else tween(150, easing = EaseOut)
    val sweeps = segments.map { animateFloatAsState(it.sweepAngle, sectorMotion, label = "cacheSweep-${it.target}") }
    val starts = segments.map { animateFloatAsState(it.startAngle, sectorMotion, label = "cacheStart-${it.target}") }
    val expansion = segments.map {
        animateFloatAsState(
            if (pressed == it.target) 1f else 0f,
            if (reduceMotion || !active) snap() else tween(200, easing = CacheDonutEasing),
            label = "cachePress-${it.target}"
        )
    }
    val labelAlphas = segments.map {
        val target = if (!loading && it.fraction > 0.05f && it.fraction < 1f) 1f else 0f
        animateFloatAsState(target, labelMotion, label = "cacheLabelAlpha-${it.target}")
    }
    val labelScales = segments.map {
        val target = if (it.fraction < 0.08f || it.fraction >= 1f) 0.85f else 1f
        animateFloatAsState(target, labelMotion, label = "cacheLabelScale-${it.target}")
    }
    val segmentColors = segments.map { segment ->
        val resolvedIdx = colorIndices.getOrElse(segment.colorIndex) { segment.colorIndex }
        val targetColor = palette[resolvedIdx % palette.size]
        animateColorAsState(
            targetValue = targetColor,
            animationSpec = if (reduceMotion || !active) snap() else tween(350, easing = CacheDonutEasing),
            label = "cacheColor-${segment.target}"
        )
    }
    val finished = animateFloatAsState(
        if (complete) 1f else 0f,
        if (reduceMotion) snap() else tween(650, easing = CacheDonutEasing),
        label = "cacheEmptyCompletion"
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val unselectedLabelColor = AppContentColors.secondaryText()
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
    }
    val minMaxSegTmp = remember { FloatArray(2) }
    val sectorSegTmp = remember { FloatArray(2) }
    val ringClipPath = remember { Path() }
    val wedgePath = remember { Path() }
    val glyphPath = remember { Path() }
    val checkPath = remember { Path() }

    Box(
        modifier = modifier
            .size(donutSize)
            .then(gate.modifier)
            .semantics {
                contentDescription = when {
                    loading -> "正在统计缓存"
                    complete -> "缓存清理完成"
                    else -> "$centerCaption $centerSize；可点击扇区或分类行修改选择"
                }
            }
            .pointerInput(active, loading, complete, donutSize, ringThickness) {
                if (!active || loading || complete) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val outer = minOf(size.width, size.height) / 2f - 8.dp.toPx()
                    val inner = (outer - ringThickness.toPx() - 6.dp.toPx()).coerceAtLeast(0f)
                    val hitOuter = outer + 10.dp.toPx()
                    fun resolveTarget(pos: Offset): CacheClearTarget? = resolveCacheClearDonutHitTarget(
                        segments = latestSegments,
                        dx = pos.x - size.width / 2f,
                        dy = pos.y - size.height / 2f,
                        innerRadius = inner,
                        outerRadius = hitOuter
                    )
                    var currentTarget = resolveTarget(down.position)
                    if (currentTarget != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    pressed = currentTarget
                    latestPress(currentTarget)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                val releaseTarget = resolveTarget(change.position)
                                if (releaseTarget != null) {
                                    if (releaseTarget != currentTarget) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    latestClick(releaseTarget)
                                }
                                break
                            }
                            if (!change.pressed) break
                            val movedTarget = resolveTarget(change.position)
                            if (movedTarget != currentTarget) {
                                currentTarget = movedTarget
                                pressed = movedTarget
                                latestPress(movedTarget)
                                if (movedTarget != null) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    } finally {
                        pressed = null
                        latestPress(null)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val outer = size.minDimension / 2f - 8.dp.toPx()
            val baseThickness = ringThickness.toPx()
            val loadingWeight = loadingAnim.value.coerceIn(0f, 1f)
            val completeWeight = finished.value.coerceIn(0f, 1f)
            val thinThickness = minOf(baseThickness, 9.dp.toPx())
            val thickness = lerpFloat(baseThickness, thinThickness, maxOf(loadingWeight, completeWeight))
            val trackRadius = outer - thickness / 2f

            // 加载背景轨道环
            if (loadingWeight > 0.001f) {
                drawCircle(
                    color = track.copy(alpha = 0.28f * loadingWeight),
                    radius = trackRadius,
                    style = Stroke(thickness)
                )
            } else {
                drawCircle(track.copy(alpha = 0.20f), trackRadius, style = Stroke(baseThickness * 0.72f))
            }

            computeLoadingArcSegments(frameClockMs % 5400f, minMaxSegTmp)
            val minAngle = minMaxSegTmp[0]
            val maxAngle = minMaxSegTmp[1]
            val sqrt2 = 1.4142135f
            val glyphSize = if (compactText) 6.5.dp.toPx() else 8.dp.toPx()
            val labelClearance = if (compactText) 26.dp.toPx() else 36.dp.toPx()

            val nonZeroCount = segments.count { it.sweepAngle > 0.1f }
            val separatorAngle = if (nonZeroCount < 2) 0f else 2f

            segments.forEachIndexed { index, segment ->
                val rawSweep = sweeps[index].value
                if (rawSweep <= 0.1f && loadingWeight <= 0.001f) return@forEachIndexed
                if (completeWeight >= 1f) return@forEachIndexed

                computeLoadingArcSegments((frameClockMs + index * 80f) % 5400f, sectorSegTmp)
                val loadFrom = minOf(maxOf(sectorSegTmp[0], minAngle), maxAngle)
                val loadTo = minOf(maxOf(sectorSegTmp[1], minAngle), maxAngle)
                if (loadingWeight >= 1f && loadFrom >= loadTo) return@forEachIndexed

                val loadCenter = (loadFrom + loadTo) / 2f
                val loadHalfSweep = abs(loadTo - loadFrom) / 2f
                val sectorHalfSweep = rawSweep / 2f
                val sectorCenter = starts[index].value + sectorHalfSweep

                val angleCenter = when {
                    loadingWeight <= 0.001f -> sectorCenter
                    loadingWeight >= 0.999f -> loadCenter
                    else -> lerpFloat(
                        sectorCenter + floor(maxAngle / 360f) * 360f,
                        loadCenter,
                        loadingWeight
                    )
                }
                val halfSweep = lerpFloat(sectorHalfSweep, loadHalfSweep, loadingWeight)
                val sweep = halfSweep * 2f
                if (sweep <= 0.1f) return@forEachIndexed
                val start = angleCenter - halfSweep

                val pressWeight = expansion[index].value * (1f - loadingWeight)
                val activeThickness = lerpFloat(baseThickness, thinThickness, loadingWeight)
                val stroke = activeThickness + pressWeight * 9.dp.toPx()
                val arcRadius = outer - (baseThickness - stroke) / 2f + pressWeight * 4.5.dp.toPx()
                val color = segmentColors[index].value
                val gap = separatorAngle * (1f - loadingWeight)
                val sliceAlpha = 1f - completeWeight
                val highlightColor = blendCacheColor(color)
                val outerBound = arcRadius + stroke / 2f
                val innerBound = (arcRadius - stroke / 2f).coerceAtLeast(0f)

                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            highlightColor.copy(alpha = sliceAlpha),
                            color.copy(alpha = sliceAlpha)
                        ),
                        center = center,
                        radius = outerBound.coerceAtLeast(1f)
                    ),
                    startAngle = start + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = center - Offset(arcRadius, arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(
                        width = stroke,
                        cap = if (loadingWeight > 0.05f) StrokeCap.Round else StrokeCap.Butt
                    )
                )

                val middleAngle = angleCenter * PI.toFloat() / 180f
                val textPosition = center + Offset(cos(middleAngle), sin(middleAngle)) * arcRadius
                val labelAlpha = (1f - loadingWeight) * (1f - completeWeight) * labelAlphas[index].value
                val particleGateAlpha = maxOf(0f, (1f - loadingWeight) / 0.75f - 0.75f) * (1f - completeWeight)

                // 环内径向漂移分类图标（零 Path 分配，带中心距文本渐隐）
                if (!reduceMotion && gate.active && particleGateAlpha > 0.02f && sweep > 4f) {
                    ringClipPath.reset()
                    ringClipPath.fillType = PathFillType.EvenOdd
                    ringClipPath.addOval(Rect(center - Offset(outerBound, outerBound), Size(outerBound * 2f, outerBound * 2f)))
                    ringClipPath.addOval(Rect(center - Offset(innerBound, innerBound), Size(innerBound * 2f, innerBound * 2f)))
                    wedgePath.reset()
                    wedgePath.moveTo(center.x, center.y)
                    wedgePath.arcTo(
                        Rect(center - Offset(outerBound, outerBound), Size(outerBound * 2f, outerBound * 2f)),
                        start + gap / 2f,
                        (sweep - gap).coerceAtLeast(0.5f),
                        false
                    )
                    wedgePath.close()
                    clipPath(ringClipPath) {
                        clipPath(wedgePath) {
                            val stepAngle = 8f
                            val fromIdx = floor((start + gap / 2f) / stepAngle).toInt()
                            val toIdx = kotlin.math.ceil((start + sweep - gap / 2f) / stepAngle).toInt()
                            for (i in fromIdx..toIdx) {
                                val angle = i * stepAngle
                                val t = ((particleTime + 100f) * (1f + (sin(angle * 2000f) + 1f) * 0.25f)) % 1f
                                val r = lerpFloat(innerBound - glyphSize * sqrt2, outerBound + glyphSize * sqrt2, t)
                                val rad = angle * PI.toFloat() / 180f
                                val px = center.x + r * cos(rad)
                                val py = center.y + r * sin(rad)
                                val distToLabel = hypot((px - textPosition.x).toDouble(), (py - textPosition.y).toDouble()).toFloat()
                                val labelAvoid = lerpFloat(1f, (distToLabel / labelClearance).coerceIn(0f, 1f), labelAlpha)
                                val pAlpha = (0.65f * particleGateAlpha * (-1.75f * abs(t - 0.5f) + 1f) *
                                    (0.25f * (sin(t * PI.toFloat()) - 1f) + 1f) * labelAvoid).coerceIn(0f, 1f)
                                if (pAlpha > 0.02f) {
                                    val scale = (0.75f * (0.25f * (sin(t * PI.toFloat()) - 1f) + 1f) *
                                        (0.8f + (sin(angle) + 1f) * 0.25f)).coerceIn(0.4f, 1.15f)
                                    val scaledExtent = glyphSize * scale
                                    translate(px - scaledExtent / 2f, py - scaledExtent / 2f) {
                                        drawCacheCategoryGlyph(
                                            target = segment.target,
                                            color = Color.White.copy(alpha = pAlpha),
                                            extent = scaledExtent,
                                            reusablePath = glyphPath
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (labelAlpha > 0.01f && sweep >= 20f) {
                    val labelColor = if (color.luminance() > 0.45f) Color.Black.copy(alpha = 0.84f) else Color.White
                    labelPaint.color = labelColor.toArgb()
                    labelPaint.textSize = (if (compactText) 10.sp else 12.sp).toPx() * labelScales[index].value
                    labelPaint.alpha = (labelAlpha * 255).toInt().coerceIn(0, 255)
                    drawIntoCanvas {
                        it.nativeCanvas.drawText(
                            segment.chartPercentLabel,
                            textPosition.x,
                            textPosition.y - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                            labelPaint
                        )
                    }
                }
            }

            if (completeWeight > 0f) {
                val green = Brush.linearGradient(CacheClearCompleteGradientColors)
                drawCircle(green, trackRadius, alpha = completeWeight, style = Stroke(thickness, cap = StrokeCap.Round))
                checkPath.reset()
                checkPath.moveTo(center.x - outer * 0.30f, center.y + outer * 0.04f)
                checkPath.lineTo(center.x - outer * 0.10f, center.y + outer * 0.24f)
                checkPath.lineTo(center.x + outer * 0.35f, center.y - outer * 0.21f)
                drawPath(
                    path = checkPath,
                    brush = green,
                    alpha = completeWeight,
                    style = Stroke(
                        width = if (compactText) 7.dp.toPx() else 9.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                if (!reduceMotion && completeWeight < 1f) {
                    val burstColor = CacheClearCompleteGradientColors.last()
                    repeat(20) { i ->
                        val angle = (i * 2f * PI / 20).toFloat()
                        val r = outer * (0.82f + completeWeight * 0.55f)
                        val position = center + Offset(cos(angle), sin(angle)) * r
                        drawCircle(burstColor, (1f - completeWeight) * 2.dp.toPx(), position, alpha = completeWeight * (1f - completeWeight) * 2f)
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = (1f - loadingAnim.value) * (1f - finished.value)
            }
        ) {
            AnimatedContent(
                targetState = loading to centerSize,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        (fadeIn(tween(450, easing = CacheDonutEasing)) +
                            scaleIn(initialScale = 0.6f, animationSpec = tween(450, easing = CacheDonutEasing)))
                            .togetherWith(
                                fadeOut(tween(180)) +
                                    scaleOut(targetScale = 0.6f, animationSpec = tween(180))
                            )
                    }
                },
                label = "cacheDonutCenterValue"
            ) { (isLoading, sizeText) ->
                val parts = sizeText.split(' ', limit = 2)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(
                        text = if (isLoading) "—" else parts[0],
                        fontSize = when {
                            compactText && parts[0].length > 4 -> 16.sp
                            compactText -> 20.sp
                            parts[0].length > 4 -> 22.sp
                            else -> 26.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = AppContentColors.primaryText(),
                        maxLines = 1
                    )
                    val unitAndCaption = if (isLoading) {
                        "统计中"
                    } else {
                        val unit = parts.getOrElse(1) { "" }
                        if (unit.isNotEmpty()) "$unit · $centerCaption" else centerCaption
                    }
                    AppText(
                        text = unitAndCaption,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun CacheClearCompletionBadge(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 88.dp,
) {
    val reduceMotion = LocalSystemReduceMotion.current
    val progress = animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduceMotion) snap() else tween(650, easing = CacheDonutEasing),
        label = "cacheCompletionBadge"
    )
    val checkPath = remember { Path() }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier.size(badgeSize)) {
        val stroke = 7.dp.toPx()
        val radius = size.minDimension / 2f - stroke
        val t = progress.value
        val green = Brush.linearGradient(CacheClearCompleteGradientColors)
        drawCircle(trackColor.copy(alpha = 0.25f), radius, style = Stroke(stroke))
        drawArc(
            brush = green,
            startAngle = -90f,
            sweepAngle = 360f * t,
            useCenter = false,
            topLeft = center - Offset(radius, radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        checkPath.reset()
        checkPath.moveTo(center.x - radius * 0.34f, center.y)
        checkPath.lineTo(center.x - radius * 0.08f, center.y + radius * 0.26f)
        checkPath.lineTo(center.x + radius * 0.38f, center.y - radius * 0.28f)
        drawPath(
            path = checkPath,
            brush = green,
            alpha = t,
            style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun blendCacheColor(color: Color): Color = Color(
    red = color.red + (1f - color.red) * 0.14f,
    green = color.green + (1f - color.green) * 0.14f,
    blue = color.blue + (1f - color.blue) * 0.14f
)

/** 统一 24 格线性语义图形，同时用于分类标识和环内装饰。 */
internal fun DrawScope.drawCacheCategoryGlyph(
    target: CacheClearTarget,
    color: Color,
    extent: Float,
    reusablePath: Path = Path()
) {
    val unit = extent / 24f
    reusablePath.reset()
    fun move(x: Float, y: Float) = reusablePath.moveTo(x * unit, y * unit)
    fun line(x: Float, y: Float) = reusablePath.lineTo(x * unit, y * unit)
    when (target) {
        CacheClearTarget.PLAYBACK_QUALITY -> {
            move(8f, 5f); line(19f, 12f); line(8f, 19f); reusablePath.close()
        }
        CacheClearTarget.NETWORK -> {
            move(4f, 8f); line(4f, 5f); line(20f, 5f); line(20f, 8f)
            move(12f, 5f); line(12f, 19f); move(4f, 16f); line(4f, 19f); line(20f, 19f); line(20f, 16f)
        }
        CacheClearTarget.IMAGE_PREVIEW -> {
            move(4f, 4f); line(20f, 4f); line(20f, 20f); line(4f, 20f); reusablePath.close()
            move(4f, 17f); line(10f, 11f); line(14f, 15f); line(17f, 12f); line(20f, 15f)
            drawCircle(color, 1.5f * unit, Offset(15f * unit, 8f * unit))
        }
        CacheClearTarget.SUBTITLE_DANMAKU -> {
            move(4f, 5f); line(20f, 5f); line(20f, 17f); line(11f, 17f); line(6f, 21f); line(6f, 17f); line(4f, 17f); reusablePath.close()
            move(8f, 10f); line(16f, 10f); move(8f, 13f); line(14f, 13f)
        }
        CacheClearTarget.TEMP_FILES_AND_LOGS -> {
            move(6f, 3f); line(14f, 3f); line(19f, 8f); line(19f, 21f); line(6f, 21f); reusablePath.close()
            move(14f, 3f); line(14f, 8f); line(19f, 8f); move(9f, 12f); line(16f, 12f); move(9f, 16f); line(14f, 16f)
        }
        CacheClearTarget.APP_METADATA -> {
            drawCircle(color, 4f * unit, Offset(12f * unit, 7f * unit), style = Stroke(1.8f * unit))
            reusablePath.arcTo(Rect(4f * unit, 13f * unit, 20f * unit, 25f * unit), 180f, 180f, false)
        }
    }
    drawPath(reusablePath, color, style = Stroke(1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
