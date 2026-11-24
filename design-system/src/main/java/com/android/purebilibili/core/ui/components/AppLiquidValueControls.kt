/*
 * Adapted from Kyant AndroidLiquidGlass LiquidToggle, LiquidSlider and DampedDragAnimation.
 * Copyright 2025 Kyant. Apache-2.0; see assets/licenses/AndroidLiquidGlass.LICENSE.
 * BiliPai: native theme fallback, safe sibling track capture, horizontal gesture ownership,
 * discrete values, accessibility actions, and interruption-safe coroutine jobs.
 */
package com.android.purebilibili.core.ui.components

import com.android.purebilibili.core.ui.animation.LiquidIndicatorMotion
import com.android.purebilibili.core.ui.animation.LiquidIndicatorVelocity

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import com.android.purebilibili.core.ui.AppSurfaceTokens
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import com.android.purebilibili.core.theme.calculateContrastRatio
import com.android.purebilibili.core.theme.opaqueCompositeOver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import top.yukonga.miuix.kmp.blur.*
import com.android.purebilibili.core.ui.effect.lens
import top.yukonga.miuix.kmp.material.EdgePeak
import top.yukonga.miuix.kmp.material.EdgeStrokes
import top.yukonga.miuix.kmp.material.drawEdgeStroke
import androidx.compose.ui.res.colorResource
import com.android.purebilibili.designsystem.R
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.MediaContrastPalette
import kotlin.math.roundToInt

/** A row can record explicit intent without treating persisted state restoration as input. */
internal class LiquidSwitchIntent {
    var requested: Boolean? = null
}
internal val LocalLiquidSwitchIntent = compositionLocalOf<LiquidSwitchIntent?> { null }

private class LiquidValueMotion(private val scope: CoroutineScope, initial: Float, private val motionEnabled: Boolean) {
    private val value = Animatable(initial)
    private var pointerValue by mutableStateOf<Float?>(null)
    // Read in graphicsLayer/drawBehind: pointer updates bypass coroutine/frame scheduling.
    val position: Float get() = pointerValue ?: value.value
    val press = Animatable(0f)
    val scaleX = Animatable(1f)
    val scaleY = Animatable(1f)
    val velocity = LiquidIndicatorVelocity(scope)
    var target = initial
        private set
    private var valueJob: Job? = null
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var selectionPulseJob: Job? = null
    private var lastFrameNanos = 0L
    private var lastFrameValue = initial

    fun beginGesture() {
        selectionPulseJob?.cancel()
        val current = position
        valueJob?.cancel()
        velocity.freeze()
        pointerValue = current
        target = current
        lastFrameNanos = System.nanoTime()
        lastFrameValue = current
    }

    fun trackPointer(next: Float) {
        val bounded = next.coerceIn(0f, 1f)
        target = bounded
        pointerValue = bounded
        if (motionEnabled) {
            val now = System.nanoTime()
            val seconds = (now - lastFrameNanos) / 1_000_000_000f
            if (lastFrameNanos != 0L && seconds > 0f) {
                val speed = (bounded - lastFrameValue) / seconds
                velocity.updateTarget(speed)
            }
            lastFrameNanos = now
            lastFrameValue = bounded
        }
    }

    fun finishGesture(next: Float) {
        val current = pointerValue
        if (current == null) {
            update(next)
            return
        }
        target = next.coerceIn(0f, 1f)
        valueJob?.cancel()
        val settledTarget = target
        valueJob = scope.launch {
            // Seed animation from the final pointer position before releasing its draw state.
            // Both mutations happen before suspension, so there is no jump to an old frame.
            value.snapTo(current)
            if (motionEnabled) {
                velocity.updateTarget(0f)
            } else velocity.snapTo(0f)
            pointerValue = null
            if (motionEnabled) value.animateTo(settledTarget, LiquidIndicatorMotion.positionSpec())
            else value.snapTo(settledTarget)
        }
    }

    fun update(next: Float) {
        // Parent callbacks/setting flows may echo snapped or earlier values during a gesture.
        // The pointer owns the visual position until release or cancellation.
        if (pointerValue != null) return
        target = next.coerceIn(0f, 1f)
        valueJob?.cancel()
        valueJob = scope.launch {
            if (!motionEnabled) {
                velocity.snapTo(0f)
                value.snapTo(target)
                return@launch
            }
            lastFrameNanos = 0L
            lastFrameValue = value.value
            value.animateTo(target, LiquidIndicatorMotion.positionSpec()) {
                val now = System.nanoTime()
                if (lastFrameNanos != 0L) {
                    val deltaSeconds = (now - lastFrameNanos) / 1_000_000_000f
                    if (deltaSeconds > 0f) {
                        val speed = (value - lastFrameValue) / deltaSeconds
                        this@LiquidValueMotion.velocity.updateTarget(speed)
                    }
                }
                lastFrameValue = value
                lastFrameNanos = now
            }
            velocity.updateTarget(0f)
        }
    }

    fun synchronize(next: Float) {
        if (pointerValue != null || abs(target - next) < 0.001f) return
        target = next.coerceIn(0f, 1f)
        selectionPulseJob?.cancel()
        valueJob?.cancel()
        releaseJob?.cancel()
        pressJob?.cancel()
        valueJob = scope.launch {
            value.snapTo(target)
            velocity.snapTo(0f)
            press.snapTo(0f)
            scaleX.snapTo(1f)
            scaleY.snapTo(1f)
        }
    }

    /** Preference rows can toggle checked without sending pointer events to the thumb. */
    fun animateSelection(next: Float) {
        if (pointerValue != null || abs(target - next) < 0.001f) return
        selectionPulseJob?.cancel()
        update(next)
        if (!motionEnabled) return
        pressed(true)
        selectionPulseJob = scope.launch {
            valueJob?.join()
            pressed(false)
        }
    }

    private fun animatePress(active: Boolean) {
        pressJob?.cancel()
        pressJob = scope.launch {
            launch { press.animateTo(if (active) 1f else 0f, LiquidIndicatorMotion.pressSpec()) }
            launch { scaleX.animateTo(if (active) LiquidIndicatorMotion.pressedScale else 1f, LiquidIndicatorMotion.scaleXSpec()) }
            launch { scaleY.animateTo(if (active) LiquidIndicatorMotion.pressedScale else 1f, LiquidIndicatorMotion.scaleYSpec()) }
        }
    }

    fun pressed(active: Boolean) {
        releaseJob?.cancel()
        if (!motionEnabled) {
            pressJob?.cancel()
            pressJob = scope.launch {
                press.snapTo(0f); scaleX.snapTo(1f); scaleY.snapTo(1f)
            }
            return
        }
        if (active) {
            animatePress(true)
        } else {
            // Let a short tap expose the lens before taking over its expansion job.
            // A new pointer cancels this waiter, so release cannot interrupt a new hold.
            releaseJob = scope.launch {
                withTimeoutOrNull(LiquidIndicatorMotion.BloomWaitMillis) {
                    snapshotFlow { minOf(scaleX.value, scaleY.value) }
                        .first { it >= LiquidIndicatorMotion.pressedScale }
                }
                withTimeoutOrNull(LiquidIndicatorMotion.SettleWaitMillis) {
                    snapshotFlow { position }.first { abs(it - target) <= LiquidIndicatorMotion.SettleTolerance }
                }
                animatePress(false)
            }
        }
    }

}

// Primitive parameters avoid boxing coordinates for each drag event.
private fun interface LiquidValueDrag {
    fun update(positionOrDeltaPx: Float, widthPx: Float)
}

/** Horizontal drag claims input only after slop; vertical list scrolling remains native. */
@Composable
private fun Modifier.liquidValueGesture(
    motion: LiquidValueMotion,
    absoluteDrag: Boolean,
    onDrag: LiquidValueDrag,
    onFinish: (Boolean, Float?, Float) -> Unit,
    onCancel: () -> Unit,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val latestDrag by rememberUpdatedState(onDrag)
    val latestFinish by rememberUpdatedState(onFinish)
    val latestCancel by rememberUpdatedState(onCancel)
    return pointerInput(motion, interactionSource, absoluteDrag) {
    awaitEachGesture {
        // Slop detection also reads Main. Starting in Initial would re-read this
        // consumed down in Main and immediately cancel instead of seeing movement.
        val down = awaitFirstDown(false, PointerEventPass.Main)
        // Main visits the child before enclosing clickables. Own the press here;
        // horizontal slop still arbitrates against vertical scrolling.
        down.consume()
        var complete = false
        val pressInteraction = PressInteraction.Press(down.position)
        interactionSource?.tryEmit(pressInteraction)
        motion.beginGesture()
        motion.pressed(true)
        try {
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                if (currentEvent.changes.count { it.pressed } > 1) throw CancellationException("Multiple pointers")
                change.consume()
                latestDrag.update(if (absoluteDrag) change.position.x else over, size.width.toFloat())
            }
            if (drag != null) {
                complete = horizontalDrag(drag.id) { change ->
                    if (currentEvent.changes.count { it.pressed } > 1) throw CancellationException("Multiple pointers")
                    latestDrag.update(
                        if (absoluteDrag) change.position.x else change.position.x - change.previousPosition.x,
                        size.width.toFloat(),
                    )
                    change.consume()
                }
                if (complete) latestFinish(true, null, size.width.toFloat())
            } else {
                val end = currentEvent.changes.firstOrNull { it.id == down.id }
                if (end != null && !end.pressed && !end.isConsumed) {
                    complete = true
                    end.consume()
                    latestFinish(false, end.position.x / size.width.coerceAtLeast(1), size.width.toFloat())
                }
            }
        } finally {
            interactionSource?.tryEmit(if (complete) PressInteraction.Release(pressInteraction) else PressInteraction.Cancel(pressInteraction))
            if (!complete) latestCancel()
            motion.pressed(false)
        }
    }
}
}

/** Fill the lens's complete sampling area before compositing its sibling track. */
private class LiquidValueBackdrop(
    private val track: Backdrop,
    private val background: Color,
    private val press: () -> Float,
    private val compressTrack: Boolean,
) : Backdrop {
    override val isCoordinatesDependent: Boolean = track.isCoordinatesDependent
    override val offsetResidualX: Float get() = track.offsetResidualX
    override val offsetResidualY: Float get() = track.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        // The effect's recording scope includes refraction/blur padding. Cover all of it;
        // a 6dp slider track alone leaves the enlarged lens sampling transparent pixels.
        drawRect(background)
        if (compressTrack) {
            val progress = press().coerceIn(0f, 1f)
            scale(
                scaleX = androidx.compose.ui.util.lerp(2f / 3f, 0.75f, progress),
                scaleY = androidx.compose.ui.util.lerp(0f, 0.75f, progress),
            ) {
                with(track) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
            }
        } else {
            with(track) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        }
    }
}

private val liquidValueThumbRim = EdgeStrokes.MiddleLight.copy(
    width = (0.5f / 1.5f).dp,
    peak = EdgePeak.Vertical,
)

@Composable
private fun LiquidValueThumb(
    motion: LiquidValueMotion, track: Backdrop, modifier: Modifier, thumbSize: DpSize,
    opticsActive: Boolean,
    thumbColor: Color = appControlThumbColor(),
    content: (@Composable () -> Unit)? = null,
) {
    val lensScale = (minOf(thumbSize.width, thumbSize.height) / 24.dp).coerceAtMost(1f)
    val surfaceModifier = if (opticsActive) {
        Modifier
            .drawBackdrop(
                backdrop = track,
                shape = { CircleShape },
                effects = {
                    val progress = motion.press.value
                    if (progress > 0.001f) {
                        blur(8.dp.toPx() * (1f - progress))
                        lens(
                            refractionHeight = 5.dp.toPx() * lensScale * progress,
                            refractionAmount = 10.dp.toPx() * lensScale * progress,
                            chromaticAberration = 0.24f,
                        )
                    }
                },
                onDrawFront = { drawEdgeStroke(CircleShape, liquidValueThumbRim, motion.press.value.coerceIn(0f, 1f)) },
                layerBlock = {
                    val velocity = motion.velocity.value
                    scaleX = LiquidIndicatorMotion.scaleX(motion.scaleX.value, velocity)
                    scaleY = LiquidIndicatorMotion.scaleY(motion.scaleY.value, velocity)
                },
                onDrawSurface = { drawRect(thumbColor.copy(alpha = 1f - motion.press.value.coerceIn(0f, 1f))) },
            )
            .innerShadow(CircleShape) {
                radius = 4.dp.toPx() * motion.press.value
                alpha = motion.press.value.coerceIn(0f, 1f)
                color = Color.Black.copy(alpha = 0.15f)
            }
    } else {
        // 静止态圆钮 100% 不透明，直接绘制实色表面，避免空转 drawBackdrop 与内阴影通道。
        Modifier
            .graphicsLayer {
                val velocity = motion.velocity.value
                scaleX = LiquidIndicatorMotion.scaleX(motion.scaleX.value, velocity)
                scaleY = LiquidIndicatorMotion.scaleY(motion.scaleY.value, velocity)
            }
            .background(thumbColor, CircleShape)
    }
    Box(
        modifier
            .dropShadow(CircleShape, Shadow(radius = 4.dp, color = Color.Black, alpha = 0.05f))
            .then(surfaceModifier)
            .size(thumbSize),
        contentAlignment = Alignment.Center,
    ) { content?.invoke() }
}

@Composable
internal fun AppLiquidSwitch(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier,
    enabled: Boolean, thumbContent: (@Composable () -> Unit)?,
    interactionSource: MutableInteractionSource?,
) {
    val geometry = AppLiquidControlGeometry
    val thisDensity = androidx.compose.ui.platform.LocalDensity.current.density
    var trackWidthPx by remember(geometry.switchTrack, thisDensity) {
        mutableFloatStateOf(geometry.switchTrack.width.value * thisDensity)
    }
    fun switchTravel(): Float {
        val thumb = (geometry.thumb.width.value * thisDensity).coerceAtMost(trackWidthPx)
        val inset = (geometry.switchInset.value * thisDensity).coerceAtMost((trackWidthPx - thumb) / 2f)
        return (trackWidthPx - thumb - inset * 2f).coerceAtLeast(0f)
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticsEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.hapticFeedbackEnabled
    val scope = rememberCoroutineScope()
    val motionEnabled = LocalComponentMotionEnabled.current
    val motion = remember(scope, motionEnabled) { LiquidValueMotion(scope, if (checked) 1f else 0f, motionEnabled) }
    val interactive = enabled && onCheckedChange != null
    val latestChecked by rememberUpdatedState(checked)
    val latestChange by rememberUpdatedState(onCheckedChange)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val latestLtr by rememberUpdatedState(isLtr)
    val opticsActive by remember(motion) { derivedStateOf { motion.press.value > 0.001f } }
    val trackSource = com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource(
        recordingEnabled = opticsActive && com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current,
    )
    val track = trackSource.backdrop
    val active = if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidControlsUseThemeColor) {
        com.android.purebilibili.core.ui.liquidControlThemeColor()
    } else colorResource(R.color.liquid_switch_active)
    val inactive = colorResource(R.color.liquid_value_inactive)
    val background = AppSurfaceTokens.background()
    // 仅液态开关按实际开启轨道选择圆钮前景，避免混用原生主题的 onPrimary。
    val activeTrackColor = opaqueCompositeOver(active, background)
    val activeThumbColor = if (
        calculateContrastRatio(Color.Black, activeTrackColor) >=
        calculateContrastRatio(Color.White, activeTrackColor)
    ) Color.Black else Color.White
    val switchThumbColor = when (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidSwitchThumbMode) {
        com.android.purebilibili.core.ui.LiquidSwitchThumbMode.THEME ->
            if (checked) activeThumbColor else appControlThumbColor()
        com.android.purebilibili.core.ui.LiquidSwitchThumbMode.DARK -> Color(0xFF202124)
        com.android.purebilibili.core.ui.LiquidSwitchThumbMode.LIGHT -> Color.White
    }
    val switchBackdrop = remember(track, background, motion) {
        LiquidValueBackdrop(track, background, { motion.press.value }, compressTrack = true)
    }
    val rowIntent = LocalLiquidSwitchIntent.current
    LaunchedEffect(checked, motion, rowIntent) {
        val next = if (checked) 1f else 0f
        if (rowIntent != null && rowIntent.requested == checked) {
            rowIntent.requested = null
            motion.animateSelection(next)
        } else {
            motion.synchronize(next)
        }
    }
    fun select(next: Boolean) {
        if (hapticsEnabled && next != latestChecked) haptic.performHapticFeedback(if (next) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff)
        motion.finishGesture(if (next) 1f else 0f)
        latestChange?.invoke(next)
    }
    fun activate() { motion.pressed(true); select(!latestChecked); motion.pressed(false) }
    Box(modifier
        .sizeIn(minWidth = geometry.switchTrack.width, minHeight = geometry.controlHeight)
        .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
        .semantics {
            role = Role.Switch
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            if (!enabled) disabled()
            if (interactive) onClick { activate(); true }
        }
        .onKeyEvent {
            if (interactive && it.type == KeyEventType.KeyUp && (it.key == Key.Spacebar || it.key == Key.Enter || it.key == Key.DirectionCenter)) {
                activate(); true
            } else false
        }
        .focusable(enabled = interactive)
        .then(if (interactive) Modifier.liquidValueGesture(
            motion,
            absoluteDrag = false,
            onDrag = LiquidValueDrag { delta, _ ->
                val travel = switchTravel()
                if (travel > 0f) motion.trackPointer(motion.target + delta / travel * if (latestLtr) 1f else -1f)
            },
            onFinish = { dragged, _, _ -> select(if (dragged) motion.target >= 0.5f else !latestChecked) },
            onCancel = { motion.finishGesture(if (latestChecked) 1f else 0f) },
            interactionSource = interactionSource,
        ) else Modifier), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.align(Alignment.CenterStart).size(geometry.switchTrack)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .then(trackSource.modifier).clip(CircleShape)
            .drawBehind { drawRect(lerp(inactive, active, motion.position.coerceIn(0f, 1f))) })
        Box(Modifier.matchParentSize()) {
            LiquidValueThumb(motion, switchBackdrop, Modifier.align(Alignment.CenterStart).graphicsLayer {
                val fraction = if (isLtr) motion.position else 1f - motion.position
                val inset = geometry.switchInset.toPx().coerceAtMost((trackWidthPx - size.width).coerceAtLeast(0f) / 2f)
                translationX = inset + (trackWidthPx - size.width - inset * 2f).coerceAtLeast(0f) * fraction
            }, geometry.thumb, opticsActive = opticsActive, thumbColor = switchThumbColor, content = thumbContent)
        }
    }
}

@Composable
internal fun AppLiquidSlider(
    value: Float, onValueChange: (Float) -> Unit, modifier: Modifier,
    valueRange: ClosedFloatingPointRange<Float>, steps: Int, onValueChangeFinished: (() -> Unit)?,
    colors: AppSliderColors?, enabled: Boolean, interactionSource: MutableInteractionSource?,
) {
    val geometry = AppLiquidControlGeometry
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun sliderInsetPx(widthPx: Float): Float =
        minOf(with(density) { geometry.sliderInset.toPx() }, widthPx.coerceAtLeast(0f) / 2f)
    val boundsModifier = modifier.fillMaxWidth().height(geometry.controlHeight)
    val scope = rememberCoroutineScope()
    val span = valueRange.endInclusive - valueRange.start
    fun fraction(value: Float) = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val motionEnabled = LocalComponentMotionEnabled.current
    val motion = remember(scope, valueRange, motionEnabled) { LiquidValueMotion(scope, fraction(value), motionEnabled) }
    var rawDragFraction by remember(valueRange) { mutableFloatStateOf(fraction(value)) }
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinish by rememberUpdatedState(onValueChangeFinished)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val latestLtr by rememberUpdatedState(isLtr)
    val opticsActive by remember(motion) { derivedStateOf { motion.press.value > 0.001f } }
    val trackSource = com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource(
        recordingEnabled = opticsActive && com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current,
    )
    val track = trackSource.backdrop
    val background = AppSurfaceTokens.background()
    val sliderBackdrop = remember(track, background, motion) {
        LiquidValueBackdrop(track, background, { motion.press.value }, compressTrack = false)
    }
    val active = colors?.activeTrackColor ?: if (
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidControlsUseThemeColor
    ) com.android.purebilibili.core.ui.liquidControlThemeColor() else colorResource(R.color.liquid_slider_active)
    val inactive = colors?.inactiveTrackColor ?: colorResource(R.color.liquid_value_inactive)
    fun update(progress: Float, fromPointer: Boolean = false) {
        val bounded = progress.coerceIn(0f, 1f)
        val snapped = if (steps > 0) (bounded * (steps + 1)).roundToInt().toFloat() / (steps + 1) else bounded
        if (fromPointer) motion.trackPointer(bounded) else motion.finishGesture(snapped)
        latestChange(valueRange.start + snapped * span)
    }
    LaunchedEffect(value, valueRange, motion) { motion.update(fraction(value)) }
    val trackHeight = geometry.sliderTrackHeight
    val thumbSize = geometry.thumb
    var sliderWidthPx by remember(density) { mutableFloatStateOf(with(density) { 144.dp.toPx() }) }
    Box(
        boundsModifier
            .onSizeChanged { if (it.width > 0) sliderWidthPx = it.width.toFloat() }
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
                if (!enabled) disabled()
                if (enabled) setProgress { update(fraction(it)); latestFinish?.invoke(); true }
            }
            .onKeyEvent {
                if (enabled && it.type == KeyEventType.KeyDown && (it.key == Key.DirectionLeft || it.key == Key.DirectionRight)) {
                    val direction = if ((it.key == Key.DirectionRight) == latestLtr) 1f else -1f
                    update(motion.target + direction / if (steps > 0) (steps + 1) else 20)
                    latestFinish?.invoke(); true
                } else false
            }
            .focusable(enabled = enabled)
            .then(if (enabled) Modifier.liquidValueGesture(
                motion,
                absoluteDrag = true,
                onDrag = LiquidValueDrag { position, width ->
                    rawDragFraction = resolveAppSliderPointerFraction(
                        positionPx = position,
                        widthPx = width,
                        insetPx = sliderInsetPx(width),
                        isLtr = latestLtr,
                    )
                    update(rawDragFraction, fromPointer = true)
                },
                onFinish = { dragged, position, width ->
                    if (dragged) update(rawDragFraction)
                    else if (position != null) {
                        update(resolveAppSliderPointerFraction(
                            positionPx = position * width,
                            widthPx = width,
                            insetPx = sliderInsetPx(width),
                            isLtr = latestLtr,
                        ))
                    }
                    latestFinish?.invoke()
                },
                onCancel = { motion.finishGesture(fraction(latestValue)) },
                interactionSource = interactionSource,
            ) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth()
            .padding(horizontal = with(density) { sliderInsetPx(sliderWidthPx).toDp() })
            .height(trackHeight).then(trackSource.modifier).clip(CircleShape).background(inactive)) {
            Box(Modifier.fillMaxSize().drawBehind {
                val progress = motion.position.coerceIn(0f, 1f)
                val left = if (isLtr) 0f else size.width * (1f - progress)
                drawRect(active, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(size.width * progress, size.height))
            })
        }
        LiquidValueThumb(motion, sliderBackdrop, Modifier.graphicsLayer {
            val progress = if (isLtr) motion.position else 1f - motion.position
            val insetPx = sliderInsetPx(sliderWidthPx)
            val travel = (sliderWidthPx - insetPx * 2f).coerceAtLeast(0f)
            translationX = insetPx - size.width / 2f + travel * progress
        }, thumbSize, opticsActive = opticsActive, thumbColor = colors?.thumbColor ?: appControlThumbColor())
    }
}
