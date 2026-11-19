// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.nav.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.runtime.NavDriverSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge

/**
 * Payload emitted by the [PredictiveBackHandler] progress [Flow].
 *
 * Mirrors the fields of [top.yukonga.miuix.kmp.nav.transition.NavGesture] so the host can map a
 * back-gesture stream onto the single `animatedTop` driver, typically via
 * `animatedTop.snapToFinger(topIndex, event.progress, anchor)`.
 *
 * @property progress Gesture completion; `0f` at touch-down, approaching `1f` as the user commits.
 *   Saturates just under `1f` (the driver's finger cap), even on devices that misreport past 1.
 * @property swipeEdge The screen edge the gesture originated from. Non-gesture sources (Desktop
 *   ESC, Web triggers) report [NavSwipeEdge.None].
 * @property touchY The vertical touch position in pixels, used by transitions that pivot or
 *   anchor the outgoing layer around the finger.
 * @property frameTimeMillis Timestamp of the event in milliseconds, for estimating the gesture's
 *   release velocity. `0` for sources without timing (discrete triggers).
 */
@Stable
class NavBackEvent(
    val progress: Float,
    val swipeEdge: NavSwipeEdge,
    val touchY: Float,
    val frameTimeMillis: Long = 0,
)

/**
 * Back-navigation bridge registered on the [androidx.navigationevent] dispatcher.
 *
 * One common implementation serves every platform: the dispatcher is provided by the host
 * ([LocalNavigationEventDispatcherOwner]) and fed by platform inputs — the system predictive-back
 * stream on Android, the host window's ESC key on Desktop, the screen-edge gesture on iOS. Going
 * through the shared dispatcher (instead of platform-specific wiring) also yields correct
 * precedence for free: handlers are arbitrated last-composed-enabled-first, so an open overlay
 * (dialog / bottom sheet / popup — they register the same way) consumes back before this
 * navigation handler does.
 *
 * Lifecycle of a single gesture:
 * 1. A back gesture starts and [onProgress] is invoked with a cold per-gesture [Flow]; the host
 *    collects it and snaps `animatedTop` for each [NavBackEvent].
 * 2. If the gesture is committed, the [Flow] completes, and [onCommit] is invoked after the
 *    collector returns (pop the top entry and let the convergence spring settle to
 *    `topIndex - 1`).
 * 3. If the gesture is cancelled, the in-flight collection is cancelled and [onCancel] is
 *    invoked (spring `animatedTop` back to `topIndex`).
 *
 * A discrete trigger with no gesture stream (Desktop ESC, a programmatic back) skips [onProgress]
 * and invokes [onCommit] directly; the commit settle animates the pop through the shared spring.
 * Exactly one of [onCommit] / [onCancel] fires per resolved gesture.
 *
 * @param enabled Whether the handler is active. When `false`, the back action falls through to
 *   the next handler (e.g. exits the app on Android, no-ops elsewhere).
 * @param onProgress Suspend block receiving the cold per-gesture [Flow] of [NavBackEvent].
 *   Collect it to track finger progress; the block returns when the stream ends.
 * @param onCommit Invoked once per committed back action, after [onProgress] (if any) returns.
 * @param onCancel Invoked once when a gesture is cancelled before commit.
 */
@Composable
fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: suspend (Flow<NavBackEvent>) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) = PredictiveBackHandlerWithSessions(
    enabled = enabled,
    onProgress = { _, events -> onProgress(events) },
    onCommit = { onCommit() },
    onCancel = { onCancel() },
)

/** Session-aware wiring used internally when terminal work must be tied to its originating gesture. */
@Composable
internal fun PredictiveBackHandlerWithSessions(
    enabled: Boolean,
    onProgress: suspend (sessionId: Long, events: Flow<NavBackEvent>) -> Unit,
    onCommit: (sessionId: Long?) -> Unit,
    onCancel: (sessionId: Long) -> Unit,
) {
    // No dispatcher owner means the host has no back-event source at all (never the case on
    // Android or under the multiplatform window hosts); the handler is simply inert then.
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return
    val scope = rememberCoroutineScope()

    val handler = remember(dispatcher, scope) { NavigationEventFlowAdapter(scope) }
    SideEffect {
        handler.isBackEnabled = enabled
        handler.currentOnProgress = onProgress
        handler.currentOnCommit = onCommit
        handler.currentOnCancel = onCancel
    }
    DisposableEffect(dispatcher, handler) {
        dispatcher.addHandler(handler)
        onDispose { handler.remove() }
    }
}

/**
 * Adapts the dispatcher's synchronous callback protocol (`onBackStarted` -> `onBackProgressed*`
 * -> `onBackCompleted` / `onBackCancelled`) to the suspend-Flow contract of
 * [PredictiveBackHandler].
 *
 * Subclassing [NavigationEventHandler] (the same extension point the androidx compose wrapper
 * uses internally) keeps the event order strictly sequential on the main thread — no snapshot
 * round-trip sits between a progress event and the terminal callback, so a commit can never race
 * ahead of an unprocessed progress snap.
 *
 * Per gesture: [onBackStarted] opens an unbounded channel and launches the collector
 * ([currentOnProgress]); [onBackProgressed] enqueues; [onBackCompleted] closes the stream, waits
 * for the collector to drain the tail, then fires [currentOnCommit]; [onBackCancelled] cancels
 * the collector and fires [currentOnCancel].
 * Progress and terminal callbacks carry the originating
 * session ID so delayed work cannot resolve a newer gesture. A completed callback with no open
 * session (a discrete trigger such as a Desktop ESC press) reports a null ID and commits directly.
 */
internal class NavigationEventFlowAdapter(
    private val scope: CoroutineScope,
) : NavigationEventHandler<NavigationEventInfo>(
    initialInfo = NavigationEventInfo.None,
    isBackEnabled = false,
    isForwardEnabled = false,
) {
    var currentOnProgress: suspend (sessionId: Long, events: Flow<NavBackEvent>) -> Unit = { _, _ -> }
    var currentOnCommit: (sessionId: Long?) -> Unit = {}
    var currentOnCancel: (sessionId: Long) -> Unit = {}

    private var channel: Channel<NavBackEvent>? = null
    private var collector: Job? = null
    private var activeSessionId: Long? = null
    private var nextSessionId = 0L

    // The base-class callbacks are protected; the bodies live in internal handle* twins so the
    // ordering semantics stay unit-testable without a dispatcher.
    override fun onBackStarted(event: NavigationEvent) = handleBackStarted(event)

    override fun onBackProgressed(event: NavigationEvent) = handleBackProgressed(event)

    override fun onBackCompleted() = handleBackCompleted()

    override fun onBackCancelled() = handleBackCancelled()

    internal fun handleBackStarted(event: NavigationEvent) {
        val sessionId = ++nextSessionId
        val ch = Channel<NavBackEvent>(capacity = Channel.UNLIMITED)
        channel = ch
        activeSessionId = sessionId
        val onProgress = currentOnProgress
        collector = scope.launch { onProgress(sessionId, ch.receiveAsFlow()) }
        ch.trySend(event.toNavBackEvent())
    }

    internal fun handleBackProgressed(event: NavigationEvent) {
        channel?.trySend(event.toNavBackEvent())
    }

    internal fun handleBackCompleted() {
        val ch = channel
        val job = collector
        val sessionId = activeSessionId
        channel = null
        collector = null
        activeSessionId = null
        if (ch == null || job == null || sessionId == null) {
            // Discrete trigger: no gesture session was ever started. Commit directly; the
            // commit settle animates the pop through the shared spring from wherever an
            // in-flight transition currently sits.
            currentOnCommit(null)
            return
        }
        ch.close()
        val onCommit = currentOnCommit
        scope.launch {
            // Let the collector drain trailing progress events and return before committing,
            // so no stale snap can land after the commit settle has taken over the driver.
            job.join()
            onCommit(sessionId)
        }
    }

    internal fun handleBackCancelled() {
        val ch = channel
        val job = collector
        val sessionId = activeSessionId
        channel = null
        collector = null
        activeSessionId = null
        if (ch == null || job == null || sessionId == null) return
        ch.close()
        val onCancel = currentOnCancel
        scope.launch {
            job.cancelAndJoin()
            onCancel(sessionId)
        }
    }
}

/** Maps a dispatcher [NavigationEvent] to the common [NavBackEvent]. */
private fun NavigationEvent.toNavBackEvent(): NavBackEvent = NavBackEvent(
    // Misconfigured devices report progress past 1; saturate at the driver's cap so
    // `1 - progress` transition math stays consistent with the driven geometry.
    progress = progress.coerceIn(0f, NavDriverSpec.MAX_FINGER_PROGRESS),
    swipeEdge = when (swipeEdge) {
        NavigationEvent.EDGE_LEFT -> NavSwipeEdge.Left
        NavigationEvent.EDGE_RIGHT -> NavSwipeEdge.Right
        else -> NavSwipeEdge.None
    },
    touchY = touchY,
    frameTimeMillis = frameTimeMillis,
)
