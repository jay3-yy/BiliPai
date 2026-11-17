// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.nav.runtime

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Anchored gesture progress (grab-anchor model, spec 2026-06-10 §3.1 / invariant 6).
 *
 * A gesture may claim the stack while the shared spring is still mid-flight. [anchor] is the
 * progress already travelled toward pop at the claim instant (`topIndex - animatedTop.value`,
 * signed): positive when grabbed mid-push, negative when grabbed while a pop-settle is still
 * reeling a leaving entry out above the new top. The total progress is strictly additive —
 * `anchor + fingerProgress` — so the slope stays exactly 1 (1:1 with the finger, no easing on
 * this axis) and the first frame (`fingerProgress == 0`) maps back to the sampled anchor: zero
 * jump by construction.
 *
 * Clamp range:
 * - upper bound [NavDriverSpec.MAX_FINGER_PROGRESS], just under the fully-popped end (see the
 *   constant for why exactly 1 is never reachable by a finger); an `anchor > 0` grab saturates
 *   early (the page pins at the end while the finger keeps travelling), matching the reference
 *   interactive-pop feel.
 * - lower bound `min(anchor, 0f)`: with `anchor >= 0` a reverse drag can at most push the page
 *   back to rest (never into the covered regime — the static analogue of the cancel velocity
 *   clamp); with `anchor < 0` it can at most freeze the leaving entry at the grab point, never
 *   re-revealing an already-popped page (the back stack no longer holds it).
 *
 * @param anchor progress toward pop at the claim instant, signed; `0f` for a rest-state grab.
 * @param fingerProgress finger travel since the claim in progress units, unclamped.
 * @return total progress on the pop axis, in `min(anchor, 0f)..`[NavDriverSpec.MAX_FINGER_PROGRESS].
 */
internal fun anchoredProgress(anchor: Float, fingerProgress: Float): Float = (anchor + fingerProgress).coerceIn(anchor.coerceAtMost(0f), NavDriverSpec.MAX_FINGER_PROGRESS)

/** 手势视觉策略与驱动共享末端上限；完整退出仍由提交动画完成。 */
fun boundedNavGestureProgress(progress: Float): Float = anchoredProgress(anchor = 0f, fingerProgress = progress)

/**
 * Pure mapping from a rest-state gesture progress to the `animatedTop` target value.
 *
 * The `anchor == 0` special case of the grab-anchor model: a gesture starting from a settled
 * top (`animatedTop == topIndex`). A fully completed gesture (`progress == 1`) drives
 * `animatedTop` exactly one step toward the previous entry, i.e. `topIndex - 1`. The mapping is
 * strictly linear (1:1 with the finger): no easing lives on the `finger -> animatedTop` axis,
 * so no inverse-transform is ever needed (contrast nav3's SeekableTransitionState, which bakes
 * easing into its fraction).
 *
 * @param topIndex index of the current top entry in the back stack (`lastIndex`).
 * @param progress raw gesture progress; clamped to `0f..`[NavDriverSpec.MAX_FINGER_PROGRESS] by [anchoredProgress].
 * @return the `animatedTop` value the gesture should snap to.
 */
internal fun fingerTarget(topIndex: Int, progress: Float): Float = topIndex - anchoredProgress(anchor = 0f, fingerProgress = progress)

/**
 * Canonical constants of the shared `animatedTop` driver: the default spring/tween numbers
 * (consumed as [NavSettleSpec] defaults) and the release commit/cancel thresholds (consumed by
 * [navBackCommitDecision]). Settle curves themselves are per-transition
 * ([top.yukonga.miuix.kmp.nav.transition.NavMotion]); no other phase redefines these constants.
 */
internal object NavDriverSpec {
    /**
     * Critically damped: the default never bounces. An underdamped settle (the earlier 0.9)
     * oscillates past the target — invisible at ~0.15% when starting from rest, but a
     * velocity-seeded commit (a flung back-swipe) overshoots visibly and springs back.
     * Overshoot is per-transition opt-in ([NavSettleSpec.Spring] with `clampOvershoot = false`);
     * this constant only seeds the default.
     */
    const val DAMPING_RATIO: Float = 1f

    /**
     * Low stiffness on the depth scale (units = entries). With [DAMPING_RATIO] and the tight
     * [VISIBILITY_THRESHOLD], a full one-step push/pop settles in roughly half a second
     * (`t ≈ -ln(threshold) / (damping·√stiffness)`), matching the established miuix navigation
     * feel (a ~500ms transition) rather than a snappier default. The value keeps the decay
     * envelope of the original tuning (`1·√146 ≈ 0.9·√180`), so moving to critical damping
     * did not change the perceived duration.
     */
    const val STIFFNESS: Float = 146f

    /**
     * `animatedTop` is measured in entry-index units, so it converges visually once
     * within a few hundredths of an index. Tighter than the default 0.01 for px.
     */
    const val VISIBILITY_THRESHOLD: Float = 0.0025f

    /**
     * Duration of the default programmatic full-step tween ([NavMotion.Default]'s
     * `programmatic`). The same 500ms the established navigation uses; the curve completes in
     * exactly this time regardless of distance (a multi-step pop sweeps all layers within the
     * same window, like the reference's single content transition).
     */
    const val PROGRAMMATIC_DURATION_MILLIS: Int = 500

    /**
     * Minimum settle distance (in entry-index units) for a from-rest settle to qualify as a
     * programmatic full step ([usesProgrammaticCurve]). Programmatic pushes/pops always move
     * integer distances; anything shorter is a partial-position continuation (a released
     * gesture, an interrupted transition) and belongs to the live spring. Slightly under 1
     * to tolerate visibility-threshold residue from a previous settle.
     */
    const val FULL_STEP_THRESHOLD: Float = 0.999f

    /**
     * Velocity (in progress-units per second) above which a release is treated as a
     * deliberate fling and commits/cancels by sign alone, ignoring position.
     */
    const val COMMIT_VELOCITY_THRESHOLD: Float = 1.0f

    /** Position fallback: progress at/after which a low-velocity release commits. */
    const val COMMIT_POSITION_THRESHOLD: Float = 0.5f

    /**
     * Upper saturation of a finger-driven position ([anchoredProgress]), just under the
     * fully-popped end: `d = -1` (visible-window exit + unload) is reserved for a committed
     * settle. An overdriven gesture — back progress misreported past 1, or a full-extent
     * swipe — would otherwise blank the outgoing entry and its dim scrim mid-gesture.
     */
    const val MAX_FINGER_PROGRESS: Float = 0.999f
}

/** Maps a public settle spec onto the concrete Compose animation spec. */
internal fun NavSettleSpec.toAnimationSpec(): AnimationSpec<Float> = when (this) {
    is NavSettleSpec.Spring -> androidx.compose.animation.core.spring(
        dampingRatio = dampingRatio,
        stiffness = stiffness,
        visibilityThreshold = visibilityThreshold,
    )

    is NavSettleSpec.Tween -> tween(durationMillis = durationMillis, easing = easing)
}

/**
 * The most negative (toward-target) velocity a commit settle may be seeded with without
 * overshooting the target, given the [remainingDistance] still to travel.
 *
 * For a critically damped spring `x(t) = e^(-ωt)·(x0 + (v0 + ω·x0)·t)`, the trajectory crosses
 * zero iff `v0 < -ω·x0` — so flooring the seed at `-√stiffness · distance` is the exact
 * no-overshoot condition for the default (conservative for overdamped springs). Slower releases
 * keep their full velocity (snap -> spring continuity intact); only the excess speed that could
 * ONLY have become a visible bounce-back past the fully-popped position is dropped.
 *
 * Returns [Float.NEGATIVE_INFINITY] (no floor) when the spec opted out of clamping
 * ([NavSettleSpec.Spring.clampOvershoot] `= false`) or cannot consume a seed velocity at all
 * ([NavSettleSpec.Tween]).
 *
 * @param remainingDistance distance from the current value down to the settle target, `>= 0`.
 * @return the velocity floor to pass through `coerceAtLeast`.
 */
internal fun NavSettleSpec.commitVelocityFloor(remainingDistance: Float): Float = when {
    this is NavSettleSpec.Spring && clampOvershoot ->
        if (remainingDistance <= 0f) 0f else -sqrt(stiffness) * remainingDistance

    else -> Float.NEGATIVE_INFINITY
}

/**
 * Decides whether a released edge-swipe gesture should commit (pop the top entry)
 * or cancel (spring back), per spec §7.2 "velocity-first,
 * position-fallback".
 *
 * Velocity takes priority: a release flung hard enough in either direction wins
 * regardless of how far the finger travelled. Only when the release velocity sits
 * inside the dead zone (`|velocity| < velocityThreshold`) does position decide.
 *
 * @param progress gesture completion at release, `0f..1f` (0 = untouched, 1 = fully popped).
 * @param velocity release velocity in progress-units per second; positive points toward pop.
 * @param velocityThreshold magnitude above which velocity alone decides.
 * @param positionThreshold progress at/after which a low-velocity release commits.
 * @return `true` to commit (pop), `false` to cancel (spring back).
 */
internal fun navBackCommitDecision(
    progress: Float,
    velocity: Float,
    velocityThreshold: Float = NavDriverSpec.COMMIT_VELOCITY_THRESHOLD,
    positionThreshold: Float = NavDriverSpec.COMMIT_POSITION_THRESHOLD,
): Boolean = when {
    velocity >= velocityThreshold -> true
    velocity <= -velocityThreshold -> false
    else -> progress >= positionThreshold
}

/**
 * Decides whether a system predictive-back completion should be accepted.
 *
 * The platform completion callback is already a commit decision, so a low-velocity release must
 * not be reclassified from position. The only override is a strong velocity back toward rest,
 * which guards against OEMs that report completion while the user is visibly cancelling.
 *
 * @param velocity release velocity in progress-units per second; positive points toward pop.
 * @param velocityThreshold magnitude at/above which return velocity overrides completion.
 */
internal fun navBackCompletionDecision(
    velocity: Float,
    velocityThreshold: Float = NavDriverSpec.COMMIT_VELOCITY_THRESHOLD,
): Boolean = velocity > -velocityThreshold

/**
 * Drives [this] `animatedTop` to follow a gesture finger 1:1, with no spring or easing on the
 * path (spec §7.1, "snap mode"). Each gesture event calls this with the latest [progress]; the
 * value lands exactly on `topIndex - anchoredProgress(anchor, progress)`, so a later [settleTo]
 * can hand off from precisely where the finger left it.
 *
 * [anchor] implements the grab-anchor model ([anchoredProgress]): callers that claim the stack
 * while the shared spring is mid-flight pass the progress sampled at the claim instant, making
 * the first snap a no-op (zero jump). Rest-state gestures pass the default `0f`.
 *
 * A NaN [anchor] (a snap dispatched before the grab anchor was sampled) is a no-op: NaN written
 * into the driving float is unrecoverable — every later sample, release decision, and settle
 * integrates from the current value and nothing ever snaps it back to a finite absolute.
 *
 * @param topIndex index of the current top entry (`backStack.lastIndex`).
 * @param progress finger travel since the claim in progress units, unclamped.
 * @param anchor progress toward pop at the claim instant; see [anchoredProgress].
 */
internal suspend fun Animatable<Float, AnimationVector1D>.snapToFinger(
    topIndex: Int,
    progress: Float,
    anchor: Float = 0f,
) {
    if (anchor.isNaN()) return
    snapTo(topIndex - anchoredProgress(anchor = anchor, fingerProgress = progress))
}

/**
 * Converges [this] `animatedTop` to [target] through the governing settle curve (spec §7.1
 * "settle mode"). Used for normal push/pop and for gesture release (commit -> `topIndex - 1`,
 * cancel -> `topIndex`).
 *
 * By default [initialVelocity] is [this] Animatable's own current [velocity], so the
 * value AND its first derivative stay continuous across the snap->spring boundary,
 * eliminating the visual jolt a fresh-from-zero spring would cause. Callers that
 * track a separate finger velocity (e.g. an edge swipe whose drag delta differs
 * from the Animatable's internal velocity) may pass it explicitly. A [NavSettleSpec.Tween]
 * spec uses the seed only when `matchReleaseVelocity` is enabled; ordinary tweens ignore it.
 *
 * @param target destination value on the depth axis (an entry index, possibly fractional during interruption).
 * @param spec the settle curve; defaults to the default commit spring.
 * @param initialVelocity velocity (depth-units per second) to seed the curve with; defaults to the current [velocity].
 */
internal suspend fun Animatable<Float, AnimationVector1D>.settleTo(
    target: Float,
    spec: NavSettleSpec = NavMotion.Default.commit,
    initialVelocity: Float = velocity,
    onFrame: (() -> Unit)? = null,
) {
    val animationSpec = if (spec is NavSettleSpec.Tween && spec.matchReleaseVelocity) {
        tween<Float>(
            durationMillis = spec.durationMillis.coerceAtLeast(0),
            easing = NavVelocityHandoffEasing(
                distance = target - value,
                initialVelocity = initialVelocity,
                durationMillis = spec.durationMillis,
            ),
        )
    } else {
        spec.toAnimationSpec()
    }
    animateTo(
        targetValue = target,
        animationSpec = animationSpec,
        initialVelocity = initialVelocity,
        block = if (onFrame != null) ({ onFrame() }) else null,
    )
}

/**
 * Cancel-direction settle: converges to [target] with the rest position pinned as an upper
 * bound, so even an underdamped cancel spring can never carry the top entry past rest into the
 * covered regime (boundary ownership, input blocking and the dim scrim all flip there). Bounds
 * are cleared afterwards even if the settle is interrupted.
 */
internal suspend fun Animatable<Float, AnimationVector1D>.settleCancel(
    target: Float,
    spec: NavSettleSpec,
    initialVelocity: Float = velocity,
    onFrame: (() -> Unit)? = null,
) {
    updateBounds(lowerBound = null, upperBound = target)
    try {
        settleTo(target = target, spec = spec, initialVelocity = initialVelocity, onFrame = onFrame)
    } finally {
        updateBounds(lowerBound = null, upperBound = null)
    }
}

/**
 * Whether a settle starting with [velocity] over [distance] should play the programmatic
 * fixed-duration curve ([NavMotion.programmatic]) instead of the live spring.
 *
 * Only a from-rest, full-step settle qualifies — exactly the programmatic push/pop case, which
 * must match the established navigation curve point for point. Anything carrying velocity (an
 * interrupted tween, a gesture handoff) or covering a partial distance (a settle resumed from a
 * mid-gesture position) stays on the spring: a fixed-duration tween cannot seed velocity and
 * would mis-pace short distances.
 *
 * @param velocity the driver's velocity at settle start (depth-units per second).
 * @param distance signed distance from the current value to the target (entry-index units).
 */
internal fun usesProgrammaticCurve(velocity: Float, distance: Float): Boolean = velocity == 0f && abs(distance) >= NavDriverSpec.FULL_STEP_THRESHOLD

/**
 * Renderer-side settle: converges [this] `animatedTop` to [target], dispatching between the
 * governing transition's settle curves (spec 2026-06-10 §"programmatic curve match"):
 *
 * - **From rest over a full step** (a programmatic push/pop/multi-pop): [NavMotion.programmatic]
 *   as declared by the governing transition (the default reproduces the established 500ms curve).
 * - **Anything else** (carrying velocity from an interrupted tween, or resuming from a partial
 *   position after a gesture): the live spring via [settleTo], seeded with the current velocity
 *   so the handoff is velocity-continuous. When the declared commit phase is a tween (which
 *   cannot seed velocity), the default spring serves instead.
 *
 * @param target destination value on the depth axis.
 * @param motion the governing transition's settle physics; defaults to [NavMotion.Default].
 */
internal suspend fun Animatable<Float, AnimationVector1D>.settleProgrammatic(
    target: Float,
    motion: NavMotion = NavMotion.Default,
    onFrame: (() -> Unit)? = null,
) {
    if (usesProgrammaticCurve(velocity = velocity, distance = target - value)) {
        animateTo(
            targetValue = target,
            animationSpec = motion.programmatic.toAnimationSpec(),
            block = if (onFrame != null) ({ onFrame() }) else null,
        )
    } else {
        val spring = motion.commit as? NavSettleSpec.Spring ?: NavSettleSpec.Spring()
        settleTo(target, spring, onFrame = onFrame)
    }
}
