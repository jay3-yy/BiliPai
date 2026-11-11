// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.nav.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.runtime.NavSettleSink
import top.yukonga.miuix.kmp.nav.runtime.anchoredProgress
import top.yukonga.miuix.kmp.nav.runtime.commitVelocityFloor
import top.yukonga.miuix.kmp.nav.runtime.navBackCommitDecision
import top.yukonga.miuix.kmp.nav.runtime.settleCancel
import top.yukonga.miuix.kmp.nav.runtime.settleTo
import top.yukonga.miuix.kmp.nav.runtime.snapToFinger
import top.yukonga.miuix.kmp.nav.runtime.trackSettle
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import kotlin.coroutines.coroutineContext

/**
 * Interactive swipe-to-dismiss that drives `animatedTop` directly, finger-following, along the axis
 * and direction the current transition declares ([NavSwipeDirection]).
 *
 * Attached by NavDisplay to the display container (so an in-flight transition can be grabbed from
 * anywhere on screen, not just the moving top entry's translated bounds). A single unified pointer
 * handler replaces the orientation-locked
 * `detectHorizontalDragGestures` / `detectVerticalDragGestures` detectors, which only owned one axis
 * and yielded the pointer to any other consumer — the source of two defects: an in-progress dismiss
 * cancelled the instant the finger drifted onto the cross axis (a nested scroll claimed the pointer),
 * and in-page taps/scrolls fired while the gesture was driving the transition. The unified handler
 * instead works in two phases:
 *
 * 1. **Engagement** — movement is watched on [PointerEventPass.Final], after descendants had a chance
 *    to consume it on the Main pass. The first consumed non-zero position change starts a one-move
 *    confirmation window independent of navigation touch slop: another consumed position change
 *    confirms that child content owns the whole sequence, while an unconsumed change means the first
 *    consumption was only a clickable cancelling its press and navigation may claim. Either owner is
 *    then permanent until all pointers lift. A clearly cross-axis or opposite-direction drag is left
 *    to content.
 * 2. **Follow** — once claimed, **every** pointer change is consumed on **both** axes, so neither a
 *    nested scroll nor any in-page handler can act on the finger that is driving the dismiss, and a
 *    cross-axis wiggle can no longer steal or cancel the gesture. The dismiss ends **only** on finger
 *    lift; there is no cross-axis cancel path.
 *
 * [NavSwipeDirection.None] disables the gesture entirely (pop via back button / system back).
 *
 * While following, `animatedTop` is driven via [snapToFinger] to
 * `topIndex - anchoredProgress(anchor, fingerProgress)`, where `anchor` is the progress already
 * travelled toward pop at the claim instant (sampled atomically with halting any in-flight settle —
 * grab-anchor invariant 6, so interrupting a running push/pop transition is jump-free), and
 * `fingerProgress` is the recognized finger travel **in the dismiss direction beyond touch slop**
 * over the layout extent on that axis. It includes travel observed before ownership was confirmed,
 * so the page catches up without restarting at the claim event; after that it remains linear and
 * 1:1 with the finger (§7.1, no interpolation on this axis).
 *
 * On release the decision is delegated to [navBackCommitDecision] (**velocity-first,
 * position-fallback**, §7.2), using the shared thresholds [NavDriverSpec.COMMIT_VELOCITY_THRESHOLD] /
 * [NavDriverSpec.COMMIT_POSITION_THRESHOLD]. Release velocity is sampled with a [VelocityTracker]
 * (instantaneous, not a per-event delta proxy) and expressed in **progress-units per second** (axis
 * pixels/sec ÷ layout extent, signed toward the dismiss direction). That value feeds
 * [navBackCommitDecision] directly. For the convergence spring it is negated to the depth axis
 * (`animatedTop = topIndex - progress`, so depth velocity = -progress velocity) and handed to the
 * single shared spring via [settleTo]'s `initialVelocity`, making the snap -> spring handoff
 * velocity-continuous (§0.3 single spring).
 *
 * On commit [onCommit] is invoked **synchronously**, decoupled from the settle (see the release body
 * for why gating the pop behind the settle is unsafe in a per-entry coroutine scope).
 *
 * @param enabled Whether the swipe is active. Disable on the root entry (nothing to pop).
 * @param direction The dismiss axis + direction; [NavSwipeDirection.None] disables the gesture.
 * @param animatedTop The single shared depth driver.
 * @param topIndex Current top entry index being peeled away.
 * @param motion Settle physics for the release settles (commit/cancel curves); NavDisplay resolves
 *   it from the governing transition.
 * @param onCommit Invoked synchronously the moment a release is classified as a commit (pop the back
 *   stack here); the shared spring then settles `animatedTop` to the new top.
 * @param onCancel Invoked once after the cancel spring settles back to [topIndex].
 * @param onGesture Receives the live [NavGesture] context per follow event (progress, edge, touch
 *   position) for gesture-aware transitions, and `null` when the gesture has fully resolved. The
 *   last value is intentionally kept through the release settle (a transition pivoting around the
 *   touch point must not snap back to center at lift); the cancel branch clears it after its
 *   settle, the commit branch leaves clearing to the host (the leaving entry's unload).
 */
// composed { } is required here: the swipe needs composition-scoped state (coroutine scope,
// up-to-date callbacks). A Modifier.Node rewrite is a deferred optimization, so suppress the
// no-composed lint locally rather than disabling the performance rule project-wide.
fun Modifier.navSwipeDismiss(
    enabled: Boolean,
    direction: NavSwipeDirection,
    animatedTop: Animatable<Float, AnimationVector1D>,
    topIndex: Int,
    motion: NavMotion = NavMotion.Default,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onGesture: (NavGesture?) -> Unit = {},
): Modifier = navSwipeDismissImpl(
    enabled = enabled,
    direction = direction,
    animatedTop = animatedTop,
    topIndex = topIndex,
    motion = motion,
    settleSink = null,
    onCommit = onCommit,
    onCancel = onCancel,
    onGesture = onGesture,
)

/**
 * Implementation body shared by the public [navSwipeDismiss] and NavDisplay's internal wiring:
 * [settleSink], when present, publishes the release settle as a
 * [top.yukonga.miuix.kmp.nav.transition.NavSettle] context (the bare public modifier drives and
 * settles but publishes no context). [externalGestureOwnership] is an even/odd generation token:
 * odd means another source (such as system predictive back) currently owns the gesture, and every
 * ownership change advances the value. A pointer sequence latches any generation change so queued
 * work cannot resume after a fast external start+finish cycle.
 */
@Suppress("ktlint:compose:modifier-composed-check")
internal fun Modifier.navSwipeDismissImpl(
    enabled: Boolean,
    direction: NavSwipeDirection,
    animatedTop: Animatable<Float, AnimationVector1D>,
    topIndex: Int,
    motion: NavMotion,
    settleSink: NavSettleSink?,
    externalGestureOwnership: () -> Long = { 0L },
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onGesture: (NavGesture?) -> Unit,
): Modifier = composed {
    if (!enabled || direction == NavSwipeDirection.None) return@composed this

    val scope = rememberCoroutineScope()
    val currentMotion = rememberUpdatedState(motion)
    val currentExternalGestureOwnership = rememberUpdatedState(externalGestureOwnership)
    val currentOnCommit = rememberUpdatedState(onCommit)
    val currentOnCancel = rememberUpdatedState(onCancel)
    val currentOnGesture = rememberUpdatedState(onGesture)

    // Edge semantics for gesture-aware transitions: a horizontal back swipe reads as starting from
    // the screen edge the finger travels away from; vertical dismissal has no meaningful edge.
    val swipeEdge = when (direction) {
        NavSwipeDirection.LeftToRight -> NavSwipeEdge.Left
        NavSwipeDirection.RightToLeft -> NavSwipeEdge.Right
        else -> NavSwipeEdge.None
    }

    val isHorizontal = direction == NavSwipeDirection.LeftToRight || direction == NavSwipeDirection.RightToLeft
    // Sign mapping a raw axis delta onto "progress toward dismiss": +1 when the dismiss direction is the
    // axis-positive one (right / down), -1 when it is axis-negative (left / up).
    val dismissSign = when (direction) {
        NavSwipeDirection.LeftToRight, NavSwipeDirection.TopToBottom -> 1f
        NavSwipeDirection.RightToLeft, NavSwipeDirection.BottomToTop -> -1f
        NavSwipeDirection.None -> 0f // unreachable (guarded above)
    }

    this.pointerInput(enabled, direction, topIndex) {
        val pointerInputContext = coroutineContext
        awaitEachGesture {
            // Layout extent along the gesture axis, used to normalise finger travel into 0f..1f progress.
            // Read per-gesture from the live pointer scope so a resize between gestures is picked up.
            val extent = (if (isHorizontal) size.width else size.height).toFloat().coerceAtLeast(1f)
            val slop = viewConfiguration.touchSlop

            val down = awaitFirstDown(requireUnconsumed = false)
            val initialExternalOwnership = currentExternalGestureOwnership.value()
            var sequenceJob: Job? = null
            var blockedByExternalGesture = false
            fun externalGestureOwnsSequence(): Boolean {
                if (!blockedByExternalGesture && (
                        initialExternalOwnership and 1L != 0L ||
                            currentExternalGestureOwnership.value() != initialExternalOwnership
                        )
                ) {
                    blockedByExternalGesture = true
                    sequenceJob?.cancel()
                }
                return blockedByExternalGesture
            }

            // Tracks pointer position over time for an accurate instantaneous release velocity. Sampled
            // from the down so the velocity window is populated even before the gesture engages.
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            // --- Engagement phase: descendants see Main first. The pure arbiter keeps child
            // consumption evidence independent from navigation's touch-slop eligibility, so a
            // lower-slop child can establish permanent ownership before navigation may engage. ---
            val arbitrator = NavSwipeArbitrator(touchSlop = slop)
            var initialDismissTravelPx = 0f
            var claimTouchY = down.position.y
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (externalGestureOwnsSequence()) return@awaitEachGesture
                if (event.changes.count { it.pressed } > 1) {
                    arbitrator.cancel()
                    return@awaitEachGesture
                }
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    arbitrator.onEnd()
                    return@awaitEachGesture // lifted before engaging: a tap / short press
                }
                val delta = change.positionChangeIgnoreConsumed()
                if (delta.x == 0f && delta.y == 0f) continue
                val axisDelta = if (isHorizontal) delta.x else delta.y
                val crossDelta = if (isHorizontal) delta.y else delta.x
                when (
                    arbitrator.onPositionChange(
                        towardDeltaPx = dismissSign * axisDelta,
                        crossDeltaPx = crossDelta,
                        consumed = change.isConsumed,
                    )
                ) {
                    NavSwipeArbitrationOutcome.Continue -> Unit

                    NavSwipeArbitrationOutcome.YieldToChild,
                    NavSwipeArbitrationOutcome.Cancelled,
                    -> return@awaitEachGesture

                    NavSwipeArbitrationOutcome.ClaimNavigation -> {
                        // Keep touch slop as a dead zone, then catch up to the recognized drag.
                        initialDismissTravelPx = arbitrator.initialDismissTravelPx
                        claimTouchY = change.position.y
                        change.consume()
                        break
                    }
                }
            }

            // Anchor and finger-snap work belongs to this pointer sequence, not to the whole
            // composition. Parenting it to pointerInput also cancels queued mutations when
            // this modifier restarts; external ownership cancels it explicitly below.
            val activeSequenceJob = Job(pointerInputContext[Job])
            sequenceJob = activeSequenceJob
            val sequenceScope = CoroutineScope(pointerInputContext + activeSequenceJob)

            // --- Grab anchor (invariant 6): sampled atomically with halting the in-flight settle,
            // inside the FIRST driver coroutine rather than this pointer handler (this scope is
            // @RestrictsSuspension — a foreign suspend call like stop() cannot run here). Between
            // the event that claims the gesture and the moment a launched coroutine runs, the
            // shared spring may still advance a frame; stop() first (cancelling the settle through
            // the Animatable mutex) and THEN read the value, so the first snap target is exactly
            // where the spring halted — zero jump. FIFO ordering on the single-threaded composition
            // dispatcher guarantees this coroutine STARTS before any follow-phase snap, but NOT
            // that it completes first: when a settle is mid-flight, stop() suspends across extra
            // dispatcher slices while the cancelled animation unwinds, and on desktop/web a pointer
            // event can interleave there and launch a snap that executes while anchor is still NaN.
            // Such pre-anchor snaps are dropped at the launch site below (and snapToFinger itself
            // rejects a NaN anchor): a NaN written into the driving float would be unrecoverable. ---
            var anchor = Float.NaN
            sequenceScope.launch {
                if (externalGestureOwnsSequence()) return@launch
                animatedTop.stop()
                if (externalGestureOwnsSequence()) return@launch
                anchor = topIndex - animatedTop.value
                val initialFingerProgress = initialDismissTravelPx / extent
                if (initialFingerProgress > 0f) {
                    animatedTop.snapToFinger(topIndex = topIndex, progress = initialFingerProgress, anchor = anchor)
                    if (externalGestureOwnsSequence()) return@launch
                    currentOnGesture.value(
                        NavGesture(
                            progress = anchoredProgress(anchor = anchor, fingerProgress = initialFingerProgress).coerceAtLeast(0f),
                            swipeEdge = swipeEdge,
                            touchY = claimTouchY,
                            initialTouchY = down.position.y,
                        ),
                    )
                }
            }

            // --- Follow phase: we own the pointer. Consume every change on both axes (so neither a nested
            // scroll nor any in-page handler sees the finger, and no cross-axis movement can cancel). The
            // dismiss catches up to the recognized pre-claim travel, then follows 1:1 and ends only on lift. ---
            var dismissTravelPx = initialDismissTravelPx
            var forceCancel = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (externalGestureOwnsSequence()) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == down.id }
                if (forceCancel || event.changes.any { it.id != down.id && it.pressed } || change == null) {
                    forceCancel = true
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                    continue
                }
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    // 系统取消触摸会表现为已消费的抬起，不应提交返回。
                    forceCancel = change.isConsumed
                    event.changes.forEach { it.consume() }
                    break
                }
                val delta = change.positionChange()
                val axisDelta = if (isHorizontal) delta.x else delta.y
                dismissTravelPx += dismissSign * axisDelta
                change.consume()
                // Unclamped here; anchoredProgress clamps the total to [min(anchor, 0), MAX_FINGER_PROGRESS].
                val fingerProgress = dismissTravelPx / extent
                val touchY = change.position.y
                sequenceScope.launch {
                    // Pre-anchor event (stop() above still unwinding a mid-flight settle): drop it.
                    // At most the first 1-2 events on desktop/web; the first executed snap still
                    // lands exactly on the sampled anchor, so the zero-jump grab is preserved.
                    if (externalGestureOwnsSequence() || anchor.isNaN()) return@launch
                    animatedTop.snapToFinger(topIndex = topIndex, progress = fingerProgress, anchor = anchor)
                    if (externalGestureOwnsSequence()) return@launch
                    // Published inside the driver coroutine so the anchor is always the sampled one.
                    currentOnGesture.value(
                        NavGesture(
                            progress = anchoredProgress(anchor = anchor, fingerProgress = fingerProgress).coerceAtLeast(0f),
                            swipeEdge = swipeEdge,
                            touchY = touchY,
                            initialTouchY = down.position.y,
                        ),
                    )
                }
            }

            if (externalGestureOwnsSequence()) return@awaitEachGesture

            // --- Release: velocity-first / position-fallback, velocity-continuous spring handoff. ---
            // If the finger lifted before the anchor coroutine ran (claim -> immediate lift), fall
            // back to sampling here: the decision then sees the freshest value, and the settle below
            // re-owns the float through the mutex either way.
            val releaseAnchor = if (anchor.isNaN()) topIndex - animatedTop.value else anchor
            // Total progress on the pop axis; negative totals (leaving entry frozen above the grab
            // point) count as 0 for the commit decision — cancel-biased by construction.
            val progress = anchoredProgress(anchor = releaseAnchor, fingerProgress = dismissTravelPx / extent).coerceAtLeast(0f)
            // Instantaneous release velocity along the axis, in pixels/sec, projected onto the dismiss
            // direction and normalised to progress-units/sec (positive points toward pop).
            val velocity = velocityTracker.calculateVelocity()
            val axisVelocity = if (isHorizontal) velocity.x else velocity.y
            val progressVelocity = if (forceCancel) 0f else dismissSign * axisVelocity / extent
            // Depth axis is the inverse of the progress axis, so negate for the spring handoff.
            val depthVelocity = -progressVelocity
            if (!forceCancel && navBackCommitDecision(progress = progress, velocity = progressVelocity)) {
                // Commit the back-stack change SYNCHRONOUSLY, never gated behind the settle. `scope` is
                // this entry's composition scope; the commit settle drives `animatedTop` to `topIndex - 1`,
                // at which point this (top) entry reaches relativeDepth -1, leaves the visible window, and
                // its host leaves composition -- cancelling `scope`. If the pop were sequenced AFTER
                // `settleTo` inside that scope, the cancellation could drop the pop entirely: the back stack
                // would stay unchanged while the renderer's shared spring pulls `animatedTop` back to the
                // un-popped top, so the page "springs back" and feels stuck. Popping first lets the renderer
                // own the final convergence; the settle below only seeds the spring with the release velocity.
                if (settleSink != null) {
                    // Unified ownership: hand the release velocity to the renderer the same way
                    // the predictive-back path does and let its retarget own (and publish) the
                    // commit settle. Launching our own settle here would lose the race against
                    // the renderer's UNDISPATCHED retarget effect, and the post-pop disable
                    // (topIndex drops to 0) cancels this scope before a queued launch even runs.
                    settleSink.pendingSettleVelocity = depthVelocity
                    currentOnCommit.value()
                } else {
                    currentOnCommit.value()
                    scope.launch {
                        if (externalGestureOwnsSequence()) return@launch
                        // Seed the governing commit curve with the release velocity. The
                        // no-overshoot floor applies only while the commit spec keeps overshoot
                        // clamped (the default): even a critically damped spring crosses its
                        // target once when the seeded fling speed exceeds ω·distance. The floor
                        // is computed from the value the settle actually starts at — all pending
                        // finger snaps are FIFO-ordered before this launch.
                        val motionNow = currentMotion.value
                        val floor = motionNow.commit.commitVelocityFloor(animatedTop.value - (topIndex - 1f))
                        animatedTop.settleTo(
                            target = (topIndex - 1).toFloat(),
                            spec = motionNow.commit,
                            initialVelocity = depthVelocity.coerceAtLeast(floor),
                        )
                    }
                }
            } else {
                // Cancel keeps the entry in place (never marked removing), so its scope survives the settle
                // and onCancel can safely run after it.
                //
                // Clamp the seeded velocity to <= 0. The depth axis runs from `topIndex - 1` (fully
                // dismissed) up to `topIndex` (rest), and at release `animatedTop` is always <= topIndex.
                // A release that flicks AWAY from the dismiss direction (e.g. a back-swipe yanked back the
                // other way) produces a POSITIVE depth velocity, which would carry the shared spring PAST
                // the rest position to `animatedTop > topIndex`. That momentarily drops THIS top entry into
                // the covered regime (relativeDepth > 0), so its transition applies the covered parallax and
                // the page flashes a few frames in the wrong direction. A cancel has nothing above the top
                // to reveal, so that upward momentum is purely spurious: drop it and let the spring ease back
                // to rest from the current position. Legitimate "return" motion (negative depth velocity) is
                // kept, preserving the snap -> spring velocity continuity; it points away from the cancel
                // target, so it can never cross it (no bounce, no overshoot floor needed on this branch).
                scope.launch {
                    if (externalGestureOwnsSequence()) return@launch
                    val settleBody: suspend (onFrame: (() -> Unit)?) -> Unit = { onFrame ->
                        animatedTop.settleCancel(
                            target = topIndex.toFloat(),
                            spec = currentMotion.value.cancel,
                            initialVelocity = depthVelocity.coerceAtMost(0f),
                            onFrame = onFrame,
                        )
                    }
                    if (settleSink != null) {
                        settleSink.trackSettle(phase = NavSettlePhase.Cancel, releaseVelocity = progressVelocity) { onFrame ->
                            settleBody(onFrame)
                        }
                    } else {
                        settleBody(null)
                    }
                    if (externalGestureOwnsSequence()) return@launch
                    // The gesture context stays frozen through the settle (no pivot snap at lift)
                    // and is released only now, with the entry back at rest.
                    currentOnGesture.value(null)
                    currentOnCancel.value()
                }
            }

            // Normal release keeps FIFO behavior: already queued final snaps may finish
            // before the composition-scope settle, but no new sequence work can be added.
            activeSequenceJob.complete()
        }
    }
}
