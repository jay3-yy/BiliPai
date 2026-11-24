package com.android.purebilibili.core.ui.animation

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

val LocalLiquidIndicatorWobbleEnabled = compositionLocalOf { true }

/** Interaction policy owns the impulse; Compose owns spring timing, cancellation and settling. */
internal class LiquidIndicatorWobble(
    initialPosition: Float,
    private val scope: CoroutineScope,
) {
    private var position = initialPosition
    private var peakSpeed = 0f
    private var distance = 0f
    private var armed = false
    private var released = false
    private var landingConsumed = false
    private var worker: Job? = null
    private var animation by mutableStateOf(AnimationState(0f))
    val velocityWeight: Float get() = 1f
    val stretch: Float get() = animation.value
    var isSelectionMotion by mutableStateOf(false)
        private set

    private fun stop() {
        worker?.cancel()
        worker = null
        animation = AnimationState(0f)
        isSelectionMotion = false
    }

    fun onSelectionChanged(preserveRelease: Boolean = false) {
        if (preserveRelease && released) return
        stop()
        released = false
        landingConsumed = false
        armed = true
        peakSpeed = max(peakSpeed, 1.5f)
    }

    fun onInteractionStarted() = reset(position)

    fun onInteractionReleased(nextPosition: Float = position) {
        val pendingDistance = if (nextPosition.isFinite()) abs(nextPosition - position) else 0f
        if (!armed && distance + pendingDistance < 0.02f) return
        stop()
        released = true
        landingConsumed = false
        armed = true
        peakSpeed = max(peakSpeed, pendingDistance * 60f)
    }

    /** Samples describe real input time, independent of the system animation duration scale. */
    fun advance(
        nextPosition: Float,
        elapsedNanos: Long,
        canSettle: Boolean = true,
        manualInteraction: Boolean = false,
        holdSelectionMotion: Boolean = false,
        landingMotion: Boolean = false,
    ): Boolean {
        if (!nextPosition.isFinite() || elapsedNanos > 500_000_000L) {
            reset(if (nextPosition.isFinite()) nextPosition else position)
            return false
        }
        if (elapsedNanos <= 0L) return false
        val delta = abs(nextPosition - position)
        position = nextPosition
        val moving = delta > 0.0001f
        if (!landingMotion) landingConsumed = false
        if (moving && !landingConsumed) {
            peakSpeed = max(peakSpeed, (delta * 1_000_000_000f / elapsedNanos).coerceAtMost(30f))
            distance += delta
            armed = armed || distance >= 0.02f
        }
        if (manualInteraction || !canSettle || (!landingMotion && (moving || holdSelectionMotion))) {
            if (!moving && isSelectionMotion) {
                armed = false
                distance = 0f
                peakSpeed = 0f
            }
            stop()
            return moving
        }
        if (armed) {
            armed = false
            distance = 0f
            landingConsumed = true
            val speed = peakSpeed
            peakSpeed = 0f
            stop()
            isSelectionMotion = true
            val current = AnimationState(
                initialValue = 0f,
                initialVelocity = -(1.8f + 0.5f * (1f - 2f / (exp(2f * speed / 3f) + 1f))),
            )
            animation = current
            worker = scope.launch {
                current.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 300f, visibilityThreshold = 0.001f))
                animation = AnimationState(0f)
                isSelectionMotion = false
                released = false
            }
        }
        return worker?.isActive == true
    }

    fun reset(nextPosition: Float) {
        stop()
        position = nextPosition
        peakSpeed = 0f
        distance = 0f
        armed = false
        released = false
        landingConsumed = false
    }
}

class LiquidIndicatorWobbleState internal constructor(val enabled: Boolean) : State<Float> {
    internal var source: () -> Float = { 0f }
    internal var selectionMotion: () -> Boolean = { false }
    override val value: Float get() = if (enabled) source() else 0f
    val isSelectionMotion: Boolean get() = enabled && selectionMotion()
    val velocityWeight: Float get() = 1f
}

/** Observe input changes only; the spring owns frame work and stops when it settles. */
@Composable
fun rememberLiquidIndicatorWobble(
    enabled: Boolean,
    geometryKey: Any,
    canSettle: () -> Boolean = { true },
    selectionKey: () -> Any? = { null },
    interactionActive: () -> Boolean = { false },
    selectionMotionActive: () -> Boolean? = { null },
    position: () -> Float,
): LiquidIndicatorWobbleState {
    val latestPosition = rememberUpdatedState(position)
    val latestCanSettle = rememberUpdatedState(canSettle)
    val latestSelectionKey = rememberUpdatedState(selectionKey)
    val latestInteractionActive = rememberUpdatedState(interactionActive)
    val latestSelectionMotion = rememberUpdatedState(selectionMotionActive)
    val motionEnabled = enabled && LocalLiquidIndicatorWobbleEnabled.current &&
        com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
        !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    val scope = rememberCoroutineScope()
    val wobble = remember(motionEnabled, geometryKey) { LiquidIndicatorWobble(position(), scope) }
    val result = remember(motionEnabled, geometryKey) { LiquidIndicatorWobbleState(motionEnabled) }
    // Frame reads are confined to the indicator consumer, including the official spring state.
    result.source = { wobble.stretch.coerceIn(-0.18f, 0.18f) }
    result.selectionMotion = { wobble.isSelectionMotion }
    DisposableEffect(wobble) {
        onDispose { wobble.reset(latestPosition.value()) }
    }
    LaunchedEffect(wobble) {
        if (!motionEnabled) return@LaunchedEffect
        var observedPosition = latestPosition.value()
        var observedCanSettle = latestCanSettle.value()
        var observedSelectionKey = latestSelectionKey.value()
        var observedManual = false
        var observedSelectionMotion: Boolean? = false
        var pendingPosition = observedPosition
        var pendingCanSettle = observedCanSettle
        var pendingSelectionKey = observedSelectionKey
        var pendingManual = observedManual
        var pendingSelectionMotion = observedSelectionMotion
        var revision = 0
        var previousNanos = System.nanoTime()
        snapshotFlow {
            val nextPosition = latestPosition.value()
            val nextCanSettle = latestCanSettle.value()
            val nextSelectionKey = latestSelectionKey.value()
            val nextManual = latestInteractionActive.value()
            val nextSelectionMotion = latestSelectionMotion.value()
            if (nextPosition != pendingPosition ||
                nextCanSettle != pendingCanSettle ||
                nextSelectionKey != pendingSelectionKey ||
                nextManual != pendingManual ||
                nextSelectionMotion != pendingSelectionMotion
            ) {
                pendingPosition = nextPosition
                pendingCanSettle = nextCanSettle
                pendingSelectionKey = nextSelectionKey
                pendingManual = nextManual
                pendingSelectionMotion = nextSelectionMotion
                revision++
            }
            revision
        }.collect {
            val nextPosition = pendingPosition
            val nextCanSettle = pendingCanSettle
            val nextSelectionKey = pendingSelectionKey
            val nextManual = pendingManual
            val nextSelectionMotion = pendingSelectionMotion
            if (nextPosition != observedPosition ||
                nextCanSettle != observedCanSettle ||
                nextSelectionKey != observedSelectionKey ||
                nextManual != observedManual ||
                nextSelectionMotion != observedSelectionMotion
            ) {
                val now = System.nanoTime()
                val selectionChanged = nextSelectionKey != observedSelectionKey
                val pressed = !observedManual && nextManual
                val released = observedManual && !nextManual
                val tapStarted = nextSelectionMotion == true && observedSelectionMotion != true
                if (pressed) wobble.onInteractionStarted()
                else if (released) wobble.onInteractionReleased(nextPosition)
                else if (!nextManual && (selectionChanged || tapStarted)) {
                    wobble.onSelectionChanged(preserveRelease = !tapStarted)
                }
                // An idle interval is not a suspended animation: explicit new input still arms recoil.
                val elapsed = if (selectionChanged || pressed || released || tapStarted) 16_666_667L
                    else (now - previousNanos).coerceIn(1L, 50_000_000L)
                wobble.advance(nextPosition, elapsed, nextCanSettle, nextManual,
                    nextSelectionMotion == true, landingMotion = nextCanSettle)
                previousNanos = now
                observedPosition = nextPosition
                observedCanSettle = nextCanSettle
                observedSelectionKey = nextSelectionKey
                observedManual = nextManual
                observedSelectionMotion = nextSelectionMotion
            }
        }
    }
    return result
}

/** Recoil joins braking within the last 2.5% of the available travel range. */
fun isLiquidIndicatorNearLanding(position: Float, target: Float, range: Float): Boolean =
    position.isFinite() && target.isFinite() && range.isFinite() &&
        abs(position - target) <= (abs(range) * LiquidIndicatorMotion.ReleaseDistanceFraction).coerceAtLeast(0.001f)

/** Shared landing policy for taps, indicator drags and page swipes. Held input always wins. */
fun canStartLiquidIndicatorRecoil(
    position: Float,
    target: Float,
    range: Float,
    interactionActive: Boolean,
    returning: Boolean = true,
): Boolean = !interactionActive && returning && isLiquidIndicatorNearLanding(position, target, range)

fun liquidWobbleScaleX(base: Float, stretch: Float): Float = base * (1f + stretch)
fun liquidWobbleScaleY(base: Float, stretch: Float): Float = base * (1f - stretch * 1.4f)

/** Material stays active through the visible shape's return, including spring undershoot. */
fun liquidIndicatorShapeProgress(scaleX: Float, scaleY: Float, bloomScale: Float): Float =
    (max(abs(scaleX - 1f), abs(scaleY - 1f)) / (bloomScale - 1f).coerceAtLeast(0.18f))
        .coerceIn(0f, 1f)

/** Rendering checks interaction state directly so a previous recoil never leaks into a new drag. */
fun resolveLiquidIndicatorRecoilStretch(stretch: Float, canSettle: Boolean): Float =
    if (canSettle) stretch else 0f

/** Logical-axis shift: compress against an endpoint, then peel away as the shape rebounds. */
fun liquidWobbleEdgeTranslationPx(
    stretch: Float,
    position: Float,
    lastIndex: Int,
    extentPx: Float,
    edgeInsetPx: Float,
    baseScale: Float = 1f,
): Float {
    if (lastIndex <= 0 || !position.isFinite() || !stretch.isFinite() || extentPx <= 0f || stretch == 0f) return 0f
    val edgeDistance: Float
    val edge = when {
        position < 0.35f -> { edgeDistance = abs(position); -1f }
        position > lastIndex - 0.35f -> { edgeDistance = abs(position - lastIndex); 1f }
        else -> return 0f
    }
    val proximity = (1f - edgeDistance / 0.35f).coerceIn(0f, 1f)
    val edgeWeight = proximity * proximity * (3f - 2f * proximity)
    val inset = edgeInsetPx.coerceAtLeast(0f)
    // Close the measured gap early enough for both tap braking and release recoil.
    // Compensate the width lost to squash and any remaining press magnification.
    val contact = if (stretch < 0f) (-stretch / 0.02f).coerceAtMost(1f)
        else (stretch / 0.04f).coerceAtMost(1f)
    // Bloom compensation is much larger than the inset. Ramping it with the
    // 0.02 squash threshold made every zero crossing shove a magnified endpoint
    // sideways. Ease that correction over the shape's full collision range;
    // keep squash/rebound amplitude and full-contact geometry unchanged.
    val magnificationProgress = (abs(stretch) / 0.1f).coerceIn(0f, 1f)
    val magnificationContact = magnificationProgress * magnificationProgress *
        (3f - 2f * magnificationProgress)
    val magnificationCorrection = extentPx * (1f - baseScale) * (1f + stretch) / 2f * magnificationContact
    val shift = -stretch * extentPx / 2f + magnificationCorrection +
        (if (stretch < 0f) contact else -contact) * inset
    return edge * shift * edgeWeight
}
