// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.nav.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.nav.transition.NavRole

/**
 * Internal rendering unit of the navigation runtime.
 *
 * One [NavEntry] is produced per back-stack key by the [NavEntryBuilder] DSL. It is the unit of
 * diffing (via [contentKey]), of [androidx.compose.runtime.movableContentOf] identity, and of
 * per-entry state scoping (saveable state / lifecycle / view-model).
 *
 * @param K the concrete route key type this entry renders.
 * @property contentKey value-stable identity used for diffing and for keeping composition identity
 *   stable across recompositions and reorderings. Defaults to the route key itself when the DSL
 *   caller does not supply one.
 * @property metadata arbitrary per-entry data (e.g. a per-route transition override is stored here
 *   by the DSL). Empty by default.
 */
@PublishedApi
internal class NavEntry<K : Any>(
    key: K,
    val contentKey: Any,
    metadata: Map<String, Any>,
    content: @Composable (K) -> Unit,
) {
    /**
     * Registration payload as built by the DSL. A holder class (rather than separate snapshot
     * fields) for two reasons: a composable lambda cannot round-trip through a function-typed
     * snapshot delegate — its runtime class implements the composable calling convention, not
     * `FunctionN`, so the delegate's checkcast throws — and the route [key] must swap atomically
     * with [content]: with a coarse `contentKey` the adopted entry may carry a different key
     * instance (updated route arguments, or even another route type whose contentKey collides),
     * and rendering the old key with the new lambda would show stale arguments or throw a
     * ClassCastException.
     */
    private class Registration<K : Any>(
        val key: K,
        val content: @Composable (K) -> Unit,
    )

    /**
     * Snapshot-backed so [adoptFrom] can refresh a surviving instance in place when the entry
     * provider is rebuilt (a DSL capture changed) or the back stack replaced this slot's route
     * under the same [contentKey]: instance identity must stay stable for `movableContentOf`,
     * so the reconciler reuses the old instance and swaps the registration.
     */
    private var registration: Registration<K> by mutableStateOf(Registration(key, content))

    /** The navigation key this entry currently renders (refreshed by [adoptFrom]). */
    val key: K get() = registration.key

    /** Arbitrary per-entry data (per-route transition / swipe overrides live here). */
    // 转场和手势元数据由宿主消费，不使只读取页面内容的组合失效。
    var metadata: Map<String, Any> by mutableStateOf(metadata)
        private set

    /**
     * Adopts the registration payload of a freshly built [other] entry with the same [contentKey],
     * keeping this instance (and its composition identity) alive. Guarded by payload equality so
     * the routine reconcile on every stack mutation — which rebuilds entries from an unchanged
     * provider and an unchanged route — never invalidates readers: the key value, the content
     * lambda reference, and the metadata values only change when the route arguments or the
     * provider itself changed.
     */
    fun adoptFrom(other: NavEntry<*>) {
        @Suppress("UNCHECKED_CAST")
        val incoming = (other as NavEntry<K>).registration
        val current = registration
        if (current.key != incoming.key || current.content !== incoming.content) {
            registration = incoming
        }
        metadata = other.metadata
    }

    /**
     * Per-entry presentation snapshot maintained by the reconciler (role + removal flag). Read by
     * the renderer to pick the governing [top.yukonga.miuix.kmp.nav.transition.NavTransition].
     */
    var presentation: NavPresentationState by mutableStateOf(
        NavPresentationState(role = NavRole.Top, isRemoving = false),
    )

    internal fun withContent(content: @Composable (K) -> Unit): NavEntry<K> =
        NavEntry(key, contentKey, metadata, content)

    /** Renders this entry's content with its typed key. */
    @Composable
    fun Content() {
        // Single snapshot read so the lambda and the key always come from the same registration.
        val current = registration
        current.content(current.key)
    }
}

/**
 * Render-state snapshot of a single [NavEntry].
 *
 * @property role the entry's current [NavRole] (Top / Incoming / Outgoing / Covered).
 * @property isRemoving true while the entry has been removed from the back stack but is still
 *   animating out (relative depth has not yet reached the unload threshold).
 */
@Immutable
internal data class NavPresentationState(
    val role: NavRole,
    val isRemoving: Boolean,
)
