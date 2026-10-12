/*
 * Adapted from Kyant AndroidLiquidGlass LiquidToggle, LiquidSlider and DampedDragAnimation.
 * Copyright 2025 Kyant. Apache-2.0; see assets/licenses/AndroidLiquidGlass.LICENSE.
 * BiliPai: native theme fallback, safe sibling track capture, horizontal gesture ownership,
 * discrete values, accessibility actions, and interruption-safe coroutine jobs.
 */
package com.android.purebilibili.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import top.yukonga.miuix.kmp.blur.*
import com.android.purebilibili.core.ui.effect.lens
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import androidx.compose.ui.res.colorResource
import com.android.purebilibili.designsystem.R
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.MediaContrastPalette
import kotlin.math.roundToInt

private class LiquidValueMotion(private val scope: CoroutineScope, initial: Float, private val motionEnabled: Boolean) {
    private val value = Animatable(initial)
    private var pointerValue by mutableStateOf<Float?>(null)
    // Read in graphicsLayer/drawBehind: pointer updates bypass coroutine/frame scheduling.
    val position: Float get() = pointerValue ?: value.value
    val press = Animatable(0f)
    val scaleX = Animatable(1f)
    val scaleY = Animatable(1f)
    val velocity = Animatable(0f)
    var target = initial
        private set
    private var valueJob: Job? = null
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var selectionPulseJob: Job? = null
    private var velocityJob: Job? = null
    private var lastFrameNanos = 0L
    private var lastFrameValue = initial

    fun beginGesture() {
        selectionPulseJob?.cancel()
        val current = position
        valueJob?.cancel()
        velocityJob?.cancel()
        pointerValue = current
        target = current
    }

    fun trackPointer(next: Float) {
        val bounded = next.coerceIn(0f, 1f)
        target = bounded
        pointerValue = bounded
    }

    fun finishGesture(next: Float) {
        val current = pointerValue
        if (current == null) {
            update(next)
            return
        }
        target = next.coerceIn(0f, 1f)
        valueJob?.cancel()
        velocityJob?.cancel()
        val settledTarget = target
        valueJob = scope.launch {
            // Seed animation from the final pointer position before releasing its draw state.
            // Both mutations happen before suspension, so there is no jump to an old frame.
            value.snapTo(current)
            velocity.snapTo(0f)
            pointerValue = null
            if (motionEnabled) value.animateTo(settledTarget, spring(1f, 1000f, 0.001f))
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
                velocityJob?.cancel()
                velocity.snapTo(0f)
                value.snapTo(target)
                return@launch
            }
            lastFrameNanos = 0L
            lastFrameValue = value.value
            value.animateTo(target, spring(1f, 1000f, 0.001f)) {
                val now = System.nanoTime()
                if (lastFrameNanos != 0L) {
                    val deltaSeconds = (now - lastFrameNanos) / 1_000_000_000f
                    if (deltaSeconds > 0f) {
                        val speed = (value - lastFrameValue) / deltaSeconds
                        velocityJob?.cancel()
                        velocityJob = scope.launch { this@LiquidValueMotion.velocity.animateTo(speed, spring(0.5f, 300f, 0.01f)) }
                    }
                }
                lastFrameValue = value
                lastFrameNanos = now
            }
            velocityJob?.cancel()
            velocityJob = scope.launch { velocity.animateTo(0f, spring(0.5f, 300f, 0.01f)) }
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
            launch { press.animateTo(if (active) 1f else 0f, spring(1f, 1000f, 0.001f)) }
            launch { scaleX.animateTo(if (active) 1.5f else 1f, spring(0.6f, 250f, 0.001f)) }
            launch { scaleY.animateTo(if (active) 1.5f else 1f, spring(0.7f, 250f, 0.001f)) }
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
                withTimeoutOrNull(300L) {
                    snapshotFlow { position }.first { abs(it - target) < 0.025f }
                }
                withTimeoutOrNull(120L) {
                    snapshotFlow { press.value }.first { it >= 0.6f }
                }
                animatePress(false)
            }
        }
    }

}

/** Horizontal drag claims input only after slop; vertical list scrolling remains native. */
private fun Modifier.liquidValueGesture(
    motion: LiquidValueMotion,
    onDelta: (Float, Float) -> Unit,
    onFinish: (Boolean, Float?) -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
): Modifier = pointerInput(motion, interactionSource) {
    awaitEachGesture {
        val down = awaitFirstDown(false, PointerEventPass.Initial)
        var complete = false
        val pressInteraction = PressInteraction.Press(down.position)
        interactionSource?.tryEmit(pressInteraction)
        motion.beginGesture()
        onStart()
        motion.pressed(true)
        try {
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                if (currentEvent.changes.count { it.pressed } > 1) throw CancellationException("Multiple pointers")
                change.consume()
                onDelta(over, size.width.toFloat())
            }
            if (drag != null) {
                complete = horizontalDrag(drag.id) { change ->
                    if (currentEvent.changes.count { it.pressed } > 1) throw CancellationException("Multiple pointers")
                    onDelta(change.position.x - change.previousPosition.x, size.width.toFloat())
                    change.consume()
                }
                if (complete) onFinish(true, null)
            } else {
                val end = currentEvent.changes.firstOrNull { it.id == down.id }
                if (end != null && !end.pressed && !end.isConsumed) {
                    complete = true
                    end.consume()
                    onFinish(false, end.position.x / size.width.coerceAtLeast(1))
                }
            }
        } finally {
            interactionSource?.tryEmit(if (complete) PressInteraction.Release(pressInteraction) else PressInteraction.Cancel(pressInteraction))
            if (!complete) onCancel()
            motion.pressed(false)
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
        downscaleFactor: Int,
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

@Composable
private fun LiquidValueThumb(
    motion: LiquidValueMotion, track: Backdrop, modifier: Modifier,
    thumbColor: Color = MediaContrastPalette.Foreground,
    content: (@Composable () -> Unit)? = null,
) {
    Box(modifier
        .dropShadow(CircleShape, Shadow(radius = 4.dp, color = Color.Black, alpha = 0.05f))
        .drawBackdrop(
            backdrop = track,
            shape = { CircleShape },
            effects = {
                val progress = motion.press.value
                blur(8.dp.toPx() * (1f - progress))
                lens(5.dp.toPx() * progress, 10.dp.toPx() * progress, chromaticAberration = 1f)
            },
            highlight = { Highlight(width = (0.5f / 1.5f).dp, alpha = motion.press.value.coerceIn(0f, 1f)) },
            layerBlock = {
                val velocity = motion.velocity.value / 50f
                scaleX = motion.scaleX.value / (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))
                scaleY = motion.scaleY.value * (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))
            },
            onDrawSurface = { drawRect(thumbColor.copy(alpha = 1f - motion.press.value.coerceIn(0f, 1f))) },
        )
        .innerShadow(CircleShape) {
            radius = 4.dp.toPx() * motion.press.value
            alpha = motion.press.value.coerceIn(0f, 1f)
            color = Color.Black.copy(alpha = 0.15f)
        }
        .size(40.dp, 24.dp), contentAlignment = Alignment.Center) { content?.invoke() }
}

@Composable
internal fun AppLiquidSwitch(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier,
    enabled: Boolean, thumbContent: (@Composable () -> Unit)?,
    interactionSource: MutableInteractionSource?,
) {
    val thisDensity = androidx.compose.ui.platform.LocalDensity.current.density
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
    val track = rememberLayerBackdrop()
    val active = colorResource(R.color.liquid_switch_active)
    val inactive = colorResource(R.color.liquid_value_inactive)
    val background = AppSurfaceTokens.background()
    val switchBackdrop = remember(track, background, motion) {
        LiquidValueBackdrop(track, background, { motion.press.value }, compressTrack = true)
    }
    LaunchedEffect(checked, motion) { motion.animateSelection(if (checked) 1f else 0f) }
    fun select(next: Boolean) {
        if (hapticsEnabled && next != latestChecked) haptic.performHapticFeedback(if (next) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff)
        motion.finishGesture(if (next) 1f else 0f)
        latestChange?.invoke(next)
    }
    fun activate() { motion.pressed(true); select(!latestChecked); motion.pressed(false) }
    Box(modifier
        .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
        .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
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
            onDelta = { delta, _ -> motion.trackPointer(motion.target + delta / (20.dp.value * thisDensity) * if (latestLtr) 1f else -1f) },
            onFinish = { dragged, _ -> select(if (dragged) motion.target >= 0.5f else !latestChecked) },
            onCancel = { motion.finishGesture(if (latestChecked) 1f else 0f) },
            interactionSource = interactionSource,
        ) else Modifier), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.align(Alignment.CenterStart).size(64.dp, 28.dp).layerBackdrop(track).clip(CircleShape)
            .drawBehind { drawRect(lerp(inactive, active, motion.position.coerceIn(0f, 1f))) })
        LiquidValueThumb(motion, switchBackdrop, Modifier.align(Alignment.CenterStart).graphicsLayer {
            val fraction = if (isLtr) motion.position else 1f - motion.position
            translationX = 2.dp.toPx() + 20.dp.toPx() * fraction
        }, content = thumbContent)
    }
}

@Composable
internal fun AppLiquidSlider(
    value: Float, onValueChange: (Float) -> Unit, modifier: Modifier,
    valueRange: ClosedFloatingPointRange<Float>, steps: Int, onValueChangeFinished: (() -> Unit)?,
    colors: AppSliderColors?, enabled: Boolean, interactionSource: MutableInteractionSource?,
) {
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
    val track = rememberLayerBackdrop()
    val background = AppSurfaceTokens.background()
    val sliderBackdrop = remember(track, background, motion) {
        LiquidValueBackdrop(track, background, { motion.press.value }, compressTrack = false)
    }
    val active = colors?.activeTrackColor ?: colorResource(R.color.liquid_slider_active)
    val inactive = colors?.inactiveTrackColor ?: colorResource(R.color.liquid_value_inactive)
    fun update(progress: Float, fromPointer: Boolean = false) {
        val bounded = progress.coerceIn(0f, 1f)
        val snapped = if (steps > 0) (bounded * (steps + 1)).roundToInt().toFloat() / (steps + 1) else bounded
        if (fromPointer) motion.trackPointer(bounded) else motion.finishGesture(snapped)
        latestChange(valueRange.start + snapped * span)
    }
    LaunchedEffect(value, valueRange, motion) { motion.update(fraction(value)) }
    BoxWithConstraints(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
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
                onDelta = { delta, width ->
                    if (width > 0f) {
                        rawDragFraction = (rawDragFraction + delta / width * if (latestLtr) 1f else -1f).coerceIn(0f, 1f)
                        update(rawDragFraction, fromPointer = true)
                    }
                },
                onStart = { rawDragFraction = motion.target },
                onFinish = { dragged, position ->
                    if (dragged) update(rawDragFraction)
                    else if (position != null) update(if (latestLtr) position else 1f - position)
                    latestFinish?.invoke()
                },
                onCancel = { motion.finishGesture(fraction(latestValue)) },
                interactionSource = interactionSource,
            ) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 144.dp.value * androidx.compose.ui.platform.LocalDensity.current.density
        Box((if (constraints.hasBoundedWidth) Modifier.fillMaxWidth() else Modifier.width(144.dp)).height(6.dp).layerBackdrop(track).clip(CircleShape).background(inactive)) {
            Box(Modifier.fillMaxSize().drawBehind {
                val progress = motion.position.coerceIn(0f, 1f)
                val left = if (isLtr) 0f else size.width * (1f - progress)
                drawRect(active, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(size.width * progress, size.height))
            })
        }
        LiquidValueThumb(motion, sliderBackdrop, Modifier.graphicsLayer {
            val progress = if (isLtr) motion.position else 1f - motion.position
            translationX = (-size.width / 2f + width * progress).coerceIn(-size.width / 4f, (width - size.width * 3f / 4f).coerceAtLeast(-size.width / 4f))
        }, thumbColor = colors?.thumbColor ?: MediaContrastPalette.Foreground)
    }
}
