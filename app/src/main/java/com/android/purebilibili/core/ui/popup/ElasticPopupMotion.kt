package com.android.purebilibili.core.ui.popup

import com.android.purebilibili.core.ui.motion.GlassMorphSpringSpec
import com.android.purebilibili.core.ui.motion.GlassMorphSprings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/** 弹性参数只用于玻璃菜单；总时长为有限收尾上限，response 是各通道的响应周期。 */
internal object ElasticPopupMotion {
    const val OpenSeconds = 0.65f
    const val CloseSeconds = 0.75f
    const val RetargetSeconds = 0.55f
    val Eject = GlassMorphSprings.Expand
    val Absorb = GlassMorphSprings.Return
    val Collapse = GlassMorphSpringSpec(1f, 0.21f)
    val Kick = GlassMorphSpringSpec(1f, 0.14f)
    val BlurOut = GlassMorphSpringSpec(0.8f, 0.315f)
    val LensOff = GlassMorphSpringSpec(1f, 0.42f)
    val AbsorbLensOff = GlassMorphSpringSpec(1f, 0.28f)
    val SmoothingRelease = GlassMorphSpringSpec(1f, 0.42f)
    private val FastSmoothing = GlassMorphSpringSpec(1f, 0.07f)

    private fun staged(time: Float, delay: Float, from: Float, first: Float, last: Float,
                       initial: GlassMorphSpringSpec, released: GlassMorphSpringSpec): Float {
        if (time <= delay) return initial.sample(time, from, first).value
        val turn = initial.sample(delay, from, first)
        return released.sample(time - delay, turn.value, last, turn.velocity).value
    }

    private fun rect(center: Offset, width: Float, height: Float): Rect {
        val w = width.coerceAtLeast(0.001f)
        val h = height.coerceAtLeast(0.001f)
        return Rect(center.x - w / 2f, center.y - h / 2f, center.x + w / 2f, center.y + h / 2f)
    }

    private fun diameter(card: Rect, anchor: Rect): Float =
        (card.width * 0.5f * anchor.height / max(card.width, card.height))
            .coerceIn(0.001f, anchor.minDimension.coerceAtLeast(0.001f))

    fun frame(state: PopupMorphState, time: Float, radius: Float, density: Float): AppGlassPopupFrame {
        val start = if (state.opening) state.openStartFrame else state.closeStartFrame
        if (state.opening && start != null) return retarget(time, state.openDuration, start,
            state.previousFrame ?: start, state.sampleSeconds,
            AppGlassPopupFrame(state.card, radius, 1f, 1f, 0f, lensProgress = 0f), Eject)
        if (!state.opening && !state.hasSourceControl) {
            val result = AppGlassPopupMotion.closing(time, state.closeStartSeconds,
                state.card, state.anchor, radius, false, density, start, returnToControl = false)
            return result.copy(lensProgress = start?.lensProgress?.times(
                1f - (time / state.closeDuration).coerceIn(0f, 1f)))
        }
        if (!state.opening && state.interruptedClose && start != null) return retarget(time, state.closeDuration,
            start, state.previousFrame ?: start, state.sampleSeconds, collapsed(state.card, state.anchor), Absorb)
        return if (state.opening) opening(time, state.card, state.anchor, radius, state.hasSourceControl, density)
        else closing(time, state.card, state.anchor, radius, start, density)
    }

    internal fun opening(time: Float, card: Rect, anchor: Rect, radius: Float, source: Boolean, density: Float): AppGlassPopupFrame {
        if (card.isEmpty) return AppGlassPopupFrame(card, 0f, 1f, 0f, 0f, surfaceAlpha = 0f, lensProgress = 0f)
        val end = AppGlassPopupFrame(card, radius.coerceIn(0f, card.minDimension / 2f), 1f, 1f, 0f, lensProgress = 0f)
        if (time >= OpenSeconds) return end
        val t = time.coerceAtLeast(0f)
        val p = Eject.sample(t, 0f, 1f).value
        if (anchor.isEmpty) {
            val scale = 0.9f + 0.1f * p
            return end.copy(rect = rect(card.center, card.width * scale, card.height * scale),
                radius = end.radius * scale, contentScale = scale, contentAlpha = p.coerceIn(0f, 1f),
                surfaceAlpha = p.coerceIn(0f, 1f))
        }
        val initialScale = diameter(card, anchor) / card.width
        // 同一弹簧、时间与零初速度的通道共用进度，只映射各自的起终值。
        val scale = (initialScale + (1f - initialScale) * p).coerceAtLeast(0.001f)
        val height = card.width + (card.height - card.width) * p
        val distance = card.center.y - anchor.center.y
        val kick = if (source) staged(t, 0.07f, 0f,
            distance.sign * min(abs(distance) * 0.124f, 23f * density), 0f, Kick, Kick) else 0f
        val body = rect(anchor.center + (card.center - anchor.center) * p + Offset(0f, kick * scale),
            card.width * scale, height * scale)
        val corner = staged(t, 0.07f, card.width / 2f, card.width, radius, Eject, Eject) * scale
        val sourceScale = 1f - 0.75f * p
        val pivot = anchor.center + (card.center - anchor.center) * 0.25f
        val sourceCenter = anchor.center + (pivot - anchor.center) * p
        val sourceRect = if (source) rect(sourceCenter,
            Collapse.sample(t, anchor.width, anchor.minDimension).value * sourceScale,
            Collapse.sample(t, anchor.height, anchor.minDimension).value * sourceScale) else null
        val content = p.coerceIn(0f, 1f)
        val sourceAlpha = (1f - p).coerceIn(0f, 1f)
        val portal = staged(t, 0.084f, 0f, 1f, 0f, Collapse, BlurOut).coerceAtLeast(0f) * 4f * density
        val smoothing = staged(t, 0.21f, 0f, 20f * density, 0f, Collapse, SmoothingRelease).coerceAtLeast(0f)
        return AppGlassPopupFrame(body, corner.coerceIn(0f, body.minDimension / 2f), scale, content,
            sqrt((4f * density * (1f - content)).let { it * it } + portal * portal),
            sourceRect = sourceRect, sourceAlpha = if (source) sourceAlpha else 0f,
            sourceBlur = 4f * density * (1f - sourceAlpha),
            surfaceBlend = if (sourceRect == null) 0f else min(smoothing, min(body.minDimension, sourceRect.minDimension) * 0.65f),
            materialProgress = Collapse.sample(t, 0f, 1f).value.coerceIn(0f, 1f),
            lensProgress = staged(t, 0.084f, 1f, 1f, 0f, Kick, LensOff).coerceIn(0f, 1f))
    }

    private fun collapsed(card: Rect, anchor: Rect): AppGlassPopupFrame {
        val diameter = diameter(card, anchor)
        return AppGlassPopupFrame(rect(anchor.center, diameter, diameter), diameter / 2f,
            diameter / card.width.coerceAtLeast(1f), 0f, 0f, surfaceAlpha = 0f,
            sourceRect = anchor, sourceAlpha = 1f, materialProgress = 0f, lensProgress = 0f)
    }

    private fun closing(time: Float, card: Rect, anchor: Rect, radius: Float, from: AppGlassPopupFrame?, density: Float): AppGlassPopupFrame {
        val start = from ?: AppGlassPopupFrame(card, radius, 1f, 1f, 0f, lensProgress = 0f)
        if (time <= 0f) return start
        if (time >= CloseSeconds) return collapsed(card, anchor)
        val end = collapsed(card, anchor)
        val base = retarget(time, CloseSeconds, start, start, 1f / 120f, end, Absorb)
        val sourceStart = start.sourceRect ?: rect(anchor.center + (start.rect.center - anchor.center) * 0.25f,
            anchor.width * 0.25f, anchor.height * 0.25f)
        fun value(a: Float, b: Float) = Absorb.sample(time, a, b).value
        val sourceRect = rect(Offset(value(sourceStart.center.x, anchor.center.x), value(sourceStart.center.y, anchor.center.y)),
            value(sourceStart.width, anchor.width), value(sourceStart.height, anchor.height))
        val smoothing = staged(time, 0.07f, start.surfaceBlend, 20f * density, 0f, FastSmoothing, SmoothingRelease).coerceAtLeast(0f)
        val lens = staged(time, 0.07f, start.lensProgress ?: 0f, 1f, 0f, Kick, AbsorbLensOff).coerceIn(0f, 1f)
        // 从手势当前清晰度继续；正常关闭仍使用原先随正文消退增加的模糊时序。
        val contentOut = if (start.contentAlpha > 0f) {
            (1f - base.contentAlpha / start.contentAlpha).coerceIn(0f, 1f)
        } else 1f
        return base.copy(sourceRect = sourceRect,
            surfaceBlend = min(smoothing, min(base.rect.minDimension, sourceRect.minDimension) * 0.65f),
            contentBlur = start.contentBlur + (4f * density - start.contentBlur) * contentOut,
            sourceBlur = 4f * density * (1f - base.sourceAlpha), lensProgress = lens)
    }

    /** 每个通道从当前值与采样速度重定向，保留中途反向，不重放入场脉冲。 */
    internal fun retarget(time: Float, duration: Float, start: AppGlassPopupFrame, previous: AppGlassPopupFrame,
                          sampleSeconds: Float, target: AppGlassPopupFrame, spec: GlassMorphSpringSpec): AppGlassPopupFrame {
        if (time <= 0f) return start
        if (time >= duration) return target
        fun value(a: Float, before: Float, b: Float): Float =
            spec.sample(time, a, b, (a - before) / sampleSeconds.coerceAtLeast(0.0001f)).value
        fun bounds(a: Rect, before: Rect, b: Rect): Rect = rect(
            Offset(value(a.center.x, before.center.x, b.center.x), value(a.center.y, before.center.y, b.center.y)),
            value(a.width, before.width, b.width), value(a.height, before.height, b.height))
        val body = bounds(start.rect, previous.rect, target.rect)
        val source = start.sourceRect ?: target.sourceRect
        val targetSource = target.sourceRect ?: source?.let { rect(target.rect.center, it.width * 0.25f, it.height * 0.25f) }
        val roundness = value(popupCornerRoundness(start), popupCornerRoundness(previous), popupCornerRoundness(target)).coerceIn(0f, 1f)
        return AppGlassPopupFrame(body, body.minDimension / 2f * roundness,
            value(start.contentScale, previous.contentScale, target.contentScale).coerceAtLeast(0.001f),
            value(start.contentAlpha, previous.contentAlpha, target.contentAlpha).coerceIn(0f, 1f),
            value(start.contentBlur, previous.contentBlur, target.contentBlur).coerceAtLeast(0f),
            surfaceAlpha = value(start.surfaceAlpha, previous.surfaceAlpha, target.surfaceAlpha).coerceIn(0f, 1f),
            sourceRect = if (source != null && targetSource != null) bounds(source, previous.sourceRect ?: source, targetSource) else null,
            sourceAlpha = value(start.sourceAlpha, previous.sourceAlpha, target.sourceAlpha).coerceIn(0f, 1f),
            sourceBlur = value(start.sourceBlur, previous.sourceBlur, target.sourceBlur).coerceAtLeast(0f),
            surfaceBlend = value(start.surfaceBlend, previous.surfaceBlend, target.surfaceBlend).coerceAtLeast(0f),
            materialProgress = value(start.materialProgress, previous.materialProgress, target.materialProgress).coerceIn(0f, 1f),
            contentStretchX = value(start.contentStretchX, previous.contentStretchX, target.contentStretchX).coerceAtLeast(0.001f),
            contentStretchY = value(start.contentStretchY, previous.contentStretchY, target.contentStretchY).coerceAtLeast(0.001f),
            lensProgress = value(start.lensProgress ?: 0f, previous.lensProgress ?: 0f, target.lensProgress ?: 0f).coerceIn(0f, 1f))
    }
}
