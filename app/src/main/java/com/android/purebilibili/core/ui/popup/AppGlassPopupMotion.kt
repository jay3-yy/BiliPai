package com.android.purebilibili.core.ui.popup

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.android.purebilibili.core.ui.components.AppGlassMenuPlacement
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

internal data class AppGlassPopupFrame(
    val rect: Rect,
    val radius: Float,
    val contentScale: Float,
    val contentAlpha: Float,
    val contentBlur: Float,
    val surfaceAlpha: Float = 1f,
    val sourceRect: Rect? = null,
    val sourceAlpha: Float = 0f,
    val sourceBlur: Float = 0f,
    val surfaceBlend: Float = 0f,
    val materialProgress: Float = 1f,
    val contentStretchX: Float = 1f,
    val contentStretchY: Float = 1f,
    /** 弹性通道显式控制透镜脉冲；标准保留原有材质进度映射。 */
    val lensProgress: Float? = null,
)

/** 源快照接回底色与阴影时，联合外壳退让；无有效源的菜单保持自身材质。 */
internal fun popupMaterialOwnership(frame: AppGlassPopupFrame, sourceAvailable: Boolean): Float {
    if (!sourceAvailable || frame.sourceRect == null) return 1f
    val sourceCoverage = frame.sourceAlpha.coerceIn(0f, 1f) *
        (1f - frame.materialProgress.coerceIn(0f, 1f))
    return 1f - sourceCoverage
}

/** 曲率按当前短边归一化，供普通转场及中途反向使用。 */
internal fun popupCornerRoundness(frame: AppGlassPopupFrame): Float =
    if (frame.rect.minDimension <= 0f) 0f else (frame.radius / (frame.rect.minDimension / 2f)).coerceIn(0f, 1f)

internal object AppGlassPopupMotion {
    const val OpenSeconds = 0.45f
    const val ReturnSeconds = com.android.purebilibili.core.ui.AppGlassPopupMotionSpec.AnchoredReturnMillis / 1000f
    const val ShrinkSeconds = com.android.purebilibili.core.ui.AppGlassPopupMotionSpec.UnanchoredExitMillis / 1000f
    private val easeOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    private val easeIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    private val glyphIn = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    private val easeInOut = AppMotionEasing.IosEaseInOut

    private fun spring(t: Float, stiffness: Float, ratio: Float): Float {
        val omega = sqrt(stiffness)
        val damped = omega * sqrt(1f - ratio * ratio)
        return 1f - exp(-ratio * omega * t) *
            (cos(damped * t) + ratio * omega / damped * sin(damped * t))
    }

    private fun cornerBlend(time: Float, start: Float, end: Float): Float {
        val t = ((time - start) / (end - start)).coerceIn(0f, 1f)
        return (t * t * t * (t * (t * 6f - 15f) + 10f)).coerceIn(0f, 1f)
    }

    /** 连接颈受两端实际尺寸约束；小控件不会被固定融合半径撑成一团。 */
    private fun blendBand(main: Rect, source: Rect, density: Float): Float =
        min(28f * density, min(main.minDimension, source.minDimension).coerceAtLeast(0f) * 0.65f)

    private fun pulse(time: Float, peak: Float, sharpness: Float): Float {
        if (time <= 0f) return 0f
        val x = time / peak
        return x.pow(sharpness) * exp(sharpness * (1f - x))
    }

    private fun rect(center: Offset, width: Float, height: Float): Rect = Rect(
        center.x - width / 2f, center.y - height / 2f,
        center.x + width / 2f, center.y + height / 2f,
    )

    fun closeDuration(hasSourceControl: Boolean): Float = if (hasSourceControl) ReturnSeconds else ShrinkSeconds

    fun closeDuration(anchor: Rect): Float = closeDuration(!anchor.isEmpty)

    fun opening(time: Float, card: Rect, anchor: Rect, radius: Float, sourceControl: Boolean = false, density: Float = 1f): AppGlassPopupFrame {
        if (card.isEmpty || time >= OpenSeconds) return AppGlassPopupFrame(card,
            radius.coerceIn(0f, max(0f, card.minDimension / 2f)), 1f, 1f, 0f)
        val t = time.coerceAtLeast(0f)
        val progress = spring(t, 400f, 0.754f)
        val reveal = progress.coerceIn(0f, 1f)
        if (anchor.isEmpty) {
            val scale = 0.9f + 0.1f * progress
            return AppGlassPopupFrame(rect(card.center, card.width * scale, card.height * scale),
                min(radius, card.minDimension / 2f).coerceAtLeast(0f) * scale, scale, reveal, 0f, reveal)
        }
        val startScale = 0.41f * anchor.minDimension / card.width
        val scale = (startScale + (1f - startScale) * progress).coerceAtLeast(0.001f)
        val height = card.width + (card.height - card.width) * progress
        val distance = card.center.y - anchor.center.y
        val dip = if (sourceControl) distance.sign * (min(1f, kotlin.math.abs(distance) / (30f * density)) * 23f * density +
            0.124f * kotlin.math.abs(distance)) * pulse(t, 0.098f, 3.3f) else 0f
        val center = anchor.center + (card.center - anchor.center) * progress + Offset(0f, dip)
        // 形体可有弹性，曲率不另起一轮弹簧；先保持气泡，再平滑落到卡片圆角。
        val cornerProgress = cornerBlend(t, 0.07f, 0.3f)
        val halfMinDimension = min(card.width * scale, max(0f, height * scale)) / 2f
        val corners = halfMinDimension + (radius.coerceIn(0f, halfMinDimension) - halfMinDimension) * cornerProgress
        val blur = density * sqrt((4f * (1f - reveal)).pow(2) + (1.9f * pulse(t, 0.069f, 2.1f)).pow(2))
        val inner = card.deflate(max(anchor.width, anchor.height) * 0.25f / 2f + 36f * density)
        val sunk = anchor.center + (card.center - anchor.center) * 0.25f
        val target = if (inner.isEmpty) card.center else Offset(
            sunk.x.coerceIn(inner.left, inner.right), sunk.y.coerceIn(inner.top, inner.bottom),
        )
        val sourceScale = 1f - 0.75f * progress
        // 外壳先展开，正文随后清晰；源控件、材质与连接颈分别交接，不随几何过冲闪回。
        val under = if (sourceControl) cornerBlend(t, 0f, 0.09f) else min(1f, reveal / 0.25f)
        val body = rect(center, card.width * scale, max(0f, height * scale))
        val source = if (sourceControl) rect(anchor.center + (target - anchor.center) * progress,
            anchor.width * sourceScale, anchor.height * sourceScale) else null
        val neck = cornerBlend(t, 0f, 0.055f) * (1f - cornerBlend(t, 0.18f, 0.34f))
        return AppGlassPopupFrame(
            body,
            corners.coerceIn(0f, halfMinDimension), scale,
            if (sourceControl) cornerBlend(t, 0.025f, 0.2f) else reveal, blur,
            sourceRect = source,
            sourceAlpha = if (sourceControl) 1f - under else 0f,
            sourceBlur = 4f * density * under,
            surfaceBlend = if (source != null) blendBand(body, source, density) * neck else 0f,
            materialProgress = if (sourceControl) cornerBlend(t, 0f, 0.18f) else 1f,
        )
    }

    /** 中途改变目标时只延续一次运动，不叠加展开或回收的脉冲。 */
    fun retarget(
        time: Float,
        duration: Float,
        start: AppGlassPopupFrame,
        previous: AppGlassPopupFrame,
        sampleSeconds: Float,
        target: AppGlassPopupFrame,
    ): AppGlassPopupFrame {
        if (time <= 0f) return start
        if (time >= duration) return target.copy(
            radius = target.radius.coerceIn(0f, max(0f, target.rect.minDimension / 2f)))
        val t = (time / duration).coerceIn(0f, 1f)
        val h00 = 2f * t * t * t - 3f * t * t + 1f
        val h10 = t * t * t - 2f * t * t + t
        val h01 = -2f * t * t * t + 3f * t * t
        fun value(a: Float, before: Float, b: Float): Float {
            val limit = 3f * kotlin.math.abs(b - a) / duration
            val velocity = ((a - before) / sampleSeconds.coerceAtLeast(0.0001f)).coerceIn(-limit, limit)
            return h00 * a + h10 * velocity * duration + h01 * b
        }
        fun bounds(a: Rect, before: Rect, b: Rect): Rect {
            val center = Offset(value(a.center.x, before.center.x, b.center.x), value(a.center.y, before.center.y, b.center.y))
            return rect(center, value(a.width, before.width, b.width).coerceAtLeast(0.001f),
                value(a.height, before.height, b.height).coerceAtLeast(0.001f))
        }
        val glyph = start.sourceRect ?: target.sourceRect
        val targetGlyph = target.sourceRect ?: glyph
        val result = bounds(start.rect, previous.rect, target.rect)
        // 反向时延续的是曲率比例，避免尺寸和绝对半径各自插值而互相越界。
        val roundness = value(popupCornerRoundness(start), popupCornerRoundness(previous),
            popupCornerRoundness(target)).coerceIn(0f, 1f)
        return AppGlassPopupFrame(
            rect = result,
            radius = result.minDimension / 2f * roundness,
            contentScale = value(start.contentScale, previous.contentScale, target.contentScale).coerceAtLeast(0.001f),
            contentStretchX = value(start.contentStretchX, previous.contentStretchX, target.contentStretchX),
            contentStretchY = value(start.contentStretchY, previous.contentStretchY, target.contentStretchY),
            contentAlpha = value(start.contentAlpha, previous.contentAlpha, target.contentAlpha).coerceIn(0f, 1f),
            contentBlur = value(start.contentBlur, previous.contentBlur, target.contentBlur).coerceAtLeast(0f),
            surfaceAlpha = value(start.surfaceAlpha, previous.surfaceAlpha, target.surfaceAlpha).coerceIn(0f, 1f),
            sourceRect = if (glyph != null && targetGlyph != null) bounds(glyph, previous.sourceRect ?: glyph, targetGlyph) else null,
            sourceAlpha = value(start.sourceAlpha, previous.sourceAlpha, target.sourceAlpha).coerceIn(0f, 1f),
            sourceBlur = value(start.sourceBlur, previous.sourceBlur, target.sourceBlur).coerceAtLeast(0f),
            surfaceBlend = value(start.surfaceBlend, previous.surfaceBlend, target.surfaceBlend).coerceAtLeast(0f),
            materialProgress = value(start.materialProgress, previous.materialProgress, target.materialProgress).coerceIn(0f, 1f),
        )
    }

    /** 从关闭瞬间的展开几何继续，提前关闭时不先跳回完整卡片。 */
    fun closing(time: Float, startTime: Float, card: Rect, anchor: Rect, radius: Float, sourceControl: Boolean = false, density: Float = 1f, startFrame: AppGlassPopupFrame? = null, returnToControl: Boolean = true): AppGlassPopupFrame {
        val start = startFrame ?: opening(startTime, card, anchor, radius, sourceControl, density)
        if (card.isEmpty || time <= 0f) return start
        if (anchor.isEmpty || !returnToControl) {
            val progress = easeOut.transform((time / ShrinkSeconds).coerceIn(0f, 1f))
            val scale = 1f - 0.2f * progress
            val origin = if (anchor.isEmpty) card.center else Offset(
                anchor.center.x.coerceIn(start.rect.left, start.rect.right),
                anchor.center.y.coerceIn(start.rect.top, start.rect.bottom),
            )
            return start.copy(
                rect = rect(origin + (start.rect.center - origin) * scale,
                    start.rect.width * scale, start.rect.height * scale),
                radius = start.radius * scale, contentScale = start.contentScale * scale,
                contentAlpha = start.contentAlpha * (1f - progress), surfaceAlpha = start.surfaceAlpha * (1f - progress),
                sourceAlpha = start.sourceAlpha * (1f - progress),
                surfaceBlend = start.surfaceBlend * (1f - progress),
                materialProgress = start.materialProgress * (1f - progress),
            )
        }
        val t = time.coerceAtLeast(0f)
        val ringing = 1f - easeIn.transform(((t - 0.4f) / 0.1f).coerceIn(0f, 1f))
        val progress = 1f + (spring(t, 165f, 0.8f) - 1f) * ringing
        val from = start.rect
        val away = (from.center.y - anchor.center.y).sign
        val dip = min(0.21f * kotlin.math.abs(from.center.y - anchor.center.y), 22f * density) * pulse(t, 0.072f, 2.4f)
        val targetScale = 0.41f * anchor.minDimension / max(from.width, from.height).coerceAtLeast(1f)
        val scale = (1f + (targetScale - 1f) * progress).coerceAtLeast(0.001f)
        val center = from.center + (anchor.center - from.center) * progress + Offset(0f, away * dip * ringing)
        val result = rect(center, from.width * scale, max(0f, (from.height + (from.width - from.height) * progress) * scale))
        val reveal = start.contentAlpha * (1f - easeOut.transform((t / 0.1f).coerceIn(0f, 1f)))
        val absorbed = easeInOut.transform(((t / ReturnSeconds - 0.6f) / 0.3f).coerceIn(0f, 1f))
        val targetContentScale = min(result.width / card.width, result.height / card.height)
        val contentScale = start.contentScale + (targetContentScale - start.contentScale) * progress.coerceIn(0f, 1f)
        val contentOut = if (start.contentAlpha > 0.001f) 1f - reveal / start.contentAlpha else 1f
        val fromGlyph = start.sourceRect ?: rect(anchor.center + (from.center - anchor.center) * 0.25f,
            anchor.width * 0.25f, anchor.height * 0.25f)
        val lift = min(0.25f * kotlin.math.abs(from.center.y - anchor.center.y), 19f * density) * pulse(t, 0.176f, 4.9f)
        val glyphRect = rect(fromGlyph.center + (anchor.center - fromGlyph.center) * progress -
            Offset(0f, away * lift * ringing),
            fromGlyph.width + (anchor.width - fromGlyph.width) * progress,
            fromGlyph.height + (anchor.height - fromGlyph.height) * progress)
        val cornerProgress = cornerBlend(t, 0f, ReturnSeconds * 0.6f)
        val startRoundness = popupCornerRoundness(start)
        val corners = result.minDimension / 2f * (startRoundness + (1f - startRoundness) * cornerProgress)
        return AppGlassPopupFrame(
            result, corners,
            contentScale.coerceAtLeast(0.001f), reveal,
            start.contentBlur + (8f * density - start.contentBlur) * contentOut, start.surfaceAlpha * (1f - absorbed),
            sourceRect = if (sourceControl) glyphRect else null,
            sourceAlpha = if (sourceControl) {
                val initial = start.sourceAlpha
                initial + (1f - initial) * glyphIn.transform((t / 0.47f).coerceIn(0f, 1f))
            } else 0f,
            sourceBlur = start.sourceBlur * (1f - (t / 0.1f).coerceIn(0f, 1f)),
            surfaceBlend = if (sourceControl) {
                val band = blendBand(result, glyphRect, density)
                (start.surfaceBlend + (band - start.surfaceBlend) * cornerBlend(t, 0f, 0.07f)) * (1f - absorbed)
            } else 0f,
            materialProgress = start.materialProgress * (1f - absorbed),
            contentStretchX = start.contentStretchX + (1f - start.contentStretchX) * progress.coerceIn(0f, 1f),
            contentStretchY = start.contentStretchY + (1f - start.contentStretchY) * progress.coerceIn(0f, 1f),
        )
    }
}

/** 局部采样轮廓与裁切共用同一几何，避免材质把自定义形状误判成椭圆。 */
internal fun glassPopupLocalOutline(
    frame: AppGlassPopupFrame,
    card: Rect,
    size: Size,
    content: Boolean,
): Pair<Rect, Float> {
    if (card.isEmpty) return Rect(Offset.Zero, size) to 0f
    val scale = (if (content) frame.contentScale else frame.rect.width / card.width).coerceAtLeast(0.001f)
    val width = frame.rect.width / (scale * if (content) frame.contentStretchX else 1f)
    val height = frame.rect.height / (scale * if (content) frame.contentStretchY else 1f)
    val rect = Rect(size.width / 2f - width / 2f, size.height / 2f - height / 2f,
        size.width / 2f + width / 2f, size.height / 2f + height / 2f)
    return rect to (frame.radius / scale).coerceIn(0f, min(width, height) / 2f)
}

internal fun resolveAppGlassPopupPosition(
    anchor: Rect?,
    centered: Boolean,
    popupSize: IntSize,
    hostSize: IntSize,
    margin: Int,
    overlap: Int,
    placement: AppGlassMenuPlacement = AppGlassMenuPlacement.ANCHOR_END,
    verticalMargin: Int = margin,
): IntOffset {
    val minX = margin.coerceAtMost(((hostSize.width - popupSize.width) / 2).coerceAtLeast(0))
    val minY = verticalMargin.coerceAtMost(((hostSize.height - popupSize.height) / 2).coerceAtLeast(0))
    val maxX = (hostSize.width - margin - popupSize.width).coerceAtLeast(minX)
    val maxY = (hostSize.height - verticalMargin - popupSize.height).coerceAtLeast(minY)
    val x = when {
        placement == AppGlassMenuPlacement.START -> minX
        placement == AppGlassMenuPlacement.END || placement == AppGlassMenuPlacement.END_BOTTOM -> maxX
        centered || anchor == null -> (hostSize.width - popupSize.width) / 2
        else -> anchor.right.roundToInt() - popupSize.width
    }
    val y = when {
        placement == AppGlassMenuPlacement.END_BOTTOM -> maxY
        centered || anchor == null || placement == AppGlassMenuPlacement.START || placement == AppGlassMenuPlacement.END ->
            (hostSize.height - popupSize.height) / 2
        else -> anchor.top.roundToInt() - overlap
    }
    return IntOffset(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
}

internal fun resolveGlassMenuInitialScroll(item: Rect, viewportHeight: Int, maxScroll: Int): Int =
    (item.center.y - viewportHeight / 2f).roundToInt().coerceIn(0, maxScroll.coerceAtLeast(0))

/** 手势预览保留来源与卡片身份，确认后将当前帧交给原有回收动画。 */
internal fun popupPredictiveBackFrame(
    frame: AppGlassPopupFrame,
    progress: Float,
    density: Float,
): AppGlassPopupFrame {
    if (progress <= 0f) return frame
    val scale = 1f - 0.2f * progress
    val half = frame.rect.size * (scale / 2f)
    return frame.copy(
        rect = Rect(frame.rect.center - Offset(half.width, half.height),
            frame.rect.center + Offset(half.width, half.height)),
        radius = frame.radius * scale,
        contentScale = frame.contentScale * scale,
        contentAlpha = frame.contentAlpha * (1f - progress),
        contentBlur = frame.contentBlur + 8f * density * progress,
        surfaceAlpha = frame.surfaceAlpha * (1f - progress),
    )
}
