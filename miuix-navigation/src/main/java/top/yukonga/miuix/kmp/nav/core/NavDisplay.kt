// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.nav.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandlerWithSessions
import top.yukonga.miuix.kmp.nav.gesture.drivePredictiveBack
import top.yukonga.miuix.kmp.nav.gesture.navSwipeDismissImpl
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.runtime.NavPresentation
import top.yukonga.miuix.kmp.nav.runtime.commitVelocityFloor
import top.yukonga.miuix.kmp.nav.runtime.isVisibleAt
import top.yukonga.miuix.kmp.nav.runtime.navBackCompletionDecision
import top.yukonga.miuix.kmp.nav.runtime.navReconcile
import top.yukonga.miuix.kmp.nav.runtime.relativeDepth
import top.yukonga.miuix.kmp.nav.runtime.rememberNavPresentation
import top.yukonga.miuix.kmp.nav.runtime.settleCancel
import top.yukonga.miuix.kmp.nav.runtime.settleProgrammatic
import top.yukonga.miuix.kmp.nav.runtime.settleTo
import top.yukonga.miuix.kmp.nav.runtime.trackSettle
import top.yukonga.miuix.kmp.nav.state.NavEntryViewModelStores
import top.yukonga.miuix.kmp.nav.state.NavSaveableStateHolder
import top.yukonga.miuix.kmp.nav.state.ProvideNavEntryLifecycle
import top.yukonga.miuix.kmp.nav.state.ProvideNavEntryViewModelStore
import top.yukonga.miuix.kmp.nav.state.navMaxLifecycleFor
import top.yukonga.miuix.kmp.nav.state.navSaveableKey
import top.yukonga.miuix.kmp.nav.state.rememberNavEntryLifecycleOwner
import top.yukonga.miuix.kmp.nav.state.rememberNavEntryViewModelStoreOwner
import top.yukonga.miuix.kmp.nav.state.rememberNavEntryViewModelStores
import top.yukonga.miuix.kmp.nav.state.rememberNavSaveableStateHolder
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.squircle.absoluteSquircleClip
import kotlin.reflect.KClass

/**
 * Metadata key under which a per-route [NavTransition] override is stored on a built [NavEntry].
 *
 * The renderer reads this key from [NavEntry.metadata]; a `null`/absent value means the entry
 * inherits the global transition passed to [NavDisplay].
 */
@PublishedApi
internal const val NAV_TRANSITION_METADATA_KEY: String = "top.yukonga.miuix.kmp.nav.transition"

/**
 * Metadata key under which a per-route [NavSwipeDirection] override is stored on a built [NavEntry].
 *
 * A `null`/absent value means the entry inherits the dismiss direction of its governing
 * [NavTransition.dismissDirection]; an explicit value (including [NavSwipeDirection.None] to disable
 * the gesture) wins.
 */
@PublishedApi
internal const val NAV_SWIPE_DISMISS_METADATA_KEY: String = "top.yukonga.miuix.kmp.nav.swipeDismiss"

/**
 * DSL receiver of the [NavDisplay] content lambda.
 *
 * Each `entry<T> { ... }` call registers, keyed by the route's runtime [KClass], how to build a
 * [NavEntry] for keys of that type. [build] materializes the registrations into a lookup function.
 */
class NavEntryBuilder {
    private val factories: MutableMap<KClass<*>, (NavKey) -> NavEntry<*>> = mutableMapOf()

    /**
     * Registers how to render keys of type [T].
     *
     * @param contentKey derives a value-stable identity for each entry from its route instance;
     *   when `null` the key instance itself is used (data classes / data objects give stable
     *   equality out of the box). Content keys must be UNIQUE across the whole back stack —
     *   pushing two entries that resolve to equal content keys (e.g. the same route value twice)
     *   is rejected with an [IllegalArgumentException] at reconcile time. The contentKey is also
     *   the saveable-state identity, so it must be stable across recompositions and process death.
     *   Saveable state is namespaced by the contentKey's `toString()`, which adds two requirements:
     *   distinct keys must not print one string (data class `toString()` omits the package, so
     *   same-named route classes in different packages collide — also rejected at reconcile time),
     *   and the string must be value-derived. A key keeping the default identity `toString()`
     *   (`pkg.Cls@1a2b3c`) passes every runtime check but resolves to a new string after process
     *   death, silently resetting the entry's `rememberSaveable` state — use data classes / data
     *   objects, or return a value-derived key here.
     * @param transition per-route transition override; `null` inherits [NavDisplay]'s global
     *   transition (see design §6.4). Stored in the entry's metadata.
     * @param swipeDismiss per-route interactive swipe-to-dismiss direction; `null` inherits the
     *   governing transition's [NavTransition.dismissDirection]. Pass [NavSwipeDirection.None] to
     *   disable the gesture on this route, or another direction to override it.
     * @param metadata extra per-entry metadata, merged with the transition / swipe-dismiss overrides.
     * @param content the composable rendering a key of type [T].
     */
    inline fun <reified T : NavKey> entry(
        noinline contentKey: ((T) -> Any)? = null,
        transition: NavTransition? = null,
        swipeDismiss: NavSwipeDirection? = null,
        metadata: Map<String, Any> = emptyMap(),
        noinline content: @Composable (T) -> Unit,
    ) {
        register(T::class) { key ->
            @Suppress("UNCHECKED_CAST")
            val typed = key as T
            var mergedMetadata = metadata
            if (transition != null) mergedMetadata = mergedMetadata + (NAV_TRANSITION_METADATA_KEY to transition)
            if (swipeDismiss != null) mergedMetadata = mergedMetadata + (NAV_SWIPE_DISMISS_METADATA_KEY to swipeDismiss)
            NavEntry(
                key = typed,
                contentKey = contentKey?.invoke(typed) ?: typed,
                metadata = mergedMetadata,
                content = content,
            )
        }
    }

    /**
     * Low-level registration hook used by the [entry] inline function. Public only so the inline
     * reified [entry] can call it across the inline boundary; not intended for direct use.
     */
    @PublishedApi
    internal fun register(clazz: KClass<*>, factory: (NavKey) -> NavEntry<*>) {
        factories[clazz] = factory
    }

    /**
     * Materializes the accumulated registrations into an entry lookup. Matching is by the key's
     * exact runtime class: register an [entry] for every concrete route type (supertype or
     * interface registrations do NOT cover subtypes — common-code [kotlin.reflect.KClass] offers
     * no portable superclass walk). Throws if a key has no registered [entry] of its exact type.
     */
    @PublishedApi
    internal fun build(): (NavKey) -> NavEntry<*> = { key ->
        val factory = factories[key::class]
            ?: error("No entry { } registered for ${key::class.simpleName ?: key::class}")
        factory(key)
    }
}

/**
 * Materializes a [NavEntryBuilder] DSL block into an entry lookup function.
 *
 * The [NavDisplay] DSL overloads call this internally; the low-level [NavDisplay] overload accepts a
 * prebuilt lookup of this exact shape.
 */
internal fun entryProvider(builder: NavEntryBuilder.() -> Unit): (NavKey) -> NavEntry<*> = NavEntryBuilder().apply(builder).build()

/**
 * Drives [NavPresentation.animatedTop] towards [target], unless a gesture currently owns the
 * float (in which case the gesture layer snaps it via `snapToFinger`). Implemented as a
 * [LaunchedEffect] keyed on [target] that defers to the driver's settle dispatch
 * (`settleProgrammatic`: the fixed-duration programmatic curve from rest over a full step, the
 * velocity-continuous shared spring otherwise) — the renderer never builds its own
 * [androidx.compose.animation.core.AnimationSpec].
 */
@Composable
private fun NavPresentation.animateTopTo(target: Float, motion: NavMotion, prepareEnterFrame: Boolean) {
    val currentMotion = rememberUpdatedState(motion)
    LaunchedEffect(target) {
        // Consume the release velocity exactly once per retarget, whether or not it gets used.
        val released = pendingSettleVelocity
        pendingSettleVelocity = 0f
        if (animatedTop.value == target) return@LaunchedEffect
        if (prepareEnterFrame && target > animatedTop.value) {
            // The incoming source frame participates in this frame's layout/draw. Start the
            // clock on the following frame so its initial composition cannot consume the tween.
            withFrameNanos { }
            withFrameNanos { }
        }
        // A gesture-release settle toward this same target may already own the float (a swipe
        // commit pops synchronously and queues its velocity-seeded settle ahead of this effect);
        // defer to it instead of stomping the seeded spring with a fresh-from-rest curve.
        if (animatedTop.isRunning && animatedTop.targetValue == target) return@LaunchedEffect
        val motionNow = currentMotion.value
        if (gesture != null && target < animatedTop.value) {
            // A gesture-driven back resolved into this retarget (the commit context stays frozen
            // through the settle). Phase comes from the context, not the velocity: platforms
            // without usable frame timing deliver released == 0 yet this is still a commit, and
            // the governing commit curve (possibly a fixed-duration tween) must drive it. The
            // no-overshoot floor applies only while the commit spec keeps overshoot clamped.
            trackSettle(phase = NavSettlePhase.Commit, releaseVelocity = -released) { onFrame ->
                val floor = motionNow.commit.commitVelocityFloor(animatedTop.value - target)
                animatedTop.settleTo(
                    target = target,
                    spec = motionNow.commit,
                    initialVelocity = released.coerceAtLeast(floor),
                    onFrame = onFrame,
                )
            }
        } else {
            trackSettle(phase = NavSettlePhase.Programmatic, releaseVelocity = 0f) { onFrame ->
                animatedTop.settleProgrammatic(target, motionNow, onFrame = onFrame)
            }
        }
    }
}

/**
 * Provides [owner] as the entry-scoped back dispatcher when present; renders [content] directly
 * when the host has no back source. The branch is static per platform in practice.
 */
@Composable
private fun ProvideNavEntryBackScope(
    owner: NavigationEventDispatcherOwner?,
    content: @Composable () -> Unit,
) {
    if (owner != null) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner, content = content)
    } else {
        content()
    }
}

/**
 * Remembers the entry-scoped child [NavigationEventDispatcherOwner]. Deliberately NOT the
 * library's `rememberNavigationEventDispatcherOwner`: `dispose()` cascades to descendants and is
 * not idempotent, so a nested display's dispatchers would be torn down twice (ancestor cascade +
 * their own movable-content-deferred dispose effect) and throw. Both writes are guarded here; the
 * initial enabled state applies at construction (no first-frame window).
 */
@Composable
private fun rememberNavEntryDispatcherOwner(
    enabled: Boolean,
    parent: NavigationEventDispatcherOwner,
): NavigationEventDispatcherOwner {
    val dispatcher = remember(parent) {
        NavigationEventDispatcher(parent = parent.navigationEventDispatcher).also { it.isEnabled = enabled }
    }
    SideEffect {
        try {
            dispatcher.isEnabled = enabled
        } catch (_: IllegalStateException) {
            // Ancestor cascade-disposed this dispatcher mid-teardown; the write is moot.
        }
    }
    DisposableEffect(dispatcher) {
        onDispose {
            try {
                dispatcher.dispose()
            } catch (_: IllegalStateException) {
                // Already disposed by an ancestor's cascade; idempotent by construction here.
            }
        }
    }
    return remember(dispatcher) {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
        }
    }
}

/** Reads the per-route [NavTransition] override from an entry's metadata, or null to inherit. */
private fun NavEntry<*>.transitionOrNull(): NavTransition? = metadata[NAV_TRANSITION_METADATA_KEY] as? NavTransition

/** Reads the per-route [NavSwipeDirection] override from an entry's metadata, or null to inherit. */
private fun NavEntry<*>.swipeDismissOrNull(): NavSwipeDirection? = metadata[NAV_SWIPE_DISMISS_METADATA_KEY] as? NavSwipeDirection

/** Monotonic even/odd ownership token with generation-scoped release semantics. */
internal class PredictiveBackOwnership {
    var generation by mutableLongStateOf(0L)
        private set

    fun acquire(): Long {
        generation = if (generation and 1L == 0L) generation + 1L else generation + 2L
        return generation
    }

    fun release(lease: Long): Boolean {
        if (generation != lease) return false
        generation = lease + 1L
        return true
    }
}

private class PredictiveBackSession(
    val ownershipLease: Long,
    val completionPolicy: NavBackCompletionPolicy?,
    var peakProgress: Float = 0f,
    var progress: Float = 0f,
    var gesture: NavGesture? = null,
    var progressVelocity: Float = 0f,
)

/**
 * Private rendering core shared by all [NavDisplay] overloads.
 *
 * Responsibilities:
 * - build one [NavEntry] per current back-stack key via [entryProvider];
 * - reconcile against the previous render set ([NavPresentation.reconcile]) to obtain the presentation
 *   set (current ∪ exiting) and the [NavChange];
 * - keep the single driving float [NavPresentation.animatedTop] converging to `lastIndex` via the
 *   shared spring ([animateTopTo] → `settleTo`);
 * - precompute a `contentKey -> index` map at reconcile time so depth math is an O(1) lookup at
 *   render time (no O(n^2) rebuild of NavEntry instances per frame);
 * - render only the visible window ([derivedStateOf] over `presented` + the governing opaqueDepth),
 *   applying the governing transition (boundary ownership §4.3), the orthogonal [effects], and
 *   per-entry state scopes; fully-exited entries are unloaded.
 *
 * Visual reads of [NavPresentation.animatedTop] happen inside deferred `graphicsLayer { }` blocks so
 * the per-frame float change does not recompose this function. The saveable-state holder is created
 * **once here** and passed down to each [NavEntryHost] (stable identity, see §0.4).
 */
@Composable
private fun NavDisplayLayout(
    backStack: NavBackStack,
    entryProvider: (NavKey) -> NavEntry<*>,
    onBack: () -> Unit,
    transition: NavTransition,
    effects: NavDisplayEffects,
    backCompletionPolicy: NavBackCompletionPolicy?,
    onBackStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = rememberNavPresentation(backStack.lastIndex)
    val stateHolder = rememberNavSaveableStateHolder()
    val viewModelStores = rememberNavEntryViewModelStores()

    val topIndex = backStack.lastIndex
    val backScope = rememberCoroutineScope()
    val currentOnBack = rememberUpdatedState(onBack)
    val currentOnBackStarted = rememberUpdatedState(onBackStarted)
    val currentBackCompletionPolicy = rememberUpdatedState(backCompletionPolicy)
    val focusManager = LocalFocusManager.current

    // Build entries for the current back stack and reconcile. The build runs only when the back-stack
    // key list changes; entry instances for surviving keys are reused by the presentation. The
    // reconcile pass classifies the change against the PREVIOUS back-stack content keys (kept in a
    // remembered holder, not the presented set — the latter still holds exiting keys) and precomputes a
    // stable contentKey -> back-stack-index map so the render loop never rebuilds NavEntry instances to
    // recover an index (no O(n^2)). Declared ahead of the gesture wiring: the settle physics
    // resolution below feeds the gesture callbacks.
    val currentKeyList = backStack.toList()
    // Covered entries stay composed, so clear their focus when the top entry changes.
    val previousTopKey = remember { arrayOfNulls<NavKey>(1) }
    val currentTopKey = currentKeyList.lastOrNull()
    SideEffect {
        val previous = previousTopKey[0]
        if (previous != null && previous != currentTopKey) {
            focusManager.clearFocus()
        }
        previousTopKey[0] = currentTopKey
    }
    // Plain (non-snapshot) holder for the previous classification input; bookkeeping only, never read
    // by UI, so it must not be snapshot state (no cross-phase back-write).
    val previousContentKeys = remember { arrayOfNulls<List<Any>>(1) }
    // Persistent contentKey -> back-stack index. Current entries get their fresh index on each
    // reconcile; a leaving (popped/replaced) entry KEEPS its last index here so it can still render
    // its exit animation, and is pruned only when it has finished leaving (see the unload effect
    // below). Snapshot map, written only on actual change: a pure back-stack reorder changes
    // neither presentation.presented nor the driving float, so these index writes are the only
    // signal that re-evaluates the visible window and the entry hosts.
    val indexByContentKey = remember { mutableStateMapOf<Any, Int>() }
    remember(currentKeyList, entryProvider) {
        val currentEntries = currentKeyList.map { entryProvider(it) }
        val newContentKeys = currentEntries.map { it.contentKey }
        // Content keys are the entry identity everywhere downstream (presentation reconcile,
        // index map, saveable state, ViewModel store). A duplicate would fold two stack positions
        // into one instance and wedge the presentation, so reject it with an actionable message.
        // Saveable state is the one subsystem keyed by contentKey.toString() rather than by the
        // key object (SaveableStateProvider needs a Bundle-storable key), so distinct-by-equals
        // keys with an equal toString() would still collapse into one rememberSaveable slot:
        // co-composed colliders crash in the saveable holder, separated ones silently corrupt
        // each other's state. Validate both identity domains in the same pass.
        val seenContentKeys = HashSet<Any>(newContentKeys.size)
        val keyBySaveableKey = HashMap<String, Any>(newContentKeys.size)
        for (contentKey in newContentKeys) {
            require(seenContentKeys.add(contentKey)) {
                "Duplicate contentKey on the back stack: $contentKey. Every entry needs a unique " +
                    "contentKey — derive a per-instance key via entry(contentKey = { route -> ... }) " +
                    "or keep the route values distinct."
            }
            val saveableKey = navSaveableKey(contentKey)
            val owner = keyBySaveableKey.put(saveableKey, contentKey)
            require(owner == null) {
                "Distinct contentKeys collapse to the same saveable-state key \"$saveableKey\": " +
                    "$owner (${owner!!::class.simpleName}) and $contentKey " +
                    "(${contentKey::class.simpleName}) are not equal, yet rememberSaveable state " +
                    "is namespaced by contentKey.toString(), so these entries would corrupt each " +
                    "other's saved state. Derive distinguishing keys via " +
                    "entry(contentKey = { route -> ... })."
            }
        }
        // Entries already off the back stack but still presented (animating out) keep their
        // saveable slot until they unload, so an incoming key must not collide with them either.
        // Keys equal to a current one are the revival path (pop + same-frame re-push) and fold
        // back into the same entry, so only unequal leaving keys are checked.
        presentation.presented.fastForEach { leaving ->
            val leavingKey = leaving.contentKey
            if (leavingKey !in seenContentKeys) {
                val saveableKey = navSaveableKey(leavingKey)
                val incoming = keyBySaveableKey[saveableKey]
                require(incoming == null) {
                    "contentKey $incoming (${incoming!!::class.simpleName}) collapses to the same " +
                        "saveable-state key \"$saveableKey\" as $leavingKey " +
                        "(${leavingKey::class.simpleName}), which left the back stack but is " +
                        "still animating out and holds a live rememberSaveable slot. Derive " +
                        "distinguishing keys via entry(contentKey = { route -> ... })."
                }
            }
        }
        presentation.reconcile(currentEntries, navReconcile(previousContentKeys[0] ?: emptyList(), newContentKeys))
        previousContentKeys[0] = newContentKeys
        currentEntries.forEachIndexed { index, e ->
            if (indexByContentKey[e.contentKey] != index) indexByContentKey[e.contentKey] = index
        }
    }

    // Settle physics of the moving top boundary: the topmost PRESENTED entry's governing
    // transition declares the curves (per-route override wins). During a pop the leaving entry
    // is still the topmost presented entry until it unloads, so its motion carries its own exit
    // settle. Derived off the snapshot list so it recomputes on reconcile/unload, never per frame.
    val topMotion by remember(presentation, transition, indexByContentKey) {
        derivedStateOf {
            var topEntry: NavEntry<*>? = null
            var topIdx = -1
            val presented = presentation.presented
            for (i in presented.indices) {
                val idx = indexByContentKey[presented[i].contentKey] ?: continue
                if (idx > topIdx) {
                    topIdx = idx
                    topEntry = presented[i]
                }
            }
            (topEntry?.transitionOrNull() ?: transition).motion
        }
    }

    // Wire the platform predictive back gesture to the single driver. Enabled only when there is
    // something to pop (size > 1). During the gesture each NavBackEvent snaps animatedTop 1:1;
    // on commit onBack() pops and the renderer's animateTopTo converges to the new top; on cancel
    // the shared spring settles back to topIndex. Modifier.navSwipeDismiss on the display
    // container drives the same animatedTop for the interactive edge swipe.
    // Even values are idle; every predictive-back start advances to a new odd ownership
    // generation, and only that session's terminal callback may advance its lease to the next
    // even value. Pointer work can still detect a complete start+finish while suspended, without
    // an older delayed terminal callback making a newer active session appear idle.
    val predictiveBackOwnership = remember { PredictiveBackOwnership() }
    val predictiveBackSessions = remember { mutableMapOf<Long, PredictiveBackSession>() }
    val cancelPredictiveBack: (Long) -> Unit = cancel@{ sessionId ->
        val session = predictiveBackSessions.remove(sessionId) ?: return@cancel
        // A delayed terminal callback from an older session must not settle or release the
        // newer predictive gesture that currently owns the shared driver.
        if (!predictiveBackOwnership.release(session.ownershipLease)) return@cancel
        val restoreGeneration = predictiveBackOwnership.generation
        val restoringGesture = presentation.gesture
        presentation.pendingSettleVelocity = 0f
        // Claim the Animatable before returning to the event dispatcher. A queued restore
        // could otherwise start AFTER a new finger has stopped the previous animation.
        backScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (predictiveBackOwnership.generation != restoreGeneration) return@launch
            presentation.trackSettle(phase = NavSettlePhase.Cancel, releaseVelocity = 0f) { onFrame ->
                presentation.animatedTop.settleCancel(
                    target = topIndex.toFloat(),
                    spec = topMotion.cancel,
                    onFrame = onFrame,
                )
            }
            if (predictiveBackOwnership.generation == restoreGeneration &&
                presentation.gesture === restoringGesture
            ) {
                presentation.gesture = null
            }
        }
    }
    val commitPredictiveBack: (Long) -> Unit = commit@{ sessionId ->
        val session = predictiveBackSessions.remove(sessionId) ?: return@commit
        if (predictiveBackOwnership.generation != session.ownershipLease) return@commit
        val velocity = session.progressVelocity
        presentation.pendingSettleVelocity = if (session.completionPolicy != null) {
            -velocity.coerceAtLeast(0f)
        } else {
            -velocity
        }
        try {
            currentOnBack.value()
        } finally {
            predictiveBackOwnership.release(session.ownershipLease)
        }
    }
    PredictiveBackHandlerWithSessions(
        enabled = backStack.size > 1,
        drainEventsOnCancel = backCompletionPolicy != null,
        onProgress = { sessionId, events ->
            currentOnBackStarted.value()
            val session = PredictiveBackSession(
                completionPolicy = currentBackCompletionPolicy.value,
                ownershipLease = predictiveBackOwnership.acquire(),
            )
            predictiveBackSessions[sessionId] = session
            presentation.pendingSettleVelocity = 0f
            // Per-gesture trackers: the initial touch anchors vertical-follow transitions, the
            // last two timestamped events estimate the release velocity (depth-units/sec).
            var initialTouchY = Float.NaN
            var lastProgress = 0f
            var lastTimeMillis = 0L
            drivePredictiveBack(
                // Mirror each event into the live gesture context for gesture-aware transitions
                // (touch-following shifts etc.); NavBackEvent and NavGesture share field semantics.
                events = events.onEach { event ->
                    if (initialTouchY.isNaN()) initialTouchY = event.touchY
                    if (event.frameTimeMillis > lastTimeMillis && lastTimeMillis != 0L) {
                        val progressVelocity = (event.progress - lastProgress) * 1000f / (event.frameTimeMillis - lastTimeMillis)
                        session.progressVelocity = progressVelocity
                        if (predictiveBackOwnership.generation == session.ownershipLease) {
                            presentation.pendingSettleVelocity = -progressVelocity
                        }
                    }
                    if (event.frameTimeMillis != 0L) {
                        lastProgress = event.progress
                        lastTimeMillis = event.frameTimeMillis
                    }
                    val gesture = NavGesture(
                        progress = event.progress,
                        swipeEdge = event.swipeEdge,
                        touchY = event.touchY,
                        initialTouchY = initialTouchY,
                    )
                    session.gesture = gesture
                    if (predictiveBackOwnership.generation == session.ownershipLease) {
                        presentation.gesture = gesture
                    }
                },
                animatedTop = presentation.animatedTop,
                topIndex = topIndex,
                isActive = { predictiveBackOwnership.generation == session.ownershipLease },
                onProgressApplied = { progress ->
                    session.progress = progress
                    session.peakProgress = maxOf(session.peakProgress, progress)
                },
            )
        },
        onCommit = commit@{ sessionId ->
            if (sessionId == null) {
                // A bare completion (Desktop ESC / programmatic back) is discrete and must commit
                // regardless of any pointer-dismiss gesture currently stored in presentation.
                currentOnBack.value()
                return@commit
            }
            val session = predictiveBackSessions[sessionId] ?: return@commit
            val progressVelocity = session.progressVelocity
            // Default routes retain the platform completion plus upstream velocity guard.
            // An opt-in card policy decides from the applied pose and per-session peak instead.
            val shouldCommit = session.completionPolicy?.shouldCommit(
                progress = session.progress,
                peakProgress = session.peakProgress,
                velocity = progressVelocity,
            ) ?: navBackCompletionDecision(velocity = progressVelocity)
            if (shouldCommit) {
                commitPredictiveBack(sessionId)
            } else {
                cancelPredictiveBack(sessionId)
            }
        },
        onCancel = { sessionId ->
            val session = predictiveBackSessions[sessionId]
            if (session?.completionPolicy?.shouldCommitOnCancel(
                    progress = session.progress,
                    peakProgress = session.peakProgress,
                ) == true
            ) {
                commitPredictiveBack(sessionId)
            } else {
                cancelPredictiveBack(sessionId)
            }
        },
    )

    // Unload entries that have finished leaving — either they slid fully off the front edge
    // (relativeDepth <= -1, the pop case) or a current entry now occupies their index (the replace
    // case, where the driver never moves past them). Such entries are no longer (or never were) in the
    // visible window, so no NavEntryHost runs to self-unload; prune them here at the layout level: drop
    // the saved state, clear the entry's ViewModelStore in the display registry, and remove from the
    // presentation set + index map. The registry — not the host composition — owns store lifetime, so
    // a depth-culled entry that is still on the back stack keeps its ViewModels.
    val finishedLeaving by remember(presentation, indexByContentKey) {
        derivedStateOf {
            val top = presentation.animatedTop.value
            val currentSize = backStack.size
            val presented = presentation.presented
            // Hand-rolled scan: this calculation re-runs on every animation frame (it must, so the
            // derived state can compare results), and the common nothing-leaving case has to stay
            // allocation-free — no filter list, no closure, no boxing.
            var result: MutableList<NavEntry<*>>? = null
            for (i in presented.indices) {
                val e = presented[i]
                if (!e.presentation.isRemoving) continue
                val index = indexByContentKey[e.contentKey]
                val springRunning = (e.transitionOrNull() ?: transition).retainsSpringOvershoot &&
                    presentation.animatedTop.isRunning
                if (index == null || (top - index <= -1f && !springRunning) || index < currentSize) {
                    (result ?: ArrayList<NavEntry<*>>(presented.size).also { result = it }).add(e)
                }
            }
            result ?: emptyList()
        }
    }
    if (finishedLeaving.isNotEmpty()) {
        SideEffect {
            finishedLeaving.fastForEach { e ->
                stateHolder.removeState(e.contentKey)
                viewModelStores.clearStore(e.contentKey)
                indexByContentKey.remove(e.contentKey)
                presentation.unload(e)
            }
            // A committed back gesture resolves here (its leaving entry just unloaded); release
            // the gesture context that was kept frozen through the settle.
            presentation.gesture = null
        }
    }

    // Drive the single shared float towards the new top index via the governing settle curves. The
    // renderer only sets the target; the interactive gesture layer may snapToFinger it instead.
    val prepareEnterFrame = presentation.presented.any { entry ->
        indexByContentKey[entry.contentKey] == backStack.lastIndex &&
            (entry.transitionOrNull() ?: transition).preparesEnterFrame
    }
    presentation.animateTopTo(backStack.lastIndex.toFloat(), topMotion, prepareEnterFrame)

    // Effective interactive dismiss direction of the CURRENT top entry: a per-route override wins,
    // else its governing transition declares the natural direction. Rebuilding one entry to read
    // its metadata is O(1) and only happens when the stack, the global transition, or the entry
    // provider changes.
    val topDismissDirection = remember(currentKeyList, transition, entryProvider) {
        val topEntry = entryProvider(currentKeyList.last())
        topEntry.swipeDismissOrNull() ?: (topEntry.transitionOrNull() ?: transition).dismissDirection
    }

    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    Box(
        // The unified swipe recognizer lives on the DISPLAY CONTAINER, not on the top entry's host:
        // the host's hit-rect follows its graphicsLayer translation, so early in a push most of the
        // screen belongs to the covered layer below and an interrupting back-swipe could never
        // engage. The container sees every pointer, while the recognizer's engagement phase waits
        // until descendants have had the Main pass so same-axis interactive content gets first
        // refusal. Once navigation claims a sequence it still consumes from the Initial pass. This
        // keeps the whole screen grabbable at any point of an in-flight transition without stealing
        // slider or scroll gestures. At rest the settled top host was full-screen anyway.
        modifier = modifier
            .onSizeChanged { layoutSize = it }
            .navSwipeDismissImpl(
                // Never on the root (nothing to pop); NavSwipeDirection.None keeps it disabled.
                enabled = topIndex > 0,
                direction = topDismissDirection,
                animatedTop = presentation.animatedTop,
                topIndex = topIndex,
                motion = topMotion,
                settleSink = presentation,
                externalGestureOwnership = { predictiveBackOwnership.generation },
                onCommit = currentOnBack.value,
                onCancel = {},
                onGesture = {
                    if (it != null && presentation.gesture == null) currentOnBackStarted.value()
                    presentation.gesture = it
                },
            ),
    ) {
        // Backdrop behind every entry (reference shell behavior): a solid layer in the page
        // background color, so the area revealed around a scaled-down entry reads as the page
        // background extending outward instead of whatever sits behind the navigation host. At
        // rest it is fully covered by the opaque top entry.
        if (effects.backdropColor.isSpecified) {
            Box(modifier = Modifier.fillMaxSize().background(effects.backdropColor))
        }

        // Visible window (spec §4.4 / §9): an entry is visible when -1 < d <= opaqueDepth. The window
        // depth is the MAX opaqueDepth across the global transition and every presented entry's
        // per-route override — the renderer keeps a layer alive while ANY transition in play would, so
        // a Modal override (opaqueDepth 2f) keeps the layer below it visible even under a shallower
        // global transition. Over-keeping a hidden layer is cheap; under-keeping would blank it out.
        // The derived calculation re-runs each animation frame (it must, so the derived state can
        // compare results) but notifies readers only when the membership actually changes — i.e.
        // when a window boundary is crossed; per-frame floats are otherwise read lazily in
        // graphicsLayer. Hand-rolled max/filter keeps the per-frame re-run free of closures,
        // iterators, and boxed floats; the only allocation left is the result list, emitted in
        // stack-index order (bottom first) so composition order matches draw order.
        val visibleEntries by remember(presentation, transition, indexByContentKey) {
            derivedStateOf {
                val top = presentation.animatedTop.value
                val presented = presentation.presented
                var windowDepth = transition.opaqueDepth
                for (i in presented.indices) {
                    val depth = (presented[i].transitionOrNull() ?: transition).opaqueDepth
                    if (depth > windowDepth) windowDepth = depth
                }
                var result: MutableList<NavEntry<*>>? = null
                for (i in presented.indices) {
                    val entry = presented[i]
                    val index = indexByContentKey[entry.contentKey] ?: continue
                    val settlingSpring = entry.presentation.isRemoving &&
                        (entry.transitionOrNull() ?: transition).retainsSpringOvershoot &&
                        presentation.animatedTop.isRunning
                    val preparingEnter = !entry.presentation.isRemoving &&
                        relativeDepth(top, index) == -1f &&
                        (entry.transitionOrNull() ?: transition).preparesEnterFrame
                    if (isVisibleAt(relativeDepth(top, index), windowDepth) || settlingSpring || preparingEnter) {
                        val list = result ?: ArrayList<NavEntry<*>>(presented.size).also { result = it }
                        // Insertion-sorted by stack index: composition order is draw order, and a
                        // pure back-stack reorder leaves `presented` in its old order. Stable, so a
                        // replace transient (equal index) keeps the newer instance on top.
                        var at = list.size
                        while (at > 0 && (indexByContentKey[list[at - 1].contentKey] ?: -1) > index) at--
                        list.add(at, entry)
                    }
                }
                result ?: emptyList()
            }
        }

        // Highest visible layer; the fullscreen dim scrim renders just beneath it.
        val topMostVisibleIndex = visibleEntries.fold(-1) { acc, e ->
            maxOf(acc, indexByContentKey[e.contentKey] ?: -1)
        }

        visibleEntries.fastForEach { entry ->
            key(entry.contentKey) {
                // Local non-null guard, NOT a non-local `return@key`: Compose Hot Reload turns the
                // compiler's non-local-return marker into a method literally named "<anonymous>",
                // which the JVM rejects at class load (ClassFormatError). The index always exists
                // here (same map the visibleEntries filter ran over), so the body always runs.
                indexByContentKey[entry.contentKey]?.let { entryIndex ->
                    // Boundary ownership (§4.3): the entry's own transition governs while it is at the
                    // top (d <= 0); the upper neighbour's transition governs the covered treatment
                    // (0 < d <= 1). NavEntryHost picks per relativeDepth inside the deferred
                    // graphicsLayer so the choice costs no recomposition.
                    val ownTransition = entry.transitionOrNull() ?: transition
                    // Resolve the upper neighbour from the PRESENTED set (which still holds a
                    // popped-but-animating-out entry), NOT the live back stack. A leaving entry keeps its
                    // last index in indexByContentKey, so index + 1 still points at it until it unloads.
                    // Using backStack here would, the instant an upper entry with a per-route transition
                    // (e.g. a bottom-up Modal) is popped, revert this covered layer to the global transition
                    // mid-dismiss — for a Modal over the horizontal-slide default that wrongly parallaxes the
                    // layer below sideways while the modal is still sliding down (§4.3 boundary ownership).
                    val upperEntry = presentation.presented.firstOrNull { indexByContentKey[it.contentKey] == entryIndex + 1 }
                    val upperTransition = upperEntry?.transitionOrNull() ?: transition

                    if (effects.dimAmount > 0f && entryIndex == topMostVisibleIndex && entryIndex > 0) {
                        // Fullscreen dim scrim UNDER the top-most visible layer (reference shell
                        // behavior): it must cover the entering page AND the backdrop revealed
                        // around it when a card-style transition scales layers down — a scrim
                        // inside the entry host would shrink with the page and leave the
                        // surroundings undimmed.
                        //
                        // The scrim is part of the covered treatment, so the top entry's governing
                        // transition owns its curve (§4.3 boundary ownership, NavTransition.scrimFraction),
                        // evaluated against the covered layer directly below (depth 0 = revealed,
                        // 1 = covered). The effects layer only caps the darkness (dimAmount). The
                        // fraction is read inside the deferred graphicsLayer, so the per-frame float
                        // never recomposes this layout.
                        val belowEntry = presentation.presented.firstOrNull { indexByContentKey[it.contentKey] == entryIndex - 1 }
                        val scrimScope = LiveNavTransitionScope(
                            presentation = presentation,
                            entryIndex = entryIndex - 1,
                            isRemoving = belowEntry?.presentation?.isRemoving == true,
                            change = presentation.change,
                            layoutSize = layoutSize,
                            layoutDirection = layoutDirection,
                            density = density,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = effects.dimAmount * ownTransition.scrimFraction(scrimScope).coerceIn(0f, 1f)
                                }
                                .background(Color.Black),
                        )
                    }

                    NavEntryHost(
                        entry = entry,
                        entryIndex = entryIndex,
                        presentation = presentation,
                        stateHolder = stateHolder,
                        viewModelStores = viewModelStores,
                        ownTransition = ownTransition,
                        upperTransition = upperTransition,
                        change = presentation.change,
                        effects = effects,
                        layoutSize = layoutSize,
                        layoutDirection = layoutDirection,
                        density = density,
                    )
                }
            }
        }
    }
}

/**
 * Bit layout of the packed per-host depth-bucket mask (see [NavEntryHost]): bits 0..2 carry the
 * [Lifecycle.State] ordinal of the lifecycle ceiling, the flag bits the three boolean gates. The
 * mask stays below 64 so the boxed derived value always hits the small-integer cache — the
 * per-frame derived recompute allocates nothing.
 */
private const val DEPTH_BUCKET_LIFECYCLE_MASK = 0b111
private const val DEPTH_BUCKET_GOVERNS_OWN = 1 shl 3
private const val DEPTH_BUCKET_CLIP_CORNERS = 1 shl 4
private const val DEPTH_BUCKET_BLOCK_INPUT = 1 shl 5

// The entry host's two pointer filters MUST NOT share a pointerInput key. Dropping the conditional
// blocker at settle makes the node chain update the surviving node in place, and that only restarts
// the handler when a key changed or the two lambdas have different classes — R8 merges them in
// release builds, so the blocking coroutine would outlive its own modifier and eat every event.
private const val BLOCK_INPUT_KEY = "top.yukonga.miuix.kmp.nav.blockInput"
private const val OPAQUE_INPUT_KEY = "top.yukonga.miuix.kmp.nav.opaqueInput"

/**
 * Renders one presented entry as a pure function of [NavPresentation.animatedTop].
 *
 * Per-frame reads of the driving float happen **only** inside deferred `graphicsLayer { }` blocks
 * (here and inside the transition's own `transformEntry`) and inside one threshold-bucketed
 * [derivedStateOf]. The host's composition scope subscribes to the bucket mask, not the raw float,
 * so a frame's float write re-runs only that pure calculation and the host recomposes only when
 * the depth crosses a threshold (a handful of times per transition), never per frame. The host:
 * - derives the packed depth buckets: lifecycle ceiling, governing-transition side, corner-clip
 *   gate, input-block gate — every composition consumer of the depth is threshold-level;
 * - selects the governing transition (own when d <= 0, upper when 0 < d) per §4.3 and applies its
 *   `transformEntry` with a [LiveNavTransitionScope] (per-frame deferred read);
 * - layers the orthogonal [effects] via [NavDisplayEffects]'s per-depth helpers
 *   (`shouldClipCornersAt` / `shouldBlockInputAt`; the dim scrim is a fullscreen sibling rendered
 *   by the layout, not a per-host overlay);
 * - scopes the content with the shared [stateHolder]'s `EntryStateContent`, a per-entry lifecycle
 *   owner, and a per-entry view-model store owner;
 * - keeps composition identity via the surrounding `key(contentKey)` + a cached `movableContentOf`.
 *
 * @param stateHolder the single saveable-state holder created once in [NavDisplayLayout] and shared
 *   across all hosts (stable identity, §0.4).
 */
@Composable
private fun NavEntryHost(
    entry: NavEntry<*>,
    entryIndex: Int,
    presentation: NavPresentation,
    stateHolder: NavSaveableStateHolder,
    viewModelStores: NavEntryViewModelStores,
    ownTransition: NavTransition,
    upperTransition: NavTransition,
    change: NavChange,
    effects: NavDisplayEffects,
    layoutSize: IntSize,
    layoutDirection: LayoutDirection,
    density: Density,
) {
    // Cache a single movableContentOf per host so the entry's content keeps its state if the host is
    // moved within the call hierarchy across recompositions.
    val movableContent = remember {
        movableContentOf<@Composable () -> Unit> { inner -> inner() }
    }

    // Composition-time depth decisions, bucketed. All four composition consumers of the driving
    // float — the lifecycle ceiling, the governing-transition side, the corner-clip gate and the
    // input-block gate — change only when the depth crosses a threshold, never per frame. Packing
    // them into one derived Int keeps this host subscribed to the buckets instead of the raw float:
    // each frame's float write re-runs only this calculation (pure math, allocation-free) while the
    // host recomposes solely on an actual threshold crossing. Raw per-frame floats are read only
    // inside the deferred graphicsLayer blocks installed below.
    val depthBuckets by remember(presentation, entryIndex, entry, effects) {
        derivedStateOf {
            val d = presentation.animatedTop.value - entryIndex
            var mask = navMaxLifecycleFor(entry.presentation, d, presentation.gesture != null).ordinal
            if (d <= 0f) mask = mask or DEPTH_BUCKET_GOVERNS_OWN
            if (effects.shouldClipCornersAt(d)) mask = mask or DEPTH_BUCKET_CLIP_CORNERS
            if (effects.shouldBlockInputAt(d)) mask = mask or DEPTH_BUCKET_BLOCK_INPUT
            mask
        }
    }

    // Per-entry state scopes. The lifecycle owner is remembered per host; the saveable holder and the
    // view-model store registry are display-level and passed from NavDisplayLayout, so the entry's
    // ViewModelStore outlives this host (depth culling must not clear it).
    val maxLifecycle = Lifecycle.State.entries[depthBuckets and DEPTH_BUCKET_LIFECYCLE_MASK]
    val lifecycleOwner = rememberNavEntryLifecycleOwner(maxLifecycle)

    // Per-entry back-dispatcher scope: back consumers inside this entry (a nested NavDisplay, an
    // in-entry back handler) register under a child dispatcher enabled only while the entry is the
    // interactive top — a covered entry stays composed, and its later-registered handlers would
    // otherwise win the shared dispatcher's LIFO arbitration and steal the system back. The child
    // shares the parent's processor, so precedence among enabled handlers is unchanged. Skipped
    // when the host has no back source.
    val parentDispatcherOwner = LocalNavigationEventDispatcherOwner.current
    val entryDispatcherOwner = if (parentDispatcherOwner != null) {
        val interactiveTop = (depthBuckets and DEPTH_BUCKET_GOVERNS_OWN) != 0 && !entry.presentation.isRemoving
        rememberNavEntryDispatcherOwner(enabled = interactiveTop, parent = parentDispatcherOwner)
    } else {
        null
    }

    // Choose the governing transition at the bucketed depth: own transition while at or entering
    // the top (d <= 0), the upper neighbour's transition while covered (0 < d). The per-frame
    // visual is then a pure deferred read inside the transition's own graphicsLayer.
    val sourceSpringLanding by remember(presentation, entryIndex, upperTransition, entry.presentation.isRemoving) {
        derivedStateOf {
            !entry.presentation.isRemoving && upperTransition.retainsSpringOvershoot &&
                presentation.animatedTop.isRunning && presentation.animatedTop.targetValue == entryIndex.toFloat() &&
                presentation.animatedTop.value <= entryIndex.toFloat()
        }
    }
    val activeTransition = if (sourceSpringLanding) upperTransition else
        if ((depthBuckets and DEPTH_BUCKET_GOVERNS_OWN) != 0) ownTransition else upperTransition
    val transitionScope = remember(
        presentation,
        entryIndex,
        entry.presentation.isRemoving,
        change,
        layoutSize,
        layoutDirection,
        density,
    ) {
        LiveNavTransitionScope(
            presentation = presentation,
            entryIndex = entryIndex,
            isRemoving = entry.presentation.isRemoving,
            change = change,
            layoutSize = layoutSize,
            layoutDirection = layoutDirection,
            density = density,
        )
    }
    val visualScope = remember(transitionScope, sourceSpringLanding) {
        if (!sourceSpringLanding) transitionScope else object : NavTransitionScope by transitionScope {
            override val relativeDepth: Float get() = transitionScope.relativeDepth.coerceAtLeast(0f)
            override val role: NavRole get() = NavRole.Covered
        }
    }
    val entryModifier = with(activeTransition) {
        Modifier.transformEntry(visualScope)
    }

    val clipModifier = if (!activeTransition.handlesOwnClipping && (depthBuckets and DEPTH_BUCKET_CLIP_CORNERS) != 0) {
        // Squircle-clip the transitioning top entry. The radius comes from the effects, so callers
        // can follow the platform screen corner (e.g. rememberNavSystemCornerRadius); the corner
        // set follows the effects' clip mode. absoluteSquircleClip uses physical corners (never
        // flipped by layout direction), so the leading side is picked per layout direction.
        val r = effects.cornerClipRadius
        when (effects.cornerClipMode) {
            // Card-style transitions scale the whole page: every corner meets visible background.
            NavCornerClipMode.All -> Modifier.absoluteSquircleClip(topLeft = r, topRight = r, bottomRight = r, bottomLeft = r)

            // Slide-style transitions: only the corners that meet the screen edge as the entering
            // page slides in — left for LTR, right for RTL — matching the reference navigation.
            NavCornerClipMode.Leading ->
                if (layoutDirection == LayoutDirection.Ltr) {
                    Modifier.absoluteSquircleClip(topLeft = r, topRight = 0.dp, bottomRight = 0.dp, bottomLeft = r)
                } else {
                    Modifier.absoluteSquircleClip(topLeft = 0.dp, topRight = r, bottomRight = r, bottomLeft = 0.dp)
                }
        }
    } else {
        Modifier
    }

    val blockInputModifier = if ((depthBuckets and DEPTH_BUCKET_BLOCK_INPUT) != 0) {
        Modifier.pointerInput(BLOCK_INPUT_KEY) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    event.changes.fastForEach { it.consume() }
                }
            }
        }
    } else {
        Modifier
    }

    // Pages are input-opaque within their bounds: a hit-testable but non-consuming filter keeps a
    // touch on a blank region of this entry from falling through to the covered (invisible but
    // still composed) entry below. In-page consumers and the container swipe are unaffected —
    // this only removes lower SIBLINGS from the hit path.
    val opaqueInputModifier = Modifier.pointerInput(OPAQUE_INPUT_KEY) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent()
        }
    }

    Box(modifier = entryModifier.then(clipModifier).then(blockInputModifier).then(opaqueInputModifier)) {
        ProvideNavEntryBackScope(entryDispatcherOwner) {
            stateHolder.EntryStateContent(entry.contentKey) {
                val vmOwner = rememberNavEntryViewModelStoreOwner(viewModelStores, entry.contentKey)
                ProvideNavEntryViewModelStore(vmOwner) {
                    ProvideNavEntryLifecycle(lifecycleOwner) {
                        movableContent {
                            CompositionLocalProvider(LocalNavTransitionScope provides transitionScope) {
                                entry.Content()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Host composable that renders and animates a navigation back stack with continuous-depth float
 * transitions.
 *
 * This is the primary overload: routes are registered via the [content] DSL ([NavEntryBuilder.entry]).
 * The [transition] is the global default; individual routes may override it via `entry(transition = …)`.
 *
 * @param backStack the live back stack to render (a [androidx.compose.runtime.snapshots.SnapshotStateList] of [NavKey]).
 * @param modifier modifier applied to the host container.
 * @param onBack callback for a system/predictive back; defaults to popping the last entry.
 * @param transition the global default [NavTransition]; per-route overrides win.
 * @param effects orthogonal visual effects (corner clip / dim / input blocking).
 * @param content the route-registration DSL block.
 */
@Composable
fun NavDisplay(
    backStack: NavBackStack,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { backStack.removeLastOrNull() },
    transition: NavTransition = NavTransitions.MiuixDefault,
    effects: NavDisplayEffects = NavDisplayEffects(),
    backCompletionPolicy: NavBackCompletionPolicy? = null,
    onBackStarted: () -> Unit = {},
    content: NavEntryBuilder.() -> Unit,
) {
    val provider = remember(content) { entryProvider(content) }
    NavDisplay(
        backStack = backStack,
        entryProvider = provider,
        modifier = modifier,
        onBack = onBack,
        transition = transition,
        effects = effects,
        backCompletionPolicy = backCompletionPolicy,
        onBackStarted = onBackStarted,
    )
}

/**
 * Convenience overload driving the back stack through a [NavController].
 *
 * @param navController the controller whose [NavController.backStack] is rendered.
 * @param modifier modifier applied to the host container.
 * @param onBack callback for a system/predictive back; defaults to [NavController.pop].
 * @param transition the global default [NavTransition]; per-route overrides win.
 * @param effects orthogonal visual effects.
 * @param content the route-registration DSL block.
 */
@Composable
@NonRestartableComposable
fun NavDisplay(
    navController: NavController,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { navController.pop() },
    transition: NavTransition = NavTransitions.MiuixDefault,
    effects: NavDisplayEffects = NavDisplayEffects(),
    backCompletionPolicy: NavBackCompletionPolicy? = null,
    onBackStarted: () -> Unit = {},
    content: NavEntryBuilder.() -> Unit,
) {
    NavDisplay(
        backStack = navController.backStack,
        modifier = modifier,
        onBack = onBack,
        transition = transition,
        effects = effects,
        backCompletionPolicy = backCompletionPolicy,
        onBackStarted = onBackStarted,
        content = content,
    )
}

/**
 * Low-level overload taking a prebuilt [entryProvider] instead of the DSL lambda.
 *
 * Declared `internal` because its signature exposes the `internal` [NavEntry] type; the public DSL
 * overloads above forward to it within the module.
 *
 * @param backStack the live back stack to render.
 * @param entryProvider lookup that builds a [NavEntry] for each [NavKey].
 * @param modifier modifier applied to the host container.
 * @param onBack callback for a system/predictive back; defaults to popping the last entry.
 * @param transition the global default [NavTransition]; per-route overrides win.
 * @param effects orthogonal visual effects.
 */
@Composable
internal fun NavDisplay(
    backStack: NavBackStack,
    entryProvider: (NavKey) -> NavEntry<*>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { backStack.removeLastOrNull() },
    transition: NavTransition = NavTransitions.MiuixDefault,
    effects: NavDisplayEffects = NavDisplayEffects(),
    backCompletionPolicy: NavBackCompletionPolicy? = null,
    onBackStarted: () -> Unit = {},
) {
    require(backStack.isNotEmpty()) { "NavDisplay back stack cannot be empty" }
    NavDisplayLayout(
        backStack = backStack,
        entryProvider = entryProvider,
        onBack = onBack,
        transition = transition,
        effects = effects,
        backCompletionPolicy = backCompletionPolicy,
        onBackStarted = onBackStarted,
        modifier = modifier,
    )
}
